package org.tron.core.db;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.capsule.TransactionResultListCapsule;

@Slf4j(topic = "DB")
@Component
public class TransactionResultListStore  extends TronStoreWithRevoking<TransactionResultListCapsule>  {

  protected TransactionResultListStore(String dbName) {
    super(dbName);
  }
}
