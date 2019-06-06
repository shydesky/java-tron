package org.tron.core.pbft;

import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.config.args.Args;
import org.tron.core.exception.BadBlockException;
import org.tron.core.pbft.message.PbftBaseMessage;
import org.tron.core.pbft.message.PbftBlockMessageCapsule;

@Slf4j(topic = "pbft")
@Component
public class PbftManager {

  public final static int agreeNodeCount = Args.getInstance().getAgreeNodeCount();

  // 消息队列
  private BlockingQueue<PbftBlockMessageCapsule> messageQueue = Queues.newLinkedBlockingQueue();
  // 作为主节点受理过的请求
  private Map<String, PbftBlockMessageCapsule> applyMsg = Maps.newConcurrentMap();

  @Autowired
  private PbftMessageHandle pbftMessageHandle;

  public void prePrepare(BlockCapsule block) {
    if (!pbftMessageHandle.isSyncing()) {
      doAction(PbftBlockMessageCapsule.buildPrePrepareMessage(block));
    }
  }

  public void forwardMessage(PbftBaseMessage message) {
    pbftMessageHandle.forwardMessage(message);
  }

  public boolean checkIsWitnessMsg(PbftBaseMessage msg) {
    return pbftMessageHandle.checkIsWitnessMsg(msg);
  }

  public boolean doAction(PbftBaseMessage msg) {
    logger.info("收到消息:{}", msg);
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
