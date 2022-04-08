# Spring Boot Quartz Starter for spring-boot-1.5.x
[![Build Status](https://travis-ci.org/viruscamp/spring-boot-starter-quartz.svg?branch=master)](https://travis-ci.org/viruscamp/spring-boot-starter-quartz)
[![License](http://img.shields.io/:license-Apache%202-red.svg)](LICENSE.txt)

This is backport of spring-boot-starter-quartz from spring-boot-2.6.6 to spring-boot-1.5.x .  
This project is compatible for all spring-boot-1.5.x , you can change spring-boot version by change project.parent.version of pom.xml .

Main codes are from [spring-boot-autoconfigure/main/quartz](https://github.com/spring-projects/spring-boot/tree/v2.6.6/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/quartz) .

Test codes are from [spring-boot-autoconfigure/test/quartz](https://github.com/spring-projects/spring-boot/tree/v2.6.6/spring-boot-project/spring-boot-autoconfigure/src/test/java/org/springframework/boot/autoconfigure/quartz) .

For the use of `DataSourceScriptDatabaseInitializer`, codes have been backported from:
- [org.springframework.boot.util](https://github.com/spring-projects/spring-boot/tree/v2.6.6/spring-boot-project/spring-boot/src/main/java/org/springframework/boot/util)
- [org.springframework.boot.sql.init](https://github.com/spring-projects/spring-boot/tree/v2.6.6/spring-boot-project/spring-boot/src/main/java/org/springframework/boot/sql/init)
- [org.springframework.boot.jdbc.init](https://github.com/spring-projects/spring-boot/tree/v2.6.6/spring-boot-project/spring-boot/src/main/java/org/springframework/boot/jdbc/init)
- [org.springframework.boot.autoconfigure.sql.init](https://github.com/spring-projects/spring-boot/tree/v2.6.6/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/sql/init)

For the use of `QuartzDatabaseInitializer`, we made `AbstractEnhancedDatabaseInitializer` as an enhanced copy of [AbstractDatabaseInitializer](https://github.com/spring-projects/spring-boot/blob/v1.5.22.RELEASE/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/AbstractDatabaseInitializer.java).
