package uk.nhs.prm.deductions.pdsadaptor.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import uk.nhs.prm.deductions.pdsadaptor.client.auth.AuthService;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

@RequiredArgsConstructor
@Component
@Slf4j
public class AuthenticatingHttpClient implements HttpClient {

    private final SimpleHttpClient httpClient;
    private final AuthService authService;

    @Override
    public <T> ResponseEntity<T> get(String url, HttpHeaders headers, Class<T> responseType) {
        try {
            return httpClient.get(url, withCurrentAuthHeader(headers), responseType);
        }
        catch (HttpStatusCodeException e) {
            if (isUnauthorized(e)) {
                log.info("GET request unauthorized. Requesting new access token");
                return httpClient.get(url, withRefreshedAuthHeader(headers), responseType);
            }
            throw e;
        }
    }

    @Override
    public <T> ResponseEntity<T> patch(String url, HttpHeaders headers, Object patchPayload, Class<T> responseType) {
        try {
            return httpClient.patch(url, withCurrentAuthHeader(headers), patchPayload, responseType);
        }
        catch (HttpStatusCodeException e) {
            if (isUnauthorized(e)) {
                log.info("PATCH request unauthorized. Requesting new access token");
                return httpClient.patch(url, withRefreshedAuthHeader(headers), patchPayload, responseType);
            }
            throw e;
        }
    }

    private boolean isUnauthorized(HttpStatusCodeException e) {
        return e.getStatusCode() == HttpStatus.UNAUTHORIZED;
    }

    private HttpHeaders withRefreshedAuthHeader(HttpHeaders headers) {
        return withAuthHeader(headers, authService.getNewAccessToken());
    }

    private HttpHeaders withCurrentAuthHeader(HttpHeaders headers) {
        return withAuthHeader(headers, authService.getAccessToken());
    }

    private HttpHeaders withAuthHeader(HttpHeaders headers, String accessToken) {
        headers.remove(AUTHORIZATION);
        headers.add(AUTHORIZATION, "Bearer " + accessToken);
        return headers;
    }
}
