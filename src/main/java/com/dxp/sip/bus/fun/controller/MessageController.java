package com.dxp.sip.bus.fun.controller;

import com.dxp.sip.bus.fun.HandlerController;
import com.dxp.sip.codec.sip.*;
import com.dxp.sip.util.CharsetUtils;
import com.dxp.sip.util.SendErrorResponseUtil;
import io.netty.channel.Channel;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;

/**
 * @author dongxinping
 */
public class MessageController implements HandlerController {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(MessageController.class);

    @Override
    public SipMethod method() {
        return SipMethod.MESSAGE;
    }

    @Override
    public void handler(FullSipRequest request, Channel channel) throws DocumentException {
        AbstractSipHeaders headers = request.headers();
        String type = headers.get(SipHeaderNames.CONTENT_TYPE);
        if (SipHeaderValues.APPLICATION_MANSCDP_XML.contentEqualsIgnoreCase(type)) {
            String xml = request.content().toString(CharsetUtils.GB_2313);
            Document document = DocumentHelper.parseText(xml);
            String cmdType = document.getRootElement().element("CmdType").getTextTrim();
            if ("Keepalive".equalsIgnoreCase(cmdType)) {
                keepalive(request, channel);
            } else {
                SendErrorResponseUtil.err400(request, channel, "cmdType not allowed.");
            }
        } else {
            SendErrorResponseUtil.err400(request, channel, "message content_type must be Application/MANSCDP+xml");
        }
    }

    private void keepalive(FullSipRequest msg, Channel channel) {
        AbstractSipHeaders headers = msg.headers();
        DefaultFullSipResponse response = new DefaultFullSipResponse(SipResponseStatus.OK);
        response.setRecipient(msg.recipient());
        AbstractSipHeaders h = response.headers();
        h.set(SipHeaderNames.FROM, headers.get(SipHeaderNames.FROM))
                .set(SipHeaderNames.TO, headers.get(SipHeaderNames.TO) + ";tag=" + System.currentTimeMillis())
                .set(SipHeaderNames.CSEQ, headers.get(SipHeaderNames.CSEQ))
                .set(SipHeaderNames.CALL_ID, headers.get(SipHeaderNames.CALL_ID))
                .set(SipHeaderNames.USER_AGENT, SipHeaderValues.USER_AGENT)
                .set(SipHeaderNames.CONTENT_LENGTH, SipHeaderValues.EMPTY_CONTENT_LENGTH);
        channel.writeAndFlush(response);
    }

}
