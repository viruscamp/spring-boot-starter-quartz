# Spring Boot Quartz Starter for spring-boot-1.5.x
![Build Status](https://github.com/viruscamp/spring-boot-starter-quartz/actions/workflows/maven.yml/badge.svg)
[![License](http://img.shields.io/:license-Apache%202-red.svg)](LICENSE.txt)

This is backport of spring-boot-starter-quartz from spring-boot-2.6.6 to spring-boot-1.5.x .  
This project is compatible for all spring-boot-1.5.x , you can change spring-boot version by change `project.parent.version` int pom.xml .

Attentions:
- The minimal java version is 1.8 instead of 1.6.
- `spring.quartz.jdbc.comment-prefix` accepts a list of string, but only use the first one.

Main codes are from [spring-boot-autoconfigure/main/quartz](https://github.com/spring-projects/spring-boot/tree/v2.6.6/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/quartz) .

Test codes are from [spring-boot-autoconfigure/test/quartz](https://github.com/spring-projects/spring-boot/tree/v2.6.6/spring-boot-project/spring-boot-autoconfigure/src/test/java/org/springframework/boot/autoconfigure/quartz) .

For the use of `QuartzDatabaseInitializer`, we made `AbstractEnhancedDatabaseInitializer` as an enhanced copy of [AbstractDatabaseInitializer](https://github.com/spring-projects/spring-boot/blob/v1.5.22.RELEASE/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/AbstractDatabaseInitializer.java).  
This branch does not use `QuartzDataSourceScriptDatabaseInitializer extends DataSourceScriptDatabaseInitializer` which is closer to spring-boot-2.6.6.
