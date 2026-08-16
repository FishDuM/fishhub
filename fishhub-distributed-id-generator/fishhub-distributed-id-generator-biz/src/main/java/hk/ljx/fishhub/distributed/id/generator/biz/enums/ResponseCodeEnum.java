package hk.ljx.fishhub.distributed.id.generator.biz.enums;

import hk.ljx.framework.common.exception.BaseExceptionInterface;
import lombok.AllArgsConstructor;
import lombok.Getter;


@Getter
@AllArgsConstructor
public enum ResponseCodeEnum implements BaseExceptionInterface {

    // ----------- 通用异常状态码 -----------
    SYSTEM_ERROR("ID-10000", "出错啦，后台小哥正在努力修复中..."),

    // ----------- 业务异常状态码 -----------
    ID_SERVICE_UNAVAILABLE("ID-20001", "ID 生成服务暂时不可用，请稍后重试"),
    ID_KEY_NOT_FOUND("ID-20002", "ID 业务标识缺失"),
    ID_INIT_FAILED("ID-20003", "ID 生成服务初始化失败"),
    ;

    // 异常码
    private final String errorCode;
    // 错误信息
    private final String errorMessage;

}
