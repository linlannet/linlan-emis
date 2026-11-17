package net.linlan.emis.federated.error;

/**
 * 资源不存在（404）。用于不泄露他人绑定是否存在、未知 provider 等场景。
 */
public class FederatedNotFoundException extends RuntimeException {

    public FederatedNotFoundException() {
        super("资源不存在");
    }
}