from setuptools import find_packages, setup

setup(
    name="mesh-inbox-s3-forwarder",
    version="1.0.0",
    packages=find_packages(where="src"),
    package_dir={"": "src"},
    install_requires=["boto3~=1.18", "mesh_client~=1.4", "setuptools==81.0.0"],
)
