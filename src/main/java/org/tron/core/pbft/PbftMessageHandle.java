package org.tron.core.pbft;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.AtomicLongMap;
import com.google.protobuf.ByteString;
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
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.common.overlay.server.SyncPool;
import org.tron.common.utils.Sha256Hash;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.config.args.Args;
import org.tron.core.db.Manager;
import org.tron.core.exception.BadItemException;
import org.tron.core.exception.ItemNotFoundException;
import org.tron.core.pbft.message.PbftMessageCapsule;
import org.tron.core.witness.WitnessController;

@Slf4j
@Component
public class PbftMessageHandle {

  private long blockNum;

  // 预准备阶段投票信息
  private Set<Long> preVotes = Sets.newConcurrentHashSet();
  // 准备阶段投票信息
  private Set<String> pareVotes = Sets.newConcurrentHashSet();
  private AtomicLongMap<String> agreePare = AtomicLongMap.create();
  // 提交阶段投票信息
  private Set<String> commitVotes = Sets.newConcurrentHashSet();
  private AtomicLongMap<String> agreeCommit = AtomicLongMap.create();

  // pbft超时
  private Map<Long, Long> timeOuts = Maps.newHashMap();
  // 请求超时，view加1，重试
  private Map<String, Long> timeOutsReq = Maps.newHashMap();

  // 成功处理过的请求
  private Map<Long, PbftMessageCapsule> doneMsg = Maps.newConcurrentMap();

  private Timer timer;

  @Autowired
  private Manager manager;
  @Autowired
  private SyncPool syncPool;
  @Autowired
  private WitnessController witnessController;

  public void onPrePrepare(BlockCapsule blockCapsule) {
    if (!checkIsCanSendPrePrepareMsg()) {
      return;
    }
    long key = blockCapsule.getNum();
    if (preVotes.contains(key)) {
      // 说明已经发起过，不能重复发起，同一高度只能发起一次投票
      return;
    }
    preVotes.add(key);
    // 启动超时控制
    timeOuts.put(key, System.currentTimeMillis());
    // 进入准备阶段
    PbftMessageCapsule paMessage = PbftMessageCapsule.buildVoteMessage(blockCapsule);
    syncPool.getActivePeers().forEach(peerConnection -> {
      peerConnection.sendMessage(paMessage);
    });
  }

  public void onPrepare(PbftMessageCapsule message)
      throws SignatureException, BadItemException, ItemNotFoundException {
    if (!checkMsg(message)) {
      logger.info("异常消息:{}", message);
      return;
    }

    String key = message.getKey();

    if (!preVotes.contains(message.getBlockNum())) {
      // 必须先过预准备
      return;
    }
    if (pareVotes.contains(key)) {
      // 说明已经投过票，不能重复投
      return;
    }
    pareVotes.add(key);

    // 票数 +1
    long agCou = agreePare.incrementAndGet(getDataKey(message));
    if (agCou >= 2 * PbftManager.maxf + 1) {
      pareVotes.remove(key);
      // 进入提交阶段
      PbftMessageCapsule cmMessage = PbftMessageCapsule.buildCommitMessage(message);
      doneMsg.put(message.getBlockNum(), cmMessage);
      syncPool.getActivePeers().forEach(peerConnection -> {
        peerConnection.sendMessage(cmMessage);
      });
    }
    // 后续的票数肯定凑不满，超时自动清除
  }

  public void onCommit(PbftMessageCapsule message)
      throws SignatureException, BadItemException, ItemNotFoundException {
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
    long agCou = agreeCommit.incrementAndGet(getDataKey(message));
    if (agCou >= 2 * PbftManager.maxf + 1) {
      remove(message.getBlockNum());
      //commit,

    }
  }

  public void onRequestData(PbftMessageCapsule message) {

  }

  public void onChangeView(PbftMessageCapsule message) {

  }

  public boolean checkMsg(PbftMessageCapsule msg)
      throws BadItemException, ItemNotFoundException, SignatureException {
    return (msg.getPbftMessage().getBlockNum() == blockNum)
        && msg.validateSignature(msg, getBlockByHash(msg.getPbftMessage().getBlockId()));
  }

  public boolean checkIsCanSendPrePrepareMsg() {
    AtomicBoolean result = new AtomicBoolean(true);
    if (!Args.getInstance().isWitness()) {
      return !result.get();
    }
    witnessController.getActiveWitnesses().forEach(bytes -> {

    });
    syncPool.getActivePeers().forEach(peerConnection -> {
      if (peerConnection.isNeedSyncFromPeer()) {
        result.set(false);
        return;
      }
    });
    return result.get();
  }

  private String getDataKey(PbftMessageCapsule message) {
    return message.getBlockNum() + "_" + Hex
        .toHexString(message.getPbftMessage().getBlockId().toByteArray());
  }

  public BlockCapsule getBlockByHash(ByteString blockId)
      throws BadItemException, ItemNotFoundException {
    return manager.getBlockById(Sha256Hash.of((blockId.toByteArray())));
  }

  // 清理请求相关状态
  private void remove(long blockNum) {
    String pre = String.valueOf(blockNum) + "_";
    preVotes.remove(blockNum);
    pareVotes.removeIf((vp) -> StringUtils.startsWith(vp, pre));
    commitVotes.removeIf((vp) -> StringUtils.startsWith(vp, pre));
    agreePare.asMap().keySet().removeIf((vp) -> StringUtils.startsWith(vp, pre));
    agreeCommit.asMap().keySet().removeIf((vp) -> StringUtils.startsWith(vp, pre));
    timeOuts.remove(blockNum);
  }

  /**
   * 检测超时情况
   */
  private void checkTimer() {
    List<Long> remo = Lists.newArrayList();
    for (Entry<Long, Long> item : timeOuts.entrySet()) {
      if (System.currentTimeMillis() - item.getValue() > 300) {
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
    timer.schedule(new TimerTask() {
      @Override
      public void run() {
        checkTimer();
      }
    }, 10, 100);
  }
}
