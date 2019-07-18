package stest.tron.wallet.onlinestress;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.tron.api.GrpcAPI;
import org.tron.api.GrpcAPI.NumberMessage;
import org.tron.api.WalletGrpc;
import org.tron.common.crypto.ECKey;
import org.tron.common.utils.Utils;
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
      .getString("foundationAccount.key1");
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
  private static AtomicInteger has_response_count = new AtomicInteger(0);
  private static AtomicInteger end_thread_count = new AtomicInteger(0);


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
    currentBlock = blockingStubFull.getNowBlock(GrpcAPI.EmptyMessage.newBuilder().build());
    beforeBlockNum = currentBlock.getBlockHeader().getRawData().getNumber();
    beforeTime = System.currentTimeMillis();
  }

  @Test(enabled = true, threadPoolSize = 300, invocationCount = 300)
  public void storageAndCpu() {

    Protocol.Block currentBlock = blockingStubFull.getNowBlock(GrpcAPI
        .EmptyMessage.newBuilder().build());

    Integer times = 0;
    Long startTime = System.currentTimeMillis();
    //while (times++ < 50 && end_thread_count.get() < 50) {
    while (true) {
      try {
        ManagedChannel channelFull = ManagedChannelBuilder.forTarget(fullnode).usePlaintext(true)
            .build();
        WalletGrpc.WalletBlockingStub blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
        blockingStubFull.getNowBlock(GrpcAPI
            .EmptyMessage.newBuilder().build());
/*        if (blockingStubFull.getNowBlock(GrpcAPI
            .EmptyMessage.newBuilder().build()).hasBlockHeader()) {
          has_response_count.addAndGet(1);

        }*/

      } catch (Exception e) {
        logger.info(e.toString());
      }
    }
    //end_thread_count.addAndGet(1);

  }

  @Test(enabled = false, threadPoolSize = 200, invocationCount = 200)
  public void sendcoin() {

    Protocol.Block currentBlock = blockingStubFull.getNowBlock(GrpcAPI
        .EmptyMessage.newBuilder().build());
    ECKey ecKey1 = new ECKey(Utils.getRandom());
    byte[] deployAddress = ecKey1.getAddress();

    Integer times = 0;
    Long startTime = System.currentTimeMillis();
    while (times++ < 100 && end_thread_count.get() < 100) {
      ecKey1 = new ECKey(Utils.getRandom());
      deployAddress = ecKey1.getAddress();
      PublicMethed.sendcoin(deployAddress, 1L, fromAddress, testKey002, blockingStubFull);

    }
    end_thread_count.addAndGet(1);

  }


/*  @AfterClass
  public void shutdown() throws InterruptedException {
    afterTime = System.currentTimeMillis();
    logger.info(
        "Average getblock success block response are :" + (has_response_count.get() * 1000) / (
            afterTime - beforeTime));

  }*/

  @AfterClass
  public void shutdown() throws InterruptedException {

    afterTime = System.currentTimeMillis();
    currentBlock = blockingStubFull.getNowBlock(GrpcAPI.EmptyMessage.newBuilder().build());
    afterBlockNum = currentBlock.getBlockHeader().getRawData().getNumber();
    Long blockNum = beforeBlockNum;
    Integer txsNum = 0;
    Integer topNum = 0;
    Integer totalNum = 0;
    Long energyTotal = 0L;
    String findOneTxid = "";

    NumberMessage.Builder builder = NumberMessage.newBuilder();
    while (blockNum <= afterBlockNum) {
      builder.setNum(blockNum);
      txsNum = blockingStubFull.getBlockByNum(builder.build()).getTransactionsCount();
      totalNum = totalNum + txsNum;
      blockNum++;
    }
    Long costTime = (afterTime - beforeTime) / 1000;
    logger.info("Duration block num is  " + (afterBlockNum - beforeBlockNum));
    logger.info("Cost time are " + costTime);
    logger.info("Top block txs num is " + topNum);
    //logger.info("Total transaction is " + (totalNum - 30));
    logger.info("Average Tps is " + (totalNum / costTime));

    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
    if (channelFull1 != null) {
      channelFull1.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}