package hk.ljx.fishhub.user.biz.enums;

import hk.ljx.framework.common.exception.BaseExceptionInterface;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResponseCodeEnum implements BaseExceptionInterface {

    // ----------- 通用异常状态码 -----------
    SYSTEM_ERROR("USER-10000", "出错啦，后台小哥正在努力修复中..."),
    PARAM_NOT_VALID("USER-10001", "参数错误"),

    // ----------- 用户基础异常 -----------
    NICK_NAME_VALID_FAIL("USER-20001", "昵称请设置2-24个字符，不能使用@《/等特殊字符"),
    FISHHUB_ID_VALID_FAIL("USER-20002", "飞鱼社区号请设置 6-15 个字符，必须包含英文字母，仅可使用英文字母、数字和下划线"),
    SEX_VALID_FAIL("USER-20003", "性别错误"),
    INTRODUCTION_VALID_FAIL("USER-20004", "个人简介不能超过100个字符"),
    UPLOAD_AVATAR_FAIL("USER-20005", "头像上传失败"),
    UPLOAD_BACKGROUND_IMG_FAIL("USER-20006", "背景图上传失败"),
    USER_NOT_FOUND("USER-20007", "该用户不存在"),
    CANT_UPDATE_OTHER_USER_PROFILE("USER-20008", "无权限修改他人用户信息"),

    // ----------- 认证鉴权异常 -----------
    CAPTCHA_NOT_FOUND_OR_EXPIRED("AUTH-20000", "验证码不存在或已过期，请重新获取"),
    CAPTCHA_ERROR("AUTH-20001", "验证码错误"),
    CAPTCHA_TOO_MANY_ATTEMPTS("AUTH-20002", "验证码输入错误超过10次，已自动刷新，请重新输入"),
    PHONE_ALREADY_REGISTERED("AUTH-20003", "该手机号已被注册，请直接登录"),
    PHONE_OR_PASSWORD_ERROR("AUTH-20005", "手机号或密码错误"),
    LOGIN_FAIL("AUTH-20006", "登录失败"),
    PASSWORD_UPDATE_FAIL("AUTH-20007", "密码更新失败，请稍后重试"),
    ACCOUNT_NOT_LOGINABLE("AUTH-20008", "该账号已被禁用或注销"),
    LOGIN_TOO_FREQUENT("AUTH-20009", "尝试过于频繁，请稍后再试"),
    REGISTER_FAIL("AUTH-20010", "注册失败，请稍后重试"),

    // ----------- 关注关系异常 -----------
    CANT_FOLLOW_YOUR_SELF("RELATION-20001", "无法关注自己"),
    FOLLOW_USER_NOT_EXISTED("RELATION-20002", "关注的用户不存在"),
    FOLLOWING_COUNT_LIMIT("RELATION-20003", "您关注的用户已达上限，请先取关部分用户"),
    ALREADY_FOLLOWED("RELATION-20004", "您已经关注了该用户"),
    CANT_UNFOLLOW_YOUR_SELF("RELATION-20005", "无法取关自己"),
    NOT_FOLLOWED("RELATION-20006", "你未关注对方，无法取关"),
    ;

    private final String errorCode;
    private final String errorMessage;
}
