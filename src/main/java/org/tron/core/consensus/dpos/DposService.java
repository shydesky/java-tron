package org.tron.core.consensus.dpos;

import static org.tron.core.consensus.base.Constant.SOLIDIFIED_THRESHOLD;

import com.google.protobuf.ByteString;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.Sha256Hash;
import org.tron.core.consensus.ConsensusDelegate;
import org.tron.core.consensus.base.BlockHandle;
import org.tron.core.consensus.base.ConsensusInterface;
import org.tron.core.consensus.base.Param;
import org.tron.protos.Protocol.Block;

@Slf4j(topic = "consensus")
@Component
public class DposService implements ConsensusInterface {

  @Autowired
  private ConsensusDelegate consensusDelegate;

  @Autowired
  private DposTask dposTask;

  @Autowired
  private DposSlot dposSlot;

  @Autowired
  private IncentiveManager incentiveManager;

  @Autowired
  private StateManager stateManager;

  @Autowired
  private StatisticManager statisticManager;

  @Autowired
  private MaintenanceManager maintenanceManager;

  @Getter
  @Setter
  private volatile boolean needSyncCheck;
  @Getter
  @Setter
  private volatile int minParticipationRate;
  @Getter
  private BlockHandle blockHandle;

  @Getter
  protected Set<ByteString> witnesses;

  @Override
  public void start(Param param) {
    this.needSyncCheck = param.isNeedSyncCheck();
    this.minParticipationRate = param.getMinParticipationRate();
    this.blockHandle = param.getBlockHandle();
    this.witnesses = param.getWitnesses();
    stateManager.setDposService(this);
    dposTask.setDposService(this);
    dposTask.init();
  }

  @Override
  public void stop() {

  }

  @Override
  public boolean validBlock(Block block) {
    if (consensusDelegate.getLatestBlockHeaderNumber() == 0) {
      return true;
    }
    ByteString witnessAddress = block.getBlockHeader().getRawData().getWitnessAddress();
    long timeStamp = block.getBlockHeader().getRawData().getTimestamp();
    long bSlot = dposSlot.getAbSlot(timeStamp);
    long hSlot = dposSlot.getAbSlot(consensusDelegate.getLatestBlockHeaderTimestamp());
    if (bSlot <= hSlot) {
      logger.warn("blockAbSlot is equals with headBlockAbSlot[" + bSlot + "]");
      return false;
    }

    long slot = dposSlot.getSlot(timeStamp);
    final ByteString scheduledWitness = dposSlot.getScheduledWitness(slot);
    if (!scheduledWitness.equals(witnessAddress)) {
      logger.warn(
          "Witness out of order, scheduledWitness[{}],blockWitness[{}],blockTimeStamp[{}],slot[{}]",
          ByteArray.toHexString(scheduledWitness.toByteArray()),
          ByteArray.toHexString(witnessAddress.toByteArray()), new DateTime(timeStamp), slot);
      return false;
    }
    return true;
  }

  @Override
  public boolean applyBlock(Block block) {
    stateManager.applyBlock(block);
    statisticManager.applyBlock(block);
    incentiveManager.applyBlock(block);
    maintenanceManager.applyBlock(block);
    updateSolidBlock();
    return true;
  }


  private void updateSolidBlock() {
    List<Long> numbers = consensusDelegate.getActiveWitnesses().stream()
        .map(address -> consensusDelegate.getWitnesseByAddress(address).getLatestBlockNum())
        .sorted()
        .collect(Collectors.toList());
    long size = consensusDelegate.getActiveWitnesses().size();
    int position = (int) (size * (1 - SOLIDIFIED_THRESHOLD * 1.0 / 100));
    long newSolidNum = numbers.get(position);
    long oldSolidNum = consensusDelegate.getLatestSolidifiedBlockNum();
    if (newSolidNum < oldSolidNum) {
      logger.warn("Update solid block number failed, new:{} < old:{}", newSolidNum, newSolidNum);
      return;
    }
    consensusDelegate.saveLatestSolidifiedBlockNum(newSolidNum);
    logger.info("Update solid block number to {}", newSolidNum);
  }

  public static Sha256Hash getBlockHash(Block block) {
    return Sha256Hash.of(block.getBlockHeader().getRawData().toByteArray());
  }
}
