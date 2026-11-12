package net.linlan.emis.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一响应结果（改编自 dsh-java-ontology 的 Result）。
 *
 * @param <T> 数据类型
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {

    private int code;
    private String message;
    private T data;

    public Result() {
    }

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    public static <T> Result<T> success(String message, T data) {
        return new Result<>(200, message, data);
    }

    public static Result<Void> forbidden(String message) {
        return new Result<>(403, message, null);
    }

    public static Result<Void> unauthorized(String message) {
        return new Result<>(401, message, null);
    }

    public static Result<Void> notFound(String message) {
        return new Result<>(404, message, null);
    }

    public static <T> Result<T> fail(String message) {
        return new Result<>(500, message, null);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
