/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.db.importer.provider;

import co.rsk.db.importer.BootstrapImportException;
import co.rsk.db.importer.BootstrapURLProvider;
import co.rsk.db.importer.provider.index.data.BootstrapDataEntry;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.HashUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BootstrapFileHandlerTest {

    @TempDir
    Path tempDir;

    private BootstrapFileHandler createHandlerWithTempPath(BootstrapURLProvider urlProvider, Unzipper unzipper, Path tempPath) throws Exception {
        BootstrapFileHandler handler = new BootstrapFileHandler(urlProvider, unzipper);
        Field field = BootstrapFileHandler.class.getDeclaredField("tempPath");
        field.setAccessible(true);
        field.set(handler, tempPath);
        return handler;
    }

    @Test
    void setTempDirectory_createsTempDirSuccessfully() {
        BootstrapFileHandler handler = new BootstrapFileHandler(mock(BootstrapURLProvider.class), mock(Unzipper.class));
        assertDoesNotThrow(handler::setTempDirectory);
    }

    @Test
    void getBootstrapData_returnsFileBytes() throws Exception {
        byte[] expectedData = "bootstrap bin content".getBytes();
        Files.write(tempDir.resolve("bootstrap-data.bin"), expectedData);

        BootstrapFileHandler handler = createHandlerWithTempPath(mock(BootstrapURLProvider.class), mock(Unzipper.class), tempDir);

        assertArrayEquals(expectedData, handler.getBootstrapData());
    }

    @Test
    void getBootstrapData_throwsWhenFileIsMissing() throws Exception {
        BootstrapFileHandler handler = createHandlerWithTempPath(mock(BootstrapURLProvider.class), mock(Unzipper.class), tempDir);
        // bootstrap-data.bin does not exist in tempDir
        assertThrows(BootstrapImportException.class, handler::getBootstrapData);
    }

    @Test
    void retrieveAndUnpack_downloadsChecksHashAndUnzips() throws Exception {
        byte[] fileContent = "fake zip bytes".getBytes();
        Path sourceFile = tempDir.resolve("source.zip");
        Files.write(sourceFile, fileContent);
        String expectedHash = Hex.toHexString(HashUtil.sha256(fileContent));

        URL fileUrl = sourceFile.toUri().toURL();
        BootstrapURLProvider urlProvider = mock(BootstrapURLProvider.class);
        when(urlProvider.getFullURL(any())).thenReturn(fileUrl);

        Unzipper unzipper = mock(Unzipper.class);

        Path downloadDir = tempDir.resolve("download");
        Files.createDirectories(downloadDir);
        BootstrapFileHandler handler = createHandlerWithTempPath(urlProvider, unzipper, downloadDir);

        BootstrapDataEntry entry = new BootstrapDataEntry(1L, "2024-01-01", "path/to/db", expectedHash, null);
        Map<String, BootstrapDataEntry> entries = new HashMap<>();
        entries.put("pubkey1", entry);

        assertDoesNotThrow(() -> handler.retrieveAndUnpack(entries));
        verify(unzipper).unzip(any(InputStream.class), eq(downloadDir));
    }

    @Test
    void retrieveAndUnpack_throwsOnDownloadFailure() throws Exception {
        URL badUrl = new URL("file:///nonexistent/path/to/file.zip");
        BootstrapURLProvider urlProvider = mock(BootstrapURLProvider.class);
        when(urlProvider.getFullURL(any())).thenReturn(badUrl);

        BootstrapFileHandler handler = createHandlerWithTempPath(urlProvider, mock(Unzipper.class), tempDir);

        BootstrapDataEntry entry = new BootstrapDataEntry(1L, "2024-01-01", "path/to/db", "deadbeef", null);
        Map<String, BootstrapDataEntry> entries = new HashMap<>();
        entries.put("pubkey1", entry);

        assertThrows(BootstrapImportException.class, () -> handler.retrieveAndUnpack(entries));
    }

    @Test
    void retrieveAndUnpack_throwsOnHashMismatch() throws Exception {
        byte[] fileContent = "fake zip bytes".getBytes();
        Path sourceFile = tempDir.resolve("source2.zip");
        Files.write(sourceFile, fileContent);

        URL fileUrl = sourceFile.toUri().toURL();
        BootstrapURLProvider urlProvider = mock(BootstrapURLProvider.class);
        when(urlProvider.getFullURL(any())).thenReturn(fileUrl);

        Path downloadDir = tempDir.resolve("download2");
        Files.createDirectories(downloadDir);
        BootstrapFileHandler handler = createHandlerWithTempPath(urlProvider, mock(Unzipper.class), downloadDir);

        String wrongHash = Hex.toHexString(HashUtil.sha256("completely different content".getBytes()));
        BootstrapDataEntry entry = new BootstrapDataEntry(1L, "2024-01-01", "path/to/db", wrongHash, null);
        Map<String, BootstrapDataEntry> entries = new HashMap<>();
        entries.put("pubkey1", entry);

        assertThrows(BootstrapImportException.class, () -> handler.retrieveAndUnpack(entries));
    }

    /**
     * The hash check digests the download incrementally instead of reading it into one {@code byte[]},
     * so it has to keep working across buffer boundaries. The other hash tests use files far smaller
     * than the 64 KiB read buffer and would pass even if the loop only ever digested its first chunk,
     * which would turn the integrity check on a real (multi-gigabyte) bootstrap into a check over its
     * first 64 KiB.
     */
    @Test
    void retrieveAndUnpack_checksHashOfContentSpanningSeveralReadBuffers() throws Exception {
        byte[] fileContent = contentSpanningSeveralReadBuffers();
        Path sourceFile = tempDir.resolve("source-large.zip");
        Files.write(sourceFile, fileContent);
        String expectedHash = Hex.toHexString(HashUtil.sha256(fileContent));

        URL fileUrl = sourceFile.toUri().toURL();
        BootstrapURLProvider urlProvider = mock(BootstrapURLProvider.class);
        when(urlProvider.getFullURL(any())).thenReturn(fileUrl);

        Unzipper unzipper = mock(Unzipper.class);

        Path downloadDir = tempDir.resolve("download-large");
        Files.createDirectories(downloadDir);
        BootstrapFileHandler handler = createHandlerWithTempPath(urlProvider, unzipper, downloadDir);

        BootstrapDataEntry entry = new BootstrapDataEntry(1L, "2024-01-01", "path/to/db", expectedHash, null);
        Map<String, BootstrapDataEntry> entries = new HashMap<>();
        entries.put("pubkey1", entry);

        assertDoesNotThrow(() -> handler.retrieveAndUnpack(entries));
        verify(unzipper).unzip(any(InputStream.class), eq(downloadDir));
    }

    @Test
    void retrieveAndUnpack_throwsWhenAByteChangesPastTheFirstReadBuffer() throws Exception {
        byte[] fileContent = contentSpanningSeveralReadBuffers();
        Path sourceFile = tempDir.resolve("source-tampered.zip");
        Files.write(sourceFile, fileContent);

        // the hash of the same content with a single byte flipped well past the first buffer: only a
        // digest that covers the whole file can tell the two apart
        byte[] tamperedContent = fileContent.clone();
        tamperedContent[tamperedContent.length - 1] ^= 0x01;
        String hashOfTamperedContent = Hex.toHexString(HashUtil.sha256(tamperedContent));

        URL fileUrl = sourceFile.toUri().toURL();
        BootstrapURLProvider urlProvider = mock(BootstrapURLProvider.class);
        when(urlProvider.getFullURL(any())).thenReturn(fileUrl);

        Path downloadDir = tempDir.resolve("download-tampered");
        Files.createDirectories(downloadDir);
        BootstrapFileHandler handler = createHandlerWithTempPath(urlProvider, mock(Unzipper.class), downloadDir);

        BootstrapDataEntry entry = new BootstrapDataEntry(1L, "2024-01-01", "path/to/db", hashOfTamperedContent, null);
        Map<String, BootstrapDataEntry> entries = new HashMap<>();
        entries.put("pubkey1", entry);

        assertThrows(BootstrapImportException.class, () -> handler.retrieveAndUnpack(entries));
    }

    @Test
    void getBootstrapDataPath_pointsAtTheExtractedBinFile() throws Exception {
        // the v2 importer streams this path instead of calling getBootstrapData(), which cannot hold
        // a file larger than Integer.MAX_VALUE in a single array
        BootstrapFileHandler handler = createHandlerWithTempPath(mock(BootstrapURLProvider.class), mock(Unzipper.class), tempDir);

        assertEquals(tempDir.resolve("bootstrap-data.bin"), handler.getBootstrapDataPath());
    }

    /**
     * Content a few bytes over three times the handler's 64 KiB read buffer, so the digest loop runs
     * several full reads plus a short final one.
     */
    private static byte[] contentSpanningSeveralReadBuffers() {
        byte[] content = new byte[3 * 64 * 1024 + 17];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) i;
        }
        return content;
    }

    @Test
    void retrieveAndUnpack_throwsOnUnzipFailure() throws Exception {
        byte[] fileContent = "fake zip bytes".getBytes();
        Path sourceFile = tempDir.resolve("source3.zip");
        Files.write(sourceFile, fileContent);
        String expectedHash = Hex.toHexString(HashUtil.sha256(fileContent));

        URL fileUrl = sourceFile.toUri().toURL();
        BootstrapURLProvider urlProvider = mock(BootstrapURLProvider.class);
        when(urlProvider.getFullURL(any())).thenReturn(fileUrl);

        Unzipper unzipper = mock(Unzipper.class);
        doThrow(new IOException("unzip failed")).when(unzipper).unzip(any(), any());

        Path downloadDir = tempDir.resolve("download3");
        Files.createDirectories(downloadDir);
        BootstrapFileHandler handler = createHandlerWithTempPath(urlProvider, unzipper, downloadDir);

        BootstrapDataEntry entry = new BootstrapDataEntry(1L, "2024-01-01", "path/to/db", expectedHash, null);
        Map<String, BootstrapDataEntry> entries = new HashMap<>();
        entries.put("pubkey1", entry);

        assertThrows(BootstrapImportException.class, () -> handler.retrieveAndUnpack(entries));
    }
}
