package ru.ifmo.rain.shaposhnikov.hello;

import info.kgeorgiy.java.advanced.hello.HelloServer;

import java.io.Closeable;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * NIO server returns "Hello, {@code <request>}" {@link String}
 *
 * @author Boris Shaposhnikov
 */
public class HelloUDPNonblockingServer implements HelloServer {
    private Selector selector;
    private DatagramChannel serverChannel;

    private ExecutorService threadPool;
    private ExecutorService mainWorker;

    private final Queue<DatagramPacket> toWrite = new ConcurrentLinkedQueue<>();

    Deque<ByteBuffer> freeBuffers = new ConcurrentLinkedDeque<>();

    private final Consumer<DatagramChannel> CLOSE_CHANNEL = Util::tryClose;

    private void response(final SelectionKey key,
                          final ByteBuffer buffer,
                          final SocketAddress address) {
        final String request = Util.read(buffer);
        final byte[] response = ("Hello, " + request).getBytes(Util.CHARSET);
        toWrite.add(new DatagramPacket(response, response.length, address));
        freeBuffers.addFirst(buffer);
        key.interestOps(SelectionKey.OP_WRITE);
        selector.wakeup();
    }

    private void read(final SelectionKey key) {
        final DatagramChannel channel = (DatagramChannel) key.channel();
        if (freeBuffers.isEmpty()) {
            key.interestOpsAnd(~SelectionKey.OP_READ);
            return;
        }
        final ByteBuffer buffer = freeBuffers.removeFirst();
        final SocketAddress address = Util.receive(channel, buffer, CLOSE_CHANNEL);
        if (address == null) {
            return;
        }
        threadPool.submit(() -> response(key, buffer, address));
    }

    private void write(final SelectionKey key) {
        final DatagramChannel channel = (DatagramChannel) key.channel();
        if (toWrite.isEmpty()) {
            key.interestOps(SelectionKey.OP_READ);
            return;
        }
        final DatagramPacket packet = toWrite.poll();
        if (!Util.send(channel, packet.getData(), packet.getSocketAddress(), CLOSE_CHANNEL)) {
            return;
        }
        key .interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
    }

    private void run() {
        while (true) {
            try {
                selector.select();
                for (final Iterator<SelectionKey> it = selector.selectedKeys().iterator(); it.hasNext(); ) {
                    final SelectionKey key = it.next();
                    try {
                        if (key.isReadable()) {
                            read(key);
                        }
                        if (key.isValid() && key.isWritable()) {
                            write(key);
                        }
                    } finally {
                        it.remove();
                    }
                }
            } catch (final IOException e) {
                close();
                System.err.println("Error during selecting: " + e.getMessage());
                return;
            }
        }
    }

    @Override
    public void start(final int port, final int threads) {
        selector = Util.tryOpenSelector();
        if (selector == null) {
            return;
        }

        try {
            serverChannel = DatagramChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.bind(new InetSocketAddress(port));
            serverChannel.register(selector, SelectionKey.OP_READ);
        } catch (final IOException e) {
            close();
            System.err.println("Error during creating a datagram channel: " + e.getMessage());
            return;
        }

        mainWorker = Executors.newSingleThreadExecutor();
        threadPool = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            try {
                freeBuffers.add(ByteBuffer.allocate(serverChannel.socket().getReceiveBufferSize()));
            } catch (final SocketException e) {
                close();
                System.err.println("Error during allocating a buffer: " + e.getMessage());
                return;
            }
        }
        mainWorker.submit(this::run);
    }

    @Override
    public void close() {
        Util.tryClose(selector);
        Util.tryClose(serverChannel);

        Util.tryShutdown(mainWorker);
        Util.tryShutdown(threadPool);
    }

    /**
     * Main function for running NIO-server.
     *
     * @param args <ul>
     *             <li>1 - port</li>
     *             <li>2 - threads count</li>
     *             </ul>
     */
    public static void main(final String[] args) {
        Util.startServer(args, HelloUDPNonblockingServer::new);
    }
}
