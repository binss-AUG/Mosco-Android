package com.vn.jet.mosco.spinserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Cấu hình xử lý bất đồng bộ (Asynchronous Configuration) cho Server Mosco.
 * Tại sao (WHY): Sử dụng ThreadPoolTaskExecutor được giới hạn tài nguyên rõ ràng để tránh việc 
 * tạo thread vô hạn (lỗi tràn RAM OOM) khi có lưu lượng chat lớn.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "chatAsyncExecutor")
    public Executor chatAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(30);
        executor.setQueueCapacity(5000);
        executor.setThreadNamePrefix("mosco-async-");
        executor.initialize();
        return executor;
    }
}
