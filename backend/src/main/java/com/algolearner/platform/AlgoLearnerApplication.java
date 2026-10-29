package com.algolearner.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import java.time.Clock;

@SpringBootApplication
public class AlgoLearnerApplication {
    public static void main(String[] args) { SpringApplication.run(AlgoLearnerApplication.class, args); }
    @Bean Clock clock() { return Clock.systemUTC(); }
}
