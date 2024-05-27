package org.ethereum.core.genesis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;

class GenesisLoaderImplTest {
    private final String GENESIS_FILE_NAME = "temp_genesis.json";

    private final String RESOURCES_GENESIS_FILE_PATH = Objects.requireNonNull(GenesisLoaderImpl.class.getResource("/genesis")).getPath() + "/" + GENESIS_FILE_NAME;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private boolean isStreamReadable(InputStream stream) {
        try {
            stream.read();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @BeforeEach
    public void createTempFiles() throws IOException {
        boolean created = (new File(RESOURCES_GENESIS_FILE_PATH)).createNewFile();
        Assertions.assertTrue(created);
    }

    @AfterEach
    public void cleanUpFiles() {
        boolean deleted = (new File(RESOURCES_GENESIS_FILE_PATH)).delete();
        Assertions.assertTrue(deleted);
    }
    
    @Test
    void loadGenesisFile_fromResourcesDir() {
        InputStream genesisFileStream = GenesisLoaderImpl.loadGenesisFile(GENESIS_FILE_NAME);

        Assertions.assertTrue(isStreamReadable(genesisFileStream));
    }

    @Test
    void loadGenesisFile_missingFile_inResourcesDir() {
        String genesisFilePath = new File("non-existent-file.json").getPath();

        Exception e = Assertions.assertThrows(GenesisLoaderException.class, () -> GenesisLoaderImpl.loadGenesisFile(genesisFilePath));
        Assertions.assertEquals("Cannot open genesis block configuration file", e.getMessage());
    }

    @Test
    void loadGenesisFile_fromSystem(@TempDir Path tempGenesisDir) throws IOException {
        File genesisFile = new File(tempGenesisDir + "/" + GENESIS_FILE_NAME);
        Assertions.assertTrue(genesisFile.createNewFile());
        InputStream genesisFileStream = GenesisLoaderImpl.loadGenesisFile(genesisFile.getPath());

        Assertions.assertTrue(isStreamReadable(genesisFileStream));
    }

    @Test
    void loadGenesisFile_missingFile_inSystem(@TempDir Path tempGenesisDir) {
        String genesisFilePath = new File(tempGenesisDir + "/non-existent-file.json").getPath();

        Exception e = Assertions.assertThrows(GenesisLoaderException.class, () -> GenesisLoaderImpl.loadGenesisFile(genesisFilePath));
        Assertions.assertEquals("Cannot open genesis block configuration file", e.getMessage());
    }
}
