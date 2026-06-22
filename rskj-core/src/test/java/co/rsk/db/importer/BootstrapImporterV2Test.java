/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.db.importer;

import co.rsk.db.importer.provider.BootstrapDataProvider;
import co.rsk.trie.IterationElement;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.datasource.DbKind;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.BlockStore;
import org.ethereum.util.RLP;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Round-trips the v2 (chunked, streaming) {@code bootstrap-data.bin} format through the importer: a
 * state trie (with short values, long values, and many nodes) is serialized into v2 bytes exactly the
 * way the exporter does, then imported, and the reconstructed state is asserted byte-for-byte.
 *
 * <p>The v2 writer here mirrors {@code FileExporter} (separate repo) on purpose, so this test also pins
 * the cross-repo format contract. A deliberately tiny chunk size forces multi-chunk sections, exercising
 * the chunk-spanning read path.
 */
class BootstrapImporterV2Test {

    @TempDir
    Path tempDir;

    @Test
    void importsV2RoundTripReconstructingState() throws IOException {
        // origin state with a mix of node shapes: short values (inlined) and long values (> 32 bytes,
        // stored separately and referenced by hash) so both the nodes and values sections are non-trivial.
        TrieStore originStore = new TrieStoreImpl(new HashMapDB());
        Map<byte[], byte[]> expected = new LinkedHashMap<>();
        Trie trie = new Trie(originStore);
        for (int i = 0; i < 64; i++) {
            byte[] key = ("account/" + i).getBytes(StandardCharsets.UTF_8);
            // alternate short (<= 32B) and long (> 32B) values
            byte[] value = (i % 2 == 0)
                    ? ("v" + i).getBytes(StandardCharsets.UTF_8)
                    : longValue(i);
            trie = trie.put(key, value);
            expected.put(key, value);
        }
        originStore.save(trie);
        byte[] stateRoot = trie.getHash().getBytes();

        // tiny chunk size to force multi-chunk sections (chunk-spanning read path)
        byte[] v2 = writeV2(originStore, stateRoot, 96);
        Path binPath = tempDir.resolve("bootstrap-data.bin");
        Files.write(binPath, v2);

        // destination: real trie store (state assertions), mocked block plumbing (block decode/save).
        TrieStore destinationStore = new TrieStoreImpl(new HashMapDB());
        BlockStore blockStore = mock(BlockStore.class);
        BlockFactory blockFactory = mock(BlockFactory.class);
        when(blockFactory.decodeBlock(any())).thenReturn(mock(Block.class));

        BootstrapDataProvider provider = mock(BootstrapDataProvider.class);
        when(provider.getBootstrapDataPath()).thenReturn(binPath);

        BootstrapImporter importer = new InMemoryValueStoreImporter(
                blockStore, destinationStore, blockFactory, DbKind.LEVEL_DB, provider);

        importer.importData();

        // blocks were decoded and saved
        verify(blockFactory, atLeastOnce()).decodeBlock(any());
        verify(blockStore, atLeastOnce()).saveBlock(any(), any(), anyBoolean());

        // the state root is retrievable and every key/value round-trips intact
        Trie reconstructed = destinationStore.retrieve(stateRoot)
                .orElseThrow(() -> new AssertionError("state root missing after import"));
        assertArrayEquals(stateRoot, reconstructed.getHash().getBytes(), "state root mismatch");
        for (Map.Entry<byte[], byte[]> e : expected.entrySet()) {
            assertArrayEquals(e.getValue(), reconstructed.get(e.getKey()),
                    "value mismatch for key " + new String(e.getKey(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void importsV2WithEmptyValuesSection() throws IOException {
        // a state whose values are all short (<= 32B) → the values section is just the terminator.
        TrieStore originStore = new TrieStoreImpl(new HashMapDB());
        Trie trie = new Trie(originStore);
        Map<byte[], byte[]> expected = new LinkedHashMap<>();
        for (int i = 0; i < 16; i++) {
            byte[] key = ("k" + i).getBytes(StandardCharsets.UTF_8);
            byte[] value = ("short" + i).getBytes(StandardCharsets.UTF_8);
            trie = trie.put(key, value);
            expected.put(key, value);
        }
        originStore.save(trie);
        byte[] stateRoot = trie.getHash().getBytes();

        byte[] v2 = writeV2(originStore, stateRoot, 1024);
        // sanity: no long values were emitted
        assertTrue(collectValueElements(originStore, stateRoot).isEmpty(), "expected no long values");
        Path binPath = tempDir.resolve("bootstrap-empty-values.bin");
        Files.write(binPath, v2);

        TrieStore destinationStore = new TrieStoreImpl(new HashMapDB());
        BlockStore blockStore = mock(BlockStore.class);
        BlockFactory blockFactory = mock(BlockFactory.class);
        when(blockFactory.decodeBlock(any())).thenReturn(mock(Block.class));
        BootstrapDataProvider provider = mock(BootstrapDataProvider.class);
        when(provider.getBootstrapDataPath()).thenReturn(binPath);

        new InMemoryValueStoreImporter(blockStore, destinationStore, blockFactory, DbKind.LEVEL_DB, provider)
                .importData();

        Trie reconstructed = destinationStore.retrieve(stateRoot)
                .orElseThrow(() -> new AssertionError("state root missing after import"));
        for (Map.Entry<byte[], byte[]> e : expected.entrySet()) {
            assertArrayEquals(e.getValue(), reconstructed.get(e.getKey()));
        }
    }

    @Test
    void detectsV2ByFirstByte() {
        assertTrue(BootstrapV2Format.isV2('R'));
        assertFalse(BootstrapV2Format.isV2(0xf8)); // a legacy v1 long-list prefix
        assertFalse(BootstrapV2Format.isV2(0xc0));
    }

    @Test
    void failsFastWhenNodesSectionMissing() throws IOException {
        StateFixture fixture = buildState();
        // blocks + values present, but the entire nodes section is omitted: a stateless snapshot must be
        // rejected during import, not "succeed" and only blow up at first state access.
        byte[] v2 = assembleV2(1024,
                syntheticBlockElements(), null, collectValueElements(fixture.store, fixture.stateRoot));

        BootstrapImporter importer = newImporter(v2, "missing-nodes.bin");

        BootstrapImportException ex = assertThrows(BootstrapImportException.class, importer::importData);
        assertTrue(ex.getMessage().toLowerCase().contains("state-nodes"), ex.getMessage());
    }

    @Test
    void failsFastWhenBlocksSectionMissing() throws IOException {
        StateFixture fixture = buildState();
        byte[] v2 = assembleV2(1024,
                null, collectNodeElements(fixture.store, fixture.stateRoot),
                collectValueElements(fixture.store, fixture.stateRoot));

        BootstrapImporter importer = newImporter(v2, "missing-blocks.bin");

        BootstrapImportException ex = assertThrows(BootstrapImportException.class, importer::importData);
        assertTrue(ex.getMessage().toLowerCase().contains("blocks"), ex.getMessage());
    }

    @Test
    void rejectsChunkLengthExceedingFileSizeBeforeAllocating() throws IOException {
        // A chunk whose declared length is far larger than the whole file, yet still under the ~2 GiB
        // array ceiling (MAX_CHUNK_BYTES). It must be rejected against the bytes actually remaining in the
        // file instead of being eagerly allocated as a multi-hundred-MB/GB byte[].
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        out.write(BootstrapV2Format.MAGIC);
        out.writeByte(BootstrapV2Format.VERSION);
        out.writeByte(BootstrapV2Format.TAG_BLOCKS);
        out.writeLong(10_000_000L); // corrupt length; no payload follows — the file ends here
        out.flush();

        BootstrapImporter importer = newImporter(bos.toByteArray(), "corrupt-length.bin");

        BootstrapImportException ex = assertThrows(BootstrapImportException.class, importer::importData);
        assertTrue(ex.getMessage().toLowerCase().contains("exceeds"), ex.getMessage());
    }

    @Test
    void failsWithActionableErrorWhenNodeReferencesMissingLongValue() throws IOException {
        StateFixture fixture = buildState();
        assertFalse(collectValueElements(fixture.store, fixture.stateRoot).isEmpty(),
                "fixture must contain at least one long value");
        // nodes present (they reference long values by hash) but the values section is emitted empty, so
        // every long value is absent from the staged store at node-save time. The opaque
        // IllegalArgumentException from the trie must be translated into an actionable import error.
        byte[] v2 = assembleV2(1024,
                syntheticBlockElements(), collectNodeElements(fixture.store, fixture.stateRoot), new ArrayList<>());

        BootstrapImporter importer = newImporter(v2, "missing-value.bin");

        BootstrapImportException ex = assertThrows(BootstrapImportException.class, importer::importData);
        assertTrue(ex.getMessage().toLowerCase().contains("long value"), ex.getMessage());
    }

    /** An origin store plus the hash of a saved state trie that mixes short and long values. */
    private static final class StateFixture {
        final TrieStore store;
        final byte[] stateRoot;

        StateFixture(TrieStore store, byte[] stateRoot) {
            this.store = store;
            this.stateRoot = stateRoot;
        }
    }

    private static StateFixture buildState() {
        TrieStore store = new TrieStoreImpl(new HashMapDB());
        Trie trie = new Trie(store);
        for (int i = 0; i < 32; i++) {
            byte[] key = ("account/" + i).getBytes(StandardCharsets.UTF_8);
            byte[] value = (i % 2 == 0) ? ("v" + i).getBytes(StandardCharsets.UTF_8) : longValue(i);
            trie = trie.put(key, value);
        }
        store.save(trie);
        return new StateFixture(store, trie.getHash().getBytes());
    }

    /** Writes {@code v2} to a temp file and wires an importer with mocked block plumbing over it. */
    private BootstrapImporter newImporter(byte[] v2, String fileName) throws IOException {
        Path binPath = tempDir.resolve(fileName);
        Files.write(binPath, v2);

        TrieStore destinationStore = new TrieStoreImpl(new HashMapDB());
        BlockStore blockStore = mock(BlockStore.class);
        BlockFactory blockFactory = mock(BlockFactory.class);
        when(blockFactory.decodeBlock(any())).thenReturn(mock(Block.class));
        BootstrapDataProvider provider = mock(BootstrapDataProvider.class);
        when(provider.getBootstrapDataPath()).thenReturn(binPath);

        return new InMemoryValueStoreImporter(
                blockStore, destinationStore, blockFactory, DbKind.LEVEL_DB, provider);
    }

    private static byte[] longValue(int seed) {
        byte[] v = new byte[40 + (seed % 7)]; // always > 32 bytes
        for (int i = 0; i < v.length; i++) {
            v[i] = (byte) (seed * 31 + i);
        }
        return v;
    }

    // --- v2 writer mirroring FileExporter (bootstrap-exporter repo) ---

    private static byte[] writeV2(TrieStore store, byte[] stateRoot, int chunkMax) throws IOException {
        return assembleV2(chunkMax,
                syntheticBlockElements(),
                collectNodeElements(store, stateRoot),
                collectValueElements(store, stateRoot));
    }

    /**
     * Assembles a v2 file from explicit section contents; a {@code null} list omits that section entirely
     * (so callers can build files that are structurally incomplete to exercise the fail-fast guards).
     */
    private static byte[] assembleV2(int chunkMax, List<byte[]> blocks, List<byte[]> nodes, List<byte[]> values)
            throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        out.write(BootstrapV2Format.MAGIC);
        out.writeByte(BootstrapV2Format.VERSION);
        if (blocks != null) {
            writeSection(out, BootstrapV2Format.TAG_BLOCKS, blocks, chunkMax);
        }
        if (nodes != null) {
            writeSection(out, BootstrapV2Format.TAG_NODES, nodes, chunkMax);
        }
        if (values != null) {
            writeSection(out, BootstrapV2Format.TAG_VALUES, values, chunkMax);
        }
        out.writeByte(BootstrapV2Format.TAG_END);
        out.flush();
        return bos.toByteArray();
    }

    /** Writes a tagged section, flushing a chunk whenever the buffer crosses {@code chunkMax}. */
    private static void writeSection(DataOutputStream out, int tag, List<byte[]> elements, int chunkMax) throws IOException {
        out.writeByte(tag);
        ByteArrayOutputStream chunk = new ByteArrayOutputStream();
        for (byte[] element : elements) {
            if (chunk.size() > 0 && chunk.size() + element.length > chunkMax) {
                flushChunk(out, chunk);
            }
            chunk.write(element);
        }
        if (chunk.size() > 0) {
            flushChunk(out, chunk);
        }
        out.writeLong(0L); // end-of-section sentinel
    }

    private static void flushChunk(DataOutputStream out, ByteArrayOutputStream chunk) throws IOException {
        byte[] bytes = chunk.toByteArray();
        out.writeLong(bytes.length);
        out.write(bytes);
        chunk.reset();
    }

    /** Mirrors FileExporter#streamStateElements node emission (root first, then non-embeddable nodes). */
    private static List<byte[]> collectNodeElements(TrieStore store, byte[] stateRoot) {
        Trie root = store.retrieve(stateRoot).orElseThrow(() -> new AssertionError("missing root"));
        List<byte[]> nodes = new ArrayList<>();
        nodes.add(RLP.encodeElement(root.toMessage()));
        Iterator<IterationElement> it = root.getInOrderIterator();
        while (it.hasNext()) {
            Trie node = it.next().getNode();
            if (node.isEmbeddable()) {
                continue;
            }
            nodes.add(RLP.encodeElement(node.toMessage()));
        }
        return nodes;
    }

    private static List<byte[]> collectValueElements(TrieStore store, byte[] stateRoot) {
        Trie root = store.retrieve(stateRoot).orElseThrow(() -> new AssertionError("missing root"));
        List<byte[]> values = new ArrayList<>();
        Iterator<IterationElement> it = root.getInOrderIterator();
        while (it.hasNext()) {
            Trie node = it.next().getNode();
            if (node.hasLongValue()) {
                values.add(RLP.encodeElement(node.getValue()));
            }
        }
        return values;
    }

    private static List<byte[]> syntheticBlockElements() {
        // a block tuple is LIST[ ELEMENT(block), ELEMENT(td) ]; the importer hands element 0 to the
        // (mocked) BlockFactory and element 1 to BlockDifficulty, so arbitrary non-empty bytes suffice.
        List<byte[]> blocks = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            byte[] blockBytes = ("block-" + i).getBytes(StandardCharsets.UTF_8);
            byte[] tdBytes = new byte[]{(byte) i};
            blocks.add(RLP.encodeList(RLP.encodeElement(blockBytes), RLP.encodeElement(tdBytes)));
        }
        return blocks;
    }

    /** Importer that stages values in memory, so the unit test needs no on-disk database. */
    private static final class InMemoryValueStoreImporter extends BootstrapImporter {
        InMemoryValueStoreImporter(BlockStore blockStore, TrieStore trieStore, BlockFactory blockFactory,
                                   DbKind dbKind, BootstrapDataProvider provider) {
            super(blockStore, trieStore, blockFactory, dbKind, provider);
        }

        @Override
        protected ValueStore openValueStore() {
            return new ValueStore(new HashMapDB(), null);
        }
    }
}
