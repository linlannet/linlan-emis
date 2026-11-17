package net.linlan.emis.federated.config;

/**
 * 角色层级常量。体验角色复用既有 role_level=2 语义，不新增角色表。
 */
public final class RoleLevels {

    public static final int DIRECTOR = 0;
    public static final int RESEARCHER = 1;
    public static final int EXPERIENCE = 2;
    public static final String EXPERIENCE_NAME = "体验角色";

    private RoleLevels() {
    }
}