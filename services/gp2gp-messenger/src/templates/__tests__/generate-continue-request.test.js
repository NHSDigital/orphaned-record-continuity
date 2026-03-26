import { v4 as uuid } from 'uuid';
import testData from './testData.json';
import dateFormat from 'dateformat';
import { generateContinueRequest } from '../generate-continue-request';
import { initializeConfig } from '../../config';

describe('generateContinueRequest', () => {
  const messageId = uuid().toUpperCase();
  const receivingAsid = testData.emisPractise.asid;
  const sendingAsid = testData.mhs.asid;
  const ehrExtractMessageId = uuid().toUpperCase();
  const gpOdsCode = testData.emisPractise.odsCode;

  it('should return the continue request template with correct values', () => {
    const timestamp = dateFormat(Date.now(), 'yyyymmddHHMMss');
    const repoOdsCode = initializeConfig().repoOdsCode;
    const returnValue = generateContinueRequest({
      messageId,
      receivingAsid,
      sendingAsid,
      ehrExtractMessageId,
      gpOdsCode
    });

    expect(returnValue).toContain(messageId);
    expect(returnValue).toContain(receivingAsid);
    expect(returnValue).toContain(sendingAsid);
    expect(returnValue).toContain(ehrExtractMessageId);
    expect(returnValue.toUpperCase()).toContain(gpOdsCode.toUpperCase());
    expect(returnValue).toContain(repoOdsCode);
    expect(returnValue).toContain(timestamp);
  });

  it('should upper case the GP ODS code', () => {
    const returnValue = generateContinueRequest({
      messageId,
      receivingAsid,
      sendingAsid,
      ehrExtractMessageId,
      gpOdsCode
    });
    expect(returnValue).toContain('N82668');
  });

  describe('failure', () => {
    it('should throw error when messageId is not defined in inputObject', () => {
      const errorMessage = new Error('Check template parameter error: id is undefined');
      const inputValues = {
        receivingAsid,
        sendingAsid,
        ehrExtractMessageId,
        gpOdsCode
      };
      expect(() => generateContinueRequest(inputValues)).toThrow(errorMessage);
    });

    it('should throw error when receivingAsid is not defined in inputObject', () => {
      const errorMessage = new Error('Check template parameter error: receivingAsid is undefined');
      const inputValues = {
        messageId,
        sendingAsid,
        ehrExtractMessageId,
        gpOdsCode
      };
      expect(() => generateContinueRequest(inputValues)).toThrow(errorMessage);
    });

    it('should throw error when sendingAsid is not defined in inputObject', () => {
      const errorMessage = new Error('Check template parameter error: sendingAsid is undefined');
      const inputValues = {
        messageId,
        receivingAsid,
        ehrExtractMessageId,
        gpOdsCode
      };
      expect(() => generateContinueRequest(inputValues)).toThrow(errorMessage);
    });

    it('should throw error when ehrExtractMessageId is not defined in inputObject', () => {
      const errorMessage = new Error(
        'Check template parameter error: ehrExtractMessageId is undefined'
      );
      const inputValues = {
        messageId,
        receivingAsid,
        sendingAsid,
        gpOdsCode
      };
      expect(() => generateContinueRequest(inputValues)).toThrow(errorMessage);
    });

    it('should throw error when gpOdsCode is not defined in inputObject', () => {
      const errorMessage = new Error('Check template parameter error: gpOdsCode is undefined');
      const inputValues = {
        messageId,
        receivingAsid,
        sendingAsid,
        ehrExtractMessageId
      };
      expect(() => generateContinueRequest(inputValues)).toThrow(errorMessage);
    });
  });
});
