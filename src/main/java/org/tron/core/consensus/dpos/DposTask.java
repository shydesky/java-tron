package org.tron.core.consensus.dpos;

import static org.tron.core.consensus.base.Constant.BLOCK_PRODUCED_INTERVAL;

import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;
import java.util.Map;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.capsule.WitnessCapsule;
import org.tron.core.config.args.Args;
import org.tron.core.consensus.base.BlockHandle;
import org.tron.core.consensus.base.Status;
import org.tron.core.db.Manager;
import org.tron.core.exception.TronException;
import org.tron.core.witness.BlockProductionCondition;

@Slf4j(topic = "consensus")
public class DposTask {

  @Autowired
  private Manager manager;

  @Autowired
  private DposStatus dposStatus;

  @Autowired
  private DposManager dposManager;

  @Setter
  private DposService dposService;

  private BlockHandle blockHandle;

  private boolean isRunning = true;

  private boolean needSyncCheck = Args.getInstance().isNeedSyncCheck();

  private int minParticipationRate = Args.getInstance().getMinParticipationRate();

  private Map<ByteString, byte[]> privateKeys = Maps.newHashMap();

  private Map<ByteString, WitnessCapsule> witnesses = Maps.newHashMap();

  private static final int PRODUCE_TIME_OUT = 500;

  public void init(BlockHandle blockHandle) {

    Runnable runnable = () -> {
      while (isRunning) {
        try {
          if (this.needSyncCheck) {
            Thread.sleep(1000);
            needSyncCheck = dposManager.getSlotTime(1) < System.currentTimeMillis();
          } else {
            DateTime time = DateTime.now();
            long timeToNextSecond = BLOCK_PRODUCED_INTERVAL
                - (time.getSecondOfMinute() * 1000 + time.getMillisOfSecond())
                % BLOCK_PRODUCED_INTERVAL;
//            if (timeToNextSecond < 50) {
//              timeToNextSecond = timeToNextSecond + ChainConstant.BLOCK_PRODUCED_INTERVAL;
//            }
            Thread.sleep(timeToNextSecond);
            blockProductionLoop();
          }
        } catch (Throwable throwable) {
          logger.error("Generate block error.", throwable);
        }
      }
    };

    new Thread(runnable, "DPosMiner").start();
  }

  private void blockProductionLoop() throws InterruptedException {
    BlockProductionCondition result = this.tryProduceBlock();

    if (result == null) {
      logger.warn("Result is null");
      return;
    }

    if (result.ordinal() <= NOT_MY_TURN.ordinal()) {
      logger.debug(result.toString());
    } else {
      logger.info(result.toString());
    }
  }

  private Status tryProduceBlock() throws Exception {

    Status status = dposStatus.get();
    if (!Status.OK.equals(status)) {
      return status;
    }

    long now = System.currentTimeMillis() + 50;

    BlockCapsule block;

    try {

      if (now < manager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp()) {
        return Status.CLOCK_ERROR;
      }

      long slot = dposManager.getSlotAtTime(now);
      if (slot == 0) {
        return Status.NOT_TIME_YET;
      }

      final ByteString scheduledWitness = dposManager.getScheduledWitness(slot);
      if (!dposService.getWitnesses().containsKey(scheduledWitness)) {
        return Status.UNELECTED;
      }

      synchronized (blockHandle.getLock()) {
        long scheduledTime = dposManager.getSlotTime(slot);

        if (scheduledTime - now > PRODUCE_TIME_OUT) {
          return Status.LAG;
        }

        //controller.setGeneratingBlock(true);

        block = blockHandle.produce();
        if (block == null) {
          return Status.EXCEPTION_PRODUCING_BLOCK;
        }

        block.setWitness(Hex.encodeHexString(scheduledWitness.toByteArray()));
        block.setWitness();


        blockHandle.complete(block);
      }

      logger.info(
          "Produce block successfully, blockNumber:{}, abSlot[{}], blockId:{}, transactionSize:{}, blockTime:{}, parentBlockId:{}",
          block.getNum(), controller.getAbSlotAtTime(now), block.getBlockId(),
          block.getTransactions().size(),
          new DateTime(block.getTimeStamp()),
          block.getParentHash());


      return  Status.OK;
    } catch (TronException e) {
      logger.error(e.getMessage(), e);
      return  Status.EXCEPTION_PRODUCING_BLOCK;
    } finally {
      controller.setGeneratingBlock(false);
    }
  }

}
