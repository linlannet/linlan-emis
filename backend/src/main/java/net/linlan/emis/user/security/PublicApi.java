package net.linlan.emis.user.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记为公开 API，不进行角色校验（改编自 dsh-java-ontology）。
 * 未标注 @PublicApi 且未标注 @RequiresRole 的端点默认拒绝（fail-closed）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicApi {
}
