/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.GarbageCollectorConfig;
import co.rsk.config.InternalService;
import co.rsk.config.NodeCliFlags;
import co.rsk.config.RskSystemProperties;
import co.rsk.config.TestSystemProperties;
import co.rsk.net.AsyncNodeBlockProcessor;
import co.rsk.net.NodeBlockProcessor;
import co.rsk.net.discovery.KnownPeersHandler;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.trie.MultiTrieStore;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import co.rsk.validators.BlockDifficultyRule;
import co.rsk.validators.BlockParentCompositeRule;
import co.rsk.validators.BlockParentDependantValidationRule;
import co.rsk.validators.BlockParentGasLimitRule;
import co.rsk.validators.BlockParentNumberRule;
import co.rsk.validators.BlockTxsFieldsValidationRule;
import co.rsk.validators.BlockTxsValidationRule;
import co.rsk.validators.PrevMinGasPriceRule;
import org.ethereum.TestUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Genesis;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.RskTestContext;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RskContextTest {

    private Path databaseDir;
    private RskSystemProperties testProperties;
    private InternalService internalService;
    private RskContext rskContext;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        databaseDir = tempDir.resolve("database");

        testProperties = spy(new TestSystemProperties());
        doReturn(0).when(testProperties).getStatesCacheSize();
        doReturn(databaseDir.toString()).when(testProperties).databaseDir();

        internalService = mock(InternalService.class);

        rskContext = makeRskContext();
    }

    @Test
    void getCliArgsSmokeTest() {
        RskTestContext testnetContext = new RskTestContext(databaseDir, "--testnet");
        MatcherAssert.assertThat(testnetContext.getCliArgs(), notNullValue());
        MatcherAssert.assertThat(testnetContext.getCliArgs().getFlags(), contains(NodeCliFlags.NETWORK_TESTNET));
        testnetContext.close();
    }

    @Test
    void shouldResolveCacheSnapshotPath() {
        Path baseStorePath = Paths.get("./db");

        Path resolvedPath = rskContext.resolveCacheSnapshotPath(baseStorePath);

        assertNotNull(resolvedPath);

        String pathSuffix = resolvedPath.toString().replace(baseStorePath.toString(), "");
        assertEquals("/rskcache", pathSuffix);
    }

    @Test
    void shouldBuildSimpleTrieStore() throws IOException {
        doReturn(new GarbageCollectorConfig(false, 1000, 3)).when(testProperties).garbageCollectorConfig();

        TrieStore trieStore = rskContext.getTrieStore();
        MatcherAssert.assertThat(trieStore, is(instanceOf(TrieStoreImpl.class)));
        MatcherAssert.assertThat(Files.list(databaseDir).count(), is(1L));
    }

    @Test
    void shouldBuildSimpleTrieStoreCleaningUpMultiTrieStore() throws IOException {
        doReturn(new GarbageCollectorConfig(false, 1000, 3)).when(testProperties).garbageCollectorConfig();

        assertTrue(databaseDir.toFile().mkdir());

        long preExistingEpochs = 4;
        for (int i = 0; i < preExistingEpochs; i++) {
            Files.createDirectory(databaseDir.resolve(String.format("unitrie_%d", i)));
        }

        MatcherAssert.assertThat(Files.list(databaseDir).count(), is(preExistingEpochs));
        TrieStore trieStore = rskContext.getTrieStore();
        MatcherAssert.assertThat(trieStore, is(instanceOf(TrieStoreImpl.class)));
        MatcherAssert.assertThat(Files.list(databaseDir).count(), is(1L));
    }

    @Test
    void shouldBuildMultiTrieStore() throws IOException {
        long numberOfEpochs = 3;
        doReturn(new GarbageCollectorConfig(true, 1000, (int) numberOfEpochs)).when(testProperties).garbageCollectorConfig();

        TrieStore trieStore = rskContext.getTrieStore();
        MatcherAssert.assertThat(trieStore, is(instanceOf(MultiTrieStore.class)));
        MatcherAssert.assertThat(Files.list(databaseDir).count(), is(numberOfEpochs));
    }

    @Test
    void shouldBuildMultiTrieStoreMigratingSingleTrieStore() throws IOException {
        rskContext.close();

        long numberOfEpochs = 3;
        doReturn(new GarbageCollectorConfig(true, 1000, (int) numberOfEpochs)).when(testProperties).garbageCollectorConfig();

        rskContext = makeRskContext();

        TrieStore trieStore = rskContext.getTrieStore();
        MatcherAssert.assertThat(trieStore, is(instanceOf(MultiTrieStore.class)));
        MatcherAssert.assertThat(Files.list(databaseDir).count(), is(numberOfEpochs));
        MatcherAssert.assertThat(Files.list(databaseDir).noneMatch(p -> p.getFileName().toString().equals("unitrie")), is(true));
    }

    @Test
    void shouldBuildMultiTrieStoreFromExistingDirectories() throws IOException {
        int numberOfEpochs = 3;
        doReturn(false).when(testProperties).databaseReset();

        assertTrue(databaseDir.toFile().mkdir());

        doReturn(new GarbageCollectorConfig(true, 1000, numberOfEpochs)).when(testProperties).garbageCollectorConfig();

        int initialEpoch = 3;
        for (int i = initialEpoch; i < initialEpoch + numberOfEpochs; i++) {
            Files.createDirectory(databaseDir.resolve(String.format("unitrie_%d", i)));
        }
        rskContext.close();
        rskContext = makeRskContext();

        TrieStore trieStore = rskContext.getTrieStore();
        MatcherAssert.assertThat(trieStore, is(instanceOf(MultiTrieStore.class)));
        MatcherAssert.assertThat(Files.list(databaseDir).count(), is((long) numberOfEpochs));
        int[] directorySuffixes = Files.list(databaseDir)
                .map(Path::getFileName)
                .map(Path::toString)
                .map(fileName -> fileName.replaceAll("unitrie_", ""))
                .mapToInt(Integer::valueOf)
                .sorted()
                .toArray();
        MatcherAssert.assertThat(directorySuffixes, is(IntStream.range(initialEpoch, initialEpoch + numberOfEpochs).toArray()));
    }

    @Test
    void buildInternalServicesWithPeerScoringSummaryService() {
        doReturn(new GarbageCollectorConfig(false, 1000, 3)).when(testProperties).garbageCollectorConfig();
        doReturn(1).when(testProperties).getNumOfAccountSlots();
        doReturn(1L).when(testProperties).getPeerScoringSummaryTime();
        doReturn(mock(ActivationConfig.class)).when(testProperties).getActivationConfig();
        doReturn(mock(ECKey.class)).when(testProperties).getMyKey();
        doReturn(Constants.testnet(null)).when(testProperties).getNetworkConstants();

        rskContext.buildInternalServices();

        assertNotNull(rskContext.getPeerScoringReporterService());
        Assertions.assertTrue(rskContext.getPeerScoringReporterService().initialized());
    }

    @Test
    void shouldBuildAsyncNodeBlockProcessor() {
        doReturn(new GarbageCollectorConfig(false, 1000, 3)).when(testProperties).garbageCollectorConfig();

        doReturn(1).when(testProperties).getNumOfAccountSlots();
        doReturn(true).when(testProperties).fastBlockPropagation();

        ActivationConfig config = mock(ActivationConfig.class);
        doReturn(true).when(config).isActive(eq(ConsensusRule.RSKIP126), anyLong());
        doReturn(config).when(testProperties).getActivationConfig();

        Constants constants = mock(Constants.class);
        doReturn(constants).when(testProperties).getNetworkConstants();

        BridgeConstants bridgeConstants = mock(BridgeConstants.class);
        doReturn(bridgeConstants).when(constants).getBridgeConstants();
        doReturn(1024).when(constants).getGasLimitBoundDivisor();

        NodeBlockProcessor nodeBlockProcessor = rskContext.getNodeBlockProcessor();
        MatcherAssert.assertThat(nodeBlockProcessor, is(instanceOf(AsyncNodeBlockProcessor.class)));
    }

    @Test
    void doubleCloseShouldNotCrash() {
        Assertions.assertFalse(rskContext.isClosed());

        rskContext.close();
        Assertions.assertTrue(rskContext.isClosed());

        rskContext.close();
        Assertions.assertTrue(rskContext.isClosed());
    }

    @Test
    void closeShouldStopInternalService() throws Exception {
        Assertions.assertFalse(rskContext.isClosed());

        rskContext.getNodeRunner().run();
        rskContext.close();
        Assertions.assertTrue(rskContext.isClosed());
        verify(internalService, times(1)).stop();
    }

    @Test
    void closedContextShouldThrowErrorWhenBeingUsed() throws IllegalAccessException {
        RskContext rskContext = new RskContext(new String[0]);

        rskContext.close();

        Set<String> methodsToSkip = new HashSet<>() {{
            add("getCliArgs");
            add("resolveCacheSnapshotPath");
            add("isClosed");
            add("close");
        }};

        for (Method method : RskContext.class.getDeclaredMethods()) {
            int modifiers = method.getModifiers();
            if ((Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers)) && !methodsToSkip.contains(method.getName())) {
                try {
                    method.invoke(rskContext, new Object[method.getParameterCount()]);
                    Assertions.fail(method.getName() + " should throw an exception when called on closed context");
                } catch (InvocationTargetException e) {
                    assertEquals("RSK Context is closed and cannot be in use anymore", e.getTargetException().getMessage());
                }
            }
        }
    }

    @Test
    void shouldMakeNewContext() throws Exception {
        Assertions.assertFalse(rskContext.isClosed());

        rskContext.getNodeRunner().run();

        rskContext.close();
        Assertions.assertTrue(rskContext.isClosed());

        rskContext = makeRskContext(); // make a brand new context
        Assertions.assertFalse(rskContext.isClosed());

        rskContext.getNodeRunner().run();

        rskContext.close();
        Assertions.assertTrue(rskContext.isClosed());
    }

    @Test
    void initialNodesAreLoadedWithoutDuplications(){
        KnownPeersHandler knownPeersHandler = mock(KnownPeersHandler.class);
        List<String> peerDiscoveryIPList = Arrays.asList("1.1.1.1:1234","1.2.3.4:4444");
        doReturn(peerDiscoveryIPList).when(testProperties).peerDiscoveryIPList();
        List<String> peersFromLastSession = Arrays.asList("4.3.2.1:4444","1.1.1.1:1234");
        when(knownPeersHandler.readPeers()).thenReturn(peersFromLastSession);
        doReturn(true).when(testProperties).usePeersFromLastSession();
        List<String> initialBootNodes = rskContext.getInitialBootNodes(knownPeersHandler);
        assertNotNull(initialBootNodes);
        assertEquals(3, initialBootNodes.size(), "Initial nodes should be 3");
        assertEquals(initialBootNodes.stream().distinct().count(), initialBootNodes.size(), "Initial nodes should not have duplicates");
    }

    /**
     * How much work a rule costs on a block that is going to be rejected anyway.
     * <p>
     * {@link BlockParentCompositeRule} evaluates in order and short-circuits on the first failure, so the
     * position of a rule decides what an invalid block costs before it is dropped. Tier 0 rules read a
     * handful of header fields. Tier 1 walks every transaction in the block and validates its signature —
     * work whose size an unauthenticated peer chooses. Tier 2 does that *and* opens a repository snapshot
     * at the parent to read sender nonces.
     * <p>
     * Rules are classified rather than pinned to a fixed sequence so that adding a rule does not "fail the
     * order test" mechanically: a new rule has to be given a tier, and the composite still has to be built
     * cheap-tier-first.
     */
    private static final Map<Class<? extends BlockParentDependantValidationRule>, Integer> RULE_COST_TIER =
            Collections.unmodifiableMap(new LinkedHashMap<>() {{
                put(BlockParentNumberRule.class, 0);
                put(BlockDifficultyRule.class, 0);
                put(BlockParentGasLimitRule.class, 0);
                put(PrevMinGasPriceRule.class, 0);
                put(BlockTxsFieldsValidationRule.class, 1); // per-transaction signature validation
                put(BlockTxsValidationRule.class, 2);       // per-transaction + repository snapshot reads
            }});

    @Test
    void blockParentDependantValidationRunsCheapRulesBeforeExpensiveOnes() {
        assertCheapRulesFirst(rskContext.getBlockParentDependantValidationRule());
    }

    @Test
    void snapBlockParentDependantValidationRunsCheapRulesBeforeExpensiveOnes() {
        assertCheapRulesFirst(rskContext.getSnapBlockParentDependantValidationRule());
    }

    @Test
    void blockParentDependantValidationKeepsItsFullSetOfRules() {
        // ordering must not be achieved by dropping a rule: the set itself is consensus-relevant
        assertRuleSet(rskContext.getBlockParentDependantValidationRule(),
                BlockParentNumberRule.class,
                BlockDifficultyRule.class,
                BlockParentGasLimitRule.class,
                PrevMinGasPriceRule.class,
                BlockTxsFieldsValidationRule.class,
                BlockTxsValidationRule.class);
    }

    @Test
    void snapBlockParentDependantValidationKeepsItsFullSetOfRules() {
        // the snap variant deliberately omits BlockTxsValidationRule (no state at the parent to read yet)
        assertRuleSet(rskContext.getSnapBlockParentDependantValidationRule(),
                BlockParentNumberRule.class,
                BlockDifficultyRule.class,
                BlockParentGasLimitRule.class,
                PrevMinGasPriceRule.class,
                BlockTxsFieldsValidationRule.class);
    }

    /**
     * Asserts the composite is ordered by non-decreasing cost, so the first rule that rejects a block is
     * always the cheapest one that can.
     */
    private static void assertCheapRulesFirst(BlockParentDependantValidationRule composite) {
        List<Class<?>> order = ruleClassesOf(composite);

        List<String> unclassified = order.stream()
                .filter(rule -> !RULE_COST_TIER.containsKey(rule))
                .map(Class::getSimpleName)
                .toList();
        assertTrue(unclassified.isEmpty(),
                "give these rules a cost tier in RULE_COST_TIER and place them accordingly: " + unclassified);

        for (int i = 1; i < order.size(); i++) {
            int previousTier = RULE_COST_TIER.get(order.get(i - 1));
            int currentTier = RULE_COST_TIER.get(order.get(i));

            assertTrue(previousTier <= currentTier,
                    String.format("%s (cost tier %d) runs after %s (cost tier %d): an invalid block pays the"
                                    + " more expensive rule before the cheaper one can reject it. Order was %s",
                            order.get(i - 1).getSimpleName(), previousTier,
                            order.get(i).getSimpleName(), currentTier,
                            order.stream().map(Class::getSimpleName).toList()));
        }
    }

    @SafeVarargs
    private static void assertRuleSet(BlockParentDependantValidationRule composite,
                                      Class<? extends BlockParentDependantValidationRule>... expected) {
        assertEquals(new HashSet<>(Arrays.asList(expected)), new HashSet<>(ruleClassesOf(composite)));
    }

    private static List<Class<?>> ruleClassesOf(BlockParentDependantValidationRule composite) {
        MatcherAssert.assertThat(composite, is(instanceOf(BlockParentCompositeRule.class)));

        List<BlockParentDependantValidationRule> rules = TestUtils.getInternalState(composite, "rules");

        return rules.stream().map(Object::getClass).toList();
    }

    private RskContext makeRskContext() {
        return new RskContext(new String[0]) {
            @Override
            public RskSystemProperties getRskSystemProperties() {
                return testProperties;
            }

            @Override
            public Genesis getGenesis() {
                return new BlockGenerator().getGenesisBlock();
            }

            @Override
            public synchronized List<InternalService> buildInternalServices() {
                // instantiate LevelDB instances which should be closed when the context is being closed
                assertNotNull(getBlockStore());
                assertNotNull(getTrieStore());
                assertNotNull(getReceiptStore());
                assertNotNull(getStateRootsStore());
                assertNotNull(getBlockStore());
                assertNotNull(getWallet());

                return Collections.singletonList(internalService);
            }
        };
    }
}
