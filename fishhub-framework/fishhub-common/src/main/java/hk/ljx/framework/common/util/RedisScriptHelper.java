package hk.ljx.framework.common.util;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

/**
 * Redis Lua 脚本加载通用工具类：统一从 classpath 加载并解析 Lua 脚本，消除各模块重复样板代码。
 */
public final class RedisScriptHelper {

    private RedisScriptHelper() {
    }

    /**
     * 加载返回类型为 Long 的 Lua 脚本
     *
     * @param luaPath 脚本类路径，例如 "/lua/update_hot_comments.lua"
     * @return 预解析的 DefaultRedisScript<Long> 实例
     */
    public static DefaultRedisScript<Long> loadLongScript(String luaPath) {
        return loadScript(luaPath, Long.class);
    }

    /**
     * 加载指定返回类型的 Lua 脚本
     *
     * @param luaPath    脚本类路径
     * @param resultType 返回结果类型
     * @param <T>        结果泛型
     * @return 预解析的 DefaultRedisScript<T> 实例
     */
    public static <T> DefaultRedisScript<T> loadScript(String luaPath, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(luaPath)));
        script.setResultType(resultType);
        return script;
    }
}
