package net.linlan.emis.federated.error;

/**
 * 联合登录业务异常：只携带业务码、HTTP 状态与固定文案，不携带第三方原始响应。
 */
public class FederatedException extends RuntimeException {

    private final int code;
    private final int httpStatus;

    public FederatedException(FederatedErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
    }

    public FederatedException(FederatedErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
    }

    public FederatedException(FederatedErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
    }

    public int getCode() { return code; }
    public int getHttpStatus() { return httpStatus; }
}