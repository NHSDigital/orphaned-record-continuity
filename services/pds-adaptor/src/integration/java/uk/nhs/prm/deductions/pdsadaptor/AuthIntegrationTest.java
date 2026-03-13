package uk.nhs.prm.deductions.pdsadaptor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AuthIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @Test
    void shouldPassWithActuatorCall(){
        ResponseEntity<String> response = restTemplate.exchange(
                createURLWithPort("/actuator/health"), HttpMethod.GET, null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldPassWithSwaggerCall(){
        ResponseEntity<String> response = restTemplate.exchange(
                createURLWithPort("/swagger-ui/index.html?configUrl=/v3/api-docs/swagger-config"), HttpMethod.GET, null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldGetUnauthorizedIfHeaderMissingAuth(){
        HttpHeaders headers = new HttpHeaders();
        headers.set("traceId", "fake-trace-id");

        ResponseEntity<String> response = restTemplate.exchange(
                createURLWithPort("/suspended-patient-status/123"), HttpMethod.GET, new HttpEntity<String>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String createURLWithPort(String uri) {
        return "http://localhost:" + port + uri;
    }
}
