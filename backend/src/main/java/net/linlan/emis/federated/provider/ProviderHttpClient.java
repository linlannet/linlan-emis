package net.linlan.emis.federated.provider;

import net.linlan.emis.federated.error.FederatedErrorCode;
import net.linlan.emis.federated.error.FederatedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * 第三方 HTTP 调用封装：有界超时、统一异常映射、不记录 code/token/secret。
 */
@Component
public class ProviderHttpClient {

    private static final Logger log = LoggerFactory.getLogger(ProviderHttpClient.class);

    private final RestClient restClient;

    public ProviderHttpClient(RestClient federatedRestClient) {
        this.restClient = federatedRestClient;
    }

    public String postForm(String url, Map<String, String> form, Map<String, String> headers) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        form.forEach(body::add);
        try {
            return restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .headers(h -> headers.forEach(h::set))
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException ex) {
            log.warn("third-party form request failed: {}", ex.getClass().getSimpleName());
            throw new FederatedException(FederatedErrorCode.PROVIDER_ERROR, ex);
        }
    }

    public String getJson(String url, Map<String, String> headers) {
        try {
            return restClient.get()
                    .uri(url)
                    .headers(h -> headers.forEach(h::set))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException ex) {
            log.warn("third-party get request failed: {}", ex.getClass().getSimpleName());
            throw new FederatedException(FederatedErrorCode.PROVIDER_ERROR, ex);
        }
    }
}