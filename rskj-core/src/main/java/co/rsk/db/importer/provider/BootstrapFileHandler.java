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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.*;

public class BootstrapFileHandler {

    private static final Logger logger = LoggerFactory.getLogger(BootstrapFileHandler.class);
    private static final String BOOTSTRAP_DATA_NAME = "bootstrap-data.zip";
    private static final String EXTRACTED_PATH = "bootstrap-data.bin";

    private final BootstrapURLProvider bootstrapUrlProvider;
    private final Unzipper unzipper;
    private Path tempPath;

    public BootstrapFileHandler(
            BootstrapURLProvider bootstrapUrlProvider,
            Unzipper unzipper) {
        this.bootstrapUrlProvider = bootstrapUrlProvider;
        this.unzipper = unzipper;
    }

    // for testing purposes these 3 methods are splitted into 3 public items
    public void setTempDirectory() {
        this.tempPath = getTempDirectory();
    }

    public byte[] getBootstrapData() {
        Path bootstrapPath = tempPath.resolve(EXTRACTED_PATH);
        try {
            return Files.readAllBytes(bootstrapPath);
        } catch (IOException e) {
            throw new BootstrapImportException(String.format(
                    "Failed reading data after decompression %s", bootstrapPath), e);

        }
    }

    public void retrieveAndUnpack(Map<String, BootstrapDataEntry> selectedEntries) {
        Path zipPath = tempPath.resolve(BOOTSTRAP_DATA_NAME);

        List<String> publicKeys = new ArrayList<>(selectedEntries.keySet());
        String publicKey = publicKeys.get(new SecureRandom().nextInt(publicKeys.size()));
        String expectedHash = selectedEntries.get(publicKey).getHash();

        String dbSuffix = selectedEntries.get(publicKey).getDb();
        URL dbURL = bootstrapUrlProvider.getFullURL(dbSuffix);

        downloadFile(zipPath, dbURL);
        checkFileHash(zipPath, expectedHash);
        unzipFile(zipPath, tempPath);
    }

    private void checkFileHash(Path databasePath, String expectedHash) {
        try {
            byte[] fileContent = Files.readAllBytes(databasePath);
            byte[] hash = HashUtil.sha256(fileContent);
            if (!Arrays.equals(hash, Hex.decode(expectedHash))) {
                throw new BootstrapImportException(String.format(
                        "File: %s does not match with expected hash: %s", databasePath, expectedHash));
            }
            logger.info("Bootstrap data hash checked");
        } catch (IOException e) {
            throw new BootstrapImportException(
                    "Error trying to read bootstrap data contents. Please start again the import process", e);
        }
    }

    private void unzipFile(Path sourcePath, Path destinationPath) {
        try (InputStream inputStream = Files.newInputStream(sourcePath)){
            unzipper.unzip(inputStream, destinationPath);
            logger.info("Bootstrap data extracted");
        } catch (IOException e) {
            throw new BootstrapImportException(
                    "The file is corrupted or incomplete. Please start again the import process", e);
        }
    }

    private Path getTempDirectory() {
        try {
            return Files.createTempDirectory("import");
        } catch (IOException e) {
            throw new BootstrapImportException(
                    "Failed to create a temporary directory. Please start again the import process", e);
        }
    }

    private void downloadFile(Path downloadPath, URL url) {
        try (InputStream is = url.openStream()) {
            Files.copy(is, downloadPath, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Bootstrap data downloaded");
        } catch (IOException e) {
            throw new BootstrapImportException(String.format(
                    "Error downloading bootstrap data from %s. Please start again the import process", url
            ), e);
        }
    }
}