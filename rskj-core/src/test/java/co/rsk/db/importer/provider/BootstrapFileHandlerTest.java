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
