package com.dxp.sip.bus.fun;

import com.dxp.sip.codec.sip.FullSipRequest;
import com.dxp.sip.codec.sip.SipMethod;
import com.dxp.sip.util.SendErrorResponseUtil;
import io.netty.channel.Channel;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.dom4j.DocumentException;


/**
 * @author dongxinping
 */
public class DispatchHandler {

    public static DispatchHandler INSTANCE = new DispatchHandler();

    /**
     * 异步执行处理函数， 不阻塞work线程。
     */
    private static final EventLoopGroup LOOP_GROUP = new DefaultEventLoopGroup(new DefaultThreadFactory("dis-hand"));

    public void handler(FullSipRequest request, Channel channel) {
        request.retain();
        LOOP_GROUP.submit(() -> handler0(request, channel));
    }

    private void handler0(FullSipRequest request, Channel channel) {
        try {
            SipMethod method = request.method();
            HandlerController controller = DispatchHandlerContext.method(method);
            if (controller == null) {
                SendErrorResponseUtil.err405(request, channel);
            } else {
                controller.handler(request, channel);
            }
        } catch (DocumentException e){
            SendErrorResponseUtil.err400(request, channel, "xml err");
        } catch(Exception e){
            SendErrorResponseUtil.err500(request, channel, e.getMessage());
        } finally{
            request.release();
        }
    }

}
