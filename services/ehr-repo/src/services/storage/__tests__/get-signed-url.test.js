import { getSignedUrl as presign } from '@aws-sdk/s3-request-presigner';
import getSignedUrl from '../get-signed-url';

jest.mock('@aws-sdk/s3-request-presigner', () => ({
  getSignedUrl: jest.fn()
}));

describe('getSignedUrl', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  ['getObject', 'putObject'].forEach((operation) => {
    it('should return presigned url from s3 for storing ehr', async () => {
      presign.mockResolvedValue('some-url');

      const conversationId = 'some-id';
      const messageId = 'some-message';

      const url = await getSignedUrl(conversationId, messageId, operation);

      expect(url).toEqual('some-url');
      expect(presign).toHaveBeenCalledTimes(1);

      const [client, command, options] = presign.mock.calls[0];

      expect(client).toBeDefined();
      expect(command).toBeDefined();
      expect(options).toEqual({ expiresIn: 60 });
    });
  });

  it('should normalise IDs to lowercase', async () => {
    presign.mockResolvedValue('some-url');

    const conversationId = 'SOME-ID';
    const messageId = 'SOME-MESSAGE';

    await getSignedUrl(conversationId, messageId, 'getObject');

    const [, command] = presign.mock.calls[0];

    expect(command.input).toEqual(
      expect.objectContaining({
        Key: `${conversationId.toLowerCase()}/${messageId.toLowerCase()}`
      })
    );
  });
});
