package info.kgeorgiy.ja.gordienko.crawler;

import info.kgeorgiy.java.advanced.crawler.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;
import java.util.concurrent.*;

public class WebCrawler implements Crawler, NewCrawler {
    private final Downloader downloader;
    private final ExecutorService downloadPool;
    private final ExecutorService extractPool;

    public WebCrawler(Downloader downloader, int downloaders, int extractors, int perHost) {
        this.downloader = downloader;
        this.downloadPool = Executors.newFixedThreadPool(downloaders);
        this.extractPool = Executors.newFixedThreadPool(extractors);
    }

    @Override
    public Result download(String url, int depth) {
        return download(url, depth, Collections.emptyList());
    }

    @Override
    public Result download(String url, int depth, List<String> excludes) {
        final Set<String> visited = ConcurrentHashMap.newKeySet();
        final Queue<String> downloaded = new ConcurrentLinkedQueue<>();
        final ConcurrentMap<String, IOException> errors = new ConcurrentHashMap<>();

        List<String> currLevelLinks = List.of(url);

        for (int lvl = 1; lvl <= depth; lvl++) {
            Phaser downloadPhaser = new Phaser(1);
            ConcurrentLinkedQueue<Document> docs = new ConcurrentLinkedQueue<>();
            ConcurrentLinkedQueue<String> nextLevelLinks = new ConcurrentLinkedQueue<>();

            for (String link : currLevelLinks) {
                if (!visited.add(link)
                        || hostContainsOneOfSubstrings(link, excludes)) continue;

                downloadPhaser.register();
                downloadPool.submit(() -> {
                    try {
                        Document doc = downloader.download(link);
                        downloaded.add(link);
                        docs.add(doc);
                    } catch (IOException e) {
                        errors.putIfAbsent(link, e);
                    } finally {
                        downloadPhaser.arriveAndDeregister();
                    }
                });
            }
            downloadPhaser.arriveAndAwaitAdvance();

            if (lvl == depth) break;

            Phaser extractPhaser = new Phaser(1);
            for (Document doc : docs) {
                extractPhaser.register();
                extractPool.submit(() -> {
                    try {
                        nextLevelLinks.addAll(doc.extractLinks());
                    } catch (IOException ignored) {
                    } finally {
                        extractPhaser.arriveAndDeregister();
                    }
                });
            }
            extractPhaser.arriveAndAwaitAdvance();
            currLevelLinks = new ArrayList<>(nextLevelLinks);
        }

        return new Result(new ArrayList<>(downloaded), errors);
    }

    @Override
    public void close() {
        downloadPool.close();
        extractPool.close();
    }


    private boolean hostContainsOneOfSubstrings(String url, List<String> substrings) {
        if (substrings.isEmpty()) return false;
        String host;
        try {
            host = URLUtils.getHost(url);
        } catch (MalformedURLException e) {
            return true;
        }
        return substrings.stream().anyMatch(host::contains);
    }

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 5) {
            System.err.println("Invalid arguments. Usage: WebCrawler url [depth [downloads [extractors [perHost]]]]");
            return;
        }

        if (Arrays.stream(args).anyMatch(Objects::isNull)) {
            System.err.println("Arguments should be non-null");
            return;
        }

        String url = args[0];

        try {
            URLUtils.getURI(url);
        } catch (MalformedURLException e) {
            System.err.printf("Given URL '%s' is invalid", url);
            return;
        }

        int depth = 1, downloaders = 1, extractors = 1, perHost = 1;
        try {
            if (args.length > 1) depth = Integer.parseInt(args[1]);
            if (args.length > 2) downloaders = Integer.parseInt(args[2]);
            if (args.length > 3) extractors = Integer.parseInt(args[3]);
            if (args.length > 4) perHost = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            System.err.println("All numeric parameters (depth, downloads, extractors, perHost) must be integers");
            return;
        }

        try (WebCrawler crawler = new WebCrawler(new CachingDownloader(0), downloaders, extractors, perHost)) {
            Result result = crawler.download(url, depth);

            System.out.println("Downloaded pages:");
            for (String u : result.getDownloaded()) {
                System.out.println(u);
            }

            System.out.println("Pages downloaded with errors:");
            for (Map.Entry<String, IOException> e : result.getErrors().entrySet()) {
                System.err.printf("%s %s%n", e.getKey(), e.getValue().getMessage());
            }
        } catch (IOException e) {
            System.err.println("I/O error occurred while downloading: " + e.getMessage());
        }
    }
}
