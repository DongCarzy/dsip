package com.dxp.sip.codec.sip;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.CharsetUtil;
import org.junit.Test;

/**
 * @author dxp
 * 2020/8/11 9:06 下午
 */
public class SipResponseEncoderTest {

    @Test
    public void testResponseEncoder(){
        DefaultFullSipResponse response = new DefaultFullSipResponse(SipResponseStatus.OK);
        final AbstractSipHeaders h = response.headers();
        h.set(SipHeaderNames.FROM, "123")
                .set(SipHeaderNames.TO, "456")
                .set(SipHeaderNames.CONTENT_LENGTH, 0)
                .set(SipHeaderNames.CSEQ, "1 message")
                .set(SipHeaderNames.CALL_ID, "123@abc")
                .set(SipHeaderNames.USER_AGENT, "d-sip");

        EmbeddedChannel ch = new EmbeddedChannel(
                new SipResponseEncoder(),
                new LoggingHandler(LogLevel.INFO)
        );
        ch.writeAndFlush(response);

        ByteBuf buf = ch.readOutbound();
        System.out.println(buf.toString(CharsetUtil.US_ASCII));
    }

}