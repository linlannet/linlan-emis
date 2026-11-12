package net.linlan.emis;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

// 排除 OAuth2 资源服务器自动配置：本服务是授权服务器（IdP），不是资源服务器；
// spring-security-oauth2-resource-server 作为 SAS 的传递依赖会被自动配置成资源服务器，
// 导致 /oauth2/token 等端点被 BearerTokenAuthenticationFilter 拦截（401 Bearer），需排除。
@SpringBootApplication(excludeName = "org.springframework.boot.autoconfigure.security.oauth2.server.resource.servlet.OAuth2ResourceServerAutoConfiguration")
@MapperScan("net.linlan.emis.user.mapper")
public class EmisApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmisApplication.class, args);
    }

    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
