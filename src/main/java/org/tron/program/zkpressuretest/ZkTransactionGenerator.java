package org.tron.program.zkpressuretest;

import com.google.protobuf.ByteString;
import com.sun.jna.Pointer;
import com.sun.org.apache.xerces.internal.impl.dv.util.HexBin;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
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
import org.tron.common.zksnark.LibrustzcashParam.SpendProofParams;
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

  private ConcurrentHashMap<Integer, byte[]> treeMap = new ConcurrentHashMap();

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

        IncrementalMerkleTreeCapsule treeCapsule = new IncrementalMerkleTreeCapsule();
        for (Integer i = 0; i < zkTransactionNum; i++) {
          IncrementalMerkleTreeContainer container = treeCapsule.toMerkleTreeContainer();
          container.append(cmHash);
          treeMap.put(i, container.getTreeCapsule().getData());
        }
        inputMerkleRoot = treeMap.get(zkTransactionNum-1);
        logger.info("Init merkleMap done");

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
        newTransaction = createTransactionType2((int) l, count);
        break;
      case 3:
        if (l == 0) {
          return;
        }
        newTransaction = createTransactionType3((int) l, count);
        break;

      case 4:
        createSpendProofOnly();
        return;
      default:
        throw new RuntimeException("Wrong testType:" + testType);
    }

    Transaction instance = newTransaction.getInstance();

    transactions.add(instance);
  }

  // private 2 public
  private TransactionCapsule createTransactionType2(int index, int count) throws ZksnarkException {

    //    long start = System.currentTimeMillis();
    if (index == 0) {
      return null;
    }
    byte[] bytes = treeMap.get(index);
    if (bytes == null) {
      throw new RuntimeException("merkleMap is not initial,index:" + index);
    }

    IncrementalMerkleTreeContainer container =
        new IncrementalMerkleTreeCapsule(bytes).toMerkleTreeContainer();

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
  private TransactionCapsule createTransactionType3(int index, int count) throws ZksnarkException {

    //    long start = System.currentTimeMillis();
    if (index == 0) {
      return null;
    }
    byte[] bytes = treeMap.get(index);
    if (bytes == null) {
      throw new RuntimeException("merkleMap is not initial,index:" + index);
    }

    IncrementalMerkleTreeContainer container =
        new IncrementalMerkleTreeCapsule(bytes).toMerkleTreeContainer();

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


  public void createSpendProofOnly() throws ZksnarkException {

    Pointer ctx = Librustzcash.librustzcashSaplingProvingCtxInit();

    byte[] ak = HexBin.decode("2021c369f4b901cc4f37d80eac2d676aa41beb2a2d835d5120005714bc687657");
    byte[] nsk = HexBin
        .decode("48ea637742229ee87b8ebffd435b27469bee46ecb7732a6e3fb27939d442c006");

    byte[] d = HexBin.decode("5aafbda15b790d38637017");
    long value = 10 * 1000000;
    byte[] rcm = HexBin
        .decode("26328c28c46fb3c3a5e0648e5fc6b312a93f9fa93b5275cf79d4f71a30cd4d00");
    byte[] alpha = HexBin
        .decode("994f6f29a8205747c510406e331d2a49faa1b517e630a4c55d9fe3856a9e030b");
    byte[] anchor = HexBin
        .decode("f2097ce0e430f74a87d5d6c574f483165c781bd6b2423ec4824505890606554f");
    byte[] voucherPath = HexBin.decode(
        "2020b2eed031d4d6a4f02a097f80b54cc1541d4163c6b6f5971f88b6e41d35c538142012935f14b676509b81eb49ef25f39269ed72309238b4c145803544b646dca62d20e1f34b034d4a3cd28557e2907ebf990c918f64ecb50a94f01d6fda5ca5c7ef722028e7b841dcbc47cceb69d7cb8d94245fb7cb2ba3a7a6bc18f13f945f7dbd6e2a20a5122c08ff9c161d9ca6fc462073396c7d7d38e8ee48cdb3bea7e2230134ed6a20d2e1642c9a462229289e5b0e3b7f9008e0301cbb93385ee0e21da2545073cb582016d6252968971a83da8521d65382e61f0176646d771c91528e3276ee45383e4a20fee0e52802cb0c46b1eb4d376c62697f4759f6c8917fa352571202fd778fd712204c6937d78f42685f84b43ad3b7b00f81285662f85c6a68ef11d62ad1a3ee0850200769557bc682b1bf308646fd0b22e648e8b9e98f57e29f5af40f6edb833e2c492008eeab0c13abd6069e6310197bf80f9c1ea6de78fd19cbae24d4a520e6cf3023208d5fa43e5a10d11605ac7430ba1f5d81fb1b68d29a640405767749e841527673206aca8448d8263e547d5ff2950e2ed3839e998d31cbc6ac9fd57bc6002b15921620cd1c8dbf6e3acc7a80439bc4962cf25b9dce7c896f3a5bd70803fc5a0e33cf00206edb16d01907b759977d7650dad7e3ec049af1a3d875380b697c862c9ec5d51c201ea6675f9551eeb9dfaaa9247bc9858270d3d3a4c5afa7177a984d5ed1be245120d6acdedf95f608e09fa53fb43dcd0990475726c5131210c9e5caeab97f0e642f20bd74b25aacb92378a871bf27d225cfc26baca344a1ea35fdd94510f3d157082c201b77dac4d24fb7258c3c528704c59430b630718bec486421837021cf75dab65120ec677114c27206f5debc1c1ed66f95e2b1885da5b7be3d736b1de98579473048204777c8776a3b1e69b73a62fa701fa4f7a6282d9aee2c7a6b82e7937d7081c23c20ba49b659fbd0b7334211ea6a9d9df185c757e70aa81da562fb912b84f49bce722043ff5457f13b926b61df552d4e402ee6dc1463f99a535f9a713439264d5b616b207b99abdc3730991cc9274727d7d82d28cb794edbc7034b4f0053ff7c4b68044420d6c639ac24b46bd19341c91b13fdcab31581ddaf7f1411336a271f3d0aa52813208ac9cf9c391e3fd42891d27238a81a8a5c1d3a72b1bcbea8cf44a58ce738961320912d82b2c2bca231f71efcf61737fbf0a08befa0416215aeef53e8bb6d23390a20e110de65c907b9dea4ae0bd83a4b0a51bea175646a64c12b4c9f931b2cb31b4920d8283386ef2ef07ebdbb4383c12a739a953a4d6e0d6fb1139a4036d693bfbb6c20ffe9fc03f18b176c998806439ff0bb8ad193afdb27b2ccbc88856916dd804e3420817de36ab2d57feb077634bca77819c8e0bd298c04f6fed0e6a83cc1356ca1552001000000000000000000000000000000000000000000000000000000000000000000000000000000");
    byte[] cv = new byte[32];
    byte[] rk = new byte[32];
    byte[] zkproof = new byte[192];

//    long start = System.currentTimeMillis();
    boolean ret;
    ret = Librustzcash.librustzcashSaplingSpendProof(new SpendProofParams(ctx, ak,
        nsk,
        d,
        rcm,
        alpha,
        value,
        anchor,
        voucherPath,
        cv,
        rk,
        zkproof));

//    long time = (System.currentTimeMillis() - start);
    countDownLatch.countDown();
//    logger.info("--- time is: " + time + ",ok," + ret);
//    return time;

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
                  .getMethod(methodName, new Class[]{})
                  .invoke(null, new Object[]{});
      if (transactionCapsule != null) {
        transactions.add(transactionCapsule.getInstance());
        // System.out.println(ByteArray.toHexString(transactionCapsule.getTransactionId().getBytes()));
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
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

  /**
   * Start the FullNode.
   */
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
