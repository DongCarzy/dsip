package com.dxp.sip.bus.handler;

import com.dxp.sip.bus.fun.DispatchHandler;
import com.dxp.sip.codec.sip.*;
import com.dxp.sip.util.AttributeKeyUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.SocketAddress;

/**
 * 处理sip请求.
 * <p>
 * <p>
 * 该对象必须单例使用.
 * @author carzy
 */
public class SipRequestHandler extends SimpleChannelInboundHandler<FullSipRequest> {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(SipRequestHandler.class);

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        if (channel instanceof DatagramChannel) {
            // UDP不处理.
            return;
        }
        SocketAddress remote = channel.remoteAddress();
        SocketAddress local = channel.localAddress();

        String remoteIpPort = remote.toString().substring(1);
        String[] clientAddr = remoteIpPort.split(":");
        String localIpPort = local.toString().substring(1);

        String via = String.format("%s/%s %s;rport=%s;received=%s;branch=z9hG4bk--",
                SipVersion.SIP_2_0_STRING, "TCP", localIpPort,  clientAddr[1], clientAddr[0]);
        channel.attr(AttributeKeyUtil.ATTR_VIA).set(via);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullSipRequest msg) {
        AbstractSipHeaders headers = msg.headers();
        Channel channel = ctx.channel();

        // 启动的时候已经声明了. TCP为NioSocketChannel, UDP为NioDatagramChannel
        if (channel instanceof NioDatagramChannel) {
            LOGGER.info("[{}{}] rec udp request msg", channel.id().asShortText(), msg.recipient().toString());
        } else {
            LOGGER.info("[{}{}] rec tcp request msg", channel.id().asShortText(), msg.recipient().toString());
        }
        if (SipMethod.BAD == msg.method()) {
            LOGGER.error("收到一个错误的SIP消息");
            StringBuilder builder = new StringBuilder();
            LOGGER.error(SipMessageUtil.appendFullRequest(builder, msg).toString());
        }

        // 异步执行
        DispatchHandler.INSTANCE.handler(msg, ctx.channel());
    }
}