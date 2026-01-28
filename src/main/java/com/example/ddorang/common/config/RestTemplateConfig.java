package com.example.ddorang.common.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.HttpRequest;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.Collections;

import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        // 큰 파일 업로드를 위해 타임아웃 증가
        // connectTimeout: 연결 타임아웃 (60초) - 연결 설정 시간
        // readTimeout: 읽기 타임아웃 (30분) - 큰 파일 업로드 및 FastAPI 처리 시간 고려
        RestTemplate restTemplate = builder
                .connectTimeout(Duration.ofSeconds(60))
                .readTimeout(Duration.ofMinutes(30))
                .build();

        restTemplate.getMessageConverters().forEach(converter -> {
            if (converter instanceof StringHttpMessageConverter) {
                ((StringHttpMessageConverter) converter).setDefaultCharset(StandardCharsets.UTF_8);
            }
        });

        restTemplate.setInterceptors(Collections.singletonList(new ClientHttpRequestInterceptor() {
            @Override
            public ClientHttpResponse intercept(
                    HttpRequest request, 
                    byte[] body, 
                    ClientHttpRequestExecution execution) throws IOException {
                
                log.info("=== RestTemplate 요청 로그 (수정된 설정) ===");
                log.info("URI: {}", request.getURI());
                log.info("Method: {}", request.getMethod());
                log.info("Headers: {}", request.getHeaders());

                // Content-Type이 null이 아니고 multipart가 아닐 때만 body 로깅
                MediaType contentType = request.getHeaders().getContentType();
                if (contentType == null || !contentType.includes(MediaType.MULTIPART_FORM_DATA)) {
                    if (body.length > 0 && body.length < 2048) {
                        log.info("Body preview (first {} bytes): {}", Math.min(body.length, 64), new String(body, 0, Math.min(body.length, 64), StandardCharsets.UTF_8));
                    }
                }
                log.info("Full Body length: {} bytes", body.length);
                
                // 요청 전송 시작 시간 기록
                long startTime = System.currentTimeMillis();
                log.info("요청 전송 시작 시간: {}", new java.util.Date(startTime));
                
                ClientHttpResponse response = execution.execute(request, body);
                
                // 응답 수신 시간 기록
                long endTime = System.currentTimeMillis();
                long duration = endTime - startTime;
                log.info("요청 완료 시간: {} (소요 시간: {}초)", new java.util.Date(endTime), duration / 1000.0);
                
                log.info("=== RestTemplate 응답 로그 (수정된 설정) ===");
                log.info("Status: {}", response.getStatusCode());
                log.info("Response Headers: {}", response.getHeaders());
                
                return response;
            }
        }));

        return restTemplate;
    }
} 