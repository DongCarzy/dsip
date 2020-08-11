package com.dxp.sip.codec.sip;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.FullHttpResponse;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author carzy
 * @date 2020/8/11
 */
public class DefaultSipResponseDecoderTest {

    String CRLF = "\r\n";

    @Test
    public void testReceiveRegister() {

        byte[] data2 = ("SIP/2.0 401 Unauthorized" + CRLF +
                "Via: SIP/2.0/UDP 172.16.0.102:5061;rport=5061;branch=z9hG4bK2995420819" + CRLF +
                "From: <sip:35010401401127000000@3501040140>;tag=4112720925" + CRLF +
                "To: <sip:35010401401127000000@3501040140>;tag=4157052765" + CRLF +
                "Call-ID: 667210238" + CRLF +
                "CSeq: 1 REGISTER" + CRLF +
                "User-Agent: HIC SERVER" + CRLF +
                "WWW-Authenticate: Digest realm=\"0\",algorithm=MD5,nonce=\"1234567890\",stale=false" + CRLF +
                "Content-Length: 0\r\n\r\n").getBytes();

        EmbeddedChannel ch = new EmbeddedChannel(
                new SipObjectDecoder(),
                new SipObjectAggregator(8192)
        );
        ch.writeInbound(Unpooled.wrappedBuffer(data2));

        SipObject res2 = ch.readInbound();
        assertNotNull(res2);
        assertTrue(res2 instanceof FullSipResponse);
        final FullSipResponse response = (FullSipResponse) res2;
        System.out.println("status ===> " + response.status().toString());
        System.out.println("headers ===> " + response.headers().toString());
        System.out.println("content ===> " + response.content().readCharSequence(response.content().readableBytes(), StandardCharsets.UTF_8));
        ((FullSipResponse) res2).release();
    }

}