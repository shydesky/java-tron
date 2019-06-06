package org.tron.core.pbft;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tron.common.overlay.message.Message;
import org.tron.core.config.args.Args;
import org.tron.core.pbft.message.PbftBlockMessageCapsule;

@Slf4j(topic = "pbft")
@Component
public class PbftMessageAction {

  private long checkPoint = 0;
  private static final int count = Args.getInstance().getCheckMsgCount();

  public void action(Message message) {
    switch (message.getType()) {
      case PBFT_BLOCK_MSG: {
        PbftBlockMessageCapsule blockMessage = (PbftBlockMessageCapsule) message;
        long blockNum = blockMessage.getPbftMessage().getRawData().getBlockNum();
        if (blockNum - checkPoint >= count) {
          checkPoint = blockNum;
          logger.info("commit msg block num is:{}", blockNum);
        }
      }
      break;
      default:
        break;
    }
  }

}
