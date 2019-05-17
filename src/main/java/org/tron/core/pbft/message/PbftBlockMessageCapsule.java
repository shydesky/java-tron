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
import org.tron.core.exception.P2pException;
import org.tron.core.exception.P2pException.TypeEnum;
import org.tron.core.net.message.MessageTypes;
import org.tron.protos.Protocol.PbftMessage;
import org.tron.protos.Protocol.PbftMessage.Type;

public class PbftBlockMessageCapsule extends Message {

  private PbftMessage pbftMessage;

  public PbftBlockMessageCapsule() {
  }

  public PbftBlockMessageCapsule(byte[] data) throws Exception {
    super(MessageTypes.PBFT_BLOCK_MSG.asByte(), data);
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

  public PbftBlockMessageCapsule setPbftMessage(PbftMessage pbftMessage) {
    this.pbftMessage = pbftMessage;
    return this;
  }

  public PbftBlockMessageCapsule setData(byte[] data) {
    this.data = data;
    return this;
  }

  public PbftBlockMessageCapsule setType(byte type) {
    this.type = type;
    return this;
  }

  public String getKey() throws P2pException {
    return getNo() + "_" + Hex.toHexString(pbftMessage.getPublicKey().toByteArray());
  }

  public String getDataKey() throws P2pException {
    return getNo() + "_" + Hex.toHexString(pbftMessage.getData().toByteArray());
  }

  public String getNo() throws P2pException {
    MessageTypes messageTypes = MessageTypes.fromByte(type);
    if (messageTypes == MessageTypes.PBFT_BLOCK_MSG) {
      return pbftMessage.getBlockNum() + "_" + messageTypes.asByte();
    } else {
      throw new P2pException(TypeEnum.BAD_MESSAGE, "don't support pbft message");
    }
  }

  public static PbftBlockMessageCapsule buildVoteMessage(BlockCapsule blockCapsule) {
    PbftBlockMessageCapsule pbftMessageCapsule = new PbftBlockMessageCapsule();
    LocalWitnesses localWitnesses = Args.getInstance().getLocalWitnesses();
    ECKey ecKey = ECKey.fromPrivate(ByteArray.fromHexString(localWitnesses.getPrivateKey()));
    ECDSASignature signature = ecKey.sign(blockCapsule.getBlockId().getBytes());
    PbftMessage.Builder builder = PbftMessage.newBuilder();
    builder.setBlockNum(blockCapsule.getNum())
        .setPbftMsgType(Type.PA)
        .setTime(System.currentTimeMillis())
        .setPublicKey(ByteString.copyFrom(localWitnesses.getPublicKey()))
        .setData(blockCapsule.getBlockId().getByteString())
        .setSign(ByteString.copyFrom(signature.toByteArray()));
    PbftMessage message = builder.build();
    return pbftMessageCapsule.setType(MessageTypes.PBFT_BLOCK_MSG.asByte())
        .setPbftMessage(message).setData(message.toByteArray());
  }

  public static PbftBlockMessageCapsule buildPrePareMessage(PbftBlockMessageCapsule ppMessage) {
    return buildMessageCapsule(ppMessage, Type.PA);
  }

  public static PbftBlockMessageCapsule buildCommitMessage(PbftBlockMessageCapsule paMessage) {
    return buildMessageCapsule(paMessage, Type.CM);
  }

  public static PbftBlockMessageCapsule buildMessageCapsule(PbftBlockMessageCapsule paMessage,
      Type type) {
    PbftBlockMessageCapsule pbftMessageCapsule = new PbftBlockMessageCapsule();
    LocalWitnesses localWitnesses = Args.getInstance().getLocalWitnesses();
    ECKey ecKey = ECKey.fromPrivate(ByteArray.fromHexString(localWitnesses.getPrivateKey()));
    ECDSASignature signature = ecKey.sign(paMessage.getPbftMessage().getData().toByteArray());
    PbftMessage.Builder builder = PbftMessage.newBuilder();
    builder.setBlockNum(paMessage.getPbftMessage().getBlockNum())
        .setPbftMsgType(type)
        .setTime(System.currentTimeMillis())
        .setPublicKey(ByteString.copyFrom(localWitnesses.getPublicKey()))
        .setData(paMessage.getPbftMessage().getData())
        .setSign(ByteString.copyFrom(signature.toByteArray()));
    PbftMessage message = builder.build();
    return pbftMessageCapsule.setType(MessageTypes.PBFT_BLOCK_MSG.asByte())
        .setPbftMessage(message).setData(message.toByteArray());
  }


  public boolean validateSignature(PbftBlockMessageCapsule pbftMessageCapsule)
      throws SignatureException {
    byte[] sigAddress = ECKey.signatureToAddress(pbftMessageCapsule.getData(),
        TransactionCapsule.getBase64FromByteString(pbftMessageCapsule.getPbftMessage().getSign()));
    byte[] witnessAccountAddress = pbftMessageCapsule.getPbftMessage().getPublicKey().toByteArray();
    return Arrays.equals(sigAddress, witnessAccountAddress);
  }

}
