package org.tron.core.pbft;

import com.alibaba.fastjson.JSON;
import java.util.List;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tron.core.config.args.Args;
import org.tron.core.db.Manager;
import org.tron.core.pbft.message.PbftBaseMessage;
import org.tron.core.pbft.message.PbftBlockMessage;
import org.tron.core.pbft.message.PbftSrMessage;

@Slf4j(topic = "pbft")
@Component
public class PbftMessageAction {

  private long checkPoint = 0;
  private final int count = Args.getInstance().getCheckMsgCount();

  @Setter
  private Manager manager;

  public void action(PbftBaseMessage message) {
    switch (message.getType()) {
      case PBFT_BLOCK_MSG: {
        PbftBlockMessage blockMessage = (PbftBlockMessage) message;
        long blockNum = blockMessage.getBlockNum();
        if (blockNum - checkPoint >= count) {
          checkPoint = blockNum;
          manager.updateLatestSolidifiedBlock(blockNum);
          logger.info("commit msg block num is:{}", blockNum);
        }
      }
      break;
      case PBFT_SR_MSG: {
        PbftSrMessage srMessage = (PbftSrMessage) message;
        List<String> srList = JSON.parseArray(srMessage.getPbftMessage().getRawData().getData()
            .toStringUtf8(), String.class);
        logger.info("sr commit msg :{}, {}", srMessage.getBlockNum(), srList);
      }
      break;
      default:
        break;
    }
  }

}
