package org.tron.core.pbft;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.tron.common.overlay.server.Channel;
import org.tron.common.overlay.server.MessageQueue;
import org.tron.core.net.peer.PeerConnection;
import org.tron.core.pbft.message.PbftBaseMessage;

@Component
@Scope("prototype")
public class PbftHandler extends SimpleChannelInboundHandler<PbftBaseMessage> {

  protected PeerConnection peer;

  private MessageQueue msgQueue;

  @Autowired
  private PbftManager pbftManager;

  private Cache<String, Boolean> msgCache = CacheBuilder.newBuilder().initialCapacity(3000)
      .maximumSize(10000).expireAfterWrite(10, TimeUnit.MINUTES).build();

  @Override
  public void channelRead0(final ChannelHandlerContext ctx, PbftBaseMessage msg) throws Exception {
    String key = buildKey(msg);
    if (msgCache.getIfPresent(key) != null || !msg.validateSignature()
        || !pbftManager.checkIsWitnessMsg(msg)) {
      return;
    }
    msgCache.put(key, true);
    pbftManager.forwardMessage(msg);
    msgQueue.receivedMessage(msg);
    pbftManager.doAction(msg);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    peer.processException(cause);
  }

  public void setMsgQueue(MessageQueue msgQueue) {
    this.msgQueue = msgQueue;
  }

  public void setChannel(Channel channel) {
    this.peer = (PeerConnection) channel;
  }

  private String buildKey(PbftBaseMessage msg) {
    return msg.getKey() + msg.getPbftMessage().getRawData()
        .getPbftMsgType().toString();
  }

}