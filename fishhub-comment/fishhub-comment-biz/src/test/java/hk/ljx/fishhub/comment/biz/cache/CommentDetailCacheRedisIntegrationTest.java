package hk.ljx.fishhub.comment.biz.cache;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.comment.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.comment.biz.model.vo.FindChildCommentItemRspVO;
import hk.ljx.fishhub.comment.biz.model.vo.FindCommentItemRspVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfSystemProperty(named = "fishhub.redis.integration", matches = "true")
class CommentDetailCacheRedisIntegrationTest {

    @Test
    void shouldReadRootAndChildJsonWithFreshClientAfterWriterIsClosed() {
        String suffix = UUID.randomUUID().toString();
        String rootKey = RedisKeyConstants.buildCommentDetailKey("root-" + suffix);
        String childKey = RedisKeyConstants.buildCommentDetailKey("child-" + suffix);
        List<String> keys = List.of(rootKey, childKey);

        String rootJson = JsonUtils.toJsonString(FindCommentItemRspVO.builder()
                .commentId(6001L)
                .content("一级评论")
                .build());
        String childJson = JsonUtils.toJsonString(FindChildCommentItemRspVO.builder()
                .commentId(6002L)
                .content("二级评论")
                .build());

        try (AnnotationConfigApplicationContext writerContext = applicationContext()) {
            CommentDetailCache writer = writerContext.getBean(CommentDetailCache.class);
            writer.putAll(Map.of(rootKey, rootJson, childKey, childJson));
        }

        try (AnnotationConfigApplicationContext readerContext = applicationContext()) {
            CommentDetailCache reader = readerContext.getBean(CommentDetailCache.class);
            List<String> cachedJson = reader.multiGet(keys);

            assertEquals(6001L, JsonUtils.parseObject(cachedJson.get(0), FindCommentItemRspVO.class).getCommentId());
            assertEquals("一级评论", JsonUtils.parseObject(cachedJson.get(0), FindCommentItemRspVO.class).getContent());
            assertEquals(6002L, JsonUtils.parseObject(cachedJson.get(1), FindChildCommentItemRspVO.class).getCommentId());
            assertEquals("二级评论", JsonUtils.parseObject(cachedJson.get(1), FindChildCommentItemRspVO.class).getContent());
        } finally {
            try (AnnotationConfigApplicationContext cleanupContext = applicationContext()) {
                cleanupContext.getBean(StringRedisTemplate.class).delete(keys);
            }
        }
    }

    private AnnotationConfigApplicationContext applicationContext() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(LettuceConnectionFactory.class, this::connectionFactory);
        context.registerBean(StringRedisTemplate.class,
                () -> new StringRedisTemplate(context.getBean(LettuceConnectionFactory.class)));
        context.register(CommentDetailCache.class);
        context.refresh();
        return context;
    }

    private LettuceConnectionFactory connectionFactory() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                System.getProperty("fishhub.redis.host", "127.0.0.1"),
                Integer.getInteger("fishhub.redis.port", 6379));
        String password = System.getProperty("fishhub.redis.password", "");
        if (!password.isBlank()) {
            configuration.setPassword(RedisPassword.of(password));
        }
        return new LettuceConnectionFactory(configuration);
    }
}
