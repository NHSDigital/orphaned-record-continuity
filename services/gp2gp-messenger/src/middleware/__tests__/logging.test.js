import { eventFinished, logError, logInfo } from '../logging';
import { logger } from '../../config/logging';

jest.mock('../../config/logging');

describe('logging', () => {
  describe('logInfo', () => {
    it('should log with level info', () => {
      logInfo('info');

      expect(logger.info).toHaveBeenCalledTimes(1);
    });
  });

  describe('logError', () => {
    it('should log with level error', () => {
      logError('error');

      expect(logger.error).toHaveBeenCalledTimes(1);
    });
  });

  describe('eventFinished', () => {
    const mockReq = {
      headers: { host: '127.0.0.1:123' },
      method: 'GET',
      originalUrl: '/test'
    };

    it('should log path as log status', () => {
      const mockRes = {
        statusCode: 200,
        statusMessage: 'OK'
      };

      eventFinished(mockReq, mockRes);
      expect(logger.info).toHaveBeenCalledWith(mockReq.originalUrl);
    });

    it('should log with level info if status code is successful', () => {
      const mockRes = {
        statusCode: 200,
        statusMessage: 'OK'
      };

      eventFinished(mockReq, mockRes);
      expect(logger.info).toHaveBeenCalledTimes(1);
    });

    it('should log with level error if status code is not successful', () => {
      const mockRes = {
        statusCode: 500,
        statusMessage: 'Internal server error'
      };

      eventFinished(mockReq, mockRes);
      expect(logger.error).toHaveBeenCalledTimes(1);
    });
  });
});
