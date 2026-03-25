import { GetObjectCommand } from '@aws-sdk/client-s3';
import { generateMultipleUUID } from '../../../utilities/integration-test-utilities';
import getSignedUrl from '../get-signed-url';
import { v4 as uuidv4 } from 'uuid';
import S3Service from '../s3';
import axios from 'axios';

jest.mock('../../../middleware/logging');

describe('S3Service integration test with localstack', () => {
  const S3CLIENT = new S3Service();

  const streamToString = async (body) => {
    if (typeof body?.transformToString === 'function') {
      return body.transformToString();
    }

    const chunks = [];
    for await (const chunk of body) {
      chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
    }
    return Buffer.concat(chunks).toString('utf-8');
  };

  const getObjectByName = async (filename) => {
    const getObjectParams = {
      Bucket: S3CLIENT.Bucket,
      Key: filename
    };

    return S3CLIENT.s3.send(new GetObjectCommand(getObjectParams));
  };

  describe('saveObjectWithName', () => {
    it('can save an object in the S3 bucket', async () => {
      const testFileName = `${uuidv4()}/${uuidv4()}`;
      const testData = 'Lorem ipsum dolor sit amet';

      await S3CLIENT.saveObjectWithName(testFileName, testData);

      const objectInBucket = await getObjectByName(testFileName);
      const objectContent = await streamToString(objectInBucket.Body);

      expect(objectContent).toEqual(testData);
    });
  });

  describe('getSignedUrl', () => {
    const testEhrCore = {
      ebXML: '<soap:Envelope><content>ebXML</content></soap:Envelope>',
      payload: '<RCMR_IN030000UK06>payload</<RCMR_IN030000UK06>',
      attachments: [],
      external_attachments: []
    };

    it('return a presigned url for upload when operation = putObject', async () => {
      const [conversationId, messageId] = generateMultipleUUID(2);
      const testFileName = `${conversationId.toLowerCase()}/${messageId.toLowerCase()}`;
      const operation = 'putObject';
      const body = JSON.stringify(testEhrCore);

      const presignedUrl = await getSignedUrl(conversationId, messageId, operation);
      const response = await axios.put(presignedUrl, body, {
        headers: {
          'Content-Type': 'text/xml'
        }
      });

      expect(response.status).toBe(200);

      const objectInBucket = await getObjectByName(testFileName);
      const objectContent = await streamToString(objectInBucket.Body);

      expect(objectContent).toEqual(body);
    });

    it('return a presigned url for download when operation = getObject', async () => {
      const [conversationId, messageId] = generateMultipleUUID(2);
      const testFileName = `${conversationId.toLowerCase()}/${messageId.toLowerCase()}`;
      const operation = 'getObject';
      const body = JSON.stringify(testEhrCore);

      await S3CLIENT.saveObjectWithName(testFileName, body);

      const presignedUrl = await getSignedUrl(conversationId, messageId, operation);
      const response = await axios.get(presignedUrl);

      expect(response.status).toBe(200);
      expect(response.data).toEqual(testEhrCore);
    });
  });
});
