package ru.ifmo.rain.shaposhnikov.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;
import ru.ifmo.rain.shaposhnikov.hello.Util.ExchangeDatagramPacket;

import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * Sending requests to the HelloUDPServer.
 *
 * @author Boris Shaposhnikov
 */
public class HelloUDPClient implements HelloClient {
    private static void requestUDP(final SocketAddress socketAddress, final String prefix,
                                   final int thread, final int requests) {
        try (final DatagramSocket datagramSocket = new DatagramSocket()) {
            datagramSocket.setSoTimeout(Util.TIMEOUT_MILLISECONDS);
            final ExchangeDatagramPacket requestPacket =
                    new ExchangeDatagramPacket(datagramSocket.getReceiveBufferSize(), socketAddress);
            IntStream.range(0, requests).forEach(request -> {
                final String requestMessage = String.format("%s%d_%d", prefix, thread, request);
                String response = "";
                while (!datagramSocket.isClosed()
                        && !Thread.currentThread().isInterrupted()
                        && !Util.isRespond(response, thread, request)) {
                    response = requestPacket.request(requestMessage, datagramSocket);
                }
            });
        } catch (final SocketException e) {
            System.err.println("Error during creating datagram socket: " + e.getMessage());
        }
    }

    @Override
    public void run(final String host, final int port, final String prefix, final int threads, final int requests) {
        final SocketAddress socketAddress;
        try {
            socketAddress = new InetSocketAddress(InetAddress.getByName(host), port);
        } catch (final UnknownHostException e) {
            System.err.println("Invalid host name: " + e.getMessage());
            return;
        }

        final ExecutorService threadPool = Executors.newFixedThreadPool(threads);
        IntStream.range(0, threads)
                .forEach(thread -> threadPool.submit(() -> requestUDP(socketAddress, prefix, thread, requests)));
        threadPool.shutdown();
        try {
            System.out.println(threadPool.awaitTermination(Util.TIMEOUT_COEFFICIENT_SECONDS * threads * requests, TimeUnit.SECONDS)
                    ? "All requests are processed"
                    : "Timeout exceeded");
        } catch (final InterruptedException e) {
            System.err.println("Working threads were interrupted: " + e.getMessage());
        }
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
        Util.startClient(args, HelloUDPClient::new);
    }
}