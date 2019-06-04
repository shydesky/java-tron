package org.tron.core.pbft;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.AtomicLongMap;
import java.security.SignatureException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.tron.common.overlay.server.SyncPool;
import org.tron.core.config.args.Args;
import org.tron.core.pbft.message.PbftBaseMessage;
import org.tron.core.pbft.message.PbftBlockMessageCapsule;

@Slf4j
@Component
public class PbftMessageHandle {

  private long blockNum;

  // 预准备阶段投票信息
  private Set<String> preVotes = Sets.newConcurrentHashSet();
  // 准备阶段投票信息
  private Set<String> pareVotes = Sets.newConcurrentHashSet();
  private AtomicLongMap<String> agreePare = AtomicLongMap.create();
  // 提交阶段投票信息
  private Set<String> commitVotes = Sets.newConcurrentHashSet();
  private AtomicLongMap<String> agreeCommit = AtomicLongMap.create();

  // pbft超时
  private Map<String, Long> timeOuts = Maps.newConcurrentMap();
  // 请求超时，view加1，重试
  private Map<String, Long> timeOutsReq = Maps.newHashMap();

  // 成功处理过的请求
  private Map<String, PbftBaseMessage> doneMsg = Maps.newConcurrentMap();

  private Timer timer;

  private SyncPool syncPool;
  @Autowired
  private PbftMessageAction pbftMessageAction;
  @Autowired
  private ApplicationContext ctx;

  public void init() {
    syncPool = ctx.getBean(SyncPool.class);
    start();
  }

  public void onPrePrepare(PbftBaseMessage message) {
    if (!checkIsCanSendPrePrepareMsg()) {
      return;
    }
    String key = message.getNo();
    if (preVotes.contains(key)) {
      // 说明已经发起过，不能重复发起，同一高度只能发起一次投票
      return;
    }
    preVotes.add(key);
    // 启动超时控制
    timeOuts.put(key, System.currentTimeMillis());
    // 进入准备阶段
    PbftBaseMessage paMessage = PbftBlockMessageCapsule.buildPrePareMessage(message);
    syncPool.getActivePeers().forEach(peerConnection -> {
      peerConnection.sendMessage(paMessage);
    });
  }

  public void onPrepare(PbftBaseMessage message) throws SignatureException {
    if (!checkMsg(message)) {
      logger.info("异常消息:{}", message);
      return;
    }

    String key = message.getKey();

    if (!preVotes.contains(message.getNo())) {
      // 必须先过预准备
      return;
    }
    if (pareVotes.contains(key)) {
      // 说明已经投过票，不能重复投
      return;
    }
    pareVotes.add(key);

    // 票数 +1
    long agCou = agreePare.incrementAndGet(message.getDataKey());
    if (agCou >= PbftManager.agreeNodeCount) {
      pareVotes.remove(key);
      // 进入提交阶段
      PbftBaseMessage cmMessage = PbftBlockMessageCapsule.buildCommitMessage(message);
      doneMsg.put(message.getNo(), cmMessage);
      syncPool.getActivePeers().forEach(peerConnection -> {
        peerConnection.sendMessage(cmMessage);
      });
    }
    // 后续的票数肯定凑不满，超时自动清除
  }

  public void onCommit(PbftBaseMessage message) throws SignatureException {
    if (!checkMsg(message)) {
      return;
    }
    // data模拟数据摘要
    String key = message.getKey();
    if (!pareVotes.contains(key)) {
      // 必须先过准备
      return;
    }
    if (commitVotes.contains(key)) {
      // 说明该节点对该项数据已经投过票，不能重复投
      return;
    }
    commitVotes.add(key);
    // 票数 +1
    long agCou = agreeCommit.incrementAndGet(message.getDataKey());
    if (agCou >= PbftManager.agreeNodeCount) {
      remove(message.getNo());
      //commit,
      pbftMessageAction.action(message);
    }
  }

  public void onRequestData(PbftBaseMessage message) {

  }

  public void onChangeView(PbftBaseMessage message) {

  }

  public boolean checkMsg(PbftBaseMessage msg)
      throws SignatureException {
    return (msg.getPbftMessage().getRawData().getBlockNum() == blockNum) && msg
        .validateSignature(msg);
  }

  public boolean checkIsCanSendPrePrepareMsg() {
    AtomicBoolean result = new AtomicBoolean(true);
    if (!Args.getInstance().isWitness()) {
      return !result.get();
    }
    //todo:check current node is witness node

    syncPool.getActivePeers().forEach(peerConnection -> {
      if (peerConnection.isNeedSyncFromPeer()) {
        result.set(false);
        return;
      }
    });
    return result.get();
  }

  // 清理请求相关状态
  private void remove(String no) {
    String pre = String.valueOf(no) + "_";
    preVotes.remove(no);
    pareVotes.removeIf((vp) -> StringUtils.startsWith(vp, pre));
    commitVotes.removeIf((vp) -> StringUtils.startsWith(vp, pre));

    agreePare.asMap().keySet().forEach(s -> {
      if (StringUtils.startsWith(s, pre)) {
        agreePare.remove(s);
      }
    });
    agreeCommit.asMap().keySet().forEach(s -> {
      if (StringUtils.startsWith(s, pre)) {
        agreeCommit.remove(s);
      }
    });
    timeOuts.remove(no);
  }

  /**
   * 检测超时情况
   */
  private void checkTimer() {
    List<String> remo = Lists.newArrayList();
    for (Entry<String, Long> item : timeOuts.entrySet()) {
      if (System.currentTimeMillis() - item.getValue() > 3000) {
        // 超时还没达成一致，则本次投票无效
        logger.info("投票无效:{}", item.getKey());
        remo.add(item.getKey());
      }
    }
    remo.forEach((it) -> {
      remove(it);
    });
  }

  public void start() {
    timer = new Timer("pbft-timer");
    timer.schedule(new TimerTask() {
      @Override
      public void run() {
        checkTimer();
      }
    }, 10, 1000);
  }
}
