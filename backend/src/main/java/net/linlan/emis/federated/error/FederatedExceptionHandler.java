package net.linlan.emis.federated.error;

import net.linlan.emis.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 联合登录异常处理器：业务码 → HTTP + Result(code, message, null)。
 * 不输出堆栈、第三方原始响应、密钥或令牌。
 */
@RestControllerAdvice
public class FederatedExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(FederatedExceptionHandler.class);

    @ExceptionHandler(FederatedException.class)
    public ResponseEntity<Result<Void>> handle(FederatedException ex) {
        log.warn("federated request rejected code={} message={}", ex.getCode(), ex.getMessage());
        return ResponseEntity.status(ex.getHttpStatus())
                .body(new Result<>(ex.getCode(), ex.getMessage(), null));
    }

    @ExceptionHandler(FederatedNotFoundException.class)
    public ResponseEntity<Result<Void>> handleNotFound(FederatedNotFoundException ex) {
        return ResponseEntity.status(404).body(Result.notFound(ex.getMessage()));
    }
}