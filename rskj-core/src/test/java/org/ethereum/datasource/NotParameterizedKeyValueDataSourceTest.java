package org.ethereum.datasource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

public class NotParameterizedKeyValueDataSourceTest {

    @TempDir
    public Path tempDir;

    @Test
    void testShouldValidateKindWithEmptyDbDirAndResetDbFalseSuccessfully() {
        KeyValueDataSource.validateDbKind(DbKind.ROCKS_DB, tempDir.toString(), false);
    }

    @Test()
    public void testShouldThrowErrorWhenValidatingDifferentKinds() throws IOException {
        FileWriter fileWriter = new FileWriter(new File(tempDir.toString(), KeyValueDataSource.DB_KIND_PROPERTIES_FILE));
        fileWriter.write("keyvalue.datasource=ROCKS_DB\n");
        fileWriter.close();
        String path = tempDir.toString();
        Assertions.assertThrows(IllegalStateException.class, () -> KeyValueDataSource.validateDbKind(DbKind.LEVEL_DB, path, false));
    }

    @Test()
    public void testShouldThrowErrorWhenDbDirIsAFile() throws IOException {
        File file = new File(tempDir.toString(), KeyValueDataSource.DB_KIND_PROPERTIES_FILE);
        FileWriter fileWriter = new FileWriter(file);
        fileWriter.write("keyvalue.datasource=ROCKS_DB\n");
        fileWriter.close();
        String path = file.getPath();
        Assertions.assertThrows(IllegalStateException.class, () -> KeyValueDataSource.validateDbKind(DbKind.LEVEL_DB, path, false));
    }
}
