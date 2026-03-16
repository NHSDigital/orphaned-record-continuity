import { param } from 'express-validator';
import { setCurrentSpanAttributes } from '../../config/tracing';
import { HealthRecordStatus } from '../../models/enums';
import { getConversationStatus } from '../../services/database/ehr-conversation-repository';

export const healthRecordControllerValidation = [
  param('conversationId').isUUID().withMessage("'conversationId' provided is not a UUID"),
  param('nhsNumber')
    .isNumeric()
    .withMessage("'nhsNumber' provided is not numeric")
    .isLength({ min: 10, max: 10 })
    .withMessage("'nhsNumber' provided is not 10 characters")
];

export const healthRecordController = async (req, res) => {
  try {
    setCurrentSpanAttributes({ conversationId: req.params.conversationId });

    const status = await getConversationStatus(req.params.conversationId);
    switch (status) {
      case HealthRecordStatus.COMPLETE:
        res.sendStatus(200);
        break;
      case HealthRecordStatus.PENDING:
        res.sendStatus(404);
        break;
      case HealthRecordStatus.NOT_FOUND:
        res.sendStatus(404);
        break;
    }
  } catch (err) {
    console.log(err);
    res.sendStatus(503);
  }
};
