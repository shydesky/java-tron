package org.tron.core.pbft.message;

import com.google.protobuf.ByteString;
import java.security.SignatureException;
import java.util.Arrays;
import org.spongycastle.util.encoders.Hex;
import org.tron.common.crypto.ECKey;
import org.tron.common.crypto.ECKey.ECDSASignature;
import org.tron.common.overlay.message.Message;
import org.tron.common.utils.ByteArray;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.capsule.TransactionCapsule;
import org.tron.core.config.args.Args;
import org.tron.core.config.args.LocalWitnesses;
import org.tron.core.net.message.MessageTypes;
import org.tron.protos.Protocol.PbftMessage;
import org.tron.protos.Protocol.PbftMessage.Type;

public class PbftMessageCapsule extends Message {

  private PbftMessage pbftMessage;

  public PbftMessageCapsule() {
  }

  public PbftMessageCapsule(byte[] data) throws Exception {
    super(MessageTypes.PBFT_MSG.asByte(), data);
    this.pbftMessage = PbftMessage.parseFrom(getCodedInputStream(data));
    if (isFilter()) {
      compareBytes(data, pbftMessage.toByteArray());
    }
  }

  @Override
  public Class<?> getAnswerMessage() {
    return null;
  }

  public PbftMessage getPbftMessage() {
    return pbftMessage;
  }

  public PbftMessageCapsule setPbftMessage(PbftMessage pbftMessage) {
    this.pbftMessage = pbftMessage;
    return this;
  }

  public PbftMessageCapsule setData(byte[] data) {
    this.data = data;
    return this;
  }

  public PbftMessageCapsule setType(byte type) {
    this.type = type;
    return this;
  }

  public String getKey() {
    return getBlockNum() + "_" + Hex.toHexString(pbftMessage.getPublicKey().toByteArray());
  }

  public long getBlockNum() {
    return pbftMessage.getBlockNum();
  }

  public static PbftMessageCapsule buildVoteMessage(BlockCapsule blockCapsule) {
    PbftMessageCapsule pbftMessageCapsule = new PbftMessageCapsule();
    LocalWitnesses localWitnesses = Args.getInstance().getLocalWitnesses();
    ECKey ecKey = ECKey.fromPrivate(ByteArray.fromHexString(localWitnesses.getPrivateKey()));
    ECDSASignature signature = ecKey.sign(blockCapsule.getBlockId().getBytes());
    PbftMessage.Builder builder = PbftMessage.newBuilder();
    builder.setBlockId(blockCapsule.getBlockId().getByteString())
        .setBlockNum(blockCapsule.getNum())
        .setPbftMsgType(Type.PA)
        .setTime(System.currentTimeMillis())
        .setPublicKey(ByteString.copyFrom(localWitnesses.getPublicKey()))
        .setData(blockCapsule.getBlockId().getByteString())
        .setSign(ByteString.copyFrom(signature.toByteArray()));
    PbftMessage message = builder.build();
    return pbftMessageCapsule.setType(MessageTypes.PBFT_MSG.asByte())
        .setPbftMessage(message).setData(message.toByteArray());
  }

  public static PbftMessageCapsule buildCommitMessage(PbftMessageCapsule paMessage) {
    PbftMessageCapsule pbftMessageCapsule = new PbftMessageCapsule();
    LocalWitnesses localWitnesses = Args.getInstance().getLocalWitnesses();
    ECKey ecKey = ECKey.fromPrivate(ByteArray.fromHexString(localWitnesses.getPrivateKey()));
    ECDSASignature signature = ecKey.sign(paMessage.getPbftMessage().getData().toByteArray());
    PbftMessage.Builder builder = PbftMessage.newBuilder();
    builder.setBlockId(paMessage.getPbftMessage().getBlockId())
        .setBlockNum(paMessage.getPbftMessage().getBlockNum())
        .setPbftMsgType(Type.CM)
        .setTime(System.currentTimeMillis())
        .setPublicKey(ByteString.copyFrom(localWitnesses.getPublicKey()))
        .setData(paMessage.getPbftMessage().getData())
        .setSign(ByteString.copyFrom(signature.toByteArray()));
    PbftMessage message = builder.build();
    return pbftMessageCapsule.setType(MessageTypes.PBFT_MSG.asByte())
        .setPbftMessage(message).setData(message.toByteArray());
  }

  public boolean validateSignature(PbftMessageCapsule pbftMessageCapsule, BlockCapsule blockCapsule)
      throws SignatureException {
    byte[] sigAddress = ECKey.signatureToAddress(pbftMessageCapsule.getData(),
        TransactionCapsule.getBase64FromByteString(pbftMessageCapsule.getPbftMessage().getSign()));
    byte[] witnessAccountAddress = blockCapsule.getInstance().getBlockHeader().getRawData()
        .getWitnessAddress().toByteArray();
    return Arrays.equals(sigAddress, witnessAccountAddress);
  }

}
