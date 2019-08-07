package org.tron.core.consensus.base;

import org.tron.core.capsule.BlockCapsule;
import org.tron.protos.Protocol.Block;
import org.tron.protos.Protocol.BlockHeader;

public interface BlockHandle {

  Status ready();

  Object getLock();

  BlockCapsule produce();

  void complete(BlockCapsule block);

}
