package hk.ljx.fishhub.framework.biz.operationlog.aspect;

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

    @Pointcut("@annotation(hk.ljx.fishhub.framework.biz.operationlog.aspect.ApiOperationLog)")
    public void apiOperationLog() {}

    @Around("apiOperationLog()")
    public Object doAround(ProceedingJoinPoint joinPoint) throws Throwable {
        // 请求开始时间
        long startTime = System.currentTimeMillis();
        // 获取被请求的类和方法
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();

        // 请求入参
        Object[] args = joinPoint.getArgs();
        // 入参转字符串
        Arrays.stream(args).map(toJsonStr()).collect(Collectors.joining(", "));
        // 功能描述信息
        String description = getApiOperationLogDescription(joinPoint);
        // 打印请求相关参数
        log.info("请求开始, 类名: {}, 方法名: {}, 参数: {}, 描述: {}", className, methodName, args, description);
        // 执行方法
        Object result = joinPoint.proceed();
        // 执行耗时
        long endTime = System.currentTimeMillis() - startTime;
        log.info("请求结束, 类名: {}, 方法名: {}, 耗时: {}ms", className, methodName, endTime);
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
        // 从 method 获取注释
        ApiOperationLog apiOperationLog = method.getAnnotation(ApiOperationLog.class);
        return apiOperationLog.description();

    }

    private Function<Object, String> toJsonStr() {
        return JsonUtils::toJsonString;
    }
}
