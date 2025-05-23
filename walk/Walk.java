package info.kgeorgiy.ja.gordienko.walk;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Walk {
    private static final int BUF_SIZE = 1024;
    private static final int HASH_LENGTH = 16;
    private static final int HASH_OUTPUT_LENGTH = 8;

    private static String sha256(Path filePath) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        StringBuilder sb = new StringBuilder("0".repeat(HASH_LENGTH));

        try (InputStream inputStream = Files.newInputStream(filePath)) {
            byte[] buf = new byte[BUF_SIZE];
            int c;
            while ((c = inputStream.read(buf)) != -1) {
                digest.update(buf, 0, c);
            }
            byte[] hash = digest.digest();
            sb.delete(0, HASH_LENGTH);
            for (int i = 0; i < HASH_OUTPUT_LENGTH; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
        } catch (IOException e) {
            System.err.printf("Error while reading file '%s':%n%s%n", filePath, e.getMessage());
        } catch (SecurityException e) {
            System.err.printf("Error: reading permission denied for file '%s':%n%s%n", filePath, e.getMessage());
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        if (args == null || args.length != 2 || args[0] == null || args[1] == null) {
            System.err.println("Expected two non-null arguments: <input_file> <output_file>");
            return;
        }

        Path inputFile;
        Path outputFile;

        try {
            inputFile = Path.of(args[0]);
        } catch (InvalidPathException e) {
            System.err.printf("Invalid input file path given: '%s'%n%s", args[0], e.getMessage());
            return;
        }

        try {
            outputFile = Path.of(args[1]);
        } catch (InvalidPathException e) {
            System.err.printf("Invalid output file path given: '%s'%n%s", args[1], e.getMessage());
            return;
        }

        Path parent = outputFile.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                System.err.printf("Failed to create directories for output file.%n%s", e.getMessage());
            }
        }

        try (BufferedReader input = Files.newBufferedReader(inputFile)) {
            try (BufferedWriter output = Files.newBufferedWriter(outputFile)) {
                String relPath;
                while ((relPath = input.readLine()) != null) {
                    try {
                        Path absPath = Path.of(relPath);
                        output.write(sha256(absPath) + " " + relPath);
                        output.newLine();
                    } catch (NoSuchAlgorithmException ignored) {
                        // ignore
                    } catch (InvalidPathException e) {
                        output.write("0".repeat(16) + " " + relPath);
                        output.newLine();
                    }
                }
            } catch (IOException e) {
                System.err.printf("Named file exists but is a directory rather than a regular file, " +
                        "does not exist but cannot be created, " +
                        "has special access settings, " +
                        "or cannot be opened for any other reason:%n%s", e.getMessage());
            }
        } catch (FileNotFoundException e) {
            System.err.printf("Error: named file does not exist, " +
                    "is a directory rather than a regular file, " +
                    "or for some other reason cannot be opened for reading:%n%s", e.getMessage());
        } catch (IOException e) {
            System.err.printf("I/O exception occurred:%n%s", e.getMessage());
        }
    }
}

