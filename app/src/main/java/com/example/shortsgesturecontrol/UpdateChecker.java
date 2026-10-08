package com.example.shortsgesturecontrol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Fresh metadata requests and update decisions, independent of Android UI. */
public final class UpdateChecker {
    public static final String REPOSITORY = "https://github.com/bottMage/zoeys-pocket-pet";
    public static final String MANIFEST = "https://raw.githubusercontent.com/bottMage/zoeys-pocket-pet/main/update.json";

    public interface Source {
        Release load() throws Exception;
    }

    public static final class Release {
        public final int version;
        public final String apkUrl;

        public Release(int version, String apkUrl) throws IOException {
            if (version <= 0 || !assetUrl(version).equals(apkUrl)) {
                throw new IOException("Incomplete or unexpected update metadata");
            }
            this.version = version;
            this.apkUrl = apkUrl;
        }
    }

    public static String assetUrl(int version) {
        return REPOSITORY + "/releases/download/v" + version + "/zoeys-pocket-pet-v" + version + ".apk";
    }

    /**
     * The same signed asset committed to main. This avoids the separate
     * release-assets host when a device's DownloadManager rejects GitHub's
     * cross-host redirect.
     */
    public static String rawAssetUrl(int version) {
        return "https://raw.githubusercontent.com/bottMage/zoeys-pocket-pet/main/updates/zoeys-pocket-pet-v"
                + version + ".apk";
    }

    public static Release check(long installed, Source manifest, Source published) throws Exception {
        Release primary = null;
        try {
            primary = manifest.load();
        } catch (Exception ignored) {
            // A valid published release is still usable when raw GitHub is unavailable.
        }
        if (primary != null && primary.version > installed) return primary;

        // Never declare "up to date" based only on a potentially stale raw manifest.
        // Also detects the gap between publishing an APK and advertising it in main.
        Release latest = published.load();
        if (latest == null) throw new IOException("No published release metadata");
        return primary != null && primary.version > latest.version ? primary : latest;
    }

    public static Release publishedRelease(long installed) throws IOException {
        String resolved = freshHead(REPOSITORY + "/releases/latest");
        String prefix = REPOSITORY + "/releases/tag/v";
        if (!resolved.startsWith(prefix)) throw new IOException("Unexpected latest release URL");
        int version;
        try {
            version = Integer.parseInt(resolved.substring(prefix.length()));
        } catch (NumberFormatException error) {
            throw new IOException("Invalid release version", error);
        }
        Release release = new Release(version, assetUrl(version));
        if (version > installed) freshHead(release.apkUrl); // Do not offer an unpublished APK.
        return release;
    }

    public static String readFreshJson(String url) throws IOException {
        HttpURLConnection connection = openFresh(url, "GET", "application/json");
        try {
            requireSuccess(connection);
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > 65536) throw new IOException("Update metadata too large");
                    output.write(buffer, 0, count);
                }
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }

    static String freshHead(String url) throws IOException {
        HttpURLConnection connection = openFresh(url, "HEAD", "*/*");
        try {
            requireSuccess(connection);
            return connection.getURL().toString();
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection openFresh(String url, String method, String accept) throws IOException {
        // Headers alone do not bypass all CDN caches. A unique query also changes the cache key.
        String freshUrl = url + (url.contains("?") ? "&" : "?") + "update_check=" + UUID.randomUUID();
        HttpURLConnection connection = (HttpURLConnection) new URL(freshUrl).openConnection();
        connection.setUseCaches(false);
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(12000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("User-Agent", "ZoeysPocketPet-Updater");
        connection.setRequestProperty("Accept", accept);
        connection.setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0");
        connection.setRequestProperty("Pragma", "no-cache");
        return connection;
    }

    private static void requireSuccess(HttpURLConnection connection) throws IOException {
        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new IOException("Update request failed: HTTP " + connection.getResponseCode());
        }
    }

    private UpdateChecker() { }
}
