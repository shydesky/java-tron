package org.tron.core.db;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.tron.core.capsule.BytesCapsule;
import org.tron.core.db2.common.AccountCacheDB;

@Slf4j
public class AccountCache extends TronStoreWithRevoking<BytesCapsule>{
  @Autowired
  public AccountCache(@Value("account-cache") String dbName) {
    super(dbName, AccountCacheDB.class);
  }
}
