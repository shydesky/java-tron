package org.tron.program.zkpressuretest;

import com.google.common.collect.Lists;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.tron.common.utils.ByteArray;
import org.tron.core.Constant;
import org.tron.core.Wallet;
import org.tron.core.capsule.TransactionCapsule;
import org.tron.core.config.args.Args;
import org.tron.core.exception.ZksnarkException;
import org.tron.core.zen.ZenTransactionBuilder;
import org.tron.core.zen.address.DiversifierT;
import org.tron.core.zen.address.FullViewingKey;
import org.tron.core.zen.address.IncomingViewingKey;
import org.tron.core.zen.address.PaymentAddress;
import org.tron.core.zen.address.SpendingKey;
import org.tron.protos.Protocol;
import org.tron.protos.Protocol.Transaction;

@Slf4j
public class ZkTransactionGenerator {

  /** Start the FullNode. */
  public static void main(String[] args) {
    logger.info("Full node running.");
    Args.setParam(args, Constant.TESTNET_CONF);
    Args cfgArgs = Args.getInstance();

    long zkTransactionNum = cfgArgs.getZkTransactionNum();
    long zkConcurrentNum = cfgArgs.getZkConcurrentNum();
    logger.info("zkTransactionNum:" + zkTransactionNum + ",zkConcurrentNum:" + zkConcurrentNum);

    AtomicLong count = new AtomicLong();
    long time = System.currentTimeMillis();
    List<Protocol.Transaction> transactions = Lists.newArrayList();

    FileOutputStream fos = null;
    String outputFile = "transaction.csv";
    try {
      fos = new FileOutputStream(new File(outputFile));
      for (int i = 0; i < zkTransactionNum; i++) {
        TransactionCapsule newTransaction = createNewTransaction(count, time);
        Transaction instance = newTransaction.getInstance();
        transactions.add(instance);
        instance.writeDelimitedTo(fos);
      }

      if (count.get() % 10000 == 0) {
        fos.flush();
        logger.info("Generate transaction success ------- ------- ------- ------- ------- Remain: " + (zkTransactionNum - count.get()) + ", Pending size: " + transactions.size());
      }

      fos.flush();
      fos.close();
    } catch (Exception ex) {
      logger.error("", ex);
      return;
    }
  }

  private static TransactionCapsule createNewTransaction(AtomicLong count, long time)
      throws ZksnarkException {
    String ownerAddress = "TXtrbmfwZ2LxtoCveEhZT86fTss1w8rwJE";
    String toAddress = "TQjKWNDCLSgqUtg9vrjzZnWhhmsgNgTfmj";
    String commonOwnerPrivateKey =
        "0528dc17428585fc4dece68b79fa7912270a1fe8e85f244372f59eb7e8925e04";

    // 20_100_000 +i = 100_000 + 10_000_000 +i +  10_000_000
    ZenTransactionBuilder builder = new ZenTransactionBuilder(null);
    builder.setTransparentInput(Wallet.decodeFromBase58Check(ownerAddress), 20_100_000L);
    // generate output proof
    SpendingKey spendingKey = SpendingKey.random();
    FullViewingKey fullViewingKey = spendingKey.fullViewingKey();
    IncomingViewingKey incomingViewingKey = fullViewingKey.inViewingKey();
    PaymentAddress paymentAddress = incomingViewingKey.address(new DiversifierT()).get();
    builder.addOutput(fullViewingKey.getOvk(), paymentAddress, 100_000, new byte[512]);

    builder.setTransparentOutput(Wallet.decodeFromBase58Check(toAddress), 10_000_000);

    TransactionCapsule transactionCap = builder.build();
    transactionCap.sign(ByteArray.fromHexString(commonOwnerPrivateKey));

    long gTime = count.incrementAndGet() + time;
    String ref = "" + gTime;

    transactionCap.setReference(gTime, ByteArray.fromString(ref));
    transactionCap.setExpiration(gTime);
    return transactionCap;
  }
}
