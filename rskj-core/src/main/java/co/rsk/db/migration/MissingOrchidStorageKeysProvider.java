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

package co.rsk.db.migration;

import co.rsk.crypto.Keccak256;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.vm.DataWord;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;

public class MissingOrchidStorageKeysProvider {

    public static final String MAPDB_FILENAME = "migration-extras";

    private static final Logger logger = LoggerFactory.getLogger(MissingOrchidStorageKeysProvider.class);

    private final URL missingStorageKeysRemoteUrl;
    private final File databaseLocalFile;

    private Map<byte[], byte[]> patchDatabase;

    public MissingOrchidStorageKeysProvider(String databaseDir, URL missingStorageKeysRemoteUrl) {
        Path databasePath = Paths.get(databaseDir);
        this.missingStorageKeysRemoteUrl = missingStorageKeysRemoteUrl;
        this.databaseLocalFile = databasePath.resolve(MAPDB_FILENAME).toFile();
    }

    public DataWord getKeccak256PreImage(Keccak256 storageKeyHash) {
        if (patchDatabase == null) {
            if (!databaseLocalFile.exists()) {
                try (ReadableByteChannel patchDatabaseStream = Channels.newChannel(missingStorageKeysRemoteUrl.openStream());
                     FileOutputStream fileOutputStream = new FileOutputStream(databaseLocalFile)) {
                    fileOutputStream.getChannel().transferFrom(patchDatabaseStream, 0, Long.MAX_VALUE);
                    fileOutputStream.flush();
                    logger.info("Missing mapping keys downloaded");
                } catch (IOException e) {
                    throw new RuntimeException(String.format(
                            "We have detected an inconsistency in your database and are unable to migrate it automatically.\n" +
                            "We also tried to download the file with patching information but were also unable.\n" +
                            "Please download %s and save it to %s to continue.\n", missingStorageKeysRemoteUrl, databaseLocalFile
                    ), e);
                }
            }
            DB indexDB = DBMaker.fileDB(databaseLocalFile).closeOnJvmShutdown().make();
            patchDatabase = indexDB.hashMapCreate("preimages")
                    .keySerializer(Serializer.BYTE_ARRAY)
                    .valueSerializer(Serializer.BYTE_ARRAY)
                    .makeOrGet();
        }

        byte[] storageKey = patchDatabase.get(storageKeyHash.getBytes());
        if (storageKey == null) {
            throw new IllegalStateException(String.format(
                    "We have detected a recoverable inconsistency in your database during the migration process.\n" +
                    "The patching information required to do this is not available at the moment, " +
                    "but it's automatically updated every hour.\n" +
                    "Please try again in a few minutes. " +
                    "If you have any question please reach through our support channel at http://gitter.im/rsksmart/rskj\n" +
                    "Missing storage key: %s",
                    storageKeyHash.toHexString()
                ));
        }
        if (!Arrays.equals(storageKeyHash.getBytes(), Keccak256Helper.keccak256(DataWord.valueOf(storageKey).getData()))) {
            throw new IllegalStateException(
                    String.format("You have downloaded an inconsistent database. %s doesn't match expected keccak256 hash (%s)",
                            Hex.toHexString(storageKey),
                            Hex.toHexString(storageKeyHash.getBytes())
                    ));
        }
        return DataWord.valueOf(storageKey);
    }
}
