package com.dxp.sip.codec.sip;

import io.netty.util.AsciiString;

import static io.netty.util.internal.MathUtil.findNextPositivePowerOfTwo;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * @author carzy
 * @see io.netty.handler.codec.http.HttpMethod
 */
public class SipMethod implements Comparable<SipMethod> {

    /**
     * BAD. 自定义的一个错误的请求头, 用于标识不能解析当前的sip请求.
     */
    public static final SipMethod BAD = new SipMethod("BAD");

    /**
     * OPTIONS
     */
    public static final SipMethod OPTIONS = new SipMethod("OPTIONS");

    /**
     * Register to server
     */
    public static final SipMethod REGISTER = new SipMethod("REGISTER");

    /**
     * INVITE
     */
    public static final SipMethod INVITE = new SipMethod("INVITE");

    /**
     * ACK
     */
    public static final SipMethod ACK = new SipMethod("ACK");

    /**
     * CANCEL
     */
    public static final SipMethod CANCEL = new SipMethod("CANCEL");

    /**
     * BYE
     */
    public static final SipMethod BYE = new SipMethod("BYE");

    //additional method begin
    /**
     * SUBSCRIBE
     */
    public static final SipMethod SUBSCRIBE = new SipMethod("SUBSCRIBE");

    /**
     * NOTIFY
     */
    public static final SipMethod NOTIFY = new SipMethod("NOTIFY");

    /**
     * MESSAGE
     */
    public static final SipMethod MESSAGE = new SipMethod("MESSAGE");

    /**
     * INFO
     */
    public static final SipMethod INFO = new SipMethod("INFO");

    /**
     * UPDATE
     */
    public static final SipMethod UPDATE = new SipMethod("UPDATE");

    /**
     * PUBLISH
     */
    public static final SipMethod PUBLISH = new SipMethod("PUBLISH");

    /**
     * PRACK
     */
    public static final SipMethod PRACK = new SipMethod("PRACK");

    /**
     * REFER
     */
    public static final SipMethod REFER = new SipMethod("REFER");
    //additional method end

    private static final EnumNameMap<SipMethod> METHOD_MAP;

    static {
        METHOD_MAP = new SipMethod.EnumNameMap<>(
                new EnumNameMap.Node<>(REGISTER.toString(), REGISTER),
                new EnumNameMap.Node<>(INVITE.toString(), INVITE),
                new EnumNameMap.Node<>(ACK.toString(), ACK),
                new EnumNameMap.Node<>(CANCEL.toString(), CANCEL),
                new EnumNameMap.Node<>(BYE.toString(), BYE),
                new EnumNameMap.Node<>(SUBSCRIBE.toString(), SUBSCRIBE),
                new EnumNameMap.Node<>(NOTIFY.toString(), NOTIFY),
                new EnumNameMap.Node<>(MESSAGE.toString(), MESSAGE),
                new EnumNameMap.Node<>(INFO.toString(), INFO),
                new EnumNameMap.Node<>(OPTIONS.toString(), OPTIONS),
                new EnumNameMap.Node<>(UPDATE.toString(), UPDATE),
                new EnumNameMap.Node<>(PUBLISH.toString(), PUBLISH),
                new EnumNameMap.Node<>(PRACK.toString(), PRACK),
                new EnumNameMap.Node<>(REFER.toString(), REFER));
    }

    /**
     * Returns the {@link SipMethod} represented by the specified name.
     * If the specified name is a standard SIP method name, a cached instance
     * will be returned.  Otherwise, a new instance will be returned.
     */
    public static SipMethod valueOf(String name) {
        SipMethod result = METHOD_MAP.get(name);
        return result != null ? result : new SipMethod(name);
    }

    private final AsciiString name;

    /**
     * Creates a new SIP method with the specified name.  You will not need to
     * create a new method unless you are implementing a protocol derived from
     */
    public SipMethod(String name) {
        name = checkNotNull(name, "name").trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("empty name");
        }

        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isISOControl(c) || Character.isWhitespace(c)) {
                throw new IllegalArgumentException("invalid character in name");
            }
        }

        this.name = AsciiString.cached(name);
    }

    /**
     * Returns the name of this method.
     */
    public String name() {
        return name.toString();
    }

    /**
     * Returns the name of this method.
     */
    public AsciiString asciiName() {
        return name;
    }

    @Override
    public int hashCode() {
        return name().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SipMethod)) {
            return false;
        }

        SipMethod that = (SipMethod) o;
        return name().equals(that.name());
    }

    @Override
    public String toString() {
        return name.toString();
    }

    @Override
    public int compareTo(SipMethod o) {
        if (o == this) {
            return 0;
        }
        return name().compareTo(o.name());
    }

    private static final class EnumNameMap<T> {
        private final EnumNameMap.Node<T>[] values;
        private final int valuesMask;

        EnumNameMap(EnumNameMap.Node<T>... nodes) {
            values = (EnumNameMap.Node<T>[]) new EnumNameMap.Node[findNextPositivePowerOfTwo(33)];
            valuesMask = values.length - 1;


            for (EnumNameMap.Node<T> node : nodes) {
                int i = hashCode(node.key) & valuesMask;
                if (values[i] != null) {
                    throw new IllegalArgumentException("index " + i + " collision between values: [" +
                            values[i].key + ", " + node.key + ']');
                }
                values[i] = node;
            }
        }

        T get(String name) {
            EnumNameMap.Node<T> node = values[hashCode(name) & valuesMask];
            return node == null || !node.key.equals(name) ? null : node.value;
        }

        private static int hashCode(String name) {
            return name.hashCode() >>> 2;
        }

        private static final class Node<T> {
            final String key;
            final T value;

            Node(String key, T value) {
                this.key = key;
                this.value = value;
            }
        }
    }
}
