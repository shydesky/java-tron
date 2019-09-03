package org.tron.program;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import java.io.File;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.tron.common.application.Application;
import org.tron.common.application.ApplicationFactory;
import org.tron.common.application.TronApplicationContext;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.Sha256Hash;
import org.tron.core.Constant;
import org.tron.core.capsule.TransactionCapsule;
import org.tron.core.capsule.TransactionInfoCapsule;
import org.tron.core.config.DefaultConfig;
import org.tron.core.config.args.Args;
import org.tron.core.db.Manager;
import org.tron.core.exception.BadItemException;
import org.tron.core.exception.ItemNotFoundException;
import org.tron.protos.Protocol;
import org.tron.protos.Protocol.*;

@Slf4j(topic = "app")
public class FullNode {
  private static Manager db;
  public static void load(String path) {
    try {
      File file = new File(path);
      if (!file.exists() || !file.isFile() || !file.canRead()) {
        return;
      }
      LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
      JoranConfigurator configurator = new JoranConfigurator();
      configurator.setContext(lc);
      lc.reset();
      configurator.doConfigure(file);
    } catch (Exception e) {
      logger.error(e.getMessage());
    }
  }

  private static Block getBlock(long blockNumber) throws BadItemException, ItemNotFoundException {
    return db.getBlockByNum(blockNumber).getInstance();
  }

  private static long[] computeBlockEnergy(long blockNumber) throws BadItemException {
    long[] result = new long[4];
    long start = System.currentTimeMillis();

    List<TransactionInfo> infoList = db.getTransactionRetStore().getTransactionInfoList(blockNumber);
    for (TransactionInfo transactionInfo : infoList) {
      result[0] += transactionInfo.getReceipt().getEnergyUsage();
      result[1] += transactionInfo.getReceipt().getEnergyFee();
      result[2] += transactionInfo.getReceipt().getOriginEnergyUsage();
      result[3] += transactionInfo.getReceipt().getEnergyUsageTotal();
    }

    long end = System.currentTimeMillis();

    logger.warn("getTransactionInfo = {}", end-start);


    return result;
  }

  private static void countEnergy(long startBlock, long number) throws BadItemException, ItemNotFoundException {
    logger.info("starbBlock = {}, blockNumber = {}", startBlock, number);

    long[] energyUsage = new long[(int)(number/200) +1];
    long[] energyFee = new long[(int)(number/200) +1];
    long[] originEnergyUsage = new long[(int)(number/200) +1];
    long[] energyUsageTatal = new long[(int)(number/200) +1];

    for (int i = 0; i < number; i++){
      logger.info("get block number = {}", startBlock+i);
      long[] energy = computeBlockEnergy(startBlock+i);
      energyUsage[i/200] += energy[0];
      energyFee[i/200] += energy[1];
      originEnergyUsage[i/200] += energy[2];
      energyUsageTatal[i/200] += energy[3];
    }

    for (int i = 0; i < energyUsage.length; i++){
      logger.info("i = {}, energyUsage, energyFee, originEnergyUsage, energyUsageTatal =  {}  {}  {} {}\n", i, energyUsage[i], energyFee[i], originEnergyUsage[i], energyUsageTatal[i]);
    }
  }

  /**
   * Start the FullNode.
   */
  public static void main(String[] args) {
    try {

      logger.info("Full node running.");
      Args.setParam(args, Constant.TESTNET_CONF);
      Args cfgArgs = Args.getInstance();

      load(cfgArgs.getLogbackPath());

      if (cfgArgs.isHelp()) {
        logger.info("Here is the help message.");
        return;
      }

      if (Args.getInstance().isDebug()) {
        logger.info("in debug mode, it won't check energy time");
      } else {
        logger.info("not in debug mode, it will check energy time");
      }

      DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
      beanFactory.setAllowCircularReferences(false);
      TronApplicationContext context =
              new TronApplicationContext(beanFactory);
      context.register(DefaultConfig.class);

      context.refresh();
      Application appT = ApplicationFactory.create(context);


      db = appT.getDbManager();

      Thread thread = new Thread(new Runnable() {

        @Override
        public void run() {
          try{
            countEnergy(cfgArgs.getStartBlockNumber(), cfgArgs.getBlockNumber());
          }catch (Exception ex){
            logger.error(""+ex.getMessage());
          }
          System.exit(0);
        }

      });

      thread.start();

      shutdown(appT);

    } catch (Exception e) {
      logger.error(e.getMessage());
    }



/*
    // grpc api server
    RpcApiService rpcApiService = context.getBean(RpcApiService.class);
    appT.addService(rpcApiService);
    if (cfgArgs.isWitness()) {
      appT.addService(new WitnessService(appT, context));
    }

    // http api server
    FullNodeHttpApiService httpApiService = context.getBean(FullNodeHttpApiService.class);
    appT.addService(httpApiService);

    // fullnode and soliditynode fuse together, provide solidity rpc and http server on the fullnode.
    if (Args.getInstance().getStorage().getDbVersion() == 2) {
      RpcApiServiceOnSolidity rpcApiServiceOnSolidity = context
          .getBean(RpcApiServiceOnSolidity.class);
      appT.addService(rpcApiServiceOnSolidity);
      HttpApiOnSolidityService httpApiOnSolidityService = context
          .getBean(HttpApiOnSolidityService.class);
      appT.addService(httpApiOnSolidityService);
    }

    appT.initServices(cfgArgs);
    appT.startServices();
    appT.startup();

    rpcApiService.blockUntilShutdown();*/
  }

  public static void shutdown(final Application app) {
    logger.info("********register application shutdown hook********");
    Runtime.getRuntime().addShutdownHook(new Thread(app::shutdown));
  }
}
