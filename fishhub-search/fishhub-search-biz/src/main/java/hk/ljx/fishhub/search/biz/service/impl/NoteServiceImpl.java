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
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;


@Service
@Slf4j
@RequiredArgsConstructor
public class NoteServiceImpl implements NoteService {

    private final RestHighLevelClient restHighLevelClient;

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

            for (SearchHit hit : hits) {
                searchNoteRspVOS.add(buildSearchNoteRspVO(hit));
            }
        } catch (IOException e) {
            log.error("==> 查询 Elasticsearch 异常: ", e);
            throw new IllegalStateException("Elasticsearch 查询失败", e);
        }

        return PageResponse.success(searchNoteRspVOS, pageNo, total);
    }

    private static SearchNoteRspVO buildSearchNoteRspVO(SearchHit hit) {
        Map<String, Object> map = hit.getSourceAsMap();
        String updateTimeStr = (String) map.get(NoteIndex.FIELD_NOTE_UPDATE_TIME);
        LocalDateTime updateTime = parseUpdateTime(updateTimeStr);

        String highlightedTitle = null;
        if (CollUtil.isNotEmpty(hit.getHighlightFields()) && hit.getHighlightFields().containsKey(NoteIndex.FIELD_NOTE_TITLE)) {
            highlightedTitle = hit.getHighlightFields().get(NoteIndex.FIELD_NOTE_TITLE).fragments()[0].string();
        }

        long likeTotal = map.get(NoteIndex.FIELD_NOTE_LIKE_TOTAL) instanceof Number n ? n.longValue() : 0L;
        long commentTotal = map.get(NoteIndex.FIELD_NOTE_COMMENT_TOTAL) instanceof Number n ? n.longValue() : 0L;
        long collectTotal = map.get(NoteIndex.FIELD_NOTE_COLLECT_TOTAL) instanceof Number n ? n.longValue() : 0L;
        Long noteId = map.get(NoteIndex.FIELD_NOTE_ID) instanceof Number n ? n.longValue() : null;
        Long creatorId = map.get(NoteIndex.FIELD_NOTE_CREATOR_ID) instanceof Number n ? n.longValue() : null;
        Integer noteType = map.get(NoteIndex.FIELD_NOTE_TYPE) instanceof Number n ? n.intValue() : null;

        return SearchNoteRspVO.builder()
                .noteId(noteId)
                .creatorId(creatorId)
                .cover((String) map.get(NoteIndex.FIELD_NOTE_COVER))
                .type(noteType)
                .videoUri((String) map.get(NoteIndex.FIELD_NOTE_VIDEO_URI))
                .title((String) map.get(NoteIndex.FIELD_NOTE_TITLE))
                .highlightTitle(highlightedTitle)
                .avatar((String) map.get(NoteIndex.FIELD_NOTE_AVATAR))
                .nickname((String) map.get(NoteIndex.FIELD_NOTE_NICKNAME))
                .updateTime(DateUtils.formatRelativeTime(updateTime))
                .likeTotal(NumberUtils.formatNumberString(likeTotal))
                .commentTotal(NumberUtils.formatNumberString(commentTotal))
                .collectTotal(NumberUtils.formatNumberString(collectTotal))
                .build();
    }

    private static LocalDateTime parseUpdateTime(String updateTimeStr) {
        if (StringUtils.isBlank(updateTimeStr)) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(updateTimeStr, DateConstants.DATE_FORMAT_Y_M_D_H_M_S);
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(updateTimeStr, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(updateTimeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception ignored) {
        }
        return LocalDateTime.now();
    }

}
