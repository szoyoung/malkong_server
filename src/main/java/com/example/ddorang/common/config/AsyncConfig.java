package com.example.ddorang.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 비동기 처리 및 스케줄링 설정
 *
 * @Async 어노테이션을 활성화하여 비동기 메서드 실행을 지원합니다.
 * @Scheduled 어노테이션을 활성화하여 주기적 작업 실행을 지원합니다.
 * VideoAnalysisService의 비동기 영상 분석에 필요
 *
 * 스레드 풀 설정:
 * - Core Pool: 5개 (기본 유지 스레드)
 * - Max Pool: 20개 (최대 스레드)
 * - Queue Capacity: 100개 (대기열)
 */
@Configuration
@EnableAsync
@EnableScheduling
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-video-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();

        log.info("비동기 스레드 풀 설정 완료: core={}, max={}, queue={}",
            executor.getCorePoolSize(),
            executor.getMaxPoolSize(),
            executor.getQueueCapacity());

        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            log.error("비동기 작업 실패: method={}, error={}",
                method.getName(), ex.getMessage(), ex);
        };
    }
}