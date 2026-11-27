package com.algolearner.platform.judge;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class JudgeExecutionConfiguration {

    @Bean
    @Profile("mock")
    JudgeExecutionPort mockProfileJudgeExecutionPort() {
        return new MockJudgeExecutionPort();
    }

    @Bean
    @ConditionalOnProperty(name = "app.judge.provider", havingValue = "mock")
    JudgeExecutionPort mockJudgeExecutionPort() {
        return new MockJudgeExecutionPort();
    }

    @Bean
    @ConditionalOnProperty(name = "app.judge.provider", havingValue = "docker")
    JudgeExecutionPort dockerJudgeExecutionPort(
            @Value("${app.judge.docker.image-java:eclipse-temurin:21-alpine}") String imageJava,
            @Value("${app.judge.docker.image-python:python:3.11-alpine}") String imagePython,
            @Value("${app.judge.docker.command-timeout-seconds:15}") int commandTimeoutSeconds) {
        return new DockerJudgeExecutionPort(imageJava, imagePython, commandTimeoutSeconds);
    }

    /** Safe default: never falls back to executing source on the application host. */
    @Bean
    @ConditionalOnMissingBean(JudgeExecutionPort.class)
    JudgeExecutionPort failClosedJudgeExecutionPort() {
        return request -> { throw new JudgeProviderException("No isolated Judge provider is configured"); };
    }
}
