/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

package co.rsk.signing;

import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.util.collections.Sets;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;

/**
 * Created by ajlopez on 29/12/2016.
 */
public class KeyFileCheckerTest {
    private static final String KEY_FILE_PATH = "./keyfiletest.txt";

    @Before
    public void init() throws IOException {
        Files.deleteIfExists(Paths.get(KEY_FILE_PATH));
    }

    @After
    public void cleanUp() throws IOException {
        Files.deleteIfExists(Paths.get(KEY_FILE_PATH));
    }

    @Test
    public void invalidFileNameIfNull() {
        KeyFileChecker checker = new KeyFileChecker(null);
        Assert.assertEquals(checker.checkKeyFile(), "Invalid Key File Name");
    }

    @Test
    public void invalidFileNameIfEmpty() {
        KeyFileChecker checker = new KeyFileChecker("");
        Assert.assertEquals(checker.checkKeyFile(), "Invalid Key File Name");
    }

    @Test
    public void fileDoesNotExist() {
        KeyFileChecker checker = new KeyFileChecker("unknown.txt");
        Assert.assertEquals(checker.checkKeyFile(), "Key File 'unknown.txt' does not exist");
    }

    @Test
    public void readKeyFromFile() throws IOException {
        this.writeTestKeyFile("bd3d20e480dfb1c9c07ba0bc8cf9052f89923d38b5128c5dbfc18d4eea3826af");
        KeyFileChecker checker = new KeyFileChecker(KEY_FILE_PATH);
        Assert.assertTrue(StringUtils.isEmpty(checker.checkKeyFile()));
    }

    @Test
    public void invalidKeyFormatInFile() throws IOException {
        this.writeTestKeyFile("zz3d20e480dfb1c9c07ba0bc8cf9052f89923d38b5128c5dbfc18d4eea3826af");
        KeyFileChecker checker = new KeyFileChecker(KEY_FILE_PATH);

        Assert.assertEquals("Error Reading Key File './keyfiletest.txt'", checker.checkKeyFile());
    }

    @Test
    public void invalidPermissions() throws IOException {
        this.writeTestKeyFile("zz3d20e480dfb1c9c07ba0bc8cf9052f89923d38b5128c5dbfc18d4eea3826af");
        KeyFileChecker checker = new KeyFileChecker(KEY_FILE_PATH);

        Assert.assertEquals("Invalid key file permissions", checker.checkFilePermissions());
    }

    @Test
    public void validPermissions() throws IOException {
        this.writeTestKeyFile("zz3d20e480dfb1c9c07ba0bc8cf9052f89923d38b5128c5dbfc18d4eea3826af");
        Files.setPosixFilePermissions(Paths.get(KEY_FILE_PATH), Sets.newSet(PosixFilePermission.OWNER_READ));
        KeyFileChecker checker = new KeyFileChecker(KEY_FILE_PATH);

        Assert.assertEquals("", checker.checkFilePermissions());
    }

    private void writeTestKeyFile(String key) throws IOException {
        PrintWriter writer = new PrintWriter(new FileWriter(KEY_FILE_PATH));
        writer.println(key);
        writer.close();
    }
}
