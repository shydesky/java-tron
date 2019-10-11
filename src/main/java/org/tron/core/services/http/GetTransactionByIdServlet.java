package org.tron.core.services.http;

import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.api.GrpcAPI.BytesMessage;
import org.tron.common.utils.ByteArray;
import org.tron.core.Wallet;
import org.tron.protos.Protocol.Transaction;


@Component
@Slf4j(topic = "API")
public class GetTransactionByIdServlet extends RateLimiterServlet {

  @Autowired
  private Wallet wallet;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      long startTime=System.currentTimeMillis();   //获取开始时间

      boolean visible = Util.getVisible(request);
      String input = request.getParameter("value");
      Transaction reply = wallet
          .getTransactionById(ByteString.copyFrom(ByteArray.fromHexString(input)));
      if (reply != null) {
        response.getWriter().println(Util.printTransaction(reply, visible));
      } else {
        response.getWriter().println("{}");
      }

      long endTime=System.currentTimeMillis(); //获取结束时间
      long diff = endTime-startTime;
      logger.error("get transactionbyid diff fullnode time: {}", diff);

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
      long startTime=System.currentTimeMillis();   //获取开始时间
      String input = request.getReader().lines()
          .collect(Collectors.joining(System.lineSeparator()));
      Util.checkBodySize(input);
      boolean visible = Util.getVisiblePost(input);
      BytesMessage.Builder build = BytesMessage.newBuilder();
      JsonFormat.merge(input, build, visible);
      Transaction reply = wallet.getTransactionById(build.getValue());
      if (reply != null) {
        response.getWriter().println(Util.printTransaction(reply, visible));
      } else {
        response.getWriter().println("{}");
      }
      long endTime=System.currentTimeMillis(); //获取结束时间
      long diff = endTime-startTime;
      logger.error("get transactionbyid diff fullnode time: {}", diff);

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