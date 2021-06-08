package com.dxp.sip.codec.sip;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.util.CharsetUtil;

import static io.netty.handler.codec.http.HttpConstants.SP;

/**
 * Encodes an {@link SipRequest} or an {@link SipContent} into
 * a {@link ByteBuf}.
 * @author carzy
 */
public class SipRequestEncoder extends AbstractSipObjectEncoder<SipRequest> {

    private static final char SLASH = '/';
    private static final char QUESTION_MARK = '?';
    private static final int SLASH_AND_SPACE_SHORT = (SLASH << 8) | SP;
    private static final int SPACE_SLASH_AND_SPACE_MEDIUM = (SP << 16) | SLASH_AND_SPACE_SHORT;

    @Override
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return super.acceptOutboundMessage(msg) && msg instanceof SipRequest;
    }

    @Override
    protected void encodeInitialLine(ByteBuf buf, SipRequest request) throws Exception {
        ByteBufUtil.copy(request.method().asciiName(), buf);

        String uri = request.uri();

        if (uri.isEmpty()) {
            // Add " / " as absolute path if uri is not present.
            // See http://tools.ietf.org/html/rfc2616#section-5.1.2
            ByteBufUtil.writeMediumBE(buf, SPACE_SLASH_AND_SPACE_MEDIUM);
        } else {
            CharSequence uriCharSequence = uri;
            boolean needSlash = false;
            int start = uri.indexOf("://");
            if (start != -1 && uri.charAt(0) != SLASH) {
                start += 3;
                // Correctly handle query params.
                // See https://github.com/netty/netty/issues/2732
                int index = uri.indexOf(QUESTION_MARK, start);
                if (index == -1) {
                    if (uri.lastIndexOf(SLASH) < start) {
                        needSlash = true;
                    }
                } else {
                    if (uri.lastIndexOf(SLASH, index) < start) {
                        uriCharSequence = new StringBuilder(uri).insert(index, SLASH);
                    }
                }
            }
            buf.writeByte(SP).writeCharSequence(uriCharSequence, CharsetUtil.UTF_8);
            if (needSlash) {
                // write "/ " after uri
                ByteBufUtil.writeShortBE(buf, SLASH_AND_SPACE_SHORT);
            } else {
                buf.writeByte(SP);
            }
        }

        request.protocolVersion().encode(buf);
        ByteBufUtil.writeShortBE(buf, CRLF_SHORT);
    }
}
