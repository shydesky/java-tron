package org.tron.core.pbft;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tron.common.overlay.message.Message;

@Slf4j
@Component
public class PbftMessageAction {

  public void action(Message message) {
    switch (message.getType()) {
      case PBFT_BLOCK_MSG: {

      }
      break;
      default:
        break;
    }
  }

}
