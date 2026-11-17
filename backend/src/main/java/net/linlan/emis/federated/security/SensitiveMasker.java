package net.linlan.emis.federated.security;

/**
 * 敏感标识掩码：前 3 + *** + 后 3；邮箱保留首字符与域名。
 */
public final class SensitiveMasker {

    private SensitiveMasker() {
    }

    public static String maskSubject(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 6) {
            return "***";
        }
        return trimmed.substring(0, 3) + "***" + trimmed.substring(trimmed.length() - 3);
    }

    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return maskSubject(email);
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        return local.charAt(0) + "***" + domain;
    }
}