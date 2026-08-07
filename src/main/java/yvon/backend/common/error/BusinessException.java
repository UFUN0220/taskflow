package yvon.backend.common.error;

public class BusinessException extends RuntimeException {

    private final BusinessErrorCode errorCode;

    public BusinessException(BusinessErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public BusinessException(BusinessErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BusinessErrorCode errorCode() {
        return errorCode;
    }
}
