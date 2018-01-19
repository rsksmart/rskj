package co.rsk.signing;

import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

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
        Assert.assertEquals(checker.checkKeyFile(), "Invalid Federate Key File Name");
    }

    @Test
    public void invalidFileNameIfEmpty() {
        KeyFileChecker checker = new KeyFileChecker("");
        Assert.assertEquals(checker.checkKeyFile(), "Invalid Federate Key File Name");
    }

    @Test
    public void fileDoesNotExist() {
        KeyFileChecker checker = new KeyFileChecker("unknown.txt");
        Assert.assertEquals(checker.checkKeyFile(), "Federate Key File 'unknown.txt' does not exist");
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

        Assert.assertEquals("Error Reading Federate Key File './keyfiletest.txt'", checker.checkKeyFile());
    }

    private void writeTestKeyFile(String key) throws IOException {
        PrintWriter writer = new PrintWriter(new FileWriter(KEY_FILE_PATH));
        writer.println(key);
        writer.close();
    }
}
