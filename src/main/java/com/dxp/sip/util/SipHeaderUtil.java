package com.dxp.sip.util;

import com.dxp.sip.codec.sip.SipVersion;
import io.netty.channel.Channel;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.SocketChannel;

import java.net.InetSocketAddress;

public class SipHeaderUtil {

    private SipHeaderUtil() {
    }

    /**
     * 组装VIA头信息.
     * <p>
     * TCP 协议时,端口和IP不是变的, 直接从channel属性上取值,不再计算.
     * @see
     * @return String
     */
    public static String via(Channel channel, InetSocketAddress address) {
        if (channel instanceof SocketChannel) {
            return channel.attr(AttributeKeyUtil.ATTR_VIA).get();
        } else if (channel instanceof DatagramChannel) {
            String ip_port = channel.localAddress().toString().substring(1);
            String[] clientAddr = address.toString().substring(1).split(":");
            return String.format("%s/%s %s;branch=z9hG4bk--%s;rport=%s;received=%s",
                    SipVersion.SIP_2_0_STRING, "UDP", ip_port, "branch", clientAddr[1], clientAddr[0]);
        } else {
            throw new IllegalArgumentException("未知Channel类型");
        }
    }
}
