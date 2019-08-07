package org.tron.core.consensus.dpos;

import static org.tron.core.consensus.base.Constant.BLOCK_PRODUCED_INTERVAL;
import static org.tron.core.consensus.base.Constant.MAX_ACTIVE_WITNESS_NUM;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.protobuf.ByteString;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.config.args.Args;
import org.tron.core.consensus.base.Status;
import org.tron.core.db.Manager;

@Slf4j(topic = "consensus")
public class DposStatus {

  @Autowired
  private Manager manager;

  @Setter
  private DposService dposService;

  private int minParticipationRate = Args.getInstance().getMinParticipationRate();

  private AtomicInteger dupBlockCount = new AtomicInteger(0);

  private AtomicLong dupBlockTime = new AtomicLong(0);

  private long blockCycle = BLOCK_PRODUCED_INTERVAL * MAX_ACTIVE_WITNESS_NUM;

  private Cache<ByteString, Long> blocks = CacheBuilder.newBuilder().maximumSize(10).build();

  public Status get() {

    Status status = dposService.getBlockHandle().ready();
    if (!Status.OK.equals(status)) {
      return status;
    }

    if (dupWitnessCheck()) {
      return Status.DUP_WITNESS;
    }

    int participation = manager.getDynamicPropertiesStore().calculateFilledSlotsCount();
    if (participation < minParticipationRate) {
      logger.warn("Participation[" + participation + "] <  MIN_PARTICIPATION_RATE[" + minParticipationRate + "]");
      return Status.LOW_PARTICIPATION;
    }

    return Status.OK;
  }

  public void process(BlockCapsule block) {
    if (block.generatedByMyself) {
      blocks.put(block.getBlockId().getByteString(), System.currentTimeMillis());
      return;
    }

    if (blocks.getIfPresent(block.getBlockId().getByteString()) != null) {
      return;
    }

    if (dposService.isNeedSyncCheck()) {
      return;
    }

    if (System.currentTimeMillis() - block.getTimeStamp() > BLOCK_PRODUCED_INTERVAL) {
      return;
    }

    if (!dposService.getPrivateKeys().containsKey(block.getWitnessAddress())) {
      return;
    }

    if (dupBlockCount.get() == 0) {
      dupBlockCount.set(new Random().nextInt(10));
    } else {
      dupBlockCount.set(10);
    }

    dupBlockTime.set(System.currentTimeMillis());

    logger.warn("Dup block produced: {}", block);
  }

  private boolean dupWitnessCheck() {
    if (dupBlockCount.get() == 0) {
      return false;
    }

    if (System.currentTimeMillis() - dupBlockTime.get() > dupBlockCount.get() * blockCycle) {
      dupBlockCount.set(0);
      return false;
    }

    return true;
  }
}
