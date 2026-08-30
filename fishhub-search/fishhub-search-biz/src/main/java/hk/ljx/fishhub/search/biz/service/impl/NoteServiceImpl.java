package hk.ljx.fishhub.search.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.google.common.collect.Lists;
import hk.ljx.framework.common.constant.DateConstants;
import hk.ljx.framework.common.response.PageResponse;
import hk.ljx.framework.common.response.Response;
import hk.ljx.framework.common.util.DateUtils;
import hk.ljx.framework.common.util.NumberUtils;
import hk.ljx.fishhub.search.biz.enums.NotePublishTimeRangeEnum;
import hk.ljx.fishhub.search.biz.enums.NoteSortTypeEnum;
import hk.ljx.fishhub.search.biz.index.NoteIndex;
import hk.ljx.fishhub.search.biz.model.vo.SearchNoteReqVO;
import hk.ljx.fishhub.search.biz.model.vo.SearchNoteRspVO;
import hk.ljx.fishhub.search.biz.service.NoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction;
import org.elasticsearch.common.lucene.search.function.FunctionScoreQuery;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FieldValueFactorFunctionBuilder;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import hk.ljx.fishhub.user.client.UserClient;
import hk.ljx.fishhub.user.dto.rsp.FindUserByIdRspDTO;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NoteServiceImpl implements NoteService {

    private static final Logger log = LoggerFactory.getLogger(NoteServiceImpl.class);
    private final RestHighLevelClient restHighLevelClient;
    private final UserClient userClient;

    /** 笔记搜索本地短缓存（3 秒）：极大降低高并发相同关键词检索时的 ES 分词与高亮开销 */
    private static final Cache<String, PageResponse<SearchNoteRspVO>> SEARCH_NOTE_LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(500)
            .maximumSize(5000)
            .expireAfterWrite(3, TimeUnit.SECONDS)
            .build();

    public NoteServiceImpl(RestHighLevelClient restHighLevelClient, UserClient userClient) {
        this.restHighLevelClient = restHighLevelClient;
        this.userClient = userClient;
    }

    /**
     * 搜索笔记
     *
     * @param searchNoteReqVO
     * @return
     */
    @Override
    public PageResponse<SearchNoteRspVO> searchNote(SearchNoteReqVO searchNoteReqVO) {
        String keyword = searchNoteReqVO.getKeyword();
        Integer pageNo = searchNoteReqVO.getPageNo();
        Integer type = searchNoteReqVO.getType();
        Integer sort = searchNoteReqVO.getSort();
        Integer publishTimeRange = searchNoteReqVO.getPublishTimeRange();

        String localCacheKey = String.format("%s:%s:%s:%s:%s", keyword, pageNo, type, sort, publishTimeRange);
        PageResponse<SearchNoteRspVO> localCached = SEARCH_NOTE_LOCAL_CACHE.getIfPresent(localCacheKey);
        if (localCached != null) {
            return localCached;
        }

        SearchRequest searchRequest = new SearchRequest(NoteIndex.NAME);
        // 新环境尚未产生公开笔记时，note 索引不存在应视为无搜索结果，而不是服务异常。
        searchRequest.indicesOptions(IndicesOptions.lenientExpandOpen());

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery().must(
                QueryBuilders.multiMatchQuery(keyword)
                        .field(NoteIndex.FIELD_NOTE_TITLE, 2.0f)
                        .field(NoteIndex.FIELD_NOTE_TOPIC)
        );

        // 若勾选了笔记类型，添加过滤条件
        if (Objects.nonNull(type)) {
            boolQueryBuilder.filter(QueryBuilders.termQuery(NoteIndex.FIELD_NOTE_TYPE, type));
        }

        // 按发布时间范围过滤
        NotePublishTimeRangeEnum notePublishTimeRangeEnum = NotePublishTimeRangeEnum.valueOf(publishTimeRange);

        if (Objects.nonNull(notePublishTimeRangeEnum)) {
            String endTime = LocalDateTime.now().format(DateConstants.DATE_FORMAT_Y_M_D_H_M_S);
            String startTime = switch (notePublishTimeRangeEnum) {
                case DAY -> DateUtils.localDateTime2String(LocalDateTime.now().minusDays(1));
                case WEEK -> DateUtils.localDateTime2String(LocalDateTime.now().minusWeeks(1));
                case HALF_YEAR -> DateUtils.localDateTime2String(LocalDateTime.now().minusMonths(6));
            };
            if (StringUtils.isNoneBlank(startTime)) {
                boolQueryBuilder.filter(QueryBuilders.rangeQuery(NoteIndex.FIELD_NOTE_CREATE_TIME)
                        .gte(startTime)
                        .lte(endTime)
                );
            }
        }

        NoteSortTypeEnum noteSortTypeEnum = NoteSortTypeEnum.valueOf(sort);

        if (Objects.nonNull(noteSortTypeEnum)) {
            switch (noteSortTypeEnum) {
                case LATEST -> sourceBuilder.sort(new FieldSortBuilder(NoteIndex.FIELD_NOTE_CREATE_TIME).order(SortOrder.DESC));
                case MOST_LIKE -> sourceBuilder.sort(new FieldSortBuilder(NoteIndex.FIELD_NOTE_LIKE_TOTAL).order(SortOrder.DESC));
                case MOST_COMMENT -> sourceBuilder.sort(new FieldSortBuilder(NoteIndex.FIELD_NOTE_COMMENT_TOTAL).order(SortOrder.DESC));
                case MOST_COLLECT -> sourceBuilder.sort(new FieldSortBuilder(NoteIndex.FIELD_NOTE_COLLECT_TOTAL).order(SortOrder.DESC));
            }
            sourceBuilder.query(boolQueryBuilder);
        } else { // 综合排序
            // 综合排序，自定义评分，并按 _score 评分降序
            sourceBuilder.sort(new FieldSortBuilder("_score").order(SortOrder.DESC));

            FunctionScoreQueryBuilder.FilterFunctionBuilder[] filterFunctionBuilders = new FunctionScoreQueryBuilder.FilterFunctionBuilder[] {
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                            new FieldValueFactorFunctionBuilder(NoteIndex.FIELD_NOTE_LIKE_TOTAL)
                                    .factor(0.5f)
                                    .modifier(FieldValueFactorFunction.Modifier.SQRT)
                                    .missing(0)
                    ),
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                            new FieldValueFactorFunctionBuilder(NoteIndex.FIELD_NOTE_COLLECT_TOTAL)
                                    .factor(0.3f)
                                    .modifier(FieldValueFactorFunction.Modifier.SQRT)
                                    .missing(0)
                    ),
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                            new FieldValueFactorFunctionBuilder(NoteIndex.FIELD_NOTE_COMMENT_TOTAL)
                                    .factor(0.2f)
                                    .modifier(FieldValueFactorFunction.Modifier.SQRT)
                                    .missing(0)
                    )
            };

            FunctionScoreQueryBuilder functionScoreQueryBuilder = QueryBuilders.functionScoreQuery(boolQueryBuilder,
                            filterFunctionBuilders)
                    .scoreMode(FunctionScoreQuery.ScoreMode.SUM)
                    .boostMode(CombineFunction.SUM);

            sourceBuilder.query(functionScoreQueryBuilder);
        }

        int pageSize = 10;
        int from = (pageNo - 1) * pageSize;
        sourceBuilder.from(from);
        sourceBuilder.size(pageSize);

        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.field(NoteIndex.FIELD_NOTE_TITLE)
                .preTags("<strong>")
                .postTags("</strong>");
        sourceBuilder.highlighter(highlightBuilder);

        searchRequest.source(sourceBuilder);

        List<SearchNoteRspVO> searchNoteRspVOS = Lists.newArrayList();
        long total = 0;
        try {
            log.debug("==> SearchRequest: {}", searchRequest.source().toString());
            SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

            total = searchResponse.getHits().getTotalHits().value;
            log.debug("==> 命中文档总数, hits: {}", total);

            SearchHits hits = searchResponse.getHits();
            List<Long> creatorIds = Lists.newArrayList();
            for (SearchHit hit : hits) {
                Map<String, Object> map = hit.getSourceAsMap();
                if (map != null && map.get(NoteIndex.FIELD_NOTE_CREATOR_ID) instanceof Number n) {
                    creatorIds.add(n.longValue());
                }
            }

            Map<Long, FindUserByIdRspDTO> userMap = Collections.emptyMap();
            if (CollUtil.isNotEmpty(creatorIds)) {
                List<FindUserByIdRspDTO> userDTOs = userClient.findByIds(creatorIds.stream().distinct().toList());
                if (CollUtil.isNotEmpty(userDTOs)) {
                    userMap = userDTOs.stream()
                            .filter(Objects::nonNull)
                            .collect(Collectors.toMap(FindUserByIdRspDTO::getId, Function.identity(), (a, b) -> a));
                }
            }

            for (SearchHit hit : hits) {
                searchNoteRspVOS.add(buildSearchNoteRspVO(hit, userMap));
            }
        } catch (IOException e) {
            log.error("==> 查询 Elasticsearch 异常: ", e);
            throw new IllegalStateException("Elasticsearch 查询失败", e);
        }

        PageResponse<SearchNoteRspVO> response = PageResponse.success(searchNoteRspVOS, pageNo, total);
        SEARCH_NOTE_LOCAL_CACHE.put(localCacheKey, response);
        return response;
    }

    private static SearchNoteRspVO buildSearchNoteRspVO(SearchHit hit, Map<Long, FindUserByIdRspDTO> userMap) {
        Map<String, Object> map = hit.getSourceAsMap();
        String updateTimeStr = (String) map.get(NoteIndex.FIELD_NOTE_UPDATE_TIME);
        LocalDateTime updateTime = parseUpdateTime(updateTimeStr);

        String highlightedTitle = null;
        if (CollUtil.isNotEmpty(hit.getHighlightFields()) && hit.getHighlightFields().containsKey(NoteIndex.FIELD_NOTE_TITLE)) {
            highlightedTitle = hit.getHighlightFields().get(NoteIndex.FIELD_NOTE_TITLE).fragments()[0].string();
        }

        long likeTotal = getLongValue(map, NoteIndex.FIELD_NOTE_LIKE_TOTAL);
        long commentTotal = getLongValue(map, NoteIndex.FIELD_NOTE_COMMENT_TOTAL);
        long collectTotal = getLongValue(map, NoteIndex.FIELD_NOTE_COLLECT_TOTAL);
        Long noteId = getLong(map, NoteIndex.FIELD_NOTE_ID);
        Long creatorId = getLong(map, NoteIndex.FIELD_NOTE_CREATOR_ID);
        Integer noteType = getInteger(map, NoteIndex.FIELD_NOTE_TYPE);

        FindUserByIdRspDTO author = creatorId != null ? userMap.get(creatorId) : null;
        String avatar = author != null ? author.getAvatar() : null;
        String nickname = author != null ? author.getNickName() : null;

        return SearchNoteRspVO.builder()
                .noteId(noteId)
                .creatorId(creatorId)
                .cover((String) map.get(NoteIndex.FIELD_NOTE_COVER))
                .type(noteType)
                .videoUri((String) map.get(NoteIndex.FIELD_NOTE_VIDEO_URI))
                .title((String) map.get(NoteIndex.FIELD_NOTE_TITLE))
                .highlightTitle(highlightedTitle)
                .avatar(avatar)
                .nickname(nickname)
                .updateTime(DateUtils.localDateTime2String(updateTime))
                .likeTotal(NumberUtils.formatNumberString(likeTotal))
                .commentTotal(NumberUtils.formatNumberString(commentTotal))
                .collectTotal(NumberUtils.formatNumberString(collectTotal))
                .build();
    }

    private static LocalDateTime parseUpdateTime(String updateTimeStr) {
        return DateUtils.parseFlexibleLocalDateTime(updateTimeStr, LocalDateTime.now());
    }

    private static Long getLong(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val instanceof Number n ? n.longValue() : null;
    }

    private static long getLongValue(Map<String, Object> map, String key) {
        Long val = getLong(map, key);
        return val != null ? val : 0L;
    }

    private static Integer getInteger(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val instanceof Number n ? n.intValue() : null;
    }

}
