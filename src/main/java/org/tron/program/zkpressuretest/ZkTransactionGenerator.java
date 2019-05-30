package org.tron.program.zkpressuretest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.stream.LongStream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.tron.common.application.TronApplicationContext;
import org.tron.common.utils.ByteArray;
import org.tron.common.zksnark.Librustzcash;
import org.tron.common.zksnark.LibrustzcashParam.InitZksnarkParams;
import org.tron.core.Constant;
import org.tron.core.Wallet;
import org.tron.core.capsule.TransactionCapsule;
import org.tron.core.config.args.Args;
import org.tron.core.exception.ZksnarkException;
import org.tron.core.services.http.FullNodeHttpApiService;
import org.tron.core.zen.ZenTransactionBuilder;
import org.tron.core.zen.address.DiversifierT;
import org.tron.core.zen.address.FullViewingKey;
import org.tron.core.zen.address.IncomingViewingKey;
import org.tron.core.zen.address.PaymentAddress;
import org.tron.core.zen.address.SpendingKey;
import org.tron.protos.Protocol.Transaction;

@Slf4j
public class ZkTransactionGenerator {
  private static TronApplicationContext context;
  //  private static Wallet wallet;
  //  private static Manager dbManager;
  private String ownerAddress = "TXtrbmfwZ2LxtoCveEhZT86fTss1w8rwJE";

  private ConcurrentLinkedQueue<Transaction> transactions = new ConcurrentLinkedQueue<>();
  private String outputFile = "transaction.csv";
  private FileOutputStream fos = null;

  private ExecutorService savePool;
  private ExecutorService generatePool;
  private CountDownLatch countDownLatch = null;
  private int zkTransactionNum = 0;

  public void init() {
    //    context = new TronApplicationContext(DefaultConfig.class);
    //    wallet = context.getBean(Wallet.class);
    //    dbManager = context.getBean(Manager.class);
    //    dbManager.getDynamicPropertiesStore().saveAllowZksnarkTransaction(1);

    Args cfgArgs = Args.getInstance();
    zkTransactionNum = cfgArgs.getZkTransactionNum();
    //    long zkConcurrentNum = cfgArgs.getZkConcurrentNum();
    countDownLatch = new CountDownLatch(zkTransactionNum);
    //    logger.info("zkTransactionNum:" + zkTransactionNum);
    System.out.println("zkTransactionNum:" + zkTransactionNum);

    librustzcashInitZksnarkParams();

    int availableProcessors = Runtime.getRuntime().availableProcessors();
    generatePool =
        Executors.newFixedThreadPool(
            availableProcessors,
            new ThreadFactory() {
              @Override
              public Thread newThread(Runnable r) {
                return new Thread(r, "generate-transaction");
              }
            });
    savePool =
        Executors.newFixedThreadPool(
            1,
            new ThreadFactory() {
              @Override
              public Thread newThread(Runnable r) {
                return new Thread(r, "save-transaction");
              }
            });

    System.out.println("availableProcessors:" + availableProcessors);

    //    AccountCapsule ownerCapsule =
    //        new AccountCapsule(
    //            ByteString.copyFromUtf8("owner"),
    //            ByteString.copyFrom(Wallet.decodeFromBase58Check(ownerAddress)),
    //            AccountType.Normal,
    //            220_000_000L);

    //    dbManager.getAccountStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);
  }

  public void start() {

    savePool.submit(
        () -> {
          while (true) {
            try {
              consumerGenerateTransaction();
            } catch (IOException e) {
              e.printStackTrace();
            }
          }
        });

    try {
      fos = new FileOutputStream(new File(outputFile), true);

      long startGenerate = System.currentTimeMillis();
      LongStream.range(0L, this.zkTransactionNum)
          .forEach(
              l -> {
                generatePool.execute(
                    () -> {
                      try {
                        generateTransaction();
                      } catch (Exception ex) {
                        ex.printStackTrace();
                        logger.error("", ex);
                      }
                    });
              });

      countDownLatch.await();

      System.out.println("generate cost time:" + (System.currentTimeMillis() - startGenerate));
      logger.info("generate cost time:" + (System.currentTimeMillis() - startGenerate));
      fos.flush();
      fos.close();
    } catch (InterruptedException | IOException e) {
      e.printStackTrace();
    } finally {
      generatePool.shutdown();

      while (true) {
        if (generatePool.isTerminated()) {
          break;
        }

        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }

      savePool.shutdown();

      while (true) {
        if (savePool.isTerminated()) {
          break;
        }

        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }

    System.exit(0);
  }

  private void generateTransaction() throws Exception {
    TransactionCapsule newTransaction = createNewTransaction();
    //    logger.info("generate cost time:" + (System.currentTimeMillis() - startGenerate));

    Transaction instance = newTransaction.getInstance();

    //    BlockCapsule blockCapsule =
    //        new BlockCapsule(100, Sha256Hash.ZERO_HASH, System.currentTimeMillis(),
    // ByteString.EMPTY);

    //    long startVerify = System.currentTimeMillis();
    //    dbManager.processTransaction(newTransaction, blockCapsule);
    //    System.out.println("Verify cost time:" + (System.currentTimeMillis() - startVerify));

    transactions.add(instance);
  }

  private void consumerGenerateTransaction() throws IOException {
    if (transactions.isEmpty()) {
      try {
        Thread.sleep(100);
        return;
      } catch (InterruptedException e) {
        System.out.println(e);
      }
    }

    Transaction transaction = transactions.poll();
    transaction.writeDelimitedTo(fos);

        long count = countDownLatch.getCount();

        if (count % 100 == 0 || (zkTransactionNum-count)  == 1 ) {
    fos.flush();
    countDownLatch.countDown();
    //      logger.info(
    System.out.println(
        "Generate transaction success ------- ------- ------- ------- ------- Remain: "
            + countDownLatch.getCount()
            + ", Pending size: "
            + transactions.size());
        }

  }

  /** Start the FullNode. */
  public static void main(String[] args) {

    System.out.println("Begin.");
    logger.info("Full node running.");
    Args.setParam(args, Constant.TESTNET_CONF);

    ZkTransactionGenerator generator = new ZkTransactionGenerator();
    generator.init();
    generator.start();

    System.out.println("Done.");
    System.exit(0);
  }

  private TransactionCapsule createNewTransaction() throws ZksnarkException {

    String toAddress = "TQjKWNDCLSgqUtg9vrjzZnWhhmsgNgTfmj";
    String commonOwnerPrivateKey =
        "0528dc17428585fc4dece68b79fa7912270a1fe8e85f244372f59eb7e8925e04";

    // 20_100_000 +i = 100_000 + 10_000_000 +i +  10_000_000
    ZenTransactionBuilder builder = new ZenTransactionBuilder();
    builder.setTransparentInput(Wallet.decodeFromBase58Check(ownerAddress), 20_100_000L);
    // generate output proof
    SpendingKey spendingKey = SpendingKey.random();
    FullViewingKey fullViewingKey = spendingKey.fullViewingKey();
    IncomingViewingKey incomingViewingKey = fullViewingKey.inViewingKey();
    PaymentAddress paymentAddress = incomingViewingKey.address(new DiversifierT().random()).get();
    builder.addOutput(fullViewingKey.getOvk(), paymentAddress, 100_000, new byte[512]);

    builder.setTransparentOutput(Wallet.decodeFromBase58Check(toAddress), 10_000_000);

    TransactionCapsule transactionCap = builder.build();
    transactionCap.sign(ByteArray.fromHexString(commonOwnerPrivateKey));

    long gTime = 0;
    String ref = "" + gTime;
//    transactionCap.setReference(gTime, ByteArray.fromString(ref));
//    transactionCap.setExpiration(gTime);
    return transactionCap;
  }

  private void librustzcashInitZksnarkParams() {
    logger.info("init zk param begin");

    String spendPath = getParamsFile("sapling-spend.params");
    System.out.println("spendPath:"+spendPath);
    String spendHash =
        "8270785a1a0d0bc77196f000ee6d221c9c9894f55307bd9357c3f0105d31ca63991ab91324160d8f53e2bbd3c2633a6eb8bdf5205d822e7f3f73edac51b2b70c";

    String outputPath = getParamsFile("sapling-output.params");
    String outputHash =
        "657e3d38dbb5cb5e7dd2970e8b03d69b4787dd907285b5a7f0790dcc8072f60bf593b32cc2d1c030e00ff5ae64bf84c5c3beb84ddc841d48264b4a171744d028";

    try {
      Librustzcash.librustzcashInitZksnarkParams(
          new InitZksnarkParams(
              spendPath.getBytes(),
              spendPath.length(),
              spendHash,
              outputPath.getBytes(),
              outputPath.length(),
              outputHash));
    } catch (ZksnarkException e) {
      logger.error("librustzcashInitZksnarkParams fail!", e);
    }
    logger.info("init zk param done");
  }


  private String getParamsFile(String fileName) {
    InputStream in = ZkTransactionGenerator.class.getClassLoader()
        .getResourceAsStream("params" + File.separator + fileName);
    File fileOut = new File(System.getProperty("java.io.tmpdir") + File.separator + fileName);
    ///var/folders/0s/_f7hdf5d2cnbcx1qljh_vc040000gn/T/
    System.out.println("java.io.tmpdir:" + System.getProperty("java.io.tmpdir"));
    try {
      FileUtils.copyToFile(in, fileOut);
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
    }
    return fileOut.getAbsolutePath();
  }
}
