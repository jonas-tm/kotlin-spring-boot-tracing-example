# Micrometer Tracing with Spring Boot Webflux and Kotlin

This repository contains a simple demo application showing the usage of [Micrometer Tracing](https://micrometer.io/docs/tracing).

It contains the following features:
- Spring Boot 3 Webflux
- Micrometer Tracing
- Zipkin tracing export
- Spring WebClient Observability Filter 
- Reactive logs with tracing data

## Requirements
- JVM 1.7+
- Docker

## Run Demo
1. Start Zipkin Tracer via
    - `docker run -p 9411:9411 openzipkin/zipkin`
2. Start application with
   - ```./gradlew :bootRun ```
4. Call test endpoint 
   - http://localhost:8080/test
3. Open Zipkin UI and query for traces
   - http://localhost:9411/zipkin/

![zipkin.png](docs%2Fimages%2Fzipkin.png)