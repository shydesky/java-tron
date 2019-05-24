package org.tron.core.services.http;

import com.google.common.util.concurrent.RateLimiter;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.api.GrpcAPI.NumberMessage;
import org.tron.core.Wallet;
import org.tron.protos.Protocol.Block;

@Component
@Slf4j(topic = "API")
public class GetBlockByNumServlet extends HttpServlet {

  @Autowired
  private Wallet wallet;

  private final Semaphore permit = new Semaphore(200, true);

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {

    try {
      permit.acquire();
      long num = Long.parseLong(request.getParameter("num"));
      Block reply = wallet.getBlockByNum(num);
      if (reply != null) {
        response.getWriter().println(Util.printBlock(reply));
      } else {
        response.getWriter().println("{}");
      }
    } catch (Exception e) {
      logger.debug("Exception: {}", e.getMessage());
      try {
        response.getWriter().println(Util.printErrorMsg(e));
      } catch (IOException ioe) {
        logger.debug("IOException: {}", ioe.getMessage());
      }
    } finally {
      permit.release();
    }

  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    try {
      permit.acquire();
      String input = request.getReader().lines()
          .collect(Collectors.joining(System.lineSeparator()));
      Util.checkBodySize(input);
      NumberMessage.Builder build = NumberMessage.newBuilder();
      JsonFormat.merge(input, build);
      Block reply = wallet.getBlockByNum(build.getNum());
      if (reply != null) {
        response.getWriter().println(Util.printBlock(reply));
      } else {
        response.getWriter().println("{}");
      }
    } catch (Exception e) {
      logger.debug("Exception: {}", e.getMessage());
      try {
        response.getWriter().println(Util.printErrorMsg(e));
      } catch (IOException ioe) {
        logger.debug("IOException: {}", ioe.getMessage());
      }
    } finally {
      permit.release();
    }
  }
}