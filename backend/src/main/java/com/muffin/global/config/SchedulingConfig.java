package com.muffin.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 스프링 스케줄링 활성화. 정산 스케줄러 등 @Scheduled 배치가 동작하도록 한다. */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
