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
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.db.StateRootHandler;
import co.rsk.db.StateRootsStoreImpl;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import co.rsk.util.NodeStopper;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.DelegationCodeResolver;
import org.ethereum.core.Repository;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.datasource.DbKind;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.DataWord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
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
 * Covers {@link ScanStateShape} against a state trie built through a real {@link MutableRepository},
 * so the keys the scan walks are the ones {@code TrieKeyMapper} actually produces.
 * <p>
 * The load-bearing case is {@link #reportsAnAccountWhoseCodeHasTheExactDelegationShape()}: a fixture that
 * genuinely carries delegation-shaped code, which the scan must find. Without it the other cases only show
 * that the tool reports nothing, which a tool that never reports anything would also satisfy.
 */
class ScanStateShapeTest {

    private static final RskAddress DELEGATED = new RskAddress("1111111111111111111111111111111111111111");
    private static final RskAddress TARGET = new RskAddress("2222222222222222222222222222222222222222");
    private static final RskAddress EF_SHORT = new RskAddress("3333333333333333333333333333333333333333");
    private static final RskAddress EF_LONG = new RskAddress("4444444444444444444444444444444444444444");
    private static final RskAddress ORDINARY = new RskAddress("5555555555555555555555555555555555555555");
    private static final RskAddress EOA = new RskAddress("6666666666666666666666666666666666666666");
    private static final RskAddress STORAGE_DECOY = new RskAddress("7777777777777777777777777777777777777777");

    private static final byte[] DELEGATION_CODE = DelegationCodeResolver.createDelegatedCode(TARGET);

    @TempDir
    private Path tempDir;

    private HashMapDB db;
    private TrieStore trieStore;

    /** Sensitivity probe: the scan must find code that is exactly the delegation designator shape. */
    @Test
    void reportsAnAccountWhoseCodeHasTheExactDelegationShape() {
        Keccak256 root = state(repository -> {
            repository.saveCode(DELEGATED, DELEGATION_CODE);
            repository.saveCode(ORDINARY, ordinaryCode());
        });

        Result result = scan(root);

        assertEquals(1, summaryField(result.records, "delegationShaped"), result.report());
        assertTrue(result.records.stream().anyMatch(line -> line.contains("\"kind\":\"account\"")
                        && line.contains("\"address\":\"" + DELEGATED.toHexString() + "\"")
                        && line.contains("\"delegationShape\":true")
                        && line.contains("\"delegatesTo\":\"" + TARGET.toHexString() + "\"")),
                result.report());
        verify(result.stopper).stop(1);
    }

    /**
     * The exact shape is 23 bytes, so 0xEF-prefixed code of any other length is the weaker signal and must be
     * counted apart from it rather than folded in.
     */
    @Test
    void countsEfPrefixedCodeSeparatelyFromTheExactShape() {
        byte[] oneByteShort = Arrays.copyOf(DELEGATION_CODE, DELEGATION_CODE.length - 1);
        byte[] oneByteLong = Arrays.copyOf(DELEGATION_CODE, DELEGATION_CODE.length + 1);

        Keccak256 root = state(repository -> {
            repository.saveCode(EF_SHORT, oneByteShort);
            repository.saveCode(EF_LONG, oneByteLong);
            repository.saveCode(ORDINARY, ordinaryCode());
        });

        Result result = scan(root);

        assertEquals(0, summaryField(result.records, "delegationShaped"), result.report());
        assertEquals(2, summaryField(result.records, "efPrefixed"), result.report());
        assertTrue(result.records.stream().anyMatch(line -> line.contains(EF_SHORT.toHexString())
                && line.contains("\"delegationShape\":false") && line.contains("\"efPrefixed\":true")), result.report());
        assertTrue(result.records.stream().anyMatch(line -> line.contains(EF_LONG.toHexString())
                && line.contains("\"delegationShape\":false") && line.contains("\"efPrefixed\":true")), result.report());
        verify(result.stopper).stop(0);
    }

    /**
     * Code longer than 32 bytes is stored out of line, keyed by its hash, so its first byte is only visible
     * after a second store read. The weaker signal has to see those too.
     */
    @Test
    void readsCodeThatTheTrieStoresOutOfLine() {
        byte[] longEfCode = new byte[64];
        longEfCode[0] = DelegationCodeResolver.DELEGATION_PREFIX[0];

        Keccak256 root = state(repository -> repository.saveCode(EF_LONG, longEfCode));

        Result result = scan(root);

        assertEquals(1, summaryField(result.records, "efPrefixed"), result.report());
        assertTrue(result.records.stream().anyMatch(line -> line.contains(EF_LONG.toHexString())
                && line.contains("\"codeLength\":64")), result.report());
        verify(result.stopper).stop(0);
    }

    @Test
    void reportsNothingAndExitsZeroWhenNoAccountCarriesEfPrefixedCode() {
        Keccak256 root = state(repository -> {
            repository.saveCode(ORDINARY, ordinaryCode());
            repository.saveCode(TARGET, ordinaryCode());
            repository.createAccount(EOA);
            repository.addBalance(EOA, Coin.valueOf(1000));
        });

        Result result = scan(root);

        assertEquals(0, summaryField(result.records, "delegationShaped"), result.report());
        assertEquals(0, summaryField(result.records, "efPrefixed"), result.report());
        assertEquals(2, summaryField(result.records, "accountsWithCode"),
                "the fixture must hold code for this case to discriminate: " + result.report());
        assertEquals(3, summaryField(result.records, "accounts"), result.report());
        assertFalse(result.records.stream().anyMatch(line -> line.contains("\"kind\":\"account\"")), result.report());
        verify(result.stopper).stop(0);
    }

    /**
     * Storage values live under the same account key as the code, one prefix byte apart. A storage cell whose
     * bytes spell a delegation designator is not code and must not be reported as one.
     */
    @Test
    void doesNotMistakeAStorageValueForCode() {
        Keccak256 root = state(repository -> {
            repository.addStorageBytes(STORAGE_DECOY, DataWord.ONE, DELEGATION_CODE);
            repository.saveCode(ORDINARY, ordinaryCode());
        });

        Result result = scan(root);

        assertEquals(0, summaryField(result.records, "delegationShaped"), result.report());
        assertEquals(0, summaryField(result.records, "efPrefixed"), result.report());
        assertEquals(1, summaryField(result.records, "accountsWithCode"), result.report());
        verify(result.stopper).stop(0);
    }

    /**
     * A scan that could not read part of the trie has not answered the question, so a gap is reported and the
     * run exits non-zero even though it found nothing.
     */
    @Test
    void reportsAGapAndExitsNonZeroWhenAStoredNodeIsAbsent() {
        Keccak256 root = state(repository -> {
            for (int i = 0; i < 24; i++) {
                repository.saveCode(addressNumbered(i), ordinaryCode());
            }
        });

        List<Keccak256> storedNodes = storedNonRootNodeHashes(root);
        assertFalse(storedNodes.isEmpty(), "the fixture must contain a stored non-root node to remove");
        Keccak256 removed = storedNodes.get(0);
        db.delete(removed.getBytes());

        Result result = scan(root);

        assertTrue(result.records.stream().anyMatch(line -> line.contains("\"kind\":\"gap\"")
                && line.contains(removed.toHexString())), result.report());
        assertTrue(summaryField(result.records, "gaps") >= 1, result.report());
        assertTrue(result.records.stream().anyMatch(line -> line.contains("\"complete\":false")), result.report());
        verify(result.stopper).stop(1);
    }

    @Test
    void reportsAnUnresolvedStateRootAsAGapRatherThanAnEmptyScan() {
        Keccak256 root = state(repository -> repository.saveCode(ORDINARY, ordinaryCode()));
        Keccak256 absentRoot = new Keccak256(Keccak256Helper.keccak256("never stored".getBytes(StandardCharsets.UTF_8)));

        Result result = scan(absentRoot);

        assertNotNull(root);
        assertTrue(result.records.stream().anyMatch(line -> line.contains("\"kind\":\"gap\"")
                && line.contains("\"of\":\"stateRoot\"")), result.report());
        assertEquals(0, summaryField(result.records, "accounts"), result.report());
        verify(result.stopper).stop(1);
    }

    @Test
    void resolvesTheRequestedBlockByNumberRatherThanTheBestBlock() {
        Keccak256 root = state(repository -> repository.saveCode(DELEGATED, DELEGATION_CODE));

        Result result = scan(root, "42", storedUnderNumber(42L));

        verify(result.blockStore).getChainBlockByNumber(42L);
        verify(result.blockStore, never()).getBestBlock();
        assertEquals(1, summaryField(result.records, "delegationShaped"), result.report());
        verify(result.stopper).stop(1);
    }

    @Test
    void rejectsAnUnparseableBlockArgumentWithoutWalking() {
        Keccak256 root = state(repository -> repository.saveCode(DELEGATED, DELEGATION_CODE));

        Result result = scan(root, "not-a-number", asBestBlock());

        assertTrue(result.records.isEmpty(), "a rejected argument must be reported without walking: " + result.report());
        verify(result.stopper).stop(1);
    }

    @Test
    void reportsBlockNotFoundWhenTheRequestedBlockNumberIsAbsent() {
        Keccak256 root = state(repository -> repository.saveCode(DELEGATED, DELEGATION_CODE));

        Result result = scan(root, "42", (blockStore, block) -> { /* nothing stored */ });

        assertTrue(result.records.isEmpty(), "a missing block must be reported without walking: " + result.report());
        verify(result.stopper).stop(1);
    }

    @Test
    void isInstantiableThroughTheNoArgConstructorTheLauncherUsesReflectively() throws Exception {
        // CliToolRskContextAware.create() builds the tool with Class.newInstance(), so the documented
        // `java -cp rsk.jar co.rsk.cli.tools.ScanStateShape` entry point needs a public no-arg constructor.
        Constructor<ScanStateShape> constructor = ScanStateShape.class.getConstructor();

        assertTrue(Modifier.isPublic(constructor.getModifiers()), "the launcher instantiates this reflectively");
        assertNotNull(constructor.newInstance());
    }

    private static byte[] ordinaryCode() {
        return new byte[]{0x60, 0x00, 0x60, 0x00, (byte) 0xf3};
    }

    private static RskAddress addressNumbered(int index) {
        byte[] bytes = new byte[RskAddress.LENGTH_IN_BYTES];
        bytes[0] = (byte) 0x0a;
        bytes[RskAddress.LENGTH_IN_BYTES - 1] = (byte) index;

        return new RskAddress(bytes);
    }

    private Keccak256 state(Consumer<Repository> fixture) {
        db = new HashMapDB().setClearOnClose(false);
        trieStore = new TrieStoreImpl(db);

        Repository repository = new MutableRepository(trieStore, new Trie(trieStore));
        fixture.accept(repository);
        repository.commit();
        repository.save();

        return new Keccak256(repository.getRoot());
    }

    private Result scan(Keccak256 root) {
        return scan(root, "best", asBestBlock());
    }

    private Result scan(Keccak256 root, String blockArg, BlockLookup stubBlockLookup) {
        BlockStore blockStore = mock(BlockStore.class);
        stubBlockLookup.stub(blockStore, blockWithStateRoot(root));

        Path report = tempDir.resolve("scan-" + System.nanoTime() + ".jsonl");
        NodeStopper stopper = mock(NodeStopper.class);

        new ScanStateShape().execute(
                new String[]{"--block", blockArg, "--report", report.toString()},
                () -> contextFor(blockStore),
                stopper);

        return new Result(readReport(report), stopper, blockStore);
    }

    private static List<String> readReport(Path report) {
        try {
            return Files.exists(report) ? Files.readAllLines(report, StandardCharsets.UTF_8) : List.of();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private RskContext contextFor(BlockStore blockStore) {
        RskSystemProperties rskSystemProperties = mock(RskSystemProperties.class);
        doReturn(tempDir.toString()).when(rskSystemProperties).databaseDir();
        doReturn(DbKind.LEVEL_DB).when(rskSystemProperties).databaseKind();

        RskContext rskContext = mock(RskContext.class);
        doReturn(blockStore).when(rskContext).getBlockStore();
        doReturn(trieStore).when(rskContext).getTrieStore();
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
        when(block.getHeader()).thenReturn(header);

        return block;
    }

    private static BlockLookup asBestBlock() {
        return (blockStore, block) -> when(blockStore.getBestBlock()).thenReturn(block);
    }

    private static BlockLookup storedUnderNumber(long number) {
        return (blockStore, block) -> when(blockStore.getChainBlockByNumber(number)).thenReturn(block);
    }

    /**
     * Every key the store holds is the hash of a node message or of a long value, so removing the root leaves
     * the stored non-root nodes. Sorted so the case removes the same node on every run.
     */
    private List<Keccak256> storedNonRootNodeHashes(Keccak256 root) {
        return db.keys().stream()
                .map(ByteArrayWrapper::getData)
                .map(Keccak256::new)
                .filter(hash -> !hash.equals(root))
                .sorted(Comparator.comparing(Keccak256::toHexString))
                .toList();
    }

    private static long summaryField(List<String> records, String field) {
        String summary = records.stream()
                .filter(line -> line.contains("\"kind\":\"summary\""))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("no summary record in:\n" + String.join("\n", records)));

        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\":(\\d+)").matcher(summary);
        assertTrue(matcher.find(), "no '" + field + "' in summary: " + summary);

        return Long.parseLong(matcher.group(1));
    }

    @FunctionalInterface
    private interface BlockLookup {
        void stub(BlockStore blockStore, Block block);
    }

    private record Result(List<String> records, NodeStopper stopper, BlockStore blockStore) {

        private String report() {
            return "\n" + String.join("\n", records);
        }
    }
}
