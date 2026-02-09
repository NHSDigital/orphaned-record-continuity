import request from 'supertest';
import app from '../app';

jest.mock('../middleware/logging');
jest.mock('../middleware/auth');

describe('app', () => {
  describe('GET /', () => {
    it('should return a 404 status code', (done) => {
      request(app).get('/').expect(404).end(done);
    });
  });

  describe('GET /any-text - an unspecified endpoint', () => {
    it('should return a 404 status code', (done) => {
      request(app).get('/any-text').expect(404).end(done);
    });
  });

  describe('Swagger Documentation', () => {
    it('GET /swagger - should return a 301 status code (redirect) and text/html content type response', (done) => {
      request(app)
        .get('/swagger')
        .expect(301)
        .expect('Content-Type', 'text/html; charset=UTF-8')
        .end(done);
    });

    it('GET /swagger/index.html - should return a 200 status code and text/html content type response', (done) => {
      request(app)
        .get('/swagger/index.html')
        .expect(200)
        .expect('Content-Type', 'text/html; charset=utf-8')
        .end(done);
    });
  });
});
