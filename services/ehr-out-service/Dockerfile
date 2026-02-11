FROM node:24-alpine3.23 AS builder

RUN apk update && \
    apk -u list && \
    apk upgrade && \
    rm -rf /var/cache/apk/*

COPY package*.json /app/

WORKDIR /app

RUN npm ci --omit=dev

# production app image
FROM alpine:3.23

# take just node without npm (including npx) or yarn
COPY --from=builder /usr/local/bin/node /usr/local/bin

# take native-install node modules
COPY --from=builder /app /app

RUN apk update && \
    apk add --no-cache bash tini aws-cli && \
    rm -rf /var/cache/apk/*


COPY build/                   /app/build

COPY scripts/run-server.sh /usr/bin/run-ehr-out-service
COPY scripts/load-api-keys.sh      /app/scripts/load-api-keys.sh

ENV NHS_ENVIRONMENT="" \
  SERVICE_URL=""

WORKDIR /app


EXPOSE 3000

ENTRYPOINT ["/sbin/tini", "--"]
CMD ["/usr/bin/run-ehr-out-service"]

RUN addgroup -g 1000 node \
    && adduser -u 1000 -G node -s /bin/sh -D node

USER node
