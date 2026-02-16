import { initializeConfig } from '../../config';
import { Endpoint, S3 } from 'aws-sdk';
import dayjs from 'dayjs';

const URL_EXPIRY_TIME = 60;
const CONTENT_TYPE = 'text/xml';
const config = initializeConfig();

export default class S3Service {
  constructor() {
    this.s3 = new S3(this._get_config());
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
    const params = {
      Bucket: config.awsS3BucketName,
      Key: filename,
      Body: data
    };
    return this.s3.putObject(params).promise();
  }

  getPresignedUrlWithFilename(filename, operation) {
    const params = {
      Bucket: this.Bucket,
      Key: filename,
      Expires: URL_EXPIRY_TIME
    };

    if (operation === 'putObject') {
      params.ContentType = CONTENT_TYPE;
    }

    return this.s3.getSignedUrlPromise(operation, params);
  }

  _isConnected() {
    return new Promise((resolve, reject) => {
      this.s3.headBucket(
        {
          Bucket: config.awsS3BucketName
        },
        (err) => {
          if (err) reject(err);
          resolve(true);
        }
      );
    });
  }

  _get_config() {
    if (config.nhsEnvironment === 'local') {
      return {
        accessKeyId: 'LSIA5678901234567890',
        secretAccessKey: 'LSIA5678901234567890',
        endpoint: new Endpoint(config.localstackUrl),
        s3ForcePathStyle: true
      };
    }

    return {};
  }
}
