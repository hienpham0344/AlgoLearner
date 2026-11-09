package com.algolearner.platform.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@Profile("!mock | (mock & integration)")
@EnableJpaAuditing
public class JpaAuditingConfiguration {
}
