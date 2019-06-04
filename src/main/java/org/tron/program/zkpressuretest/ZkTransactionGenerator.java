package org.tron.program.zkpressuretest;

import com.google.protobuf.ByteString;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Random;
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
import org.tron.common.zksnark.LibrustzcashParam;
import org.tron.common.zksnark.LibrustzcashParam.InitZksnarkParams;
import org.tron.core.Constant;
import org.tron.core.Wallet;
import org.tron.core.capsule.IncrementalMerkleTreeCapsule;
import org.tron.core.capsule.PedersenHashCapsule;
import org.tron.core.capsule.TransactionCapsule;
import org.tron.core.config.DefaultConfig;
import org.tron.core.config.args.Args;
import org.tron.core.db.Manager;
import org.tron.core.exception.ZksnarkException;
import org.tron.core.zen.ZenTransactionBuilder;
import org.tron.core.zen.ZenTransactionBuilder.SpendDescriptionInfo;
import org.tron.core.zen.address.DiversifierT;
import org.tron.core.zen.address.ExpandedSpendingKey;
import org.tron.core.zen.address.FullViewingKey;
import org.tron.core.zen.address.IncomingViewingKey;
import org.tron.core.zen.address.PaymentAddress;
import org.tron.core.zen.address.SpendingKey;
import org.tron.core.zen.merkle.IncrementalMerkleTreeContainer;
import org.tron.core.zen.merkle.IncrementalMerkleVoucherContainer;
import org.tron.core.zen.note.Note;
import org.tron.core.zen.note.NoteEncryption.Encryption.EncPlaintext;
import org.tron.protos.Contract.PedersenHash;
import org.tron.protos.Protocol.Transaction;

@Slf4j
public class ZkTransactionGenerator {
  private static TronApplicationContext context;
  private static Wallet wallet;
  private static Manager dbManager;
  private String ownerAddress = "TXtrbmfwZ2LxtoCveEhZT86fTss1w8rwJE";

  private ConcurrentLinkedQueue<Transaction> transactions = new ConcurrentLinkedQueue<>();
  private String outputFile = "transaction.csv";
  private FileOutputStream fos = null;

  private ExecutorService savePool;
  private ExecutorService generatePool;
  private CountDownLatch countDownLatch = null;
  private int zkTransactionNum = 0;
  private int testType = 0;
  private int logRange = 0;

  private Note inputNote = null;
  private PedersenHash cmHash = null;
  private volatile byte[] inputMerkleRoot = null;
  private SpendingKey inputsSendingKey = null;

  public void init() throws ZksnarkException {
    context = new TronApplicationContext(DefaultConfig.class);
    wallet = context.getBean(Wallet.class);
    dbManager = context.getBean(Manager.class);
    dbManager.getDynamicPropertiesStore().saveAllowZksnarkTransaction(1);

    Args cfgArgs = Args.getInstance();
    zkTransactionNum = cfgArgs.getZkTransactionNum();
    logger.info("zkTransactionNum:" + zkTransactionNum);
    testType = cfgArgs.getTestType();
    logger.info("testType:" + testType);
    logRange = cfgArgs.getLogRange();
    logger.info("logRange:" + logRange);

    countDownLatch = new CountDownLatch(zkTransactionNum);

    librustzcashInitZksnarkParams();

    int availableProcessors = Runtime.getRuntime().availableProcessors();
    logger.info("availableProcessors:" + availableProcessors);

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

    switch (testType) {
      case 2:
      case 3:
        inputsSendingKey =
            SpendingKey.decode("0ac83cc796b4258f09bee0617a02c8ab77b86c16d1c40f689b11cd69db9827a0");

        EncPlaintext encPlaintext = new EncPlaintext();
        encPlaintext.data =
            ByteArray.fromHexString(
                "010000000000000000000000a0b332010000000089ccb81384fe7eb45eba425f26dee2e27f5cc18de0a18905e5c3bb0551b1680b0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");
        inputNote = Note.decode(encPlaintext);
        byte[] pk_d = new byte[32];
        byte[] ivk = inputsSendingKey.fullViewingKey().inViewingKey().value;
        Librustzcash.librustzcashIvkToPkd(
            new LibrustzcashParam.IvkToPkdParams(ivk, inputNote.d.getData(), pk_d));
        inputNote.pkD = pk_d;

        PedersenHashCapsule compressCapsule1 = new PedersenHashCapsule();
        compressCapsule1.setContent(ByteString.copyFrom(inputNote.cm()));
        cmHash = compressCapsule1.getInstance();
        logger.info("cm:" + ByteArray.toHexString(inputNote.cm()));
        break;
      default:
        break;
    }
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
                        generateTransaction(l, zkTransactionNum);
                      } catch (Exception ex) {
                        ex.printStackTrace();
                        logger.error("", ex);
                      }
                    });
              });

      countDownLatch.await();

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

  private void generateTransaction(long l, int count) throws Exception {
    TransactionCapsule newTransaction = null;
    switch (testType) {
      case 1:
        newTransaction = createTransactionType1();
        break;
      case 2:
        if (l == 0) {
          return;
        }
        newTransaction = createTransactionType2((int) l, count, null);
        break;
      case 3:
        if (l == 0) {
          return;
        }
        newTransaction = createTransactionType3((int) l, count, null);
        break;
      default:
        throw new RuntimeException("Wrong testType:" + testType);
    }

    Transaction instance = newTransaction.getInstance();

    transactions.add(instance);
  }

  private void consumerGenerateTransaction() throws IOException {
    if (transactions.isEmpty()) {
      try {
        Thread.sleep(100);
        return;
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    Transaction transaction = transactions.poll();
    transaction.writeDelimitedTo(fos);

    long count = countDownLatch.getCount();

    if (count % logRange == 0) {
      fos.flush();
      logger.info(
          "Generate transaction success ------- ------- ------- ------- ------- Remain: "
              + countDownLatch.getCount()
              + ", Pending size: "
              + transactions.size());
    }

    countDownLatch.countDown();
  }

  // public 2 private
  private TransactionCapsule createTransactionType1() throws ZksnarkException {

    // 20_100_000  +  0 = 10_000_000 + 100_000 + 10_000_000
    ZenTransactionBuilder builder = new ZenTransactionBuilder();
    builder.setTransparentInput(Wallet.decodeFromBase58Check(ownerAddress), 20_100_000L);

    // generate output proof
    SpendingKey spendingKey = SpendingKey.random();
    FullViewingKey fullViewingKey = spendingKey.fullViewingKey();
    IncomingViewingKey incomingViewingKey = fullViewingKey.inViewingKey();
    PaymentAddress paymentAddress = incomingViewingKey.address(new DiversifierT()).get();
    builder.addOutput(fullViewingKey.getOvk(), paymentAddress, 100_000, new byte[512]);

    String toAddress = "TQjKWNDCLSgqUtg9vrjzZnWhhmsgNgTfmj";
    builder.setTransparentOutput(Wallet.decodeFromBase58Check(toAddress), 10_000_000);

    TransactionCapsule transactionCap = builder.build();

    String commonOwnerPrivateKey =
        "0528dc17428585fc4dece68b79fa7912270a1fe8e85f244372f59eb7e8925e04";
    transactionCap.sign(ByteArray.fromHexString(commonOwnerPrivateKey));

    return transactionCap;
  }

  private void generateExceptionTransaction() {
    FailureZKTransaction testClass = new FailureZKTransaction();
    Random random = new Random();
    int i = random.nextInt(20) % 20 + 1;
    String methodName = "FailureTest" + i;

    // System.out.println(methodName);

    try {
      TransactionCapsule transactionCapsule =
          (TransactionCapsule)
              testClass
                  .getClass()
                  .getMethod(methodName, new Class[] {})
                  .invoke(null, new Object[] {});
      if (transactionCapsule != null) {
        transactions.add(transactionCapsule.getInstance());
        // System.out.println(ByteArray.toHexString(transactionCapsule.getTransactionId().getBytes()));
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  // private 2 public
  private TransactionCapsule createTransactionType2(
      int index, int count, IncrementalMerkleTreeContainer container) throws ZksnarkException {

    //    long start = System.currentTimeMillis();
    if (index == 0) {
      return null;
    }
    if (container == null) {
      container = (new IncrementalMerkleTreeCapsule()).toMerkleTreeContainer();
      for (int i = 0; i < index; i++) {
        container.append(cmHash);
      }
    }
    //  0 + 20_100_000=  10_100_000 + 0 + 10_000_000
    ZenTransactionBuilder builder = new ZenTransactionBuilder();

    ExpandedSpendingKey expsk = inputsSendingKey.expandedSpendingKey();
    Note note = inputNote;
    IncrementalMerkleVoucherContainer voucher = container.toVoucher();
    for (int i = index; i < count; i++) {
      voucher.append(cmHash);
    }

    // 10ms
    //    logger.info("Creating voucher costs:" + (System.currentTimeMillis() - start));

    synchronized (this) {
      if (inputMerkleRoot == null) {
        inputMerkleRoot = voucher.root().getContent().toByteArray();
      } else {
        if (!Arrays.equals(voucher.root().getContent().toByteArray(), inputMerkleRoot)) {
          throw new RuntimeException("root is not equal");
        }
      }
    }

    byte[] anchor = inputMerkleRoot;
    if (!Arrays.equals(cmHash.getContent().toByteArray(), note.cm())) {
      throw new RuntimeException("cmHash is not equal");
    }

    SpendDescriptionInfo spendDescriptionInfo =
        new SpendDescriptionInfo(expsk, note, anchor, voucher);

    builder.addSpend(spendDescriptionInfo);

    String toAddress = "TQjKWNDCLSgqUtg9vrjzZnWhhmsgNgTfmj";
    builder.setTransparentOutput(Wallet.decodeFromBase58Check(toAddress), 10_100_000);

    TransactionCapsule transactionCap = builder.build();

    return transactionCap;
  }

  // private 2 private
  private TransactionCapsule createTransactionType3(
      int index, int count, IncrementalMerkleTreeContainer container) throws ZksnarkException {

    //    long start = System.currentTimeMillis();
    if (index == 0) {
      return null;
    }
    if (container == null) {
      container = (new IncrementalMerkleTreeCapsule()).toMerkleTreeContainer();
      for (int i = 0; i < index; i++) {
        container.append(cmHash);
      }
    }
    //  0 + 20_100_000=   0 + 10_100_000 + 10_000_000
    ZenTransactionBuilder builder = new ZenTransactionBuilder();

    ExpandedSpendingKey expsk = inputsSendingKey.expandedSpendingKey();
    Note note = inputNote;
    IncrementalMerkleVoucherContainer voucher = container.toVoucher();
    for (int i = index; i < count; i++) {
      voucher.append(cmHash);
    }

    // 10ms
    //    logger.info("Creating voucher costs:" + (System.currentTimeMillis() - start));

    synchronized (this) {
      if (inputMerkleRoot == null) {
        inputMerkleRoot = voucher.root().getContent().toByteArray();
      } else {
        if (!Arrays.equals(voucher.root().getContent().toByteArray(), inputMerkleRoot)) {
          throw new RuntimeException("root is not equal");
        }
      }
    }

    byte[] anchor = inputMerkleRoot;
    if (!Arrays.equals(cmHash.getContent().toByteArray(), note.cm())) {
      throw new RuntimeException("cmHash is not equal");
    }

    SpendDescriptionInfo spendDescriptionInfo =
        new SpendDescriptionInfo(expsk, note, anchor, voucher);

    builder.addSpend(spendDescriptionInfo);

    // generate output proof
    SpendingKey spendingKey = SpendingKey.random();
    FullViewingKey fullViewingKey = spendingKey.fullViewingKey();
    IncomingViewingKey incomingViewingKey = fullViewingKey.inViewingKey();
    PaymentAddress paymentAddress = incomingViewingKey.address(new DiversifierT()).get();
    builder.addOutput(fullViewingKey.getOvk(), paymentAddress, 10_100_000, new byte[512]);

    TransactionCapsule transactionCap = builder.build();

    return transactionCap;
  }

  private void librustzcashInitZksnarkParams() {
    logger.info("init zk param begin");

    String spendPath = getParamsFile("sapling-spend.params");
    logger.info("spendPath:" + spendPath);
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
    InputStream in =
        ZkTransactionGenerator.class
            .getClassLoader()
            .getResourceAsStream("params" + File.separator + fileName);
    File fileOut = new File(System.getProperty("java.io.tmpdir") + File.separator + fileName);
    /// var/folders/0s/_f7hdf5d2cnbcx1qljh_vc040000gn/T/
    logger.info("java.io.tmpdir:" + System.getProperty("java.io.tmpdir"));
    try {
      FileUtils.copyToFile(in, fileOut);
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
    }
    return fileOut.getAbsolutePath();
  }

  private void prepareShieldEnvironment() throws Exception {

    long cmNum = zkTransactionNum;
    logger.info("Start to prepare shield env");
    long start = System.currentTimeMillis();

    // update merkleStore
    IncrementalMerkleTreeContainer bestMerkle = dbManager.getMerkleContainer().getBestMerkle();
    logger.info("bestMerkle.size:" + bestMerkle.size());

    for (int i = 0; i < cmNum; i++) {
      bestMerkle.append(cmHash);
    }
    dbManager.getMerkleContainer().setBestMerkle(0, bestMerkle);

    String merkleRoot = ByteArray.toHexString(bestMerkle.getMerkleTreeKey());
    logger.info("merkleRoot:" + merkleRoot);
    logger.info("Preparing shield env costs :" + (System.currentTimeMillis() - start));
    logger.info("End");
  }

  /** Start the FullNode. */
  public static void main(String[] args) throws Exception {

    logger.info("Begin.");
    logger.info("Full node running.");
    Args.setParam(args, Constant.TESTNET_CONF);

    ZkTransactionGenerator generator = new ZkTransactionGenerator();
    generator.init();
    //    generator.prepareShieldEnvironment();

    generator.start();

    logger.info("Done.");
    System.exit(0);
  }
}
