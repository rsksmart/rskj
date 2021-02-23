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
import org.ethereum.core.Genesis;
import org.ethereum.util.RskTestContext;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class RskContextTest {

    private RskSystemProperties testProperties;
    private RskContext rskContext;
    @Rule
    public TemporaryFolder databaseDir = new TemporaryFolder();

    @Before
    public void setUp() {
        testProperties = mock(RskSystemProperties.class);
        doReturn(0).when(testProperties).getStatesCacheSize();

        rskContext = new RskContext(new String[0]) {
            @Override
            public RskSystemProperties getRskSystemProperties() {
                return testProperties;
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
        Path testDatabasesDirectory = databaseDir.getRoot().toPath();
        doReturn(new GarbageCollectorConfig(false, 1000, 3)).when(testProperties).garbageCollectorConfig();
        doReturn(testDatabasesDirectory.toString()).when(testProperties).databaseDir();

        TrieStore trieStore = rskContext.getTrieStore();
        Assert.assertThat(trieStore, is(instanceOf(TrieStoreImpl.class)));
        Assert.assertThat(Files.list(testDatabasesDirectory).count(), is(1L));
    }

    @Test
    public void shouldBuildSimpleTrieStoreCleaningUpMultiTrieStore() throws IOException, InterruptedException {
        Path testDatabasesDirectory = databaseDir.getRoot().toPath();
        doReturn(new GarbageCollectorConfig(false, 1000, 3)).when(testProperties).garbageCollectorConfig();
        doReturn(testDatabasesDirectory.toString()).when(testProperties).databaseDir();

        long preExistingEpochs = 4;
        for (int i = 0; i < preExistingEpochs; i++) {
            Files.createDirectory(testDatabasesDirectory.resolve(String.format("unitrie_%d", i)));
        }

        Assert.assertThat(Files.list(testDatabasesDirectory).count(), is(preExistingEpochs));
        TrieStore trieStore = rskContext.getTrieStore();
        Assert.assertThat(trieStore, is(instanceOf(TrieStoreImpl.class)));
        Assert.assertThat(Files.list(testDatabasesDirectory).count(), is(1L));
    }

    @Test
    public void shouldBuildMultiTrieStore() throws IOException {
        long numberOfEpochs = 3;
        Path testDatabasesDirectory = databaseDir.getRoot().toPath();
        doReturn(new GarbageCollectorConfig(true, 1000, (int) numberOfEpochs)).when(testProperties).garbageCollectorConfig();
        doReturn(testDatabasesDirectory.toString()).when(testProperties).databaseDir();

        TrieStore trieStore = rskContext.getTrieStore();
        Assert.assertThat(trieStore, is(instanceOf(MultiTrieStore.class)));
        Assert.assertThat(Files.list(testDatabasesDirectory).count(), is(numberOfEpochs));
    }

    @Test
    public void shouldBuildMultiTrieStoreMigratingSingleTrieStore() throws IOException {
        long numberOfEpochs = 3;
        Path testDatabasesDirectory = databaseDir.getRoot().toPath();
        doReturn(new GarbageCollectorConfig(true, 1000, (int) numberOfEpochs)).when(testProperties).garbageCollectorConfig();
        doReturn(testDatabasesDirectory.toString()).when(testProperties).databaseDir();

        Files.createDirectory(testDatabasesDirectory.resolve("unitrie"));

        TrieStore trieStore = rskContext.getTrieStore();
        Assert.assertThat(trieStore, is(instanceOf(MultiTrieStore.class)));
        Assert.assertThat(Files.list(testDatabasesDirectory).count(), is(numberOfEpochs));
        Assert.assertThat(Files.list(testDatabasesDirectory).noneMatch(p -> p.getFileName().toString().equals("unitrie")), is(true));
    }

    @Test
    public void shouldBuildMultiTrieStoreFromExistingDirectories() throws IOException {
        int numberOfEpochs = 3;
        Path testDatabasesDirectory = databaseDir.getRoot().toPath();
        doReturn(new GarbageCollectorConfig(true, 1000, numberOfEpochs)).when(testProperties).garbageCollectorConfig();
        doReturn(testDatabasesDirectory.toString()).when(testProperties).databaseDir();

        int initialEpoch = 3;
        for (int i = initialEpoch; i < initialEpoch + numberOfEpochs; i++) {
            Files.createDirectory(testDatabasesDirectory.resolve(String.format("unitrie_%d", i)));
        }

        TrieStore trieStore = rskContext.getTrieStore();
        Assert.assertThat(trieStore, is(instanceOf(MultiTrieStore.class)));
        Assert.assertThat(Files.list(testDatabasesDirectory).count(), is((long) numberOfEpochs));
        int[] directorySufixes = Files.list(testDatabasesDirectory)
                .map(Path::getFileName)
                .map(Path::toString)
                .map(fileName -> fileName.replaceAll("unitrie_", ""))
                .mapToInt(Integer::valueOf)
                .sorted()
                .toArray();
        Assert.assertThat(directorySufixes, is(IntStream.range(initialEpoch, initialEpoch + numberOfEpochs).toArray()));
    }

    @Test(expected = IllegalStateException.class)
    @Ignore("Permissions set fails under CircleCI")
    public void shouldFailIfCannotBuildMultiTrieStore() throws IOException {
        Path testDatabasesDirectory = Files.createTempDirectory("test", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("---------")));
        doReturn(new GarbageCollectorConfig(true, 1000, 3)).when(testProperties).garbageCollectorConfig();
        doReturn(testDatabasesDirectory.toString()).when(testProperties).databaseDir();

        RskContext rskContext = new RskContext(new String[0]) {
            @Override
            public RskSystemProperties getRskSystemProperties() {
                return testProperties;
            }
        };

        rskContext.getTrieStore();
    }

    @Test
    public void buildInternalServicesWithPeerScoringSummaryService() {
        RskContext rskContext = new RskContext(new String[0]);
        rskContext.buildInternalServices();

        Assert.assertNotNull(rskContext.getPeerScoringReporterService());
        Assert.assertTrue(rskContext.getPeerScoringReporterService().initialized());
    }

    @Test
    public void shouldBuildAsyncNodeBlockProcessor() {
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

        Path testDatabasesDirectory = databaseDir.getRoot().toPath();
        doReturn(new GarbageCollectorConfig(false, 1000, 3)).when(testProperties).garbageCollectorConfig();
        doReturn(testDatabasesDirectory.toString()).when(testProperties).databaseDir();

        doReturn(1).when(testProperties).getNumOfAccountSlots();
        doReturn(true).when(testProperties).fastBlockPropagation();
        doReturn("").when(testProperties).receiptsDataFormatVersion();

        ActivationConfig config = mock(ActivationConfig.class);
        doReturn(config).when(testProperties).getActivationConfig();

        Constants constants = mock(Constants.class);
        doReturn(constants).when(testProperties).getNetworkConstants();

        BridgeConstants bridgeConstants = mock(BridgeConstants.class);
        doReturn(bridgeConstants).when(constants).getBridgeConstants();
        doReturn(1024).when(constants).getGasLimitBoundDivisor();

        NodeBlockProcessor nodeBlockProcessor = rskContext.getNodeBlockProcessor();
        Assert.assertThat(nodeBlockProcessor, is(instanceOf(AsyncNodeBlockProcessor.class)));
    }
}
