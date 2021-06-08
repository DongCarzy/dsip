package com.dxp.sip.codec.sip;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.util.ByteProcessor;
import io.netty.util.internal.AppendableCharSequence;

import java.net.InetSocketAddress;
import java.util.List;

import static io.netty.util.internal.ObjectUtil.checkPositive;

/**
 * sip对象解析
 *
 * @author carzy
 * @date 2020/8/10
 */
public class SipObjectTcpDecoder extends ByteToMessageDecoder {

    private static final SipResponseStatus UNKNOWN_STATUS = new SipResponseStatus(999, "Unknown");

    /**
     * 默认一行的最大长度
     */
    public static final int DEFAULT_MAX_INITIAL_LINE_LENGTH = 4096;
    /**
     * 默认头信息的最大行数
     */
    public static final int DEFAULT_MAX_HEADER_SIZE = 8192;
    public static final int DEFAULT_MAX_CHUNK_SIZE = 8192;
    public static final boolean DEFAULT_VALIDATE_HEADERS = true;
    public static final int DEFAULT_INITIAL_BUFFER_SIZE = 128;

    private static final String EMPTY_VALUE = "";

    private final int maxChunkSize;
    protected final boolean validateHeaders;

    private volatile boolean resetRequested;

    private boolean decodingRequest = true;

    /**
     * These will be updated by splitHeader方法.
     */
    private CharSequence name;
    /**
     * These will be updated by splitHeader方法.
     */
    private CharSequence value;

    /**
     * 头解析器
     */
    private final SipObjectTcpDecoder.HeaderParser headerParser;
    /**
     * 行解析器
     */
    private final SipObjectTcpDecoder.LineParser lineParser;

    private SipMessage message;
    private long chunkSize;
    private long contentLength = Long.MIN_VALUE;

    /**
     * The internal state of {@link SipObjectTcpDecoder}.
     * <em>Internal use only</em>.
     */
    private enum State {
        /**
         * 控制字符,跳过
         */
        SKIP_CONTROL_CHARS,
        /**
         * 开始读取字符
         */
        READ_INITIAL,
        /**
         * 开始读取头信息
         */
        READ_HEADER,
        /**
         * 读取固定长度的值, 根据 头信息中的 content-length读取固定长度
         */
        READ_FIXED_LENGTH_CONTENT,

        BAD_MESSAGE
    }

    private State currentState = State.SKIP_CONTROL_CHARS;

    /**
     * 构造器
     * {@code maxInitialLineLength (4096}}, {@code maxHeaderSize (8192)}, and
     * {@code maxChunkSize (8192)}.
     */
    public SipObjectTcpDecoder() {
        this(DEFAULT_MAX_INITIAL_LINE_LENGTH, DEFAULT_MAX_HEADER_SIZE, DEFAULT_MAX_CHUNK_SIZE);
    }

    /**
     * Creates a new instance with the specified parameters.
     */
    public SipObjectTcpDecoder(
            int maxInitialLineLength, int maxHeaderSize, int maxChunkSize) {
        this(maxInitialLineLength, maxHeaderSize, maxChunkSize, DEFAULT_VALIDATE_HEADERS);
    }

    /**
     * Creates a new instance with the specified parameters.
     */
    public SipObjectTcpDecoder(int maxInitialLineLength, int maxHeaderSize, int maxChunkSize, boolean validateHeaders) {
        this(maxInitialLineLength, maxHeaderSize, maxChunkSize, validateHeaders, DEFAULT_INITIAL_BUFFER_SIZE);
    }

    /**
     * Creates a new instance with the specified parameters.
     */
    public SipObjectTcpDecoder(
            int maxInitialLineLength, int maxHeaderSize, int maxChunkSize,
            boolean validateHeaders, int initialBufferSize) {
        checkPositive(maxInitialLineLength, "maxInitialLineLength");
        checkPositive(maxHeaderSize, "maxHeaderSize");
        checkPositive(maxChunkSize, "maxChunkSize");

        AppendableCharSequence seq = new AppendableCharSequence(initialBufferSize);
        lineParser = new SipObjectTcpDecoder.LineParser(seq, maxInitialLineLength);
        headerParser = new SipObjectTcpDecoder.HeaderParser(seq, maxHeaderSize);
        this.maxChunkSize = maxChunkSize;
        this.validateHeaders = validateHeaders;
    }

    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf buffer, List<Object> out) {
        if (resetRequested) {
            resetNow();
        }
        final InetSocketAddress address = (InetSocketAddress) channelHandlerContext.channel().remoteAddress();

        switch (currentState) {
            case SKIP_CONTROL_CHARS:
                // Fall-through
            case READ_INITIAL:
                try {
                    AppendableCharSequence line = lineParser.parse(buffer);
                    if (line == null) {
                        return;
                    }
                    String[] initialLine = splitInitialLine(line);
                    if (initialLine.length < 3) {
                        // Invalid initial line - ignore.
                        currentState = SipObjectTcpDecoder.State.SKIP_CONTROL_CHARS;
                        return;
                    }

                    message = createMessage(initialLine, address);
                    currentState = SipObjectTcpDecoder.State.READ_HEADER;
                    // fall-through
                } catch (Exception e) {
                    out.add(invalidMessage(buffer, e, address));
                    return;
                }
            case READ_HEADER:
                try {
                    SipObjectTcpDecoder.State nextState = readHeaders(buffer);
                    if (nextState == null) {
                        return;
                    }
                    currentState = nextState;
                    if (nextState == State.SKIP_CONTROL_CHARS) {// fast-path
                        // No content is expected.
                        out.add(message);
                        out.add(LastSipContent.EMPTY_LAST_CONTENT);
                        resetNow();
                        return;
                    }
                    long contentLength = contentLength();
                    // 没有获取到 contentLength, 并且是请求,则认为响应体为0
                    if (contentLength == 0 || contentLength == -1 && isDecodingRequest()) {
                        out.add(message);
                        out.add(LastSipContent.EMPTY_LAST_CONTENT);
                        resetNow();
                        return;
                    }

                    assert nextState == State.READ_FIXED_LENGTH_CONTENT;

                    out.add(message);

                    chunkSize = contentLength;
                    return;
                } catch (Exception e) {
                    out.add(invalidMessage(buffer, e, address));
                    return;
                }
            case READ_FIXED_LENGTH_CONTENT: {
                int readLimit = buffer.readableBytes();
                if (readLimit == 0) {
                    return;
                }

                int toRead = Math.min(readLimit, maxChunkSize);
                if (toRead > chunkSize) {
                    toRead = (int) chunkSize;
                }
                ByteBuf content = buffer.readRetainedSlice(toRead);
                chunkSize -= toRead;

                if (chunkSize == 0) {
                    // Read all content.
                    out.add(new DefaultLastSipContent(content, validateHeaders));
                    resetNow();
                } else {
                    out.add(new DefaultSipContent(content));
                }
                return;
            }
            case BAD_MESSAGE: {
                // Keep discarding until disconnection.
                buffer.skipBytes(buffer.readableBytes());
                break;
            }
            default:
        }
    }

    private SipMessage invalidMessage(ByteBuf in, Exception cause, InetSocketAddress address) {
        currentState = State.BAD_MESSAGE;

        in.skipBytes(in.readableBytes());

        if (message == null) {
            message = createInvalidMessage(address);
        }
        message.setDecoderResult(DecoderResult.failure(cause));

        SipMessage ret = message;
        message = null;
        return ret;
    }

    /**
     * 清理历史缓存数据
     */
    private void resetNow() {
        this.message = null;
        name = null;
        value = null;
        contentLength = Long.MIN_VALUE;
        lineParser.reset();
        headerParser.reset();

        resetRequested = false;
        currentState = SipObjectTcpDecoder.State.SKIP_CONTROL_CHARS;
    }

    /**
     * 读取头信息.
     *
     * @param buffer 收到的buffer信息.
     * @return 下一次的读取状态.
     */
    private SipObjectTcpDecoder.State readHeaders(ByteBuf buffer) {
        final SipMessage message = this.message;
        final AbstractSipHeaders headers = message.headers();

        // 头信息是以换行符来区分的,每个头信息后跟一个换行符.  根据这个特点可以拿到一个头的行数据. line.
        AppendableCharSequence line = headerParser.parse(buffer);
        if (line == null) {
            return null;
        }
        if (line.length() > 0) {
            do {
                char firstChar = line.charAtUnsafe(0);
                if (name != null && (firstChar == ' ' || firstChar == '\t')) {
                    String trimmedLine = line.toString().trim();
                    String valueStr = String.valueOf(value);
                    value = valueStr + ' ' + trimmedLine;
                } else {
                    if (name != null) {
                        headers.add(name, value);
                    }
                    splitHeader(line);
                }
                line = headerParser.parse(buffer);
                if (line == null) {
                    return null;
                }
            } while (line.length() > 0);
        }

        // Add the last header.
        if (name != null) {
            headers.add(name, value);
        }

        // reset name and value fields
        name = null;
        value = null;

        if (contentLength() >= 0) {
            return SipObjectTcpDecoder.State.READ_FIXED_LENGTH_CONTENT;
        } else {
            // 还未读到 content-length 头, 等待下次触发从读一次.
            return SipObjectTcpDecoder.State.SKIP_CONTROL_CHARS;
        }
    }

    /**
     * 通过 content-length 来确定body 的长度.
     *
     * @return body长度, >-1表示读取到了这个头, -1 表示还未读取到 长度头信息.
     */
    private long contentLength() {
        if (contentLength == Long.MIN_VALUE) {
            contentLength = SipUtil.getContentLength(message, -1L);
        }
        return contentLength;
    }

    /**
     * 请求行或者响应行信息.
     */
    private static String[] splitInitialLine(AppendableCharSequence sb) {
        int aStart;
        int aEnd;
        int bStart;
        int bEnd;
        int cStart;
        int cEnd;

        aStart = findNonSPLenient(sb, 0);
        aEnd = findSPLenient(sb, aStart);

        bStart = findNonSPLenient(sb, aEnd);
        bEnd = findSPLenient(sb, bStart);

        cStart = findNonSPLenient(sb, bEnd);
        cEnd = findEndOfString(sb);

        return new String[]{
                sb.subStringUnsafe(aStart, aEnd),
                sb.subStringUnsafe(bStart, bEnd),
                cStart < cEnd ? sb.subStringUnsafe(cStart, cEnd) : ""};
    }

    /**
     * 查找第一个不是SP分隔符的索引.
     */
    private static int findNonSPLenient(AppendableCharSequence sb, int offset) {
        for (int result = offset; result < sb.length(); ++result) {
            char c = sb.charAtUnsafe(result);
            // See https://tools.ietf.org/html/rfc7230#section-3.5
            if (isSPLenient(c)) {
                continue;
            }
            if (Character.isWhitespace(c)) {
                // Any other whitespace delimiter is invalid
                throw new IllegalArgumentException("Invalid separator");
            }
            return result;
        }
        return sb.length();
    }

    /**
     * 查询 SP 分隔符的索引.
     */
    private static int findSPLenient(AppendableCharSequence sb, int offset) {
        for (int result = offset; result < sb.length(); ++result) {
            if (isSPLenient(sb.charAtUnsafe(result))) {
                return result;
            }
        }
        return sb.length();
    }

    /**
     * 除了CRLF终结符外，将任何形式的空格都视为SP分隔符
     * <p>
     * See https://tools.ietf.org/html/rfc7230#section-3.5
     */
    private static boolean isSPLenient(char c) {
        return c == ' ' || c == (char) 0x09 || c == (char) 0x0B || c == (char) 0x0C || c == (char) 0x0D;
    }

    /**
     * 对头消息进行拆分,获取头名称和值.
     */
    private void splitHeader(AppendableCharSequence sb) {
        final int length = sb.length();
        int nameStart;
        int nameEnd;
        int colonEnd;
        int valueStart;
        int valueEnd;

        nameStart = findNonWhitespace(sb, 0, false);
        for (nameEnd = nameStart; nameEnd < length; nameEnd++) {
            char ch = sb.charAtUnsafe(nameEnd);
            if (ch == ':' ||
                    (!isDecodingRequest() && isOWS(ch))) {
                break;
            }
        }

        if (nameEnd == length) {
            throw new IllegalArgumentException("No colon found");
        }

        for (colonEnd = nameEnd; colonEnd < length; colonEnd++) {
            if (sb.charAtUnsafe(colonEnd) == ':') {
                colonEnd++;
                break;
            }
        }

        name = sb.subStringUnsafe(nameStart, nameEnd);
        valueStart = findNonWhitespace(sb, colonEnd, true);
        if (valueStart == length) {
            value = EMPTY_VALUE;
        } else {
            valueEnd = findEndOfString(sb);
            value = sb.subStringUnsafe(valueStart, valueEnd);
        }
    }

    /**
     * 是否正在处于解码中.
     *
     * @return boolean
     */
    protected boolean isDecodingRequest() {
        return decodingRequest;
    }

    /**
     * @param initialLine 请求行信息.
     * @return SipMessage 创建sip消息对象. request, response.
     * @throws Exception ""
     */
    protected SipMessage createMessage(String[] initialLine, InetSocketAddress address) throws Exception {
        if (SipVersion.SIP_2_0_STRING.equalsIgnoreCase(initialLine[2])) {
            decodingRequest = true;
            return new DefaultSipRequest(
                    SipVersion.valueOf(initialLine[2]),
                    SipMethod.valueOf(initialLine[0]),
                    initialLine[1],
                    validateHeaders,
                    address);
        } else if (SipVersion.SIP_2_0_STRING.equalsIgnoreCase(initialLine[0])) {
            decodingRequest = false;
            return new DefaultSipResponse(
                    SipVersion.valueOf(initialLine[0]),
                    SipResponseStatus.valueOf(Integer.parseInt(initialLine[1]), initialLine[2]),
                    validateHeaders,
                    address);
        } else {
            return createInvalidMessage(address);
        }
    }

    /**
     * 消息解析失败时的处理.
     */
    protected SipMessage createInvalidMessage(InetSocketAddress address) {
        if (decodingRequest) {
            return new DefaultFullSipRequest(SipVersion.SIP_2_0, SipMethod.BAD, "/bad-request", validateHeaders, address);
        } else {
            return new DefaultFullSipResponse(SipVersion.SIP_2_0, UNKNOWN_STATUS, validateHeaders, address);
        }
    }

    /**
     * 查询第一个不是空白符的索引.
     */
    private static int findNonWhitespace(AppendableCharSequence sb, int offset, boolean validateOws) {
        for (int result = offset; result < sb.length(); ++result) {
            char c = sb.charAtUnsafe(result);
            if (!Character.isWhitespace(c)) {
                return result;
            } else if (validateOws && !isOWS(c)) {
                throw new IllegalArgumentException("Invalid separator, only a single space or horizontal tab allowed," +
                        " but received a '" + c + "'");
            }
        }
        return sb.length();
    }

    /**
     * 查询结束索引
     */
    private static int findEndOfString(AppendableCharSequence sb) {
        for (int result = sb.length() - 1; result > 0; --result) {
            if (!Character.isWhitespace(sb.charAtUnsafe(result))) {
                return result + 1;
            }
        }
        return 0;
    }

    private static boolean isOWS(char ch) {
        return ch == ' ' || ch == (char) 0x09;
    }

    /**
     * 行解析器
     */
    private final class LineParser extends HeaderParser {

        LineParser(AppendableCharSequence seq, int maxLength) {
            super(seq, maxLength);
        }

        @Override
        public AppendableCharSequence parse(ByteBuf buffer) {
            reset();
            return super.parse(buffer);
        }

        @Override
        public boolean process(byte value) throws Exception {
            if (currentState == State.SKIP_CONTROL_CHARS) {
                char c = (char) (value & 0xFF);
                if (Character.isISOControl(c) || Character.isWhitespace(c)) {
                    increaseCount();
                    return true;
                }
                currentState = State.READ_INITIAL;
            }
            return super.process(value);
        }

        @Override
        protected TooLongFrameException newException(int maxLength) {
            return new TooLongFrameException("An HTTP line is larger than " + maxLength + " bytes.");
        }
    }

    /**
     * 消息头解析
     */
    private static class HeaderParser implements ByteProcessor {
        private final AppendableCharSequence seq;
        private final int maxLength;
        private int size;

        HeaderParser(AppendableCharSequence seq, int maxLength) {
            this.seq = seq;
            this.maxLength = maxLength;
        }

        public AppendableCharSequence parse(ByteBuf buffer) {
            final int oldSize = size;
            seq.reset();
            int i = buffer.forEachByte(this);
            if (i == -1) {
                size = oldSize;
                return null;
            }
            buffer.readerIndex(i + 1);
            return seq;
        }

        public void reset() {
            size = 0;
        }

        @Override
        public boolean process(byte value) throws Exception {
            char nextByte = (char) (value & 0xFF);
            if (nextByte == SipConstants.LF) {
                int len = seq.length();
                // Drop CR if we had a CRLF pair
                if (len >= 1 && seq.charAtUnsafe(len - 1) == SipConstants.CR) {
                    --size;
                    seq.setLength(len - 1);
                }
                return false;
            }

            increaseCount();

            seq.append(nextByte);
            return true;
        }

        protected final void increaseCount() {
            if (++size > maxLength) {
                throw newException(maxLength);
            }
        }

        protected TooLongFrameException newException(int maxLength) {
            return new TooLongFrameException("HTTP header is larger than " + maxLength + " bytes.");
        }
    }
}
