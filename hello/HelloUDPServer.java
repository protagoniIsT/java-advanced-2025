package info.kgeorgiy.ja.gordienko.hello;

import info.kgeorgiy.java.advanced.hello.HelloServer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

public class HelloUDPServer implements HelloServer {
    private ExecutorService threadPool;
    private DatagramSocket socket;

    @Override
    public void start(int port, int threads) {
        try {
            socket = new DatagramSocket(port);
        } catch (SocketException e) {
            System.err.printf("Could not open socket on port '%s'", port);
            return;
        }
        threadPool = Executors.newFixedThreadPool(threads);
        IntStream.range(0, threads).forEach(i -> threadPool.submit(this::receivePacketsAndSendResponse));
    }

    private void receivePacketsAndSendResponse() {
        while (!socket.isClosed()) {
            DatagramPacket request;
            try {
                request = new DatagramPacket(
                        new byte[socket.getReceiveBufferSize()],
                        socket.getReceiveBufferSize()
                );
            } catch (SocketException e) {
                System.err.println("UDP error: " + e.getMessage());
                continue;
            }

            try {
                socket.receive(request);

                String requestMessage = new String(
                        request.getData(),
                        request.getOffset(),
                        request.getLength(),
                        StandardCharsets.UTF_8
                );

                String responseMessage = "Hello, " + requestMessage;
                byte[] responseBytes = responseMessage.getBytes(StandardCharsets.UTF_8);
                DatagramPacket response = new DatagramPacket(responseBytes,
                        0,
                        responseBytes.length,
                        request.getSocketAddress());
                socket.send(response);
            } catch (IOException e) {
                System.err.println("I/O error occurred while receiving packet: " + e.getMessage());
            }
        }
    }

    @Override
    public void close() {
        if (socket != null && !socket.isClosed()) socket.close();
        if (threadPool != null) threadPool.close();
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: <port> <threads>");
            return;
        }

        if (Arrays.stream(args).anyMatch(Objects::isNull)) {
            System.err.println("Arguments should not be null");
            return;
        }

        int port, threads;

        try {
            port = Integer.parseInt(args[0]);
            threads = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.err.println("<port> and <threads> arguments should be integer");
            return;
        }

        HelloServer helloServer = new HelloUDPServer();
        helloServer.start(port, threads);
    }
}
