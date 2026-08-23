package hk.ljx.framework.biz.context.config;

import com.alibaba.ttl.TtlRunnable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@AutoConfiguration
public class TaskExecutorAutoConfiguration {

    @Bean(name = "fishhubTaskExecutor")
    public ThreadPoolTaskExecutor fishhubTaskExecutor(@Value("${spring.application.name:application}") String applicationName) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(30);
        executor.setThreadNamePrefix(applicationName + "-executor-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.setTaskDecorator(TtlRunnable::get);
        executor.initialize();
        return executor;
    }
}
