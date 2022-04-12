# Spring Boot Quartz Starter for spring-boot-1.5.x
![Build Status](https://github.com/viruscamp/spring-boot-starter-quartz/actions/workflows/maven.yml/badge.svg)
[![License](http://img.shields.io/:license-Apache%202-red.svg)](LICENSE.txt)

This is backport of spring-boot-starter-quartz from spring-boot-2.6.6 to spring-boot-1.5.x .  
This project is compatible for all spring-boot-1.5.x , you can change spring-boot version by change `project.parent.version` int pom.xml .

Attentions:
- The minimal java version is 1.8 instead of 1.6.
- Different behavior with spring-boot-2.x, `job-store-type=jdbc` without data-source will fail
- Because there is a bug in spring-context-support-4.x `SchedulerFactoryBean`,
`spring.quartz.properties.org.quartz.scheduler.instanceName=testScheduler` is invalid,
`spring.quartz.scheduler-name` can be used as an alternative.
- In spring-boot-1.5.x, `Duration` simple format like '1m', '3s' is not supported.
Only ISO-8601 format like 'PT1M', 'P2D' is supported.

Main codes are from [spring-boot-autoconfigure/main/quartz](https://github.com/spring-projects/spring-boot/tree/v2.6.6/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/quartz) .

Test codes are from [spring-boot-autoconfigure/test/quartz](https://github.com/spring-projects/spring-boot/tree/v2.6.6/spring-boot-project/spring-boot-autoconfigure/src/test/java/org/springframework/boot/autoconfigure/quartz) .

For the use of `QuartzDatabaseInitializer`, we made `AbstractDatabaseInitializerEnhanced` as an enhanced copy of [AbstractDatabaseInitializer](https://github.com/spring-projects/spring-boot/blob/v1.5.22.RELEASE/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/AbstractDatabaseInitializer.java).  
This branch does not use `QuartzDataSourceScriptDatabaseInitializer extends DataSourceScriptDatabaseInitializer` which is closer to spring-boot-2.6.6.
