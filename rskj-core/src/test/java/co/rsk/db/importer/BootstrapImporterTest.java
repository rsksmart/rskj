package co.rsk.db.importer;

import co.rsk.db.importer.provider.BootstrapDataProvider;
import co.rsk.trie.TrieStore;
import org.ethereum.core.BlockFactory;
import org.ethereum.db.BlockStore;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BootstrapImporterTest {

    @Test
    void importData() throws IOException, URISyntaxException {
        BlockStore blockStore = mock(BlockStore.class);
        when(blockStore.getMaxNumber()).thenReturn(0L);
        when(blockStore.isEmpty()).thenReturn(false);

        BlockFactory blockFactory = mock(BlockFactory.class);
        TrieStore trieStore = mock(TrieStore.class);

        BootstrapDataProvider bootstrapDataProvider = mock(BootstrapDataProvider.class);
        // using toURI() instead of getPath() prevent some errors on Windows
        // (https://stackoverflow.com/questions/38887853/java-nio-file-invalidpathexception-with-getpath/38888561)
        Path path = Paths.get(getClass().getClassLoader().getResource("import/bootstrap-data.bin").toURI());
        byte[] oneBlockAndState = Files.readAllBytes(path);
        when(bootstrapDataProvider.getBootstrapData()).thenReturn(oneBlockAndState);
        when(bootstrapDataProvider.getSelectedHeight()).thenReturn(1L);

        BootstrapImporter bootstrapImporter = new BootstrapImporter(blockStore, trieStore,
                                                                    blockFactory,
                                                                    bootstrapDataProvider
        );

        bootstrapImporter.importData();
        verify(blockFactory, atLeastOnce()).decodeBlock(any());
    }
}
