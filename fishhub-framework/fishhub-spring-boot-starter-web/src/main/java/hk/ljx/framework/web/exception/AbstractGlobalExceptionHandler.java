package hk.ljx.framework.web.exception;

import hk.ljx.framework.common.exception.BizException;
import hk.ljx.framework.common.response.Response;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Optional;

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
            BindException.class
    })
    @ResponseBody
    public Response<Object> handleControllerException(HttpServletRequest request, Throwable e) {
        String errorCode = paramNotValidCode();
        BindingResult bindingResult = null;
        if (e instanceof MethodArgumentNotValidException) {
            bindingResult = ((MethodArgumentNotValidException) e).getBindingResult();
        } else if (e instanceof BindException) {
            bindingResult = ((BindException) e).getBindingResult();
        }
        StringBuilder sb = new StringBuilder();
        Optional.ofNullable(bindingResult.getFieldErrors()).ifPresent(errors ->
                errors.forEach(error ->
                        sb.append(error.getField())
                                .append(" ")
                                .append(error.getDefaultMessage())
                                .append(", 当前值: '")
                                .append(error.getRejectedValue())
                                .append("'; ")
                )
        );
        String errorMessage = sb.toString();
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
