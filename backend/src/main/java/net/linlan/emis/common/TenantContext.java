package net.linlan.emis.common;

/**
 * 租户上下文（改编自 dsh-java-ontology 的 TenantContext）。
 * 存放当前请求的 tenantId / roleLevel / userId，由 JWT 过滤器或服务间 header 注入。
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();
    private static final ThreadLocal<Integer> CURRENT_ROLE_LEVEL = new ThreadLocal<>();
    private static final ThreadLocal<Long> CURRENT_USER_ID = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantId(Long tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static Long getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void setRoleLevel(Integer roleLevel) {
        CURRENT_ROLE_LEVEL.set(roleLevel);
    }

    public static Integer getRoleLevel() {
        return CURRENT_ROLE_LEVEL.get();
    }

    public static void setUserId(Long userId) {
        CURRENT_USER_ID.set(userId);
    }

    public static Long getUserId() {
        return CURRENT_USER_ID.get();
    }

    public static boolean isDirector() {
        Integer level = CURRENT_ROLE_LEVEL.get();
        return level != null && level == 0;
    }

    public static boolean isResearcher() {
        Integer level = CURRENT_ROLE_LEVEL.get();
        return level != null && level == 1;
    }

    public static boolean isGuest() {
        Integer level = CURRENT_ROLE_LEVEL.get();
        return level != null && level == 2;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
        CURRENT_ROLE_LEVEL.remove();
        CURRENT_USER_ID.remove();
    }
}
