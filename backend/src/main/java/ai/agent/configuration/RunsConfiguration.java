package ai.agent.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Run workers on virtual threads; scheduling for the run expiration job. */
@Configuration
@EnableScheduling
public class RunsConfiguration {

    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService runExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
