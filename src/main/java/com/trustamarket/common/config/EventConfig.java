package com.trustamarket.common.config;

import com.trustamarket.common.util.MdcTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

// @Async / @Scheduled 활성화 + 공용 쓰레드풀 제공.
// Outbox/Inbox 스케줄러가 동작하기 위해 @EnableScheduling 필수.
@Configuration
@EnableAsync
@EnableScheduling
public class EventConfig {

    // @Async 용 공용 쓰레드풀. core 10 / max 50 / queue 100.
    // MdcTaskDecorator 로 부모 쓰레드의 MDC(X-Trace-Id 등)를 자식에 전파한다.
    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("trusta-async-");
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.initialize();
        return executor;
    }
}
