package co.rsk.db.importer;

import co.rsk.db.importer.provider.BootstrapDataProvider;
import co.rsk.trie.TrieStore;
import org.ethereum.core.BlockFactory;
import org.ethereum.db.BlockStore;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class BootstrapImporterTest {

    @Test
    public void importData() throws IOException {
        BlockStore blockStore = mock(BlockStore.class);
        when(blockStore.getMaxNumber()).thenReturn(0L);
        when(blockStore.isEmpty()).thenReturn(false);

        BlockFactory blockFactory = mock(BlockFactory.class);
        TrieStore trieStore = mock(TrieStore.class);

        BootstrapDataProvider bootstrapDataProvider = mock(BootstrapDataProvider.class);
        Path path = Paths.get(getClass().getClassLoader().getResource("import/bootstrap-data.bin").getPath());
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