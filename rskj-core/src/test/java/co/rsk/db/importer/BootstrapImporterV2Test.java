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
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

        // destination: real trie store (state assertions), mocked block plumbing (block decode/save).
        Wired wired = wire(v2, "bootstrap-data.bin");
        wired.importer().importData();

        // blocks were decoded and saved
        verify(wired.blockFactory(), atLeastOnce()).decodeBlock(any());
        verify(wired.blockStore(), atLeastOnce()).saveBlock(any(), any(), anyBoolean());

        // the state root is retrievable and every key/value round-trips intact
        Trie reconstructed = wired.destinationStore().retrieve(stateRoot)
                .orElseThrow(() -> new AssertionError("state root missing after import"));
        assertArrayEquals(stateRoot, reconstructed.getHash().getBytes(), "state root mismatch");
        for (Map.Entry<byte[], byte[]> e : expected.entrySet()) {
            assertArrayEquals(e.getValue(), reconstructed.get(e.getKey()),
                    "value mismatch for key " + new String(e.getKey(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void importsV2SkippingAnUnknownSectionReconstructingState() throws IOException {
        // a v2 file that carries an unknown/reserved section (a future optional section, e.g. a metadata
        // manifest) ahead of the data sections: an older reader must skip it and still reconstruct state.
        StateFixture fixture = buildState();
        List<byte[]> blocks = syntheticBlockElements();
        List<byte[]> values = collectValueElements(fixture.store, fixture.stateRoot);
        List<byte[]> nodes = collectNodeElements(fixture.store, fixture.stateRoot);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        out.write(BootstrapV2Format.magic());
        out.writeByte(BootstrapV2Format.VERSION);
        // a section tag this reader does not handle (reserved for a future manifest); must be skipped
        writeSection(out, BootstrapV2Format.TAG_MANIFEST,
                List.of(RLP.encodeElement("future".getBytes(StandardCharsets.UTF_8))), 1024);
        writeSection(out, BootstrapV2Format.TAG_BLOCKS, blocks, 1024);
        writeSection(out, BootstrapV2Format.TAG_VALUES, values, 1024);
        writeSection(out, BootstrapV2Format.TAG_NODES, nodes, 1024);
        out.writeByte(BootstrapV2Format.TAG_END);
        out.flush();

        Wired wired = wire(bos.toByteArray(), "with-unknown-section.bin");
        wired.importer().importData();

        Trie reconstructed = wired.destinationStore().retrieve(fixture.stateRoot)
                .orElseThrow(() -> new AssertionError("state root missing after import"));
        assertArrayEquals(fixture.stateRoot, reconstructed.getHash().getBytes());
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

        Wired wired = wire(v2, "bootstrap-empty-values.bin");
        wired.importer().importData();

        Trie reconstructed = wired.destinationStore().retrieve(stateRoot)
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
        // A chunk whose declared length is far larger than the whole file, yet still under the per-chunk
        // ceiling (MAX_CHUNK_BYTES). It must be rejected against the bytes actually remaining in the
        // file instead of being eagerly allocated as a multi-hundred-MB byte[].
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        out.write(BootstrapV2Format.magic());
        out.writeByte(BootstrapV2Format.VERSION);
        out.writeByte(BootstrapV2Format.TAG_BLOCKS);
        out.writeLong(10_000_000L); // corrupt length; no payload follows — the file ends here
        out.flush();

        BootstrapImporter importer = newImporter(bos.toByteArray(), "corrupt-length.bin");

        BootstrapImportException ex = assertThrows(BootstrapImportException.class, importer::importData);
        assertTrue(ex.getMessage().toLowerCase().contains("exceeds"), ex.getMessage());
    }

    @Test
    void rejectsChunkLengthExceedingTheFormatContractCeiling() throws IOException {
        // a declared chunk length just past MAX_CHUNK_LEN (the format-contract ceiling, independent of the
        // exporter's CHUNK_MAX tuning knob) must be rejected up front, before any allocation.
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        out.write(BootstrapV2Format.magic());
        out.writeByte(BootstrapV2Format.VERSION);
        out.writeByte(BootstrapV2Format.TAG_BLOCKS);
        out.writeLong(BootstrapV2Format.MAX_CHUNK_LEN + 1); // over the contract ceiling; no payload follows
        out.flush();

        BootstrapImporter importer = newImporter(bos.toByteArray(), "over-ceiling.bin");

        BootstrapImportException ex = assertThrows(BootstrapImportException.class, importer::importData);
        assertTrue(ex.getMessage().toLowerCase().contains("out of range"), ex.getMessage());
    }

    @Test
    void formatContractChunkCeilingCoversAFullFlushChunk() {
        // the reader's contract ceiling must admit at least one full exporter flush chunk, otherwise a
        // legitimately-produced snapshot could be rejected.
        assertTrue(BootstrapV2Format.MAX_CHUNK_LEN >= BootstrapV2Format.CHUNK_MAX,
                "MAX_CHUNK_LEN must be >= CHUNK_MAX");
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

    @Test
    void rejectsV2WithNodesSectionBeforeValuesSection() throws IOException {
        // A structurally malformed v2 file whose nodes section precedes its values section, violating the
        // co-location invariant (values must come before the nodes that reference them). Node saving would
        // otherwise fail deep inside the trie only for nodes that happen to reference a long value; the
        // section-order guard rejects the mis-ordered file up front, deterministically, regardless of which
        // nodes reference long values.
        StateFixture fixture = buildState();
        List<byte[]> blocks = syntheticBlockElements();
        List<byte[]> values = collectValueElements(fixture.store, fixture.stateRoot);
        List<byte[]> nodes = collectNodeElements(fixture.store, fixture.stateRoot);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        out.write(BootstrapV2Format.magic());
        out.writeByte(BootstrapV2Format.VERSION);
        writeSection(out, BootstrapV2Format.TAG_BLOCKS, blocks, 1024);
        // deliberately mis-ordered: nodes written BEFORE values
        writeSection(out, BootstrapV2Format.TAG_NODES, nodes, 1024);
        writeSection(out, BootstrapV2Format.TAG_VALUES, values, 1024);
        out.writeByte(BootstrapV2Format.TAG_END);
        out.flush();

        BootstrapImporter importer = newImporter(bos.toByteArray(), "nodes-before-values.bin");

        BootstrapImportException ex = assertThrows(BootstrapImportException.class, importer::importData);
        assertTrue(ex.getMessage().toLowerCase().contains("order"), ex.getMessage());
    }

    @Test
    void failsWithActionableErrorWhenBlockElementIsEmpty() throws IOException {
        // A corrupt blocks section carrying an empty element (getRLPData() == null) would otherwise surface
        // as an IllegalArgumentException from RLP.decodeList. It must be an actionable import error, like the
        // values and nodes sections.
        StateFixture fixture = buildState();
        List<byte[]> blocks = new ArrayList<>();
        blocks.add(RLP.encodeElement(new byte[0])); // empty block element -> getRLPData() == null
        byte[] v2 = assembleV2(1024, blocks,
                collectNodeElements(fixture.store, fixture.stateRoot),
                collectValueElements(fixture.store, fixture.stateRoot));

        BootstrapImporter importer = newImporter(v2, "empty-block.bin");

        BootstrapImportException ex = assertThrows(BootstrapImportException.class, importer::importData);
        assertTrue(ex.getMessage().toLowerCase().contains("blocks"), ex.getMessage());
    }

    @Test
    void failsWithActionableErrorWhenValueElementIsEmpty() throws IOException {
        // RLPElement#getRLPData() returns null for an empty element; a corrupt values section carrying one
        // would otherwise NPE deep inside saveValue. It must surface as an actionable import error.
        StateFixture fixture = buildState();
        List<byte[]> values = new ArrayList<>();
        values.add(RLP.encodeElement(new byte[0])); // empty value element -> getRLPData() == null
        byte[] v2 = assembleV2(1024,
                syntheticBlockElements(), collectNodeElements(fixture.store, fixture.stateRoot), values);

        BootstrapImporter importer = newImporter(v2, "empty-value.bin");

        BootstrapImportException ex = assertThrows(BootstrapImportException.class, importer::importData);
        assertTrue(ex.getMessage().toLowerCase().contains("value"), ex.getMessage());
    }

    @Test
    void failsWithActionableErrorWhenNodeElementIsEmpty() throws IOException {
        // A corrupt nodes section carrying an empty element (getRLPData() == null) would otherwise pass null
        // into Trie.fromMessage and surface as an unhelpful NPE/IAE. It must be an actionable import error.
        StateFixture fixture = buildState();
        List<byte[]> nodes = new ArrayList<>();
        nodes.add(RLP.encodeElement(new byte[0])); // empty node element -> getRLPData() == null
        byte[] v2 = assembleV2(1024,
                syntheticBlockElements(), nodes, collectValueElements(fixture.store, fixture.stateRoot));

        BootstrapImporter importer = newImporter(v2, "empty-node.bin");

        BootstrapImportException ex = assertThrows(BootstrapImportException.class, importer::importData);
        assertTrue(ex.getMessage().toLowerCase().contains("empty"), ex.getMessage());
    }

    @Test
    void failsWithActionableErrorWhenNodeElementIsNotAValidNodeMessage() throws IOException {
        // a nodes element that is well-formed RLP and non-empty, but not a decodable trie-node message: its
        // flags byte declares a (non-embedded) left child, yet the buffer is truncated before the 32-byte
        // child hash, so Trie.fromMessage reads past the end. That decode failure runs outside saveNode's
        // translation and would otherwise escape raw; it must surface as an actionable BootstrapImportException.
        StateFixture fixture = buildState();
        List<byte[]> nodes = new ArrayList<>();
        nodes.add(RLP.encodeElement(new byte[]{0x08})); // flags: leftNodePresent, non-embedded; no hash follows
        byte[] v2 = assembleV2(1024,
                syntheticBlockElements(), nodes, collectValueElements(fixture.store, fixture.stateRoot));

        BootstrapImporter importer = newImporter(v2, "malformed-node.bin");

        assertThrows(BootstrapImportException.class, importer::importData);
    }

    @Test
    void failsWithActionableErrorWhenChunkIsNotDecodableRlp() throws IOException {
        // a nodes section carrying a single chunk whose bytes are not decodable as RLP elements: RLP.decode2
        // would otherwise throw a raw RLPException. It must surface as an actionable BootstrapImportException.
        StateFixture fixture = buildState();
        byte[] malformedChunk = new byte[]{(byte) 0x83, 0x01, 0x02}; // RLP header says 3-byte string, only 2 follow

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        out.write(BootstrapV2Format.magic());
        out.writeByte(BootstrapV2Format.VERSION);
        writeSection(out, BootstrapV2Format.TAG_BLOCKS, syntheticBlockElements(), 1024);
        writeSection(out, BootstrapV2Format.TAG_VALUES,
                collectValueElements(fixture.store, fixture.stateRoot), 1024);
        // a hand-written nodes section with one malformed chunk (bypasses writeSection's whole-element framing)
        out.writeByte(BootstrapV2Format.TAG_NODES);
        out.writeLong(malformedChunk.length);
        out.write(malformedChunk);
        out.writeLong(0L); // end-of-section sentinel
        out.writeByte(BootstrapV2Format.TAG_END);
        out.flush();

        BootstrapImporter importer = newImporter(bos.toByteArray(), "malformed-chunk.bin");

        assertThrows(BootstrapImportException.class, importer::importData);
    }

    @Test
    void rejectsUnrecognizedFirstByteAsNeitherV1NorV2() throws IOException {
        // First byte is neither the v2 magic ('R') nor a v1 RLP list prefix (0xc0+): a corrupt or wrong file
        // must be rejected up front with a clear error, not sent down the v1 path to fail with an opaque
        // RLP error later.
        byte[] bogus = new byte[]{0x42, 0x00, 0x01}; // 'B', not v2 magic and not a v1 list prefix
        BootstrapImporter importer = newImporter(bogus, "bogus-format.bin");

        BootstrapImportException ex = assertThrows(BootstrapImportException.class, importer::importData);
        assertTrue(ex.getMessage().toLowerCase().contains("unrecognized"), ex.getMessage());
    }

    @Test
    void rejectsV2WithTrailingBytesAfterEndMarker() throws IOException {
        // A structurally complete v2 file with a stray byte appended after the end-of-sections marker. The
        // v2 format is canonical — nothing may follow TAG_END — so trailing bytes (a tampered, truncated,
        // or accidentally concatenated file) must be rejected, not silently ignored.
        StateFixture fixture = buildState();
        byte[] wellFormed = writeV2(fixture.store, fixture.stateRoot, 1024);
        byte[] withTrailing = Arrays.copyOf(wellFormed, wellFormed.length + 1);
        withTrailing[wellFormed.length] = 0x42; // stray trailing byte after TAG_END

        BootstrapImporter importer = newImporter(withTrailing, "trailing-bytes.bin");

        BootstrapImportException ex = assertThrows(BootstrapImportException.class, importer::importData);
        assertTrue(ex.getMessage().toLowerCase().contains("trailing"), ex.getMessage());
    }

    @Test
    void rejectsAnEmptyBootstrapDataFile() throws IOException {
        // a zero-byte bootstrap-data.bin (an interrupted download or a failed unzip) has no first byte to
        // dispatch on. It must be named as empty rather than guessed at as one format or the other.
        BootstrapImporter importer = newImporter(new byte[0], "empty.bin");

        BootstrapImportException ex = assertThrows(BootstrapImportException.class, importer::importData);
        assertTrue(ex.getMessage().toLowerCase().contains("empty"), ex.getMessage());
    }

    @Test
    void rejectsV2WithACorruptMagic() throws IOException {
        // the first byte is the v2 magic's 'R', so the file is dispatched down the v2 path, but the rest of
        // the magic is wrong. Without the full-magic check a corrupt file would be read as v2 and fail later
        // with an opaque error about whatever the following bytes happened to look like.
        byte[] corruptMagic = BootstrapV2Format.magic();
        corruptMagic[corruptMagic.length - 1] ^= 0x01; // keep the leading 'R', break the tail

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        out.write(corruptMagic);
        out.writeByte(BootstrapV2Format.VERSION);
        out.flush();

        BootstrapImporter importer = newImporter(bos.toByteArray(), "corrupt-magic.bin");

        BootstrapImportException ex = assertThrows(BootstrapImportException.class, importer::importData);
        assertTrue(ex.getMessage().toLowerCase().contains("magic"), ex.getMessage());
    }

    @Test
    void rejectsAnUnsupportedV2Version() throws IOException {
        // a snapshot written by a newer exporter: the framing may have changed, so an older reader must
        // refuse it by version rather than misread its sections.
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        out.write(BootstrapV2Format.magic());
        out.writeByte(BootstrapV2Format.VERSION + 1);
        out.flush();

        BootstrapImporter importer = newImporter(bos.toByteArray(), "future-version.bin");

        BootstrapImportException ex = assertThrows(BootstrapImportException.class, importer::importData);
        assertTrue(ex.getMessage().toLowerCase().contains("version"), ex.getMessage());
    }

    @Test
    void rejectsV2TruncatedBeforeTheEndOfSectionsMarker() throws IOException {
        // every section is complete and well-formed, but the file simply stops: the end-of-sections marker
        // never arrives. That is a truncated download, and the counterpart of the trailing-bytes case — the
        // reader must not treat "ran out of file" as a successfully finished import.
        StateFixture fixture = buildState();

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        out.write(BootstrapV2Format.magic());
        out.writeByte(BootstrapV2Format.VERSION);
        writeSection(out, BootstrapV2Format.TAG_BLOCKS, syntheticBlockElements(), 1024);
        writeSection(out, BootstrapV2Format.TAG_VALUES,
                collectValueElements(fixture.store, fixture.stateRoot), 1024);
        writeSection(out, BootstrapV2Format.TAG_NODES,
                collectNodeElements(fixture.store, fixture.stateRoot), 1024);
        // no TAG_END byte: the file ends here
        out.flush();

        BootstrapImporter importer = newImporter(bos.toByteArray(), "truncated-no-end-marker.bin");

        BootstrapImportException ex = assertThrows(BootstrapImportException.class, importer::importData);
        assertTrue(ex.getMessage().toLowerCase().contains("end-of-sections"), ex.getMessage());
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

    /** An importer wired over a temp v2 file, exposing the mocks/store the state-asserting tests inspect. */
    private record Wired(BootstrapImporter importer, TrieStore destinationStore,
                         BlockStore blockStore, BlockFactory blockFactory) {
    }

    /** Writes {@code v2} to a temp file and wires an importer with mocked block plumbing over it. */
    private Wired wire(byte[] v2, String fileName) throws IOException {
        Path binPath = tempDir.resolve(fileName);
        Files.write(binPath, v2);

        TrieStore destinationStore = new TrieStoreImpl(new HashMapDB());
        BlockStore blockStore = mock(BlockStore.class);
        BlockFactory blockFactory = mock(BlockFactory.class);
        when(blockFactory.decodeBlock(any())).thenReturn(mock(Block.class));
        BootstrapDataProvider provider = mock(BootstrapDataProvider.class);
        when(provider.getBootstrapDataPath()).thenReturn(binPath);

        BootstrapImporter importer = new BootstrapImporter(
                blockStore, destinationStore, blockFactory, provider);
        return new Wired(importer, destinationStore, blockStore, blockFactory);
    }

    /** Convenience for the fail-fast tests that only need the importer, not the wired store/mocks. */
    private BootstrapImporter newImporter(byte[] v2, String fileName) throws IOException {
        return wire(v2, fileName).importer();
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
        out.write(BootstrapV2Format.magic());
        out.writeByte(BootstrapV2Format.VERSION);
        if (blocks != null) {
            writeSection(out, BootstrapV2Format.TAG_BLOCKS, blocks, chunkMax);
        }
        // values are co-located BEFORE the nodes that reference them, so the importer can apply them in a
        // single streaming pass (values into the destination store, then nodes resolve them at save time).
        if (values != null) {
            writeSection(out, BootstrapV2Format.TAG_VALUES, values, chunkMax);
        }
        if (nodes != null) {
            writeSection(out, BootstrapV2Format.TAG_NODES, nodes, chunkMax);
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

    /**
     * Mirrors {@code FileExporter#streamStateElements} node emission, reproducing the v1 wire order:
     * the root element is written explicitly first (so it is emitted even when embeddable), then a full
     * in-order traversal appends every non-embeddable node. For a non-embeddable root the traversal
     * re-yields the root, so its element is emitted twice — a deliberate v1 byte-parity property that is
     * harmless on import, where saving a node by its hash is idempotent.
     */
    private static List<byte[]> collectNodeElements(TrieStore store, byte[] stateRoot) {
        Trie root = store.retrieve(stateRoot).orElseThrow(() -> new AssertionError("missing root"));
        List<byte[]> nodes = new ArrayList<>();
        nodes.add(RLP.encodeElement(root.toMessage())); // root first (even if embeddable)
        Iterator<IterationElement> it = root.getInOrderIterator();
        while (it.hasNext()) {
            Trie node = it.next().getNode();
            if (node.isEmbeddable()) {
                continue;
            }
            // a non-embeddable root is re-emitted here (v1 parity); idempotent on import
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
}
