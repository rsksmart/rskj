package org.ethereum.datasource;

import co.rsk.config.RskSystemProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class NotParameterizedKeyValueDataSourceTest {

    @TempDir
    public Path tempDir;

    @Test
    void testShouldValidateKindWithEmptyDbDirAndResetDbFalseSuccessfully() {
        KeyValueDataSource.validateDbKind(DbKind.ROCKS_DB, tempDir.toString(), false);
    }

    @Test()
    void testShouldThrowErrorWhenValidatingDifferentKinds() throws IOException {
        FileWriter fileWriter = new FileWriter(new File(tempDir.toString(), KeyValueDataSource.DB_KIND_PROPERTIES_FILE));
        fileWriter.write("keyvalue.datasource=ROCKS_DB\n");
        fileWriter.close();
        String path = tempDir.toString();
        Assertions.assertThrows(IllegalStateException.class, () -> KeyValueDataSource.validateDbKind(DbKind.LEVEL_DB, path, false));
    }

    @Test()
    void testShouldThrowErrorWhenDbDirIsAFile() throws IOException {
        File file = new File(tempDir.toString(), KeyValueDataSource.DB_KIND_PROPERTIES_FILE);
        FileWriter fileWriter = new FileWriter(file);
        fileWriter.write("keyvalue.datasource=ROCKS_DB\n");
        fileWriter.close();
        String path = file.getPath();
        Assertions.assertThrows(IllegalStateException.class, () -> KeyValueDataSource.validateDbKind(DbKind.LEVEL_DB, path, false));
    }

    @Test
    void mergeDataSourceLevelDb() {
        RskSystemProperties rskSystemProperties = mock(RskSystemProperties.class);
        doReturn(DbKind.LEVEL_DB).when(rskSystemProperties).databaseKind();
        doReturn(tempDir.toString()).when(rskSystemProperties).databaseDir();

        testMergeDataSource(DbKind.LEVEL_DB, rskSystemProperties);
    }

    @Test
    void mergeDataSourceRocksDb() {
        RskSystemProperties rskSystemProperties = mock(RskSystemProperties.class);
        doReturn(DbKind.LEVEL_DB).when(rskSystemProperties).databaseKind();
        doReturn(tempDir.toString()).when(rskSystemProperties).databaseDir();

        testMergeDataSource(DbKind.ROCKS_DB, rskSystemProperties);
    }

    @Test
    void getDbKindValueFromDbKindFileTest() throws IOException {
        String dbPath = tempDir.toString();
        File dbKindFile = tempDir.resolve(KeyValueDataSource.DB_KIND_PROPERTIES_FILE).toFile();
        String propName = "keyvalue.datasource=";

        BufferedWriter writer = new BufferedWriter(new FileWriter(dbKindFile));
        writer.write(propName + DbKind.LEVEL_DB);
        writer.close();
        DbKind dbKindLevel = KeyValueDataSource.getDbKindValueFromDbKindFile(dbPath);
        Assertions.assertEquals(DbKind.LEVEL_DB, dbKindLevel, "When DbKind file is LEVEL_DB, calculated should too");

        dbKindFile.delete();
        writer = new BufferedWriter(new FileWriter(dbKindFile));
        writer.write(propName + DbKind.ROCKS_DB);
        writer.close();
        DbKind dbKindRocks = KeyValueDataSource.getDbKindValueFromDbKindFile(dbPath);
        Assertions.assertEquals(DbKind.ROCKS_DB, dbKindRocks, "When DbKind file is ROCKS_DB, calculated should too");

        dbKindFile.delete();
        DbKind dbKindFallback = KeyValueDataSource.getDbKindValueFromDbKindFile(dbPath);
        Assertions.assertEquals(DbKind.LEVEL_DB, dbKindFallback, "When missing DbKind, LEVEL_DB should be returned as fallback");
    }

    @Test
    void generatedDbKindFileDbKindFileTest() {
        String dbPath = tempDir.toString();
        File dbKindFile = tempDir.resolve(KeyValueDataSource.DB_KIND_PROPERTIES_FILE).toFile();

        KeyValueDataSource.generatedDbKindFile(DbKind.LEVEL_DB, dbPath);
        DbKind dbKindLevel = KeyValueDataSource.getDbKindValueFromDbKindFile(dbPath);
        Assertions.assertEquals(DbKind.LEVEL_DB, dbKindLevel, "When LEVEL_DB is provided on generate, that should be the value when requested");

        dbKindFile.delete();

        KeyValueDataSource.generatedDbKindFile(DbKind.ROCKS_DB, dbPath);
        DbKind dbKindRocks = KeyValueDataSource.getDbKindValueFromDbKindFile(dbPath);
        Assertions.assertEquals(DbKind.ROCKS_DB, dbKindRocks, "When ROCKS_DB is provided on generate, that should be the value when requested");
    }

    @Test
    void validateDbKindNoFolder() throws IOException {
        String dbPathNoDir = Files.createFile(Paths.get(tempDir.toString(), "no_file")).toAbsolutePath().toString();
        Assertions.assertThrows(IllegalStateException.class, () -> KeyValueDataSource.validateDbKind(DbKind.LEVEL_DB, dbPathNoDir, false));
    }

    @Test
    void validateDbKindMissingFolder() {
        String dbPath = tempDir.toString();
        boolean shouldGenerateDbKindFile = KeyValueDataSource.validateDbKind(DbKind.ROCKS_DB, dbPath, false);

        if (shouldGenerateDbKindFile) {
            KeyValueDataSource.generatedDbKindFile(DbKind.ROCKS_DB, dbPath);
        }

        DbKind dbKindLevel = KeyValueDataSource.getDbKindValueFromDbKindFile(dbPath);
        Assertions.assertEquals(DbKind.ROCKS_DB, dbKindLevel, "When DbKind file is missing validation should create it with the provided value");
    }

    @Test
    void validateDbKindExistingFolderDifferentDbWithoutResetThrows() {
        String dbPath = tempDir.toString();
        KeyValueDataSource.generatedDbKindFile(DbKind.ROCKS_DB, dbPath);

        try {
            KeyValueDataSource.validateDbKind(DbKind.LEVEL_DB, dbPath, false);
            Assertions.fail("Should've thrown exception due to already existing dbKind file without reset flag");
        } catch (RuntimeException re) {
            Assertions.assertTrue(re.getMessage().startsWith("DbKind mismatch. You have selected"));
        }
    }

    @Test
    void validateDbKindExistingFolderDifferentDbWithResetGeneratesNewFileThrows() {
        String dbPath = tempDir.toString();
        KeyValueDataSource.generatedDbKindFile(DbKind.ROCKS_DB, dbPath);

        boolean shouldGenerateDbKindFile = KeyValueDataSource.validateDbKind(DbKind.LEVEL_DB, dbPath, true);

        if (shouldGenerateDbKindFile) {
            KeyValueDataSource.generatedDbKindFile(DbKind.LEVEL_DB, dbPath);
        }

        DbKind dbKindLevel = KeyValueDataSource.getDbKindValueFromDbKindFile(dbPath);
        Assertions.assertEquals(DbKind.LEVEL_DB, dbKindLevel, "When DbKind changes and reset flag is specified on validation, new DbKind file should be generated");
    }

    @Test
    void validateDbKindExistingFolderSameDbWithoutResetDoesNothing() {
        String dbPath = tempDir.toString();
        KeyValueDataSource.generatedDbKindFile(DbKind.ROCKS_DB, dbPath);
        KeyValueDataSource.validateDbKind(DbKind.ROCKS_DB, dbPath, false);
        DbKind dbKindLevel = KeyValueDataSource.getDbKindValueFromDbKindFile(dbPath);
        Assertions.assertEquals(DbKind.ROCKS_DB, dbKindLevel, "When same DB without reset specified on validation, nothing changes on DbKind file");
    }

    @Test
    void validateDbKindExistingFolderSameDbWithResetDoesNothing() {
        String dbPath = tempDir.toString();
        KeyValueDataSource.generatedDbKindFile(DbKind.ROCKS_DB, dbPath);
        KeyValueDataSource.validateDbKind(DbKind.ROCKS_DB, dbPath, true);
        DbKind dbKindLevel = KeyValueDataSource.getDbKindValueFromDbKindFile(dbPath);
        Assertions.assertEquals(DbKind.ROCKS_DB, dbKindLevel, "When same DB and reset specified on validation, nothing changes on DbKind file");
    }

    private void testMergeDataSource(DbKind dbKind, RskSystemProperties rskSystemProperties) {
        Path triePath = tempDir.resolve("trie");
        byte[] keyTrie = "keyTrie".getBytes();
        createDS(triePath, dbKind, keyTrie, "valueTrie".getBytes(), rskSystemProperties);

        Path multiTriePath = tempDir.resolve("multiTrie");
        byte[] keyMultiTrie = "keyMultiTrie".getBytes();
        createDS(multiTriePath, dbKind, keyMultiTrie, "valueMultiTrie".getBytes(), rskSystemProperties);

        KeyValueDataSource.mergeDataSources(triePath, Collections.singletonList(multiTriePath), dbKind, rskSystemProperties);

        // being able to init the DS proves it was closed on mergeDataSources
        KeyValueDataSource trieDS = KeyValueDataSource.makeDataSource(triePath, dbKind, rskSystemProperties);
        Assertions.assertNull(trieDS.get("missingKey".getBytes()), "Key not present on any DS should not be present after merge");
        Assertions.assertNotNull(trieDS.get(keyTrie), "Pre-existing key on destination should exist after merge");
        Assertions.assertNotNull(trieDS.get(keyMultiTrie), "Key from origination should exist on destination after merge");

        // being able to init the DS proves it was closed on mergeDataSources
        KeyValueDataSource multiTrieDS = KeyValueDataSource.makeDataSource(multiTriePath, dbKind, rskSystemProperties);
        Assertions.assertNull(multiTrieDS.get("missingKey".getBytes()), "Origination should remain intact, key not present before should not exist after merge");
        Assertions.assertNull(multiTrieDS.get(keyTrie), "Origination should remain intact, nothing copied from destination");
        Assertions.assertNotNull(multiTrieDS.get(keyMultiTrie), "Origination should remain intact, pre-existing key should exist after merge");
    }

    private static void createDS(Path triePath, DbKind rocksDb, byte[] key, byte[] value, RskSystemProperties rskSystemProperties) {
        KeyValueDataSource trieDS = KeyValueDataSource.makeDataSource(triePath, rocksDb, rskSystemProperties);
        trieDS.put(key, value);
        trieDS.flush();
        trieDS.close();
    }
}
