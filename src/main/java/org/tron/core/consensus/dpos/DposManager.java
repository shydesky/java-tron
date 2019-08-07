package org.tron.core.consensus.dpos;

import com.google.protobuf.ByteString;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.Time;
import org.tron.core.config.Parameter.ChainConstant;
import org.tron.core.db.Manager;

@Slf4j(topic = "consensus")
public class DposManager {

  @Autowired
  private Manager manager;

  public long getSlotAtTime(long when) {
    long firstSlotTime = getSlotTime(1);
    if (when < firstSlotTime) {
      return 0;
    }
    return (when - firstSlotTime) / ChainConstant.BLOCK_PRODUCED_INTERVAL + 1;
  }

  public long getSlotTime(long slotNum) {
    if (slotNum == 0) {
      return Time.getCurrentMillis();
    }

    long interval = ChainConstant.BLOCK_PRODUCED_INTERVAL;

    if (manager.getDynamicPropertiesStore().getLatestBlockHeaderNumber() == 0) {
      return manager.getGenesisBlock().getTimeStamp() + slotNum * interval;
    }

    if (manager.lastHeadBlockIsMaintenance()) {
      slotNum += manager.getSkipSlotInMaintenance();
    }

    long headSlotTime = manager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp();
    headSlotTime = headSlotTime
        - ((headSlotTime - manager.getGenesisBlock().getTimeStamp()) % interval);

    return headSlotTime + interval * slotNum;
  }

  public ByteString getScheduledWitness(final long slot) {

    final long currentSlot = getHeadSlot() + slot;

    if (currentSlot < 0) {
      throw new RuntimeException("currentSlot should be positive.");
    }

    int numberActiveWitness = this.getActiveWitnesses().size();
    int singleRepeat = ChainConstant.SINGLE_REPEAT;
    if (numberActiveWitness <= 0) {
      throw new RuntimeException("Active Witnesses is null.");
    }
    int witnessIndex = (int) currentSlot % (numberActiveWitness * singleRepeat);
    witnessIndex /= singleRepeat;
    logger.debug("currentSlot:" + currentSlot
        + ", witnessIndex" + witnessIndex
        + ", currentActiveWitnesses size:" + numberActiveWitness);

    final ByteString scheduledWitness = this.getActiveWitnesses().get(witnessIndex);
    logger.info("scheduledWitness:" + ByteArray.toHexString(scheduledWitness.toByteArray())
        + ", currentSlot:" + currentSlot);

    return scheduledWitness;
  }

  public long getHeadSlot() {
    return (manager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp() - getGenesisBlock()
        .getTimeStamp())
        / ChainConstant.BLOCK_PRODUCED_INTERVAL;
  }


}
