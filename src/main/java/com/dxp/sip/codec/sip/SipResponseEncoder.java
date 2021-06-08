package com.dxp.sip.codec.sip;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.http.*;

import static io.netty.handler.codec.http.HttpConstants.SP;

/**
 * Encodes an {@link SipResponse} or an {@link SipContent} into
 * a {@link ByteBuf}.
 * @author carzy
 */
public class SipResponseEncoder extends AbstractSipObjectEncoder<SipResponse> {

    @Override
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return super.acceptOutboundMessage(msg) && (msg instanceof SipResponse);
    }

    @Override
    protected void encodeInitialLine(ByteBuf buf, SipResponse response) throws Exception {
        response.protocolVersion().encode(buf);
        buf.writeByte(SP);
        response.status().encode(buf);
        ByteBufUtil.writeShortBE(buf, CRLF_SHORT);
    }

    protected void sanitizeHeadersBeforeEncode(SipResponse msg, boolean isAlwaysEmpty) {
        if (isAlwaysEmpty) {
            SipResponseStatus status = msg.status();
            if (status.codeClass() == HttpStatusClass.INFORMATIONAL ||
                    status.code() == HttpResponseStatus.NO_CONTENT.code()) {

                // Stripping Content-Length:
                // See https://tools.ietf.org/html/rfc7230#section-3.3.2
                msg.headers().remove(HttpHeaderNames.CONTENT_LENGTH);

                // Stripping Transfer-Encoding:
                // See https://tools.ietf.org/html/rfc7230#section-3.3.1
                msg.headers().remove(HttpHeaderNames.TRANSFER_ENCODING);
            } else if (status.code() == HttpResponseStatus.RESET_CONTENT.code()) {

                // Stripping Transfer-Encoding:
                msg.headers().remove(HttpHeaderNames.TRANSFER_ENCODING);

                // Set Content-Length: 0
                // https://httpstatuses.com/205
                msg.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 0);
            }
        }
    }

    @Override
    protected boolean isContentAlwaysEmpty(SipResponse msg) {
        // Correctly handle special cases as stated in:
        // https://tools.ietf.org/html/rfc7230#section-3.3.3
        SipResponseStatus status = msg.status();

        if (status.codeClass() == HttpStatusClass.INFORMATIONAL) {

            if (status.code() == HttpResponseStatus.SWITCHING_PROTOCOLS.code()) {
                // We need special handling for WebSockets version 00 as it will include an body.
                // Fortunally this version should not really be used in the wild very often.
                // See https://tools.ietf.org/html/draft-ietf-hybi-thewebsocketprotocol-00#section-1.2
                return msg.headers().contains(HttpHeaderNames.SEC_WEBSOCKET_VERSION);
            }
            return true;
        }
        return status.code() == HttpResponseStatus.NO_CONTENT.code() ||
                status.code() == HttpResponseStatus.NOT_MODIFIED.code() ||
                status.code() == HttpResponseStatus.RESET_CONTENT.code();
    }
}
