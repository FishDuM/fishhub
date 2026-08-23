package hk.ljx.fishhub.user.biz.auth.enums;

import hk.ljx.framework.common.exception.BaseExceptionInterface;
import lombok.AllArgsConstructor;
import lombok.Getter;


@Getter
@AllArgsConstructor
public enum ResponseCodeEnum implements BaseExceptionInterface {

    // ----------- 通用异常状态码 -----------
    SYSTEM_ERROR("AUTH-10000", "出错啦，后台小哥正在努力修复中..."),
    PARAM_NOT_VALID("AUTH-10001", "参数错误"),

    // ----------- 业务异常状态码 -----------
    CAPTCHA_NOT_FOUND_OR_EXPIRED("AUTH-20000", "验证码不存在或已过期，请重新获取"),
    CAPTCHA_ERROR("AUTH-20001", "验证码错误"),
    CAPTCHA_TOO_MANY_ATTEMPTS("AUTH-20002", "验证码输入错误超过10次，已自动刷新，请重新输入"),
    PHONE_ALREADY_REGISTERED("AUTH-20003", "该手机号已被注册，请直接登录"),
    USER_NOT_FOUND("AUTH-20004", "该用户不存在，请先注册"),
    PHONE_OR_PASSWORD_ERROR("AUTH-20005", "手机号或密码错误"),
    LOGIN_FAIL("AUTH-20006", "登录失败"),
    PASSWORD_UPDATE_FAIL("AUTH-20007", "密码更新失败，请稍后重试"),
    ACCOUNT_NOT_LOGINABLE("AUTH-20008", "该账号已被禁用或注销"),
    LOGIN_TOO_FREQUENT("AUTH-20009", "尝试过于频繁，请稍后再试"),
    REGISTER_FAIL("AUTH-20010", "注册失败，请稍后重试"),
    ;

    // 异常码
    private final String errorCode;
    // 错误信息
    private final String errorMessage;

}
