package org.tron.core.pbft;

import com.google.protobuf.ByteString;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.pbft.message.PbftBaseMessage;
import org.tron.core.pbft.message.PbftBlockMessage;
import org.tron.core.pbft.message.PbftSrMessage;

@Slf4j(topic = "pbft")
@Component
public class PbftManager {

  @Autowired
  private PbftMessageHandle pbftMessageHandle;

  public void blockPrePrepare(BlockCapsule block) {
    if (!pbftMessageHandle.isSyncing()) {
      doAction(PbftBlockMessage.buildPrePrepareMessage(block));
    }
  }

  public void srPrePrepare(BlockCapsule block, List<ByteString> currentWitness) {
    if (!pbftMessageHandle.isSyncing()) {
      doAction(PbftSrMessage.buildPrePrepareMessage(block, currentWitness));
    }
  }

  public void forwardMessage(PbftBaseMessage message) {
    pbftMessageHandle.forwardMessage(message);
  }

  public boolean checkIsWitnessMsg(PbftBaseMessage msg) {
    return pbftMessageHandle.checkIsWitnessMsg(msg);
  }

  public boolean doAction(PbftBaseMessage msg) {
    logger.info("receive pbft msg: {}", msg);
    switch (msg.getPbftMessage().getRawData().getPbftMsgType()) {
      case PREPREPARE:
        pbftMessageHandle.onPrePrepare(msg);
        break;
      case PREPARE:
        // prepare
        pbftMessageHandle.onPrepare(msg);
        break;
      case COMMIT:
        // commit
        pbftMessageHandle.onCommit(msg);
        break;
      case REQUEST:
        pbftMessageHandle.onRequestData(msg);
        break;
      case VIEW_CHANGE:
        pbftMessageHandle.onChangeView(msg);
        break;
      default:
        break;
    }
    return true;
  }

}
