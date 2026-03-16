import { validationResult } from 'express-validator';
import { logError, logInfo } from './logging';

export const validate = (req, res, next) => {
  const errors = validationResult(req);
  if (errors.isEmpty()) {
    logInfo('validation-passed');
    return next();
  }

  const mappedErrors = errors.array().map(err => ({ [err.path]: err.msg }));
  logError('validation-failed', { validation: { errors: mappedErrors } });

  return res.status(422).json({
    errors: mappedErrors
  });
};
