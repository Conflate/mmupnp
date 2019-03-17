/*
 * Copyright (c) 2019 大前良介 (OHMAE Ryosuke)
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.mm2d.upnp.internal.server;

import net.mm2d.log.Logger;
import net.mm2d.upnp.Http;
import net.mm2d.upnp.SsdpMessage;
import net.mm2d.upnp.internal.thread.TaskExecutors;
import net.mm2d.upnp.util.IoUtils;
import net.mm2d.upnp.util.NetworkUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.MalformedURLException;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.FutureTask;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * SsdpServerの共通処理を実装するクラス。
 *
 * @author <a href="mailto:ryo@mm2d.net">大前良介 (OHMAE Ryosuke)</a>
 */
// TODO: SocketChannelを使用した受信(MulticastChannelはAndroid N以降のため保留)
class SsdpServerDelegate implements SsdpServer {
    interface Receiver {
        /**
         * メッセージ受信後の処理、サブクラスにより実装する。
         *
         * @param sourceAddress 送信元アドレス
         * @param data          受信したデータ
         * @param length        受信したデータの長さ
         */
        void onReceive(
                @Nonnull InetAddress sourceAddress,
                @Nonnull byte[] data,
                int length);
    }

    @Nonnull
    private final TaskExecutors mTaskExecutors;
    @Nonnull
    private final Address mAddress;
    @Nonnull
    private final Receiver mReceiver;
    @Nonnull
    private final NetworkInterface mInterface;
    @Nonnull
    private final InterfaceAddress mInterfaceAddress;

    private final int mBindPort;
    @Nullable
    private MulticastSocket mSocket;
    @Nullable
    private ReceiveTask mReceiveTask;

    /**
     * 使用するインターフェースを指定してインスタンス作成。
     *
     * <p>使用するポートは自動割当となる。
     *
     * @param receiver         パケット受信時にコールされるreceiver
     * @param networkInterface 使用するインターフェース
     * @param address          モード
     */
    SsdpServerDelegate(
            @Nonnull final TaskExecutors executors,
            @Nonnull final Receiver receiver,
            @Nonnull final Address address,
            @Nonnull final NetworkInterface networkInterface) {
        this(executors, receiver, address, networkInterface, 0);
    }

    /**
     * 使用するインターフェースとポート指定してインスタンス作成。
     *
     * @param receiver         パケット受信時にコールされるreceiver
     * @param networkInterface 使用するインターフェース
     * @param bindPort         使用するポート
     * @param address          モード
     */
    SsdpServerDelegate(
            @Nonnull final TaskExecutors executors,
            @Nonnull final Receiver receiver,
            @Nonnull final Address address,
            @Nonnull final NetworkInterface networkInterface,
            final int bindPort) {
        mTaskExecutors = executors;
        mInterface = networkInterface;
        mInterfaceAddress = address == Address.IP_V4 ?
                findInet4Address(networkInterface.getInterfaceAddresses()) :
                findInet6Address(networkInterface.getInterfaceAddresses());
        mBindPort = bindPort;
        mReceiver = receiver;
        mAddress = address;
    }

    /**
     * マルチキャストアドレスを返す。
     *
     * @return マルチキャストアドレス
     */
    @Nonnull
    Address getAddress() {
        return mAddress;
    }

    /**
     * SSDPに使用するSocketAddress。
     *
     * @return SSDPで使用するInetSocketAddress
     */
    @Nonnull
    private InetSocketAddress getSsdpSocketAddress() {
        return mAddress.getSocketAddress();
    }

    /**
     * SSDPに使用するアドレス。
     *
     * @return SSDPで使用するInetAddress
     */
    @Nonnull
    InetAddress getSsdpInetAddress() {
        return mAddress.getInetAddress();
    }

    /**
     * SSDPに使用するアドレス＋ポートの文字列。
     *
     * @return SSDPに使用するアドレス＋ポートの文字列。
     */
    @Nonnull
    String getSsdpAddressString() {
        return mAddress.getAddressString();
    }

    // VisibleForTesting
    @Nonnull
    static InterfaceAddress findInet4Address(@Nonnull final List<InterfaceAddress> addressList) {
        for (final InterfaceAddress address : addressList) {
            if (address.getAddress() instanceof Inet4Address) {
                return address;
            }
        }
        throw new IllegalArgumentException("ni does not have IPv4 address.");
    }

    // VisibleForTesting
    @Nonnull
    static InterfaceAddress findInet6Address(@Nonnull final List<InterfaceAddress> addressList) {
        for (final InterfaceAddress address : addressList) {
            final InetAddress inetAddress = address.getAddress();
            if (inetAddress instanceof Inet6Address) {
                if (inetAddress.isLinkLocalAddress()) {
                    return address;
                }
            }
        }
        throw new IllegalArgumentException("ni does not have IPv6 address.");
    }

    @Nonnull
    public InterfaceAddress getInterfaceAddress() {
        return mInterfaceAddress;
    }

    @Nonnull
    public InetAddress getLocalAddress() {
        return mInterfaceAddress.getAddress();
    }

    @Override
    public void open() throws IOException {
        if (mSocket != null) {
            close();
        }
        mSocket = createMulticastSocket(mBindPort);
        mSocket.setNetworkInterface(mInterface);
        mSocket.setTimeToLive(4);
    }

    // VisibleForTesting
    @Nonnull
    MulticastSocket createMulticastSocket(final int port) throws IOException {
        return new MulticastSocket(port);
    }

    @Override
    public void close() {
        stop();
        IoUtils.closeQuietly(mSocket);
        mSocket = null;
    }

    @Override
    public void start() {
        if (mSocket == null) {
            throw new IllegalStateException("socket is null");
        }
        if (mReceiveTask != null) {
            stop();
        }
        final String suffix = (mBindPort == 0 ? "-ssdp-notify-" : "-ssdp-search-")
                + mInterface.getName() + "-"
                + NetworkUtils.toSimpleString(mInterfaceAddress.getAddress());
        mReceiveTask = new ReceiveTask(mReceiver, mSocket, getSsdpInetAddress(), mBindPort);
        mReceiveTask.start(mTaskExecutors, suffix);
    }

    @Override
    public void stop() {
        if (mReceiveTask == null) {
            return;
        }
        mReceiveTask.shutdownRequest();
        mReceiveTask = null;
    }

    @Override
    public void send(@Nonnull final SsdpMessage message) {
        mTaskExecutors.io(() -> sendInner(message));
    }

    private void sendInner(@Nonnull final SsdpMessage message) {
        final ReceiveTask task = mReceiveTask;
        if (task == null || !task.waitReady()) {
            return;
        }
        final MulticastSocket socket = mSocket;
        if (socket == null) {
            return;
        }
        Logger.d(() -> "send from " + getInterfaceAddress() + ":\n" + message);
        try {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            message.writeData(baos);
            final byte[] data = baos.toByteArray();
            socket.send(new DatagramPacket(data, data.length, getSsdpSocketAddress()));
        } catch (final IOException e) {
            Logger.w(e);
        }
    }

    /**
     * SsdpMessageのLocationに正常なURLが記述されており、
     * 記述のアドレスとパケットの送信元アドレスに不一致がないか検査する。
     *
     * @param message       確認するSsdpMessage
     * @param sourceAddress 送信元アドレス
     * @return true:送信元との不一致を含めてLocationに不正がある場合。false:それ以外
     */
    public boolean isInvalidLocation(
            @Nonnull final SsdpMessage message,
            @Nonnull final InetAddress sourceAddress) {
        return !isValidLocation(message, sourceAddress);
    }

    private boolean isValidLocation(
            @Nonnull final SsdpMessage message,
            @Nonnull final InetAddress sourceAddress) {
        final String location = message.getLocation();
        if (!Http.isHttpUrl(location)) {
            return false;
        }
        try {
            final InetAddress locationAddress = InetAddress.getByName(new URL(location).getHost());
            return sourceAddress.equals(locationAddress);
        } catch (final MalformedURLException | UnknownHostException ignored) {
        }
        return false;
    }

    // VisibleForTesting
    static class ReceiveTask implements Runnable {
        @Nonnull
        private final Receiver mReceiver;
        @Nonnull
        private final MulticastSocket mSocket;
        @Nonnull
        private final InetAddress mInetAddress;

        private final int mBindPort;
        @Nullable
        private FutureTask<?> mFutureTask;
        @Nullable
        private String mSuffix;

        private boolean mReady;

        private synchronized boolean waitReady() {
            final FutureTask<?> task = mFutureTask;
            if (task == null || task.isDone()) {
                return false;
            }
            if (!mReady) {
                try {
                    wait(500);
                } catch (final InterruptedException ignored) {
                }
            }
            return mReady;
        }

        private synchronized void ready() {
            mReady = true;
            notifyAll();
        }

        /**
         * インスタンス作成
         */
        ReceiveTask(
                @Nonnull final Receiver receiver,
                @Nonnull final MulticastSocket socket,
                @Nonnull final InetAddress address,
                final int port) {
            mReceiver = receiver;
            mSocket = socket;
            mInetAddress = address;
            mBindPort = port;
        }

        /**
         * スレッドを作成して処理を開始する。
         */
        synchronized void start(
                @Nonnull final TaskExecutors executors,
                @Nonnull final String suffix) {
            mReady = false;
            mSuffix = suffix;
            mFutureTask = new FutureTask<>(this, null);
            executors.server(mFutureTask);
        }

        /**
         * 割り込みを行い、スレッドを終了させる。
         *
         * <p>現在はSocketを使用しているため割り込みは効果がない。
         */
        synchronized void shutdownRequest() {
            if (mFutureTask != null) {
                mFutureTask.cancel(false);
                mFutureTask = null;
            }
        }

        @Override
        public void run() {
            final Thread thread = Thread.currentThread();
            thread.setName(thread.getName() + mSuffix);
            if (mFutureTask == null || mFutureTask.isCancelled()) {
                return;
            }
            try {
                joinGroup();
                ready();
                receiveLoop();
                leaveGroup();
            } catch (final IOException ignored) {
            }
        }

        /**
         * 受信処理を行う。
         *
         * @throws IOException 入出力処理で例外発生
         */
        // VisibleForTesting
        void receiveLoop() throws IOException {
            final byte[] buf = new byte[1500];
            while (mFutureTask != null && !mFutureTask.isCancelled()) {
                try {
                    final DatagramPacket dp = new DatagramPacket(buf, buf.length);
                    mSocket.receive(dp);
                    if (mFutureTask == null || mFutureTask.isCancelled()) {
                        break;
                    }
                    mReceiver.onReceive(dp.getAddress(), dp.getData(), dp.getLength());
                } catch (final SocketTimeoutException ignored) {
                }
            }
        }

        /**
         * Joinを行う。
         *
         * <p>特定ポートにBindしていない（マルチキャスト受信ソケットでない）場合は何も行わない
         *
         * @throws IOException Joinコールにより発生
         */
        // VisibleForTesting
        void joinGroup() throws IOException {
            if (mBindPort != 0) {
                mSocket.joinGroup(mInetAddress);
            }
        }

        /**
         * Leaveを行う。
         *
         * <p>特定ポートにBindしていない（マルチキャスト受信ソケットでない）場合は何も行わない
         *
         * @throws IOException Leaveコールにより発生
         */
        // VisibleForTesting
        void leaveGroup() throws IOException {
            if (mBindPort != 0) {
                mSocket.leaveGroup(mInetAddress);
            }
        }
    }
}
