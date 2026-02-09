from setuptools import find_packages, setup

setup(
    name="mesh-inbox-s3-forwarder",
    version="1.0.0",
    packages=find_packages(where="src"),
    package_dir={"": "src"},
    install_requires=["boto3~=1.42.44", "mesh_client~=1.4"],
)
