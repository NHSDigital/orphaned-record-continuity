import logging
from os import environ
from os.path import join
from signal import SIGINT, SIGTERM, signal

import boto3
import urllib3

from awsmesh.config import ForwarderConfig
from awsmesh.forwarder_service import MeshConfig, build_forwarder_service
from awsmesh.logging import JsonFormatter
from awsmesh.message_destination_resolver import MessageDestinationConfig
from awsmesh.secrets import SsmSecretManager


def build_mesh_config_from_ssm(ssm, config) -> MeshConfig:
    mesh_client_cert_path = join(config.forwarder_home, "client_cert.pem")
    mesh_client_key_path = join(config.forwarder_home, "client_key.pem")
    mesh_ca_cert_path = join(config.forwarder_home, "ca_cert.pem")

    secret_manager = SsmSecretManager(ssm)

    secret_manager.download_secret(config.mesh_client_cert_ssm_param_name, mesh_client_cert_path)
    secret_manager.download_secret(config.mesh_client_key_ssm_param_name, mesh_client_key_path)
    secret_manager.download_secret(config.mesh_ca_cert_ssm_param_name, mesh_ca_cert_path)

    mesh_mailbox = secret_manager.get_secret(config.mesh_mailbox_ssm_param_name)
    mesh_password = secret_manager.get_secret(config.mesh_password_ssm_param_name)
    mesh_shared_key = secret_manager.get_secret(config.mesh_shared_key_ssm_param_name)

    return MeshConfig(
        url=config.mesh_url,
        mailbox=mesh_mailbox,
        password=mesh_password,
        shared_key=bytes(mesh_shared_key, "utf-8"),
        client_cert_path=mesh_client_cert_path,
        client_key_path=mesh_client_key_path,
        ca_cert_path=mesh_ca_cert_path,
    )


def build_message_destination_config(config) -> MessageDestinationConfig:
    return MessageDestinationConfig(
        message_destination=config.message_destination,
        s3_bucket_name=config.s3_bucket_name,
        endpoint_url=config.endpoint_url,
        sns_topic_arn=config.sns_topic_arn,
    )


def build_forwarder_from_environment_variables(env_vars=environ):
    config = ForwarderConfig.from_environment_variables(env_vars)
    ssm = boto3.client("ssm", endpoint_url=config.ssm_endpoint_url)

    return build_forwarder_service(
        mesh_config=build_mesh_config_from_ssm(ssm, config),
        message_destination_config=build_message_destination_config(config),
        poll_frequency_sec=int(config.poll_frequency),
        disable_message_header_validation=config.disable_message_header_validation,
    )


def setup_logger():
    logger = logging.getLogger()
    logger.setLevel(logging.INFO)
    formatter = JsonFormatter()
    handler = logging.StreamHandler()
    handler.setFormatter(formatter)
    logger.addHandler(handler)


def main():
    setup_logger()

    forwarder_service = build_forwarder_from_environment_variables()

    def handle_sigterm(signum, frame):
        forwarder_service.stop()

    signal(SIGINT, handle_sigterm)
    signal(SIGTERM, handle_sigterm)

    forwarder_service.start()


if __name__ == "__main__":
    main()
