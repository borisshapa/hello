package ru.ifmo.rain.shaposhnikov.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.IntStream;

/**
 * NIO-client sending requests to the HelloUDPServer.
 *
 * @author Boris Shaposhnikov
 */
public class HelloUDPNonblockingClient implements HelloClient {
    private Selector selector;

    private final Set<DatagramChannel> waitingChannels = new HashSet<>();
    private int requests;
    private int workingThreads;
    private SocketAddress socketAddress;


    private final Consumer<DatagramChannel> CLOSE_CHANNEL = channel -> {
        Util.tryClose(channel);
        workingThreads--;
    };

    private void write(final String prefix, final SelectionKey key) {
        final DatagramChannel channel = (DatagramChannel) key.channel();
        final ChannelInfo channelInfo = (ChannelInfo) key.attachment();

        final int thread = channelInfo.getIndex();
        final int request = channelInfo.getCurrentRequest();

        final String requestMessage = String.format("%s%d_%d", prefix, thread, request);
        if (Util.send(channel, requestMessage.getBytes(Util.CHARSET), socketAddress, CLOSE_CHANNEL)) {
            waitingChannels.add(channel);
            key.interestOps(SelectionKey.OP_READ);
        }
    }

    private void read(final SelectionKey key) {
        final DatagramChannel channel = (DatagramChannel) key.channel();
        final ChannelInfo channelInfo = (ChannelInfo) key.attachment();

        final int thread = channelInfo.getIndex();
        final int request = channelInfo.getCurrentRequest();

        Util.receive(channel, channelInfo.getBuffer(), CLOSE_CHANNEL);

        final String response = Util.read(channelInfo.getBuffer());
        if (Util.isRespond(response, thread, request)) {
            waitingChannels.remove(channel);
            key.interestOps(SelectionKey.OP_WRITE);
            if (channelInfo.increaseCurrentRequest() == requests) {
                CLOSE_CHANNEL.accept(channel);
            }
        }
    }

    private void run(final String prefix) {
        while (workingThreads > 0) {
            try {
                if (selector.select(Util.TIMEOUT_MILLISECONDS) == 0) {
                    waitingChannels.forEach(channel -> channel.keyFor(selector).interestOps(SelectionKey.OP_WRITE));
                }
                for (final Iterator<SelectionKey> it = selector.selectedKeys().iterator(); it.hasNext(); ) {
                    final SelectionKey key = it.next();
                    try {
                        if (key.isWritable()) {
                            write(prefix, key);
                        }
                        if (key.isValid() && key.isReadable()) {
                            read(key);
                        }
                    } finally {
                        it.remove();
                    }
                }
            } catch (final IOException e) {
                System.err.println("Error during selecting: " + e.getMessage());
            }
        }
    }

    @Override
    public void run(final String host, final int port, final String prefix, final int threads, final int requests) {
        try {
            socketAddress = new InetSocketAddress(InetAddress.getByName(host), port);
        } catch (final UnknownHostException e) {
            System.err.println("Invalid host name: " + e.getMessage());
            return;
        }

        selector = Util.tryOpenSelector();
        if (selector == null) {
            return;
        }

        final List<DatagramChannel> openingChannel =  new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            try {
                final DatagramChannel channel = DatagramChannel.open();
                openingChannel.add(channel);

                channel.configureBlocking(false);
                channel.connect(socketAddress);
                channel.register(selector, SelectionKey.OP_WRITE, new ChannelInfo(channel, i));
            } catch (final IOException e) {
                Util.tryClose(selector);
                openingChannel.forEach(Util::tryClose);
                System.err.println("Error during creating a datagram channel: " + e.getMessage());
                return;
            }
        }
        this.workingThreads = threads;
        this.requests = requests;
        run(prefix);
    }

    /**
     * Main function for running the client.
     *
     * @param args <ul>
     *             <li>1 - host</li>
     *             <li>2 - port</li>
     *             <li>3 - request prefix</li>
     *             <li>4 - threads</li>
     *             <li>5 - requests in thread</li>
     *             </ul>
     */
    public static void main(final String[] args) {
        Util.startClient(args, HelloUDPNonblockingClient::new);
    }

    private static class ChannelInfo {
        private final int index;
        private int currentRequest;
        private final ByteBuffer buffer;

        ChannelInfo(final DatagramChannel channel, final int index) throws SocketException {
            this.index = index;
            this.currentRequest = 0;
            buffer = ByteBuffer.allocate(channel.socket().getReceiveBufferSize());
        }

        public int getIndex() {
            return index;
        }

        public int getCurrentRequest() {
            return currentRequest;
        }

        public int increaseCurrentRequest() {
            return ++currentRequest;
        }

        public ByteBuffer getBuffer() {
            return buffer;
        }
    }
}
