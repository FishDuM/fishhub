package hk.ljx.fishhub.user.relation.biz.exception;

import hk.ljx.framework.web.exception.AbstractGlobalExceptionHandler;
import hk.ljx.fishhub.user.relation.biz.enums.ResponseCodeEnum;
import org.springframework.web.bind.annotation.ControllerAdvice;

/** 全局异常处理器：继承框架通用实现，仅提供本服务命名空间的错误码。 */
@ControllerAdvice
public class GlobalExceptionHandler extends AbstractGlobalExceptionHandler {
    @Override
    protected String paramNotValidCode() { return ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(); }
    @Override
    protected String systemErrorCode() { return ResponseCodeEnum.SYSTEM_ERROR.getErrorCode(); }
    @Override
    protected String systemErrorMessage() { return ResponseCodeEnum.SYSTEM_ERROR.getErrorMessage(); }
}
