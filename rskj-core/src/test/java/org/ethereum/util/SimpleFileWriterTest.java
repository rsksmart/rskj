/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
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
package org.ethereum.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimpleFileWriterTest {

    private SimpleFileWriter simpleFileWriter;
    private Path filePath;

    @BeforeEach
    public void setup() throws IOException {
        simpleFileWriter = SimpleFileWriter.getInstance();
        filePath = Files.createTempFile("test", ".txt");
    }

    @Test
    void testSaveDataIntoFile() throws IOException {
        String data = "Hello, World!";
        simpleFileWriter.saveDataIntoFile(data, filePath);

        String readData = new String(Files.readAllBytes(filePath));
        assertEquals(data, readData);
    }
}