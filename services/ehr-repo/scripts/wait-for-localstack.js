const { Endpoint, S3 } = require('aws-sdk');

const MAX_ATTEMPTS = 30;

const isConnected = () => {
  return new Promise((resolve) => {
    const s3 = new S3({
      accessKeyId: 'LSIA5678901234567890',
      secretAccessKey: 'LSIA5678901234567890',
      endpoint: new Endpoint(process.env.LOCALSTACK_URL),
      s3ForcePathStyle: true
    });

    s3.headBucket(
      {
        Bucket: process.env.S3_BUCKET_NAME
      },
      (err) => {
        if (err) resolve(false);
        resolve(true);
      }
    );
  });
};

function sleep(ms) {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

const checkIfConnected = async () => {
  for (let count = 0; count <= MAX_ATTEMPTS; ++count) {
    console.log(
      `Attempting to connect to bucket in localstack (Attempt ${count} of ${MAX_ATTEMPTS})`
    );

    const result = await isConnected();

    if (result) {
      console.log('Successfully connected to localstack');
      return;
    }
    await sleep(2000);
  }

  console.log(`Connection to Localstack timedout after ${MAX_ATTEMPTS}`);
  throw new Error(`Connection to Localstack timedout after ${MAX_ATTEMPTS}`);
};

checkIfConnected();
