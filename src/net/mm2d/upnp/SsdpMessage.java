/*
 * Copyright(C) 2016 大前良介(OHMAE Ryosuke)
 */

package net.mm2d.upnp;

import net.mm2d.util.Log;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;

/**
 * @author <a href="mailto:ryo@mm2d.net">大前良介(OHMAE Ryosuke)</a>
 */
public abstract class SsdpMessage {
    private static final String TAG = "SsdpMessage";
    public static final String M_SEARCH = "M-SEARCH";
    public static final String NOTIFY = "NOTIFY";
    public static final String SSDP_ALIVE = "ssdp:alive";
    public static final String SSDP_BYEBYE = "ssdp:byebye";
    public static final String SSDP_UPDATE = "ssdp:update";
    public static final String SSDP_DISCOVER = "\"ssdp:discover\"";
    private final HttpMessage mMessage;
    private static final int DEFAULT_MAX_AGE = 1800;
    private int mMaxAge;
    private long mExpireTime;
    private String mUuid;
    private String mType;
    private String mNts;
    private String mLocation;
    private InterfaceAddress mInterfaceAddress;
    private InetSocketAddress mSourceAddress;
    private boolean mValidSegment;

    protected abstract HttpMessage newMessage();

    protected HttpMessage getMessage() {
        return mMessage;
    }

    public SsdpMessage() {
        mMessage = newMessage();
    }

    public SsdpMessage(InterfaceAddress addr, DatagramPacket dp) {
        mMessage = newMessage();
        mInterfaceAddress = addr;
        mSourceAddress = (InetSocketAddress) dp.getSocketAddress();
        mValidSegment = isSameSegment(mInterfaceAddress, mSourceAddress);
        try {
            mMessage.readData(new ByteArrayInputStream(dp.getData(), 0, dp.getLength()));
        } catch (final IOException e) {
            Log.w(TAG, e);
        }
        parseMessage();
    }

    public void parseMessage() {
        parseCacheControl();
        parseUsn();
        mExpireTime = mMaxAge * 1000 + System.currentTimeMillis();
        mLocation = mMessage.getHeader(Http.LOCATION);
        mNts = mMessage.getHeader(Http.NTS);
    }

    private boolean isSameSegment(InterfaceAddress ifa, InetSocketAddress sa) {
        final byte[] a = ifa.getAddress().getAddress();
        final byte[] b = sa.getAddress().getAddress();
        final int pref = ifa.getNetworkPrefixLength();
        final int bytes = pref / 8;
        final int bits = pref % 8;
        for (int i = 0; i < bytes; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        if (bits != 0) {
            final byte mask = (byte) (0xff << (8 - bits));
            if ((a[bytes] & mask) != (b[bytes] & mask)) {
                return false;
            }
        }
        return true;
    }

    public InterfaceAddress getInterfaceAddress() {
        return mInterfaceAddress;
    }

    private void parseCacheControl() {
        mMaxAge = DEFAULT_MAX_AGE;
        final String age = mMessage.getHeader(Http.CACHE_CONTROL);
        if (age == null || !age.toLowerCase().startsWith("max-age")) {
            return;
        }
        final int pos = age.indexOf('=');
        if (pos < 0 || pos + 1 == age.length()) {
            return;
        }
        try {
            mMaxAge = Integer.parseInt(age.substring(pos + 1));
        } catch (final NumberFormatException e) {
            return;
        }
    }

    private void parseUsn() {
        final String usn = mMessage.getHeader(Http.USN);
        if (usn == null || !usn.startsWith("uuid")) {
            return;
        }
        final int pos = usn.indexOf("::");
        if (pos < 0) {
            mUuid = usn;
            return;
        }
        mUuid = usn.substring(0, pos);
        if (pos + 2 < usn.length()) {
            mType = usn.substring(pos + 2);
        }
    }

    public boolean isValidSegment() {
        return mValidSegment;
    }

    public String getHeader(String name) {
        return mMessage.getHeader(name);
    }

    public void setHeader(String name, String value) {
        mMessage.setHeader(name, value);
    }

    public String getUuid() {
        return mUuid;
    }

    public String getType() {
        return mType;
    }

    public String getNts() {
        return mNts;
    }

    public int getMaxAge() {
        return mMaxAge;
    }

    public long getExpireTime() {
        return mExpireTime;
    }

    public String getLocation() {
        return mLocation;
    }

    @Override
    public String toString() {
        return mMessage.toString();
    }
}