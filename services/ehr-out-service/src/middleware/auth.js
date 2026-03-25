import { config } from '../config';
import { logInfo, logWarning } from './logging';

export const authenticateRequest = (req, res, next) => {
  const { consumerApiKeys } = config();
  if (Object.keys(consumerApiKeys).length === 0) {
    res.status(412).json({
      error: `Server-side Authorization keys have not been set, cannot authenticate`
    });
    return;
  }
  const validAuthorizationKeys = Object.values(consumerApiKeys);

  const authorizationKey = req.get('Authorization');

  if (!authorizationKey) {
    res.status(401).json({
      error: `The request (${req.baseUrl}) requires a valid Authorization header to be set`
    });
    return;
  }

  if (!validAuthorizationKeys.includes(authorizationKey)) {
    logWarning(`Unsuccessful Request: ${req.method} ${req.originalUrl}, Invalid API Key presented`);
    res.status(403).json({
      error: `Authorization header is provided but not valid`
    });
    return;
  }

  const consumerName = getConsumer(consumerApiKeys, authorizationKey);
  logInfo(`Consumer: ${consumerName}, Request: ${req.method} ${req.originalUrl}`);

  next();
};

const getConsumer = (consumerApiKeys, authorizationKey) => {
  const consumer = Object.keys(consumerApiKeys).filter(
    consumer => consumerApiKeys[consumer] === authorizationKey
  );
  return consumer.toString().replace(/,/g, '/');
};
