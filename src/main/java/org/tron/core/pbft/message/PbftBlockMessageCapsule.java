package org.tron.core.pbft.message;

import com.google.protobuf.ByteString;
import org.tron.common.crypto.ECKey;
import org.tron.common.crypto.ECKey.ECDSASignature;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.Sha256Hash;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.config.args.Args;
import org.tron.core.config.args.LocalWitnesses;
import org.tron.core.net.message.MessageTypes;
import org.tron.protos.Protocol.PbftMessage;
import org.tron.protos.Protocol.PbftMessage.Raw;
import org.tron.protos.Protocol.PbftMessage.Type;

public class PbftBlockMessageCapsule extends PbftBaseMessage {

  public PbftBlockMessageCapsule() {
  }

  public PbftBlockMessageCapsule(byte[] data) throws Exception {
    super(MessageTypes.PBFT_BLOCK_MSG.asByte(), data);
  }

  public String getNo() {
    return pbftMessage.getRawData().getBlockNum() + "_" + getType().asByte();
  }

  public static PbftBaseMessage buildPrePrepareMessage(BlockCapsule blockCapsule) {
    PbftBlockMessageCapsule pbftMessageCapsule = new PbftBlockMessageCapsule();
    LocalWitnesses localWitnesses = Args.getInstance().getLocalWitnesses();
    ECKey ecKey = ECKey.fromPrivate(ByteArray.fromHexString(localWitnesses.getPrivateKey()));
    Raw.Builder rawBuilder = PbftMessage.Raw.newBuilder();
    PbftMessage.Builder builder = PbftMessage.newBuilder();
    byte[] publicKey = localWitnesses.getPublicKey();
    rawBuilder.setBlockNum(blockCapsule.getNum())
        .setPbftMsgType(Type.PREPREPARE)
        .setTime(System.currentTimeMillis())
        .setPublicKey(ByteString.copyFrom(publicKey == null ? new byte[0] : publicKey))
        .setData(blockCapsule.getBlockId().getByteString());
    Raw raw = rawBuilder.build();
    byte[] hash = Sha256Hash.hash(raw.toByteArray());
    ECDSASignature signature = ecKey.sign(hash);
    builder.setRawData(raw).setSign(ByteString.copyFrom(signature.toByteArray()));
    PbftMessage message = builder.build();
    pbftMessageCapsule.setType(MessageTypes.PBFT_BLOCK_MSG.asByte())
        .setPbftMessage(message).setData(message.toByteArray());
    return pbftMessageCapsule;
  }

}
