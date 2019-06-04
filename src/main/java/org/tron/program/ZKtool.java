package org.tron.program;

import org.tron.common.utils.ByteArray;
import org.tron.core.zen.address.DiversifierT;
import org.tron.core.zen.address.SpendingKey;

public class ZKtool {
  private static final String SK = "0749c3ee436523619cfea5012128ad2a316f76212a8b8d47d01ff3ac7c1911f0";
  private static final String D = "2b2df524cec69691dcf6cb";

  public static SpendingKey getSpendingKey() {
    return new SpendingKey(ByteArray.fromHexString(SK));
  }

  public static DiversifierT getDiversifierT() {
    return new DiversifierT(ByteArray.fromHexString(D));
  }
}
