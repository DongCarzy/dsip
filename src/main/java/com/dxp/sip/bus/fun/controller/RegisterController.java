package com.dxp.sip.bus.fun.controller;

import com.dxp.sip.bus.fun.HandlerController;
import com.dxp.sip.codec.sip.*;
import com.dxp.sip.util.AttributeKeyUtil;
import com.dxp.sip.util.SipHeaderUtil;
import io.netty.channel.Channel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import org.dom4j.DocumentException;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * @author carzy
 * @date 2020/8/14
 */
public class RegisterController implements HandlerController {

    @Override
    public SipMethod method() {
        return SipMethod.REGISTER;
    }

    @Override
    public void handler(FullSipRequest request, Channel channel) throws DocumentException {
        AbstractSipHeaders headers = request.headers();
        DefaultFullSipResponse response = new DefaultFullSipResponse(SipResponseStatus.UNAUTHORIZED);
        response.setRecipient(request.recipient());
        AbstractSipHeaders h = response.headers();
        if (!headers.contains(SipHeaderNames.AUTHORIZATION)) {
            String wwwAuth = "Digest realm=\"31011000002001234567\", nonce=\"" +
                    "b700dc7cb094478503a21148184a3731\", opaque=\"5b279c2efd18d123d1f4a2182527a281\", algorithm=MD5";

            String ipPort = channel.localAddress().toString();
            h.set(SipHeaderNames.VIA, SipHeaderUtil.via(channel, request.recipient()))
                    .set(SipHeaderNames.FROM, headers.get(SipHeaderNames.FROM))
                    .set(SipHeaderNames.TO, headers.get(SipHeaderNames.TO))
                    .set(SipHeaderNames.CSEQ, headers.get(SipHeaderNames.CSEQ))
                    .set(SipHeaderNames.CALL_ID, headers.get(SipHeaderNames.CALL_ID))
                    .set(SipHeaderNames.USER_AGENT, SipHeaderValues.USER_AGENT)
                    .set(SipHeaderNames.WWW_AUTHENTICATE, wwwAuth)
                    .set(SipHeaderNames.CONTENT_LENGTH, SipHeaderValues.EMPTY_CONTENT_LENGTH);
        } else {
            //todo.. 检验是否存在等等问题.
            h.set(SipHeaderNames.FROM, headers.get(SipHeaderNames.FROM))
                    .set(SipHeaderNames.TO, headers.get(SipHeaderNames.TO) + ";tag=" + System.currentTimeMillis())
                    .set(SipHeaderNames.CSEQ, headers.get(SipHeaderNames.CSEQ))
                    .set(SipHeaderNames.CALL_ID, headers.get(SipHeaderNames.CALL_ID))
                    .set(SipHeaderNames.USER_AGENT, SipHeaderValues.USER_AGENT)
                    .set(SipHeaderNames.CONTENT_LENGTH, SipHeaderValues.EMPTY_CONTENT_LENGTH);
            response.setStatus(SipResponseStatus.OK);
        }
        channel.writeAndFlush(response);
    }
}