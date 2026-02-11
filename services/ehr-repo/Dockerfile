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

# install python native requirements (again, as per builder)
# add root CA from deductions team to trusted certificates

# TODO PRM-225 - Rip out Tini, hasn't been updated since 2020

RUN apk update && \
    apk -u list && \
    apk upgrade &&  \
    apk add --no-cache openssl ca-certificates bash tini aws-cli && \
    rm -rf /var/cache/apk/*

COPY build/                   /app/build

COPY scripts/load-api-keys.sh      /app/scripts/load-api-keys.sh
COPY scripts/run-server.sh /usr/bin/run-ehr-server

COPY ./certs/deductions.crt /usr/local/share/ca-certificates/deductions.crt
RUN update-ca-certificates

ENV NODE_EXTRA_CA_CERTS=/usr/local/share/ca-certificates/deductions.crt

ENV AUTHORIZATION_KEYS="auth-key-1" \
  NODE_ENV="prod" \
  NHS_ENVIRONMENT="" \
  S3_BUCKET_NAME="" \
  LOCALSTACK_URL=""

WORKDIR /app

EXPOSE 3000

ENTRYPOINT ["/sbin/tini", "--"]
CMD ["/usr/bin/run-ehr-server"]

RUN addgroup -g 1000 node \
    && adduser -u 1000 -G node -s /bin/sh -D node

USER node
