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
package co.rsk.cli.tools;

import co.rsk.RskContext;
import co.rsk.config.RskSystemProperties;
import co.rsk.config.TestSystemProperties;
import co.rsk.crypto.Keccak256;
import co.rsk.db.StateRootHandler;
import co.rsk.db.StateRootsStoreImpl;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieDTO;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import co.rsk.util.NodeStopper;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.datasource.DbKind;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ByteArrayWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers {@link ValidateState} against a state trie held in a real {@link TrieStoreImpl} over an in-memory
 * key-value store, so a gap can be created by deleting an individual key.
 * <p>
 * Every expectation is the hash the test itself removed (or, for the long value, the keccak of the value
 * bytes the test itself wrote), never a hash re-derived by running the walk under test.
 * <p>
 * The fixture is asserted to actually contain what each case needs — a stored non-root node, a long value,
 * and at least one embedded child — because a trie that happened to contain none of a given shape would let
 * an implementation that mishandles that shape pass unnoticed.
 */
class ValidateStateTest {

    /** Longer than 32 bytes, so the trie stores it out of line, keyed by its hash. */
    private static final byte[] LONG_VALUE = "a state value long enough to be stored out of line".getBytes(StandardCharsets.UTF_8);
    private static final Keccak256 LONG_VALUE_HASH = new Keccak256(Keccak256Helper.keccak256(LONG_VALUE));

    @TempDir
    private Path tempDir;

    private HashMapDB db;
    private TrieStore trieStore;
    private Keccak256 stateRoot;

    @BeforeEach
    void buildState() {
        db = new HashMapDB().setClearOnClose(false);
        trieStore = new TrieStoreImpl(db);

        // enough short-valued keys to push the trie past a single level, so it holds both stored internal
        // nodes and small terminal nodes the store embeds into their parent
        Trie trie = new Trie(trieStore);
        for (int i = 0; i < 24; i++) {
            trie = trie.put("account-" + i, new byte[]{(byte) i, 0x0a, 0x0b, 0x0c});
        }
        trie = trie.put("account-with-a-long-value", LONG_VALUE);

        trieStore.save(trie);
        trieStore.flush();

        stateRoot = trie.getHash();
    }

    @Test
    void reportsCompleteStateAndExitsZeroWhenNothingIsMissing() {
        Result result = validate(stateRoot);

        assertTrue(result.output.contains("State is complete"), result.output);
        assertTrue(counter(result.output, "Trie nodes visited:") > 0, result.output);
        assertEquals(1, counter(result.output, "Long values visited:"), result.output);
        assertEquals(0, counter(result.output, "Gaps found:"), result.output);
        verify(result.stopper).stop(0);
    }

    @Test
    void reportsCompleteStateWhenTheTrieHoldsEmbeddedChildren() {
        // an embedded child is inlined in its parent and is deliberately not stored under its own hash, so
        // looking one up in the store reports a gap that is not there. This case only discriminates while
        // the fixture actually contains embedded children, which is asserted rather than assumed.
        Result result = validate(stateRoot);

        assertTrue(counter(result.output, "Embedded nodes visited:") > 0,
                "the fixture must contain embedded children for this case to mean anything:\n" + result.output);
        assertEquals(0, counter(result.output, "Gaps found:"), result.output);
        assertTrue(result.output.contains("State is complete"), result.output);
        verify(result.stopper).stop(0);
    }

    @Test
    void reportsTheMissingNodeAndExitsNonZeroWhenAStoredNodeIsAbsent() {
        List<Keccak256> storedNodes = storedNonRootNodeHashes();
        assertFalse(storedNodes.isEmpty(), "the fixture must contain a stored non-root node to remove");

        Keccak256 removed = storedNodes.get(0);
        db.delete(removed.getBytes());

        Result result = validate(stateRoot);

        assertTrue(result.output.contains("missing node: " + removed.toHexString()), result.output);
        assertTrue(counter(result.output, "Gaps found:") >= 1, result.output);
        assertTrue(result.output.contains("State is INCOMPLETE"), result.output);
        verify(result.stopper).stop(1);
    }

    @Test
    void reportsTheMissingLongValueAndExitsNonZeroWhenTheValueIsAbsent() {
        assertNotNull(db.get(LONG_VALUE_HASH.getBytes()), "the fixture must hold the long value to remove");
        db.delete(LONG_VALUE_HASH.getBytes());

        Result result = validate(stateRoot);

        assertTrue(result.output.contains("missing long value: " + LONG_VALUE_HASH.toHexString()), result.output);
        assertEquals(0, counter(result.output, "Long values visited:"), result.output);
        assertTrue(result.output.contains("State is INCOMPLETE"), result.output);
        verify(result.stopper).stop(1);
    }

    @Test
    void reportsAnUnresolvedRootWithoutWalkingWhenTheStateRootIsAbsent() {
        Keccak256 absentRoot = new Keccak256(Keccak256Helper.keccak256("a root that was never stored".getBytes(StandardCharsets.UTF_8)));

        Result result = validate(absentRoot);

        assertTrue(result.output.contains("State root does not resolve"), result.output);
        assertFalse(result.output.contains("Trie nodes visited:"),
                "an unresolved root must be reported without walking:\n" + result.output);
        assertTrue(result.output.contains("State is INCOMPLETE"), result.output);
        verify(result.stopper).stop(1);
    }

    @Test
    void reportsAnUnparseableBlockArgumentAsUsageRatherThanAMissingBlock() {
        Result result = validate(stateRoot, "not-a-number");

        assertTrue(result.output.contains("Invalid block: not-a-number"), result.output);
        assertFalse(result.output.contains("Block not found"),
                "an unparseable --block is a usage error, not a missing block:\n" + result.output);
        assertFalse(result.output.contains("Trie nodes visited:"),
                "a rejected argument must be reported without walking:\n" + result.output);
        verify(result.stopper).stop(1);
    }

    @Test
    void resolvesTheRequestedBlockByNumberRatherThanTheBestBlock() {
        // the default "best" covers the other cases; an explicit number takes the other resolveBlock branch
        Result result = validate(stateRoot, "42", trieStore, storedUnderNumber(42L));

        verify(result.blockStore).getChainBlockByNumber(42L);
        verify(result.blockStore, never()).getBestBlock();
        assertTrue(result.output.contains("State is complete"), result.output);
        verify(result.stopper).stop(0);
    }

    @Test
    void reportsBlockNotFoundWhenTheRequestedBlockNumberIsAbsent() {
        // a number no block is stored under: the counterpart of the unparseable-argument case, and the
        // reason the two messages are kept distinct
        Result result = validate(stateRoot, "42", trieStore, NOT_STORED);

        assertTrue(result.output.contains("Block not found: 42"), result.output);
        assertFalse(result.output.contains("Invalid block"),
                "a resolvable-but-absent block is not a usage error:\n" + result.output);
        assertFalse(result.output.contains("Trie nodes visited:"),
                "a missing block must be reported without walking:\n" + result.output);
        verify(result.stopper).stop(1);
    }

    @Test
    void listsAtMostTwentyGapsAndCountsTheRemainderWhenManyAreMissing() {
        // one report must stay readable on a badly incomplete state: the listing is capped while the total
        // is still counted in full. Long values are used because a missing one does not prune the walk, so
        // every gap planted here is actually reachable.
        int plantedGaps = 25; // MAX_REPORTED_GAPS is 20
        List<Keccak256> valueHashes = new ArrayList<>();

        Trie trie = new Trie(trieStore);
        for (int i = 0; i < plantedGaps; i++) {
            byte[] value = distinctLongValue(i);
            valueHashes.add(new Keccak256(Keccak256Helper.keccak256(value)));
            trie = trie.put("gap-account-" + i, value);
        }
        trieStore.save(trie);
        trieStore.flush();

        assertEquals(plantedGaps, valueHashes.stream().distinct().count(), "the planted long values must be distinct");
        for (Keccak256 valueHash : valueHashes) {
            assertNotNull(db.get(valueHash.getBytes()), "the fixture must hold every long value it removes");
            db.delete(valueHash.getBytes());
        }

        Result result = validate(trie.getHash());

        assertEquals(plantedGaps, counter(result.output, "Gaps found:"), result.output);
        assertEquals(20, occurrences(result.output, "missing long value: "), result.output);
        assertTrue(result.output.contains("... and " + (plantedGaps - 20) + " more"), result.output);
        assertTrue(result.output.contains("State is INCOMPLETE"), result.output);
        verify(result.stopper).stop(1);
    }

    @Test
    void reportsACorruptStoredNodeAsAGapRatherThanPropagatingTheFailure() {
        List<Keccak256> storedNodes = storedNonRootNodeHashes();
        assertFalse(storedNodes.isEmpty(), "the fixture must contain a stored non-root node to corrupt");

        Keccak256 corrupted = storedNodes.get(0);
        // the bytes filed under the hash are present but no longer a decodable node message — the flags byte
        // declares a non-embedded left child and the buffer ends before its 32-byte hash — so retrieving it
        // throws instead of returning empty. A corrupt store is exactly the condition this tool exists to
        // report, so the failure must come back as a gap and not escape the walk.
        db.put(corrupted.getBytes(), new byte[]{0x08});

        Result result = validate(stateRoot);

        assertTrue(result.output.contains("missing node: " + corrupted.toHexString()), result.output);
        assertTrue(counter(result.output, "Gaps found:") >= 1, result.output);
        assertTrue(result.output.contains("State is INCOMPLETE"), result.output);
        verify(result.stopper).stop(1);
    }

    @Test
    void reportsALongValueAsAGapWhenTheStoreFailsReadingIt() {
        // deleting the value makes the read return null; a store that throws instead (an unreadable or
        // corrupt datasource) must be reported the same way rather than aborting the run
        TrieStore failingStore = new ValueReadFailingTrieStore(trieStore, LONG_VALUE_HASH);

        Result result = validate(stateRoot, "best", failingStore, AS_BEST_BLOCK);

        assertTrue(result.output.contains("missing long value: " + LONG_VALUE_HASH.toHexString()), result.output);
        assertEquals(0, counter(result.output, "Long values visited:"), result.output);
        assertTrue(result.output.contains("State is INCOMPLETE"), result.output);
        verify(result.stopper).stop(1);
    }

    @Test
    void isInstantiableThroughTheNoArgConstructorTheLauncherUsesReflectively() throws Exception {
        // CliToolRskContextAware.create() builds the tool with Class.newInstance(), so the documented
        // `java -cp rsk.jar co.rsk.cli.tools.ValidateState` entry point needs a public no-arg constructor.
        // Removing or narrowing it still compiles, and only fails once an operator runs the tool.
        Constructor<ValidateState> constructor = ValidateState.class.getConstructor();

        assertTrue(Modifier.isPublic(constructor.getModifiers()), "the launcher instantiates this reflectively");
        assertNotNull(constructor.newInstance());
    }

    @Test
    void reportsThroughTheDefaultPrinterWhenBuiltTheWayTheLauncherBuildsIt() {
        // every other case injects a printer to read the report back; this one runs the tool as an operator
        // gets it — built through the no-arg constructor, so the report goes to the default logging printer
        BlockStore blockStore = blockStoreHolding(stateRoot, AS_BEST_BLOCK);
        NodeStopper stopper = mock(NodeStopper.class);

        ValidateState tool = new ValidateState();
        tool.execute(new String[]{"--block", "best"}, () -> contextFor(blockStore, trieStore), stopper);

        verify(stopper).stop(0);
    }

    private static final BiConsumer<BlockStore, Block> AS_BEST_BLOCK =
            (blockStore, block) -> when(blockStore.getBestBlock()).thenReturn(block);

    private static final BiConsumer<BlockStore, Block> NOT_STORED = (blockStore, block) -> { /* nothing stored */ };

    private static BiConsumer<BlockStore, Block> storedUnderNumber(long number) {
        return (blockStore, block) -> when(blockStore.getChainBlockByNumber(number)).thenReturn(block);
    }

    /** Longer than 32 bytes so it is stored out of line, and distinct per index so its hash is too. */
    private static byte[] distinctLongValue(int index) {
        return ("a state value long enough to be stored out of line, number " + index).getBytes(StandardCharsets.UTF_8);
    }

    private Result validate(Keccak256 root) {
        return validate(root, "best");
    }

    private Result validate(Keccak256 root, String blockArg) {
        return validate(root, blockArg, trieStore, AS_BEST_BLOCK);
    }

    private Result validate(Keccak256 root, String blockArg, TrieStore store, BiConsumer<BlockStore, Block> stubBlockLookup) {
        BlockStore blockStore = blockStoreHolding(root, stubBlockLookup);

        NodeStopper stopper = mock(NodeStopper.class);
        StringBuilder output = new StringBuilder();

        ValidateState tool = new ValidateState(line -> output.append(line).append('\n'));
        tool.execute(new String[]{"--block", blockArg}, () -> contextFor(blockStore, store), stopper);

        return new Result(output.toString(), stopper, blockStore);
    }

    private BlockStore blockStoreHolding(Keccak256 root, BiConsumer<BlockStore, Block> stubBlockLookup) {
        BlockStore blockStore = mock(BlockStore.class);
        stubBlockLookup.accept(blockStore, blockWithStateRoot(root));

        return blockStore;
    }

    private RskContext contextFor(BlockStore blockStore, TrieStore store) {
        RskSystemProperties rskSystemProperties = mock(RskSystemProperties.class);
        doReturn(tempDir.toString()).when(rskSystemProperties).databaseDir();
        doReturn(DbKind.LEVEL_DB).when(rskSystemProperties).databaseKind();

        RskContext rskContext = mock(RskContext.class);
        doReturn(blockStore).when(rskContext).getBlockStore();
        doReturn(store).when(rskContext).getTrieStore();
        doReturn(stateRootHandler()).when(rskContext).getStateRootHandler();
        doReturn(rskSystemProperties).when(rskContext).getRskSystemProperties();

        return rskContext;
    }

    private static StateRootHandler stateRootHandler() {
        return new StateRootHandler(new TestSystemProperties().getActivationConfig(), new StateRootsStoreImpl(new HashMapDB()));
    }

    private static Block blockWithStateRoot(Keccak256 root) {
        BlockHeader header = mock(BlockHeader.class);
        when(header.getNumber()).thenReturn(1L);
        when(header.getStateRoot()).thenReturn(root.getBytes());

        Block block = mock(Block.class);
        when(block.getNumber()).thenReturn(1L);
        when(block.getHash()).thenReturn(new Keccak256(Keccak256Helper.keccak256("a block".getBytes(StandardCharsets.UTF_8))));
        when(block.getHeader()).thenReturn(header);

        return block;
    }

    /**
     * Every key the store holds is a 32-byte hash of either a node message or a long value, so removing the
     * root and the one known long value leaves exactly the stored non-root nodes. Sorted so the case removes
     * the same node on every run.
     */
    private List<Keccak256> storedNonRootNodeHashes() {
        return db.keys().stream()
                .map(ByteArrayWrapper::getData)
                .map(Keccak256::new)
                .filter(hash -> !hash.equals(stateRoot))
                .filter(hash -> !hash.equals(LONG_VALUE_HASH))
                .sorted(Comparator.comparing(Keccak256::toHexString))
                .toList();
    }

    private static long counter(String output, String label) {
        Matcher matcher = Pattern.compile("^" + Pattern.quote(label) + " (\\d+)$", Pattern.MULTILINE).matcher(output);
        assertTrue(matcher.find(), "no '" + label + "' line in:\n" + output);

        return Long.parseLong(matcher.group(1));
    }

    private static int occurrences(String output, String needle) {
        int count = 0;
        for (int at = output.indexOf(needle); at >= 0; at = output.indexOf(needle, at + needle.length())) {
            count++;
        }

        return count;
    }

    /**
         * Delegates to a real store but fails reading one designated long value, standing in for a datasource
         * that throws rather than reporting the key as absent.
         */
        private record ValueReadFailingTrieStore(TrieStore delegate, Keccak256 failingValueHash) implements TrieStore {

        @Override
            public byte[] retrieveValue(byte[] hash) {
                if (Arrays.equals(failingValueHash.getBytes(), hash)) {
                    throw new IllegalStateException("cannot read value " + failingValueHash.toHexString());
                }

                return delegate.retrieveValue(hash);
            }

            @Override
            public Optional<Trie> retrieve(byte[] hash) {
                return delegate.retrieve(hash);
            }

            @Override
            public void save(Trie trie) {
                delegate.save(trie);
            }

            @Override
            public void saveValue(byte[] value) {
                delegate.saveValue(value);
            }

            @Override
            public Optional<TrieDTO> retrieveDTO(byte[] hash) {
                return delegate.retrieveDTO(hash);
            }

            @Override
            public void saveDTO(TrieDTO trieDTO) {
                delegate.saveDTO(trieDTO);
            }

            @Override
            public void flush() {
                delegate.flush();
            }

            @Override
            public void dispose() {
                delegate.dispose();
            }
        }

    private static final class Result {
        private final String output;
        private final NodeStopper stopper;
        private final BlockStore blockStore;

        private Result(String output, NodeStopper stopper, BlockStore blockStore) {
            this.output = output;
            this.stopper = stopper;
            this.blockStore = blockStore;
        }
    }
}
