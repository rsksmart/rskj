package org.ethereum.datasource;

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

class NotParameterizedKeyValueDataSourceTest {

    @TempDir
    public Path tempDir;

    @Test
    void testShouldValidateKindWithEmptyDbDirAndResetDbFalseSuccessfully() {
        KeyValueDataSourceUtils.validateDbKind(DbKind.ROCKS_DB, tempDir.toString(), false);
    }

    @Test()
    void testShouldNotThrowErrorWhenValidatingDifferentKinds() throws IOException {
        FileWriter fileWriter = new FileWriter(new File(tempDir.toString(), KeyValueDataSourceUtils.DB_KIND_PROPERTIES_FILE));
        fileWriter.write("keyvalue.datasource=ROCKS_DB\n");
        fileWriter.close();
        String path = tempDir.toString();
        Assertions.assertDoesNotThrow(() -> KeyValueDataSourceUtils.validateDbKind(DbKind.LEVEL_DB, path, false));
    }

    @Test()
    void testShouldThrowErrorWhenDbDirIsAFile() throws IOException {
        File file = new File(tempDir.toString(), KeyValueDataSourceUtils.DB_KIND_PROPERTIES_FILE);
        FileWriter fileWriter = new FileWriter(file);
        fileWriter.write("keyvalue.datasource=ROCKS_DB\n");
        fileWriter.close();
        String path = file.getPath();
        Assertions.assertThrows(IllegalStateException.class, () -> KeyValueDataSourceUtils.validateDbKind(DbKind.LEVEL_DB, path, false));
    }

    @Test
    void mergeDataSourceLevelDb() {
        testMergeDataSource(DbKind.LEVEL_DB);
    }

    @Test
    void mergeDataSourceRocksDb() {
        testMergeDataSource(DbKind.ROCKS_DB);
    }

    @Test
    void getDbKindValueFromDbKindFileTest() throws IOException {
        String dbPath = tempDir.toString();
        File dbKindFile = tempDir.resolve(KeyValueDataSourceUtils.DB_KIND_PROPERTIES_FILE).toFile();
        String propName = "keyvalue.datasource=";

        BufferedWriter writer = new BufferedWriter(new FileWriter(dbKindFile));
        writer.write(propName + DbKind.LEVEL_DB);
        writer.close();
        DbKind dbKindLevel = KeyValueDataSourceUtils.getDbKindValueFromDbKindFile(dbPath).getDbKind();
        Assertions.assertEquals(DbKind.LEVEL_DB, dbKindLevel, "When DbKind file is LEVEL_DB, calculated should too");

        dbKindFile.delete();
        writer = new BufferedWriter(new FileWriter(dbKindFile));
        writer.write(propName + DbKind.ROCKS_DB);
        writer.close();
        DbKind dbKindRocks = KeyValueDataSourceUtils.getDbKindValueFromDbKindFile(dbPath).getDbKind();
        Assertions.assertEquals(DbKind.ROCKS_DB, dbKindRocks, "When DbKind file is ROCKS_DB, calculated should too");

        dbKindFile.delete();
        DbKind dbKindFallback = KeyValueDataSourceUtils.getDbKindValueFromDbKindFile(dbPath).getDbKind();
        Assertions.assertEquals(DbKind.ROCKS_DB, dbKindFallback, "When missing DbKind, LEVEL_DB should be returned as fallback");
    }

    @Test
    void generatedDbKindFileDbKindFileTest() {
        String dbPath = tempDir.toString();
        File dbKindFile = tempDir.resolve(KeyValueDataSourceUtils.DB_KIND_PROPERTIES_FILE).toFile();

        KeyValueDataSourceUtils.generatedDbKindFile(DbKind.LEVEL_DB, dbPath);
        DbKind dbKindLevel = KeyValueDataSourceUtils.getDbKindValueFromDbKindFile(dbPath).getDbKind();
        Assertions.assertEquals(DbKind.LEVEL_DB, dbKindLevel, "When LEVEL_DB is provided on generate, that should be the value when requested");

        dbKindFile.delete();

        KeyValueDataSourceUtils.generatedDbKindFile(DbKind.ROCKS_DB, dbPath);
        DbKind dbKindRocks = KeyValueDataSourceUtils.getDbKindValueFromDbKindFile(dbPath).getDbKind();
        Assertions.assertEquals(DbKind.ROCKS_DB, dbKindRocks, "When ROCKS_DB is provided on generate, that should be the value when requested");
    }

    @Test
    void validateDbKindNoFolder() throws IOException {
        String dbPathNoDir = Files.createFile(Paths.get(tempDir.toString(), "no_file")).toAbsolutePath().toString();
        Assertions.assertThrows(IllegalStateException.class, () -> KeyValueDataSourceUtils.validateDbKind(DbKind.LEVEL_DB, dbPathNoDir, false));
    }

    @Test
    void validateDbKindMissingFolder() {
        String dbPath = tempDir.toString();
        KeyValueDataSourceUtils.validateDbKind(DbKind.ROCKS_DB, dbPath, false);
        DbKind dbKindLevel = KeyValueDataSourceUtils.getDbKindValueFromDbKindFile(dbPath).getDbKind();
        Assertions.assertEquals(DbKind.ROCKS_DB, dbKindLevel, "When DbKind file is missing validation should create it with the provided value");
    }

    @Test
    void validateDbKindExistingFolderDifferentDbWithoutResetShouldNotThrowError() {
        String dbPath = tempDir.toString();
        KeyValueDataSourceUtils.generatedDbKindFile(DbKind.ROCKS_DB, dbPath);
        Assertions.assertDoesNotThrow(() -> KeyValueDataSourceUtils.validateDbKind(DbKind.LEVEL_DB, dbPath, false));
    }

    @Test
    void validateDbKindExistingFolderDifferentDbWithResetGeneratesNewFileThrows() {
        String dbPath = tempDir.toString();
        KeyValueDataSourceUtils.generatedDbKindFile(DbKind.ROCKS_DB, dbPath);
        KeyValueDataSourceUtils.validateDbKind(DbKind.LEVEL_DB, dbPath, true);
        DbKind dbKindLevel = KeyValueDataSourceUtils.getDbKindValueFromDbKindFile(dbPath).getDbKind();
        Assertions.assertEquals(DbKind.LEVEL_DB, dbKindLevel, "When DbKind changes and reset flag is specified on validation, new DbKind file should be generated");
    }

    @Test
    void validateDbKindExistingFolderSameDbWithoutResetDoesNothing() {
        String dbPath = tempDir.toString();
        KeyValueDataSourceUtils.generatedDbKindFile(DbKind.ROCKS_DB, dbPath);
        KeyValueDataSourceUtils.validateDbKind(DbKind.ROCKS_DB, dbPath, false);
        DbKind dbKindLevel = KeyValueDataSourceUtils.getDbKindValueFromDbKindFile(dbPath).getDbKind();
        Assertions.assertEquals(DbKind.ROCKS_DB, dbKindLevel, "When same DB without reset specified on validation, nothing changes on DbKind file");
    }

    @Test
    void validateDbKindExistingFolderSameDbWithResetDoesNothing() {
        String dbPath = tempDir.toString();
        KeyValueDataSourceUtils.generatedDbKindFile(DbKind.ROCKS_DB, dbPath);
        KeyValueDataSourceUtils.validateDbKind(DbKind.ROCKS_DB, dbPath, true);
        DbKind dbKindLevel = KeyValueDataSourceUtils.getDbKindValueFromDbKindFile(dbPath).getDbKind();
        Assertions.assertEquals(DbKind.ROCKS_DB, dbKindLevel, "When same DB and reset specified on validation, nothing changes on DbKind file");
    }

    private void testMergeDataSource(DbKind dbKind) {
        Path triePath = tempDir.resolve("trie");
        byte[] keyTrie = "keyTrie".getBytes();
        createDS(triePath, dbKind, keyTrie, "valueTrie".getBytes());

        Path multiTriePath = tempDir.resolve("multiTrie");
        byte[] keyMultiTrie = "keyMultiTrie".getBytes();
        createDS(multiTriePath, dbKind, keyMultiTrie, "valueMultiTrie".getBytes());

        KeyValueDataSourceUtils.mergeDataSources(triePath, Collections.singletonList(multiTriePath), dbKind);

        // being able to init the DS proves it was closed on mergeDataSources
        KeyValueDataSource trieDS = KeyValueDataSourceUtils.makeDataSource(triePath, dbKind);
        Assertions.assertNull(trieDS.get("missingKey".getBytes()), "Key not present on any DS should not be present after merge");
        Assertions.assertNotNull(trieDS.get(keyTrie), "Pre-existing key on destination should exist after merge");
        Assertions.assertNotNull(trieDS.get(keyMultiTrie), "Key from origination should exist on destination after merge");

        // being able to init the DS proves it was closed on mergeDataSources
        KeyValueDataSource multiTrieDS = KeyValueDataSourceUtils.makeDataSource(multiTriePath, dbKind);
        Assertions.assertNull(multiTrieDS.get("missingKey".getBytes()), "Origination should remain intact, key not present before should not exist after merge");
        Assertions.assertNull(multiTrieDS.get(keyTrie), "Origination should remain intact, nothing copied from destination");
        Assertions.assertNotNull(multiTrieDS.get(keyMultiTrie), "Origination should remain intact, pre-existing key should exist after merge");
    }

    private static void createDS(Path triePath, DbKind rocksDb, byte[] key, byte[] value) {
        KeyValueDataSource trieDS = KeyValueDataSourceUtils.makeDataSource(triePath, rocksDb);
        trieDS.put(key, value);
        trieDS.flush();
        trieDS.close();
    }
}
