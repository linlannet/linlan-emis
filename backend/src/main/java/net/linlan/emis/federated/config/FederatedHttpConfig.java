package net.linlan.emis.federated.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 联合登录外部调用 HTTP 客户端：有界超时，零新增依赖。
 */
@Configuration
public class FederatedHttpConfig {

    @Bean
    public RestClient federatedRestClient(FederatedProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getHttp().getConnectTimeout());
        factory.setReadTimeout(props.getHttp().getReadTimeout());
        return RestClient.builder().requestFactory(factory).build();
    }
}