package uk.nhs.prm.deductions.pdsadaptor.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.MessageDigestPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import uk.nhs.prm.deductions.pdsadaptor.service.ReadSSMParameter;

import java.util.Map;

@Configuration
@EnableWebSecurity
@Slf4j
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
public class SecurityConfig {

    @Value("${environment}")
    private String environment;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new MessageDigestPasswordEncoder("SHA-256");
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        log.info("setting up client auth");
        InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();

        if (!environment.equals("local") && !environment.equals("int-test")) {
            ReadSSMParameter ssmService = new ReadSSMParameter(createSsmClient());
            Map<String, String> userMap = ssmService.getApiKeys(environment);

            userMap.forEach((parameterName, apiKey) -> {
                String username = getUsernameFromParameter(parameterName);
                manager.createUser(
                    User.withUsername(username)
                        .password(passwordEncoder.encode(apiKey))
                        .roles("USER")
                        .build()
                );
            });
        } else {
            // local usage only
            manager.createUser(
                User.withUsername("admin")
                    .password(passwordEncoder.encode("admin"))
                    .roles("USER")
                    .build()
            );
        }

        log.info("completed auth setup");
        return manager;
    }

    private SsmClient createSsmClient() {
        return SsmClient.builder()
            .region(Region.EU_WEST_2)
            .build();
    }

    private String getUsernameFromParameter(String parameterName) {
        int indexOfSlashBeforeUsername = parameterName.lastIndexOf("/");
        return parameterName.substring(indexOfSlashBeforeUsername + 1);
    }

    private static final String[] AUTH_WHITELIST = {
        "/v2/api-docs",
        "/swagger-resources",
        "/swagger-resources/**",
        "/configuration/ui",
        "/configuration/security",
        "/swagger-ui.html",
        "/swagger/**",
        "/webjars/**",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/actuator/health"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.ignoringRequestMatchers("/**"))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(Customizer.withDefaults())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(AUTH_WHITELIST).permitAll()
                .anyRequest().authenticated());

        return http.build();
    }
}
