package com.example.ddorang.common.config;

import com.example.ddorang.auth.config.CustomAuthorizationRequestResolver;
import com.example.ddorang.auth.security.JwtAuthenticationFilter;
import com.example.ddorang.auth.security.JwtTokenProvider;
import com.example.ddorang.common.ApiPaths;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final OAuth2AuthorizedClientRepository authorizedClientRepository;

    public SecurityConfig(JwtTokenProvider jwtTokenProvider,
                         ClientRegistrationRepository clientRegistrationRepository,
                         OAuth2AuthorizedClientRepository authorizedClientRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.authorizedClientRepository = authorizedClientRepository;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   CustomAuthorizationRequestResolver customAuthorizationRequestResolver) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(ApiPaths.AUTH + "/**",
                                "/auth/**",  // /api/auth와 /auth 모두 허용
                                ApiPaths.OAUTH + "/**",
                                "/test/**",
                                "/api/files/**",
                                "/api/oauth2/login/success",
                                "/api/oauth2/refresh",
                                "/ws/**"
                                ).permitAll()
                        .requestMatchers("/api/settings/**",
                                "/api/teams/**", 
                                "/api/topics/**",
                                "/api/presentations/**",
                                "/api/video-analysis/**",
                                "/api/comments/**",
                                "/api/notifications/**",
                                "/api/auth/me",
                                "/api/oauth2/validate"
                                ).authenticated()
                        .anyRequest().permitAll()
                )
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(authEndpoint ->
                                authEndpoint.authorizationRequestResolver(customAuthorizationRequestResolver)
                        )
                        .defaultSuccessUrl("/api/oauth2/login/success", true)
                        .failureUrl("/api/oauth2/login/failure")
                        .clientRegistrationRepository(clientRegistrationRepository)
                        .authorizedClientRepository(authorizedClientRepository)
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                );

        return http.build();
    }

    @Bean
    public WebClient webClient() {
        ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2 =
                new ServletOAuth2AuthorizedClientExchangeFilterFunction(
                        clientRegistrationRepository,
                        authorizedClientRepository);
        oauth2.setDefaultOAuth2AuthorizedClient(true);
        return WebClient.builder()
                .apply(oauth2.oauth2Configuration())
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:3000"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setExposedHeaders(Arrays.asList("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
