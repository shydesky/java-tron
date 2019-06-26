package org.tron.core.pbft;

import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.pbft.message.PbftSrMessage;
import org.tron.protos.Protocol.Block;

public class PbftTest {

  @Test
  public void testPbftSrMessage() {
    BlockCapsule blockCapsule = new BlockCapsule(Block.getDefaultInstance());
    List<ByteString> srList = new ArrayList<>();
    srList.add(ByteString.copyFromUtf8("sr1"));
    srList.add(ByteString.copyFromUtf8("sr2"));
    PbftSrMessage pbftSrMessage = (PbftSrMessage) PbftSrMessage.buildPrePrepareMessage(blockCapsule, srList);
    System.out.println(pbftSrMessage);
  }

}
