package com.dxp.sip.bus.fun.controller;

import com.dxp.sip.bus.fun.HandlerController;
import com.dxp.sip.codec.sip.*;
import com.dxp.sip.util.CharsetUtils;
import com.dxp.sip.util.SendErrorResponseUtil;
import io.netty.channel.Channel;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.dom4j.DocumentException;

/**
 * @author dongxinping
 */
public class InviteController implements HandlerController {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(InviteController.class);

    @Override
    public SipMethod method() {
        return SipMethod.INVITE;
    }

    @Override
    public void handler(FullSipRequest request, Channel channel) throws DocumentException {
        AbstractSipHeaders headers = request.headers();
        String type = headers.get(SipHeaderNames.CONTENT_TYPE);
        if (SipHeaderValues.APPLICATION_SDP.contentEqualsIgnoreCase(type)) {
            //todo.. sdp解析。
            LOGGER.info("sdp: {}", request.content().toString(CharsetUtils.US_ASCII));
        } else {
            SendErrorResponseUtil.err400(request, channel, "message content_type must be Application/sdp");
        }
    }
}
