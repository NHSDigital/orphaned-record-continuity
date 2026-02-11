FROM eclipse-temurin:25.0.0_36-jre-alpine-3.23

RUN apk add --no-cache bash

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY build/libs/*.jar app.jar
ENTRYPOINT ["java","-Dlog4j2.formatMsgNoLookups=true","-jar","/app.jar"]