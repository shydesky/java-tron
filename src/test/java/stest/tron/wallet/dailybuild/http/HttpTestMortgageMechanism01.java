package stest.tron.wallet.dailybuild.http;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;
import stest.tron.wallet.common.client.Configuration;
import stest.tron.wallet.common.client.utils.HttpMethed;
import stest.tron.wallet.common.client.utils.PublicMethed;

@Slf4j
public class HttpTestMortgageMechanism01 {

  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  private final String witnessKey = Configuration.getByPath("testng.conf")
      .getString("witness.key1");
  private final byte[] witnessAddress = PublicMethed.getFinalAddress(witnessKey);
  private JSONObject responseContent;
  private HttpResponse response;
  private String httpnode = Configuration.getByPath("testng.conf").getStringList("httpnode.ip.list")
      .get(1);
  private String httpSoliditynode = Configuration.getByPath("testng.conf")
      .getStringList("httpnode.ip.list").get(2);


  Long amount = 2048000000L;

  String description = Configuration.getByPath("testng.conf")
      .getString("defaultParameter.assetDescription");
  String url = Configuration.getByPath("testng.conf")
      .getString("defaultParameter.assetUrl");
  private static final long now = System.currentTimeMillis();


  /**
   * constructor.
   */
  @Test(enabled = true, description = "UpdateBrokerage by http")
  public void test01UpdateBrokerage() {
    response = HttpMethed.sendCoin(httpnode, fromAddress, witnessAddress, amount, testKey002);
    Assert.assertTrue(HttpMethed.verificationResult(response));
    HttpMethed.waitToProduceOneBlock(httpnode);

    //update brokerage
    response = HttpMethed.updateBrokerage(httpnode, witnessAddress, 30L, witnessKey);
    Assert.assertTrue(HttpMethed.verificationResult(response));
    HttpMethed.waitToProduceOneBlock(httpnode);
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "GetBrokerage by http")
  public void test02GetBrokerage() {
    response = HttpMethed.getBrokerage(httpnode, witnessAddress);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
//    JSONArray jsonArray = JSONArray.parseArray(responseContent.getString("exchanges"));
//    Assert.assertTrue(jsonArray.size() >= 1);
//    exchangeId = jsonArray.size();
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "GetBrokerage from solidity by http")
  public void test03GetBrokerageFromSolidity() {
    HttpMethed.waitToProduceOneBlockFromSolidity(httpnode, httpSoliditynode);
    response = HttpMethed.getBrokerageFromSolidity(httpSoliditynode, witnessAddress);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
//    JSONArray jsonArray = JSONArray.parseArray(responseContent.getString("exchanges"));
//    Assert.assertTrue(jsonArray.size() >= 1);
//    exchangeId = jsonArray.size();
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "GetReward by http")
  public void test04GetReward() {
    response = HttpMethed.getReward(httpnode, witnessAddress);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
   /* Assert.assertTrue(responseContent.getInteger("exchange_id") == exchangeId);
    Assert.assertEquals(responseContent.getString("creator_address"),
        ByteArray.toHexString(exchangeOwnerAddress));
    beforeInjectBalance = responseContent.getLong("first_token_balance");

    logger.info("beforeInjectBalance" + beforeInjectBalance);*/
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "GetReward from solidity by http")
  public void test05GetRewardFromSolidity() {
    response = HttpMethed.getRewardFromSolidity(httpSoliditynode, witnessAddress);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
    /*Assert.assertTrue(responseContent.getInteger("exchange_id") == exchangeId);
    Assert.assertEquals(responseContent.getString("creator_address"),
        ByteArray.toHexString(exchangeOwnerAddress));
    beforeInjectBalance = responseContent.getLong("first_token_balance");

    logger.info("beforeInjectBalance" + beforeInjectBalance);*/
  }

  /**
   * constructor.
   */
  @AfterClass
  public void shutdown() throws InterruptedException {
    HttpMethed.disConnect();
  }
}
