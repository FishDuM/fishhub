
package hk.ljx.framework.biz.operationlog.aspect;

import hk.ljx.framework.common.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

@Aspect
@Slf4j
public class ApiOperationLogAspect {

    /**
     * 入参/出参日志截断长度
     */
    private static final int MAX_LOG_LENGTH = 500;

    @Pointcut("@annotation(hk.ljx.framework.biz.operationlog.aspect.ApiOperationLog)")
    public void apiOperationLog() {}

    /**
     * 环绕
     * @param joinPoint
     * @return
     * @throws Throwable
     */
    @Around("apiOperationLog()")
    public Object doAround(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();
        String argsJsonStr = Arrays.stream(args).map(toJsonStr().andThen(ApiOperationLogAspect::truncate))
                .collect(Collectors.joining(", "));
        String description = getApiOperationLogDescription(joinPoint);
        log.info("====== 请求开始: [{}], 入参: {}, 请求类: {}, 请求方法: {} =================================== ",
                description, argsJsonStr, className, methodName);

        Object result = joinPoint.proceed();
        long executionTime = System.currentTimeMillis() - startTime;
        log.info("====== 请求结束: [{}], 耗时: {}ms, 出参: {} =================================== ",
                description, executionTime, truncate(JsonUtils.toJsonString(result)));

        return result;
    }

    /**
     * 获取注解的描述信息
     * @param joinPoint
     * @return
     */
    private String getApiOperationLogDescription(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        ApiOperationLog apiOperationLog = method.getAnnotation(ApiOperationLog.class);
        return apiOperationLog.description();
    }

    /**
     * 截断超长日志内容
     */
    private static String truncate(String s) {
        if (s == null || s.length() <= MAX_LOG_LENGTH) {
            return s;
        }
        return s.substring(0, MAX_LOG_LENGTH) + "...(truncated " + (s.length() - MAX_LOG_LENGTH) + " chars)";
    }

    /**
     * 转 JSON 字符串
     * @return
     */
    private Function<Object, String> toJsonStr() {
        return arg -> {
            if (arg == null) {
                return "null";
            }
            if (arg instanceof org.springframework.web.multipart.MultipartFile) {
                return "MultipartFile";
            }
            if (arg instanceof jakarta.servlet.http.HttpServletRequest) {
                return "HttpServletRequest";
            }
            if (arg instanceof jakarta.servlet.http.HttpServletResponse) {
                return "HttpServletResponse";
            }
            try {
                return JsonUtils.toJsonString(arg);
            } catch (Exception e) {
                return "UnserializableObject";
            }
        };
    }

}
