package uk.nhs.prm.repo.suspension.service.http;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import uk.nhs.prm.repo.suspension.service.config.Tracer;

import java.util.Arrays;

@Component
@AllArgsConstructor
@Slf4j
public class HttpServiceClient {

    private RestTemplate restTemplate;
    private final Tracer tracer;

    public ResponseEntity<String> getWithStatusCode (String url, String username, String password) {
        var requestEntity = new HttpEntity<>(createSharedHeaders(username, password));
        return exchangeWithStatusCode(HttpMethod.GET, url, requestEntity);
    }

    public ResponseEntity<String> putWithStatusCode(String url, String username, String password, Object requestPayload) {
        var requestEntity = new HttpEntity<>(requestPayload, createSharedHeaders(username, password));
        return exchangeWithStatusCode(HttpMethod.PUT, url, requestEntity);
    }

    private ResponseEntity<String> exchangeWithStatusCode(HttpMethod method, String url, HttpEntity requestEntity) {
        ResponseEntity<String> responseEntity = restTemplate.exchange(url, method, requestEntity, String.class);
        return responseEntity;
    }

    private HttpHeaders createSharedHeaders(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.setBasicAuth(username, password);
        headers.add("traceId", tracer.getTraceId());
        return headers;
    }
}
