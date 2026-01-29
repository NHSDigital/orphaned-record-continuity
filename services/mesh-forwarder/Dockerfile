FROM python:3.9-alpine3.22

RUN apk update && \
    apk -u list && \
    apk upgrade && \
    rm -rf /var/cache/apk/*

ENV POLL_FREQUENCY=60
ENV FORWARDER_HOME=/mesh-forwarder
ARG IMAGE_TAG=image_tag \
    ENV=bucket
ENV BUILD_TAG=${IMAGE_TAG}

RUN addgroup --system mesh && adduser --ingroup mesh --system mesh

# This sets the working directory for ALL subsequent RUNs and for ENTRYPOINT
WORKDIR ${FORWARDER_HOME}

# Copy project into WORKDIR (so src is at /mesh-forwarder/src)
COPY . .

RUN python -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

RUN pip install pipenv

# Install dependencies into the current env instead of creating another venv
RUN pipenv install --deploy --system

RUN chown -R mesh:mesh ${FORWARDER_HOME}
USER mesh

ENTRYPOINT ["python", "-m", "src.awsmesh.entrypoint"]
