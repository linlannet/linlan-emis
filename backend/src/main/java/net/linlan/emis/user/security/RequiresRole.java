package net.linlan.emis.user.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色权限校验注解（改编自 dsh-java-ontology）。
 * 标注在 Controller 方法上，要求当前用户角色层级不超过指定值：0=主任 1=研究员 2=访客。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresRole {
    int value();
}
