package org.tron.core.db;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.tron.common.utils.ByteArray;

@Component
public class CommonDataBase extends TronDatabase<byte[]> {

  private static final byte[] CURRENT_SR_LIST = "CURRENT_SR_LIST".getBytes();

  public CommonDataBase() {
    super("common-database");
  }

  @Override
  public void put(byte[] key, byte[] item) {
    dbSource.putData(key, item);
  }

  @Override
  public void delete(byte[] key) {
    dbSource.deleteData(key);
  }

  @Override
  public byte[] get(byte[] key) {
    return dbSource.getData(key);
  }

  @Override
  public boolean has(byte[] key) {
    return dbSource.getData(key) != null;
  }

  public List<String> getCurrentSrList() {
    return Optional.ofNullable(get(CURRENT_SR_LIST))
        .map(ByteArray::toStr)
        .map(str -> JSON.parseArray(str, String.class))
        .orElse(Lists.newArrayList());
  }

  public void saveCurrentSrList(String currentSrList) {
    this.put(CURRENT_SR_LIST, ByteArray.fromString(currentSrList));
  }
}
