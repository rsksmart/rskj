/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.peg;

import static co.rsk.bitcoinj.core.Utils.uint32ToByteStreamLE;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP122;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP123;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP124;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP220;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP87;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP88;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP89;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import co.rsk.test.builders.BlockChainBuilder;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Genesis;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.MutableRepository;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.VM;
import org.ethereum.vm.exception.VMException;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.invoke.ProgramInvoke;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import co.rsk.asm.EVMAssembler;
import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.MessageSerializer;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.PartialMerkleTree;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionInput;
import co.rsk.bitcoinj.core.TransactionOutput;
import co.rsk.bitcoinj.core.TransactionWitness;
import co.rsk.bitcoinj.core.Utils;
import co.rsk.bitcoinj.core.VarInt;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.config.RskSystemProperties;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.BlockDifficulty;
import co.rsk.core.RskAddress;
import co.rsk.core.genesis.TestGenesisLoader;
import co.rsk.crypto.Keccak256;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.peg.bitcoin.MerkleBranch;
import co.rsk.peg.bitcoin.SimpleBtcTransaction;
import co.rsk.peg.whitelist.OneOffWhiteListEntry;
import co.rsk.peg.whitelist.UnlimitedWhiteListEntry;
import co.rsk.test.World;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import co.rsk.util.HexUtils;

/**
 * Created by ajlopez on 6/8/2016.
 */
@ExtendWith(MockitoExtension.class)
// to avoid Junit5 unnecessary stub error due to some setup generalizations
@MockitoSettings(strictness = Strictness.LENIENT)
public class BridgeTestIntegration {
    private static NetworkParameters networkParameters;
    private static BridgeRegTestConstants bridgeConstants;

    private static final BigInteger AMOUNT = new BigInteger("1000000000000000000");
    private static final BigInteger NONCE = new BigInteger("0");
    private static final BigInteger GAS_PRICE = new BigInteger("100");
    private static final BigInteger GAS_LIMIT = new BigInteger("1000");
    private static final String DATA = "80af2871";
    private static final String ERR_NOT_FROM_ACTIVE_OR_RETIRING_FED = "Sender is not part of the active or retiring federation";
    private static ECKey fedECPrivateKey;

    private TestSystemProperties config = new TestSystemProperties();
    private Constants constants;
    private ActivationConfig activationConfig;
    private ActivationConfig.ForBlock activationConfigAll;
    private BlockFactory blockFactory;

    @BeforeAll
    public static void setUpBeforeClass() {
        bridgeConstants = BridgeRegTestConstants.getInstance();
        networkParameters = bridgeConstants.getBtcParams();
        BtcECKey fedBTCPrivateKey = BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS.get(0);
        fedECPrivateKey = ECKey.fromPrivate(fedBTCPrivateKey.getPrivKey());
    }

    @BeforeEach
    public void resetConfigToRegTest() {
        config = spy(new TestSystemProperties());
        constants = Constants.regtest();
        when(config.getNetworkConstants()).thenReturn(constants);
        activationConfig = spy(ActivationConfigsForTest.genesis());
        when(config.getActivationConfig()).thenReturn(activationConfig);
        blockFactory = new BlockFactory(activationConfig);
        activationConfigAll = ActivationConfigsForTest.all().forBlock(0);
    }

    @Test
    public void callUpdateCollectionsWithSignatureNotFromFederation() throws IOException {
        BtcTransaction tx1 = createTransaction();

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationConfigAll);

        provider0.getReleaseTransactionSet().add(tx1, 1L, PegTestUtils.createHash3(0));
        provider0.save();

        track.commit();

        track = repository.startTracking();

        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(PrecompiledContracts.BRIDGE_ADDR_STR))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(new ECKey().getPrivKeyBytes());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        BlockChainBuilder blockChainBuilder = new BlockChainBuilder().setRequireUnclesValidation(false);
        World world = new World(blockChainBuilder);
        bridge.init(rskTx, world.getBlockChain().getBestBlock(), track, world.getBlockStore(), null, new LinkedList<>());
        try {
            bridge.execute(Bridge.UPDATE_COLLECTIONS.encode());
            Assertions.fail();
        } catch (Exception ex) {
            Assertions.assertTrue(ex.getMessage().contains(ERR_NOT_FROM_ACTIVE_OR_RETIRING_FED));
        }
    }

    @Test
    public void callUpdateCollectionsWithTransactionsWaitingForConfirmation() throws IOException, VMException {
        BtcTransaction tx1 = createTransaction();
        BtcTransaction tx2 = createTransaction();
        BtcTransaction tx3 = createTransaction();

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationConfig.forBlock(0));

        provider0.getReleaseTransactionSet().add(tx1, 1L);
        provider0.getReleaseTransactionSet().add(tx2, 2L);
        provider0.getReleaseTransactionSet().add(tx3, 3L);

        provider0.save();

        track.commit();

        track = repository.startTracking();
        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(PrecompiledContracts.BRIDGE_ADDR_STR))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);

        BlockChainBuilder blockChainBuilder = new BlockChainBuilder().setRequireUnclesValidation(false);
        World world = new World(blockChainBuilder);
        bridge.init(rskTx, world.getBlockChain().getBestBlock(), track, world.getBlockStore(), null, new LinkedList<>());

        bridge.execute(Bridge.UPDATE_COLLECTIONS.encode());

        track.commit();

        //Reusing same storage configuration as the height doesn't affect storage configurations for releases.
        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationConfigAll);

        Assertions.assertEquals(3, provider.getReleaseTransactionSet().getEntries().size());
        Assertions.assertEquals(0, provider.getRskTxsWaitingForSignatures().size());
    }

    @Test
    public void callUpdateCollectionsWithTransactionsWaitingForConfirmationWithEnoughConfirmations() throws IOException, VMException {
        BtcTransaction tx1 = createTransaction();
        BtcTransaction tx2 = createTransaction();
        BtcTransaction tx3 = createTransaction();

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationConfig.forBlock(0));

        provider0.getReleaseTransactionSet().add(tx1, 1L);
        provider0.getReleaseTransactionSet().add(tx2, 2L);
        provider0.getReleaseTransactionSet().add(tx3, 3L);

        provider0.save();

        track.commit();

        track = repository.startTracking();

        BlockChainBuilder blockChainBuilder = new BlockChainBuilder().setRequireUnclesValidation(false);
        World world = new World(blockChainBuilder);
        List<Block> blocks = new BlockGenerator().getSimpleBlockChain(world.getBlockChain().getBestBlock(), 10);

        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(PrecompiledContracts.BRIDGE_ADDR_STR))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());

        world.getBlockStore().saveBlock(blocks.get(1), new BlockDifficulty(BigInteger.ONE), true);

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(rskTx, blocks.get(9), track, world.getBlockStore(), null, new LinkedList<>());

        bridge.execute(Bridge.UPDATE_COLLECTIONS.encode());

        track.commit();

        // reusing same storage configuration as the height doesn't affect storage configurations for releases.
        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activationConfigAll);

        Assertions.assertEquals(2, provider.getReleaseTransactionSet().getEntries().size());
        Assertions.assertEquals(1, provider.getRskTxsWaitingForSignatures().size());
    }

    @Test
    public void sendNoRskTx() throws IOException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(null, getGenesisBlock(), track, null, null, null);
        try {
            bridge.execute(Bridge.RECEIVE_HEADERS.encode());
            Assertions.fail();
        } catch (Exception ex) {
            Assertions.assertTrue(ex.getMessage().contains("Rsk Transaction is null"));
        }

        track.commit();
    }

    @Test
    public void sendNoBlockHeader() throws BlockStoreException, IOException, VMException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(PrecompiledContracts.BRIDGE_ADDR_STR))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());

        BridgeSupportFactory bridgeSupportFactory = mock(BridgeSupportFactory.class);
        BridgeSupport bridgeSupport = mock(BridgeSupport.class);
        when(bridgeSupportFactory.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupport);
        when(bridgeSupport.getActiveFederation()).thenReturn(bridgeConstants.getGenesisFederation());
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);

        bridge.execute(Bridge.RECEIVE_HEADERS.encode());

        track.commit();

        verify(bridgeSupport, times(1)).receiveHeaders(new BtcBlock[]{});
        // TODO improve test
        Assertions.assertNotNull(track.getRoot());
    }

    @Test
    public void sendOrphanBlockHeader() throws VMException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(PrecompiledContracts.BRIDGE_ADDR_STR))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);

        Integer previousHeight = bridge.getBtcBlockchainBestChainHeight(new Object[]{});

        co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(networkParameters, 1, PegTestUtils.createHash(), PegTestUtils.createHash(), 1, Utils.encodeCompactBits(networkParameters.getMaxTarget()), 1, new ArrayList<>())
                .cloneAsHeader();
        co.rsk.bitcoinj.core.BtcBlock[] headers = new co.rsk.bitcoinj.core.BtcBlock[1];
        headers[0] = block;

        Object[] objectArray = new Object[headers.length];

        for (int i = 0; i < headers.length; i++)
            objectArray[i] = headers[i].bitcoinSerialize();

        bridge.execute(Bridge.RECEIVE_HEADERS.encode(new Object[]{objectArray}));

        track.commit();

        Assertions.assertEquals(previousHeight, bridge.getBtcBlockchainBestChainHeight(new Object[]{}));
        // TODO improve test
        Assertions.assertNotNull(track.getRoot());
    }

    @Test
    public void executeWithFunctionSignatureLengthTooShortBeforeRskip88() throws VMException {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        Transaction mockedTx = mock(Transaction.class);
        bridge.init(mockedTx, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        Assertions.assertNull(bridge.execute(new byte[3]));
    }

    @Test
    public void executeWithFunctionSignatureLengthTooShortAfterRskip88() {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP88), anyLong());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig
        );
        Bridge bridge = new Bridge(
                PrecompiledContracts.BRIDGE_ADDR,
                constants,
                activationConfig,
                bridgeSupportFactory
        );
        Transaction mockedTx = mock(Transaction.class);

        try {
            bridge.init(mockedTx, getGenesisBlock(), createRepository().startTracking(), null, null, null);
            bridge.execute(new byte[3]);
            Assertions.fail();
        } catch (VMException e) {
            Assertions.assertTrue(e.getMessage().contains("Invalid data given"));
        }
    }

    @Test
    public void executeWithInexistentFunctionBeforeRskip88() throws VMException {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        Transaction mockedTx = mock(Transaction.class);
        bridge.init(mockedTx, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        Assertions.assertNull(bridge.execute(new byte[4]));
    }

    @Test
    public void executeWithInexistentFunctionAfterRskip88() {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP88), anyLong());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        Transaction mockedTx = mock(Transaction.class);

        try {
            bridge.init(mockedTx, getGenesisBlock(), createRepository().startTracking(), null, null, null);
            bridge.execute(new byte[4]);
            Assertions.fail();
        } catch (VMException e) {
            Assertions.assertTrue(e.getMessage().contains("Invalid data given"));
        }
    }

    @Test
    public void receiveHeadersNotFromTheFederation() {
        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(PrecompiledContracts.BRIDGE_ADDR_STR))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(new ECKey().getPrivKeyBytes());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(rskTx, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        try {
            bridge.execute(Bridge.RECEIVE_HEADERS.encode());
            Assertions.fail();
        } catch (Exception ex) {
            Assertions.assertTrue(ex.getMessage().contains(ERR_NOT_FROM_ACTIVE_OR_RETIRING_FED));
        }

        //track.commit();
    }

    @Test
    public void receiveHeadersWithNonParseableHeader() throws VMException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(PrecompiledContracts.BRIDGE_ADDR_STR))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);

        Object[] objectArray = new Object[1];
        objectArray[0] = new byte[60];

        byte[] data = Bridge.RECEIVE_HEADERS.encode(new Object[]{objectArray});

        Assertions.assertNull(bridge.execute(data));

    }

    @Test
    public void receiveHeadersWithCorrectSizeHeaders() throws Exception {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(PrecompiledContracts.BRIDGE_ADDR_STR))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());

        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);
        Bridge bridge = spy(new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactoryMock));

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);

        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);

        final int numBlocks = 10;
        co.rsk.bitcoinj.core.BtcBlock[] headers = new co.rsk.bitcoinj.core.BtcBlock[numBlocks];

        for (int i = 0; i < numBlocks; i++) {
            co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(networkParameters, 1, PegTestUtils.createHash(), PegTestUtils.createHash(), 1, Utils.encodeCompactBits(networkParameters.getMaxTarget()), 1, new ArrayList<>()).cloneAsHeader();
            headers[i] = block;
        }

        byte[][] headersSerialized = new byte[headers.length][];

        for (int i = 0; i < headers.length; i++) {
            headersSerialized[i] = headers[i].bitcoinSerialize();
        }

        try (MockedStatic<BridgeUtils> bridgeUtilsMocked = mockStatic(BridgeUtils.class)) {
            bridgeUtilsMocked.when(() -> BridgeUtils.isFromFederateMember(any(), any())).thenReturn(true);

            MessageSerializer serializer = bridgeConstants.getBtcParams().getDefaultSerializer();
            MessageSerializer spySerializer = Mockito.spy(serializer);

            NetworkParameters btcParamsMock = mock(NetworkParameters.class);
            BridgeConstants bridgeConstantsMock = mock(BridgeConstants.class);

            when(bridgeConstantsMock.getBtcParams()).thenReturn(btcParamsMock);
            when(btcParamsMock.getDefaultSerializer()).thenReturn(spySerializer);

            TestUtils.setInternalState(bridge, "bridgeConstants", bridgeConstantsMock);

            bridge.init(rskTx, getGenesisBlock(), track, null, null, null);

            bridge.execute(Bridge.RECEIVE_HEADERS.encode(new Object[]{headersSerialized}));

            track.commit();

            verify(bridgeSupportMock, times(1)).receiveHeaders(headers);
            for (int i = 0; i < headers.length; i++) {
                verify(spySerializer, times(1)).makeBlock(headersSerialized[i]);
            }
        }
    }

    @Test
    public void receiveHeadersWithIncorrectSizeHeaders() throws Exception {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(PrecompiledContracts.BRIDGE_ADDR_STR))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(new ECKey().getPrivKeyBytes());


        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);

        Bridge spiedBridge = spy(new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactoryMock));
        spiedBridge.init(rskTx, getGenesisBlock(), track, null, null, null);

        final int numBlocks = 10;
        co.rsk.bitcoinj.core.BtcBlock[] headers = new co.rsk.bitcoinj.core.BtcBlock[numBlocks];
        byte[][] headersSerialized = new byte[headers.length][];

        // Add a couple of transactions to the block so that it doesn't serialize as just the header
        for (int i = 0; i < numBlocks; i++) {
            co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(
                    networkParameters,
                    1,
                    PegTestUtils.createHash(),
                    PegTestUtils.createHash(),
                    1,
                    Utils.encodeCompactBits(networkParameters.getMaxTarget()),
                    1,
                    new ArrayList<>()
            );

            BtcECKey from = new BtcECKey();
            BtcECKey to = new BtcECKey();

            // Coinbase TX
            BtcTransaction coinbaseTx = new BtcTransaction(networkParameters);
            coinbaseTx.addInput(Sha256Hash.ZERO_HASH, -1, ScriptBuilder.createOpReturnScript(new byte[0]));
            block.addTransaction(coinbaseTx);

            // Random TX
            BtcTransaction inputTx = new BtcTransaction(networkParameters);
            inputTx.addOutput(Coin.FIFTY_COINS, from.toAddress(networkParameters));
            BtcTransaction outputTx = new BtcTransaction(networkParameters);
            outputTx.addInput(inputTx.getOutput(0));
            outputTx.getInput(0).disconnect();
            outputTx.addOutput(Coin.COIN, to.toAddress(networkParameters));
            block.addTransaction(outputTx);

            headers[i] = block;
            headersSerialized[i] = block.bitcoinSerialize();

            // Make sure we would be able to deserialize the block
            Assertions.assertEquals(block, networkParameters.getDefaultSerializer().makeBlock(headersSerialized[i]));
        }

        try (MockedStatic<BridgeUtils> bridgeUtilsMocked = mockStatic(BridgeUtils.class)) {
            bridgeUtilsMocked.when(() -> BridgeUtils.isFromFederateMember(any(), any())).thenReturn(true);

            NetworkParameters btcParamsMock = mock(NetworkParameters.class);
            BridgeConstants bridgeConstantsMock = mock(BridgeConstants.class);

            TestUtils.setInternalState(spiedBridge, "bridgeConstants", bridgeConstantsMock);

            spiedBridge.execute(Bridge.RECEIVE_HEADERS.encode(new Object[]{headersSerialized}));

            track.commit();

            verify(bridgeSupportMock, never()).receiveHeaders(headers);
            verify(btcParamsMock, never()).getDefaultSerializer();
        }
    }

    @Test
    public void registerBtcTransactionNotFromFederation() {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(PrecompiledContracts.BRIDGE_ADDR_STR))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(new ECKey().getPrivKeyBytes());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(constants.getBridgeConstants().getBtcParams()),
                constants.getBridgeConstants(),
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);


        byte[] data = Bridge.REGISTER_BTC_TRANSACTION.encode(new byte[3], 1, new byte[30]);

        try {
            bridge.execute(data);
            Assertions.fail();
        } catch (Exception ex) {
            Assertions.assertTrue(ex.getMessage().contains(ERR_NOT_FROM_ACTIVE_OR_RETIRING_FED));
        }
    }

    @Test
    public void receiveHeadersWithHugeDeclaredTransactionsSize() throws VMException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(PrecompiledContracts.BRIDGE_ADDR_STR))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);

        NetworkParameters btcParams = RegTestParams.get();
        BtcBlock block = new BtcBlock(btcParams, 1, PegTestUtils.createHash(), PegTestUtils.createHash(), 1, 1, 1, new ArrayList<BtcTransaction>()) {
            @Override
            protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
                Utils.uint32ToByteStreamLE(getVersion(), stream);
                stream.write(getPrevBlockHash().getReversedBytes());
                stream.write(getMerkleRoot().getReversedBytes());
                Utils.uint32ToByteStreamLE(getTimeSeconds(), stream);
                Utils.uint32ToByteStreamLE(getDifficultyTarget(), stream);
                Utils.uint32ToByteStreamLE(getNonce(), stream);

                stream.write(new VarInt(Integer.MAX_VALUE).encode());
            }

            @Override
            public byte[] bitcoinSerialize() {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try {
                    bitcoinSerializeToStream(baos);
                } catch (IOException e) {
                }
                return baos.toByteArray();
            }
        };

        Object[] objectArray = new Object[1];
        objectArray[0] = block.bitcoinSerialize();

        byte[] data = Bridge.RECEIVE_HEADERS.encode(new Object[]{objectArray});

        Assertions.assertNull(bridge.execute(data));

    }

    @Test
    public void registerBtcTransactionWithNonParseableTx() throws VMException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(PrecompiledContracts.BRIDGE_ADDR_STR))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);


        byte[] data = Bridge.REGISTER_BTC_TRANSACTION.encode(new byte[3], 1, new byte[30]);

        Assertions.assertNull(bridge.execute(data));
    }

    @Test
    public void registerBtcTransactionWithHugeDeclaredInputsSize() throws VMException {
        NetworkParameters btcParams = RegTestParams.get();
        BtcTransaction tx = new HugeDeclaredSizeBtcTransaction(btcParams, true, false, false, false);
        registerBtcTransactionWithHugeDeclaredSize(tx);
    }

    @Test
    public void registerBtcTransactionWithHugeDeclaredOutputsSize() throws VMException {
        NetworkParameters btcParams = RegTestParams.get();
        BtcTransaction tx = new HugeDeclaredSizeBtcTransaction(btcParams, false, true, false, false);
        registerBtcTransactionWithHugeDeclaredSize(tx);
    }

    @Test
    public void registerBtcTransactionWithHugeDeclaredWitnessPushCountSize() throws VMException {
        NetworkParameters btcParams = RegTestParams.get();
        BtcTransaction tx = new HugeDeclaredSizeBtcTransaction(btcParams, false, false, true, false);
        registerBtcTransactionWithHugeDeclaredSize(tx);
    }

    @Test
    public void registerBtcTransactionWithHugeDeclaredWitnessPushSize() throws VMException {
        NetworkParameters btcParams = RegTestParams.get();
        BtcTransaction tx = new HugeDeclaredSizeBtcTransaction(btcParams, false, false, false, true);
        registerBtcTransactionWithHugeDeclaredSize(tx);
    }

    @Test
    public void registerBtcTransactionWithNonParseableMerkleeProof1() throws Exception {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(PrecompiledContracts.BRIDGE_ADDR_STR))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);

        NetworkParameters btcParams = RegTestParams.get();
        BtcTransaction tx = new BtcTransaction(btcParams);
        tx.addOutput(Coin.COIN, new BtcECKey().toAddress(btcParams));
        tx.addInput(PegTestUtils.createHash(), 0, ScriptBuilder.createInputScript(null, new BtcECKey()));

        byte[] data = Bridge.REGISTER_BTC_TRANSACTION.encode(tx.bitcoinSerialize(), 1, new byte[3]);

        Assertions.assertNull(bridge.execute(data));
    }

    @Test
    public void registerBtcTransactionWithNonParseableMerkleeProof2() throws VMException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(PrecompiledContracts.BRIDGE_ADDR_STR))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);

        NetworkParameters btcParams = RegTestParams.get();
        BtcTransaction tx = new BtcTransaction(btcParams);
        tx.addOutput(Coin.COIN, new BtcECKey().toAddress(btcParams));
        tx.addInput(PegTestUtils.createHash(), 0, ScriptBuilder.createInputScript(null, new BtcECKey()));

        byte[] data = Bridge.REGISTER_BTC_TRANSACTION.encode(tx.bitcoinSerialize(), 1, new byte[30]);

        Assertions.assertNull(bridge.execute(data));
    }

    @Test
    public void registerBtcTransactionWithHugeDeclaredSizeMerkleeProof() throws VMException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(PrecompiledContracts.BRIDGE_ADDR_STR))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);

        NetworkParameters btcParams = RegTestParams.get();
        BtcTransaction tx = new BtcTransaction(btcParams);
        tx.addOutput(Coin.COIN, new BtcECKey().toAddress(btcParams));
        tx.addInput(PegTestUtils.createHash(), 0, ScriptBuilder.createInputScript(null, new BtcECKey()));

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(Sha256Hash.wrap("0000000000000000000000000000000000000000000000000000000000000001"));
        hashes.add(Sha256Hash.wrap("0000000000000000000000000000000000000000000000000000000000000002"));
        hashes.add(Sha256Hash.wrap("0000000000000000000000000000000000000000000000000000000000000003"));
        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 3) {
            public void bitcoinSerializeToStream(OutputStream stream) throws IOException {
                uint32ToByteStreamLE(getTransactionCount(), stream);
                stream.write(new VarInt(Integer.MAX_VALUE).encode());
                //stream.write(new VarInt(hashes.size()).encode());
                for (Sha256Hash hash : hashes)
                    stream.write(hash.getReversedBytes());

                stream.write(new VarInt(bits.length).encode());
                stream.write(bits);
            }

        };
        byte[] pmtSerialized = pmt.bitcoinSerialize();

        byte[] data = Bridge.REGISTER_BTC_TRANSACTION.encode(tx.bitcoinSerialize(), 1, pmtSerialized);

        Assertions.assertNull(bridge.execute(data));
    }

    @Test
    public void getFederationAddress() throws Exception {
        // Case with genesis federation
        Federation federation = bridgeConstants.getGenesisFederation();
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        Transaction mockedTx = mock(Transaction.class);
        bridge.init(mockedTx, getGenesisBlock(), track, null, null, null);

        byte[] data = Bridge.GET_FEDERATION_ADDRESS.encode();

        Assertions.assertArrayEquals(Bridge.GET_FEDERATION_ADDRESS.encodeOutputs(federation.getAddress().toString()), bridge.execute(data));
    }

    @Test
    public void getMinimumLockTxValue() throws Exception {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        Transaction mockedTx = mock(Transaction.class);
        bridge.init(mockedTx, getGenesisBlock(), track, null, null, null);

        byte[] data = Bridge.GET_MINIMUM_LOCK_TX_VALUE.encode();

        Assertions.assertArrayEquals(Bridge.GET_MINIMUM_LOCK_TX_VALUE.encodeOutputs(bridgeConstants.getLegacyMinimumPeginTxValueInSatoshis().value), bridge.execute(data));
    }

    @Test
    public void addSignatureNotFromFederation() throws Exception {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(PrecompiledContracts.BRIDGE_ADDR_STR))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(new ECKey().getPrivKeyBytes());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);

        byte[] federatorPublicKeySerialized = new byte[3];
        Object[] signaturesObjectArray = new Object[0];
        byte[] rskTxHash = new byte[32];
        byte[] data = Bridge.ADD_SIGNATURE.encode(federatorPublicKeySerialized, signaturesObjectArray, rskTxHash);

        try {
            bridge.execute(data);
            Assertions.fail();
        } catch (Exception ex) {
            Assertions.assertTrue(ex.getMessage().contains(ERR_NOT_FROM_ACTIVE_OR_RETIRING_FED));
        }
    }

    @Test
    public void addSignatureWithNonParseablePublicKey() throws Exception {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(PrecompiledContracts.BRIDGE_ADDR_STR))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);

        byte[] federatorPublicKeySerialized = new byte[3];
        Object[] signaturesObjectArray = new Object[0];
        byte[] rskTxHash = new byte[32];
        byte[] data = Bridge.ADD_SIGNATURE.encode(federatorPublicKeySerialized, signaturesObjectArray, rskTxHash);

        Assertions.assertNull(bridge.execute(data));
    }

    @Test
    public void addSignatureWithEmptySignatureArray() throws Exception {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(PrecompiledContracts.BRIDGE_ADDR_STR))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);

        byte[] federatorPublicKeySerialized = new BtcECKey().getPubKey();
        Object[] signaturesObjectArray = new Object[0];
        byte[] rskTxHash = new byte[32];
        byte[] data = Bridge.ADD_SIGNATURE.encode(federatorPublicKeySerialized, signaturesObjectArray, rskTxHash);

        Assertions.assertNull(bridge.execute(data));
    }

    @Test
    public void addSignatureWithNonParseableSignature() throws Exception {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(PrecompiledContracts.BRIDGE_ADDR_STR))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(rskTx, new BlockGenerator().getGenesisBlock(), track, null, null, null);

        byte[] federatorPublicKeySerialized = new BtcECKey().getPubKey();
        Object[] signaturesObjectArray = new Object[]{new byte[3]};
        byte[] rskTxHash = new byte[32];
        byte[] data = Bridge.ADD_SIGNATURE.encode(federatorPublicKeySerialized, signaturesObjectArray, rskTxHash);

        Assertions.assertNull(bridge.execute(data));
    }

    @Test
    public void addSignatureWithNonParseableRskTx() throws Exception {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(PrecompiledContracts.BRIDGE_ADDR_STR))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(rskTx, new BlockGenerator().getGenesisBlock(), track, null, null, null);

        byte[] federatorPublicKeySerialized = new BtcECKey().getPubKey();
        Object[] signaturesObjectArray = new Object[]{new BtcECKey().sign(Sha256Hash.ZERO_HASH).encodeToDER()};
        byte[] rskTxHash = new byte[3];
        byte[] data = Bridge.ADD_SIGNATURE.encode(federatorPublicKeySerialized, signaturesObjectArray, rskTxHash);

        Assertions.assertNull(bridge.execute(data));
    }

    @Test
    public void exceptionInUpdateCollection() {
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig, null);

        try {
            bridge.updateCollections(null);
            Assertions.fail();
        } catch (VMException ex) {
            Assertions.assertEquals("Exception onBlock", ex.getMessage());
        }
    }

    @Test
    public void exceptionInReleaseBtc() {
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig, null);

        try {
            bridge.releaseBtc(null);
            Assertions.fail();
        } catch (VMException ex) {
            Assertions.assertEquals("Exception in releaseBtc", ex.getMessage());
        }
    }

    @Test
    public void exceptionInGetStateForBtcReleaseClient() {
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig, null);

        try {
            bridge.getStateForBtcReleaseClient(null);
            Assertions.fail();
        } catch (VMException ex) {
            Assertions.assertEquals("Exception in getStateForBtcReleaseClient", ex.getMessage());
        }
    }

    @Test
    public void exceptionInGetStateForDebugging() {
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig, null);

        try {
            bridge.getStateForDebugging(null);
            Assertions.fail();
        } catch (VMException ex) {
            Assertions.assertEquals("Exception in getStateForDebugging", ex.getMessage());
        }
    }

    @Test
    public void exceptionInGetBtcBlockchainBestChainHeight() {
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig, null);

        try {
            bridge.getBtcBlockchainBestChainHeight(null);
            Assertions.fail();
        } catch (VMException ex) {
            Assertions.assertEquals("Exception in getBtcBlockchainBestChainHeight", ex.getMessage());
        }
    }

    @Test
    public void exceptionInGetBtcBlockchainBlockLocator() {
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig, null);

        try {
            bridge.getBtcBlockchainBlockLocator(null);
            Assertions.fail();
        } catch (VMException ex) {
            Assertions.assertEquals("Exception in getBtcBlockchainBlockLocator", ex.getMessage());
        }
    }

    @Test
    public void getBtcBlockchainBlockLocatorBeforeRskip89Fork() throws Exception {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        String hashedString = "0000000000000000000000000000000000000000000000000000000000000001";

        Sha256Hash hash = Sha256Hash.wrap(hashedString);

        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);
        Bridge bridge = spy(new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactoryMock));

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportMock.getBtcBlockchainBlockLocator())
                .then((InvocationOnMock invocation) -> Arrays.asList(hash));

        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);

        bridge.init(mock(Transaction.class), getGenesisBlock(), track, null, null, null);

        byte[] result = bridge.execute(Bridge.GET_BTC_BLOCKCHAIN_BLOCK_LOCATOR.encode(new Object[]{}));
        Object[] decodedResult = (Object[]) BridgeMethods.GET_BTC_BLOCKCHAIN_BLOCK_LOCATOR.getFunction().decodeResult(result)[0];

        Assertions.assertEquals(1, decodedResult.length);
        Assertions.assertEquals(hashedString, decodedResult[0]);
    }

    @Test
    public void getBtcBlockchainBlockLocatorAfterRskip88And89Fork() {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP88), anyLong());
        doReturn(true).when(activationConfig).isActive(eq(RSKIP89), anyLong());
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);

        bridge.init(mock(Transaction.class), getGenesisBlock(), track, null, null, null);

        try {
            bridge.execute(Bridge.GET_BTC_BLOCKCHAIN_BLOCK_LOCATOR.encode(new Object[]{}));
            Assertions.fail();
        } catch (VMException e) {
            Assertions.assertTrue(e.getMessage().contains("Invalid data given:"));

        }
    }

    @Test
    public void getGasForDataFreeTx() {
        activationConfig = ActivationConfigsForTest.bridgeUnitTest();

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);

        org.ethereum.core.Transaction rskTx = CallTransaction.createCallTransaction(
                0,
                1,
                1,
                PrecompiledContracts.BRIDGE_ADDR,
                0,
                Bridge.UPDATE_COLLECTIONS,
                Constants.REGTEST_CHAIN_ID);
        rskTx.sign(BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS.get(0).getPrivKeyBytes());

        Block rskExecutionBlock = new BlockGenerator().createChildBlock(getGenesisInstance(config));

        Repository mockRepository = mock(Repository.class);

        bridge.init(rskTx, rskExecutionBlock, mockRepository, null, null, null);
        Assertions.assertEquals(0, bridge.getGasForData(rskTx.getData()));
    }

    @Test
    public void getGasForDataInvalidFunction() {
        getGasForDataPaidTx(23000, null);
    }

    @Test
    public void getGasForDataUpdateCollections() {
        getGasForDataPaidTx(48000 + 8, Bridge.UPDATE_COLLECTIONS);
    }

    @Test
    public void getGasForDataReceiveHeaders() {
        getGasForDataPaidTx(22000 + 8, Bridge.RECEIVE_HEADERS);
    }

    @Test
    public void getGasForDataRegisterBtcTransaction() {
        getGasForDataPaidTx(22000 + 228 * 2, Bridge.REGISTER_BTC_TRANSACTION, new byte[3], 1, new byte[3]);
    }

    @Test
    public void getGasForDataReleaseBtc() {
        getGasForDataPaidTx(23000 + 8, Bridge.RELEASE_BTC);
    }

    @Test
    public void getGasForDataAddSignature() {
        getGasForDataPaidTx(70000 + 548 * 2, Bridge.ADD_SIGNATURE, new byte[3], new byte[3][2], new byte[3]);
    }

    @Test
    public void getGasForDataGSFBRC() {
        getGasForDataPaidTx(4000 + 8, Bridge.GET_STATE_FOR_BTC_RELEASE_CLIENT);
    }

    @Test
    public void getGasForDataGSFD() {
        getGasForDataPaidTx(3_000_000 + 8, Bridge.GET_STATE_FOR_DEBUGGING);
    }

    @Test
    public void getGasForDataGBBBCH() {
        getGasForDataPaidTx(19000 + 8, Bridge.GET_BTC_BLOCKCHAIN_BEST_CHAIN_HEIGHT);
    }

    @Test
    public void getGasForDataGBBBL() {
        getGasForDataPaidTx(76000 + 8, Bridge.GET_BTC_BLOCKCHAIN_BLOCK_LOCATOR);
    }

    @Test
    public void getGasForDataGetFederationAddress() {
        getGasForDataPaidTx(11000 + 8, Bridge.GET_FEDERATION_ADDRESS);
    }

    @Test
    public void getGasForDataGetMinimumLockTxValue() {
        getGasForDataPaidTx(2000 + 8, Bridge.GET_MINIMUM_LOCK_TX_VALUE);
    }

    @Test
    public void isBtcTxHashAlreadyProcessed_normalFlow() throws IOException, VMException {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(null, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        Set<Sha256Hash> hashes = new HashSet<>();
        when(bridgeSupportMock.isBtcTxHashAlreadyProcessed(any(Sha256Hash.class))).then((InvocationOnMock invocation) -> hashes.contains(invocation.<Sha256Hash>getArgument(0)));

        hashes.add(Sha256Hash.of("hash_1".getBytes()));
        hashes.add(Sha256Hash.of("hash_2".getBytes()));
        hashes.add(Sha256Hash.of("hash_3".getBytes()));
        hashes.add(Sha256Hash.of("hash_4".getBytes()));

        for (Sha256Hash hash : hashes) {
            Assertions.assertTrue(bridge.isBtcTxHashAlreadyProcessed(new Object[]{hash.toString()}));
            verify(bridgeSupportMock).isBtcTxHashAlreadyProcessed(hash);
        }
        Assertions.assertFalse(bridge.isBtcTxHashAlreadyProcessed(new Object[]{Sha256Hash.of("anything".getBytes()).toString()}));
        Assertions.assertFalse(bridge.isBtcTxHashAlreadyProcessed(new Object[]{Sha256Hash.of("yetanotheranything".getBytes()).toString()}));
    }

    @Test
    public void isBtcTxHashAlreadyProcessed_exception() throws IOException {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(null, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);

        try {
            bridge.isBtcTxHashAlreadyProcessed(new Object[]{"notahash"});
            Assertions.fail();
        } catch (VMException e) {
        }
        verify(bridgeSupportMock, never()).isBtcTxHashAlreadyProcessed(any());
    }

    @Test
    public void getBtcTxHashProcessedHeight_normalFlow() throws IOException, VMException {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(null, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        Map<Sha256Hash, Long> hashes = new HashMap<>();
        when(bridgeSupportMock.getBtcTxHashProcessedHeight(any(Sha256Hash.class))).then((InvocationOnMock invocation) -> hashes.get(invocation.<Sha256Hash>getArgument(0)));

        hashes.put(Sha256Hash.of("hash_1".getBytes()), 1L);
        hashes.put(Sha256Hash.of("hash_2".getBytes()), 2L);
        hashes.put(Sha256Hash.of("hash_3".getBytes()), 3L);
        hashes.put(Sha256Hash.of("hash_4".getBytes()), 4L);

        for (Map.Entry<Sha256Hash, Long> entry : hashes.entrySet()) {
            Assertions.assertEquals(entry.getValue(), bridge.getBtcTxHashProcessedHeight(new Object[]{entry.getKey().toString()}));
            verify(bridgeSupportMock).getBtcTxHashProcessedHeight(entry.getKey());
        }
        Assertions.assertNull(bridge.getBtcTxHashProcessedHeight(new Object[]{Sha256Hash.of("anything".getBytes()).toString()}));
    }

    @Test
    public void getBtcTxHashProcessedHeight_exception() throws IOException {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(null, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);

        try {
            bridge.getBtcTxHashProcessedHeight(new Object[]{"notahash"});
            Assertions.fail();
        } catch (VMException e) {
        }
        verify(bridgeSupportMock, never()).getBtcTxHashProcessedHeight(any());
    }

    @Test
    public void getFederationSize() {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(null, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getFederationSize()).thenReturn(1234);

        Assertions.assertEquals(1234, bridge.getFederationSize(new Object[]{}).intValue());
    }

    @Test
    public void getFederationThreshold() {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(constants.getBridgeConstants().getBtcParams()),
                constants.getBridgeConstants(),
                activationConfig);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(null, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getFederationThreshold()).thenReturn(5678);

        Assertions.assertEquals(5678, bridge.getFederationThreshold(new Object[]{}).intValue());
    }

    @Test
    public void getFederationCreationTime() {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(null, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getFederationCreationTime()).thenReturn(Instant.ofEpochMilli(5000));

        Assertions.assertEquals(5000, bridge.getFederationCreationTime(new Object[]{}).intValue());
    }

    @Test
    public void getFederationCreationBlockNumber() {
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getFederationCreationBlockNumber()).thenReturn(42L);

        MatcherAssert.assertThat(bridge.getFederationCreationBlockNumber(new Object[]{}), is(42L));
    }

    @Test
    public void getFederatorPublicKey_beforeMultikey() throws VMException {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP123), anyLong());
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactoryMock);
        bridge.init(mock(Transaction.class), getGenesisBlock(), createRepository().startTracking(), null, null, null);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);

        when(bridgeSupportMock.getFederatorPublicKey(any(int.class))).then((InvocationOnMock invocation) ->
                BigInteger.valueOf(invocation.<Integer>getArgument(0)).toByteArray());
        bridge.init(mock(Transaction.class), getGenesisBlock(), null, null, null, null);

        Assertions.assertTrue(Arrays.equals(new byte[]{10},
                (byte[]) BridgeMethods.GET_FEDERATOR_PUBLIC_KEY.getFunction().decodeResult(
                        bridge.execute(BridgeMethods.GET_FEDERATOR_PUBLIC_KEY.getFunction().encode(new Object[]{BigInteger.valueOf(10)}))
                )[0]
        ));

        Assertions.assertTrue(Arrays.equals(new byte[]{20},
                (byte[]) BridgeMethods.GET_FEDERATOR_PUBLIC_KEY.getFunction().decodeResult(
                        bridge.execute(BridgeMethods.GET_FEDERATOR_PUBLIC_KEY.getFunction().encode(new Object[]{BigInteger.valueOf(20)}))
                )[0]
        ));

        Assertions.assertTrue(Arrays.equals(new byte[]{1, 0},
                (byte[]) BridgeMethods.GET_FEDERATOR_PUBLIC_KEY.getFunction().decodeResult(
                        bridge.execute(BridgeMethods.GET_FEDERATOR_PUBLIC_KEY.getFunction().encode(new Object[]{BigInteger.valueOf(256)}))
                )[0]
        ));
    }

    @Test
    public void getFederatorPublicKey_afterMultikey() throws VMException {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP123), anyLong());
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactoryMock);
        bridge.init(mock(Transaction.class), getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);

        Assertions.assertNull(bridge.execute(BridgeMethods.GET_FEDERATOR_PUBLIC_KEY.getFunction().encode(new Object[]{BigInteger.valueOf(10)})));
        verify(bridgeSupportMock, never()).getFederatorPublicKey(any(int.class));
    }

    @Test
    public void getFederatorPublicKeyOfType_beforeMultikey() throws VMException {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP123), anyLong());

        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactoryMock);

        bridge.init(mock(Transaction.class), getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);

        Assertions.assertNull(bridge.execute(BridgeMethods.GET_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().encode(new Object[]{BigInteger.valueOf(10), "btc"})));
        verify(bridgeSupportMock, never()).getFederatorPublicKeyOfType(any(int.class), any(FederationMember.KeyType.class));
    }

    @Test
    public void getFederatorPublicKeyOfType_afterMultikey() throws Exception {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP123), anyLong());

        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactoryMock);
        bridge.init(mock(Transaction.class), getGenesisBlock(), createRepository().startTracking(), null, null, null);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);
        when(bridgeSupportMock.getFederatorPublicKeyOfType(any(int.class), any(FederationMember.KeyType.class))).then((InvocationOnMock invocation) ->
                BigInteger.valueOf(invocation.<Number>getArgument(0).longValue()).toString()
                        .concat((invocation.<FederationMember.KeyType>getArgument(1)).getValue()).getBytes()
        );
        bridge.init(mock(Transaction.class), getGenesisBlock(), createRepository().startTracking(), null, null, null);

        Assertions.assertTrue(Arrays.equals("10btc".getBytes(),
                (byte[]) BridgeMethods.GET_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().decodeResult(
                        bridge.execute(BridgeMethods.GET_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().encode(new Object[]{BigInteger.valueOf(10), "btc"}))
                )[0]
        ));

        Assertions.assertTrue(Arrays.equals("200rsk".getBytes(),
                (byte[]) BridgeMethods.GET_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().decodeResult(
                        bridge.execute(BridgeMethods.GET_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().encode(new Object[]{BigInteger.valueOf(200), "rsk"}))
                )[0]
        ));

        Assertions.assertTrue(Arrays.equals("172mst".getBytes(),
                (byte[]) BridgeMethods.GET_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().decodeResult(
                        bridge.execute(BridgeMethods.GET_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().encode(new Object[]{BigInteger.valueOf(172), "mst"}))
                )[0]
        ));
    }

    @Test
    public void getRetiringFederationSize() {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(null, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getRetiringFederationSize()).thenReturn(1234);

        Assertions.assertEquals(1234, bridge.getRetiringFederationSize(new Object[]{}).intValue());
    }

    @Test
    public void getRetiringFederationThreshold() {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(constants.getBridgeConstants().getBtcParams()),
                constants.getBridgeConstants(),
                activationConfig);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(null, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getRetiringFederationThreshold()).thenReturn(5678);

        Assertions.assertEquals(5678, bridge.getRetiringFederationThreshold(new Object[]{}).intValue());
    }

    @Test
    public void getRetiringFederationCreationTime() {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(null, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getRetiringFederationCreationTime()).thenReturn(Instant.ofEpochMilli(5000));

        Assertions.assertEquals(5000, bridge.getRetiringFederationCreationTime(new Object[]{}).intValue());
    }

    @Test
    public void getRetiringFederationCreationBlockNumber() {
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getRetiringFederationCreationBlockNumber()).thenReturn(42L);

        MatcherAssert.assertThat(bridge.getRetiringFederationCreationBlockNumber(new Object[]{}), is(42L));
    }

    @Test
    public void getRetiringFederatorPublicKey_beforeMultikey() throws Exception {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP123), anyLong());
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactoryMock);
        bridge.init(mock(Transaction.class), getGenesisBlock(), createRepository().startTracking(), null, null, null);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);
        when(bridgeSupportMock.getRetiringFederatorPublicKey(any(int.class))).then((InvocationOnMock invocation) ->
                BigInteger.valueOf(invocation.<Integer>getArgument(0)).toByteArray());
        bridge.init(mock(Transaction.class), getGenesisBlock(), createRepository().startTracking(), null, null, null);


        Assertions.assertTrue(Arrays.equals(new byte[]{10},
                (byte[]) BridgeMethods.GET_RETIRING_FEDERATOR_PUBLIC_KEY.getFunction().decodeResult(
                        bridge.execute(BridgeMethods.GET_RETIRING_FEDERATOR_PUBLIC_KEY.getFunction().encode(new Object[]{BigInteger.valueOf(10)}))
                )[0]
        ));

        Assertions.assertTrue(Arrays.equals(new byte[]{20},
                (byte[]) BridgeMethods.GET_RETIRING_FEDERATOR_PUBLIC_KEY.getFunction().decodeResult(
                        bridge.execute(BridgeMethods.GET_RETIRING_FEDERATOR_PUBLIC_KEY.getFunction().encode(new Object[]{BigInteger.valueOf(20)}))
                )[0]
        ));

        Assertions.assertTrue(Arrays.equals(new byte[]{1, 0},
                (byte[]) BridgeMethods.GET_RETIRING_FEDERATOR_PUBLIC_KEY.getFunction().decodeResult(
                        bridge.execute(BridgeMethods.GET_RETIRING_FEDERATOR_PUBLIC_KEY.getFunction().encode(new Object[]{BigInteger.valueOf(256)}))
                )[0]
        ));
    }

    @Test
    public void getRetiringFederatorPublicKey_afterMultikey() throws Exception {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP123), anyLong());
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactoryMock);

        bridge.init(mock(Transaction.class), getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);

        Assertions.assertNull(bridge.execute(BridgeMethods.GET_RETIRING_FEDERATOR_PUBLIC_KEY.getFunction().encode(new Object[]{BigInteger.valueOf(10)})));
        verify(bridgeSupportMock, never()).getRetiringFederatorPublicKey(any(int.class));
    }

    @Test
    public void getRetiringFederatorPublicKeyOfType_beforeMultikey() throws Exception {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP123), anyLong());
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactoryMock);

        bridge.init(mock(Transaction.class), getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);

        Assertions.assertNull(bridge.execute(BridgeMethods.GET_RETIRING_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().encode(new Object[]{BigInteger.valueOf(10), "btc"})));
        verify(bridgeSupportMock, never()).getRetiringFederatorPublicKeyOfType(any(int.class), any(FederationMember.KeyType.class));
    }

    @Test
    public void getRetiringFederatorPublicKeyOfType_afterMultikey() throws Exception {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP123), anyLong());
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactoryMock);

        bridge.init(mock(Transaction.class), getGenesisBlock(), createRepository().startTracking(), null, null, null);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);
        when(bridgeSupportMock.getRetiringFederatorPublicKeyOfType(any(int.class), any(FederationMember.KeyType.class))).then((InvocationOnMock invocation) ->
                BigInteger.valueOf(invocation.<Number>getArgument(0).longValue()).toString()
                        .concat((invocation.<FederationMember.KeyType>getArgument(1)).getValue()).getBytes()
        );
        bridge.init(mock(Transaction.class), getGenesisBlock(), createRepository().startTracking(), null, null, null);

        Assertions.assertTrue(Arrays.equals("10btc".getBytes(),
                (byte[]) BridgeMethods.GET_RETIRING_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().decodeResult(
                        bridge.execute(BridgeMethods.GET_RETIRING_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().encode(new Object[]{BigInteger.valueOf(10), "btc"}))
                )[0]
        ));

        Assertions.assertTrue(Arrays.equals("105rsk".getBytes(),
                (byte[]) BridgeMethods.GET_RETIRING_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().decodeResult(
                        bridge.execute(BridgeMethods.GET_RETIRING_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().encode(new Object[]{BigInteger.valueOf(105), "rsk"}))
                )[0]
        ));

        Assertions.assertTrue(Arrays.equals("232mst".getBytes(),
                (byte[]) BridgeMethods.GET_RETIRING_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().decodeResult(
                        bridge.execute(BridgeMethods.GET_RETIRING_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().encode(new Object[]{BigInteger.valueOf(232), "mst"}))
                )[0]
        ));
    }

    @Test
    public void getPendingFederationSize() {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(constants.getBridgeConstants().getBtcParams()),
                constants.getBridgeConstants(),
                activationConfig);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(null, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getPendingFederationSize()).thenReturn(1234);

        Assertions.assertEquals(1234, bridge.getPendingFederationSize(new Object[]{}).intValue());
    }

    @Test
    public void getPendingFederatorPublicKey_beforeMultikey() throws Exception {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP123), anyLong());
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactoryMock);

        bridge.init(mock(Transaction.class), getGenesisBlock(), createRepository().startTracking(), null, null, null);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);
        when(bridgeSupportMock.getPendingFederatorPublicKey(any(int.class))).then((InvocationOnMock invocation) ->
                BigInteger.valueOf(invocation.<Integer>getArgument(0)).toByteArray());
        bridge.init(mock(Transaction.class), getGenesisBlock(), createRepository().startTracking(), null, null, null);

        Assertions.assertTrue(Arrays.equals(new byte[]{10},
                (byte[]) BridgeMethods.GET_PENDING_FEDERATOR_PUBLIC_KEY.getFunction().decodeResult(
                        bridge.execute(BridgeMethods.GET_PENDING_FEDERATOR_PUBLIC_KEY.getFunction().encode(new Object[]{BigInteger.valueOf(10)}))
                )[0]
        ));

        Assertions.assertTrue(Arrays.equals(new byte[]{20},
                (byte[]) BridgeMethods.GET_PENDING_FEDERATOR_PUBLIC_KEY.getFunction().decodeResult(
                        bridge.execute(BridgeMethods.GET_PENDING_FEDERATOR_PUBLIC_KEY.getFunction().encode(new Object[]{BigInteger.valueOf(20)}))
                )[0]
        ));

        Assertions.assertTrue(Arrays.equals(new byte[]{1, 0},
                (byte[]) BridgeMethods.GET_PENDING_FEDERATOR_PUBLIC_KEY.getFunction().decodeResult(
                        bridge.execute(BridgeMethods.GET_PENDING_FEDERATOR_PUBLIC_KEY.getFunction().encode(new Object[]{BigInteger.valueOf(256)}))
                )[0]
        ));
    }

    @Test
    public void getPendingFederatorPublicKey_afterMultikey() throws Exception {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP123), anyLong());
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactoryMock);

        bridge.init(mock(Transaction.class), getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);

        Assertions.assertNull(bridge.execute(BridgeMethods.GET_PENDING_FEDERATOR_PUBLIC_KEY.getFunction().encode(new Object[]{BigInteger.valueOf(10)})));
        verify(bridgeSupportMock, never()).getPendingFederatorPublicKey(any(int.class));
    }

    @Test
    public void getPendingFederatorPublicKeyOfType_beforeMultikey() throws Exception {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP123), anyLong());
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactoryMock);

        bridge.init(mock(Transaction.class), getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);

        Assertions.assertNull(bridge.execute(BridgeMethods.GET_PENDING_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().encode(new Object[]{BigInteger.valueOf(10), "btc"})));
        verify(bridgeSupportMock, never()).getPendingFederatorPublicKeyOfType(any(int.class), any(FederationMember.KeyType.class));
    }

    @Test
    public void getPendingFederatorPublicKeyOfType_afterMultikey() throws Exception {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP123), anyLong());
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactoryMock);

        bridge.init(mock(Transaction.class), getGenesisBlock(), createRepository().startTracking(), null, null, null);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);
        when(bridgeSupportMock.getPendingFederatorPublicKeyOfType(any(int.class), any(FederationMember.KeyType.class))).then((InvocationOnMock invocation) ->
                BigInteger.valueOf(invocation.<Number>getArgument(0).longValue()).toString()
                        .concat((invocation.<FederationMember.KeyType>getArgument(1)).getValue()).getBytes()
        );
        bridge.init(mock(Transaction.class), getGenesisBlock(), createRepository().startTracking(), null, null, null);

        Assertions.assertTrue(Arrays.equals("10btc".getBytes(),
                (byte[]) BridgeMethods.GET_PENDING_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().decodeResult(
                        bridge.execute(BridgeMethods.GET_PENDING_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().encode(new Object[]{BigInteger.valueOf(10), "btc"}))
                )[0]
        ));

        Assertions.assertTrue(Arrays.equals("82rsk".getBytes(),
                (byte[]) BridgeMethods.GET_PENDING_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().decodeResult(
                        bridge.execute(BridgeMethods.GET_PENDING_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().encode(new Object[]{BigInteger.valueOf(82), "rsk"}))
                )[0]
        ));

        Assertions.assertTrue(Arrays.equals("123mst".getBytes(),
                (byte[]) BridgeMethods.GET_PENDING_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().decodeResult(
                        bridge.execute(BridgeMethods.GET_PENDING_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction().encode(new Object[]{BigInteger.valueOf(123), "mst"}))
                )[0]
        ));
    }

    @Test
    public void createFederation() throws BridgeIllegalArgumentException {
        Transaction txMock = mock(Transaction.class);
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(constants.getBridgeConstants().getBtcParams()),
                constants.getBridgeConstants(),
                activationConfig);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(txMock, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.voteFederationChange(txMock, new ABICallSpec("create", new byte[][]{}))).thenReturn(123);

        Assertions.assertEquals(123, bridge.createFederation(new Object[]{}).intValue());
    }

    @Test
    public void addFederatorPublicKey_beforeMultikey() throws Exception {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP123), anyLong());

        Transaction txMock = mock(Transaction.class);

        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactoryMock);

        bridge.init(txMock, getGenesisBlock(), createRepository().startTracking(), null, null, null);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);
        when(bridgeSupportMock.voteFederationChange(txMock, new ABICallSpec("add", new byte[][]{Hex.decode("aabbccdd")})))
                .thenReturn(123);
        bridge.init(txMock, getGenesisBlock(), null, null, null, null);

        Assertions.assertEquals(123,
                ((BigInteger) BridgeMethods.ADD_FEDERATOR_PUBLIC_KEY.getFunction().decodeResult(
                        bridge.execute(BridgeMethods.ADD_FEDERATOR_PUBLIC_KEY.getFunction().encode(new Object[]{Hex.decode("aabbccdd")}))
                )[0]).intValue()
        );
    }

    @Test
    public void addFederatorPublicKey_afterMultikey() throws Exception {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP123), anyLong());

        Transaction txMock = mock(Transaction.class);
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactoryMock);


        bridge.init(txMock, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);

        Assertions.assertNull(
                bridge.execute(BridgeMethods.ADD_FEDERATOR_PUBLIC_KEY.getFunction().encode(new Object[]{Hex.decode("aabbccdd")}))
        );

        verify(bridgeSupportMock, never()).voteFederationChange(any(Transaction.class), any(ABICallSpec.class));
    }

    @Test
    public void addFederatorPublicKeyMultikey_beforeMultikey() throws Exception {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP123), anyLong());

        Transaction txMock = mock(Transaction.class);
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactoryMock);


        bridge.init(txMock, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);

        Assertions.assertNull(
                bridge.execute(BridgeMethods.ADD_FEDERATOR_PUBLIC_KEY_MULTIKEY.getFunction().encode(new Object[]{
                        Hex.decode("aabb"), Hex.decode("ccdd"), Hex.decode("eeff")
                }))
        );

        verify(bridgeSupportMock, never()).voteFederationChange(any(Transaction.class), any(ABICallSpec.class));
    }

    @Test
    public void addFederatorPublicKeyMultikey_afterMultikey() throws Exception {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP123), anyLong());

        Transaction txMock = mock(Transaction.class);

        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactoryMock);

        bridge.init(txMock, getGenesisBlock(), createRepository().startTracking(), null, null, null);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);
        when(bridgeSupportMock.voteFederationChange(txMock, new ABICallSpec("add-multi", new byte[][]{
                Hex.decode("aabb"), Hex.decode("ccdd"), Hex.decode("eeff")
        }))).thenReturn(123);
        bridge.init(txMock, getGenesisBlock(), createRepository().startTracking(), null, null, null);

        Assertions.assertEquals(123,
                ((BigInteger) BridgeMethods.ADD_FEDERATOR_PUBLIC_KEY_MULTIKEY.getFunction().decodeResult(
                        bridge.execute(BridgeMethods.ADD_FEDERATOR_PUBLIC_KEY_MULTIKEY.getFunction().encode(new Object[]{
                                Hex.decode("aabb"), Hex.decode("ccdd"), Hex.decode("eeff")
                        }))
                )[0]).intValue()
        );
    }

    @Test
    public void commitFederation_ok() throws BridgeIllegalArgumentException {
        Transaction txMock = mock(Transaction.class);
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(txMock, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);

        when(bridgeSupportMock.voteFederationChange(txMock, new ABICallSpec("commit", new byte[][]{Hex.decode("01020304")}))).thenReturn(123);

        Assertions.assertEquals(123, bridge.commitFederation(new Object[]{Hex.decode("01020304")}).intValue());
    }

    @Test
    public void commitFederation_wrongParameterType() throws BridgeIllegalArgumentException {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(null, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);

        Assertions.assertEquals(-10, bridge.commitFederation(new Object[]{"i'm not a byte array"}).intValue());
        verify(bridgeSupportMock, never()).voteFederationChange(any(), any());
    }

    @Test
    public void rollbackFederation() throws BridgeIllegalArgumentException {
        Transaction txMock = mock(Transaction.class);
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(constants.getBridgeConstants().getBtcParams()),
                constants.getBridgeConstants(),
                activationConfig);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(txMock, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.voteFederationChange(txMock, new ABICallSpec("rollback", new byte[][]{}))).thenReturn(456);

        Assertions.assertEquals(456, bridge.rollbackFederation(new Object[]{}).intValue());
    }

    @Test
    public void getLockWhitelistSize() {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(null, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getLockWhitelistSize()).thenReturn(1234);

        Assertions.assertEquals(1234, bridge.getLockWhitelistSize(new Object[]{}).intValue());
    }

    @Test
    public void getLockWhitelistAddress() {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(constants.getBridgeConstants().getBtcParams()),
                constants.getBridgeConstants(),
                activationConfig);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(null, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        OneOffWhiteListEntry mockedEntry10 = new OneOffWhiteListEntry(new BtcECKey().toAddress(networkParameters), Coin.COIN);
        OneOffWhiteListEntry mockedEntry20 = new OneOffWhiteListEntry(new BtcECKey().toAddress(networkParameters), Coin.COIN);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getLockWhitelistEntryByIndex(10)).then((InvocationOnMock invocation) -> mockedEntry10);
        when(bridgeSupportMock.getLockWhitelistEntryByIndex(20)).then((InvocationOnMock invocation) -> mockedEntry20);

        Assertions.assertEquals(mockedEntry10.address().toBase58(), bridge.getLockWhitelistAddress(new Object[]{BigInteger.valueOf(10)}));
        Assertions.assertEquals(mockedEntry20.address().toBase58(), bridge.getLockWhitelistAddress(new Object[]{BigInteger.valueOf(20)}));
    }

    @Test
    public void getLockWhitelistEntryByAddressBeforeRskip87And88Fork() throws VMException {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP87), anyLong());
        doReturn(false).when(activationConfig).isActive(eq(RSKIP88), anyLong());
        Address address = new BtcECKey().toAddress(networkParameters);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction mockedTransaction = mock(Transaction.class);

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(constants.getBridgeConstants().getBtcParams()),
                constants.getBridgeConstants(),
                activationConfig);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(mockedTransaction, getGenesisBlock(), track, null, null, null);

        Assertions.assertNull(bridge.execute(Bridge.GET_LOCK_WHITELIST_ENTRY_BY_ADDRESS.encode(new Object[]{address.toBase58()})));
    }

    @Test
    public void getLockWhitelistEntryByAddressAfterRskip87Fork() throws Exception {
        byte[] result;
        Transaction mockedTransaction;

        doReturn(true).when(activationConfig).isActive(eq(RSKIP87), anyLong());
        doReturn(false).when(activationConfig).isActive(eq(RSKIP88), anyLong());
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Address mockedAddressForUnlimited = new BtcECKey().toAddress(networkParameters);
        Address mockedAddressForOneOff = new BtcECKey().toAddress(networkParameters);


        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportMock.getLockWhitelistEntryByAddress(mockedAddressForUnlimited.toBase58()))
                .then((InvocationOnMock invocation) -> new UnlimitedWhiteListEntry(mockedAddressForUnlimited));
        when(bridgeSupportMock.getLockWhitelistEntryByAddress(mockedAddressForOneOff.toBase58()))
                .then((InvocationOnMock invocation) -> new OneOffWhiteListEntry(mockedAddressForOneOff, Coin.COIN));

        mockedTransaction = mock(Transaction.class);

        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);
        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactoryMock);
        bridge.init(mockedTransaction, getGenesisBlock(), track, null, null, null);

        // Get the unlimited whitelist address
        result = bridge.execute(Bridge.GET_LOCK_WHITELIST_ENTRY_BY_ADDRESS.encode(new Object[]{mockedAddressForUnlimited.toBase58()}));
        BigInteger decodedResult = (BigInteger) BridgeMethods.GET_LOCK_WHITELIST_ENTRY_BY_ADDRESS.getFunction().decodeResult(result)[0];

        Assertions.assertEquals(0, decodedResult.longValue());

        // Get the one-off whitelist address
        result = bridge.execute(Bridge.GET_LOCK_WHITELIST_ENTRY_BY_ADDRESS.encode(new Object[]{mockedAddressForOneOff.toBase58()}));
        decodedResult = (BigInteger) BridgeMethods.GET_LOCK_WHITELIST_ENTRY_BY_ADDRESS.getFunction().decodeResult(result)[0];

        Assertions.assertEquals(Coin.COIN.value, decodedResult.longValue());

        // Try fetch an unexisting address
        result = bridge.execute(Bridge.GET_LOCK_WHITELIST_ENTRY_BY_ADDRESS.encode(new Object[]{(new BtcECKey().toAddress(networkParameters)).toBase58()}));
        decodedResult = (BigInteger) BridgeMethods.GET_LOCK_WHITELIST_ENTRY_BY_ADDRESS.getFunction().decodeResult(result)[0];

        Assertions.assertEquals(-1, decodedResult.longValue());
    }

    @Test
    public void addLockWhitelistAddressBeforeRskip87Fork() throws VMException {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP87), anyLong());
        doReturn(false).when(activationConfig).isActive(eq(RSKIP88), anyLong());

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction mockedTransaction = mock(Transaction.class);
        // Just setting a random address as the sender
        RskAddress sender = new RskAddress(fedECPrivateKey.getAddress());
        when(mockedTransaction.getSender()).thenReturn(sender);

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(mockedTransaction, getGenesisBlock(), track, null, null, null);

        byte[] result = bridge.execute(Bridge.ADD_LOCK_WHITELIST_ADDRESS.encode(new Object[]{
                new BtcECKey().toAddress(networkParameters).toBase58(),
                BigInteger.valueOf(Coin.COIN.getValue())
        }));

        BigInteger decodedResult = (BigInteger) BridgeMethods.ADD_LOCK_WHITELIST_ADDRESS.getFunction().decodeResult(result)[0];

        Assertions.assertEquals(BridgeSupport.LOCK_WHITELIST_GENERIC_ERROR_CODE.intValue(), decodedResult.intValue());
    }

    @Test
    public void addLockWhitelistAddressAfterRskip87And88Fork() {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP87), anyLong());
        doReturn(true).when(activationConfig).isActive(eq(RSKIP88), anyLong());

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction mockedTransaction = mock(Transaction.class);
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(mockedTransaction, getGenesisBlock(), track, null, null, null);

        try {
            bridge.execute(Bridge.ADD_LOCK_WHITELIST_ADDRESS.encode(new Object[]{"i-am-an-address", BigInteger.valueOf(25L)}));
            Assertions.fail();
        } catch (Exception e) {
            Throwable causeException = e.getCause();
            Assertions.assertEquals(BridgeIllegalArgumentException.class, causeException.getClass());
            Assertions.assertTrue(causeException.getMessage().startsWith("Invalid data given"));
        }
    }

    @Test
    public void addOneOffLockWhitelistAddressBeforeRskip87And88Fork() throws VMException {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP87), anyLong());
        doReturn(false).when(activationConfig).isActive(eq(RSKIP88), anyLong());

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction mockedTransaction = mock(Transaction.class);
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(constants.getBridgeConstants().getBtcParams()),
                constants.getBridgeConstants(),
                activationConfig);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(mockedTransaction, getGenesisBlock(), track, null, null, null);

        Assertions.assertNull(bridge.execute(Bridge.ADD_ONE_OFF_LOCK_WHITELIST_ADDRESS.encode(new Object[]{"i-am-an-address", BigInteger.valueOf(25L)})));
    }

    @Test
    public void addOneOffLockWhitelistAddressAfterRskip87Fork() throws VMException {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP87), anyLong());
        doReturn(false).when(activationConfig).isActive(eq(RSKIP88), anyLong());

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction mockedTransaction = mock(Transaction.class);
        // Just setting a random address as the sender
        RskAddress sender = new RskAddress(fedECPrivateKey.getAddress());
        when(mockedTransaction.getSender()).thenReturn(sender);

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(mockedTransaction, getGenesisBlock(), track, null, null, null);

        byte[] result = bridge.execute(Bridge.ADD_ONE_OFF_LOCK_WHITELIST_ADDRESS.encode(new Object[]{
                new BtcECKey().toAddress(networkParameters).toBase58(),
                BigInteger.valueOf(Coin.COIN.getValue())
        }));

        BigInteger decodedResult = (BigInteger) BridgeMethods.ADD_ONE_OFF_LOCK_WHITELIST_ADDRESS.getFunction().decodeResult(result)[0];

        Assertions.assertEquals(BridgeSupport.LOCK_WHITELIST_GENERIC_ERROR_CODE.intValue(), decodedResult.intValue());
    }

    @Test
    public void addUnlimitedLockWhitelistAddressBeforeRskip87And88Fork() throws VMException {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP87), anyLong());
        doReturn(false).when(activationConfig).isActive(eq(RSKIP88), anyLong());

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction mockedTransaction = mock(Transaction.class);
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(mockedTransaction, getGenesisBlock(), track, null, null, null);

        Assertions.assertNull(bridge.execute(Bridge.ADD_UNLIMITED_LOCK_WHITELIST_ADDRESS.encode(new Object[]{"i-am-an-address"})));
    }

    @Test
    public void addUnlimitedLockWhitelistAddressAfterRskip87Fork() throws VMException {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP87), anyLong());
        doReturn(false).when(activationConfig).isActive(eq(RSKIP88), anyLong());

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction mockedTransaction = mock(Transaction.class);
        // Just setting a random address as the sender
        RskAddress sender = new RskAddress(fedECPrivateKey.getAddress());
        when(mockedTransaction.getSender()).thenReturn(sender);

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(mockedTransaction, getGenesisBlock(), track, null, null, null);

        byte[] result = bridge.execute(Bridge.ADD_UNLIMITED_LOCK_WHITELIST_ADDRESS.encode(new Object[]{
                new BtcECKey().toAddress(networkParameters).toBase58()
        }));

        BigInteger decodedResult = (BigInteger) BridgeMethods.ADD_UNLIMITED_LOCK_WHITELIST_ADDRESS.getFunction().decodeResult(result)[0];
        bridge.init(mockedTransaction, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);

        Assertions.assertEquals(BridgeSupport.LOCK_WHITELIST_GENERIC_ERROR_CODE.intValue(), decodedResult.intValue());
    }

    @Test
    public void removeLockWhitelistAddress() {
        Transaction mockedTransaction = mock(Transaction.class);
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(mockedTransaction, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.removeLockWhitelistAddress(mockedTransaction, "i-am-an-address")).thenReturn(1234);

        Assertions.assertEquals(1234, bridge.removeLockWhitelistAddress(new Object[]{"i-am-an-address"}).intValue());
    }

    @Test
    public void getFeePerKb() {
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getFeePerKb())
                .thenReturn(Coin.valueOf(12345678901234L));

        Assertions.assertEquals(12345678901234L, bridge.getFeePerKb(new Object[]{}));
    }

    @Test
    public void voteFeePerKb_ok() {
        Transaction txMock = mock(Transaction.class);
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(constants.getBridgeConstants().getBtcParams()),
                constants.getBridgeConstants(),
                activationConfig);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(txMock, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.voteFeePerKbChange(txMock, Coin.valueOf(2)))
                .thenReturn(123);

        Assertions.assertEquals(123, bridge.voteFeePerKbChange(new Object[]{BigInteger.valueOf(2)}).intValue());
    }

    @Test
    public void voteFeePerKb_wrongParameterType() throws BridgeIllegalArgumentException {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(constants.getBridgeConstants().getBtcParams()),
                constants.getBridgeConstants(),
                activationConfig);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(null, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);

        Assertions.assertEquals(-10, bridge.voteFeePerKbChange(new Object[]{"i'm not a byte array"}).intValue());
        verify(bridgeSupportMock, never()).voteFederationChange(any(), any());
    }

    @Test
    public void precompiledContractAddress() {
        Assertions.assertArrayEquals(
                PrecompiledContracts.BRIDGE_ADDR.getBytes(),
                Hex.decode(PrecompiledContracts.BRIDGE_ADDR_STR));
        Assertions.assertArrayEquals(
                PrecompiledContracts.BRIDGE_ADDR.getBytes(),
                HexUtils.stringHexToByteArray(PrecompiledContracts.BRIDGE_ADDR_STR));
    }

    @Test
    public void testBlock457BridgeCall() throws Exception {
        // block 457 in mainnet exposed a bug in a fix made to SolidityType. The purpose of this test is to make sure this block keeps working
        // block 457 was the first federate call.
        byte[] data = Files.readAllBytes(Paths.get(this.getClass().getResource("/bridge/block457.bin").toURI()));

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = spy(Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(PrecompiledContracts.BRIDGE_ADDR_STR))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build());
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());

        // Setup bridge
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);

        Genesis genesisBlock = new BlockGenerator().getGenesisBlock();
        ActivationConfig.ForBlock spiedActivations = spy(activationConfig.forBlock(genesisBlock.getNumber()));
        when(activationConfig.forBlock(genesisBlock.getNumber())).thenReturn(spiedActivations);

        bridge.init(rskTx, genesisBlock, track, null, null, null);

        bridge.execute(data);
        verify(spiedActivations, times(1)).isActive(ConsensusRule.RSKIP88);
        verify(rskTx, never()).isLocalCallTransaction();
    }

    @Test
    public void executeMethodWithOnlyLocalCallsAllowed_localCallTx() throws Exception {
        Transaction tx = mock(Transaction.class);
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactoryMock);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Address address = new BtcECKey().toAddress(networkParameters);
        when(bridgeSupportMock.getFederationAddress()).thenReturn(address);
        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);

        bridge.init(tx, getGenesisBlock(), null, null, null, null);

        byte[] data = BridgeMethods.GET_FEDERATION_ADDRESS.getFunction().encode(new Object[]{});
        bridge.execute(data);

        verify(bridgeSupportMock, times(1)).getFederationAddress();
    }

    @Test
    public void executeMethodWithOnlyLocalCallsAllowed_nonLocalCallTx_beforeOrchid() throws Exception {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP88), anyLong());

        Transaction tx = mock(Transaction.class);

        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactoryMock);

        Address expectedResult = new BtcECKey().toAddress(networkParameters);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportMock.getFederationAddress()).thenReturn(expectedResult);
        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);

        bridge.init(tx, getGenesisBlock(), null, null, null, null);

        byte[] data = BridgeMethods.GET_FEDERATION_ADDRESS.getFunction().encode(new Object[]{});
        String result = (String) BridgeMethods.GET_FEDERATION_ADDRESS.getFunction().decodeResult(bridge.execute(data))[0];
        Assertions.assertEquals(expectedResult.toBase58(), result);
        bridge.execute(data);

        // TODO improve test
        Assertions.assertNotNull(data);
    }

    @Test
    public void executeMethodWithOnlyLocalCallsAllowed_nonLocalCallTx() {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP88), anyLong());

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);

        try {
            Transaction tx = mock(Transaction.class);
            when(tx.isLocalCallTransaction()).thenReturn(false);
            BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

            Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                    bridgeSupportFactoryMock);
            when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);
            bridge.init(tx, getGenesisBlock(), null, null, null, null);

            byte[] data = BridgeMethods.GET_FEDERATION_ADDRESS.getFunction().encode(new Object[]{});
            bridge.execute(data);
            Assertions.fail();
        } catch (VMException e) {
            verify(bridgeSupportMock, never()).getFederationAddress();
            Assertions.assertTrue(e.getMessage().contains("Non-local-call"));
        }
    }

    @Test
    public void executeMethodWithAnyCallsAllowed_localCallTx() throws Exception {
        executeAndCheckMethodWithAnyCallsAllowed(true);
    }

    @Test
    public void executeMethodWithAnyCallsAllowed_nonLocalCallTx() throws Exception {
        executeAndCheckMethodWithAnyCallsAllowed(false);
    }

    @Test
    public void getBtcTransactionConfirmationsBeforeWasabi() throws Exception {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP122), anyLong());

        Transaction txMock = mock(Transaction.class);
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactoryMock);


        bridge.init(txMock, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);

        byte[][] arr = new byte[1][];
        arr[0] = new byte[]{};
        Object[] params = new Object[]{new byte[0], new byte[0], BigInteger.valueOf(1), arr};
        Assertions.assertNull(bridge.execute(Bridge.GET_BTC_TRANSACTION_CONFIRMATIONS.encode(params)));
    }

    @Test
    public void getBtcTransactionConfirmationsAfterWasabi_ok() throws Exception {
        Transaction txMock = mock(Transaction.class);
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        BiFunction<List<Sha256Hash>, Integer, MerkleBranch> merkleBranchFactory = mock(BiFunction.class);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactoryMock, merkleBranchFactory);

        bridge.init(txMock, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);

        byte[] btcTxHash = Sha256Hash.of(Hex.decode("aabbcc")).getBytes();
        byte[] btcBlockHash = Sha256Hash.of(Hex.decode("ddeeff")).getBytes();
        byte[][] merkleBranchHashes = new byte[][]{
                Sha256Hash.of(Hex.decode("11")).getBytes(),
                Sha256Hash.of(Hex.decode("22")).getBytes(),
                Sha256Hash.of(Hex.decode("33")).getBytes(),
        };
        BigInteger merkleBranchBits = BigInteger.valueOf(123);

        MerkleBranch merkleBranch = mock(MerkleBranch.class);
        when(merkleBranchFactory.apply(any(), any())).then((Answer<MerkleBranch>) invocation -> {
            // Check constructor parameters are correct

            List<Sha256Hash> hashes = invocation.getArgument(0);
            Assertions.assertArrayEquals(merkleBranchHashes[0], hashes.get(0).getBytes());
            Assertions.assertArrayEquals(merkleBranchHashes[1], hashes.get(1).getBytes());
            Assertions.assertArrayEquals(merkleBranchHashes[2], hashes.get(2).getBytes());

            Integer bits = invocation.getArgument(1);
            Assertions.assertEquals(123, bits.intValue());

            return merkleBranch;
        });

        when(bridgeSupportMock.getBtcTransactionConfirmations(any(Sha256Hash.class), any(Sha256Hash.class), any(MerkleBranch.class))).then((Answer<Integer>) invocation -> {
            // Check parameters are correct
            Sha256Hash txHash = invocation.getArgument(0);
            Assertions.assertArrayEquals(btcTxHash, txHash.getBytes());

            Sha256Hash blockHash = invocation.getArgument(1);
            Assertions.assertArrayEquals(btcBlockHash, blockHash.getBytes());

            MerkleBranch merkleBranchArg = invocation.getArgument(2);
            Assertions.assertEquals(merkleBranch, merkleBranchArg);

            return 78;
        });

        Assertions.assertEquals(78, bridge.getBtcTransactionConfirmations(new Object[]{
                btcTxHash,
                btcBlockHash,
                merkleBranchBits,
                merkleBranchHashes
        }));
    }

    @Test
    public void getBtcTransactionConfirmationsAfterWasabi_errorInBridgeSupport() throws Exception {
        Transaction txMock = mock(Transaction.class);
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);

        BiFunction<List<Sha256Hash>, Integer, MerkleBranch> merkleBranchFactory = mock(BiFunction.class);
        Bridge bridge = spy(new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory, merkleBranchFactory));
        bridge.init(txMock, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);

        byte[] btcTxHash = Sha256Hash.of(Hex.decode("aabbcc")).getBytes();
        byte[] btcBlockHash = Sha256Hash.of(Hex.decode("ddeeff")).getBytes();
        byte[][] merkleBranchHashes = new byte[][]{
                Sha256Hash.of(Hex.decode("11")).getBytes(),
                Sha256Hash.of(Hex.decode("22")).getBytes(),
                Sha256Hash.of(Hex.decode("33")).getBytes(),
        };
        BigInteger merkleBranchBits = BigInteger.valueOf(123);

        MerkleBranch merkleBranch = mock(MerkleBranch.class);
        when(merkleBranchFactory.apply(any(), any())).then((Answer<MerkleBranch>) invocation -> {
            // Check constructor parameters are correct

            List<Sha256Hash> hashes = invocation.getArgument(0);
            Assertions.assertArrayEquals(merkleBranchHashes[0], hashes.get(0).getBytes());
            Assertions.assertArrayEquals(merkleBranchHashes[1], hashes.get(1).getBytes());
            Assertions.assertArrayEquals(merkleBranchHashes[2], hashes.get(2).getBytes());

            Integer bits = invocation.getArgument(1);
            Assertions.assertEquals(123, bits.intValue());

            return merkleBranch;
        });

        when(bridgeSupportMock.getBtcTransactionConfirmations(any(Sha256Hash.class), any(Sha256Hash.class), any(MerkleBranch.class))).then((Answer<Integer>) invocation -> {
            // Check parameters are correct
            Sha256Hash txHash = invocation.getArgument(0);
            Assertions.assertArrayEquals(btcTxHash, txHash.getBytes());

            Sha256Hash blockHash = invocation.getArgument(1);
            Assertions.assertArrayEquals(btcBlockHash, blockHash.getBytes());

            MerkleBranch merkleBranchArg = invocation.getArgument(2);
            Assertions.assertEquals(merkleBranch, merkleBranchArg);

            throw new VMException("bla bla bla");
        });

        try {
            bridge.getBtcTransactionConfirmations(new Object[]{
                    btcTxHash,
                    btcBlockHash,
                    merkleBranchBits,
                    merkleBranchHashes
            });
            Assertions.fail();
        } catch (VMException e) {
            Assertions.assertTrue(e.getMessage().contains("in getBtcTransactionConfirmations"));
            Assertions.assertEquals(VMException.class, e.getCause().getClass());
            Assertions.assertTrue(e.getCause().getMessage().contains("bla bla bla"));
        }
    }

    @Test
    public void getBtcTransactionConfirmationsAfterWasabi_merkleBranchConstructionError() throws Exception {
        Transaction txMock = mock(Transaction.class);
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);

        BiFunction<List<Sha256Hash>, Integer, MerkleBranch> merkleBranchFactory = mock(BiFunction.class);
        Bridge bridge = spy(new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory, merkleBranchFactory));
        bridge.init(txMock, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);

        byte[] btcTxHash = Sha256Hash.of(Hex.decode("aabbcc")).getBytes();
        byte[] btcBlockHash = Sha256Hash.of(Hex.decode("ddeeff")).getBytes();
        byte[][] merkleBranchHashes = new byte[][]{
                Sha256Hash.of(Hex.decode("11")).getBytes(),
                Sha256Hash.of(Hex.decode("22")).getBytes(),
                Sha256Hash.of(Hex.decode("33")).getBytes(),
        };
        BigInteger merkleBranchBits = BigInteger.valueOf(123);

        when(merkleBranchFactory.apply(any(), any())).then((Answer<MerkleBranch>) invocation -> {
            // Check constructor parameters are correct

            List<Sha256Hash> hashes = invocation.getArgument(0);
            Assertions.assertArrayEquals(merkleBranchHashes[0], hashes.get(0).getBytes());
            Assertions.assertArrayEquals(merkleBranchHashes[1], hashes.get(1).getBytes());
            Assertions.assertArrayEquals(merkleBranchHashes[2], hashes.get(2).getBytes());

            Integer bits = invocation.getArgument(1);
            Assertions.assertEquals(123, bits.intValue());

            throw new IllegalArgumentException("blabla");
        });

        try {
            bridge.getBtcTransactionConfirmations(new Object[]{
                    btcTxHash,
                    btcBlockHash,
                    merkleBranchBits,
                    merkleBranchHashes
            });
            Assertions.fail();
        } catch (VMException e) {
            Assertions.assertTrue(e.getMessage().contains("in getBtcTransactionConfirmations"));
            Assertions.assertEquals(IllegalArgumentException.class, e.getCause().getClass());
            verify(bridgeSupportMock, never()).getBtcTransactionConfirmations(any(), any(), any());
        }
    }

    @Test
    public void getBtcTransactionConfirmations_gasCost() {
        Transaction txMock = mock(Transaction.class);
        doReturn(true).when(activationConfig).isActive(eq(RSKIP122), anyLong());
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(constants.getBridgeConstants().getBtcParams()),
                constants.getBridgeConstants(),
                activationConfig);

        Bridge bridge = spy(new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory));
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        bridge.init(txMock, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);

        try (MockedStatic<BridgeUtils> bridgeUtilsMocked = mockStatic(BridgeUtils.class)) {
            bridgeUtilsMocked.when(() -> BridgeUtils.isContractTx(any(Transaction.class))).thenReturn(false);

            byte[] btcTxHash = Sha256Hash.of(Hex.decode("aabbcc")).getBytes();
            byte[] btcBlockHash = Sha256Hash.of(Hex.decode("ddeeff")).getBytes();
            byte[][] merkleBranchHashes = new byte[][]{
                    Sha256Hash.of(Hex.decode("11")).getBytes(),
                    Sha256Hash.of(Hex.decode("22")).getBytes(),
                    Sha256Hash.of(Hex.decode("33")).getBytes(),
            };
            BigInteger merkleBranchBits = BigInteger.valueOf(123);

            Object[] args = new Object[]{
                    btcTxHash,
                    btcBlockHash,
                    merkleBranchBits,
                    merkleBranchHashes
            };

            when(bridgeSupportMock.getBtcTransactionConfirmationsGetCost(eq(args))).thenReturn(1234L);
            CallTransaction.Function fn = BridgeMethods.GET_BTC_TRANSACTION_CONFIRMATIONS.getFunction();

            byte[] data = fn.encode(args);

            Assertions.assertEquals(2 * data.length + 1234L, bridge.getGasForData(data));
        }
    }

    @Test
    public void getBtcBlockchainBlockHashAtDepth() throws BlockStoreException, IOException, VMException {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(null, getGenesisBlock(), createRepository().startTracking(), null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        Sha256Hash mockedResult = Sha256Hash.of(Hex.decode("aabbcc"));
        when(bridgeSupportMock.getBtcBlockchainBlockHashAtDepth(555)).thenReturn(mockedResult);

        Assertions.assertEquals(mockedResult, Sha256Hash.wrap(bridge.getBtcBlockchainBlockHashAtDepth(new Object[]{BigInteger.valueOf(555)})));
    }

    @Test
    public void testCallFromContract_beforeOrchid() {
//        GenesisConfig mockedConfig = spy(new GenesisConfig());
//        when(mockedConfig.isRskip88()).thenReturn(false);
//        config.setBlockchainConfig(mockedConfig);
        blockFactory = new BlockFactory(config.getActivationConfig());

        PrecompiledContracts precompiledContracts = new PrecompiledContracts(config, null);
        EVMAssembler assembler = new EVMAssembler();
        ProgramInvoke invoke = new ProgramInvokeMockImpl();

        // Save code on the sender's address so that the bridge
        // thinks its being called by a contract
        byte[] callerCode = assembler.assemble("0xaabb 0xccdd 0xeeff");
        invoke.getRepository().saveCode(new RskAddress(invoke.getOwnerAddress().getLast20Bytes()), callerCode);

        VM vm = new VM(config.getVmConfig(), precompiledContracts);

        // Encode a call to the bridge's getMinimumLockTxValue function
        // That means first pushing the corresponding encoded ABI storage to memory (MSTORE)
        // and then doing a DELEGATECALL to the corresponding address with the correct parameters
        String bridgeFunctionHex = ByteUtil.toHexString(Bridge.GET_MINIMUM_LOCK_TX_VALUE.encode());
        bridgeFunctionHex = String.format("0x%s%s", bridgeFunctionHex, String.join("", Collections.nCopies(32 * 2 - bridgeFunctionHex.length(), "0")));
        String asm = String.format("%s 0x00 MSTORE 0x20 0x30 0x20 0x00 0x0000000000000000000000000000000001000006 0x6000 DELEGATECALL", bridgeFunctionHex);
        int numOps = asm.split(" ").length;
        byte[] code = assembler.assemble(asm);

        // Mock a transaction, all we really need is a hash
        Transaction tx = mock(Transaction.class);
        when(tx.getHash()).thenReturn(new Keccak256("001122334455667788990011223344556677889900112233445566778899aabb"));

        try {
            // Run the program on the VM
            Program program = new Program(config.getVmConfig(), precompiledContracts, blockFactory, mock(ActivationConfig.ForBlock.class), code, invoke, tx, new HashSet<>());
            for (int i = 0; i < numOps; i++) {
                vm.step(program);
            }
            Assertions.fail();
        } catch (NullPointerException e) {
            Assertions.assertNull(e.getMessage());
        }
    }

    @Test
    public void testCallFromContract_afterOrchid() {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP88), anyLong());
        blockFactory = new BlockFactory(activationConfig);

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(constants.getBridgeConstants().getBtcParams()),
                constants.getBridgeConstants(),
                activationConfig);

        PrecompiledContracts precompiledContracts = new PrecompiledContracts(config,
                bridgeSupportFactory);
        EVMAssembler assembler = new EVMAssembler();
        ProgramInvoke invoke = new ProgramInvokeMockImpl();

        // Save code on the sender's address so that the bridge
        // thinks its being called by a contract
        byte[] callerCode = assembler.assemble("0xaabb 0xccdd 0xeeff");
        invoke.getRepository().saveCode(new RskAddress(invoke.getOwnerAddress().getLast20Bytes()), callerCode);

        VM vm = new VM(config.getVmConfig(), precompiledContracts);

        // Encode a call to the bridge's getMinimumLockTxValue function
        // That means first pushing the corresponding encoded ABI storage to memory (MSTORE)
        // and then doing a DELEGATECALL to the corresponding address with the correct parameters
        String bridgeFunctionHex = ByteUtil.toHexString(Bridge.GET_MINIMUM_LOCK_TX_VALUE.encode());
        bridgeFunctionHex = String.format("0x%s%s", bridgeFunctionHex, String.join("", Collections.nCopies(32 * 2 - bridgeFunctionHex.length(), "0")));
        String asm = String.format("%s 0x00 MSTORE 0x20 0x30 0x20 0x00 0x0000000000000000000000000000000001000006 0x6000 DELEGATECALL", bridgeFunctionHex);
        int numOps = asm.split(" ").length;
        byte[] code = assembler.assemble(asm);

        // Mock a transaction, all we really need is a hash
        Transaction tx = mock(Transaction.class);
        when(tx.getHash()).thenReturn(new Keccak256("001122334455667788990011223344556677889900112233445566778899aabb"));

        // Run the program on the VM
        Program program = new Program(config.getVmConfig(), precompiledContracts, blockFactory, activationConfig.forBlock(0), code, invoke, tx, new HashSet<>());
        try {
            for (int i = 0; i < numOps; i++) {
                vm.step(program);
            }
            Assertions.fail();
        } catch (RuntimeException e) {
            Assertions.assertTrue(e.getMessage().contains("Non-local-call"));
        }
    }

    @Test
    public void localCallOnlyMethodsDefinition() {
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig, null);

        // To force initialization
        String foo = Bridge.UPDATE_COLLECTIONS.name;

        // Actual tests
        Arrays.asList(
                BridgeMethods.GET_BTC_BLOCKCHAIN_INITIAL_BLOCK_HEIGHT,
                BridgeMethods.GET_BTC_BLOCKCHAIN_BLOCK_LOCATOR,
                BridgeMethods.GET_BTC_BLOCKCHAIN_BLOCK_HASH_AT_DEPTH,
                BridgeMethods.GET_BTC_TX_HASH_PROCESSED_HEIGHT,
                BridgeMethods.GET_FEDERATION_ADDRESS,
                BridgeMethods.GET_FEDERATION_CREATION_BLOCK_NUMBER,
                BridgeMethods.GET_FEDERATION_CREATION_TIME,
                BridgeMethods.GET_FEDERATION_SIZE,
                BridgeMethods.GET_FEDERATION_THRESHOLD,
                BridgeMethods.GET_FEDERATOR_PUBLIC_KEY,
                BridgeMethods.GET_FEE_PER_KB,
                BridgeMethods.GET_LOCK_WHITELIST_ADDRESS,
                BridgeMethods.GET_LOCK_WHITELIST_ENTRY_BY_ADDRESS,
                BridgeMethods.GET_LOCK_WHITELIST_SIZE,
                BridgeMethods.GET_MINIMUM_LOCK_TX_VALUE,
                BridgeMethods.GET_PENDING_FEDERATION_HASH,
                BridgeMethods.GET_PENDING_FEDERATION_SIZE,
                BridgeMethods.GET_PENDING_FEDERATOR_PUBLIC_KEY,
                BridgeMethods.GET_RETIRING_FEDERATION_ADDRESS,
                BridgeMethods.GET_RETIRING_FEDERATION_CREATION_BLOCK_NUMBER,
                BridgeMethods.GET_RETIRING_FEDERATION_CREATION_TIME,
                BridgeMethods.GET_RETIRING_FEDERATION_SIZE,
                BridgeMethods.GET_RETIRING_FEDERATION_THRESHOLD,
                BridgeMethods.GET_RETIRING_FEDERATOR_PUBLIC_KEY,
                BridgeMethods.GET_STATE_FOR_BTC_RELEASE_CLIENT,
                BridgeMethods.GET_STATE_FOR_DEBUGGING,
                BridgeMethods.IS_BTC_TX_HASH_ALREADY_PROCESSED
        ).stream().forEach(m -> {
            Assertions.assertTrue(m.onlyAllowsLocalCalls(bridge, new Object[0]));
        });
    }

    @Test
    public void mineableMethodsDefinition() {
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig, null);

        // To force initialization
        String foo = Bridge.UPDATE_COLLECTIONS.name;

        // Actual tests
        Arrays.asList(
                BridgeMethods.ADD_FEDERATOR_PUBLIC_KEY,
                BridgeMethods.ADD_LOCK_WHITELIST_ADDRESS,
                BridgeMethods.ADD_ONE_OFF_LOCK_WHITELIST_ADDRESS,
                BridgeMethods.ADD_UNLIMITED_LOCK_WHITELIST_ADDRESS,
                BridgeMethods.ADD_SIGNATURE,
                BridgeMethods.COMMIT_FEDERATION,
                BridgeMethods.CREATE_FEDERATION,
                BridgeMethods.RECEIVE_HEADERS,
                BridgeMethods.REGISTER_BTC_TRANSACTION,
                BridgeMethods.RELEASE_BTC,
                BridgeMethods.REMOVE_LOCK_WHITELIST_ADDRESS,
                BridgeMethods.ROLLBACK_FEDERATION,
                BridgeMethods.SET_LOCK_WHITELIST_DISABLE_BLOCK_DELAY,
                BridgeMethods.UPDATE_COLLECTIONS,
                BridgeMethods.VOTE_FEE_PER_KB,
                BridgeMethods.GET_ACTIVE_FEDERATION_CREATION_BLOCK_HEIGHT
        ).stream().forEach(m -> {
            Assertions.assertFalse(m.onlyAllowsLocalCalls(bridge, new Object[0]));
        });
    }

    @Test
    public void getBtcBlockchainBestChainHeight_beforeRskip220_isMineable() {
        ActivationConfig.ForBlock activationsMock = mock(ActivationConfig.ForBlock.class);
        when(activationsMock.isActive(RSKIP220)).thenReturn(true);

        Bridge bridge = getBridgeInstance(activationsMock);

        Assertions.assertFalse(BridgeMethods.GET_BTC_BLOCKCHAIN_BEST_CHAIN_HEIGHT.onlyAllowsLocalCalls(bridge, new Object[0]));
    }

    @Test
    public void getBtcBlockchainBestChainHeight_afterRskip220_onlyAllowsLocalCalls() {
        ActivationConfig.ForBlock activationsMock = mock(ActivationConfig.ForBlock.class);
        when(activationsMock.isActive(RSKIP220)).thenReturn(false);

        Bridge bridge = getBridgeInstance(activationsMock);

        Assertions.assertTrue(BridgeMethods.GET_BTC_BLOCKCHAIN_BEST_CHAIN_HEIGHT.onlyAllowsLocalCalls(bridge, new Object[0]));
    }

    @Test
    public void receiveHeadersGasCost_beforeDynamicCost() {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP124), anyLong());

        Transaction txMock = mock(Transaction.class);
        when(txMock.getReceiveAddress()).thenReturn(RskAddress.nullAddress());
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                mock(BridgeSupportFactory.class));
        bridge.init(txMock, getGenesisBlock(), null, null, null, null);

        for (int numberOfHeaders = 0; numberOfHeaders < 10; numberOfHeaders++) {
            byte[][] headers = new byte[numberOfHeaders][];
            for (int i = 0; i < numberOfHeaders; i++) headers[i] = Hex.decode("00112233445566778899");

            byte[] data = BridgeMethods.RECEIVE_HEADERS.getFunction().encode(new Object[]{headers});

            Assertions.assertEquals(22000L + 2 * data.length, bridge.getGasForData(data));
        }
    }

    @Test
    public void receiveHeadersGasCost_afterDynamicCost() {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP124), anyLong());

        Transaction txMock = mock(Transaction.class);
        when(txMock.getReceiveAddress()).thenReturn(RskAddress.nullAddress());
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                mock(BridgeSupportFactory.class));
        bridge.init(txMock, getGenesisBlock(), null, null, null, null);

        final long BASE_COST = 66_000L;
        for (int numberOfHeaders = 0; numberOfHeaders < 10; numberOfHeaders++) {
            byte[][] headers = new byte[numberOfHeaders][];
            for (int i = 0; i < numberOfHeaders; i++) headers[i] = Hex.decode("00112233445566778899");

            byte[] data = BridgeMethods.RECEIVE_HEADERS.getFunction().encode(new Object[]{headers});

            long cost = BASE_COST + 2 * data.length;
            if (numberOfHeaders > 1) {
                cost += 1650L * (numberOfHeaders - 1);
            }
            Assertions.assertEquals(cost, bridge.getGasForData(data));
        }
    }

    @Test
    public void receiveHeadersAccess_beforePublic_noAccessIfNotFromFederationMember() throws Exception {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP124), anyLong());

        RskAddress sender = new RskAddress("2acc95758f8b5f583470ba265eb685a8f45fc9d5");
        Transaction txMock = mock(Transaction.class);
        when(txMock.getSender()).thenReturn(sender);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportMock.getRetiringFederation()).thenReturn(null);
        when(bridgeSupportMock.getActiveFederation()).thenReturn(BridgeRegTestConstants.getInstance().getGenesisFederation());

        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactoryMock);

        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);
        bridge.init(txMock, getGenesisBlock(), null, null, null, null);

        byte[][] headers = new byte[][]{Hex.decode(
                "0000002006226e46111a0b59caaf126043eb5bbf28c34f3a5e332a1fc7b2b73cf188910ff698ee112158f5573a90b7403cfba074addd61c547b3639c6afdcf52588eb8e2a1ef825cffff7f2000000000"
        )};

        byte[] data = BridgeMethods.RECEIVE_HEADERS.getFunction().encode(new Object[]{headers});

        try {
            bridge.execute(data);
            Assertions.fail();
        } catch (VMException e) {
            Assertions.assertTrue(e.getMessage().contains("Sender is not part of the active"));
        }
        verify(bridgeSupportMock, never()).receiveHeaders(any(BtcBlock[].class));
    }

    @Test
    public void receiveHeadersAccess_beforePublic_accessIfFromFederationMember() throws Exception {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP124), anyLong());

        RskAddress sender = new RskAddress(ECKey.fromPrivate(HashUtil.keccak256("federator1".getBytes(StandardCharsets.UTF_8))).getAddress());
        Transaction txMock = mock(Transaction.class);
        when(txMock.getSender()).thenReturn(sender);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportMock.getRetiringFederation()).thenReturn(null);
        when(bridgeSupportMock.getActiveFederation()).thenReturn(BridgeRegTestConstants.getInstance().getGenesisFederation());

        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactoryMock);

        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);

        bridge.init(txMock, getGenesisBlock(), null, null, null, null);

        byte[][] headers = new byte[][]{Hex.decode(
                "0000002006226e46111a0b59caaf126043eb5bbf28c34f3a5e332a1fc7b2b73cf188910ff698ee112158f5573a90b7403cfba074addd61c547b3639c6afdcf52588eb8e2a1ef825cffff7f2000000000"
        )};

        byte[] data = BridgeMethods.RECEIVE_HEADERS.getFunction().encode(new Object[]{headers});

        Assertions.assertNull(bridge.execute(data));
        verify(bridgeSupportMock, times(1)).receiveHeaders(any(BtcBlock[].class));
    }

    @Test
    public void receiveHeadersAccess_afterPublic_accessFromAnyone() throws Exception {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP124), anyLong());

        Transaction txMock = mock(Transaction.class);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);

        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactoryMock);

        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);

        bridge.init(txMock, getGenesisBlock(), null, null, null, null);

        byte[][] headers = new byte[][]{Hex.decode(
                "0000002006226e46111a0b59caaf126043eb5bbf28c34f3a5e332a1fc7b2b73cf188910ff698ee112158f5573a90b7403cfba074addd61c547b3639c6afdcf52588eb8e2a1ef825cffff7f2000000000"
        )};

        byte[] data = BridgeMethods.RECEIVE_HEADERS.getFunction().encode(new Object[]{headers});

        Assertions.assertNull(bridge.execute(data));
        verify(bridgeSupportMock, times(1)).receiveHeaders(any(BtcBlock[].class));
    }

    @Test
    public void bridgeSupportIsCreatedOnInit() {
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactoryMock);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);

        bridge.init(mock(Transaction.class), getGenesisBlock(), null, null, null, null);

        Assertions.assertNotNull(TestUtils.getInternalState(bridge, "bridgeSupport"));
    }

    private static Repository createRepository() {
        return new MutableRepository(new MutableTrieCache(new MutableTrieImpl(null, new Trie())));
    }

    public static Genesis getGenesisInstance(RskSystemProperties config) {
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        return new TestGenesisLoader(trieStore, config.genesisInfo(), config.getNetworkConstants().getInitialNonce(), false, false, false).load();
    }

    private void executeAndCheckMethodWithAnyCallsAllowed(boolean localCall) throws Exception {
        Transaction tx = mock(Transaction.class);

        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactoryMock);


        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupportMock);
        when(bridgeSupportMock.voteFeePerKbChange(tx, Coin.CENT)).thenReturn(1);

        bridge.init(tx, getGenesisBlock(), null, null, null, null);

        byte[] data = BridgeMethods.VOTE_FEE_PER_KB.getFunction().encode(new Object[]{Coin.CENT.longValue()});
        bridge.execute(data);

        verify(bridgeSupportMock, times(1)).voteFeePerKbChange(tx, Coin.CENT);
    }

    @Test
    public void getBtcBlockchainInitialBlockHeight() throws IOException, VMException {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(null, getGenesisBlock(), null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        TestUtils.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getBtcBlockchainInitialBlockHeight()).thenReturn(1234);

        Assertions.assertEquals(1234, bridge.getBtcBlockchainInitialBlockHeight(new Object[]{}).intValue());
    }

    private BtcTransaction createTransaction() {
        return new SimpleBtcTransaction(networkParameters, PegTestUtils.createHash());
    }

    private Block getGenesisBlock() {
        return new BlockGenerator().getGenesisBlock();
    }

    private void getGasForDataPaidTx(int expected, CallTransaction.Function function, Object... funcArgs) {
        activationConfig = ActivationConfigsForTest.bridgeUnitTest();

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        org.ethereum.core.Transaction rskTx;
        if (function == null) {
            rskTx = CallTransaction.createRawTransaction(
                    0,
                    1,
                    1,
                    PrecompiledContracts.BRIDGE_ADDR,
                    0,
                    new byte[]{1, 2, 3},
                    Constants.REGTEST_CHAIN_ID
            );
        } else {
            rskTx = CallTransaction.createCallTransaction(
                    0,
                    1,
                    1,
                    PrecompiledContracts.BRIDGE_ADDR,
                    0,
                    function,
                    Constants.REGTEST_CHAIN_ID,
                    funcArgs
            );
        }

        rskTx.sign(BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS.get(0).getPrivKeyBytes());

        BlockGenerator blockGenerator = new BlockGenerator();
        Block rskExecutionBlock = blockGenerator.createChildBlock(getGenesisInstance(config));
        for (int i = 0; i < 20; i++) {
            rskExecutionBlock = blockGenerator.createChildBlock(rskExecutionBlock);
        }

        Repository mockRepository = mock(Repository.class);

        bridge.init(rskTx, rskExecutionBlock, mockRepository, null, null, null);
        Assertions.assertEquals(expected, bridge.getGasForData(rskTx.getData()));
    }

    private void registerBtcTransactionWithHugeDeclaredSize(BtcTransaction tx) throws VMException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(PrecompiledContracts.BRIDGE_ADDR_STR))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(AMOUNT)
                .build();
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig,
                bridgeSupportFactory);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);

        byte[] serializedTx = tx.bitcoinSerialize();

        byte[] data = Bridge.REGISTER_BTC_TRANSACTION.encode(serializedTx, 1, new byte[30]);

        Assertions.assertNull(bridge.execute(data));
    }

    private Bridge getBridgeInstance(ActivationConfig.ForBlock activations) {
        BridgeSupportFactory bridgeSupportFactoryMock = mock(BridgeSupportFactory.class);
        when(bridgeSupportFactoryMock.newInstance(any(), any(), any(), any())).thenReturn(mock(BridgeSupport.class));

        when(activationConfig.forBlock(anyLong())).thenReturn(activations);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig, bridgeSupportFactoryMock);
        bridge.init(mock(Transaction.class), getGenesisBlock(), null, null, null, null);

        return bridge;
    }

    private static class HugeDeclaredSizeBtcTransaction extends BtcTransaction {

        private boolean hackInputsSize;
        private boolean hackOutputsSize;
        private boolean hackWitnessPushCountSize;
        private boolean hackWitnessPushSize;

        public HugeDeclaredSizeBtcTransaction(NetworkParameters params, boolean hackInputsSize, boolean hackOutputsSize, boolean hackWitnessPushCountSize, boolean hackWitnessPushSize) {
            super(params);
            BtcTransaction inputTx = new BtcTransaction(params);
            inputTx.addOutput(Coin.FIFTY_COINS, BtcECKey.fromPrivate(BigInteger.valueOf(123456)).toAddress(params));
            Address to = BtcECKey.fromPrivate(BigInteger.valueOf(1000)).toAddress(params);
            this.addInput(inputTx.getOutput(0));
            this.getInput(0).disconnect();
            TransactionWitness witness = new TransactionWitness(1);
            witness.setPush(0, new byte[]{0});
            this.setWitness(0, witness);
            this.addOutput(Coin.COIN, to);

            this.hackInputsSize = hackInputsSize;
            this.hackOutputsSize = hackOutputsSize;
            this.hackWitnessPushCountSize = hackWitnessPushCountSize;
            this.hackWitnessPushSize = hackWitnessPushSize;
        }

        protected void bitcoinSerializeToStream(OutputStream stream, boolean serializeWitRequested) throws IOException {
            boolean serializeWit = serializeWitRequested && hasWitness();
            uint32ToByteStreamLE(getVersion(), stream);
            if (serializeWit) {
                stream.write(new byte[]{0, 1});
            }

            long inputsSize = hackInputsSize ? Integer.MAX_VALUE : getInputs().size();
            stream.write(new VarInt(inputsSize).encode());
            for (TransactionInput in : getInputs())
                in.bitcoinSerialize(stream);
            long outputsSize = hackOutputsSize ? Integer.MAX_VALUE : getOutputs().size();
            stream.write(new VarInt(outputsSize).encode());
            for (TransactionOutput out : getOutputs())
                out.bitcoinSerialize(stream);
            if (serializeWit) {
                for (int i = 0; i < getInputs().size(); i++) {
                    TransactionWitness witness = getWitness(i);
                    long pushCount = hackWitnessPushCountSize ? Integer.MAX_VALUE : witness.getPushCount();
                    stream.write(new VarInt(pushCount).encode());
                    for (int y = 0; y < witness.getPushCount(); y++) {
                        byte[] push = witness.getPush(y);
                        long pushLength = hackWitnessPushSize ? Integer.MAX_VALUE : push.length;
                        stream.write(new VarInt(pushLength).encode());
                        stream.write(push);
                    }
                }
            }
            uint32ToByteStreamLE(getLockTime(), stream);
        }
    }
}
