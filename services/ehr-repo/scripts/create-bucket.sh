#!/bin/bash
set -e
awslocal --endpoint-url=http://localhost:4566 s3 mb s3://test-bucket
set +e
