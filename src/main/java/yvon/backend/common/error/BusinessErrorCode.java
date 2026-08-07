package yvon.backend.common.error;

public enum BusinessErrorCode {
    SUCCESS("0", "success"),
    INVALID_PARAMETER("COMMON_400", "请求参数校验失败"),
    UNAUTHORIZED("COMMON_401", "未认证"),
    FORBIDDEN("COMMON_403", "无权限"),
    RESOURCE_NOT_FOUND("COMMON_404", "资源不存在"),
    CONFLICT("COMMON_409", "资源状态冲突"),
    BUSINESS_ERROR("BUSINESS_400", "业务处理失败"),
    INTERNAL_ERROR("COMMON_500", "系统内部错误");

    private final String code;
    private final String defaultMessage;

    BusinessErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
