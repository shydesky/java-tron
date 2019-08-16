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
import org.springframework.stereotype.Component;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.Sha256Hash;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.capsule.WitnessCapsule;
import org.tron.core.config.Parameter.ChainConstant;
import org.tron.core.config.args.Args;
import org.tron.core.consensus.ConsensusDelegate;
import org.tron.core.consensus.base.BlockHandle;
import org.tron.core.consensus.base.State;
import org.tron.protos.Protocol.Block;
import org.tron.protos.Protocol.BlockHeader;

@Slf4j(topic = "consensus")
@Component
public class DposTask {

  @Autowired
  private ConsensusDelegate consensusDelegate;

  @Setter
  private DposService dposService;

  @Autowired
  private DposSlot dposSlot;

  @Autowired
  private StateManager stateManager;

  private Thread produceThread;

  private volatile boolean isRunning = true;

  public void init() {

    Runnable runnable = () -> {
      while (isRunning) {
        try {
          if (dposService.isNeedSyncCheck()) {
            Thread.sleep(1000);
            dposService.setNeedSyncCheck(dposSlot.getTime(1) < System.currentTimeMillis());
          } else {
            long time = BLOCK_PRODUCED_INTERVAL - System.currentTimeMillis() % BLOCK_PRODUCED_INTERVAL;
            Thread.sleep(time);
            State state = produceBlock();
            if (!State.OK.equals(state)) {
              logger.info("Produce block failed: {}", state);
            }
          }
        } catch (Throwable throwable) {
          logger.error("Produce block error.", throwable);
        }
      }
    };
    produceThread = new Thread(runnable, "DPosMiner");
    produceThread.start();
  }

  public void stop() {
    isRunning = false;
    produceThread.interrupt();
  }

  private State produceBlock() {

    State status = stateManager.getState();
    if (!State.OK.equals(status)) {
      return status;
    }

    synchronized (dposService.getBlockHandle().getLock()) {

      long slot = dposSlot.getSlot(System.currentTimeMillis() + 50);
      if (slot == 0) {
        return State.NOT_TIME_YET;
      }

      final ByteString scheduledWitness = dposSlot.getScheduledWitness(slot);
      if (!dposService.getWitnesses().contains(scheduledWitness)) {
        return State.NOT_MY_TURN;
      }

      BlockHeader.raw raw = BlockHeader.raw.newBuilder()
          .setWitnessAddress(scheduledWitness)
          .setTimestamp(dposSlot.getTime(slot))
          .build();

      Block block = dposService.getBlockHandle().produce(raw);
      if (block == null) {
        return State.PRODUCE_BLOCK_FAILED;
      }

      stateManager.setCurrentBlock(block);

      dposService.getBlockHandle().complete(block);

      logger.info("Produce block successfully, num:{}, time:{}, witness:{}, hash:{} parentHash:{}",
          block.getBlockHeader().getRawData().getNumber(),
          new DateTime(block.getBlockHeader().getRawData().getTimestamp()),
          block.getBlockHeader().getRawData().getWitnessAddress(),
          DposService.getBlockHash(block),
          ByteArray.toHexString(block.getBlockHeader().getRawData().getParentHash().toByteArray()));
    }

    return State.OK;
  }

}
