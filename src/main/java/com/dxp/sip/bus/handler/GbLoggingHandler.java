package com.dxp.sip.bus.handler;

import com.dxp.sip.util.CharsetUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.logging.ByteBufFormat;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.internal.StringUtil;

/**
 * 日志处理
 *
 * @author dongxinping
 */
public class GbLoggingHandler extends LoggingHandler {

    public GbLoggingHandler(LogLevel level){
        super(level);
    }

    /**
     * Generates the default log message of the specified event whose argument is an arbitrary object.
     */
    private String formatSimple(ChannelHandlerContext ctx, String eventName, Object msg) {
        String chStr = ctx.channel().id().asShortText();
        String msgStr = msg.toString();
        return chStr + " " + eventName + ": " + msgStr;
    }

    private void appendPrettyHexDump(StringBuilder dump, ByteBuf buf) {
        dump.append(buf.toString(CharsetUtils.GB_2313));
    }

    @Override
    public String format(ChannelHandlerContext ctx, String eventName, Object arg) {
        if (arg instanceof ByteBuf) {
            return formatByteBuf(ctx, eventName, (ByteBuf) arg);
        } else if (arg instanceof ByteBufHolder) {
            return formatByteBufHolder(ctx, eventName, (ByteBufHolder) arg);
        } else {
            return formatSimple(ctx, eventName, arg);
        }
    }


    @Override
    public String format(ChannelHandlerContext ctx, String eventName) {
        String chStr = ctx.channel().id().asShortText();
        return chStr + " " + eventName;
    }

    private String formatByteBuf(ChannelHandlerContext ctx, String eventName, ByteBuf msg) {
        String chStr = ctx.channel().id().asShortText();
        int length = msg.readableBytes();
        if (length == 0) {
            return chStr + " " + eventName + ": 0B";
        } else {
            int outputLength = chStr.length() + 1 + eventName.length() + 13;
            if (byteBufFormat() == ByteBufFormat.HEX_DUMP) {
                int rows = length / 16 + (length % 15 == 0 ? 0 : 1) + 4;
                int hexDumpLength = 2 + rows * 80;
                outputLength += hexDumpLength;
            }
            StringBuilder buf = new StringBuilder(outputLength);
            buf.append(chStr).append(' ').append(eventName).append(": ").append(length).append('B');
            if (byteBufFormat() == ByteBufFormat.HEX_DUMP) {
                buf.append(StringUtil.NEWLINE);
                appendPrettyHexDump(buf, msg);
            }
            return buf.toString();
        }
    }

    /**
     * Generates the default log message of the specified event whose argument is a [ByteBufHolder].
     */
    private String formatByteBufHolder(ChannelHandlerContext ctx, String eventName, ByteBufHolder msg) {
        String chStr = ctx.channel().id().asShortText();
        String msgStr = msg.toString();
        ByteBuf content = msg.content();
        int length = content.readableBytes();
        if (length == 0) {
            return chStr + " " + eventName + ", " + msgStr + ", 0B";
        } else {
            int outputLength = chStr.length() + 1 + eventName.length() + 2 + msgStr.length() + 13;
            if (byteBufFormat() == ByteBufFormat.HEX_DUMP) {
                int rows = length / 16 + (length % 15 == 0 ? 0 : 1) + 4;
                int hexDumpLength = 2 + rows * 80;
                outputLength += hexDumpLength;
            }
            StringBuilder buf = new StringBuilder(outputLength);
            buf.append(chStr).append(' ').append(eventName).append(": ")
                    .append(msgStr).append(", ").append(length).append('B');
            if (byteBufFormat() == ByteBufFormat.HEX_DUMP) {
                buf.append(StringUtil.NEWLINE);
                appendPrettyHexDump(buf, content);
            }
            return buf.toString();
        }
    }

}
