package org.tron.core.db2.common;

import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.common.primitives.Longs;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.WeakHashMap;
import lombok.extern.slf4j.Slf4j;
import org.tron.core.db.common.WrappedByteArray;

@Slf4j(topic = "DB")
public class AccountCacheDB implements DB<byte[], byte[]>, Flusher {

  private Map<Key, Key> db = new WeakHashMap<>();

  @Override
  public byte[] get(byte[] key) {
    Key result = db.get(Key.of(key));
    return result == null ? null : result.getBytes();
  }

  @Override
  public void put(byte[] key, byte[] value) {
    if (key == null || value == null) {
      return;
    }

    Key k = Key.copyOf(key);
    Key v = Key.copyOf(value);
    db.put(k, v);
  }

  @Override
  public long size() {
    return db.size();
  }

  @Override
  public boolean isEmpty() {
    return db.isEmpty();
  }

  @Override
  public void remove(byte[] key) {
    if (key != null) {
      db.remove(Key.of(key));
    }
  }

  @Override
  public Iterator<Entry<byte[], byte[]>> iterator() {
    return Iterators.transform(db.entrySet().iterator(),
        e -> Maps.immutableEntry(e.getKey().getBytes(), e.getValue().getBytes()));
  }

  @Override
  public void flush(Map<WrappedByteArray, WrappedByteArray> batch) {
    batch.forEach((k, v) -> this.put(k.getBytes(), v.getBytes()));
  }

  @Override
  public void close() {
    reset();
    db = null;
  }

  @Override
  public void reset() {
    db.clear();
  }
}
