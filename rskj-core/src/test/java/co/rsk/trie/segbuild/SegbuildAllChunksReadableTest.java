package co.rsk.trie.segbuild;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every chunk in the set must be openable by this build. A chunk written by a newer RocksDB than
 * the one linked here cannot be read at all, so this is what says whether the library version in
 * use is sufficient for the store on disk.
 */
class SegbuildAllChunksReadableTest {

    private static final Path ROOT = Paths.get(System.getProperty("segbuild.dir", "/srv/segbuild-ro"));

    @Test
    void everyChunkCanBeOpened() {
        Assumptions.assumeTrue(Files.isDirectory(ROOT), "segbuild store not present at " + ROOT);

        SegbuildChunkIndex index = SegbuildChunkIndex.scan(ROOT);
        List<String> unreadable = new ArrayList<>();

        for (SegbuildChunk chunk : index.getChunks()) {
            SegbuildTrieStore store = new SegbuildTrieStore(index);
            try {
                store.positionAt(chunk.getFirstBlock());
                store.contains(new byte[32]);
            } catch (RuntimeException e) {
                unreadable.add(chunk + " -> " + e.getMessage());
            } finally {
                store.dispose();
            }
        }

        assertEquals(List.of(), unreadable, "chunks this build cannot open");
        System.out.println("@@@ all " + index.size() + " chunks opened");
    }
}
