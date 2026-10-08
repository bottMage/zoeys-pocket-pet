package com.example.shortsgesturecontrol;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Regression checks using the production checker and a local HTTP fixture. */
public final class UpdateCheckerCheck {
    private static UpdateChecker.Release release(int version) throws IOException {
        return new UpdateChecker.Release(version, UpdateChecker.assetUrl(version));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void fails(UpdateChecker.Source operation) throws Exception {
        try {
            operation.load();
        } catch (IOException expected) {
            return;
        }
        throw new AssertionError("Failed metadata must not become 'up to date'");
    }

    public static void main(String[] args) throws Exception {
        UpdateChecker.Source offline = () -> { throw new IOException("offline"); };
        require(UpdateChecker.check(38, () -> release(38), () -> release(39)).version == 39,
                "Stale manifest hides the published release");
        require(UpdateChecker.check(38, () -> release(37), () -> release(39)).version == 39,
                "Very stale manifest hides the published release");
        require(UpdateChecker.check(38, () -> release(39), offline).version == 39,
                "New valid manifest must work even if the second source is unavailable");
        require(UpdateChecker.check(38, offline, () -> release(39)).version == 39,
                "Unavailable raw host must fall back to public releases");
        require(UpdateChecker.check(39, () -> release(38), () -> release(39)).version == 39,
                "Published latest version should confirm up-to-date");
        require(UpdateChecker.check(39, () -> release(39), () -> release(38)).version == 39,
                "Never downgrade a newer valid manifest");
        fails(() -> UpdateChecker.check(38, offline, offline));
        fails(() -> UpdateChecker.check(38, () -> release(38), offline));
        fails(() -> new UpdateChecker.Release(0, ""));
        fails(() -> new UpdateChecker.Release(39, ""));
        fails(() -> new UpdateChecker.Release(39, UpdateChecker.assetUrl(38)));
        fails(() -> new UpdateChecker.Release(39, "https://other.example/update.apk"));
        require(UpdateChecker.check(38, () -> new UpdateChecker.Release(0, ""), () -> release(39)).version == 39,
                "Malformed primary metadata must fall back, not report up-to-date");

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        Set<String> queries = new HashSet<>();
        AtomicInteger freshRequests = new AtomicInteger();
        server.createContext("/metadata", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            boolean fresh = query != null && query.contains("update_check=")
                    && queries.add(query)
                    && "no-cache, no-store, max-age=0".equals(exchange.getRequestHeaders().getFirst("Cache-Control"))
                    && "no-cache".equals(exchange.getRequestHeaders().getFirst("Pragma"));
            if (fresh) freshRequests.incrementAndGet();
            byte[] body = (fresh ? "fresh" : "stale").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/unavailable", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.createContext("/oversized", exchange -> {
            byte[] body = new byte[65537];
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/latest", exchange -> {
            exchange.getResponseHeaders().add("Location", "/tag/v39");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/tag/v39", exchange -> {
            require("HEAD".equals(exchange.getRequestMethod()), "Release check downloaded a page");
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        try {
            require("fresh".equals(UpdateChecker.readFreshJson(base + "/metadata")), "First check is stale");
            require("fresh".equals(UpdateChecker.readFreshJson(base + "/metadata")), "Repeat check reused cache key");
            require("fresh".equals(UpdateChecker.readFreshJson(base + "/metadata?existing=1")), "Existing query lost");
            require(freshRequests.get() == 3, "Not all requests bypassed the cache");
            require(UpdateChecker.freshHead(base + "/latest").equals(base + "/tag/v39"), "Latest release redirect not followed");
            fails(() -> { UpdateChecker.readFreshJson(base + "/unavailable"); return release(38); });
            fails(() -> { UpdateChecker.readFreshJson(base + "/oversized"); return release(38); });
        } finally {
            server.stop(0);
        }
        System.out.println("PASS: fresh repeated requests, stale manifest recovery, source outages, invalid metadata, redirects and HTTP failures");
    }
}
