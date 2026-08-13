package hk.ljx.fishhub.data.align.job;

import hk.ljx.fishhub.data.align.constant.TableConstants;
import hk.ljx.fishhub.data.align.domain.mapper.CreateTableMapper;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;


@Component
@Slf4j
public class CreateTableXxlJob {

    /**
     * 表总分片数
     */
    @Value("${table.shards}")
    private int tableShards;

    @Resource
    private CreateTableMapper createTableMapper;

    /**
     * 数据库可能在当天任意时间重建，不能只依赖凌晨的调度任务建表。
     * 在 MQ 监听器开始消费前补齐当天和次日的表，保证增量消息始终有表可写。
     */
    @PostConstruct
    public void initializeDailyTables() {
        createDailyTables(false);
    }

    /**
     * 创建当天和次日的数据对齐分片表。
     */
    @XxlJob("createTableJobHandler")
    public void createTableJobHandler() throws Exception {
        createDailyTables(true);
    }

    private void createDailyTables(boolean xxlJobTriggered) {
        // 消费端写入的是当天的表，因此需要同时建好今天和明天的表，避免当天 00:00 前服务重启时无表可写
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);

        for (LocalDate date : List.of(today, tomorrow)) {
            String dateStr = date.format(formatter);
            log.info("## 开始确保日增量数据表存在，日期: {}", dateStr);
            if (xxlJobTriggered) {
                XxlJobHelper.log("## 开始创建日增量数据表，日期: {}...", dateStr);
            }

            if (tableShards > 0) {
                for (int hashKey = 0; hashKey < tableShards; hashKey++) {
                    // 表名后缀
                    String tableNameSuffix = TableConstants.buildTableNameSuffix(dateStr, hashKey);

                    // 创建表
                    createTableMapper.createDataAlignFollowingCountTempTable(tableNameSuffix);
                    createTableMapper.createDataAlignFansCountTempTable(tableNameSuffix);
                    createTableMapper.createDataAlignNoteCollectCountTempTable(tableNameSuffix);
                    createTableMapper.createDataAlignUserCollectCountTempTable(tableNameSuffix);
                    createTableMapper.createDataAlignUserLikeCountTempTable(tableNameSuffix);
                    createTableMapper.createDataAlignNoteLikeCountTempTable(tableNameSuffix);
                    createTableMapper.createDataAlignNotePublishCountTempTable(tableNameSuffix);
                }
            }

            log.info("## 日增量数据表检查完成，日期: {}", dateStr);
            if (xxlJobTriggered) {
                XxlJobHelper.log("## 结束创建日增量数据表，日期: {}...", dateStr);
            }
        }
    }

}
