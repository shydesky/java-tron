package org.tron.core.consensus.base;

import java.util.List;
import org.tron.protos.Protocol.Block;

public interface ConsensusInterface {

//  void register(BlockHandle handle, List<byte[]> privateKeys);

  void init(BlockHandle handle, List<byte[]> privateKeys);

  boolean processBlock(Block block);

}
