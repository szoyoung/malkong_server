package com.example.ddorang.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:3000")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true); // 쿠키를 사용할 경우 true
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // API 경로는 정적 리소스 핸들러에서 제외
        // 정적 리소스는 특정 경로에만 매핑
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
    }
}