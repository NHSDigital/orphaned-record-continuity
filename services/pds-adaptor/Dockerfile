FROM eclipse-temurin:11-jre-alpine-3.23

RUN apk update && \
    apk -u list && \
    apk upgrade && \
    apk add --no-cache bash && \
    rm -rf /var/cache/apk/*

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY build/libs/*.jar app.jar
COPY run-application.sh ./

ENTRYPOINT ["./run-application.sh"]
