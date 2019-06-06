package org.tron.core.pbft;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.AtomicLongMap;
import com.google.protobuf.ByteString;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.tron.common.overlay.server.SyncPool;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.config.args.Args;
import org.tron.core.db.Manager;
import org.tron.core.pbft.message.PbftBaseMessage;
import org.tron.core.pbft.message.PbftBlockMessageCapsule;

@Slf4j(topic = "pbft")
@Component
public class PbftMessageHandle {

  // 预准备阶段投票信息
  private Set<String> preVotes = Sets.newConcurrentHashSet();
  // 准备阶段投票信息
  private Set<String> pareVotes = Sets.newConcurrentHashSet();
  private AtomicLongMap<String> agreePare = AtomicLongMap.create();
  private Cache<String, PbftBaseMessage> pareMsgCache = CacheBuilder.newBuilder()
      .initialCapacity(1000).maximumSize(10000).expireAfterWrite(10, TimeUnit.MINUTES).build();
  // 提交阶段投票信息
  private Set<String> commitVotes = Sets.newConcurrentHashSet();
  private AtomicLongMap<String> agreeCommit = AtomicLongMap.create();
  private Cache<String, PbftBaseMessage> commitMsgCache = CacheBuilder.newBuilder()
      .initialCapacity(1000).maximumSize(10000).expireAfterWrite(10, TimeUnit.MINUTES).build();
  // pbft超时
  private Map<String, Long> timeOuts = Maps.newConcurrentMap();
  // 请求超时，view加1，重试
  private Map<String, Long> timeOutsReq = Maps.newHashMap();

  // 成功处理过的请求
  private Map<String, PbftBaseMessage> doneMsg = Maps.newConcurrentMap();

  private byte[] witnessAddress = Args.getInstance().getLocalWitnesses().getWitnessAccountAddress();

  private Timer timer;

  private SyncPool syncPool;
  private Manager manager;
  @Autowired
  private PbftMessageAction pbftMessageAction;
  @Autowired
  private ApplicationContext ctx;

  public void init() {
    syncPool = ctx.getBean(SyncPool.class);
    manager = ctx.getBean(Manager.class);
    start();
  }

  public void onPrePrepare(PbftBaseMessage message) {
    String key = message.getNo();
    if (preVotes.contains(key)) {
      // 说明已经发起过，不能重复发起，同一高度只能发起一次投票
      return;
    }
    preVotes.add(key);
    // 启动超时控制
    timeOuts.put(key, System.currentTimeMillis());
    //
    checkPrepareMsgCache(key);
    // 进入准备阶段,如果不是sr节点不需要准备
    if (!checkIsCanSendMsg(message)) {
      return;
    }
    PbftBaseMessage paMessage = PbftBlockMessageCapsule.buildPrePareMessage(message);
    forwardMessage(paMessage);
  }

  public void onPrepare(PbftBaseMessage message) {
    String key = message.getKey();

    if (!preVotes.contains(message.getNo())) {
      // 必须先过预准备
      pareMsgCache.put(key, message);
      return;
    }
    if (pareVotes.contains(key)) {
      // 说明已经投过票，不能重复投
      return;
    }
    pareVotes.add(key);
    //
    checkCommitMsgCache(message.getNo());
    if (!checkIsCanSendMsg(message)) {
      return;
    }
    // 票数 +1
    if (!doneMsg.containsKey(message.getNo())) {
      long agCou = agreePare.incrementAndGet(message.getDataKey());
      if (agCou >= PbftManager.agreeNodeCount) {
        agreePare.remove(message.getDataKey());
        // 进入提交阶段
        PbftBaseMessage cmMessage = PbftBlockMessageCapsule.buildCommitMessage(message);
        doneMsg.put(message.getNo(), cmMessage);
        forwardMessage(cmMessage);
      }
    }
    // 后续的票数肯定凑不满，超时自动清除
  }

  public void onCommit(PbftBaseMessage message) {
    // data模拟数据摘要
    String key = message.getKey();
    if (!pareVotes.contains(key)) {
      // 必须先过准备
      commitMsgCache.put(key, message);
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
      if (!isSyncing()) {
        pbftMessageAction.action(message);
      }
    }
  }

  public void onRequestData(PbftBaseMessage message) {

  }

  public void onChangeView(PbftBaseMessage message) {

  }

  public void forwardMessage(PbftBaseMessage message) {
    if (syncPool == null) {
      return;
    }
    syncPool.getActivePeers().forEach(peerConnection -> {
      peerConnection.sendMessage(message);
    });
  }

  private void checkPrepareMsgCache(String key) {
    for (Entry<String, PbftBaseMessage> entry : pareMsgCache.asMap().entrySet()) {
      if (StringUtils.startsWith(entry.getKey(), key)) {
        onPrepare(entry.getValue());
        pareMsgCache.invalidate(entry.getKey());
      }
    }
  }

  private void checkCommitMsgCache(String key) {
    for (Entry<String, PbftBaseMessage> entry : commitMsgCache.asMap().entrySet()) {
      if (StringUtils.startsWith(entry.getKey(), key)) {
        onCommit(entry.getValue());
        commitMsgCache.invalidate(entry.getKey());
      }
    }
  }

  public boolean checkMsg(PbftBaseMessage msg) throws SignatureException {
    return msg.validateSignature() && checkIsWitnessMsg(msg);
  }

  public boolean checkIsCanSendMsg(PbftBaseMessage msg) {
    if (!Args.getInstance().isWitness()) {
      return false;
    }
    if (!manager.getWitnessScheduleStore().getActiveWitnesses().stream().anyMatch(witness -> {
      return Arrays.equals(witness.toByteArray(), witnessAddress);
    })) {
      return false;
    }
    return !isSyncing();
  }

  public boolean checkIsWitnessMsg(PbftBaseMessage msg) {
    //check current node is witness node
    if (manager == null) {
      return false;
    }
    long blockNum = msg.getPbftMessage().getRawData().getBlockNum();
    List<ByteString> witnessList;
    BlockCapsule blockCapsule = null;
    try {
      blockCapsule = manager.getBlockByNum(blockNum);
    } catch (Exception e) {
      logger.error("can not find the block,num is: {}, error reason: {}", blockNum, e.getMessage());
    }
    if (blockCapsule == null || blockCapsule.getTimeStamp() > manager
        .getBeforeMaintenanceTime()) {
      witnessList = manager.getCurrentWitness();
    } else {
      witnessList = manager.getBeforeWitness();
    }
    return witnessList.stream()
        .anyMatch(witness -> witness.equals(msg.getPbftMessage().getRawData().getPublicKey()));
  }

  public boolean isSyncing() {
    if (syncPool == null) {
      return true;
    }
    AtomicBoolean result = new AtomicBoolean(false);
    syncPool.getActivePeers().forEach(peerConnection -> {
      if (peerConnection.isNeedSyncFromPeer()) {
        result.set(true);
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
    doneMsg.remove(no);
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
        remove(item.getKey());
      }
    }
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
