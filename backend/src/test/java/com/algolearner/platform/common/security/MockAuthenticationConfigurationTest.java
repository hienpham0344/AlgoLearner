package com.algolearner.platform.common.security;

import java.time.Clock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MockAuthenticationConfigurationTest {
    @ParameterizedTest
    @ValueSource(strings = {"", "mock", "dev", "test", "prod", "mock,dev,prod", "mock,test,production"})
    void unsignedDecoderIsDisabledOutsideExplicitLocalMode(String profiles) {
        context(profiles).run(c -> assertThat(c).doesNotHaveBean(JwtDecoder.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"mock,dev", "mock,test"})
    void explicitLocalModeEnablesDecoder(String profiles) {
        context(profiles).run(c -> assertThat(c).hasSingleBean(JwtDecoder.class));
    }

    private ApplicationContextRunner context(String profiles) {
        return new ApplicationContextRunner()
            .withInitializer(c -> c.getEnvironment().setActiveProfiles(profiles.isEmpty() ? new String[0] : profiles.split(",")))
            .withBean(AccountRepository.class, () -> mock(AccountRepository.class))
            .withBean(Clock.class, Clock::systemUTC)
            .withUserConfiguration(MockAuthenticationConfiguration.class);
    }
}
