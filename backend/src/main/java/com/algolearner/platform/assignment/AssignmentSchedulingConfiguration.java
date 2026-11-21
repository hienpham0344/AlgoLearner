package com.algolearner.platform.assignment;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@Profile("!mock & !test")
@EnableScheduling
public class AssignmentSchedulingConfiguration { }
