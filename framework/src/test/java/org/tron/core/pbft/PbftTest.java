package org.tron.core.pbft;

import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.tron.common.utils.ByteArray;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.pbft.message.PbftSrMessage;
import org.tron.protos.Protocol.Block;

public class PbftTest {

  @Test
  public void testPbftSrMessage() {
    BlockCapsule blockCapsule = new BlockCapsule(Block.getDefaultInstance());
    List<ByteString> srList = new ArrayList<>();

    srList.add(ByteString.copyFrom(ByteArray.fromHexString("41f08012b4881c320eb40b80f1228731898824e09d")));
    srList.add(ByteString.copyFrom(ByteArray.fromHexString("41df309fef25b311e7895562bd9e11aab2a58816d2")));
    PbftSrMessage pbftSrMessage = (PbftSrMessage) PbftSrMessage.buildPrePrepareMessage(blockCapsule, srList);
    System.out.println(pbftSrMessage);
  }

}
