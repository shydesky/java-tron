package stest.tron.wallet.onlinestress;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.tron.api.GrpcAPI;
import org.tron.api.WalletGrpc;
import org.tron.core.Wallet;
import org.tron.protos.Protocol;
import org.tron.protos.Protocol.Block;
import org.tron.protos.Protocol.TransactionInfo;
import stest.tron.wallet.common.client.Configuration;
import stest.tron.wallet.common.client.Parameter.CommonConstant;
import stest.tron.wallet.common.client.utils.PublicMethed;

@Slf4j
public class TestStorageAndCpu {

  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("witness.key5");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);

  private final String testKey003 = Configuration.getByPath("testng.conf")
      .getString("witness.key4");
  private final byte[] testAddress003 = PublicMethed.getFinalAddress(testKey003);

  private final String testKey004 = Configuration.getByPath("testng.conf")
      .getString("witness.key3");
  private final byte[] testAddress004 = PublicMethed.getFinalAddress(testKey004);

  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private ManagedChannel channelFull1 = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull1 = null;
  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(1);
  private String fullnode1 = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(0);
  ArrayList<String> txidList = new ArrayList<String>();

  Optional<TransactionInfo> infoById = null;
  Long beforeTime;
  Long afterTime;
  Long beforeBlockNum;
  Long afterBlockNum;
  Block currentBlock;
  Long currentBlockNum;


  @BeforeSuite
  public void beforeSuite() {
    Wallet wallet = new Wallet();
    Wallet.setAddressPreFixByte(CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
  }

  /**
   * constructor.
   */

  @BeforeClass(enabled = true)
  public void beforeClass() {
    PublicMethed.printAddress(testKey002);
    PublicMethed.printAddress(testKey003);
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    channelFull1 = ManagedChannelBuilder.forTarget(fullnode1)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
    blockingStubFull1 = WalletGrpc.newBlockingStub(channelFull1);
    currentBlock = blockingStubFull1.getNowBlock(GrpcAPI.EmptyMessage.newBuilder().build());
    beforeBlockNum = currentBlock.getBlockHeader().getRawData().getNumber();
    beforeTime = System.currentTimeMillis();
  }

  @Test(enabled = true, threadPoolSize = 31, invocationCount = 31)
  public void storageAndCpu() {

    Protocol.Block currentBlock = blockingStubFull.getNowBlock(GrpcAPI
        .EmptyMessage.newBuilder().build());

    while (true) {
      try {
        ManagedChannel channelFull = null;
        WalletGrpc.WalletBlockingStub blockingStubFull = null;
        channelFull = ManagedChannelBuilder.forTarget(fullnode)
            .usePlaintext(true)
            .build();
        blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
        blockingStubFull.getNowBlock(GrpcAPI
            .EmptyMessage.newBuilder().build());
      } catch (Exception e) {
        logger.info(e.toString());
      }


    }
  }

  /**
   * constructor.
   */

  @AfterClass
  public void shutdown() throws InterruptedException {
    /*
    afterTime = System.currentTimeMillis();
    try {
      Thread.sleep(10000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    currentBlock = blockingStubFull1.getNowBlock(GrpcAPI.EmptyMessage.newBuilder().build());
    afterBlockNum = currentBlock.getBlockHeader().getRawData().getNumber() + 2;
    Long blockNum = beforeBlockNum;
    Integer txsNum = 0;
    Integer topNum = 0;
    Integer totalNum = 0;
    Long energyTotal = 0L;
    String findOneTxid = "";

    NumberMessage.Builder builder = NumberMessage.newBuilder();
    while (blockNum <= afterBlockNum) {
      builder.setNum(blockNum);
      txsNum = blockingStubFull1.getBlockByNum(builder.build()).getTransactionsCount();
      totalNum = totalNum + txsNum;
      if (topNum < txsNum) {
        topNum = txsNum;
        findOneTxid = ByteArray.toHexString(Sha256Hash.hash(blockingStubFull1
            .getBlockByNum(builder.build()).getTransactionsList().get(2)
            .getRawData().toByteArray()));
        //logger.info("find one txid is " + findOneTxid);
      }

      blockNum++;
    }
    Long costTime = (afterTime - beforeTime - 31000) / 1000;
    logger.info("Duration block num is  " + (afterBlockNum - beforeBlockNum - 11));
    logger.info("Cost time are " + costTime);
    logger.info("Top block txs num is " + topNum);
    logger.info("Total transaction is " + (totalNum - 30));
    logger.info("Average Tps is " + (totalNum / costTime));

    infoById = PublicMethed.getTransactionInfoById(findOneTxid, blockingStubFull1);
    Long oneEnergyTotal = infoById.get().getReceipt().getEnergyUsageTotal();
    logger.info("EnergyTotal is " + oneEnergyTotal);
    logger.info("Average energy is " + oneEnergyTotal * (totalNum / costTime));
*/

    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
    if (channelFull1 != null) {
      channelFull1.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}