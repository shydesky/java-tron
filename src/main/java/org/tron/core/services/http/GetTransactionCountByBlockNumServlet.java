package org.tron.core.services.http;

import java.io.IOException;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.api.GrpcAPI.NumberMessage;
import org.tron.core.Wallet;


@Component
@Slf4j(topic = "API")
public class GetTransactionCountByBlockNumServlet extends RateLimiterServlet {

  @Autowired
  private Wallet wallet;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      long startTime=System.currentTimeMillis();   //获取开始时间

      long num = Long.parseLong(request.getParameter("num"));
      long count = wallet.getTransactionCountByBlockNum(num);
      response.getWriter().println("{\"count\": " + count + "}");

      long endTime=System.currentTimeMillis(); //获取结束时间
      long diff = endTime-startTime;
      logger.error("GetTransactionCountByBlockNumServlet diff fullnode time: {}", diff);

    } catch (Exception e) {
      logger.debug("Exception: {}", e.getMessage());
      try {
        response.getWriter().println(Util.printErrorMsg(e));
      } catch (IOException ioe) {
        logger.debug("IOException: {}", ioe.getMessage());
      }
    }
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      String input = request.getReader().lines()
          .collect(Collectors.joining(System.lineSeparator()));
      Util.checkBodySize(input);
      boolean visible = Util.getVisiblePost(input);
      NumberMessage.Builder build = NumberMessage.newBuilder();
      JsonFormat.merge(input, build, visible);
      long count = wallet.getTransactionCountByBlockNum(build.getNum());
      response.getWriter().println("{\"count\": " + count + "}");
    } catch (Exception e) {
      logger.debug("Exception: {}", e.getMessage());
      try {
        response.getWriter().println(Util.printErrorMsg(e));
      } catch (IOException ioe) {
        logger.debug("IOException: {}", ioe.getMessage());
      }
    }
  }
}