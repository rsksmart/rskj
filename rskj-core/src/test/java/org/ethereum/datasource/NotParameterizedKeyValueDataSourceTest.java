package org.ethereum.datasource;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class NotParameterizedKeyValueDataSourceTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testShouldValidateKindWithEmptyDbDirAndResetDbFalseSuccessfully() {
        KeyValueDataSource.validateDbKind(DbKind.ROCKS_DB, tempFolder.getRoot().getPath(), false);
    }

    @Test(expected = IllegalStateException.class)
    public void testShouldThrowErrorWhenValidatingDifferentKinds() throws IOException {
        FileWriter fileWriter = new FileWriter(new File(tempFolder.getRoot(), KeyValueDataSource.DB_KIND_PROPERTIES_FILE));
        fileWriter.write("keyvalue.datasource=ROCKS_DB\n");
        fileWriter.close();
        KeyValueDataSource.validateDbKind(DbKind.LEVEL_DB, tempFolder.getRoot().getPath(), false);
    }

    @Test(expected = IllegalStateException.class)
    public void testShouldThrowErrorWhenDbDirIsAFile() throws IOException {
        File file = new File(tempFolder.getRoot(), KeyValueDataSource.DB_KIND_PROPERTIES_FILE);
        FileWriter fileWriter = new FileWriter(file);
        fileWriter.write("keyvalue.datasource=ROCKS_DB\n");
        fileWriter.close();
        KeyValueDataSource.validateDbKind(DbKind.LEVEL_DB, file.getPath(), false);
    }
}
