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
import co.rsk.config.BridgeConstants;
import co.rsk.config.GarbageCollectorConfig;
import co.rsk.config.NodeCliFlags;
import co.rsk.config.RskSystemProperties;
import co.rsk.net.AsyncNodeBlockProcessor;
import co.rsk.net.NodeBlockProcessor;
import co.rsk.trie.MultiTrieStore;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Genesis;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.RskTestContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class RskContextTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File databaseDir;
    private RskSystemProperties testProperties;
    private RskContext rskContext;

    @Before
    public void setUp() throws IOException {
        databaseDir = this.temporaryFolder.newFolder("database");

        testProperties = mock(RskSystemProperties.class);
        doReturn(0).when(testProperties).getStatesCacheSize();
        doReturn(databaseDir.getAbsolutePath()).when(testProperties).databaseDir();

        rskContext = new RskContext(new String[0]) {
            @Override
            public RskSystemProperties getRskSystemProperties() {
                return testProperties;
            }

            @Override
            public Genesis getGenesis() {
                return new BlockGenerator().getGenesisBlock();
            }
        };
    }

    @Test
    public void getCliArgsSmokeTest() {
        RskTestContext devnetContext = new RskTestContext(new String[] { "--devnet" });
        assertThat(devnetContext.getCliArgs(), notNullValue());
        assertThat(devnetContext.getCliArgs().getFlags(), contains(NodeCliFlags.NETWORK_DEVNET));
    }

    @Test
    public void shouldBuildSimpleTrieStore() throws IOException {
        doReturn(new GarbageCollectorConfig(false, 1000, 3)).when(testProperties).garbageCollectorConfig();

        TrieStore trieStore = rskContext.getTrieStore();
        assertThat(trieStore, is(instanceOf(TrieStoreImpl.class)));
        assertThat(Files.list(databaseDir.toPath()).count(), is(1L));
    }

    @Test
    public void shouldBuildSimpleTrieStoreCleaningUpMultiTrieStore() throws IOException {
        Path testDatabasesDirectory = databaseDir.toPath();
        doReturn(new GarbageCollectorConfig(false, 1000, 3)).when(testProperties).garbageCollectorConfig();

        long preExistingEpochs = 4;
        for (int i = 0; i < preExistingEpochs; i++) {
            Files.createDirectory(testDatabasesDirectory.resolve(String.format("unitrie_%d", i)));
        }

        assertThat(Files.list(testDatabasesDirectory).count(), is(preExistingEpochs));
        TrieStore trieStore = rskContext.getTrieStore();
        assertThat(trieStore, is(instanceOf(TrieStoreImpl.class)));
        assertThat(Files.list(testDatabasesDirectory).count(), is(1L));
    }

    @Test
    public void shouldBuildMultiTrieStore() throws IOException {
        long numberOfEpochs = 3;
        Path testDatabasesDirectory = databaseDir.toPath();
        doReturn(new GarbageCollectorConfig(true, 1000, (int) numberOfEpochs)).when(testProperties).garbageCollectorConfig();

        TrieStore trieStore = rskContext.getTrieStore();
        assertThat(trieStore, is(instanceOf(MultiTrieStore.class)));
        assertThat(Files.list(testDatabasesDirectory).count(), is(numberOfEpochs));
    }

    @Test
    public void shouldBuildMultiTrieStoreMigratingSingleTrieStore() throws IOException {
        long numberOfEpochs = 3;
        Path testDatabasesDirectory = databaseDir.toPath();
        doReturn(new GarbageCollectorConfig(true, 1000, (int) numberOfEpochs)).when(testProperties).garbageCollectorConfig();

        Files.createDirectory(testDatabasesDirectory.resolve("unitrie"));

        TrieStore trieStore = rskContext.getTrieStore();
        assertThat(trieStore, is(instanceOf(MultiTrieStore.class)));
        assertThat(Files.list(testDatabasesDirectory).count(), is(numberOfEpochs));
        assertThat(Files.list(testDatabasesDirectory).noneMatch(p -> p.getFileName().toString().equals("unitrie")), is(true));
    }

    @Test
    public void shouldBuildMultiTrieStoreFromExistingDirectories() throws IOException {
        int numberOfEpochs = 3;
        Path testDatabasesDirectory = databaseDir.toPath();
        doReturn(new GarbageCollectorConfig(true, 1000, numberOfEpochs)).when(testProperties).garbageCollectorConfig();

        int initialEpoch = 3;
        for (int i = initialEpoch; i < initialEpoch + numberOfEpochs; i++) {
            Files.createDirectory(testDatabasesDirectory.resolve(String.format("unitrie_%d", i)));
        }

        TrieStore trieStore = rskContext.getTrieStore();
        assertThat(trieStore, is(instanceOf(MultiTrieStore.class)));
        assertThat(Files.list(testDatabasesDirectory).count(), is((long) numberOfEpochs));
        int[] directorySuffixes = Files.list(testDatabasesDirectory)
                .map(Path::getFileName)
                .map(Path::toString)
                .map(fileName -> fileName.replaceAll("unitrie_", ""))
                .mapToInt(Integer::valueOf)
                .sorted()
                .toArray();
        assertThat(directorySuffixes, is(IntStream.range(initialEpoch, initialEpoch + numberOfEpochs).toArray()));
    }

    @Test
    public void buildInternalServicesWithPeerScoringSummaryService() {
        doReturn(new GarbageCollectorConfig(false, 1000, 3)).when(testProperties).garbageCollectorConfig();
        doReturn(1).when(testProperties).getNumOfAccountSlots();
        doReturn(1L).when(testProperties).getPeerScoringSummaryTime();
        doReturn(mock(ActivationConfig.class)).when(testProperties).getActivationConfig();
        doReturn(mock(ECKey.class)).when(testProperties).getMyKey();
        doReturn(Constants.testnet()).when(testProperties).getNetworkConstants();

        rskContext.buildInternalServices();

        assertNotNull(rskContext.getPeerScoringReporterService());
        assertTrue(rskContext.getPeerScoringReporterService().initialized());
    }

    @Test
    public void shouldBuildAsyncNodeBlockProcessor() {
        doReturn(new GarbageCollectorConfig(false, 1000, 3)).when(testProperties).garbageCollectorConfig();

        doReturn(1).when(testProperties).getNumOfAccountSlots();
        doReturn(true).when(testProperties).fastBlockPropagation();

        ActivationConfig config = mock(ActivationConfig.class);
        doReturn(config).when(testProperties).getActivationConfig();

        Constants constants = mock(Constants.class);
        doReturn(constants).when(testProperties).getNetworkConstants();

        BridgeConstants bridgeConstants = mock(BridgeConstants.class);
        doReturn(bridgeConstants).when(constants).getBridgeConstants();
        doReturn(1024).when(constants).getGasLimitBoundDivisor();

        NodeBlockProcessor nodeBlockProcessor = rskContext.getNodeBlockProcessor();
        assertThat(nodeBlockProcessor, is(instanceOf(AsyncNodeBlockProcessor.class)));
    }

    @Test
    public void doubleCloseShouldNotCrash() {
        assertFalse(rskContext.isClosed());

        rskContext.close();
        assertTrue(rskContext.isClosed());

        rskContext.close();
        assertTrue(rskContext.isClosed());
    }

    @Test
    public void closedContextShouldThrowErrorWhenBeingUsed() throws InvocationTargetException, IllegalAccessException {
        RskContext rskContext = new RskContext(new String[0]);

        rskContext.close();

        Set<String> methodsToSkip = new HashSet<String>() {{
            add("getCliArgs");
            add("isClosed");
            add("close");
        }};

        for (Method method : RskContext.class.getDeclaredMethods()) {
            int modifiers = method.getModifiers();
            if ((Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers)) && !methodsToSkip.contains(method.getName())) {
                try {
                    method.invoke(rskContext, new Object[method.getParameterCount()]);
                    fail("runNode should throw an exception");
                } catch (InvocationTargetException e) {
                    assertEquals("RSK Context is closed and cannot be in use anymore", e.getTargetException().getMessage());
                }
            }
        }
    }
}
