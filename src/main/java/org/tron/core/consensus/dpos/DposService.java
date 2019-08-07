package org.tron.core.consensus.dpos;

import static org.tron.core.witness.BlockProductionCondition.NOT_MY_TURN;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.tron.common.application.Application;
import org.tron.common.application.TronApplicationContext;
import org.tron.common.backup.BackupManager;
import org.tron.common.backup.BackupManager.BackupStatusEnum;
import org.tron.common.backup.BackupServer;
import org.tron.common.crypto.ECKey;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.StringUtil;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.capsule.WitnessCapsule;
import org.tron.core.config.Parameter.ChainConstant;
import org.tron.core.config.args.Args;
import org.tron.core.consensus.base.BlockHandle;
import org.tron.core.db.Manager;
import org.tron.core.exception.AccountResourceInsufficientException;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.core.exception.TronException;
import org.tron.core.exception.UnLinkedBlockException;
import org.tron.core.exception.ValidateScheduleException;
import org.tron.core.exception.ValidateSignatureException;
import org.tron.core.net.TronNetService;
import org.tron.core.net.message.BlockMessage;
import org.tron.core.witness.BlockProductionCondition;
import org.tron.core.witness.WitnessController;

@Slf4j(topic = "consensus")
public class DposService {

  @Getter
  private static volatile boolean needSyncCheck = Args.getInstance().isNeedSyncCheck();

  @Getter
  protected Map<ByteString, WitnessCapsule> witnesses = Maps.newHashMap();

  @Getter
  private Map<ByteString, byte[]> privateKeys = Maps.newHashMap();

  @Getter
  private Map<byte[], byte[]> privateKeyToAddressMap = Maps.newHashMap();

  @Getter
  BlockHandle blockHandle;

}
