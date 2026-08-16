package hk.ljx.fishhub.distributed.id.generator.biz.exception;

import hk.ljx.framework.common.response.Response;
import hk.ljx.fishhub.distributed.id.generator.biz.enums.ResponseCodeEnum;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


/**
 * 全局异常处理器。
 *
 * <p>号段缓冲耗尽或数据库不可用时 Leaf 以 {@link LeafServerException} 暴露，
 * 对调用方而言属于"服务暂时不可用"，HTTP 状态使用 503（可重试语义），
 * 与其余微服务统一返回 {@link Response} 结构。
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 服务不可用：号段/雪花内部异常、初始化失败。503 提示调用方可稍后重试。
     */
    @ExceptionHandler({ LeafServerException.class, InitException.class })
    public ResponseEntity<Response<Object>> handleUnavailableException(HttpServletRequest request, Exception e) {
        ResponseCodeEnum code = e instanceof InitException
                ? ResponseCodeEnum.ID_INIT_FAILED
                : ResponseCodeEnum.ID_SERVICE_UNAVAILABLE;
        log.error("{} ID 生成服务不可用, errorCode: {}", request.getRequestURI(), code.getErrorCode(), e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Response.fail(code));
    }

    /**
     * 业务标识缺失：属于调用方参数问题。
     */
    @ExceptionHandler({ NoKeyException.class })
    public ResponseEntity<Response<Object>> handleNoKeyException(HttpServletRequest request, NoKeyException e) {
        log.warn("{} 请求缺少业务 key", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Response.fail(ResponseCodeEnum.ID_KEY_NOT_FOUND));
    }

    /**
     * 其他异常兜底。
     */
    @ExceptionHandler({ Exception.class })
    public ResponseEntity<Response<Object>> handleOtherException(HttpServletRequest request, Exception e) {
        log.error("{} request error, ", request.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Response.fail(ResponseCodeEnum.SYSTEM_ERROR));
    }
}
