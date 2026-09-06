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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/**
 * Drives {@link DpdDownloader} against a loopback HTTP server: the three archives
 * must all arrive and open as ZIPs before the importer is allowed to touch the
 * database, and a failure must leave no temp files behind.
 */
class DpdDownloaderTest {

    private HttpServer server;
    private final Map<String, byte[]> files = new HashMap<>();
    private final Map<String, Integer> statuses = new HashMap<>();
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/dpd/", exchange -> {
            String name = exchange.getRequestURI().getPath().substring("/dpd/".length());
            byte[] body = files.get(name);
            int status = statuses.getOrDefault(name, body == null ? 404 : 200);
            if (body == null) {
                body = "<html>not found</html>".getBytes(StandardCharsets.UTF_8);
            }
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/dpd/";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void shouldFetchAllThreeArchives_whenServerServesValidZips() throws IOException {
        files.put(DpdDownloader.ACTIVE_ARCHIVE, zip("drug.txt"));
        files.put(DpdDownloader.INACTIVE_DETAIL_ARCHIVE, zip("drug_ia.txt"));
        files.put(DpdDownloader.INACTIVE_TABLE_ARCHIVE, zip("inactive.txt"));

        DpdDownloader.DpdArchives archives = DpdDownloader.download(baseUrl);
        try {
            assertThat(archives.getActive()).isFile();
            assertThat(archives.getInactiveDetail()).isFile();
            assertThat(archives.getInactiveTable()).isFile();
            assertThat(archives.getActive().length()).isEqualTo(files.get(DpdDownloader.ACTIVE_ARCHIVE).length);
        } finally {
            archives.deleteAll();
        }
        assertThat(archives.getActive()).doesNotExist();
        assertThat(archives.getInactiveDetail()).doesNotExist();
        assertThat(archives.getInactiveTable()).doesNotExist();
    }

    @Test
    void shouldDeleteTheArchivesItAlreadyFetched_whenALaterOneFails() throws IOException {
        // download()'s @throws promises "archives already fetched are deleted before the
        // exception is thrown, so a failure leaves nothing behind", and the class comment above
        // says a failure must leave no temp files behind. Nothing asserted it. A rebuild that
        // fails on the third archive would otherwise strand two multi-hundred-megabyte files in
        // the temp directory on every retry — on the appliance, that is the disk the EMR shares.
        files.put(DpdDownloader.ACTIVE_ARCHIVE, zip("drug.txt"));
        files.put(DpdDownloader.INACTIVE_DETAIL_ARCHIVE, zip("drug_ia.txt"));
        // INACTIVE_TABLE_ARCHIVE absent -> the third fetch 404s, after two have landed.
        Set<Path> before = tempArchives();

        assertThatThrownBy(() -> DpdDownloader.download(baseUrl)).isInstanceOf(IOException.class);

        Set<Path> leaked = tempArchives();
        leaked.removeAll(before);
        assertThat(leaked).as("temp archives left behind by a failed download").isEmpty();
    }

    /** The dpd-*.zip temp files currently on disk, so a test can diff its own leaks. */
    private static Set<Path> tempArchives() throws IOException {
        Path tmp = Paths.get(System.getProperty("java.io.tmpdir"));
        try (Stream<Path> files = Files.list(tmp)) {
            return files.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith("dpd-") && n.endsWith(".zip");
            }).collect(Collectors.toCollection(HashSet::new));
        }
    }

    @Test
    void shouldFailBeforeReturning_whenOneArchiveIsMissing() {
        files.put(DpdDownloader.ACTIVE_ARCHIVE, zip("drug.txt"));
        files.put(DpdDownloader.INACTIVE_TABLE_ARCHIVE, zip("inactive.txt"));
        // INACTIVE_DETAIL_ARCHIVE absent -> 404

        assertThatThrownBy(() -> DpdDownloader.download(baseUrl))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("HTTP 404")
                .hasMessageContaining(DpdDownloader.INACTIVE_DETAIL_ARCHIVE);
    }

    @Test
    void shouldRejectHtmlServedAsZip_withStatus200() {
        // A proxy block page or maintenance notice with a 200 status: the old code
        // handed this to the parser as a corrupt archive after dropping every table.
        files.put(DpdDownloader.ACTIVE_ARCHIVE, "<html><body>blocked</body></html>".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> DpdDownloader.fetch(baseUrl + DpdDownloader.ACTIVE_ARCHIVE))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not a ZIP archive");
    }

    @Test
    void shouldRejectEmptyBody_forValidStatus() {
        files.put(DpdDownloader.ACTIVE_ARCHIVE, new byte[0]);

        assertThatThrownBy(() -> DpdDownloader.fetch(baseUrl + DpdDownloader.ACTIVE_ARCHIVE))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("empty download");
    }

    @Test
    void shouldRejectZipWithoutEntries_forValidStatus() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ZipOutputStream(bytes).close();
        files.put(DpdDownloader.ACTIVE_ARCHIVE, bytes.toByteArray());

        assertThatThrownBy(() -> DpdDownloader.fetch(baseUrl + DpdDownloader.ACTIVE_ARCHIVE))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("no entries");
    }

    @Test
    void shouldRefuseUnreachableHost_withinTimeout() {
        // Port 1 on loopback: connection refused immediately, so this proves the
        // failure surfaces as an IOException rather than proving the timeout value.
        assertThatThrownBy(() -> DpdDownloader.fetch("http://127.0.0.1:1/allfiles.zip"))
                .isInstanceOf(IOException.class);
    }

    @Test
    void shouldValidateZipFile_forRealArchive() throws IOException {
        File f = File.createTempFile("dpd-test-", ".zip");
        try {
            java.nio.file.Files.write(f.toPath(), zip("drug.txt"));
            DpdDownloader.validateZip(f, "test");
        } finally {
            assertThat(f.delete()).isTrue();
        }
    }

    private static byte[] zip(String entryName) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
                zip.putNextEntry(new ZipEntry(entryName));
                zip.write("\"1\",\"\",\"Human\"\n".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
