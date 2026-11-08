package com.algolearner.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.context.annotation.Bean;
import java.time.Clock;

@SpringBootApplication
@EnableJpaAuditing
public class AlgoLearnerApplication {
    public static void main(String[] args) {
        SpringApplication.run(AlgoLearnerApplication.class, args);
    }
    @Bean Clock clock() { return Clock.systemUTC(); }
}
