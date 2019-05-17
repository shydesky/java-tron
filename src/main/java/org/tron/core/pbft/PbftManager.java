package org.tron.core.pbft;

import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.AtomicLongMap;
import java.security.SignatureException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.core.exception.P2pException;
import org.tron.core.pbft.message.PbftBlockMessageCapsule;

@Slf4j
@Component
public class PbftManager {

  public final static int size = 27; // 总节点数
  public final static int maxf = (size - 1) / 3; // 最大失效节点

  private volatile boolean isRun = false;

  // 消息队列
  private BlockingQueue<PbftBlockMessageCapsule> messageQueue = Queues.newLinkedBlockingQueue();
  // 预准备阶段投票信息
  private Set<String> preVotes = Sets.newConcurrentHashSet();
  // 准备阶段投票信息
  private Set<String> pareVotes = Sets.newConcurrentHashSet();
  private AtomicLongMap<String> agreePare = AtomicLongMap.create();
  // 提交阶段投票信息
  private Set<String> commitVotes = Sets.newConcurrentHashSet();
  private AtomicLongMap<String> agreeCommit = AtomicLongMap.create();
  // 成功处理过的请求
  private Map<String, PbftBlockMessageCapsule> doneMsg = Maps.newConcurrentMap();
  // 作为主节点受理过的请求
  private Map<String, PbftBlockMessageCapsule> applyMsg = Maps.newConcurrentMap();

  @Autowired
  private PbftMessageHandle pbftMessageHandle;

  public boolean doAction(PbftBlockMessageCapsule msg)
      throws SignatureException, P2pException {
    if (!isRun) {
      return false;
    }
    if (msg != null) {
      logger.info("收到消息:{}", msg);
      switch (msg.getPbftMessage().getPbftMsgType()) {
        case PP:
          pbftMessageHandle.onPrePrepare(msg);
          break;
        case PA:
          // prepare
          pbftMessageHandle.onPrepare(msg);
          break;
        case CM:
          // commit
          pbftMessageHandle.onCommit(msg);
          break;
        case REQ:
          pbftMessageHandle.onRequestData(msg);
          break;
        case CV:
          pbftMessageHandle.onChangeView(msg);
          break;
        default:
          break;
      }
      return true;
    }
    return false;
  }

}
