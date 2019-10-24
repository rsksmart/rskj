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

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Unzipper {

    /**
     *
     * @param sourceZipStream this parameter receives a stream of bytes with a zip file compression.
     *                        This could be used to process a file or other kinds of stream
     * @param destinationPath The path where the contents of the zip will be saved
     * @throws IOException
     */
    public void unzip(InputStream sourceZipStream, Path destinationPath) throws IOException {
        byte[] buffer = new byte[1024];
        try (ZipInputStream zis = new ZipInputStream(sourceZipStream)) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                Path destFile = destinationPath.resolve(zipEntry.getName());
                if (!destFile.normalize().startsWith(destinationPath) || zipEntry.getName().contains("..")) {
                    throw new IllegalArgumentException(String.format(
                            "The source contains an illegal entry: %s", zipEntry.getName()
                    ));
                }
                if (zipEntry.isDirectory()) {
                    Files.createDirectory(destFile);
                } else {
                    try (OutputStream fos = Files.newOutputStream(destFile)) {
                        for (int len = zis.read(buffer); len > 0; len = zis.read(buffer)) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
        }
    }
}