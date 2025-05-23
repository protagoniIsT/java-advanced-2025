package info.kgeorgiy.java.advanced.hello;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;

public final class Util {
    private static final Charset CHARSET = StandardCharsets.UTF_8;
    private static final String DIGITS_STR = IntStream.rangeClosed(0, 65535)
            .filter(Character::isDigit)
            .mapToObj(Character::toString)
            .collect(Collectors.joining());
    private static final String NON_ZERO_DIGITS_STR = IntStream.rangeClosed(0, 65535)
            .filter(Character::isDigit)
            .filter(c -> Character.getNumericValue(c) != 0)
            .mapToObj(Character::toString)
            .collect(Collectors.joining());

    private static final Pattern DIGIT = Pattern.compile("([" + DIGITS_STR + "])");
    private static final Pattern NON_DIGIT = Pattern.compile("([^" + DIGITS_STR + "])");
    private static final Pattern NON_ZERO_DIGIT = Pattern.compile("([" + NON_ZERO_DIGITS_STR + "])");
    private static final Pattern NUMBER = Pattern.compile("([" + DIGITS_STR + "]+)");
    private static final Pattern NON_NUMBER = Pattern.compile("[^\\p{IsDigit}]+");

    private static final List<String> ANSWER = List.of(
            "Hello, %s", "Привет, %s", "Bonjour %s", "Hola %s" // trimmed for brevity
    );

    private static final List<NumberFormat> FORMATS = List.copyOf(
            Arrays.stream(Locale.getAvailableLocales())
                    .map(NumberFormat::getNumberInstance)
                    .peek(f -> f.setGroupingUsed(false))
                    .collect(Collectors.toMap(f -> f.format(123L), Function.identity(), (a, b) -> a))
                    .values()
    );

    private static final List<BiFunction<String, Random, String>> EVIL_MODIFICATIONS = List.of(
            (s, r) -> s,
            (s, r) -> s,
            replaceAll(NON_DIGIT, s -> "_"),
            replaceAll(NON_DIGIT, s -> "-"),
            replaceAll(NON_DIGIT, s -> s + s),
            (s, r) -> NUMBER.matcher(s).replaceAll(m -> i18nReplace(select(ANSWER, r), m.group()))
    );

    private static Mode mode;

    private Util() {}

    public static String getString(DatagramPacket packet) {
        return getString(packet.getData(), packet.getOffset(), packet.getLength());
    }

    public static String getString(byte[] data, int offset, int length) {
        return new String(data, offset, length, CHARSET);
    }

    public static byte[] getBytes(String string) {
        return string.getBytes(CHARSET);
    }

    public static DatagramPacket createPacket(DatagramSocket socket) throws SocketException {
        return new DatagramPacket(new byte[socket.getReceiveBufferSize()], socket.getReceiveBufferSize());
    }

    public static void send(DatagramSocket socket, String request, SocketAddress address) throws IOException {
        DatagramPacket packet = new DatagramPacket(new byte[0], 0);
        packet.setSocketAddress(address);
        send(socket, packet, request);
    }

    private static void send(DatagramSocket socket, DatagramPacket packet, String request) throws IOException {
        byte[] bytes = getBytes(request);
        packet.setData(bytes);
        packet.setLength(bytes.length);
        socket.send(packet);
    }

    public static String receive(DatagramSocket socket) throws IOException {
        DatagramPacket packet = createPacket(socket);
        socket.receive(packet);
        return getString(packet);
    }public static String request(String string, DatagramSocket socket, SocketAddress address) throws IOException {
        send(socket, string, address);
        return receive(socket);
    }

    public static String response(String request) {
        return response(request, "Hello, $");
    }

    public static String response(String request, String format) {
        return format.replace("$", request);
    }

    public static void server(List<String> templates, int threads, double p, DatagramSocket socket) throws IOException {
        int[] expected = new int[threads];
        Random random = new Random(4702347503224875082L + Objects.hash(templates, threads, p));

        try {
            while (true) {
                DatagramPacket packet = createPacket(socket);
                socket.receive(packet);
                String request = getString(packet);
                String[] parts = NON_NUMBER.split(request);
                String message = "Invalid or unexpected request: " + request;

                Assertions.assertTrue(parts.length > 1, message);

                try {
                    int thread = Integer.parseInt(parts[parts.length - 1]) - 1;
                    Assertions.assertTrue(0 <= thread && thread < expected.length, message);

                    int no = expected[thread];
                    Assertions.assertTrue(no < templates.size(), message);
                    Assertions.assertEquals(expected(templates, expected, thread), request, message);

                    String response = mode.apply(request, random);
                    if (no != 0 && !(p >= random.nextDouble()) && random.nextBoolean()) {
                        String corrupt = mode.corrupt(response, random);
                        if (!corrupt.equals(response)) {
                            send(socket, packet, corrupt);
                        }
                    } else {
                        expected[thread]++;
                        send(socket, packet, response);
                    }
                } catch (NumberFormatException e) {
                    throw new AssertionError(message);
                }
            }
        } catch (IOException e) {
            if (socket.isClosed()) {
                IntStream.range(0, threads).forEach(i ->
                        Assertions.assertEquals(templates.size(), expected[i],
                                () -> "Invalid number of requests on thread %d, last: %s".formatted(i, expected(templates, expected, i))));
            } else {
                throw e;
            }
        }
    }

    private static String expected(List<String> templates, int[] no, int thread) {
        return templates.get(no[thread]).replace("$", String.valueOf(thread + 1));
    }

    private static <T> T select(List<T> items, Random random) {
        return items.get(random.nextInt(items.size()));
    }

    private static String i18n(String request, NumberFormat format) {
        return response(NUMBER.matcher(request).replaceAll(match -> format.format(Long.parseLong(match.group()))));
    }

    private static String i18nReplace(String format, String number) {
        NumberFormat nf = select(FORMATS, new Random());
        return response(format.replace("%s", nf.format(Long.parseLong(number))));
    }

    public static void setMode(String test) {
        mode = test.endsWith("-i18n") ? Mode.I18N : (test.endsWith("-evil") ? Mode.EVIL : Mode.NORMAL);
    }public enum Mode {
        NORMAL(
                (request, random) -> Util.response(request),
                List.of(
                        (s, r) -> "",
                        (s, r) -> "~",
                        replaceAll(Pattern.compile("[_\\-]"), x -> "1"),
                        replaceAll(NUMBER, x -> "0"),
                        replaceOne(NUMBER, x -> "0"),
                        replaceOne(NON_ZERO_DIGIT, x -> "0"),
                        replaceAll(DIGIT, x -> x + x),
                        replaceOne(DIGIT, x -> "-"),
                        replaceOne(DIGIT, x -> "")
                )
        ),
        I18N(
                (request, random) -> random.nextInt(3) == 0 ? NORMAL.apply(request, random) : i18n(request, select(FORMATS, random)),
                NORMAL.corruptions
        ),
        EVIL(
                (request, random) -> I18N.apply(select(EVIL_MODIFICATIONS, random).apply(request, random), random),
                Stream.concat(NORMAL.corruptions.stream(), Stream.of(
                        replaceOne(NUMBER, x -> x + x),
                        replaceOne(NUMBER, x -> x + x.charAt(0)),
                        replaceOne(NUMBER, x -> x.charAt(0) + x)
                )).toList()
        );

        private final BiFunction<String, Random, String> f;
        private final List<BiFunction<String, Random, String>> corruptions;

        Mode(BiFunction<String, Random, String> f, List<BiFunction<String, Random, String>> corruptions) {
            this.f = f;
            this.corruptions = corruptions;
        }

        public String apply(String request, Random random) {
            return f.apply(request, random);
        }

        public String corrupt(String request, Random random) {
            return select(corruptions, random).apply(request, random);
        }
    }

    private static BiFunction<String, Random, String> replaceOne(Pattern pattern, Function<String, String> f) {
        return (s, r) -> {
            List<MatchResult> results = pattern.matcher(s).results().toList();
            if (results.isEmpty()) return s;
            MatchResult result = select(results, r);
            return s.substring(0, result.start()) + f.apply(result.group()) + s.substring(result.end());
        };
    }

    private static BiFunction<String, Random, String> replaceAll(Pattern pattern, Function<String, String> f) {
        return (s, r) -> pattern.matcher(s).replaceAll(mr -> f.apply(mr.group()));
    }
}