package org.tron.core.pbft.message;

import com.google.protobuf.ByteString;
import java.io.IOException;
import java.security.SignatureException;
import java.util.Arrays;
import org.spongycastle.util.encoders.Hex;
import org.tron.common.crypto.ECKey;
import org.tron.common.crypto.ECKey.ECDSASignature;
import org.tron.common.overlay.message.Message;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.Sha256Hash;
import org.tron.core.capsule.TransactionCapsule;
import org.tron.core.config.args.Args;
import org.tron.core.config.args.LocalWitnesses;
import org.tron.core.exception.P2pException;
import org.tron.protos.Protocol.PbftMessage;
import org.tron.protos.Protocol.PbftMessage.Raw;
import org.tron.protos.Protocol.PbftMessage.Type;

public abstract class PbftBaseMessage extends Message {

  protected PbftMessage pbftMessage;

  public PbftBaseMessage() {
  }

  public PbftBaseMessage(byte type, byte[] data) throws IOException, P2pException {
    super(type, data);
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

  public PbftBaseMessage setPbftMessage(PbftMessage pbftMessage) {
    this.pbftMessage = pbftMessage;
    return this;
  }

  public PbftBaseMessage setData(byte[] data) {
    this.data = data;
    return this;
  }

  public PbftBaseMessage setType(byte type) {
    this.type = type;
    return this;
  }

  public String getKey() {
    return getNo() + "_" + Hex.toHexString(pbftMessage.getRawData().getPublicKey().toByteArray());
  }

  public String getDataKey() {
    return getNo() + "_" + Hex.toHexString(pbftMessage.getRawData().getData().toByteArray());
  }

  public abstract String getNo();

  public boolean validateSignature()
      throws SignatureException {
    byte[] hash = Sha256Hash.hash(getPbftMessage().getRawData().toByteArray());
    byte[] sigAddress = ECKey.signatureToAddress(hash, TransactionCapsule
        .getBase64FromByteString(getPbftMessage().getSign()));
    byte[] witnessAccountAddress = getPbftMessage().getRawData().getPublicKey().toByteArray();
    return Arrays.equals(sigAddress, witnessAccountAddress);
  }

  public PbftBaseMessage buildPrePareMessage() {
    return buildMessageCapsule(Type.PREPARE);
  }

  public PbftBaseMessage buildCommitMessage() {
    return buildMessageCapsule(Type.COMMIT);
  }

  private PbftBaseMessage buildMessageCapsule(Type type) {
    PbftBlockMessageCapsule pbftMessageCapsule = new PbftBlockMessageCapsule();
    LocalWitnesses localWitnesses = Args.getInstance().getLocalWitnesses();
    ECKey ecKey = ECKey.fromPrivate(ByteArray.fromHexString(localWitnesses.getPrivateKey()));
    PbftMessage.Builder builder = PbftMessage.newBuilder();
    Raw.Builder rawBuilder = PbftMessage.Raw.newBuilder();
    byte[] publicKey = localWitnesses.getPublicKey();
    rawBuilder.setBlockNum(getPbftMessage().getRawData().getBlockNum())
        .setPbftMsgType(type)
        .setTime(System.currentTimeMillis())
        .setPublicKey(ByteString.copyFrom(publicKey == null ? new byte[0] : publicKey))
        .setData(getPbftMessage().getRawData().getData());
    Raw raw = rawBuilder.build();
    byte[] hash = Sha256Hash.hash(raw.toByteArray());
    ECDSASignature signature = ecKey.sign(hash);
    builder.setRawData(raw).setSign(ByteString.copyFrom(signature.toByteArray()));
    PbftMessage message = builder.build();
    pbftMessageCapsule.setType(getType().asByte())
        .setPbftMessage(message).setData(message.toByteArray());
    return pbftMessageCapsule;
  }

  @Override
  public String toString() {
    return "PbftMsgType:" + pbftMessage.getRawData().getPbftMsgType()
        + ", node address:" + Hex.toHexString(pbftMessage.getRawData().getPublicKey().toByteArray())
        + ", block num:" + pbftMessage.getRawData().getBlockNum()
        + ", data:" + Hex.toHexString(pbftMessage.getRawData().getData().toByteArray())
        + ", " + super.toString();
  }
}
