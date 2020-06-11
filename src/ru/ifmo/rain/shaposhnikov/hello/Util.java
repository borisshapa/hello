package ru.ifmo.rain.shaposhnikov.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;
import info.kgeorgiy.java.advanced.hello.HelloServer;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Selector;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper methods for Hello client and server.
 *
 * @author Boris Shaposhnikov
 */
public class Util {
    /**
     * A regular expression to verify the response is correct.
     */
    private static final Pattern RESPOND_PATTERN = Pattern.compile("\\D*(\\d+)\\D*(\\d+)\\D*");

    /**
     * UTF-8 charset
     */
    public static final Charset CHARSET = StandardCharsets.UTF_8;

    /**
     * Timeout for {@link java.nio.channels.Selector} and {@link DatagramSocket} of clients
     */
    public static final int TIMEOUT_MILLISECONDS = 300;

    /**
     * Timeout for {@link java.util.concurrent.ExecutorService}
     */
    public static final int AWAITING_TIME_SECONDS = 3;

    /**
     * Time to send one {@link HelloClient} request.
     */
    public static final int TIMEOUT_COEFFICIENT_SECONDS = 1000;

    /**
     * Parse integer argument
     *
     * @param arg argument to parse
     * @return integer
     */
    public static int parseIntegerArgument(final String arg) {
        Objects.requireNonNull(arg);
        return Integer.parseInt(arg);
    }

    /**
     * Checks if the response is correct for the given request.
     *
     * @param response checked request
     * @param thread   thread number in request
     * @param request  request number in request
     * @return <var>true</var> if and only if the request is correct, otherwise <var>false</var>.
     */
    public static boolean isRespond(final String response, final int thread, final int request) {
        final Matcher matcher = RESPOND_PATTERN.matcher(response);
        return matcher.matches()
                && matcher.group(1).equals(String.valueOf(thread))
                && matcher.group(2).equals(String.valueOf(request));
    }

    /**
     * Sends a request on passed address.
     *
     * @param channel {@link DatagramChannel} from which we are sending
     * @param request request data
     * @param address where to send
     * @param close   {@link Consumer} channel closing function in case of error
     * @return <var>true</var> if and only if the sending was successful, otherwise <var>false</var>.
     */
    public static boolean send(final DatagramChannel channel,
                               final byte[] request,
                               final SocketAddress address,
                               final Consumer<DatagramChannel> close) {
        try {
            channel.send(ByteBuffer.wrap(request), address);
            System.out.println("Send: " + new String(request, CHARSET));
            return true;
        } catch (final IOException e) {
            close.accept(channel);
            System.err.println("Error during sending: " + e.getMessage());
            return false;
        }
    }

    /**
     * Receives a request.
     *
     * @param channel {@link DatagramChannel} from which we are receiving
     * @param buffer  where to put the received data
     * @param close   {@link Consumer} channel closing function in case of error
     * @return {@link SocketAddress} where did the data come from if the receipt was successful and <var>null</var> if with an error.
     */
    public static SocketAddress receive(final DatagramChannel channel,
                                        final ByteBuffer buffer,
                                        final Consumer<DatagramChannel> close) {
        try {
            System.out.println("Receive: " + new String(buffer.array(), CHARSET));
            return channel.receive(buffer);
        } catch (final IOException e) {
            close.accept(channel);
            System.err.println("Error during receiving: " + e.getMessage());
            return null;
        }
    }

    /**
     * Gets data from the channel. Data is read until the first 0 byte or until the {@link ByteBuffer#limit()}.
     *
     * @param buffer where to put data
     * @return {@link DatagramPacket} in which the received data and the address from which it was received are stored.
     */
    public static String read(final ByteBuffer buffer) {
        buffer.flip();
        int total = 0;
        byte[] bytes = new byte[10];
        for (int i = 0; i < buffer.limit(); i++) {
            final byte b = buffer.get();
            if (b == 0) {
                break;
            } else {
                if (bytes.length == total) {
                    final byte[] newBytes = new byte[bytes.length * 2];
                    System.arraycopy(bytes, 0, newBytes, 0, bytes.length);
                    bytes = newBytes;
                }
                bytes[total++] = b;
            }
        }
        buffer.clear();
        return new String(bytes, 0, total, CHARSET);
    }

    /**
     * Starts a {@link HelloServer}.
     *
     * @param args       arguments for startup a server
     * @param serverType {@link Supplier} returning a {@link HelloServer} of the desired type
     */
    public static void startServer(final String[] args, final Supplier<HelloServer> serverType) {
        final Util.ServerContext serverContext = Util.ServerContext.parseServerArguments(args);
        if (serverContext == null) {
            return;
        }
        try (final HelloServer server = serverType.get()) {
            server.start(serverContext.getPort(), serverContext.getThreads());
            System.out.println("The server was started");
            try (final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                reader.readLine();
            } catch (final IOException e) {
                System.err.println("Error during reading from the console");
            }
        }
    }

    /**
     * Opens a new {@link Selector}
     *
     * @return open selector, if the opening went without errors and <var>null</var> otherwise
     */
    public static Selector tryOpenSelector() {
        try {
            return Selector.open();
        } catch (final IOException e) {
            System.err.println("Error during opening selector: " + e.getMessage());
            return null;
        }
    }

    /**
     * Attempts to close an object that implements an {@link Closeable} interface.
     *
     * @param closeable what to close
     */
    public static void tryClose(final Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (final IOException e) {
            System.err.println("Error during closing: " + e.getMessage());
        }
    }

    /**
     * Shutdowns an {@link ExecutorService} and and waits for the completion of tasks or the expiration of the timeout.
     *
     * @param executorService what to shutdown
     */
    public static void tryShutdown(final ExecutorService executorService) {
        if (executorService == null) {
            return;
        }
        executorService.shutdownNow();
        try {
            executorService.awaitTermination(Util.AWAITING_TIME_SECONDS, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            System.err.println("Error during terminate executor services: " + e.getMessage());
        }
    }

    /**
     * Starts a {@link HelloClient}.
     *
     * @param args       arguments for startup a client
     * @param clientType {@link Supplier} returning a {@link HelloClient} of the desired type
     */
    public static void startClient(final String[] args, final Supplier<HelloClient> clientType) {
        final Util.ClientContext clientContext = Util.ClientContext.parseClientArguments(args);
        if (clientContext == null) {
            return;
        }
        clientType.get().run(clientContext.getHost(), clientContext.getPort(),
                clientContext.getPrefix(), clientContext.getThreads(), clientContext.getRequests());
        System.out.println("The client was started");
    }

    /**
     * Server startup parameters
     */
    public static class ServerContext {
        private final int port;
        private final int threads;

        /**
         * Creates class with passed parameters.
         *
         * @param port    the port to which the server is bound.
         * @param threads the number of threads that the server must create to process requests.
         */
        ServerContext(final int port, final int threads) {
            this.port = port;
            this.threads = threads;
        }

        /**
         * Returns number of threads that the server must create to process requests.
         *
         * @return number of threads
         */
        public int getThreads() {
            return threads;
        }

        /**
         * Returns port to which the server is bound.
         *
         * @return port
         */
        public int getPort() {
            return port;
        }

        /**
         * Creates a structure with parameters from {@link String} array
         *
         * @param args arguments from which to take data
         * @return server context
         */
        public static ServerContext parseServerArguments(final String[] args) {
            Objects.requireNonNull(args);
            if (args.length != 2) {
                System.err.println("Expected 2 arguments");
                return null;
            }
            final int port = Util.parseIntegerArgument(args[0]);
            final int threads = Util.parseIntegerArgument(args[1]);
            return new ServerContext(port, threads);
        }
    }

    /**
     * Client startup parameters
     */
    public static class ClientContext {
        private final String host;
        private final int port;
        private final String prefix;
        private final int threads;
        private final int requests;

        /**
         * Creates class with passed parameters.
         *
         * @param host     the host on which to create the client
         * @param port     the port on which to create the client
         * @param prefix   the request prefix
         * @param threads  the number of threads that the client must create to send requests
         * @param requests the number of requests that the client must send in one thread
         */
        ClientContext(final String host, final int port,
                      final String prefix, final int threads, final int requests) {
            this.host = host;
            this.port = port;
            this.prefix = prefix;
            this.threads = threads;
            this.requests = requests;
        }

        /**
         * Returns the host on which to create the client.
         *
         * @return port
         */
        public int getPort() {
            return port;
        }

        /**
         * Returns the number of requests that the client must send in one thread.
         *
         * @return number of requests
         */
        public int getRequests() {
            return requests;
        }

        /**
         * Returns the number of threads that the client must create to send requests.
         *
         * @return number of threads
         */
        public int getThreads() {
            return threads;
        }

        /**
         * Returns the host on which to create the client.
         *
         * @return host
         */
        public String getHost() {
            return host;
        }

        /**
         * Returns the request prefix.
         *
         * @return prefix {@link String}
         */
        public String getPrefix() {
            return prefix;
        }

        /**
         * Creates a structure with parameters from {@link String} array
         *
         * @param args arguments from which to take data
         * @return client context
         */
        public static ClientContext parseClientArguments(final String[] args) {
            Objects.requireNonNull(args);
            if (args.length != 5) {
                System.err.println("Expected 5 arguments");
                return null;
            }
            final String host = args[0];
            final int port = Util.parseIntegerArgument(args[1]);
            final String prefix = args[2];
            final int threads = Util.parseIntegerArgument(args[3]);
            final int requests = Util.parseIntegerArgument(args[4]);
            if (threads < 1) {
                System.err.println("Working threads count must be a positive number");
                return null;
            }
            if (requests < 1) {
                System.err.println("Requests count must be a positive number");
                return null;
            }
            return new ClientContext(host, port, prefix, threads, requests);
        }
    }


    /**
     * Datagram packet wrapper for reusing
     */
    public static class ExchangeDatagramPacket {
        private final byte[] buffer;
        private final DatagramPacket datagramPacket;

        /**
         * Constructs datagram packet with buffer size for receive passed
         *
         * @param bufferSize buffer size for receiving
         */
        ExchangeDatagramPacket(final int bufferSize) {
            buffer = new byte[bufferSize];
            datagramPacket = new DatagramPacket(buffer, bufferSize);
        }

        /**
         * Constructs datagram packet with buffer size for receive passed and binding to <var>socketAddress</var>
         *
         * @param bufferSize    buffer size for receiving
         * @param socketAddress socket address to bind
         */
        ExchangeDatagramPacket(final int bufferSize, final SocketAddress socketAddress) {
            this(bufferSize);
            datagramPacket.setSocketAddress(socketAddress);
        }

        /**
         * Packs {@link String} to packet
         *
         * @param string {@link String} to pack
         */
        public void setData(final String string) {
            datagramPacket.setData(string.getBytes(CHARSET));
            datagramPacket.setLength(datagramPacket.getData().length);
        }

        /**
         * Gets {@link String} from packet buffer
         *
         * @return message from packet
         */
        public String getData() {
            return new String(datagramPacket.getData(), datagramPacket.getOffset(), datagramPacket.getLength(), CHARSET);
        }

        /**
         * Replaces buffer to empty buffer with the size available for this socket
         */
        public void clear() {
            datagramPacket.setData(buffer);
            datagramPacket.setLength(buffer.length);
        }

        /**
         * Returns {@link DatagramPacket}
         *
         * @return {@link DatagramPacket}
         */
        public DatagramPacket getDatagramPacket() {
            return datagramPacket;
        }

        /**
         * Returns a response
         *
         * @param socket {@link DatagramSocket} to get response
         */
        public String receive(final DatagramSocket socket) {
            try {
                datagramPacket.setData(buffer, 0, buffer.length);
                socket.receive(datagramPacket);
                final String response = getData();
                System.out.println("Receive: " + response);
                return response;
            } catch (final IOException e) {
                System.err.println("Error during receiving :" + e.getMessage());
                return "";
            }
        }

        /**
         * Send a request {@link String}
         *
         * @param request what to send
         * @param socket  where to send
         */
        public void send(final String request, final DatagramSocket socket) {
            try {
                datagramPacket.setData(request.getBytes(CHARSET));
                datagramPacket.setLength(datagramPacket.getData().length);
                socket.send(datagramPacket);
                System.out.println("Send: " + getData());
            } catch (final IOException e) {
                System.err.println("Error during sending: " + e.getMessage());
            }
        }

        /**
         * Sends request and returns response
         *
         * @param request what to send
         * @param socket  {@link DatagramSocket} for package
         * @return response {@link String}
         */
        public String request(final String request, final DatagramSocket socket) {
            send(request, socket);
            return receive(socket);
        }
    }
}
