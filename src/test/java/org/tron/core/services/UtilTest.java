package org.tron.core.services;

import com.alibaba.fastjson.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.tron.core.services.http.Util;

public class UtilTest {

  private static long getValue(final JSONObject jsonObject, final String key) {
    try {
      return Util.getJsonLongValue(jsonObject, key);
    } catch (Exception e) {
      System.out.println(e.getMessage());
    }
    return Long.MAX_VALUE;
  }

  @Test
  public void TestGetJsonLongValue() {
    long t1 = System.currentTimeMillis();
    String stringJson = "{\"id1\":10000,\"id2\":1.43e9,\"id3\":-123131313123," +
        "\"id4\":100000000e100000000,\"id5\":9000}";
    JSONObject jsonObject = JSONObject.parseObject(stringJson);
    Assert.assertEquals(getValue(jsonObject, "id1"), 10000L);
    Assert.assertEquals(getValue(jsonObject, "id2"), 1430000000L);
    Assert.assertEquals(getValue(jsonObject, "id3"), -123131313123L);
    Assert.assertEquals(getValue(jsonObject, "id4"), Long.MAX_VALUE);
    Assert.assertEquals(getValue(jsonObject, "id5"), 9000);
    Assert.assertEquals(getValue(jsonObject, "id6"), Long.MAX_VALUE);
    System.out.println("Time last:" + (System.currentTimeMillis()-t1));
  }
}
