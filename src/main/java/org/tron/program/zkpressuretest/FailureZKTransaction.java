package org.tron.program.zkpressuretest;

import com.google.protobuf.ByteString;
import org.tron.common.utils.ByteArray;
import org.tron.core.Wallet;
import org.tron.core.capsule.IncrementalMerkleTreeCapsule;
import org.tron.core.capsule.PedersenHashCapsule;
import org.tron.core.capsule.TransactionCapsule;
import org.tron.core.exception.BadItemException;
import org.tron.core.exception.ZksnarkException;
import org.tron.core.zen.ZenTransactionBuilder;
import org.tron.core.zen.address.ExpandedSpendingKey;
import org.tron.core.zen.address.FullViewingKey;
import org.tron.core.zen.address.IncomingViewingKey;
import org.tron.core.zen.address.PaymentAddress;
import org.tron.core.zen.address.SpendingKey;
import org.tron.core.zen.merkle.IncrementalMerkleTreeContainer;
import org.tron.core.zen.merkle.IncrementalMerkleVoucherContainer;
import org.tron.core.zen.note.Note;
import org.tron.program.ZKtool;
import org.tron.protos.Contract.PedersenHash;

public class FailureZKTransaction {
//  private static final String OWNER_ADDRESS = "TRGhNNfnmgLegT4zHNjEqDSADjgmnHvubJ";
//  private static final String OWNER_PRIVATE_KEY =
//      "7f7f701e94d4f1dd60ee5205e7ea8ee31121427210417b608a6b2e96433549a7";

//  private static final String TO_ADDRESS = "TXtrbmfwZ2LxtoCveEhZT86fTss1w8rwJE";
//  private static final String TO_PRIVATE_KEY =
//      "0528dc17428585fc4dece68b79fa7912270a1fe8e85f244372f59eb7e8925e04";
//
//  private static final String OWNER_ADDRESS = "TDQE4yb3E7dvDjouvu8u7GgSnMZbxAEumV";
//  private static final String OWNER_PRIVATE_KEY =
//      "85a449304487085205d48a402c30877e888fcb34391d65cfdc9cad420127826f";


  private static final String OWNER_ADDRESS = "TXtrbmfwZ2LxtoCveEhZT86fTss1w8rwJE";
  private static final String OWNER_PRIVATE_KEY =
      "0528dc17428585fc4dece68b79fa7912270a1fe8e85f244372f59eb7e8925e04";

  private static final String TO_ADDRESS = "TRGhNNfnmgLegT4zHNjEqDSADjgmnHvubJ";
  private static final String TO_PRIVATE_KEY =
      "7f7f701e94d4f1dd60ee5205e7ea8ee31121427210417b608a6b2e96433549a7";

  private static final long SHIELD_FEE = 10_000_000L;

  private static IncrementalMerkleVoucherContainer createSimpleMerkleVoucherContainer(byte[] cm)
      throws ZksnarkException {
    IncrementalMerkleTreeContainer tree =
        new IncrementalMerkleTreeContainer(new IncrementalMerkleTreeCapsule());
    PedersenHashCapsule compressCapsule1 = new PedersenHashCapsule();
    compressCapsule1.setContent(ByteString.copyFrom(cm));
    PedersenHash a = compressCapsule1.getInstance();
    tree.append(a);
    IncrementalMerkleVoucherContainer voucher = tree.toVoucher();
    return voucher;
  }

  //仅公开地址转公开地址
  public static TransactionCapsule FailureTest1() throws ZksnarkException {
    ZenTransactionBuilder builder = new ZenTransactionBuilder();
    builder.setTransparentInput(Wallet.decodeFromBase58Check(OWNER_ADDRESS), 210_000_000L);
    builder.setTransparentOutput(Wallet.decodeFromBase58Check(TO_ADDRESS), 200_000_000L);

    TransactionCapsule transactionCap = builder.build();
    transactionCap.sign(ByteArray.fromHexString(OWNER_PRIVATE_KEY));

    return transactionCap;
  }

  //公开地址A转匿名地址，但用公开地址B的私钥签名
  public static TransactionCapsule FailureTest2() throws ZksnarkException {
    ZenTransactionBuilder builder = new ZenTransactionBuilder();
    builder.setTransparentInput(Wallet.decodeFromBase58Check(OWNER_ADDRESS), 210_000_000L);
    //TO amount
    SpendingKey spendingKey = ZKtool.getSpendingKey();
    FullViewingKey fullViewingKey = spendingKey.fullViewingKey();
    IncomingViewingKey incomingViewingKey = fullViewingKey.inViewingKey();
    PaymentAddress paymentAddress = incomingViewingKey.address(ZKtool.getDiversifierT()).get();
    builder.addOutput(fullViewingKey.getOvk(), paymentAddress, 200_000_000L, new byte[512]);

    TransactionCapsule transactionCap = builder.build();
    transactionCap.sign(ByteArray.fromHexString(TO_PRIVATE_KEY));

    return transactionCap;
  }

  //公开地址A转匿名地址，转出公开地址不签名
  public static TransactionCapsule FailureTest3() throws ZksnarkException {
    ZenTransactionBuilder builder = new ZenTransactionBuilder();
    builder.setTransparentInput(Wallet.decodeFromBase58Check(OWNER_ADDRESS), 210_000_000L);
    //TO amount
    SpendingKey spendingKey = ZKtool.getSpendingKey();
    FullViewingKey fullViewingKey = spendingKey.fullViewingKey();
    IncomingViewingKey incomingViewingKey = fullViewingKey.inViewingKey();
    PaymentAddress paymentAddress = incomingViewingKey.address(ZKtool.getDiversifierT()).get();
    builder.addOutput(fullViewingKey.getOvk(), paymentAddress, 200_000_000L, new byte[512]);

    TransactionCapsule transactionCap = builder.build();

    return transactionCap;
  }

  //公开地址转匿名地址，转出金额 - 转入金额 < fee
  public static TransactionCapsule FailureTest4() throws ZksnarkException {
    ZenTransactionBuilder builder = new ZenTransactionBuilder();
    builder.setTransparentInput(Wallet.decodeFromBase58Check(OWNER_ADDRESS), 210_000_000L);
    //TO amount
    SpendingKey spendingKey = ZKtool.getSpendingKey();
    FullViewingKey fullViewingKey = spendingKey.fullViewingKey();
    IncomingViewingKey incomingViewingKey = fullViewingKey.inViewingKey();
    PaymentAddress paymentAddress = incomingViewingKey.address(ZKtool.getDiversifierT()).get();
    builder.addOutput(fullViewingKey.getOvk(), paymentAddress, 205_000_000L, new byte[512]);

    TransactionCapsule transactionCap = builder.build();
    transactionCap.sign(ByteArray.fromHexString(OWNER_PRIVATE_KEY));

    return transactionCap;
  }

  //公开地址转匿名地址，转出金额大于账户实际余额
  public static TransactionCapsule FailureTest5() throws ZksnarkException {
    ZenTransactionBuilder builder = new ZenTransactionBuilder();
    builder.setTransparentInput(Wallet.decodeFromBase58Check(OWNER_ADDRESS), Long.MAX_VALUE);
    //TO amount
    SpendingKey spendingKey = ZKtool.getSpendingKey();
    FullViewingKey fullViewingKey = spendingKey.fullViewingKey();
    IncomingViewingKey incomingViewingKey = fullViewingKey.inViewingKey();
    PaymentAddress paymentAddress = incomingViewingKey.address(ZKtool.getDiversifierT()).get();
    builder.addOutput(fullViewingKey.getOvk(), paymentAddress, (Long.MAX_VALUE-SHIELD_FEE), new byte[512]);

    TransactionCapsule transactionCap = builder.build();
    transactionCap.sign(ByteArray.fromHexString(OWNER_PRIVATE_KEY));

    return transactionCap;
  }

  //公开地址转匿名地址，转出公开账户不存在
  public static TransactionCapsule FailureTest6() throws ZksnarkException {
    ZenTransactionBuilder builder = new ZenTransactionBuilder();
    builder.setTransparentInput(Wallet.decodeFromBase58Check(
        "TDLGnqBSjqSeSbwF9KvNaZe7fnZm7VX4h3"), 210_000_000L);
    //TO amount
    SpendingKey spendingKey = ZKtool.getSpendingKey();
    FullViewingKey fullViewingKey = spendingKey.fullViewingKey();
    IncomingViewingKey incomingViewingKey = fullViewingKey.inViewingKey();
    PaymentAddress paymentAddress = incomingViewingKey.address(ZKtool.getDiversifierT()).get();
    builder.addOutput(fullViewingKey.getOvk(), paymentAddress, 200_000_000L, new byte[512]);

    TransactionCapsule transactionCap = builder.build();
    transactionCap.sign(ByteArray.fromHexString(
        "721d63b074f18d41c147e04c952ec93467777a30b6f16745bc47a8eae5076545"));

    return transactionCap;
  }

  //公开地址转匿名地址，转出金额为0
  public static TransactionCapsule FailureTest7() throws ZksnarkException {
    ZenTransactionBuilder builder = new ZenTransactionBuilder();
    builder.setTransparentInput(Wallet.decodeFromBase58Check(OWNER_ADDRESS), 0);
    //TO amount
    SpendingKey spendingKey = ZKtool.getSpendingKey();
    FullViewingKey fullViewingKey = spendingKey.fullViewingKey();
    IncomingViewingKey incomingViewingKey = fullViewingKey.inViewingKey();
    PaymentAddress paymentAddress = incomingViewingKey.address(ZKtool.getDiversifierT()).get();
    builder.addOutput(fullViewingKey.getOvk(), paymentAddress, 0-SHIELD_FEE, new byte[512]);

    TransactionCapsule transactionCap = builder.build();
    transactionCap.sign(ByteArray.fromHexString(OWNER_PRIVATE_KEY));

    return transactionCap;
  }

  //公开地址转匿名地址，转出金额为负数
  public static TransactionCapsule FailureTest8() throws ZksnarkException {
    ZenTransactionBuilder builder = new ZenTransactionBuilder();
    builder.setTransparentInput(Wallet.decodeFromBase58Check(OWNER_ADDRESS), -100);
    //TO amount
    SpendingKey spendingKey = ZKtool.getSpendingKey();
    FullViewingKey fullViewingKey = spendingKey.fullViewingKey();
    IncomingViewingKey incomingViewingKey = fullViewingKey.inViewingKey();
    PaymentAddress paymentAddress = incomingViewingKey.address(ZKtool.getDiversifierT()).get();
    builder.addOutput(fullViewingKey.getOvk(), paymentAddress, -100-SHIELD_FEE, new byte[512]);

    TransactionCapsule transactionCap = builder.build();
    transactionCap.sign(ByteArray.fromHexString(OWNER_PRIVATE_KEY));

    return transactionCap;
  }

  //公开地址转匿名地址，公开地址非法
  public static TransactionCapsule FailureTest9() throws ZksnarkException {
    ZenTransactionBuilder builder = new ZenTransactionBuilder();
    builder.setTransparentInput("aaaaa".getBytes(), 210_000_000L);
    //TO amount
    SpendingKey spendingKey = ZKtool.getSpendingKey();
    FullViewingKey fullViewingKey = spendingKey.fullViewingKey();
    IncomingViewingKey incomingViewingKey = fullViewingKey.inViewingKey();
    PaymentAddress paymentAddress = incomingViewingKey.address(ZKtool.getDiversifierT()).get();
    builder.addOutput(fullViewingKey.getOvk(), paymentAddress, 200_000_000L, new byte[512]);

    TransactionCapsule transactionCap = builder.build();

    return transactionCap;
  }

  //匿名地址转公开地址，公开地址非法
  public static TransactionCapsule FailureTest10() throws BadItemException, ZksnarkException  {
    ZenTransactionBuilder builder = new ZenTransactionBuilder();
    //From amount
    SpendingKey sk = ZKtool.getSpendingKey();
    FullViewingKey fullViewingKey = sk.fullViewingKey();
    ExpandedSpendingKey expsk = sk.expandedSpendingKey();
    IncomingViewingKey incomingViewingKey = fullViewingKey.inViewingKey();
    PaymentAddress address = incomingViewingKey.address(ZKtool.getDiversifierT()).get();

    Note note = new Note(address, 210_000_000L);
    IncrementalMerkleVoucherContainer voucher = createSimpleMerkleVoucherContainer(note.cm());
    byte[] anchor = voucher.root().getContent().toByteArray();
    builder.addSpend(expsk, note, anchor, voucher);
    //TO amount
    builder.setTransparentOutput("bbbbbbb".getBytes(), 200_000_000L);

    TransactionCapsule transactionCap = builder.build();

    return transactionCap;
  }

  //匿名地址转公开地址，公开地址转入金额为0
  public static TransactionCapsule FailureTest11() throws BadItemException, ZksnarkException  {
    ZenTransactionBuilder builder = new ZenTransactionBuilder();
    //From amount
    SpendingKey sk = ZKtool.getSpendingKey();
    FullViewingKey fullViewingKey = sk.fullViewingKey();
    ExpandedSpendingKey expsk = sk.expandedSpendingKey();
    IncomingViewingKey incomingViewingKey = fullViewingKey.inViewingKey();
    PaymentAddress address = incomingViewingKey.address(ZKtool.getDiversifierT()).get();

    Note note = new Note(address, 0+SHIELD_FEE);
    IncrementalMerkleVoucherContainer voucher = createSimpleMerkleVoucherContainer(note.cm());
    byte[] anchor = voucher.root().getContent().toByteArray();
    builder.addSpend(expsk, note, anchor, voucher);
    //TO amount
    builder.setTransparentOutput(Wallet.decodeFromBase58Check(TO_ADDRESS), 0);

    TransactionCapsule transactionCap = builder.build();

    return transactionCap;
  }

  //匿名地址转公开地址，公开地址转入金额为负数
  public static TransactionCapsule FailureTest12() throws BadItemException, ZksnarkException  {
    ZenTransactionBuilder builder = new ZenTransactionBuilder();
    //From amount
    SpendingKey sk = ZKtool.getSpendingKey();
    FullViewingKey fullViewingKey = sk.fullViewingKey();
    ExpandedSpendingKey expsk = sk.expandedSpendingKey();
    IncomingViewingKey incomingViewingKey = fullViewingKey.inViewingKey();
    PaymentAddress address = incomingViewingKey.address(ZKtool.getDiversifierT()).get();

    Note note = new Note(address, -100+SHIELD_FEE);
    IncrementalMerkleVoucherContainer voucher = createSimpleMerkleVoucherContainer(note.cm());
    byte[] anchor = voucher.root().getContent().toByteArray();
    builder.addSpend(expsk, note, anchor, voucher);
    //TO amount
    builder.setTransparentOutput(Wallet.decodeFromBase58Check(TO_ADDRESS), -100);

    TransactionCapsule transactionCap = builder.build();

    return transactionCap;
  }

  //公开地址+匿名地址 转出到公开地址
  public static TransactionCapsule FailureTest13() throws BadItemException, ZksnarkException  {
    ZenTransactionBuilder builder = new ZenTransactionBuilder();
    //From amount
    SpendingKey sk = ZKtool.getSpendingKey();
    FullViewingKey fullViewingKey = sk.fullViewingKey();
    ExpandedSpendingKey expsk = sk.expandedSpendingKey();
    IncomingViewingKey incomingViewingKey = fullViewingKey.inViewingKey();
    PaymentAddress address = incomingViewingKey.address(ZKtool.getDiversifierT()).get();

    Note note = new Note(address, 210_000_000L);
    IncrementalMerkleVoucherContainer voucher = createSimpleMerkleVoucherContainer(note.cm());
    byte[] anchor = voucher.root().getContent().toByteArray();
    builder.addSpend(expsk, note, anchor, voucher);
    //From public address
    builder.setTransparentInput(Wallet.decodeFromBase58Check(OWNER_ADDRESS), 200_000_000L);
    //TO amount
    builder.setTransparentOutput(Wallet.decodeFromBase58Check(TO_ADDRESS), 400_000_000L);

    TransactionCapsule transactionCap = builder.build();
    transactionCap.sign(ByteArray.fromHexString(OWNER_PRIVATE_KEY));

    return transactionCap;
  }

  //匿名地址转公开，匿名的个数超过10个
  public static TransactionCapsule FailureTest14() throws BadItemException, ZksnarkException  {
    ZenTransactionBuilder builder = new ZenTransactionBuilder();
    //From amount
    SpendingKey sk = ZKtool.getSpendingKey();
    FullViewingKey fullViewingKey = sk.fullViewingKey();
    ExpandedSpendingKey expsk = sk.expandedSpendingKey();
    IncomingViewingKey incomingViewingKey = fullViewingKey.inViewingKey();
    PaymentAddress address = incomingViewingKey.address(ZKtool.getDiversifierT()).get();
    for (int i = 0; i < 11; i++) {
      Note note = new Note(address, 10_000_000L);
      IncrementalMerkleVoucherContainer voucher = createSimpleMerkleVoucherContainer(note.cm());
      byte[] anchor = voucher.root().getContent().toByteArray();
      builder.addSpend(expsk, note, anchor, voucher);
    }
    //TO amount
    builder.setTransparentOutput(Wallet.decodeFromBase58Check(TO_ADDRESS), 100_000_000L);

    TransactionCapsule transactionCap = builder.build();
    return transactionCap;
  }

  //公开转匿名，匿名的note个数超过10个
  public static TransactionCapsule FailureTest15() throws ZksnarkException  {
    ZenTransactionBuilder builder = new ZenTransactionBuilder();
    //From amount
    builder.setTransparentInput(Wallet.decodeFromBase58Check(OWNER_ADDRESS), 110_000_000L);
    //TO amount
    SpendingKey spendingKey = ZKtool.getSpendingKey();
    FullViewingKey fullViewingKey = spendingKey.fullViewingKey();
    IncomingViewingKey incomingViewingKey = fullViewingKey.inViewingKey();
    PaymentAddress paymentAddress = incomingViewingKey.address(ZKtool.getDiversifierT()).get();
    for (int i = 0; i < 11; i++) {
      builder.addOutput(fullViewingKey.getOvk(), paymentAddress, 10_000_000L, new byte[512]);
    }

    TransactionCapsule transactionCap = builder.build();
    transactionCap.sign(ByteArray.fromHexString(OWNER_PRIVATE_KEY));

    return transactionCap;
  }

  //匿名地址转出，无目标账户
  public static TransactionCapsule FailureTest16() throws BadItemException, ZksnarkException  {
    ZenTransactionBuilder builder = new ZenTransactionBuilder();
    //From amount
    SpendingKey sk = ZKtool.getSpendingKey();
    FullViewingKey fullViewingKey = sk.fullViewingKey();
    ExpandedSpendingKey expsk = sk.expandedSpendingKey();
    IncomingViewingKey incomingViewingKey = fullViewingKey.inViewingKey();
    PaymentAddress address = incomingViewingKey.address(ZKtool.getDiversifierT()).get();
    Note note = new Note(address, 10_000_000L);
    IncrementalMerkleVoucherContainer voucher = createSimpleMerkleVoucherContainer(note.cm());
    byte[] anchor = voucher.root().getContent().toByteArray();
    builder.addSpend(expsk, note, anchor, voucher);

    TransactionCapsule transactionCap = builder.build();

    return transactionCap;
  }

  //公开地址转匿名，但不设置公开地址
  public static TransactionCapsule FailureTest17() throws BadItemException, ZksnarkException  {
    ZenTransactionBuilder builder = new ZenTransactionBuilder();
    //From amount
    builder.setTransparentInput(ByteArray.fromHexString(null), 210_000_000L);
    //TO amount
    SpendingKey spendingKey = ZKtool.getSpendingKey();

    FullViewingKey fullViewingKey = spendingKey.fullViewingKey();
    IncomingViewingKey incomingViewingKey = fullViewingKey.inViewingKey();
    PaymentAddress paymentAddress = incomingViewingKey.address(ZKtool.getDiversifierT()).get();
    builder.addOutput(fullViewingKey.getOvk(), paymentAddress, 200_000_000L, new byte[512]);

    TransactionCapsule transactionCap = builder.build();
    transactionCap.sign(ByteArray.fromHexString(OWNER_PRIVATE_KEY));

    return transactionCap;
  }

  //公开+匿名地址转匿名地址，但不在设置公开地址
  public static TransactionCapsule FailureTest18() throws BadItemException, ZksnarkException  {
    ZenTransactionBuilder builder = new ZenTransactionBuilder();
    //From amount
    builder.setTransparentInput(ByteArray.fromHexString(null), 210_000_000L);

    SpendingKey sk = ZKtool.getSpendingKey();
    ExpandedSpendingKey expsk = sk.expandedSpendingKey();
    FullViewingKey fullViewingKey = sk.fullViewingKey();
    IncomingViewingKey incomingViewingKey = fullViewingKey.inViewingKey();
    PaymentAddress address = incomingViewingKey.address(ZKtool.getDiversifierT()).get();

    Note note = new Note(address, 210_000_000L);
    IncrementalMerkleVoucherContainer voucher = createSimpleMerkleVoucherContainer(note.cm());
    byte[] anchor = voucher.root().getContent().toByteArray();

    builder.addSpend(expsk, note, anchor, voucher);
    //TO amount
    builder.addOutput(fullViewingKey.getOvk(), address, 200_000_000L, new byte[512]);

    TransactionCapsule transactionCap = builder.build();

    return transactionCap;
  }

  //公开地址A转匿名地址+公开地址B，但不设置B的公开地址
  public static TransactionCapsule FailureTest19() throws BadItemException, ZksnarkException  {
    ZenTransactionBuilder builder = new ZenTransactionBuilder();
    //From amount
    builder.setTransparentInput(Wallet.decodeFromBase58Check(OWNER_ADDRESS), 210_000_000L);
    //TO amount
    SpendingKey spendingKey = ZKtool.getSpendingKey();
    FullViewingKey fullViewingKey = spendingKey.fullViewingKey();
    IncomingViewingKey incomingViewingKey = fullViewingKey.inViewingKey();
    PaymentAddress paymentAddress = incomingViewingKey.address(ZKtool.getDiversifierT()).get();

    builder.addOutput(fullViewingKey.getOvk(), paymentAddress, 200_000_000L, new byte[512]);
    builder.setTransparentOutput(ByteArray.fromHexString(null), 200_000_000L);

    TransactionCapsule transactionCap = builder.build();
    transactionCap.sign(ByteArray.fromHexString(OWNER_PRIVATE_KEY));

    return transactionCap;
  }

  //公开转匿名，其中匿名的note中金额存在为负数的情况，满足金额平衡
  public static TransactionCapsule FailureTest20() throws BadItemException, ZksnarkException  {
    ZenTransactionBuilder builder = new ZenTransactionBuilder();
    //From amount
    builder.setTransparentInput(Wallet.decodeFromBase58Check(OWNER_ADDRESS), 210_000_000L);
    //TO amount
    SpendingKey spendingKey = ZKtool.getSpendingKey();
    FullViewingKey fullViewingKey = spendingKey.fullViewingKey();
    IncomingViewingKey incomingViewingKey = fullViewingKey.inViewingKey();
    PaymentAddress paymentAddress = incomingViewingKey.address(ZKtool.getDiversifierT()).get();
    for (int i = 0; i < 2; i++) {
      builder.addOutput(fullViewingKey.getOvk(), paymentAddress, 200_000_000L, new byte[512]);
    }
    builder.addOutput(fullViewingKey.getOvk(), paymentAddress, -200_000_000L, new byte[512]);

    TransactionCapsule transactionCap = builder.build();
    transactionCap.sign(ByteArray.fromHexString(OWNER_PRIVATE_KEY));

    return transactionCap;
  }

  //匿名地址转公开，匿名note中有4个note的金额为0，剩余一个为有效值，不满足金额平衡
  public static TransactionCapsule FailureTest21() throws BadItemException, ZksnarkException  {
    ZenTransactionBuilder builder = new ZenTransactionBuilder();
    //From shield address
    SpendingKey sk = ZKtool.getSpendingKey();
    ExpandedSpendingKey expsk = sk.expandedSpendingKey();
    FullViewingKey fullViewingKey = sk.fullViewingKey();
    IncomingViewingKey incomingViewingKey = fullViewingKey.inViewingKey();
    PaymentAddress address = incomingViewingKey.address(ZKtool.getDiversifierT()).get();
    {
      Note note = new Note(address, 200_000_000L);
      IncrementalMerkleVoucherContainer voucher = createSimpleMerkleVoucherContainer(note.cm());
      byte[] anchor = voucher.root().getContent().toByteArray();
      builder.addSpend(expsk, note, anchor, voucher);
    }
    for (int i = 0; i < 4; i++) {
      Note note = new Note(address, 0);
      IncrementalMerkleVoucherContainer voucher = createSimpleMerkleVoucherContainer(note.cm());
      byte[] anchor = voucher.root().getContent().toByteArray();
      builder.addSpend(expsk, note, anchor, voucher);
    }
    //TO amount
    builder.setTransparentOutput(Wallet.decodeFromBase58Check(TO_ADDRESS), 200_000_000L);

    TransactionCapsule transactionCap = builder.build();
    return transactionCap;
  }

  //匿名转公开，其中匿名的note中金额存在为负数的情况，满足金额平衡
  public static TransactionCapsule FailureTest22() throws BadItemException, ZksnarkException  {
    ZenTransactionBuilder builder = new ZenTransactionBuilder();
    //From shield address
    SpendingKey sk = ZKtool.getSpendingKey();
    ExpandedSpendingKey expsk = sk.expandedSpendingKey();
    FullViewingKey fullViewingKey = sk.fullViewingKey();
    IncomingViewingKey incomingViewingKey = fullViewingKey.inViewingKey();
    PaymentAddress address = incomingViewingKey.address(ZKtool.getDiversifierT()).get();

    for (int i = 0; i < 2; i++) {
      Note note = new Note(address, 210_000_000);
      IncrementalMerkleVoucherContainer voucher = createSimpleMerkleVoucherContainer(note.cm());
      byte[] anchor = voucher.root().getContent().toByteArray();
      builder.addSpend(expsk, note, anchor, voucher);
    }
    {
      Note note = new Note(address, -210_000_000);
      IncrementalMerkleVoucherContainer voucher = createSimpleMerkleVoucherContainer(note.cm());
      byte[] anchor = voucher.root().getContent().toByteArray();
      builder.addSpend(expsk, note, anchor, voucher);
    }
    //TO amount
    builder.setTransparentOutput(Wallet.decodeFromBase58Check(TO_ADDRESS), 200_000_000L);

    TransactionCapsule transactionCap = builder.build();
    return transactionCap;
  }

  //两个匿名note转公开地址，但两个note的和超过long类型的最大值
  public static TransactionCapsule FailureTest23() throws BadItemException, ZksnarkException  {
    ZenTransactionBuilder builder = new ZenTransactionBuilder();
    //From shield address
    SpendingKey sk = ZKtool.getSpendingKey();
    ExpandedSpendingKey expsk = sk.expandedSpendingKey();
    FullViewingKey fullViewingKey = sk.fullViewingKey();
    IncomingViewingKey incomingViewingKey = fullViewingKey.inViewingKey();
    PaymentAddress address = incomingViewingKey.address(ZKtool.getDiversifierT()).get();
    {
      Note note = new Note(address, Long.MAX_VALUE - SHIELD_FEE - 1);
      IncrementalMerkleVoucherContainer voucher = createSimpleMerkleVoucherContainer(note.cm());
      byte[] anchor = voucher.root().getContent().toByteArray();

      builder.addSpend(expsk, note, anchor, voucher);
    }
    {
      Note note = new Note(address, SHIELD_FEE);
      IncrementalMerkleVoucherContainer voucher = createSimpleMerkleVoucherContainer(note.cm());
      byte[] anchor = voucher.root().getContent().toByteArray();
      builder.addSpend(expsk, note, anchor, voucher);
    }
    //TO amount
    builder.setTransparentOutput(Wallet.decodeFromBase58Check(TO_ADDRESS), Long.MAX_VALUE - 1);

    TransactionCapsule transactionCap = builder.build();
    return transactionCap;
  }

}
