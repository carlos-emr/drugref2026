/*
 * Copyright (c) 2026 CARLOS EMR Project Contributors. All Rights Reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package io.github.carlos_emr.drugref2026.ca.dpd.fetch;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.drugref2026.util.MiscUtils;

/**
 * Fetches the three Health Canada Drug Product Database archives an update needs,
 * and proves each one is a readable ZIP, <em>before</em> the importer touches a
 * single table.
 *
 * <p>The previous pipeline dropped every DPD table first and then opened each URL
 * with a bare {@code URL.openStream()}: no timeout, no status check, and the
 * {@link IOException} swallowed by a {@code printStackTrace()}. A server that could
 * not reach {@code www.canada.ca} (egress firewall, proxy, TLS interception, a
 * moved file) was therefore left with an empty drug database and a search that
 * answered "None found" until someone reloaded the seed by hand. Downloading
 * everything up front turns every one of those failures into a clean abort with
 * nothing lost.
 *
 * <p>Outbound HTTPS is the JVM's own: an installation behind a proxy configures
 * it with the standard {@code https.proxyHost}/{@code https.proxyPort} system
 * properties, and a TLS-intercepting proxy needs its CA in the JVM trust store.
 */
public final class DpdDownloader {

    /** The three archives, in the order the importer consumes them. */
    public static final String ACTIVE_ARCHIVE = "allfiles.zip";
    /**
     * Health Canada's 2018 cut of the inactive-product detail tables. The
     * current {@code allfiles_ia.zip} carries a different layout the parser
     * does not yet understand, so the importer still reads this legacy file.
     */
    public static final String INACTIVE_DETAIL_ARCHIVE = "Allfiles_ia-Oct10.zip";
    public static final String INACTIVE_TABLE_ARCHIVE = "inactive.zip";

    /** Sent so the download is attributable in Health Canada's logs and not refused as a bare Java client. */
    static final String USER_AGENT = "CARLOS-DrugRef/1.0 (+https://github.com/carlos-emr/drugref2026)";

    private static final int CONNECT_TIMEOUT_MS = 30_000;
    /** Per-read stall limit, not a total: the largest archive is tens of megabytes on a slow clinic link. */
    private static final int READ_TIMEOUT_MS = 300_000;

    private static final Logger logger = MiscUtils.getLogger();

    /**
     * The downloaded archives as local temp files. {@link #deleteAll()} removes
     * them once the import is over, whatever its outcome.
     */
    public static final class DpdArchives {
        private final File active;
        private final File inactiveDetail;
        private final File inactiveTable;

        DpdArchives(File active, File inactiveDetail, File inactiveTable) {
            this.active = active;
            this.inactiveDetail = inactiveDetail;
            this.inactiveTable = inactiveTable;
        }

        public File getActive() {
            return active;
        }

        public File getInactiveDetail() {
            return inactiveDetail;
        }

        public File getInactiveTable() {
            return inactiveTable;
        }

        /** Removes the temp files; safe to call more than once. */
        public void deleteAll() {
            for (File f : new File[] {active, inactiveDetail, inactiveTable}) {
                if (f != null && f.exists() && !f.delete()) {
                    logger.warn("could not delete temporary DPD archive " + f);
                }
            }
        }
    }

    private DpdDownloader() {
    }

    /**
     * Downloads and validates all three archives from {@code baseUrl}.
     *
     * @param baseUrl the directory URL the archive names are appended to
     *                (the {@code DPD_BASE_URL} property)
     * @return the local archives; the caller owns them and must {@link DpdArchives#deleteAll()}
     * @throws IOException if any archive cannot be fetched or is not a ZIP. Archives
     *                     already fetched are deleted before the exception is thrown,
     *                     so a failure leaves nothing behind.
     */
    public static DpdArchives download(String baseUrl) throws IOException {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        List<File> fetched = new ArrayList<>();
        try {
            for (String name : new String[] {ACTIVE_ARCHIVE, INACTIVE_DETAIL_ARCHIVE, INACTIVE_TABLE_ARCHIVE}) {
                fetched.add(fetch(base + "/" + name));
            }
        } catch (IOException | RuntimeException e) {
            for (File f : fetched) {
                if (!f.delete()) {
                    logger.warn("could not delete temporary DPD archive " + f);
                }
            }
            throw e;
        }
        return new DpdArchives(fetched.get(0), fetched.get(1), fetched.get(2));
    }

    /**
     * Directory for the downloaded archives, or {@code null} for the JVM's temp directory,
     * which is what production uses.
     *
     * <p>A test seam, and it has to be one: {@link File#createTempFile(String, String)} resolves
     * {@code java.io.tmpdir} once at JVM start, so a test cannot redirect it by setting the
     * property. Without this, the only way to check that a failed download leaves nothing
     * behind is to diff the shared temp directory, which any concurrent build writing a
     * {@code dpd-*.zip} turns into a spurious failure.
     */
    static File tempDir = null;

    /**
     * Fetches one URL to a temp file and verifies the result opens as a ZIP with
     * at least one entry.
     *
     * @throws IOException on a connection failure, a non-200 status, a truncated
     *                     transfer, or a body that is not a ZIP archive (typically an
     *                     HTML error or captive-portal page served with status 200)
     */
    static File fetch(String url) throws IOException {
        logger.info("DrugRef update: downloading " + url);
        File out = File.createTempFile("dpd-", ".zip", tempDir);
        try {
            HttpURLConnection connection = openConnection(url);
            long expected;
            long copied;
            try {
                int status = connection.getResponseCode();
                if (status != HttpURLConnection.HTTP_OK) {
                    throw new IOException("HTTP " + status + " fetching " + url);
                }
                expected = connection.getContentLengthLong();
                try (InputStream in = connection.getInputStream();
                     FileOutputStream fos = new FileOutputStream(out)) {
                    copied = IOUtils.copyLarge(in, fos);
                }
            } finally {
                // Release the socket on every path. An update that fails here is retried
                // by an operator, and a non-200 left the connection alive until GC.
                connection.disconnect();
            }
            if (expected >= 0 && copied != expected) {
                throw new IOException("truncated download of " + url + ": got " + copied
                        + " of " + expected + " bytes");
            }
            validateZip(out, url);
            logger.info("DrugRef update: downloaded " + url + " (" + copied + " bytes)");
            return out;
        } catch (IOException | RuntimeException e) {
            if (!out.delete()) {
                logger.warn("could not delete temporary DPD archive " + out);
            }
            throw e;
        }
    }

    private static HttpURLConnection openConnection(String url) throws IOException {
        URL target;
        try {
            target = URI.create(url).toURL();
        } catch (IllegalArgumentException e) {
            throw new IOException("invalid DPD URL " + url + ": " + e.getMessage(), e);
        }
        HttpURLConnection connection = (HttpURLConnection) target.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "application/zip, application/octet-stream;q=0.9, */*;q=0.1");
        return connection;
    }

    /**
     * Rejects anything that is not a non-empty ZIP. A 200 response that is really
     * an HTML page (proxy block page, maintenance notice) would otherwise reach the
     * parser as a corrupt archive after the tables were already rebuilt.
     */
    static void validateZip(File file, String source) throws IOException {
        if (file.length() == 0) {
            throw new IOException("empty download from " + source);
        }
        try (ZipFile zip = new ZipFile(file)) {
            if (zip.size() == 0) {
                throw new IOException("ZIP archive from " + source + " contains no entries");
            }
        } catch (java.util.zip.ZipException e) {
            throw new IOException("download from " + source + " is not a ZIP archive: " + e.getMessage(), e);
        }
    }
}
