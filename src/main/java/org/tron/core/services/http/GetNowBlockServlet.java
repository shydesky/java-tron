package org.tron.core.services.http;

import java.io.IOException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.core.Wallet;
import org.tron.protos.Protocol.Block;


@Component
@Slf4j(topic = "API")
public class GetNowBlockServlet extends HttpServlet {

  @Autowired
  private Wallet wallet;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      long time1, time2, time3, time4;
      time1 = System.currentTimeMillis();
      Block reply = wallet.getNowBlock();
      time2 = System.currentTimeMillis();
      if (reply != null) {
        String ret = Util.printBlock(reply);
        time3 = System.currentTimeMillis();
        response.getWriter().println(ret);
      } else {
        time3 = System.currentTimeMillis();
        response.getWriter().println("{}");
      }
      time4 = System.currentTimeMillis();
      logger.info("all={}, getnowblock={}, printblock={}, println={}", time4 - time1, time2-time1, time3 - time2, time4 - time3);
    } catch (Exception e) {
      logger.error("Exception: {}", e.getMessage());
      try {
        response.getWriter().println(Util.printErrorMsg(e));
      } catch (IOException ioe) {
        logger.error("IOException: {}", ioe.getMessage());
      }
    }
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    doGet(request, response);
  }
}