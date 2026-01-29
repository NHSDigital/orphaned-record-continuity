FROM eclipse-temurin:21-jre-alpine-3.23

RUN apk update && \
    apk -u list && \
    apk upgrade && \
    apk add --no-cache bash && \
    rm -rf /var/cache/apk/*

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY build/libs/*.jar app.jar
ENTRYPOINT ["java","-Dlog4j2.formatMsgNoLookups=true","-jar","/app.jar"]