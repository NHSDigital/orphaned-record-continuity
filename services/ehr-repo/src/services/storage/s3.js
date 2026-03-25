import { initializeConfig } from '../../config';
import {
  S3Client,
  PutObjectCommand,
  HeadBucketCommand,
  GetObjectCommand
} from '@aws-sdk/client-s3';
import { getSignedUrl } from '@aws-sdk/s3-request-presigner';
import dayjs from 'dayjs';

const URL_EXPIRY_TIME = 60;
const CONTENT_TYPE = 'text/xml';
const config = initializeConfig();

export default class S3Service {
  constructor() {
    this.s3 = new S3Client(this._get_config());
    this.Bucket = config.awsS3BucketName;
  }

  checkS3Health() {
    const result = {
      type: 's3',
      bucketName: config.awsS3BucketName,
      available: true,
      writable: false
    };

    const date = dayjs().format('YYYY-MM-DD HH:mm:ss');

    return this._isConnected()
      .then(() =>
        this.saveObjectWithName('health-check.txt', date)
          .then(() => ({ ...result, writable: true }))
          .catch((err) => ({ ...result, error: err }))
      )
      .catch((err) => ({ ...result, error: err, available: false }));
  }

  saveObjectWithName(filename, data) {
    const command = new PutObjectCommand({
      Bucket: config.awsS3BucketName,
      Key: filename,
      Body: data
    });

    return this.s3.send(command);
  }

  getPresignedUrlWithFilename(filename, operation) {
    const params = {
      Bucket: this.Bucket,
      Key: filename,
      ...(operation === 'putObject' ? { ContentType: CONTENT_TYPE } : {})
    };

    const command =
      operation === 'putObject' ? new PutObjectCommand(params) : new GetObjectCommand(params);

    return getSignedUrl(this.s3, command, {
      expiresIn: URL_EXPIRY_TIME
    });
  }

  _isConnected() {
    const command = new HeadBucketCommand({
      Bucket: config.awsS3BucketName
    });

    return this.s3.send(command).then(() => true);
  }

  _get_config() {
    if (config.nhsEnvironment === 'local') {
      return {
        region: 'eu-west-2',
        credentials: {
          accessKeyId: 'LSIA5678901234567890',
          secretAccessKey: 'LSIA5678901234567890'
        },
        endpoint: config.localstackUrl,
        forcePathStyle: true
      };
    }

    return {
      region: process.env.AWS_REGION || 'eu-west-2'
    };
  }
}
