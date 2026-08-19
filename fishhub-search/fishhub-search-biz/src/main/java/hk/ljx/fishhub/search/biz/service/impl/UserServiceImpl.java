package hk.ljx.fishhub.search.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.google.common.collect.Lists;
import hk.ljx.framework.common.response.PageResponse;
import hk.ljx.framework.common.util.NumberUtils;
import hk.ljx.fishhub.search.biz.index.UserIndex;
import hk.ljx.fishhub.search.biz.model.vo.SearchUserReqVO;
import hk.ljx.fishhub.search.biz.model.vo.SearchUserRspVO;
import hk.ljx.fishhub.search.biz.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;


@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final RestHighLevelClient restHighLevelClient;

    /**
     * 搜索用户
     *
     * @param searchUserReqVO
     * @return
     */
    @Override
    public PageResponse<SearchUserRspVO> searchUser(SearchUserReqVO searchUserReqVO) {
        // 查询关键词
        String keyword = searchUserReqVO.getKeyword();
        // 当前页码
        Integer pageNo = searchUserReqVO.getPageNo();

        // 构建 SearchRequest，指定索引
        SearchRequest searchRequest = new SearchRequest(UserIndex.NAME);
        // 新环境尚未注册用户时，user 索引不存在应视为无搜索结果，而不是服务异常。
        searchRequest.indicesOptions(IndicesOptions.lenientExpandOpen());

        // 构建查询内容
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        // 构建 multi_match 查询，查询 nickname 和 fishhub_id 字段
        sourceBuilder.query(QueryBuilders.multiMatchQuery(
                keyword, UserIndex.FIELD_USER_NICKNAME, UserIndex.FIELD_USER_FISHHUB_ID));

        // 排序，按 fans_total 降序
        SortBuilder<?> sortBuilder = new FieldSortBuilder(UserIndex.FIELD_USER_FANS_TOTAL)
                .order(SortOrder.DESC);
        sourceBuilder.sort(sortBuilder);

        // 设置分页，from 和 size
        int pageSize = 10; // 每页展示数据量
        int from = (pageNo - 1) * pageSize; // 偏移量

        sourceBuilder.from(from);
        sourceBuilder.size(pageSize);

        // 设置高亮字段
        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.field(UserIndex.FIELD_USER_NICKNAME)
                .preTags("<strong>") // 设置包裹标签
                .postTags("</strong>");
        sourceBuilder.highlighter(highlightBuilder);

        // 将构建的查询条件设置到 SearchRequest 中
        searchRequest.source(sourceBuilder);

        // 返参 VO 集合
        List<SearchUserRspVO> searchUserRspVOS = Lists.newArrayList();
        // 总文档数，默认为 0
        long total = 0;
        try {
            log.debug("==> SearchRequest: {}", searchRequest);

            // 执行查询请求
            SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

            // 处理搜索结果
            total = searchResponse.getHits().getTotalHits().value;
            log.debug("==> 命中文档总数, hits: {}", total);

            // 获取搜索命中的文档列表
            SearchHits hits = searchResponse.getHits();

            for (SearchHit hit : hits) {
                log.debug("==> 文档数据: {}", hit.getSourceAsString());

                // 获取文档的所有字段（以 Map 的形式返回）
                Map<String, Object> sourceAsMap = hit.getSourceAsMap();

                // 提取特定字段值
                Long userId = ((Number) sourceAsMap.get(UserIndex.FIELD_USER_ID)).longValue();
                String nickname = (String) sourceAsMap.get(UserIndex.FIELD_USER_NICKNAME);
                String avatar = (String) sourceAsMap.get(UserIndex.FIELD_USER_AVATAR);
                String fishhubId = (String) sourceAsMap.get(UserIndex.FIELD_USER_FISHHUB_ID);
                long noteTotal = ((Number) sourceAsMap.getOrDefault(UserIndex.FIELD_USER_NOTE_TOTAL, 0)).longValue();
                long fansTotal = ((Number) sourceAsMap.getOrDefault(UserIndex.FIELD_USER_FANS_TOTAL, 0)).longValue();

                // 获取高亮字段
                String highlightedNickname = null;
                if (CollUtil.isNotEmpty(hit.getHighlightFields())
                        && hit.getHighlightFields().containsKey(UserIndex.FIELD_USER_NICKNAME)) {
                    highlightedNickname = hit.getHighlightFields().get(UserIndex.FIELD_USER_NICKNAME).fragments()[0].string();
                }

                // 构建 VO 实体类
                SearchUserRspVO searchUserRspVO = SearchUserRspVO.builder()
                        .userId(userId)
                        .nickname(nickname)
                        .avatar(avatar)
                        .fishhubId(fishhubId)
                        .noteTotal(noteTotal)
                        .fansTotal(NumberUtils.formatNumberString(fansTotal))
                        .highlightNickname(highlightedNickname)
                        .build();
                searchUserRspVOS.add(searchUserRspVO);
            }
        } catch (Exception e) {
            log.error("==> 查询 Elasticserach 异常: ", e);
            throw new IllegalStateException("Elasticsearch 查询失败", e);
        }

        return PageResponse.success(searchUserRspVOS, pageNo, total);
    }

}
