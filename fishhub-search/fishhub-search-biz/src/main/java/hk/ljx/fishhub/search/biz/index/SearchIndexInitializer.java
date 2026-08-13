package hk.ljx.fishhub.search.biz.index;

import hk.ljx.fishhub.search.biz.domain.mapper.SelectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetMappingsRequest;
import org.elasticsearch.client.indices.GetMappingsResponse;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 在 Canal 或补偿任务写入第一条文档之前创建显式映射。
 * 发现旧的动态映射时，从 MySQL 重建版本化索引并切换别名。
 */
@Component
@Slf4j
public class SearchIndexInitializer implements ApplicationRunner {

    @Resource
    private RestHighLevelClient client;
    @Resource
    private SelectMapper selectMapper;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        ensureNoteIndex();
        ensureUserIndex();
    }

    private void ensureNoteIndex() throws IOException {
        Map<String, String> expectedTypes = Map.ofEntries(
                Map.entry(NoteIndex.FIELD_NOTE_ID, "long"),
                Map.entry(NoteIndex.FIELD_NOTE_CREATOR_ID, "long"),
                Map.entry(NoteIndex.FIELD_NOTE_COVER, "keyword"),
                Map.entry(NoteIndex.FIELD_NOTE_VIDEO_URI, "keyword"),
                Map.entry(NoteIndex.FIELD_NOTE_TITLE, "text"),
                Map.entry(NoteIndex.FIELD_NOTE_TOPIC, "text"),
                Map.entry(NoteIndex.FIELD_NOTE_NICKNAME, "text"),
                Map.entry(NoteIndex.FIELD_NOTE_AVATAR, "keyword"),
                Map.entry(NoteIndex.FIELD_NOTE_TYPE, "integer"),
                Map.entry(NoteIndex.FIELD_NOTE_CREATE_TIME, "date"),
                Map.entry(NoteIndex.FIELD_NOTE_UPDATE_TIME, "date"),
                Map.entry(NoteIndex.FIELD_NOTE_LIKE_TOTAL, "long"),
                Map.entry(NoteIndex.FIELD_NOTE_COLLECT_TOTAL, "long"),
                Map.entry(NoteIndex.FIELD_NOTE_COMMENT_TOTAL, "long")
        );
        ensureIndex(NoteIndex.NAME, expectedTypes, noteMapping(),
                selectMapper.selectEsNoteIndexData(null, null), NoteIndex.FIELD_NOTE_ID);
    }

    private void ensureUserIndex() throws IOException {
        Map<String, String> expectedTypes = Map.of(
                UserIndex.FIELD_USER_ID, "long",
                UserIndex.FIELD_USER_NICKNAME, "text",
                UserIndex.FIELD_USER_AVATAR, "keyword",
                UserIndex.FIELD_USER_FISHHUB_ID, "keyword",
                UserIndex.FIELD_USER_NOTE_TOTAL, "long",
                UserIndex.FIELD_USER_FANS_TOTAL, "long"
        );
        ensureIndex(UserIndex.NAME, expectedTypes, userMapping(),
                selectMapper.selectEsUserIndexData(null), UserIndex.FIELD_USER_ID);
    }

    private void ensureIndex(String alias, Map<String, String> expectedTypes, XContentBuilder mapping,
                             List<Map<String, Object>> documents, String idField) throws IOException {
        if (!client.indices().exists(new GetIndexRequest(alias), RequestOptions.DEFAULT)) {
            createVersionedIndex(alias, mapping, documents, idField, true);
            return;
        }

        GetMappingsResponse response = client.indices().getMapping(
                new GetMappingsRequest().indices(alias), RequestOptions.DEFAULT);
        Map<String, MappingMetaData> existingMappings = response.mappings();
        boolean directConcreteIndex = existingMappings.containsKey(alias);
        if (hasExpectedTypes(existingMappings, expectedTypes) && !directConcreteIndex) {
            return;
        }

        String physicalIndex = createVersionedIndex(alias, mapping, documents, idField, false);
        if (directConcreteIndex) {
            // Elasticsearch 不允许索引与别名同名，旧的动态索引必须先删除再创建别名。
            client.indices().delete(new DeleteIndexRequest(alias), RequestOptions.DEFAULT);
            addAlias(alias, physicalIndex);
        } else {
            IndicesAliasesRequest aliasesRequest = new IndicesAliasesRequest();
            existingMappings.keySet().forEach(index -> aliasesRequest.addAliasAction(
                    IndicesAliasesRequest.AliasActions.remove().index(index).alias(alias)));
            aliasesRequest.addAliasAction(IndicesAliasesRequest.AliasActions.add()
                    .index(physicalIndex).alias(alias));
            client.indices().updateAliases(aliasesRequest, RequestOptions.DEFAULT);
            for (String oldIndex : existingMappings.keySet()) {
                client.indices().delete(new DeleteIndexRequest(oldIndex), RequestOptions.DEFAULT);
            }
        }
        log.info("Elasticsearch 索引 {} 已从 MySQL 重建并切换到 {}", alias, physicalIndex);
    }

    private void addAlias(String alias, String physicalIndex) throws IOException {
        IndicesAliasesRequest aliasesRequest = new IndicesAliasesRequest();
        aliasesRequest.addAliasAction(IndicesAliasesRequest.AliasActions.add()
                .index(physicalIndex).alias(alias));
        client.indices().updateAliases(aliasesRequest, RequestOptions.DEFAULT);
    }

    private String createVersionedIndex(String alias, XContentBuilder mapping,
                                        List<Map<String, Object>> documents, String idField,
                                        boolean createAlias) throws IOException {
        String physicalIndex = alias + "_v" + System.currentTimeMillis();
        CreateIndexRequest createRequest = new CreateIndexRequest(physicalIndex).mapping(mapping);
        if (createAlias) {
            createRequest.alias(new Alias(alias));
        }
        client.indices().create(createRequest, RequestOptions.DEFAULT);

        if (documents != null && !documents.isEmpty()) {
            BulkRequest bulkRequest = new BulkRequest();
            for (Map<String, Object> document : documents) {
                bulkRequest.add(new IndexRequest(physicalIndex)
                        .id(String.valueOf(document.get(idField)))
                        .source(document));
            }
            BulkResponse bulkResponse = client.bulk(bulkRequest, RequestOptions.DEFAULT);
            if (bulkResponse.hasFailures()) {
                throw new IllegalStateException("Elasticsearch 索引重建失败: " + bulkResponse.buildFailureMessage());
            }
        }
        return physicalIndex;
    }

    @SuppressWarnings("unchecked")
    private boolean hasExpectedTypes(Map<String, MappingMetaData> mappings,
                                     Map<String, String> expectedTypes) {
        if (mappings.isEmpty()) {
            return false;
        }
        Object propertiesValue = mappings.values().iterator().next().sourceAsMap().get("properties");
        if (!(propertiesValue instanceof Map<?, ?> properties)) {
            return false;
        }
        for (Map.Entry<String, String> expected : expectedTypes.entrySet()) {
            Object fieldValue = properties.get(expected.getKey());
            if (!(fieldValue instanceof Map<?, ?> field)
                    || !expected.getValue().equals(field.get("type"))) {
                return false;
            }
        }
        return true;
    }

    private XContentBuilder noteMapping() throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder().startObject().startObject("properties");
        addField(builder, NoteIndex.FIELD_NOTE_ID, "long");
        addField(builder, NoteIndex.FIELD_NOTE_CREATOR_ID, "long");
        addField(builder, NoteIndex.FIELD_NOTE_COVER, "keyword");
        addField(builder, NoteIndex.FIELD_NOTE_VIDEO_URI, "keyword");
        addField(builder, NoteIndex.FIELD_NOTE_TITLE, "text");
        addField(builder, NoteIndex.FIELD_NOTE_TOPIC, "text");
        addField(builder, NoteIndex.FIELD_NOTE_NICKNAME, "text");
        addField(builder, NoteIndex.FIELD_NOTE_AVATAR, "keyword");
        addField(builder, NoteIndex.FIELD_NOTE_TYPE, "integer");
        addDateField(builder, NoteIndex.FIELD_NOTE_CREATE_TIME);
        addDateField(builder, NoteIndex.FIELD_NOTE_UPDATE_TIME);
        addField(builder, NoteIndex.FIELD_NOTE_LIKE_TOTAL, "long");
        addField(builder, NoteIndex.FIELD_NOTE_COLLECT_TOTAL, "long");
        addField(builder, NoteIndex.FIELD_NOTE_COMMENT_TOTAL, "long");
        return builder.endObject().endObject();
    }

    private XContentBuilder userMapping() throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder().startObject().startObject("properties");
        addField(builder, UserIndex.FIELD_USER_ID, "long");
        addField(builder, UserIndex.FIELD_USER_NICKNAME, "text");
        addField(builder, UserIndex.FIELD_USER_AVATAR, "keyword");
        addField(builder, UserIndex.FIELD_USER_FISHHUB_ID, "keyword");
        addField(builder, UserIndex.FIELD_USER_NOTE_TOTAL, "long");
        addField(builder, UserIndex.FIELD_USER_FANS_TOTAL, "long");
        return builder.endObject().endObject();
    }

    private void addField(XContentBuilder builder, String name, String type) throws IOException {
        builder.startObject(name).field("type", type).endObject();
    }

    private void addDateField(XContentBuilder builder, String name) throws IOException {
        builder.startObject(name)
                .field("type", "date")
                .field("format", "yyyy-MM-dd HH:mm:ss||strict_date_optional_time||epoch_millis")
                .endObject();
    }
}
