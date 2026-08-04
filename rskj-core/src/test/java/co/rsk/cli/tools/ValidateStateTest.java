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

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
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

    private Result validate(Keccak256 root) {
        return validate(root, "best");
    }

    private Result validate(Keccak256 root, String blockArg) {
        Block bestBlock = blockWithStateRoot(root);

        BlockStore blockStore = mock(BlockStore.class);
        when(blockStore.getBestBlock()).thenReturn(bestBlock);

        RskSystemProperties rskSystemProperties = mock(RskSystemProperties.class);
        doReturn(tempDir.toString()).when(rskSystemProperties).databaseDir();
        doReturn(DbKind.LEVEL_DB).when(rskSystemProperties).databaseKind();

        RskContext rskContext = mock(RskContext.class);
        doReturn(blockStore).when(rskContext).getBlockStore();
        doReturn(trieStore).when(rskContext).getTrieStore();
        doReturn(stateRootHandler()).when(rskContext).getStateRootHandler();
        doReturn(rskSystemProperties).when(rskContext).getRskSystemProperties();

        NodeStopper stopper = mock(NodeStopper.class);
        StringBuilder output = new StringBuilder();

        ValidateState tool = new ValidateState(line -> output.append(line).append('\n'));
        tool.execute(new String[]{"--block", blockArg}, () -> rskContext, stopper);

        return new Result(output.toString(), stopper);
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
                .collect(Collectors.toList());
    }

    private static long counter(String output, String label) {
        Matcher matcher = Pattern.compile("^" + Pattern.quote(label) + " (\\d+)$", Pattern.MULTILINE).matcher(output);
        assertTrue(matcher.find(), "no '" + label + "' line in:\n" + output);

        return Long.parseLong(matcher.group(1));
    }

    private static final class Result {
        private final String output;
        private final NodeStopper stopper;

        private Result(String output, NodeStopper stopper) {
            this.output = output;
            this.stopper = stopper;
        }
    }
}
