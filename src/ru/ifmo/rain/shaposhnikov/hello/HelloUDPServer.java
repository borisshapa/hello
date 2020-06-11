package ru.ifmo.rain.shaposhnikov.hello;

import info.kgeorgiy.java.advanced.hello.HelloServer;
import ru.ifmo.rain.shaposhnikov.hello.Util.ExchangeDatagramPacket;

import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Server returns "Hello, {@code <request>}" {@link String}
 *
 * @author Boris Shaposhnikov
 */
public class HelloUDPServer implements HelloServer {
    private DatagramSocket datagramSocket;
    private ExecutorService threadPool;
    private int bufferSize;

    private String response(final String request) {
        return "Hello, " + request;
    }

    private void listen() {
        final ExchangeDatagramPacket packet = new ExchangeDatagramPacket(bufferSize);
        while (!datagramSocket.isClosed() && !Thread.currentThread().isInterrupted()) {
            final String request = packet.receive(datagramSocket);
            packet.send(response(request), datagramSocket);
        }
    }

    @Override
    public void start(final int port, final int threads) {
        try {
            datagramSocket = new DatagramSocket(port);
            bufferSize = datagramSocket.getReceiveBufferSize();
        } catch (final SocketException e) {
            System.err.println("Error during creating a datagram socket: " + e.getMessage());
            return;
        }
        threadPool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            threadPool.submit(this::listen);
        }
    }

    @Override
    public void close() {
        Util.tryClose(datagramSocket);
        Util.tryShutdown(threadPool);
    }

    /**
     * Main function for running server.
     *
     * @param args <ul>
     *             <li>1 - port</li>
     *             <li>2 - threads count</li>
     *             </ul>
     */
    public static void main(final String[] args) {
        Util.startServer(args, HelloUDPServer::new);
    }
}
