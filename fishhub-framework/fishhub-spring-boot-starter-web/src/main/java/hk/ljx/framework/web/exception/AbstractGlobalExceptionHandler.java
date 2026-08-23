package hk.ljx.framework.web.exception;

import hk.ljx.framework.common.exception.BizException;
import hk.ljx.framework.common.response.Response;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Servlet 服务统一异常处理基类。
 *
 * <p>各业务服务保留一个薄的 GlobalExceptionHandler 子类，继承本类的全部处理逻辑，
 * 仅通过覆盖三个 getter 提供本服务命名空间下的错误码（如 USER-10001 / NOTE-10001）。</p>
 */
@Slf4j
public abstract class AbstractGlobalExceptionHandler {

    /** 参数校验失败错误码（各服务命名空间不同，由子类提供） */
    protected abstract String paramNotValidCode();

    /** 系统异常错误码 */
    protected abstract String systemErrorCode();

    /** 系统异常错误消息 */
    protected abstract String systemErrorMessage();

    @ExceptionHandler({ BizException.class })
    @ResponseBody
    public Response<Object> handleBizException(HttpServletRequest request, BizException e) {
        log.warn("{} request fail, errorCode: {}, errorMessage: {}", request.getRequestURI(), e.getErrorCode(), e.getErrorMessage());
        return Response.fail(e);
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            jakarta.validation.ConstraintViolationException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class
    })
    @ResponseBody
    public Response<Object> handleControllerException(HttpServletRequest request, Throwable e) {
        String errorCode = paramNotValidCode();
        if (e instanceof jakarta.validation.ConstraintViolationException cve) {
            String errorMessage = cve.getMessage();
            log.warn("{} request error, errorCode: {}, errorMessage: {}", request.getRequestURI(), errorCode, errorMessage);
            return Response.fail(errorCode, errorMessage);
        }
        if (e instanceof org.springframework.http.converter.HttpMessageNotReadableException) {
            String errorMessage = "请求体 JSON 格式非法或不可解析";
            log.warn("{} request error, errorCode: {}, errorMessage: {}", request.getRequestURI(), errorCode, errorMessage);
            return Response.fail(errorCode, errorMessage);
        }
        BindingResult bindingResult = null;
        if (e instanceof MethodArgumentNotValidException) {
            bindingResult = ((MethodArgumentNotValidException) e).getBindingResult();
        } else if (e instanceof BindException) {
            bindingResult = ((BindException) e).getBindingResult();
        }
        String errorMessage = "参数校验失败";
        if (bindingResult != null && bindingResult.hasErrors()) {
            String msg = bindingResult.getFieldErrors().stream()
                    .map(err -> err.getField() + " " + err.getDefaultMessage() + ", 当前值: '" + err.getRejectedValue() + "'")
                    .collect(Collectors.joining("; "));
            if (StringUtils.isNotBlank(msg)) {
                errorMessage = msg;
            }
        }
        log.warn("{} request error, errorCode: {}, errorMessage: {}", request.getRequestURI(), errorCode, errorMessage);
        return Response.fail(errorCode, errorMessage);
    }

    @ExceptionHandler({ IllegalArgumentException.class })
    @ResponseBody
    public Response<Object> handleIllegalArgumentException(HttpServletRequest request, IllegalArgumentException e) {
        String errorCode = paramNotValidCode();
        String errorMessage = e.getMessage();
        log.warn("{} request error, errorCode: {}, errorMessage: {}", request.getRequestURI(), errorCode, errorMessage);
        return Response.fail(errorCode, errorMessage);
    }

    @ExceptionHandler({ Exception.class })
    @ResponseBody
    public Response<Object> handleOtherException(HttpServletRequest request, Exception e) {
        log.error("{} request error, ", request.getRequestURI(), e);
        return Response.fail(systemErrorCode(), systemErrorMessage());
    }
}
