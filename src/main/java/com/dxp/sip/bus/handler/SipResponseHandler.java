package com.dxp.sip.bus.handler;

import com.dxp.sip.codec.sip.FullSipResponse;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * 处理sip响应请求
 *
 * @author dongxinping
 */
public class SipResponseHandler extends SimpleChannelInboundHandler<FullSipResponse> {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(SipResponseHandler.class);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullSipResponse msg) throws Exception {
        Channel channel = ctx.channel();

        // 启动的时候已经声明了. TCP为NioSocketChannel, UDP为NioDatagramChannel
        if (channel instanceof NioDatagramChannel) {
            LOGGER.info("[{}-{}] rec udp response msg", channel.id().asShortText(), msg.recipient().toString());
        } else {
            LOGGER.info("[{}-{}] rec tcp response msg", channel.id().asShortText(), msg.recipient().toString());
        }
    }
}
