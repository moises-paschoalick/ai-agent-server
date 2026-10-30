package ai.agent.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ToolsConfiguration {

    /** Replaceable in tests (e.g. a fixed clock for the date/time system tool). */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
