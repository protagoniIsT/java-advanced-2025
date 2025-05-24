package info.kgeorgiy.ja.gordienko.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;
import info.kgeorgiy.java.advanced.hello.NewHelloClient;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

public class HelloUDPClient implements NewHelloClient {

    @Override
    public void newRun(List<Request> requests, int threads) {
        ExecutorService threadPool = Executors.newFixedThreadPool(threads);
        IntStream.rangeClosed(1, threads).<Runnable>mapToObj(threadNum -> () -> sendRequestsAndAwaitResponse(requests, threadNum))
                         .forEachOrdered(threadPool::submit);
        threadPool.close();
    }

    private void sendRequestsAndAwaitResponse(List<Request> requests, int threadNum) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(100);
            byte[] buf = new byte[socket.getReceiveBufferSize()];
            DatagramPacket response = new DatagramPacket(buf, buf.length);

            for (Request req : requests) {
                String requestMessage = req.template().replace("$", Integer.toString(threadNum));
                byte[] requestBytes = requestMessage.getBytes(StandardCharsets.UTF_8);
                InetAddress addr = InetAddress.getByName(req.host());
                DatagramPacket request = new DatagramPacket(requestBytes, requestBytes.length, addr, req.port());

                while (true) {
                    socket.send(request);
                    try {
                        socket.receive(response);
                        String responseMessage = new String(
                                response.getData(), response.getOffset(), response.getLength(), StandardCharsets.UTF_8
                        );
                        if (responseMessage.contains(requestMessage)) {
                            System.out.printf("Request: %s%nResponse: %s%n", requestMessage, responseMessage);
                            break;
                        }
                    } catch (SocketTimeoutException ignored) {
                    }
                }
            }
        } catch (SocketException e) {
            System.err.println("Error opening DatagramSocket: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Client error in thread " + threadNum + ": " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        if (args.length != 5) {
            System.err.println("Usage: <host port prefix requests threads>");
            return;
        }

        if (Arrays.stream(args).anyMatch(Objects::isNull)) {
            System.err.println("Arguments should not be null");
        }

        String host = args[0];

        int port;
        try {
            port = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.err.println("Second argument <port> should be integer");
            return;
        }

        String prefix = args[2];

        int requests;
        int threads;
        try {
            requests = Integer.parseInt(args[3]);
            threads = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            System.err.println("Arguments <requests> and <threads> should be integer");
            return;
        }

        HelloClient helloClient = new HelloUDPClient();
        helloClient.run(host, port, prefix, requests, threads);
    }
}
