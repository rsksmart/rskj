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
import co.rsk.config.*;
import co.rsk.net.AsyncNodeBlockProcessor;
import co.rsk.net.NodeBlockProcessor;
import co.rsk.trie.MultiTrieStore;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class RskContextTest {

    private Path databaseDir;
    private RskSystemProperties testProperties;
    private InternalService internalService;
    private RskContext rskContext;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        databaseDir = tempDir.resolve("database");

        testProperties = spy(new TestSystemProperties());
        doReturn(0).when(testProperties).getStatesCacheSize();
        doReturn(databaseDir.toString()).when(testProperties).databaseDir();

        internalService = mock(InternalService.class);

        rskContext = makeRskContext();
    }

    @Test
    void getCliArgsSmokeTest() {
        RskTestContext devnetContext = new RskTestContext(databaseDir, "--devnet");
        MatcherAssert.assertThat(devnetContext.getCliArgs(), notNullValue());
        MatcherAssert.assertThat(devnetContext.getCliArgs().getFlags(), contains(NodeCliFlags.NETWORK_DEVNET));
        devnetContext.close();
    }

    @Test
    void shouldResolveCacheSnapshotPath() {
        Path baseStorePath = Paths.get("./db");

        Path resolvedPath = rskContext.resolveCacheSnapshotPath(baseStorePath);

        Assertions.assertNotNull(resolvedPath);

        String pathSuffix = resolvedPath.toString().replace(baseStorePath.toString(), "");
        Assertions.assertEquals("/rskcache", pathSuffix);
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

        Assertions.assertNotNull(rskContext.getPeerScoringReporterService());
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

        Set<String> methodsToSkip = new HashSet<String>() {{
            add("getCliArgs");
            add("resolveCacheSnapshotPath");
            add("isClosed");
            add("close");
            add("getCurrentDbKind");
        }};

        for (Method method : RskContext.class.getDeclaredMethods()) {
            int modifiers = method.getModifiers();
            if ((Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers)) && !methodsToSkip.contains(method.getName())) {
                try {
                    method.invoke(rskContext, new Object[method.getParameterCount()]);
                    Assertions.fail(method.getName() + " should throw an exception when called on closed context");
                } catch (InvocationTargetException e) {
                    Assertions.assertEquals("RSK Context is closed and cannot be in use anymore", e.getTargetException().getMessage());
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
                Assertions.assertNotNull(getBlockStore());
                Assertions.assertNotNull(getTrieStore());
                Assertions.assertNotNull(getReceiptStore());
                Assertions.assertNotNull(getStateRootsStore());
                Assertions.assertNotNull(getBlockStore());
                Assertions.assertNotNull(getWallet());

                return Collections.singletonList(internalService);
            }
        };
    }
}
