package com.algolearner.platform.admin;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@Profile("internal-auth")
@EnableScheduling
public class AccountSchedulingConfiguration { }
