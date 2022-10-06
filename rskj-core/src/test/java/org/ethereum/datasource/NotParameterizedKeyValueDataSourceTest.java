package org.ethereum.datasource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;

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
    void mergeDataSourceLevelDbTest() throws IOException {
        testMergeDataSource(DbKind.LEVEL_DB);
    }

    @Test
    void mergeDataSourceRocksDbTest() throws IOException {
        testMergeDataSource(DbKind.ROCKS_DB);
    }

    private void testMergeDataSource(DbKind dbKind) {
        Path triePath = tempDir.resolve("trie");
        byte[] keyTrie = "keyTrie".getBytes();
        createDS(triePath, dbKind, keyTrie, "valueTrie".getBytes());

        Path multiTriePath = tempDir.resolve("multiTrie");
        byte[] keyMultiTrie = "keyMultiTrie".getBytes();
        createDS(multiTriePath, dbKind, keyMultiTrie, "valueMultiTrie".getBytes());

        KeyValueDataSource.mergeDataSources(triePath, Collections.singletonList(multiTriePath), dbKind);

        // being able to init the DS proves it was closed on mergeDataSources
        KeyValueDataSource trieDS = KeyValueDataSource.makeDataSource(triePath, dbKind);
        Assertions.assertNull(trieDS.get("missingKey".getBytes()), "Key not present on any DS should not be present after merge");
        Assertions.assertNotNull(trieDS.get(keyTrie), "Pre-existing key on destination should exist after merge");
        Assertions.assertNotNull(trieDS.get(keyMultiTrie), "Key from origination should exist on destination after merge");

        // being able to init the DS proves it was closed on mergeDataSources
        KeyValueDataSource multiTrieDS = KeyValueDataSource.makeDataSource(multiTriePath, dbKind);
        Assertions.assertNull(multiTrieDS.get("missingKey".getBytes()), "Origination should remain intact, key not present before should not exist after merge");
        Assertions.assertNull(multiTrieDS.get(keyTrie), "Origination should remain intact, nothing copied from destination");
        Assertions.assertNotNull(multiTrieDS.get(keyMultiTrie), "Origination should remain intact, pre-existing key should exist after merge");
    }

    private static void createDS(Path triePath, DbKind rocksDb, byte[] key, byte[] value) {
        KeyValueDataSource trieDS = KeyValueDataSource.makeDataSource(triePath, rocksDb);
        trieDS.put(key, value);
        trieDS.flush();
        trieDS.close();
    }
}
