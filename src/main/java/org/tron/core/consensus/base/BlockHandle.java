package org.tron.core.consensus.base;

import org.tron.protos.Protocol.Block;
import org.tron.protos.Protocol.BlockHeader;

public interface BlockHandle {

  State getState();

  Object getLock();

  Block produce(BlockHeader.raw blockHead);

  void complete(Block block);

}
