package org.tron.core.consensus.dpos;

import static org.tron.core.consensus.base.Constant.BLOCK_PRODUCED_INTERVAL;
import static org.tron.core.consensus.base.Constant.SINGLE_REPEAT;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.common.utils.Time;
import org.tron.core.consensus.ConsensusDelegate;

@Slf4j(topic = "consensus")
@Component
public class DposSlot {

  @Autowired
  private ConsensusDelegate consensusDelegate;

  public long getAbSlot(long time) {
    return (time - consensusDelegate.getGenesisBlock().getTimeStamp()) / BLOCK_PRODUCED_INTERVAL;
  }

  public long getSlot(long time) {
    long firstSlotTime = getTime(1);
    if (time < firstSlotTime) {
      return 0;
    }
    return (time - firstSlotTime) / BLOCK_PRODUCED_INTERVAL + 1;
  }

  public long getTime(long slot) {
    if (slot == 0) {
      return Time.getCurrentMillis();
    }
    long interval = BLOCK_PRODUCED_INTERVAL;
    if (consensusDelegate.getLatestBlockHeaderNumber() == 0) {
      return consensusDelegate.getGenesisBlock().getTimeStamp() + slot * interval;
    }
    if (consensusDelegate.lastHeadBlockIsMaintenance()) {
      slot += consensusDelegate.getSkipSlotInMaintenance();
    }
    long time = consensusDelegate.getLatestBlockHeaderTimestamp();
    time = time - ((time - consensusDelegate.getGenesisBlock().getTimeStamp()) % interval);
    return time + interval * slot;
  }

  public ByteString getScheduledWitness(long slot) {
    final long currentSlot = getAbSlot(consensusDelegate.getLatestBlockHeaderTimestamp()) + slot;
    if (currentSlot < 0) {
      throw new RuntimeException("current slot should be positive.");
    }
    int size = consensusDelegate.getActiveWitnesses().size();
    if (size <= 0) {
      throw new RuntimeException("active witnesses is null.");
    }
    int witnessIndex = (int) currentSlot % (size * SINGLE_REPEAT);
    witnessIndex /= SINGLE_REPEAT;
    return consensusDelegate.getActiveWitnesses().get(witnessIndex);
  }

}
