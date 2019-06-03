package org.tron.core.pbft;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tron.common.overlay.message.Message;
import org.tron.core.pbft.message.PbftBlockMessageCapsule;

@Slf4j
@Component
public class PbftMessageAction {

  private long checkPoint = 0;
  private static int count = 1;

  public void action(Message message) {
    switch (message.getType()) {
      case PBFT_BLOCK_MSG: {
        PbftBlockMessageCapsule blockMessage = (PbftBlockMessageCapsule) message;
        if (blockMessage.getPbftMessage().getRawData().getBlockNum() - checkPoint >= count) {
          checkPoint = blockMessage.getPbftMessage().getRawData().getBlockNum();
        }
      }
      break;
      default:
        break;
    }
  }

}
