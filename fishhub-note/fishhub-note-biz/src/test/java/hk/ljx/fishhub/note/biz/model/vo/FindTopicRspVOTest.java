package hk.ljx.fishhub.note.biz.model.vo;

import hk.ljx.framework.common.util.JsonUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FindTopicRspVOTest {

    @Test
    void shouldDeserializeTopicSnapshotFromRedisJson() throws Exception {
        String snapshot = JsonUtils.toJsonString(List.of(
                FindTopicRspVO.builder().id(1L).name("Java").build()));

        List<FindTopicRspVO> topics = JsonUtils.parseList(snapshot, FindTopicRspVO.class);

        assertEquals(1L, topics.get(0).getId());
        assertEquals("Java", topics.get(0).getName());
    }
}
