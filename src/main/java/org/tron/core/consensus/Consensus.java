package org.tron.core.consensus;

import java.util.List;
import org.tron.core.consensus.base.BlockHandle;
import org.tron.protos.Protocol.Block;

public class Consensus {

  void init(BlockHandle handle, List<byte[]> privateKeys) {

  }

  void start() {

  }

  void stop() {

  }

  boolean processBlock(Block block) {
    return true;
  }

}
