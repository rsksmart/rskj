/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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
package co.rsk.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class SimpleFileWriter {
    private static final String TMP = ".tmp";
    private static SimpleFileWriter instance;

    private SimpleFileWriter() {
    }

    public static SimpleFileWriter getInstance() {
        if (instance == null) {
            instance = new SimpleFileWriter();
        }
        return instance;
    }

    public void savePropertiesIntoFile(Properties properties, Path filePath) throws IOException {
        File tempFile = File.createTempFile(filePath.toString(), TMP);
        try (FileWriter writer = new FileWriter(tempFile, false)) {
            properties.store(writer, null);
        }
        filePath.toFile().getParentFile().mkdirs();
        Files.move(tempFile.toPath(), filePath, REPLACE_EXISTING);
    }
    public void saveDataIntoFile(String data, Path filePath) throws IOException {

        File tempFile = File.createTempFile(filePath.toString(), TMP);
        try (FileWriter writer = new FileWriter(tempFile, false)) {
            writer.write(data);
        }
        filePath.toFile().getParentFile().mkdirs();
        Files.move(tempFile.toPath(), filePath, REPLACE_EXISTING);
    }
}

