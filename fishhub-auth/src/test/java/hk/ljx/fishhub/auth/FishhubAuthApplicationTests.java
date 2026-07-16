package hk.ljx.fishhub.auth;

import hk.ljx.fishhub.auth.domain.dataobject.UserDO;
import hk.ljx.fishhub.auth.domain.mapper.UserDOMapper;
import hk.ljx.framework.common.util.JsonUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

@SpringBootTest
@Slf4j
class FishhubAuthApplicationTests {

    @Resource
    private UserDOMapper userDOMapper;

    /**
     * 测试插入数据
     */
    @Test
    void testInsert() {
        UserDO userDO = UserDO.builder()
                .username("fish")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        userDOMapper.insert(userDO);
    }

    @Test
    void testSelect() {
        // 查询主键 ID 为 1 的记录
        UserDO userDO = userDOMapper.selectByPrimaryKey(1L);
        log.info("User: {}", JsonUtils.toJsonString(userDO));
    }
}
