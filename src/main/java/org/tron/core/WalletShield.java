package org.tron.core;

import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.api.GrpcAPI;
import org.tron.api.GrpcAPI.BlockList;
import org.tron.api.GrpcAPI.BytesMessage;
import org.tron.api.GrpcAPI.DecryptNotes;
import org.tron.api.GrpcAPI.DiversifierMessage;
import org.tron.api.GrpcAPI.ExpandedSpendingKeyMessage;
import org.tron.api.GrpcAPI.IncomingViewingKeyMessage;
import org.tron.api.GrpcAPI.NfParameters;
import org.tron.api.GrpcAPI.NoteParameters;
import org.tron.api.GrpcAPI.PaymentAddressMessage;
import org.tron.api.GrpcAPI.PrivateParameters;
import org.tron.api.GrpcAPI.PrivateParametersWithoutAsk;
import org.tron.api.GrpcAPI.ReceiveNote;
import org.tron.api.GrpcAPI.SpendAuthSigParameters;
import org.tron.api.GrpcAPI.SpendNote;
import org.tron.api.GrpcAPI.SpendResult;
import org.tron.common.utils.ByteArray;
import org.tron.common.zksnark.Librustzcash;
import org.tron.common.zksnark.LibrustzcashParam.ComputeNfParams;
import org.tron.common.zksnark.LibrustzcashParam.CrhIvkParams;
import org.tron.common.zksnark.LibrustzcashParam.IvkToPkdParams;
import org.tron.common.zksnark.LibrustzcashParam.SpendSigParams;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.capsule.BlockCapsule.BlockId;
import org.tron.core.capsule.IncrementalMerkleTreeCapsule;
import org.tron.core.capsule.IncrementalMerkleVoucherCapsule;
import org.tron.core.capsule.PedersenHashCapsule;
import org.tron.core.capsule.TransactionCapsule;
import org.tron.core.capsule.TransactionInfoCapsule;
import org.tron.core.config.args.Args;
import org.tron.core.db.Manager;
import org.tron.core.exception.BadItemException;
import org.tron.core.exception.ContractValidateException;
import org.tron.core.exception.ItemNotFoundException;
import org.tron.core.exception.ZksnarkException;
import org.tron.core.zen.ZenTransactionBuilder;
import org.tron.core.zen.address.DiversifierT;
import org.tron.core.zen.address.ExpandedSpendingKey;
import org.tron.core.zen.address.IncomingViewingKey;
import org.tron.core.zen.address.KeyIo;
import org.tron.core.zen.address.PaymentAddress;
import org.tron.core.zen.address.SpendingKey;
import org.tron.core.zen.merkle.IncrementalMerkleTreeContainer;
import org.tron.core.zen.merkle.IncrementalMerkleVoucherContainer;
import org.tron.core.zen.note.Note;
import org.tron.core.zen.note.NoteEncryption.Encryption;
import org.tron.core.zen.note.OutgoingPlaintext;
import org.tron.protos.Contract.IncrementalMerkleVoucherInfo;
import org.tron.protos.Contract.OutputPoint;
import org.tron.protos.Contract.OutputPointInfo;
import org.tron.protos.Contract.PedersenHash;
import org.tron.protos.Contract.ReceiveDescription;
import org.tron.protos.Contract.ShieldedTransferContract;
import org.tron.protos.Protocol;
import org.tron.protos.Protocol.Block;
import org.tron.protos.Protocol.Transaction;
import org.tron.protos.Protocol.Transaction.Contract;
import org.tron.protos.Protocol.Transaction.Contract.ContractType;

@Slf4j
@Component
public class WalletShield {

  @Autowired private Manager dbManager;

  public boolean getAllowShieldedTransactionApi() {
    return Args.getInstance().isAllowShieldedTransactionApi();
  }

  private void validateInput(OutputPointInfo request) throws BadItemException, ZksnarkException {
    if (!getAllowShieldedTransactionApi()) {
      throw new ZksnarkException("ShieldedTransactionApi is not allowed");
    }
    if (request.getBlockNum() < 0 || request.getBlockNum() > 1000) {
      throw new BadItemException("request.BlockNum must be range in【0，1000】");
    }

    if (request.getOutPointsCount() < 1 || request.getOutPointsCount() > 10) {
      throw new BadItemException("request.OutPointsCount must be range in【1，10】");
    }

    for (org.tron.protos.Contract.OutputPoint outputPoint : request.getOutPointsList()) {

      if (outputPoint.getHash() == null) {
        throw new BadItemException("outPoint.getHash() == null");
      }
      if (outputPoint.getIndex() >= Constant.ZC_OUTPUT_DESC_MAX_SIZE
          || outputPoint.getIndex() < 0) {
        throw new BadItemException(
            "outPoint.getIndex() > "
                + Constant.ZC_OUTPUT_DESC_MAX_SIZE
                + " || outPoint.getIndex() < 0");
      }
    }
  }

  public IncrementalMerkleVoucherInfo getMerkleTreeVoucherInfo(OutputPointInfo request)
      throws ItemNotFoundException, BadItemException, InvalidProtocolBufferException,
          ZksnarkException {
    if (!getAllowShieldedTransactionApi()) {
      throw new ZksnarkException("ShieldedTransactionApi is not allowed");
    }
    validateInput(request);
    IncrementalMerkleVoucherInfo.Builder result = IncrementalMerkleVoucherInfo.newBuilder();

    long largeBlockNum = 0;
    for (org.tron.protos.Contract.OutputPoint outputPoint : request.getOutPointsList()) {
      Long blockNum1 = getBlockNumber(outputPoint);
      if (blockNum1 > largeBlockNum) {
        largeBlockNum = blockNum1;
      }
    }

    logger.debug("largeBlockNum:" + largeBlockNum);
    int opIndex = 0;

    List<IncrementalMerkleVoucherContainer> witnessList = Lists.newArrayList();
    for (org.tron.protos.Contract.OutputPoint outputPoint : request.getOutPointsList()) {
      Long blockNum1 = getBlockNumber(outputPoint);
      logger.debug("blockNum:" + blockNum1 + ",opIndex:" + opIndex++);
      if (blockNum1 + 100 < largeBlockNum) {
        throw new RuntimeException(
            "blockNum:" + blockNum1 + " + 100 < largeBlockNum:" + largeBlockNum);
      }
      IncrementalMerkleVoucherContainer witness = createWitness(outputPoint, blockNum1);
      updateLowWitness(witness, blockNum1, largeBlockNum);
      witnessList.add(witness);
    }

    int synBlockNum = request.getBlockNum();
    if (synBlockNum != 0) {
      // According to the blockNum in the request, obtain the block before [block2+1, blockNum], and
      // update the two witnesses.
      updateWitnesses(witnessList, largeBlockNum + 1, synBlockNum);
    }

    for (IncrementalMerkleVoucherContainer w : witnessList) {
      w.getVoucherCapsule().resetRt();
      result.addVouchers(w.getVoucherCapsule().getInstance());
      result.addPaths(ByteString.copyFrom(w.path().encode()));
    }

    return result.build();
  }

  public long getShieldedTransactionFee() {
    return dbManager.getDynamicPropertiesStore().getShieldedTransactionFee();
  }

  public TransactionCapsule createShieldedTransaction(PrivateParameters request)
      throws ContractValidateException, RuntimeException, ZksnarkException {
    if (!getAllowShieldedTransactionApi()) {
      throw new ZksnarkException("createshieldedtransaction is not allowed");
    }
    ZenTransactionBuilder builder = new ZenTransactionBuilder(this);

    byte[] transparentFromAddress = request.getTransparentFromAddress().toByteArray();
    byte[] ask = request.getAsk().toByteArray();
    byte[] nsk = request.getNsk().toByteArray();
    byte[] ovk = request.getOvk().toByteArray();

    if (ArrayUtils.isEmpty(transparentFromAddress)
        && (ArrayUtils.isEmpty(ask) || ArrayUtils.isEmpty(nsk) || ArrayUtils.isEmpty(ovk))) {
      throw new ContractValidateException("No input address");
    }

    long fromAmount = request.getFromAmount();
    if (!ArrayUtils.isEmpty(transparentFromAddress) && fromAmount <= 0) {
      throw new ContractValidateException("Input amount must > 0");
    }

    List<SpendNote> shieldedSpends = request.getShieldedSpendsList();
    if (!(ArrayUtils.isEmpty(ask) || ArrayUtils.isEmpty(nsk) || ArrayUtils.isEmpty(ovk))
        && shieldedSpends.isEmpty()) {
      throw new ContractValidateException("No input note");
    }

    List<ReceiveNote> shieldedReceives = request.getShieldedReceivesList();
    byte[] transparentToAddress = request.getTransparentToAddress().toByteArray();
    if (shieldedReceives.isEmpty() && ArrayUtils.isEmpty(transparentToAddress)) {
      throw new ContractValidateException("No output address");
    }

    long toAmount = request.getToAmount();
    if (!ArrayUtils.isEmpty(transparentToAddress) && toAmount <= 0) {
      throw new ContractValidateException("Output amount must > 0");
    }

    // add
    if (!ArrayUtils.isEmpty(transparentFromAddress)) {
      builder.setTransparentInput(transparentFromAddress, fromAmount);
    }

    if (!ArrayUtils.isEmpty(transparentToAddress)) {
      builder.setTransparentOutput(transparentToAddress, toAmount);
    }

    // input
    if (!(ArrayUtils.isEmpty(ask) || ArrayUtils.isEmpty(nsk) || ArrayUtils.isEmpty(ovk))) {
      ExpandedSpendingKey expsk = new ExpandedSpendingKey(ask, nsk, ovk);
      for (SpendNote spendNote : shieldedSpends) {
        GrpcAPI.Note note = spendNote.getNote();
        PaymentAddress paymentAddress = KeyIo.decodePaymentAddress(note.getPaymentAddress());
        if (paymentAddress == null) {
          throw new ZksnarkException("paymentAddress format is wrong");
        }
        Note baseNote =
            new Note(
                paymentAddress.getD(),
                paymentAddress.getPkD(),
                note.getValue(),
                note.getRcm().toByteArray());

        IncrementalMerkleVoucherContainer voucherContainer =
            new IncrementalMerkleVoucherCapsule(spendNote.getVoucher()).toMerkleVoucherContainer();
        builder.addSpend(
            expsk,
            baseNote,
            spendNote.getAlpha().toByteArray(),
            spendNote.getVoucher().getRt().toByteArray(),
            voucherContainer);
      }
    }

    // output
    for (ReceiveNote receiveNote : shieldedReceives) {
      PaymentAddress paymentAddress =
          KeyIo.decodePaymentAddress(receiveNote.getNote().getPaymentAddress());
      if (paymentAddress == null) {
        throw new ZksnarkException("paymentAddress format is wrong");
      }
      builder.addOutput(
          ovk,
          paymentAddress.getD(),
          paymentAddress.getPkD(),
          receiveNote.getNote().getValue(),
          receiveNote.getNote().getRcm().toByteArray(),
          receiveNote.getNote().getMemo().toByteArray());
    }

    TransactionCapsule transactionCapsule = null;
    try {
      transactionCapsule = builder.build();
    } catch (ZksnarkException e) {
      logger.error("createShieldedTransaction except, error is " + e.toString());
    }
    return transactionCapsule;
  }

  public TransactionCapsule createShieldedTransactionWithoutSpendAuthSig(
      PrivateParametersWithoutAsk request) throws ContractValidateException, ZksnarkException {
    if (!getAllowShieldedTransactionApi()) {
      throw new ZksnarkException("createshieldedtransactionwithoutspendauthsig is not allowed");
    }

    ZenTransactionBuilder builder = new ZenTransactionBuilder(this);

    byte[] transparentFromAddress = request.getTransparentFromAddress().toByteArray();
    byte[] ak = request.getAk().toByteArray();
    byte[] nsk = request.getNsk().toByteArray();
    byte[] ovk = request.getOvk().toByteArray();

    if (ArrayUtils.isEmpty(transparentFromAddress)
        && (ArrayUtils.isEmpty(ak) || ArrayUtils.isEmpty(nsk) || ArrayUtils.isEmpty(ovk))) {
      throw new ContractValidateException("No input address");
    }

    long fromAmount = request.getFromAmount();
    if (!ArrayUtils.isEmpty(transparentFromAddress) && fromAmount <= 0) {
      throw new ContractValidateException("Input amount must > 0");
    }

    List<SpendNote> shieldedSpends = request.getShieldedSpendsList();
    if (!(ArrayUtils.isEmpty(ak) || ArrayUtils.isEmpty(nsk) || ArrayUtils.isEmpty(ovk))
        && shieldedSpends.isEmpty()) {
      throw new ContractValidateException("No input note");
    }

    List<ReceiveNote> shieldedReceives = request.getShieldedReceivesList();
    byte[] transparentToAddress = request.getTransparentToAddress().toByteArray();
    if (shieldedReceives.isEmpty() && ArrayUtils.isEmpty(transparentToAddress)) {
      throw new ContractValidateException("No output address");
    }

    long toAmount = request.getToAmount();
    if (!ArrayUtils.isEmpty(transparentToAddress) && toAmount <= 0) {
      throw new ContractValidateException("Output amount must > 0");
    }

    // add
    if (!ArrayUtils.isEmpty(transparentFromAddress)) {
      builder.setTransparentInput(transparentFromAddress, fromAmount);
    }

    if (!ArrayUtils.isEmpty(transparentToAddress)) {
      builder.setTransparentOutput(transparentToAddress, toAmount);
    }

    // input
    if (!(ArrayUtils.isEmpty(ak) || ArrayUtils.isEmpty(nsk) || ArrayUtils.isEmpty(ovk))) {
      for (SpendNote spendNote : shieldedSpends) {
        GrpcAPI.Note note = spendNote.getNote();
        PaymentAddress paymentAddress = KeyIo.decodePaymentAddress(note.getPaymentAddress());
        if (paymentAddress == null) {
          throw new ZksnarkException("paymentAddress format is wrong");
        }
        Note baseNote =
            new Note(
                paymentAddress.getD(),
                paymentAddress.getPkD(),
                note.getValue(),
                note.getRcm().toByteArray());

        IncrementalMerkleVoucherContainer voucherContainer =
            new IncrementalMerkleVoucherCapsule(spendNote.getVoucher()).toMerkleVoucherContainer();
        builder.addSpend(
            ak,
            nsk,
            ovk,
            baseNote,
            spendNote.getAlpha().toByteArray(),
            spendNote.getVoucher().getRt().toByteArray(),
            voucherContainer);
      }
    }

    // output
    for (ReceiveNote receiveNote : shieldedReceives) {
      PaymentAddress paymentAddress =
          KeyIo.decodePaymentAddress(receiveNote.getNote().getPaymentAddress());
      if (paymentAddress == null) {
        throw new ZksnarkException("paymentAddress format is wrong");
      }
      builder.addOutput(
          ovk,
          paymentAddress.getD(),
          paymentAddress.getPkD(),
          receiveNote.getNote().getValue(),
          receiveNote.getNote().getRcm().toByteArray(),
          receiveNote.getNote().getMemo().toByteArray());
    }

    TransactionCapsule transactionCapsule = null;
    try {
      transactionCapsule = builder.buildWithoutAsk();
    } catch (ZksnarkException e) {
      logger.error("createShieldedTransaction except, error is " + e.toString());
    }
    return transactionCapsule;
  }

  public BytesMessage getSpendingKey() throws ZksnarkException {
    if (!getAllowShieldedTransactionApi()) {
      throw new ZksnarkException("ShieldedTransactionApi is not allowed");
    }
    byte[] sk = SpendingKey.random().getValue();
    return BytesMessage.newBuilder().setValue(ByteString.copyFrom(sk)).build();
  }

  public ExpandedSpendingKeyMessage getExpandedSpendingKey(ByteString spendingKey)
      throws BadItemException, ZksnarkException {
    if (!getAllowShieldedTransactionApi()) {
      throw new ZksnarkException("ShieldedTransactionApi is not allowed");
    }
    if (Objects.isNull(spendingKey)) {
      throw new BadItemException("spendingKey is null");
    }
    if (ByteArray.toHexString(spendingKey.toByteArray()).length() != 64) {
      throw new BadItemException("the length of spendingKey's hexstring should be 64");
    }

    ExpandedSpendingKey expandedSpendingKey = null;
    SpendingKey sk = new SpendingKey(spendingKey.toByteArray());
    expandedSpendingKey = sk.expandedSpendingKey();

    ExpandedSpendingKeyMessage.Builder responseBuild = ExpandedSpendingKeyMessage.newBuilder();
    responseBuild
        .setAsk(ByteString.copyFrom(expandedSpendingKey.getAsk()))
        .setNsk(ByteString.copyFrom(expandedSpendingKey.getNsk()))
        .setOvk(ByteString.copyFrom(expandedSpendingKey.getOvk()));

    return responseBuild.build();
  }

  public BytesMessage getAkFromAsk(ByteString ask) throws BadItemException, ZksnarkException {
    if (!getAllowShieldedTransactionApi()) {
      throw new ZksnarkException("ShieldedTransactionApi is not allowed");
    }
    if (Objects.isNull(ask)) {
      throw new BadItemException("ask is null");
    }
    if (ByteArray.toHexString(ask.toByteArray()).length() != 64) {
      throw new BadItemException("the length of ask's hexstring should be 64");
    }

    byte[] ak = ExpandedSpendingKey.getAkFromAsk(ask.toByteArray());
    return BytesMessage.newBuilder().setValue(ByteString.copyFrom(ak)).build();
  }

  public BytesMessage getNkFromNsk(ByteString nsk) throws BadItemException, ZksnarkException {
    if (!getAllowShieldedTransactionApi()) {
      throw new ZksnarkException("ShieldedTransactionApi is not allowed");
    }
    if (Objects.isNull(nsk)) {
      throw new BadItemException("nsk is null");
    }
    if (ByteArray.toHexString(nsk.toByteArray()).length() != 64) {
      throw new BadItemException("the length of nsk's hexstring should be 64");
    }

    byte[] nk = ExpandedSpendingKey.getNkFromNsk(nsk.toByteArray());
    return BytesMessage.newBuilder().setValue(ByteString.copyFrom(nk)).build();
  }

  public IncomingViewingKeyMessage getIncomingViewingKey(byte[] ak, byte[] nk)
      throws ZksnarkException {
    if (!getAllowShieldedTransactionApi()) {
      throw new ZksnarkException("ShieldedTransactionApi is not allowed");
    }
    //    if (ak.length != 32 || nk.length != 32) {
    //      throw new BadItemException("the byte length of ak and nk should be 32");
    //    }

    byte[] ivk = new byte[32]; // the incoming viewing key
    Librustzcash.librustzcashCrhIvk(new CrhIvkParams(ak, nk, ivk));
    return IncomingViewingKeyMessage.newBuilder().setIvk(ByteString.copyFrom(ivk)).build();
  }

  public DiversifierMessage getDiversifier() throws ZksnarkException {
    if (!getAllowShieldedTransactionApi()) {
      throw new ZksnarkException("ShieldedTransactionApi is not allowed");
    }
    byte[] d;
    while (true) {
      d = org.tron.keystore.Wallet.generateRandomBytes(Constant.ZC_DIVERSIFIER_SIZE);
      if (Librustzcash.librustzcashCheckDiversifier(d)) {
        break;
      }
    }
    DiversifierMessage diversifierMessage =
        DiversifierMessage.newBuilder().setD(ByteString.copyFrom(d)).build();

    return diversifierMessage;
  }

  public BytesMessage getRcm() throws ZksnarkException {
    if (!getAllowShieldedTransactionApi()) {
      throw new ZksnarkException("ShieldedTransactionApi is not allowed");
    }
    byte[] rcm = Note.generateR();
    return BytesMessage.newBuilder().setValue(ByteString.copyFrom(rcm)).build();
  }

  public PaymentAddressMessage getPaymentAddress(IncomingViewingKey ivk, DiversifierT d)
      throws BadItemException, ZksnarkException {
    if (!getAllowShieldedTransactionApi()) {
      throw new ZksnarkException("ShieldedTransactionApi is not allowed");
    }

    PaymentAddressMessage spa = null;

    if (!Librustzcash.librustzcashCheckDiversifier(d.getData())) {
      throw new BadItemException("d is not valid");
    }

    Optional<PaymentAddress> op = ivk.address(d);
    if (op.isPresent()) {
      DiversifierMessage ds =
          DiversifierMessage.newBuilder().setD(ByteString.copyFrom(d.getData())).build();

      PaymentAddress paymentAddress = op.get();
      spa =
          PaymentAddressMessage.newBuilder()
              .setD(ds)
              .setPkD(ByteString.copyFrom(paymentAddress.getPkD()))
              .setPaymentAddress(KeyIo.encodePaymentAddress(paymentAddress))
              .build();
    }
    return spa;
  }

  public SpendResult isSpend(NoteParameters noteParameters) throws ZksnarkException {
    if (!getAllowShieldedTransactionApi()) {
      throw new ZksnarkException("ShieldedTransactionApi is not allowed");
    }
    GrpcAPI.Note note = noteParameters.getNote();
    byte[] ak = noteParameters.getAk().toByteArray();
    byte[] nk = noteParameters.getNk().toByteArray();
    PaymentAddress paymentAddress = KeyIo.decodePaymentAddress(note.getPaymentAddress());
    if (paymentAddress == null) {
      throw new ZksnarkException("paymentAddress format is wrong");
    }
    Note baseNote =
        new Note(
            paymentAddress.getD(),
            paymentAddress.getPkD(),
            note.getValue(),
            note.getRcm().toByteArray());

    IncrementalMerkleVoucherContainer voucherContainer =
        new IncrementalMerkleVoucherCapsule(noteParameters.getVoucher()).toMerkleVoucherContainer();

    byte[] nf = baseNote.nullifier(ak, nk, voucherContainer.position());

    SpendResult result;
    if (dbManager.getNullfierStore().has(nf)) {
      result =
          SpendResult.newBuilder().setResult(true).setMessage("input note already spent").build();
    } else {
      result =
          SpendResult.newBuilder()
              .setResult(false)
              .setMessage("input note not spent or not exists")
              .build();
    }

    return result;
  }

  public BytesMessage createSpendAuthSig(SpendAuthSigParameters spendAuthSigParameters)
      throws ZksnarkException {
    if (!getAllowShieldedTransactionApi()) {
      throw new ZksnarkException("ShieldedTransactionApi is not allowed");
    }
    byte[] result = new byte[64];
    SpendSigParams spendSigPasrams =
        new SpendSigParams(
            spendAuthSigParameters.getAsk().toByteArray(),
            spendAuthSigParameters.getAlpha().toByteArray(),
            spendAuthSigParameters.getTxHash().toByteArray(),
            result);
    Librustzcash.librustzcashSaplingSpendSig(spendSigPasrams);

    return BytesMessage.newBuilder().setValue(ByteString.copyFrom(result)).build();
  }

  public BytesMessage createShieldNullifier(NfParameters nfParameters) throws ZksnarkException {
    if (!getAllowShieldedTransactionApi()) {
      throw new ZksnarkException("ShieldedTransactionApi is not allowed");
    }
    byte[] ak = nfParameters.getAk().toByteArray();
    byte[] nk = nfParameters.getNk().toByteArray();

    byte[] result = new byte[32]; // 256
    GrpcAPI.Note note = nfParameters.getNote();
    IncrementalMerkleVoucherCapsule incrementalMerkleVoucherCapsule =
        new IncrementalMerkleVoucherCapsule(nfParameters.getVoucher());
    IncrementalMerkleVoucherContainer incrementalMerkleVoucherContainer =
        new IncrementalMerkleVoucherContainer(incrementalMerkleVoucherCapsule);
    PaymentAddress paymentAddress = KeyIo.decodePaymentAddress(note.getPaymentAddress());
    if (paymentAddress == null) {
      throw new ZksnarkException("paymentAddress format is wrong");
    }
    ComputeNfParams computeNfParams =
        new ComputeNfParams(
            paymentAddress.getD().getData(),
            paymentAddress.getPkD(),
            note.getValue(),
            note.getRcm().toByteArray(),
            ak,
            nk,
            incrementalMerkleVoucherContainer.position(),
            result);
    if (!Librustzcash.librustzcashComputeNf(computeNfParams)) {
      return null;
    }

    return BytesMessage.newBuilder().setValue(ByteString.copyFrom(result)).build();
  }

  public BytesMessage getShieldTransactionHash(Transaction transaction)
      throws ContractValidateException {
    List<Contract> contract = transaction.getRawData().getContractList();
    if (contract == null || contract.size() == 0) {
      throw new ContractValidateException("contract is null");
    }
    ContractType contractType = contract.get(0).getType();
    if (contractType != ContractType.ShieldedTransferContract) {
      throw new ContractValidateException("Not a shielded transaction");
    }
    TransactionCapsule transactionCapsule = new TransactionCapsule(transaction);
    byte[] transactionHash =
        TransactionCapsule.getShieldTransactionHashIgnoreTypeException(transactionCapsule);
    return BytesMessage.newBuilder().setValue(ByteString.copyFrom(transactionHash)).build();
  }

  private long getBlockNumber(OutputPoint outPoint)
      throws ItemNotFoundException, BadItemException, InvalidProtocolBufferException,
          ZksnarkException {
    if (!getAllowShieldedTransactionApi()) {
      throw new ZksnarkException("ShieldedTransactionApi is not allowed");
    }
    ByteString txId = outPoint.getHash();

    // Get blockNum from transactionInfo
    TransactionInfoCapsule transactionInfoCapsule1 =
        dbManager.getTransactionHistoryStore().get(txId.toByteArray());
    if (transactionInfoCapsule1 == null) {
      throw new RuntimeException("tx is not found:" + ByteArray.toHexString(txId.toByteArray()));
    }
    return transactionInfoCapsule1.getBlockNumber();
  }

  // in:outPoint,out:blockNumber
  private IncrementalMerkleVoucherContainer createWitness(OutputPoint outPoint, Long blockNumber)
      throws ItemNotFoundException, BadItemException, InvalidProtocolBufferException,
          ZksnarkException {
    if (!getAllowShieldedTransactionApi()) {
      throw new ZksnarkException("ShieldedTransactionApi is not allowed");
    }
    ByteString txId = outPoint.getHash();

    // Get the tree in blockNum-1 position
    byte[] treeRoot = dbManager.getMerkleTreeIndexStore().get(blockNumber - 1);
    if (treeRoot == null) {
      throw new RuntimeException("treeRoot is null,blockNumber:" + (blockNumber - 1));
    }

    IncrementalMerkleTreeCapsule treeCapsule = dbManager.getMerkleTreeStore().get(treeRoot);
    if (treeCapsule == null) {
      if (ByteArray.toHexString(treeRoot)
          .equals("fbc2f4300c01f0b7820d00e3347c8da4ee614674376cbc45359daa54f9b5493e")) {
        treeCapsule = new IncrementalMerkleTreeCapsule();
      } else {
        throw new RuntimeException("tree is null,treeRoot:" + ByteArray.toHexString(treeRoot));
      }
    }
    IncrementalMerkleTreeContainer tree = treeCapsule.toMerkleTreeContainer();

    // Get the block of blockNum
    BlockCapsule block = dbManager.getBlockByNum(blockNumber);

    IncrementalMerkleVoucherContainer witness = null;

    // get the witness in three parts
    boolean found = false;
    for (Transaction transaction : block.getInstance().getTransactionsList()) {

      Contract contract = transaction.getRawData().getContract(0);
      if (contract.getType() == ContractType.ShieldedTransferContract) {
        ShieldedTransferContract zkContract =
            contract.getParameter().unpack(ShieldedTransferContract.class);

        if (new TransactionCapsule(transaction).getTransactionId().getByteString().equals(txId)) {
          found = true;

          if (outPoint.getIndex() >= zkContract.getReceiveDescriptionCount()) {
            throw new RuntimeException(
                "outPoint.getIndex():"
                    + outPoint.getIndex()
                    + " >= zkContract.getReceiveDescriptionCount():"
                    + zkContract.getReceiveDescriptionCount());
          }

          int index = 0;
          for (ReceiveDescription receiveDescription : zkContract.getReceiveDescriptionList()) {
            PedersenHashCapsule cmCapsule = new PedersenHashCapsule();
            cmCapsule.setContent(receiveDescription.getNoteCommitment());
            PedersenHash cm = cmCapsule.getInstance();

            if (index < outPoint.getIndex()) {
              tree.append(cm);
            } else if (outPoint.getIndex() == index) {
              tree.append(cm);
              witness = tree.getTreeCapsule().deepCopy().toMerkleTreeContainer().toVoucher();
            } else {
              witness.append(cm);
            }

            index++;
          }

        } else {
          for (org.tron.protos.Contract.ReceiveDescription receiveDescription :
              zkContract.getReceiveDescriptionList()) {
            PedersenHashCapsule cmCapsule = new PedersenHashCapsule();
            cmCapsule.setContent(receiveDescription.getNoteCommitment());
            PedersenHash cm = cmCapsule.getInstance();
            if (witness != null) {
              witness.append(cm);
            } else {
              tree.append(cm);
            }
          }
        }
      }
    }

    if (!found) {
      throw new RuntimeException("not found cm");
    }

    return witness;
  }

  private void updateWitnesses(
      List<IncrementalMerkleVoucherContainer> witnessList, long large, int synBlockNum)
      throws ItemNotFoundException, BadItemException, InvalidProtocolBufferException,
          ZksnarkException {
    if (!getAllowShieldedTransactionApi()) {
      throw new ZksnarkException("ShieldedTransactionApi is not allowed");
    }
    long start = large;
    long end = large + synBlockNum - 1;

    long latestBlockHeaderNumber =
        dbManager.getDynamicPropertiesStore().getLatestBlockHeaderNumber();

    if (end > latestBlockHeaderNumber) {
      throw new RuntimeException(
          "synBlockNum is too large, cmBlockNum plus synBlockNum must be <= latestBlockNumber");
    }

    for (long n = start; n <= end; n++) {
      BlockCapsule block = dbManager.getBlockByNum(n);
      for (Transaction transaction1 : block.getInstance().getTransactionsList()) {

        Contract contract1 = transaction1.getRawData().getContract(0);
        if (contract1.getType() == ContractType.ShieldedTransferContract) {

          ShieldedTransferContract zkContract =
              contract1.getParameter().unpack(ShieldedTransferContract.class);

          for (org.tron.protos.Contract.ReceiveDescription receiveDescription :
              zkContract.getReceiveDescriptionList()) {

            PedersenHashCapsule cmCapsule = new PedersenHashCapsule();
            cmCapsule.setContent(receiveDescription.getNoteCommitment());
            PedersenHash cm = cmCapsule.getInstance();
            for (IncrementalMerkleVoucherContainer wit : witnessList) {
              wit.append(cm);
            }
          }
        }
      }
    }
  }

  private void updateLowWitness(
      IncrementalMerkleVoucherContainer witness, long blockNum1, long blockNum2)
      throws ItemNotFoundException, BadItemException, InvalidProtocolBufferException,
          ZksnarkException {
    long start;
    long end;
    if (blockNum1 < blockNum2) {
      start = blockNum1 + 1;
      end = blockNum2;
    } else {
      return;
    }

    for (long n = start; n <= end; n++) {
      BlockCapsule block = dbManager.getBlockByNum(n);
      for (Transaction transaction1 : block.getInstance().getTransactionsList()) {

        Contract contract1 = transaction1.getRawData().getContract(0);
        if (contract1.getType() == ContractType.ShieldedTransferContract) {

          ShieldedTransferContract zkContract =
              contract1.getParameter().unpack(ShieldedTransferContract.class);

          for (org.tron.protos.Contract.ReceiveDescription receiveDescription :
              zkContract.getReceiveDescriptionList()) {

            PedersenHashCapsule cmCapsule = new PedersenHashCapsule();
            cmCapsule.setContent(receiveDescription.getNoteCommitment());
            PedersenHash cm = cmCapsule.getInstance();
            witness.append(cm);
          }
        }
      }
    }
  }

  public BlockList getBlocksByLimitNext(long number, long limit) {
    if (limit <= 0) {
      return null;
    }
    BlockList.Builder blockListBuilder = BlockList.newBuilder();
    dbManager
        .getBlockStore()
        .getLimitNumber(number, limit)
        .forEach(blockCapsule -> blockListBuilder.addBlock(blockCapsule.getInstance()));
    return blockListBuilder.build();
  }

  /*
   * try to get cm belongs to ivk
   */
  public GrpcAPI.DecryptNotes scanNoteByIvk(long startNum, long endNum, byte[] ivk)
      throws BadItemException, ZksnarkException {
    if (!getAllowShieldedTransactionApi()) {
      throw new ZksnarkException("ShieldedTransactionApi is not allowed");
    }
    GrpcAPI.DecryptNotes.Builder builder = GrpcAPI.DecryptNotes.newBuilder();
    if (!(startNum >= 0 && endNum > startNum && endNum - startNum <= 1000)) {
      throw new BadItemException(
          "request require startNum >= 0 && endNum > startNum && endNum - startNum <= 1000");
    }

    BlockList blockList = this.getBlocksByLimitNext(startNum, endNum - startNum);
    for (Block block : blockList.getBlockList()) {
      for (Transaction transaction : block.getTransactionsList()) {
        TransactionCapsule transactionCapsule = new TransactionCapsule(transaction);
        byte[] txid = transactionCapsule.getTransactionId().getBytes();
        List<Transaction.Contract> contracts = transaction.getRawData().getContractList();
        if (contracts.size() == 0) {
          continue;
        }
        Transaction.Contract c = contracts.get(0);
        if (c.getType() != Contract.ContractType.ShieldedTransferContract) {
          continue;
        }
        ShieldedTransferContract stContract = null;
        try {
          stContract =
              c.getParameter().unpack(org.tron.protos.Contract.ShieldedTransferContract.class);
        } catch (InvalidProtocolBufferException e) {
          throw new ZksnarkException("unpack ShieldedTransferContract failed.");
        }

        for (int index = 0; index < stContract.getReceiveDescriptionList().size(); index++) {
          ReceiveDescription r = stContract.getReceiveDescription(index);
          Optional<Note> notePlaintext =
              Note.decrypt(
                  r.getCEnc().toByteArray(), // ciphertext
                  ivk,
                  r.getEpk().toByteArray(), // epk
                  r.getNoteCommitment().toByteArray() // cmu
                  );

          if (notePlaintext.isPresent()) {
            Note noteText = notePlaintext.get();

            byte[] pk_d = new byte[32];
            if (!Librustzcash.librustzcashIvkToPkd(
                new IvkToPkdParams(ivk, noteText.d.getData(), pk_d))) {
              continue;
            }

            String paymentAddress =
                KeyIo.encodePaymentAddress(new PaymentAddress(noteText.d, pk_d));
            GrpcAPI.Note note =
                GrpcAPI.Note.newBuilder()
                    .setPaymentAddress(paymentAddress)
                    .setValue(noteText.value)
                    .setRcm(ByteString.copyFrom(noteText.rcm))
                    .setMemo(ByteString.copyFrom(noteText.memo))
                    .build();
            DecryptNotes.NoteTx noteTx =
                DecryptNotes.NoteTx.newBuilder()
                    .setNote(note)
                    .setTxid(ByteString.copyFrom(txid))
                    .setIndex(index)
                    .build();

            builder.addNoteTxs(noteTx);
          }
        } // end of ReceiveDescriptionList
      } // end of transaction
    } // end of blocklist
    return builder.build();
  }

  /*
   * try to get cm belongs to ovk
   */
  public GrpcAPI.DecryptNotes scanNoteByOvk(long startNum, long endNum, byte[] ovk)
      throws BadItemException, ZksnarkException {
    if (!getAllowShieldedTransactionApi()) {
      throw new ZksnarkException("ShieldedTransactionApi is not allowed");
    }
    GrpcAPI.DecryptNotes.Builder builder = GrpcAPI.DecryptNotes.newBuilder();
    if (!(startNum >= 0 && endNum > startNum && endNum - startNum <= 1000)) {
      throw new BadItemException(
          "request require startNum >= 0 && endNum > startNum && endNum - startNum <= 1000");
    }
    BlockList blockList = this.getBlocksByLimitNext(startNum, endNum - startNum);
    for (Block block : blockList.getBlockList()) {
      for (Transaction transaction : block.getTransactionsList()) {
        TransactionCapsule transactionCapsule = new TransactionCapsule(transaction);
        byte[] txid = transactionCapsule.getTransactionId().getBytes();
        List<Transaction.Contract> contracts = transaction.getRawData().getContractList();
        if (contracts.size() == 0) {
          continue;
        }
        Transaction.Contract c = contracts.get(0);
        if (c.getType() != Protocol.Transaction.Contract.ContractType.ShieldedTransferContract) {
          continue;
        }
        ShieldedTransferContract stContract = null;
        try {
          stContract =
              c.getParameter().unpack(org.tron.protos.Contract.ShieldedTransferContract.class);
        } catch (InvalidProtocolBufferException e) {
          throw new RuntimeException("unpack ShieldedTransferContract failed.");
        }

        for (int index = 0; index < stContract.getReceiveDescriptionList().size(); index++) {
          ReceiveDescription r = stContract.getReceiveDescription(index);
          Encryption.OutCiphertext c_out = new Encryption.OutCiphertext();
          c_out.data = r.getCOut().toByteArray();
          Optional<OutgoingPlaintext> notePlaintext =
              OutgoingPlaintext.decrypt(
                  c_out, // ciphertext
                  ovk,
                  r.getValueCommitment().toByteArray(), // cv
                  r.getNoteCommitment().toByteArray(), // cmu
                  r.getEpk().toByteArray() // epk
                  );

          if (notePlaintext.isPresent()) {
            OutgoingPlaintext decrypted_out_ct_unwrapped = notePlaintext.get();
            // decode c_enc with pkd、esk
            Encryption.EncCiphertext ciphertext = new Encryption.EncCiphertext();
            ciphertext.data = r.getCEnc().toByteArray();
            Optional<Note> foo =
                Note.decrypt(
                    ciphertext,
                    r.getEpk().toByteArray(),
                    decrypted_out_ct_unwrapped.esk,
                    decrypted_out_ct_unwrapped.pk_d,
                    r.getNoteCommitment().toByteArray());

            if (foo.isPresent()) {
              Note bar = foo.get();
              String paymentAddress =
                  KeyIo.encodePaymentAddress(
                      new PaymentAddress(bar.d, decrypted_out_ct_unwrapped.pk_d));
              GrpcAPI.Note note =
                  GrpcAPI.Note.newBuilder()
                      .setPaymentAddress(paymentAddress)
                      .setValue(bar.value)
                      .setRcm(ByteString.copyFrom(bar.rcm))
                      .setMemo(ByteString.copyFrom(bar.memo))
                      .build();

              DecryptNotes.NoteTx noteTx =
                  DecryptNotes.NoteTx.newBuilder()
                      .setNote(note)
                      .setTxid(ByteString.copyFrom(txid))
                      .setIndex(index)
                      .build();

              builder.addNoteTxs(noteTx);
            }
          }
        } // end of ReceiveDescriptionList
      } // end of transaction
    } // end of blocklist
    return builder.build();
  }

  public TransactionCapsule createTransactionCapsuleWithoutValidate(
      com.google.protobuf.Message message, ContractType contractType) {
    TransactionCapsule trx = new TransactionCapsule(message, contractType);
    try {
      BlockId blockId = dbManager.getHeadBlockId();
      if (Args.getInstance().getTrxReferenceBlock().equals("solid")) {
        blockId = dbManager.getSolidBlockId();
      }
      trx.setReference(blockId.getNum(), blockId.getBytes());
      long expiration =
          dbManager.getHeadBlockTimeStamp()
              + Args.getInstance().getTrxExpirationTimeInMilliseconds();
      trx.setExpiration(expiration);
      trx.setTimestamp();
    } catch (Exception e) {
      logger.error("Create transaction capsule failed.", e);
    }
    return trx;
  }
}
