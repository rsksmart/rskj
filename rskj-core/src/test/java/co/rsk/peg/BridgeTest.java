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

import co.rsk.asm.EVMAssembler;
import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.BlockDifficulty;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.peg.bitcoin.SimpleBtcTransaction;
import co.rsk.peg.whitelist.OneOffWhiteListEntry;
import co.rsk.peg.whitelist.UnlimitedWhiteListEntry;
import co.rsk.test.World;
import co.rsk.trie.TrieImpl;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.BlockchainConfig;
import org.ethereum.config.BlockchainNetConfig;
import org.ethereum.config.blockchain.GenesisConfig;
import org.ethereum.config.blockchain.regtest.RegTestGenesisConfig;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.db.MutableRepository;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.util.RskTestFactory;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.VM;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.invoke.ProgramInvoke;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.internal.util.reflection.Whitebox;
import org.mockito.invocation.InvocationOnMock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

import static co.rsk.bitcoinj.core.Utils.uint32ToByteStreamLE;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * Created by ajlopez on 6/8/2016.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({Bridge.class, BridgeUtils.class})
public class BridgeTest {
    private static NetworkParameters networkParameters;
    private static BridgeConstants bridgeConstants;

    private static final BigInteger AMOUNT = new BigInteger("1000000000000000000");
    private static final BigInteger NONCE = new BigInteger("0");
    private static final BigInteger GAS_PRICE = new BigInteger("100");
    private static final BigInteger GAS_LIMIT = new BigInteger("1000");
    private static final String DATA = "80af2871";
    private static final String ERR_NOT_FROM_ACTIVE_OR_RETIRING_FED = "Sender is not part of the active or retiring federation";
    private static ECKey fedECPrivateKey;
    private static TestSystemProperties config = new TestSystemProperties();

    @BeforeClass
    public static void setUpBeforeClass() {
        config.setBlockchainConfig(new RegTestGenesisConfig());
        bridgeConstants = config.getBlockchainConfig().getCommonConstants().getBridgeConstants();
        networkParameters = bridgeConstants.getBtcParams();
        BtcECKey fedBTCPrivateKey = ((BridgeRegTestConstants)bridgeConstants).getFederatorPrivateKeys().get(0);
        fedECPrivateKey = ECKey.fromPrivate(fedBTCPrivateKey.getPrivKey());
    }

    @Before
    public void resetConfigToRegTest() {
        config.setBlockchainConfig(new RegTestGenesisConfig());
    }

    @Test
    public void callUpdateCollectionsWithSignatureNotFromFederation() throws IOException {
        BtcTransaction tx1 = createTransaction();

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageConfiguration bridgeStorageConfigurationAtThisHeight = BridgeStorageConfiguration.fromBlockchainConfig(config.getBlockchainConfig().getConfigForBlock(0));
        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants(), bridgeStorageConfigurationAtThisHeight);

        provider0.getReleaseTransactionSet().add(tx1, 1L);
        provider0.save();

        track.commit();

        track = repository.startTracking();

        Transaction rskTx = new Transaction(config, PrecompiledContracts.BRIDGE_ADDR_STR, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        rskTx.sign(new ECKey().getPrivKeyBytes());
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        World world = new World();
        bridge.init(rskTx, world.getBlockChain().getBestBlock(), track, world.getBlockChain().getBlockStore(), null, new LinkedList<>());
        try {
            bridge.execute(Bridge.UPDATE_COLLECTIONS.encode());
            Assert.fail();
        } catch (Exception ex) {
            Assert.assertTrue(ex.getMessage().contains(ERR_NOT_FROM_ACTIVE_OR_RETIRING_FED));
        }
    }

    @Test
    public void callUpdateCollectionsWithTransactionsWaitingForConfirmation() throws IOException {
        BtcTransaction tx1 = createTransaction();
        BtcTransaction tx2 = createTransaction();
        BtcTransaction tx3 = createTransaction();

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageConfiguration bridgeStorageConfigurationAtThisHeight = BridgeStorageConfiguration.fromBlockchainConfig(config.getBlockchainConfig().getConfigForBlock(0));
        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants(), bridgeStorageConfigurationAtThisHeight);

        provider0.getReleaseTransactionSet().add(tx1, 1L);
        provider0.getReleaseTransactionSet().add(tx2, 2L);
        provider0.getReleaseTransactionSet().add(tx3, 3L);

        provider0.save();

        track.commit();

        track = repository.startTracking();
        Transaction rskTx = new Transaction(config, PrecompiledContracts.BRIDGE_ADDR_STR, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        World world = new World();
        bridge.init(rskTx, world.getBlockChain().getBestBlock(), track, world.getBlockChain().getBlockStore(), null, new LinkedList<>());

        bridge.execute(Bridge.UPDATE_COLLECTIONS.encode());

        track.commit();

        // reusing same storage configuration as the height doesn't affect storage configurations for releases.
        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants(), bridgeStorageConfigurationAtThisHeight);

        Assert.assertEquals(3, provider.getReleaseTransactionSet().getEntries().size());
        Assert.assertEquals(0, provider.getRskTxsWaitingForSignatures().size());
    }

    @Test
    public void callUpdateCollectionsWithTransactionsWaitingForConfirmationWithEnoughConfirmations() throws IOException {
        BtcTransaction tx1 = createTransaction();
        BtcTransaction tx2 = createTransaction();
        BtcTransaction tx3 = createTransaction();

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        BridgeStorageConfiguration bridgeStorageConfigurationAtThisHeight = BridgeStorageConfiguration.fromBlockchainConfig(config.getBlockchainConfig().getConfigForBlock(0));
        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants(), bridgeStorageConfigurationAtThisHeight);

        provider0.getReleaseTransactionSet().add(tx1, 1L);
        provider0.getReleaseTransactionSet().add(tx2, 2L);
        provider0.getReleaseTransactionSet().add(tx3, 3L);

        provider0.save();

        track.commit();

        track = repository.startTracking();

        World world = new World();
        List<Block> blocks = new BlockGenerator().getSimpleBlockChain(world.getBlockChain().getBestBlock(), 10);


        Transaction rskTx = new Transaction(config, PrecompiledContracts.BRIDGE_ADDR_STR, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());

        world.getBlockChain().getBlockStore().saveBlock(blocks.get(1), new BlockDifficulty(BigInteger.ONE), true);

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(rskTx, blocks.get(9), track, world.getBlockChain().getBlockStore(), null, new LinkedList<>());

        bridge.execute(Bridge.UPDATE_COLLECTIONS.encode());

        track.commit();

        // reusing same storage configuration as the height doesn't affect storage configurations for releases.
        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, config.getBlockchainConfig().getCommonConstants().getBridgeConstants(), bridgeStorageConfigurationAtThisHeight);

        Assert.assertEquals(2, provider.getReleaseTransactionSet().getEntries().size());
        Assert.assertEquals(1, provider.getRskTxsWaitingForSignatures().size());
    }

    @Test
    public void sendNoRskTx() throws IOException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, getGenesisBlock(), track, null, null, null);
        try {
            bridge.execute(Bridge.RECEIVE_HEADERS.encode());
            Assert.fail();
        } catch (Exception ex) {
            Assert.assertTrue(ex.getMessage().contains("Rsk Transaction is null"));
        }

        track.commit();
    }

    @Test
    public void sendNoBlockHeader() throws IOException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = new Transaction(config, PrecompiledContracts.BRIDGE_ADDR_STR, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);

        bridge.execute(Bridge.RECEIVE_HEADERS.encode());

        track.commit();
    }

    @Test
    public void sendOrphanBlockHeader() {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = new Transaction(config, PrecompiledContracts.BRIDGE_ADDR_STR, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);

        co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(networkParameters, 1, PegTestUtils.createHash(), PegTestUtils.createHash(), 1, Utils.encodeCompactBits(networkParameters.getMaxTarget()), 1, new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock[] headers = new co.rsk.bitcoinj.core.BtcBlock[1];
        headers[0] = block;

        Object[] objectArray = new Object[headers.length];

        for (int i = 0; i < headers.length; i++)
            objectArray[i] = headers[i].bitcoinSerialize();

        bridge.execute(Bridge.RECEIVE_HEADERS.encode(new Object[]{objectArray}));

        track.commit();
    }

    @Test
    public void executeWithFunctionSignatureLengthTooShortBeforeRskip88() {

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        Transaction mockedTx = mock(Transaction.class);
        bridge.init(mockedTx, getGenesisBlock(), null, null, null, null);
        Assert.assertNull(bridge.execute(new byte[3]));
    }

    @Test
    public void executeWithFunctionSignatureLengthTooShortAfterRskip88() {

        GenesisConfig mockedConfig = spy(new GenesisConfig());
        when(mockedConfig.isRskip88()).thenReturn(true);
        config.setBlockchainConfig(mockedConfig);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        Transaction mockedTx = mock(Transaction.class);

        try {
            bridge.init(mockedTx, getGenesisBlock(), null, null, null, null);
            bridge.execute(new byte[3]);
            Assert.fail();
        } catch (RuntimeException e) {
            Assert.assertTrue(e.getMessage().contains("Invalid data given"));
        }
    }


    @Test
    public void executeWithInexistentFunctionBeforeRskip88() {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        Transaction mockedTx = mock(Transaction.class);
        bridge.init(mockedTx, getGenesisBlock(), null, null, null, null);
        Assert.assertNull(bridge.execute(new byte[4]));
    }

    @Test
    public void executeWithInexistentFunctionAfterRskip88() {
        GenesisConfig mockedConfig = spy(new GenesisConfig());
        when(mockedConfig.isRskip88()).thenReturn(true);
        config.setBlockchainConfig(mockedConfig);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        Transaction mockedTx = mock(Transaction.class);

        try {
            bridge.init(mockedTx, getGenesisBlock(), null, null, null, null);
            bridge.execute(new byte[4]);
            Assert.fail();
        } catch (RuntimeException e) {
            Assert.assertTrue(e.getMessage().contains("Invalid data given"));
        }
    }

    @Test
    public void receiveHeadersNotFromTheFederation() throws IOException {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = new Transaction(config, PrecompiledContracts.BRIDGE_ADDR_STR, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        rskTx.sign(new ECKey().getPrivKeyBytes());

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);
        try {
            bridge.execute(Bridge.RECEIVE_HEADERS.encode());
            Assert.fail();
        } catch (Exception ex) {
            Assert.assertTrue(ex.getMessage().contains(ERR_NOT_FROM_ACTIVE_OR_RETIRING_FED));
        }

        track.commit();
    }

    @Test
    public void receiveHeadersWithNonParseableHeader() {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = new Transaction(config, PrecompiledContracts.BRIDGE_ADDR_STR, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);

        Object[] objectArray = new Object[1];
        objectArray[0] = new byte[60];

        byte[] data = Bridge.RECEIVE_HEADERS.encode(new Object[]{objectArray});

        Assert.assertNull(bridge.execute(data));

    }

    @Test
    public void receiveHeadersWithCorrectSizeHeaders() throws Exception {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = new Transaction(config, PrecompiledContracts.BRIDGE_ADDR_STR, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);

        final int numBlocks = 10;
        co.rsk.bitcoinj.core.BtcBlock[] headers = new co.rsk.bitcoinj.core.BtcBlock[numBlocks];

        for (int i = 0; i < numBlocks; i++) {
            co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(networkParameters, 1, PegTestUtils.createHash(), PegTestUtils.createHash(), 1, Utils.encodeCompactBits(networkParameters.getMaxTarget()), 1, new ArrayList<>());
            headers[i] = block;
        }

        byte[][] headersSerialized = new byte[headers.length][];

        for (int i = 0; i < headers.length; i++) {
            headersSerialized[i] = headers[i].bitcoinSerialize();
        }

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        PowerMockito.whenNew(BridgeSupport.class).withAnyArguments().thenReturn(bridgeSupportMock);

        PowerMockito.mockStatic(BridgeUtils.class);
        when(BridgeUtils.isFromFederateMember(any(), any())).thenReturn(true);

        MessageSerializer serializer = bridgeConstants.getBtcParams().getDefaultSerializer();
        MessageSerializer spySerializer = Mockito.spy(serializer);

        NetworkParameters btcParamsMock = mock(NetworkParameters.class);
        BridgeConstants bridgeConstantsMock = mock(BridgeConstants.class);

        when(bridgeConstantsMock.getBtcParams()).thenReturn(btcParamsMock);
        when(btcParamsMock.getDefaultSerializer()).thenReturn(spySerializer);

        Whitebox.setInternalState(bridge, "bridgeConstants", bridgeConstantsMock);

        bridge.execute(Bridge.RECEIVE_HEADERS.encode(new Object[]{headersSerialized}));

        track.commit();

        verify(bridgeSupportMock, times(1)).receiveHeaders(headers);
        for (int i = 0; i < headers.length; i++) {
            verify(spySerializer, times(1)).makeBlock(headersSerialized[i]);
        }
    }

    @Test
    public void receiveHeadersWithIncorrectSizeHeaders() throws Exception {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = new Transaction(config, PrecompiledContracts.BRIDGE_ADDR_STR, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        rskTx.sign(new ECKey().getPrivKeyBytes());

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);

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
            Assert.assertEquals(block, networkParameters.getDefaultSerializer().makeBlock(headersSerialized[i]));
        }

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        PowerMockito.whenNew(BridgeSupport.class).withAnyArguments().thenReturn(bridgeSupportMock);

        PowerMockito.mockStatic(BridgeUtils.class);
        when(BridgeUtils.isFromFederateMember(any(), any())).thenReturn(true);

        NetworkParameters btcParamsMock = mock(NetworkParameters.class);
        BridgeConstants bridgeConstantsMock = mock(BridgeConstants.class);

        when(bridgeConstantsMock.getBtcParams()).thenReturn(btcParamsMock);

        Whitebox.setInternalState(bridge, "bridgeConstants", bridgeConstantsMock);

        bridge.execute(Bridge.RECEIVE_HEADERS.encode(new Object[]{headersSerialized}));

        track.commit();

        verify(bridgeSupportMock, never()).receiveHeaders(headers);
        verify(btcParamsMock, never()).getDefaultSerializer();
    }

    public void registerBtcTransactionNotFromFederation() throws Exception{
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = new Transaction(config, PrecompiledContracts.BRIDGE_ADDR_STR, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        rskTx.sign(new ECKey().getPrivKeyBytes());

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);


        byte[] data = Bridge.REGISTER_BTC_TRANSACTION.encode(new byte[3], 1, new byte[30]);

        try {
            bridge.execute(data);
            Assert.fail();
        } catch (Exception ex) {
            Assert.assertTrue(ex.getMessage().contains(ERR_NOT_FROM_ACTIVE_OR_RETIRING_FED));
        }
    }

    @Test
    public void receiveHeadersWithHugeDeclaredTransactionsSize() {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = new Transaction(config, PrecompiledContracts.BRIDGE_ADDR_STR, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
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

        Assert.assertNull(bridge.execute(data));

    }


    @Test
    public void registerBtcTransactionWithNonParseableTx() {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = new Transaction(config, PrecompiledContracts.BRIDGE_ADDR_STR, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);


        byte[] data = Bridge.REGISTER_BTC_TRANSACTION.encode(new byte[3], 1, new byte[30]);

        Assert.assertNull(bridge.execute(data));
    }

    @Test
    public void registerBtcTransactionWithHugeDeclaredInputsSize() {
        NetworkParameters btcParams = RegTestParams.get();
        BtcTransaction tx = new HugeDeclaredSizeBtcTransaction(btcParams, true, false, false, false);
        registerBtcTransactionWithHugeDeclaredSize(tx);
    }

    @Test
    public void registerBtcTransactionWithHugeDeclaredOutputsSize() {
        NetworkParameters btcParams = RegTestParams.get();
        BtcTransaction tx = new HugeDeclaredSizeBtcTransaction(btcParams, false, true, false, false);
        registerBtcTransactionWithHugeDeclaredSize(tx);
    }

    @Test
    public void registerBtcTransactionWithHugeDeclaredWitnessPushCountSize() {
        NetworkParameters btcParams = RegTestParams.get();
        BtcTransaction tx = new HugeDeclaredSizeBtcTransaction(btcParams, false, false, true, false);
        registerBtcTransactionWithHugeDeclaredSize(tx);
    }

    @Test
    public void registerBtcTransactionWithHugeDeclaredWitnessPushSize() {
        NetworkParameters btcParams = RegTestParams.get();
        BtcTransaction tx = new HugeDeclaredSizeBtcTransaction(btcParams, false, false, false, true);
        registerBtcTransactionWithHugeDeclaredSize(tx);
    }

    private void registerBtcTransactionWithHugeDeclaredSize(BtcTransaction tx) {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = new Transaction(config, PrecompiledContracts.BRIDGE_ADDR_STR, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);

        byte[] serializedTx = tx.bitcoinSerialize();

        byte[] data = Bridge.REGISTER_BTC_TRANSACTION.encode(serializedTx, 1, new byte[30]);

        Assert.assertNull(bridge.execute(data));
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
            witness.setPush(0, new byte[] {0});
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
    };

    @Test
    public void registerBtcTransactionWithNonParseableMerkleeProof1() throws Exception{
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = new Transaction(config, PrecompiledContracts.BRIDGE_ADDR_STR, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);

        NetworkParameters btcParams = RegTestParams.get();
        BtcTransaction tx = new BtcTransaction(btcParams);
        tx.addOutput(Coin.COIN, new BtcECKey().toAddress(btcParams));
        tx.addInput(PegTestUtils.createHash(), 0, ScriptBuilder.createInputScript(null, new BtcECKey()));

        byte[] data = Bridge.REGISTER_BTC_TRANSACTION.encode(tx.bitcoinSerialize(), 1, new byte[3]);

        Assert.assertNull(bridge.execute(data));
    }

    @Test
    public void registerBtcTransactionWithNonParseableMerkleeProof2() throws Exception{
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = new Transaction(config, PrecompiledContracts.BRIDGE_ADDR_STR, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);

        NetworkParameters btcParams = RegTestParams.get();
        BtcTransaction tx = new BtcTransaction(btcParams);
        tx.addOutput(Coin.COIN, new BtcECKey().toAddress(btcParams));
        tx.addInput(PegTestUtils.createHash(), 0, ScriptBuilder.createInputScript(null, new BtcECKey()));

        byte[] data = Bridge.REGISTER_BTC_TRANSACTION.encode(tx.bitcoinSerialize(), 1, new byte[30]);

        Assert.assertNull(bridge.execute(data));
    }

    @Test
    public void registerBtcTransactionWithHugeDeclaredSizeMerkleeProof() throws Exception{
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = new Transaction(config, PrecompiledContracts.BRIDGE_ADDR_STR, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
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

        Assert.assertNull(bridge.execute(data));
    }


    @Test
    public void getFederationAddress() throws Exception {
        // Case with genesis federation
        Federation federation = bridgeConstants.getGenesisFederation();
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        Transaction mockedTx = mock(Transaction.class);
        when(mockedTx.isLocalCallTransaction()).thenReturn(true);
        bridge.init(mockedTx, getGenesisBlock(), track, null, null, null);

        byte[] data = Bridge.GET_FEDERATION_ADDRESS.encode();

        Assert.assertArrayEquals(Bridge.GET_FEDERATION_ADDRESS.encodeOutputs(federation.getAddress().toString()), bridge.execute(data));
    }

    @Test
    public void getMinimumLockTxValue() throws Exception{
        Repository repository = createRepository();
        Repository track = repository.startTracking();


        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        Transaction mockedTx = mock(Transaction.class);
        when(mockedTx.isLocalCallTransaction()).thenReturn(true);
        bridge.init(mockedTx, getGenesisBlock(), track, null, null, null);

        byte[] data = Bridge.GET_MINIMUM_LOCK_TX_VALUE.encode();

        Assert.assertArrayEquals(Bridge.GET_MINIMUM_LOCK_TX_VALUE.encodeOutputs(bridgeConstants.getMinimumLockTxValue().value), bridge.execute(data));
    }

    @Test
    public void addSignatureNotFromFederation() throws Exception{
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = new Transaction(config, PrecompiledContracts.BRIDGE_ADDR_STR, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        rskTx.sign(new ECKey().getPrivKeyBytes());

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);

        byte[] federatorPublicKeySerialized = new byte[3];
        Object[] signaturesObjectArray = new Object[0];
        byte[] rskTxHash = new byte[32];
        byte[] data = Bridge.ADD_SIGNATURE.encode(federatorPublicKeySerialized, signaturesObjectArray, rskTxHash);

        try {
            bridge.execute(data);
            Assert.fail();
        } catch (Exception ex) {
            Assert.assertTrue(ex.getMessage().contains(ERR_NOT_FROM_ACTIVE_OR_RETIRING_FED));
        }
    }

    @Test
    public void addSignatureWithNonParseablePublicKey() throws Exception{
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = new Transaction(config, PrecompiledContracts.BRIDGE_ADDR_STR, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);

        byte[] federatorPublicKeySerialized = new byte[3];
        Object[] signaturesObjectArray = new Object[0];
        byte[] rskTxHash = new byte[32];
        byte[] data = Bridge.ADD_SIGNATURE.encode(federatorPublicKeySerialized, signaturesObjectArray, rskTxHash);

        Assert.assertNull(bridge.execute(data));
    }

    @Test
    public void addSignatureWithEmptySignatureArray() throws Exception{
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = new Transaction(config, PrecompiledContracts.BRIDGE_ADDR_STR, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(rskTx, getGenesisBlock(), track, null, null, null);

        byte[] federatorPublicKeySerialized = new BtcECKey().getPubKey();
        Object[] signaturesObjectArray = new Object[0];
        byte[] rskTxHash = new byte[32];
        byte[] data = Bridge.ADD_SIGNATURE.encode(federatorPublicKeySerialized, signaturesObjectArray, rskTxHash);

        Assert.assertNull(bridge.execute(data));
    }

    @Test
    public void addSignatureWithNonParseableSignature() throws Exception{
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = new Transaction(config, PrecompiledContracts.BRIDGE_ADDR_STR, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(rskTx, new BlockGenerator().getGenesisBlock(), track, null, null, null);

        byte[] federatorPublicKeySerialized = new BtcECKey().getPubKey();
        Object[] signaturesObjectArray = new Object[]{new byte[3]};
        byte[] rskTxHash = new byte[32];
        byte[] data = Bridge.ADD_SIGNATURE.encode(federatorPublicKeySerialized, signaturesObjectArray, rskTxHash);

        Assert.assertNull(bridge.execute(data));
    }

    @Test
    public void addSignatureWithNonParseableRskTx() throws Exception{
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = new Transaction(config, PrecompiledContracts.BRIDGE_ADDR_STR, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(rskTx, new BlockGenerator().getGenesisBlock(), track, null, null, null);

        byte[] federatorPublicKeySerialized = new BtcECKey().getPubKey();
        Object[] signaturesObjectArray = new Object[]{new BtcECKey().sign(Sha256Hash.ZERO_HASH).encodeToDER()};
        byte[] rskTxHash = new byte[3];
        byte[] data = Bridge.ADD_SIGNATURE.encode(federatorPublicKeySerialized, signaturesObjectArray, rskTxHash);

        Assert.assertNull(bridge.execute(data));
    }

    @Test
    public void exceptionInUpdateCollection() {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);

        try {
            bridge.updateCollections(null);
            Assert.fail();
        }
        catch (RuntimeException ex) {
            Assert.assertEquals("Exception onBlock", ex.getMessage());
        }
    }

    @Test
    public void exceptionInReleaseBtc() {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);

        try {
            bridge.releaseBtc(null);
            Assert.fail();
        }
        catch (RuntimeException ex) {
            Assert.assertEquals("Exception in releaseBtc", ex.getMessage());
        }
    }

    @Test
    public void exceptionInGetStateForBtcReleaseClient() {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);

        try {
            bridge. getStateForBtcReleaseClient(null);
            Assert.fail();
        }
        catch (RuntimeException ex) {
            Assert.assertEquals("Exception in getStateForBtcReleaseClient", ex.getMessage());
        }
    }

    @Test
    public void exceptionInGetStateForDebugging() {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);

        try {
            bridge.getStateForDebugging(null);
            Assert.fail();
        }
        catch (RuntimeException ex) {
            Assert.assertEquals("Exception in getStateForDebugging", ex.getMessage());
        }
    }

    @Test
    public void exceptionInGetBtcBlockchainBestChainHeight() {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);

        try {
            bridge.getBtcBlockchainBestChainHeight(null);
            Assert.fail();
        }
        catch (RuntimeException ex) {
            Assert.assertEquals("Exception in getBtcBlockchainBestChainHeight", ex.getMessage());
        }
    }

    @Test
    public void exceptionInGetBtcBlockchainBlockLocator() {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);

        try {
            bridge.getBtcBlockchainBlockLocator(null);
            Assert.fail();
        }
        catch (RuntimeException ex) {
            Assert.assertEquals("Exception in getBtcBlockchainBlockLocator", ex.getMessage());
        }
    }

    @Test
    public void getBtcBlockchainBlockLocatorBeforeRskip89Fork() throws Exception {
        GenesisConfig mockedConfig = spy(new GenesisConfig());
        config.setBlockchainConfig(mockedConfig);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        String hashedString = "0000000000000000000000000000000000000000000000000000000000000001";

        Sha256Hash hash = Sha256Hash.wrap(hashedString);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportMock.getBtcBlockchainBlockLocator())
                .then((InvocationOnMock invocation) -> Arrays.asList(hash));

        Bridge spiedBridge = PowerMockito.spy(new Bridge(config, PrecompiledContracts.BRIDGE_ADDR));
        PowerMockito.doReturn(bridgeSupportMock).when(spiedBridge, "setup");

        spiedBridge.init(mock(Transaction.class), getGenesisBlock(), track, null, null, null);

        byte[] result = spiedBridge.execute(Bridge.GET_BTC_BLOCKCHAIN_BLOCK_LOCATOR.encode(new Object[]{ }));
        Object[] decodedResult = (Object[]) BridgeMethods.GET_BTC_BLOCKCHAIN_BLOCK_LOCATOR.getFunction().decodeResult(result)[0];

        Assert.assertEquals(1, decodedResult.length);
        Assert.assertEquals(hashedString, decodedResult[0]);
    }

    @Test
    public void getBtcBlockchainBlockLocatorAfterRskip88And89Fork() {
        GenesisConfig mockedConfig = spy(new GenesisConfig());
        when(mockedConfig.isRskip88()).thenReturn(true);
        when(mockedConfig.isRskip89()).thenReturn(true);
        config.setBlockchainConfig(mockedConfig);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);

        bridge.init(mock(Transaction.class), getGenesisBlock(), track, null, null, null);

        try {
            bridge.execute(Bridge.GET_BTC_BLOCKCHAIN_BLOCK_LOCATOR.encode(new Object[]{ }));
            Assert.fail();
        } catch (RuntimeException e) {
            Assert.assertTrue(e.getMessage().contains("Invalid data given:"));

        }
    }

    private BtcTransaction createTransaction() {
        return new SimpleBtcTransaction(networkParameters, PegTestUtils.createHash());
    }

    @Test
    public void getGasForDataFreeTx() {
        BlockchainNetConfig blockchainNetConfigOriginal = config.getBlockchainConfig();
        config.setBlockchainConfig(new UnitTestBlockchainNetConfig());

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);

        org.ethereum.core.Transaction rskTx = CallTransaction.createCallTransaction(
                config, 0,
                1,
                1,
                PrecompiledContracts.BRIDGE_ADDR,
                0,
                Bridge.UPDATE_COLLECTIONS);
        rskTx.sign(((BridgeRegTestConstants)bridgeConstants).getFederatorPrivateKeys().get(0).getPrivKeyBytes());

        Block rskExecutionBlock = new BlockGenerator().createChildBlock(RskTestFactory.getGenesisInstance(config));

        Repository mockRepository = mock(Repository.class);
        when(mockRepository.getCode(any(RskAddress.class))).thenReturn(null);

        bridge.init(rskTx, rskExecutionBlock, mockRepository, null, null, null);
        Assert.assertEquals(0, bridge.getGasForData(rskTx.getData()));

        config.setBlockchainConfig(blockchainNetConfigOriginal);
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
        getGasForDataPaidTx(22000 + 228*2, Bridge.REGISTER_BTC_TRANSACTION, new byte[3], 1, new byte[3]);
    }

    @Test
    public void getGasForDataReleaseBtc() {
        getGasForDataPaidTx(23000 + 8, Bridge.RELEASE_BTC);
    }

    @Test
    public void getGasForDataAddSignature() {
        getGasForDataPaidTx(70000 + 548*2, Bridge.ADD_SIGNATURE, new byte[3], new byte[3][2], new byte[3]);
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

    private void getGasForDataPaidTx(int expected, CallTransaction.Function function, Object... funcArgs) {
        BlockchainNetConfig blockchainNetConfigOriginal = config.getBlockchainConfig();
        config.setBlockchainConfig(new UnitTestBlockchainNetConfig());

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        org.ethereum.core.Transaction rskTx;
        if (function==null) {
            rskTx = CallTransaction.createRawTransaction(
                    config, 0,
                    1,
                    1,
                    PrecompiledContracts.BRIDGE_ADDR,
                    0,
                    new byte[]{1,2,3});
        } else {
            rskTx = CallTransaction.createCallTransaction(
                    config, 0,
                    1,
                    1,
                    PrecompiledContracts.BRIDGE_ADDR,
                    0,
                    function,
                    funcArgs);
        }

        rskTx.sign(((BridgeRegTestConstants)bridgeConstants).getFederatorPrivateKeys().get(0).getPrivKeyBytes());

        BlockGenerator blockGenerator = new BlockGenerator();
        Block rskExecutionBlock = blockGenerator.createChildBlock(RskTestFactory.getGenesisInstance(config));
        for (int i = 0; i < 20; i++) {
            rskExecutionBlock = blockGenerator.createChildBlock(rskExecutionBlock);
        }

        Repository mockRepository = mock(Repository.class);
        when(mockRepository.getCode(any(RskAddress.class))).thenReturn(null);

        bridge.init(rskTx, rskExecutionBlock, mockRepository, null, null, null);
        Assert.assertEquals(expected, bridge.getGasForData(rskTx.getData()));

        config.setBlockchainConfig(blockchainNetConfigOriginal);
    }

    @Test
    public void isBtcTxHashAlreadyProcessed_normalFlow() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, getGenesisBlock(), null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        Set<Sha256Hash> hashes = new HashSet<>();
        when(bridgeSupportMock.isBtcTxHashAlreadyProcessed(any(Sha256Hash.class))).then((InvocationOnMock invocation) -> hashes.contains(invocation.getArgumentAt(0, Sha256Hash.class)));

        hashes.add(Sha256Hash.of("hash_1".getBytes()));
        hashes.add(Sha256Hash.of("hash_2".getBytes()));
        hashes.add(Sha256Hash.of("hash_3".getBytes()));
        hashes.add(Sha256Hash.of("hash_4".getBytes()));

        for (Sha256Hash hash : hashes) {
            Assert.assertTrue(bridge.isBtcTxHashAlreadyProcessed(new Object[]{hash.toString()}));
            verify(bridgeSupportMock).isBtcTxHashAlreadyProcessed(hash);
        }
        Assert.assertFalse(bridge.isBtcTxHashAlreadyProcessed(new Object[]{Sha256Hash.of("anything".getBytes()).toString()}));
        Assert.assertFalse(bridge.isBtcTxHashAlreadyProcessed(new Object[]{Sha256Hash.of("yetanotheranything".getBytes()).toString()}));
    }

    @Test
    public void isBtcTxHashAlreadyProcessed_exception() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, getGenesisBlock(), null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);

        boolean thrown = false;
        try {
            bridge.isBtcTxHashAlreadyProcessed(new Object[]{"notahash"});
        } catch (RuntimeException e) {
            thrown = true;
        }
        Assert.assertTrue(thrown);
        verify(bridgeSupportMock, never()).isBtcTxHashAlreadyProcessed(any());
    }

    @Test
    public void getBtcTxHashProcessedHeight_normalFlow() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, getGenesisBlock(), null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        Map<Sha256Hash, Long> hashes = new HashMap<>();
        when(bridgeSupportMock.getBtcTxHashProcessedHeight(any(Sha256Hash.class))).then((InvocationOnMock invocation) -> hashes.get(invocation.getArgumentAt(0, Sha256Hash.class)));

        hashes.put(Sha256Hash.of("hash_1".getBytes()), 1L);
        hashes.put(Sha256Hash.of("hash_2".getBytes()), 2L);
        hashes.put(Sha256Hash.of("hash_3".getBytes()), 3L);
        hashes.put(Sha256Hash.of("hash_4".getBytes()), 4L);

        for (Map.Entry<Sha256Hash, Long> entry : hashes.entrySet()) {
            Assert.assertEquals(entry.getValue(), bridge.getBtcTxHashProcessedHeight(new Object[]{entry.getKey().toString()}));
            verify(bridgeSupportMock).getBtcTxHashProcessedHeight(entry.getKey());
        }
        Assert.assertNull(bridge.getBtcTxHashProcessedHeight(new Object[]{Sha256Hash.of("anything".getBytes()).toString()}));
    }

    @Test
    public void getBtcTxHashProcessedHeight_exception() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, getGenesisBlock(), null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);

        boolean thrown = false;
        try {
            bridge.getBtcTxHashProcessedHeight(new Object[]{"notahash"});
        } catch (RuntimeException e) {
            thrown = true;
        }
        Assert.assertTrue(thrown);
        verify(bridgeSupportMock, never()).getBtcTxHashProcessedHeight(any());
    }

    @Test
    public void getFederationSize() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, getGenesisBlock(), null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getFederationSize()).thenReturn(1234);

        Assert.assertEquals(1234, bridge.getFederationSize(new Object[]{}).intValue());
    }

    @Test
    public void getFederationThreshold() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, getGenesisBlock(), null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getFederationThreshold()).thenReturn(5678);

        Assert.assertEquals(5678, bridge.getFederationThreshold(new Object[]{}).intValue());
    }

    @Test
    public void getFederationCreationTime() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, getGenesisBlock(), null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getFederationCreationTime()).thenReturn(Instant.ofEpochMilli(5000));

        Assert.assertEquals(5000, bridge.getFederationCreationTime(new Object[]{}).intValue());
    }

    @Test
    public void getFederationCreationBlockNumber() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getFederationCreationBlockNumber()).thenReturn(42L);

        Assert.assertThat(bridge.getFederationCreationBlockNumber(new Object[]{}), is(42L));
    }

    @Test
    public void getFederatorPublicKey() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, getGenesisBlock(), null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getFederatorPublicKey(any(int.class))).then((InvocationOnMock invocation) ->
                BigInteger.valueOf(invocation.getArgumentAt(0, int.class)).toByteArray());

        Assert.assertTrue(Arrays.equals(new byte[]{10}, bridge.getFederatorPublicKey(new Object[]{BigInteger.valueOf(10)})));
        Assert.assertTrue(Arrays.equals(new byte[]{20}, bridge.getFederatorPublicKey(new Object[]{BigInteger.valueOf(20)})));
        Assert.assertTrue(Arrays.equals(new byte[]{1, 0}, bridge.getFederatorPublicKey(new Object[]{BigInteger.valueOf(256)})));
    }

    @Test
    public void getRetiringFederationSize() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, getGenesisBlock(), null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getRetiringFederationSize()).thenReturn(1234);

        Assert.assertEquals(1234, bridge.getRetiringFederationSize(new Object[]{}).intValue());
    }

    @Test
    public void getRetiringFederationThreshold() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, getGenesisBlock(), null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getRetiringFederationThreshold()).thenReturn(5678);

        Assert.assertEquals(5678, bridge.getRetiringFederationThreshold(new Object[]{}).intValue());
    }

    @Test
    public void getRetiringFederationCreationTime() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, getGenesisBlock(), null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getRetiringFederationCreationTime()).thenReturn(Instant.ofEpochMilli(5000));

        Assert.assertEquals(5000, bridge.getRetiringFederationCreationTime(new Object[]{}).intValue());
    }

    @Test
    public void getRetiringFederationCreationBlockNumber() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getRetiringFederationCreationBlockNumber()).thenReturn(42L);

        Assert.assertThat(bridge.getRetiringFederationCreationBlockNumber(new Object[]{}), is(42L));
    }

    @Test
    public void getRetiringFederatorPublicKey() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, getGenesisBlock(), null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getRetiringFederatorPublicKey(any(int.class))).then((InvocationOnMock invocation) ->
                BigInteger.valueOf(invocation.getArgumentAt(0, int.class)).toByteArray());

        Assert.assertTrue(Arrays.equals(new byte[]{10}, bridge.getRetiringFederatorPublicKey(new Object[]{BigInteger.valueOf(10)})));
        Assert.assertTrue(Arrays.equals(new byte[]{20}, bridge.getRetiringFederatorPublicKey(new Object[]{BigInteger.valueOf(20)})));
        Assert.assertTrue(Arrays.equals(new byte[]{1, 0}, bridge.getRetiringFederatorPublicKey(new Object[]{BigInteger.valueOf(256)})));
    }

    @Test
    public void getPendingFederationSize() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, getGenesisBlock(), null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getPendingFederationSize()).thenReturn(1234);

        Assert.assertEquals(1234, bridge.getPendingFederationSize(new Object[]{}).intValue());
    }

    @Test
    public void getPendingFederatorPublicKey() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, getGenesisBlock(), null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getPendingFederatorPublicKey(any(int.class))).then((InvocationOnMock invocation) ->
                BigInteger.valueOf(invocation.getArgumentAt(0, int.class)).toByteArray());

        Assert.assertTrue(Arrays.equals(new byte[]{10}, bridge.getPendingFederatorPublicKey(new Object[]{BigInteger.valueOf(10)})));
        Assert.assertTrue(Arrays.equals(new byte[]{20}, bridge.getPendingFederatorPublicKey(new Object[]{BigInteger.valueOf(20)})));
        Assert.assertTrue(Arrays.equals(new byte[]{1, 0}, bridge.getPendingFederatorPublicKey(new Object[]{BigInteger.valueOf(256)})));
    }

    @Test
    public void createFederation() throws IOException {
        Transaction txMock = mock(Transaction.class);
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(txMock, getGenesisBlock(), null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.voteFederationChange(txMock, new ABICallSpec("create", new byte[][]{}))).thenReturn(123);

        Assert.assertEquals(123, bridge.createFederation(new Object[]{}).intValue());
    }

    @Test
    public void addFederatorPublicKey_ok() throws IOException {
        Transaction txMock = mock(Transaction.class);
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(txMock, getGenesisBlock(), null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.voteFederationChange(txMock, new ABICallSpec("add", new byte[][] { Hex.decode("aabbccdd") })))
                .thenReturn(123);

        Assert.assertEquals(123, bridge.addFederatorPublicKey(new Object[]{Hex.decode("aabbccdd")}).intValue());
    }

    @Test
    public void addFederatorPublicKey_wrongParameterType() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, getGenesisBlock(), null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);

        Assert.assertEquals(-10, bridge.addFederatorPublicKey(new Object[]{ "i'm not a byte array" }).intValue());
        verify(bridgeSupportMock, never()).voteFederationChange(any(), any());
    }

    @Test
    public void commitFederation_ok() throws IOException {
        Transaction txMock = mock(Transaction.class);
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(txMock, getGenesisBlock(), null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);

        when(bridgeSupportMock.voteFederationChange(txMock, new ABICallSpec("commit", new byte[][] { Hex.decode("01020304") }))).thenReturn(123);

        Assert.assertEquals(123, bridge.commitFederation(new Object[]{ Hex.decode("01020304") }).intValue());
    }

    @Test
    public void commitFederation_wrongParameterType() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, getGenesisBlock(), null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);

        Assert.assertEquals(-10, bridge.commitFederation(new Object[]{ "i'm not a byte array" }).intValue());
        verify(bridgeSupportMock, never()).voteFederationChange(any(), any());
    }

    @Test
    public void rollbackFederation() throws IOException {
        Transaction txMock = mock(Transaction.class);
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(txMock, getGenesisBlock(), null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.voteFederationChange(txMock, new ABICallSpec("rollback", new byte[][]{}))).thenReturn(456);

        Assert.assertEquals(456, bridge.rollbackFederation(new Object[]{}).intValue());
    }

    @Test
    public void getLockWhitelistSize() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, getGenesisBlock(), null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getLockWhitelistSize()).thenReturn(1234);

        Assert.assertEquals(1234, bridge.getLockWhitelistSize(new Object[]{}).intValue());
    }

    @Test
    public void getLockWhitelistAddress() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, getGenesisBlock(), null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        OneOffWhiteListEntry mockedEntry10 = new OneOffWhiteListEntry(new BtcECKey().toAddress(networkParameters), Coin.COIN);
        OneOffWhiteListEntry mockedEntry20 = new OneOffWhiteListEntry(new BtcECKey().toAddress(networkParameters), Coin.COIN);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getLockWhitelistEntryByIndex(10)).then((InvocationOnMock invocation) -> mockedEntry10);
        when(bridgeSupportMock.getLockWhitelistEntryByIndex(20)).then((InvocationOnMock invocation) -> mockedEntry20);

        Assert.assertEquals(mockedEntry10.address().toBase58(), bridge.getLockWhitelistAddress(new Object[]{BigInteger.valueOf(10)}));
        Assert.assertEquals(mockedEntry20.address().toBase58(), bridge.getLockWhitelistAddress(new Object[]{BigInteger.valueOf(20)}));
    }

    @Test
    public void getLockWhitelistEntryByAddressBeforeRskip87And88Fork() throws IOException {
        GenesisConfig mockedConfig = spy(new GenesisConfig());
        when(mockedConfig.isRskip87()).thenReturn(false);
        when(mockedConfig.isRskip88()).thenReturn(false);
        config.setBlockchainConfig(mockedConfig);

        Address address = new BtcECKey().toAddress(networkParameters);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction mockedTransaction = mock(Transaction.class);
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(mockedTransaction, getGenesisBlock(), track, null, null, null);

        Assert.assertNull(bridge.execute(Bridge.GET_LOCK_WHITELIST_ENTRY_BY_ADDRESS.encode(new Object[]{ address.toBase58() })));
    }

    @Test
    public void getLockWhitelistEntryByAddressAfterRskip87Fork() throws IOException, Exception {
        byte[] result;
        Transaction mockedTransaction;

        GenesisConfig mockedConfig = spy(new GenesisConfig());
        when(mockedConfig.isRskip87()).thenReturn(true);
        config.setBlockchainConfig(mockedConfig);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Address mockedAddressForUnlimited = new BtcECKey().toAddress(networkParameters);
        Address mockedAddressForOneOff = new BtcECKey().toAddress(networkParameters);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportMock.getLockWhitelistEntryByAddress(mockedAddressForUnlimited.toBase58()))
                .then((InvocationOnMock invocation) -> new UnlimitedWhiteListEntry(mockedAddressForUnlimited));
        when(bridgeSupportMock.getLockWhitelistEntryByAddress(mockedAddressForOneOff.toBase58()))
                .then((InvocationOnMock invocation) -> new OneOffWhiteListEntry(mockedAddressForOneOff, Coin.COIN));

        Bridge spiedBridge = PowerMockito.spy(new Bridge(config, PrecompiledContracts.BRIDGE_ADDR));
        PowerMockito.doReturn(bridgeSupportMock).when(spiedBridge, "setup");

        mockedTransaction = mock(Transaction.class);
        when(mockedTransaction.isLocalCallTransaction()).thenReturn(true);
        spiedBridge.init(mockedTransaction, getGenesisBlock(), track, null, null, null);

        // Get the unlimited whitelist address
        result = spiedBridge.execute(Bridge.GET_LOCK_WHITELIST_ENTRY_BY_ADDRESS.encode(new Object[]{ mockedAddressForUnlimited.toBase58() }));
        BigInteger decodedResult = (BigInteger) BridgeMethods.GET_LOCK_WHITELIST_ENTRY_BY_ADDRESS.getFunction().decodeResult(result)[0];

        Assert.assertEquals(0, decodedResult.longValue());

        // Get the one-off whitelist address
        result = spiedBridge.execute(Bridge.GET_LOCK_WHITELIST_ENTRY_BY_ADDRESS.encode(new Object[]{ mockedAddressForOneOff.toBase58() }));
        decodedResult = (BigInteger) BridgeMethods.GET_LOCK_WHITELIST_ENTRY_BY_ADDRESS.getFunction().decodeResult(result)[0];

        Assert.assertEquals(Coin.COIN.value, decodedResult.longValue());

        // Try fetch an unexisting address
        result = spiedBridge.execute(Bridge.GET_LOCK_WHITELIST_ENTRY_BY_ADDRESS.encode(new Object[]{ (new BtcECKey().toAddress(networkParameters)).toBase58() }));
        decodedResult = (BigInteger) BridgeMethods.GET_LOCK_WHITELIST_ENTRY_BY_ADDRESS.getFunction().decodeResult(result)[0];

        Assert.assertEquals(-1, decodedResult.longValue());
    }

    @Test
    public void addLockWhitelistAddressBeforeRskip87Fork() throws IOException {
        GenesisConfig mockedConfig = spy(new GenesisConfig());
        when(mockedConfig.isRskip87()).thenReturn(false);
        config.setBlockchainConfig(mockedConfig);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction mockedTransaction = mock(Transaction.class);
        // Just setting a random address as the sender
        RskAddress sender = new RskAddress(fedECPrivateKey.getAddress());
        when(mockedTransaction.getSender()).thenReturn(sender);

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(mockedTransaction, getGenesisBlock(), track, null, null, null);

        byte[] result = bridge.execute(Bridge.ADD_LOCK_WHITELIST_ADDRESS.encode(new Object[]{
                new BtcECKey().toAddress(networkParameters).toBase58(),
                BigInteger.valueOf(Coin.COIN.getValue())
        }));

        BigInteger decodedResult = (BigInteger) BridgeMethods.ADD_LOCK_WHITELIST_ADDRESS.getFunction().decodeResult(result)[0];

        Assert.assertEquals(BridgeSupport.LOCK_WHITELIST_GENERIC_ERROR_CODE.intValue(), decodedResult.intValue());
    }

    @Test
    public void addLockWhitelistAddressAfterRskip87And88Fork() throws IOException {
        GenesisConfig mockedConfig = spy(new GenesisConfig());
        when(mockedConfig.isRskip87()).thenReturn(true);
        when(mockedConfig.isRskip88()).thenReturn(true);
        config.setBlockchainConfig(mockedConfig);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction mockedTransaction = mock(Transaction.class);
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(mockedTransaction, getGenesisBlock(), track, null, null, null);

        try {
            bridge.execute(Bridge.ADD_LOCK_WHITELIST_ADDRESS.encode(new Object[]{ "i-am-an-address", BigInteger.valueOf(25L) }));
            Assert.fail();
        } catch(Exception e) {
            Throwable causeException = e.getCause();
            Assert.assertEquals(BridgeIllegalArgumentException.class, causeException.getClass());
            Assert.assertTrue(causeException.getMessage().startsWith("Invalid data given"));
        }
    }

    @Test
    public void addOneOffLockWhitelistAddressBeforeRskip87And88Fork() throws IOException {
        GenesisConfig mockedConfig = spy(new GenesisConfig());
        when(mockedConfig.isRskip87()).thenReturn(false);
        when(mockedConfig.isRskip88()).thenReturn(false);
        config.setBlockchainConfig(mockedConfig);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction mockedTransaction = mock(Transaction.class);
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(mockedTransaction, getGenesisBlock(), track, null, null, null);

        Assert.assertNull(bridge.execute(Bridge.ADD_ONE_OFF_LOCK_WHITELIST_ADDRESS.encode(new Object[]{ "i-am-an-address", BigInteger.valueOf(25L) })));
    }

    @Test
    public void addOneOffLockWhitelistAddressAfterRskip87Fork() throws IOException {
        GenesisConfig mockedConfig = spy(new GenesisConfig());
        when(mockedConfig.isRskip87()).thenReturn(true);
        config.setBlockchainConfig(mockedConfig);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction mockedTransaction = mock(Transaction.class);
        // Just setting a random address as the sender
        RskAddress sender = new RskAddress(fedECPrivateKey.getAddress());
        when(mockedTransaction.getSender()).thenReturn(sender);

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(mockedTransaction, getGenesisBlock(), track, null, null, null);

        byte[] result = bridge.execute(Bridge.ADD_ONE_OFF_LOCK_WHITELIST_ADDRESS.encode(new Object[]{
                new BtcECKey().toAddress(networkParameters).toBase58(),
                BigInteger.valueOf(Coin.COIN.getValue())
        }));

        BigInteger decodedResult = (BigInteger) BridgeMethods.ADD_ONE_OFF_LOCK_WHITELIST_ADDRESS.getFunction().decodeResult(result)[0];

        Assert.assertEquals(BridgeSupport.LOCK_WHITELIST_GENERIC_ERROR_CODE.intValue(), decodedResult.intValue());
    }

    @Test
    public void addUnlimitedLockWhitelistAddressBeforeRskip87And88Fork() {
        GenesisConfig mockedConfig = spy(new GenesisConfig());
        when(mockedConfig.isRskip87()).thenReturn(false);
        when(mockedConfig.isRskip88()).thenReturn(false);
        config.setBlockchainConfig(mockedConfig);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction mockedTransaction = mock(Transaction.class);
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(mockedTransaction, getGenesisBlock(), track, null, null, null);

        Assert.assertNull(bridge.execute(Bridge.ADD_UNLIMITED_LOCK_WHITELIST_ADDRESS.encode(new Object[]{ "i-am-an-address" })));
    }

    @Test
    public void addUnlimitedLockWhitelistAddressAfterRskip87Fork() {
        GenesisConfig mockedConfig = spy(new GenesisConfig());
        when(mockedConfig.isRskip87()).thenReturn(true);
        config.setBlockchainConfig(mockedConfig);

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction mockedTransaction = mock(Transaction.class);
        // Just setting a random address as the sender
        RskAddress sender = new RskAddress(fedECPrivateKey.getAddress());
        when(mockedTransaction.getSender()).thenReturn(sender);

        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(mockedTransaction, getGenesisBlock(), track, null, null, null);

        byte[] result = bridge.execute(Bridge.ADD_UNLIMITED_LOCK_WHITELIST_ADDRESS.encode(new Object[]{
                new BtcECKey().toAddress(networkParameters).toBase58()
        }));

        BigInteger decodedResult = (BigInteger) BridgeMethods.ADD_UNLIMITED_LOCK_WHITELIST_ADDRESS.getFunction().decodeResult(result)[0];
        bridge.init(mockedTransaction, getGenesisBlock(), null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.addOneOffLockWhitelistAddress(mockedTransaction, "i-am-an-address", BigInteger.valueOf(Coin.COIN.getValue()))).thenReturn(1234);

        Assert.assertEquals(BridgeSupport.LOCK_WHITELIST_GENERIC_ERROR_CODE.intValue(), decodedResult.intValue());
    }

    @Test
    public void removeLockWhitelistAddress() throws IOException {
        Transaction mockedTransaction = mock(Transaction.class);
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(mockedTransaction, getGenesisBlock(), null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.removeLockWhitelistAddress(mockedTransaction, "i-am-an-address")).thenReturn(1234);

        Assert.assertEquals(1234, bridge.removeLockWhitelistAddress(new Object[]{ "i-am-an-address" }).intValue());
    }

    @Test
    public void getFeePerKb() {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getFeePerKb())
                .thenReturn(Coin.valueOf(12345678901234L));

        Assert.assertEquals(12345678901234L, bridge.getFeePerKb(new Object[]{}));
    }

    @Test
    public void voteFeePerKb_ok() throws IOException {
        Transaction txMock = mock(Transaction.class);
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(txMock, getGenesisBlock(), null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.voteFeePerKbChange(txMock, Coin.valueOf(2)))
                .thenReturn(123);

        Assert.assertEquals(123, bridge.voteFeePerKbChange(new Object[]{BigInteger.valueOf(2)}).intValue());
    }

    @Test
    public void voteFeePerKb_wrongParameterType() throws IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, getGenesisBlock(), null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);

        Assert.assertEquals(-10, bridge.voteFeePerKbChange(new Object[]{ "i'm not a byte array" }).intValue());
        verify(bridgeSupportMock, never()).voteFederationChange(any(), any());
    }

    @Test
    public void precompiledContractAddress() {
        Assert.assertArrayEquals(
                PrecompiledContracts.BRIDGE_ADDR.getBytes(),
                Hex.decode(PrecompiledContracts.BRIDGE_ADDR_STR));
        Assert.assertArrayEquals(
                PrecompiledContracts.BRIDGE_ADDR.getBytes(),
                TypeConverter.stringHexToByteArray(PrecompiledContracts.BRIDGE_ADDR_STR));
    }

    @Test
    public void testBlock457BridgeCall() throws Exception {
        // block 457 in mainnet exposed a bug in a fix made to SolidityType. The purpose of this test is to make sure this block keeps working
        // block 457 was the first federate call.
        byte[] data = Files.readAllBytes(Paths.get(this.getClass().getResource("/bridge/block457.bin").toURI()));

        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Transaction rskTx = new Transaction(config, PrecompiledContracts.BRIDGE_ADDR_STR, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        rskTx.sign(fedECPrivateKey.getPrivKeyBytes());

        // Setup bridge
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(rskTx, new BlockGenerator().getGenesisBlock(), track, null, null, null);

        Logger mockedLogger = mock(Logger.class);
        setFinalStatic(Bridge.class.getDeclaredField("logger"), mockedLogger);

        bridge.execute(data);
        verify(mockedLogger, never()).warn(any(String.class), any(), any()); // "Invalid function arguments {} for function {}."
    }

    @Test
    public void executeMethodWithOnlyLocalCallsAllowed_localCallTx() throws Exception {
        Transaction tx = mock(Transaction.class);
        when(tx.isLocalCallTransaction()).thenReturn(true);
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(tx, getGenesisBlock(), null, null, null, null);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        PowerMockito.whenNew(BridgeSupport.class).withAnyArguments().thenReturn(bridgeSupportMock);
        Address address = new BtcECKey().toAddress(networkParameters);
        when(bridgeSupportMock.getFederationAddress()).thenReturn(address);

        byte[] data = BridgeMethods.GET_FEDERATION_ADDRESS.getFunction().encode(new Object[]{});
        bridge.execute(data);

        verify(bridgeSupportMock, times(1)).getFederationAddress();
    }

    @Test
    public void executeMethodWithOnlyLocalCallsAllowed_nonLocalCallTx_beforeOrchid() throws Exception {
        GenesisConfig mockedConfig = spy(new GenesisConfig());
        when(mockedConfig.isRskip88()).thenReturn(false);
        config.setBlockchainConfig(mockedConfig);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        when(bridgeSupportMock.getFederationAddress()).thenReturn(new BtcECKey().toAddress(networkParameters));
        PowerMockito.whenNew(BridgeSupport.class).withAnyArguments().thenReturn(bridgeSupportMock);

        Transaction tx = mock(Transaction.class);
        when(tx.isLocalCallTransaction()).thenReturn(false);
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(tx, getGenesisBlock(), null, null, null, null);

        byte[] data = BridgeMethods.GET_FEDERATION_ADDRESS.getFunction().encode(new Object[]{});
        bridge.execute(data);
    }

    @Test
    public void executeMethodWithOnlyLocalCallsAllowed_nonLocalCallTx() throws Exception {
        GenesisConfig mockedConfig = spy(new GenesisConfig());
        when(mockedConfig.isRskip88()).thenReturn(true);
        config.setBlockchainConfig(mockedConfig);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        PowerMockito.whenNew(BridgeSupport.class).withAnyArguments().thenReturn(bridgeSupportMock);

        try {
            Transaction tx = mock(Transaction.class);
            when(tx.isLocalCallTransaction()).thenReturn(false);
            Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
            bridge.init(tx, getGenesisBlock(), null, null, null, null);

            byte[] data = BridgeMethods.GET_FEDERATION_ADDRESS.getFunction().encode(new Object[]{});
            bridge.execute(data);
            Assert.fail();
        } catch (RuntimeException e) {
            verify(bridgeSupportMock, never()).getFederationAddress();
            Assert.assertTrue(e.getMessage().contains("Non-local-call"));
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

    private void executeAndCheckMethodWithAnyCallsAllowed(boolean localCall) throws Exception {
        Transaction tx = mock(Transaction.class);
        when(tx.isLocalCallTransaction()).thenReturn(localCall);
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(tx, getGenesisBlock(), null, null, null, null);

        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        PowerMockito.whenNew(BridgeSupport.class).withAnyArguments().thenReturn(bridgeSupportMock);
        when(bridgeSupportMock.voteFeePerKbChange(tx, Coin.CENT)).thenReturn(1);

        byte[] data = BridgeMethods.VOTE_FEE_PER_KB.getFunction().encode(new Object[]{ Coin.CENT.longValue() });
        bridge.execute(data);

        verify(bridgeSupportMock, times(1)).voteFeePerKbChange(tx, Coin.CENT);
    }

    // We need reflection to mock static final fields
    private void setFinalStatic(Field field, Object newValue) throws Exception {
        field.setAccessible(true);
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        field.set(null, newValue);
    }

    public void getBtcBlockchainInitialBlockHeight() throws  IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, getGenesisBlock(), null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        when(bridgeSupportMock.getBtcBlockchainInitialBlockHeight()).thenReturn(1234);

        Assert.assertEquals(1234, bridge.getBtcBlockchainInitialBlockHeight(new Object[]{}).intValue());
    }

    @Test
    public void getBtcBlockchainBlockHashAtDepth() throws BlockStoreException, IOException {
        Bridge bridge = new Bridge(config, PrecompiledContracts.BRIDGE_ADDR);
        bridge.init(null, getGenesisBlock(), null, null, null, null);
        BridgeSupport bridgeSupportMock = mock(BridgeSupport.class);
        Whitebox.setInternalState(bridge, "bridgeSupport", bridgeSupportMock);
        Sha256Hash mockedResult = Sha256Hash.of(Hex.decode("aabbcc"));
        when(bridgeSupportMock.getBtcBlockchainBlockHashAtDepth(555)).thenReturn(mockedResult);

        Assert.assertEquals(mockedResult, Sha256Hash.wrap(bridge.getBtcBlockchainBlockHashAtDepth(new Object[]{BigInteger.valueOf(555)})));
    }

    private Block getGenesisBlock() {
        return new BlockGenerator().getGenesisBlock();
    }

    @Test
    public void testCallFromContract_beforeOrchid() {
        GenesisConfig mockedConfig = spy(new GenesisConfig());
        when(mockedConfig.isRskip88()).thenReturn(false);
        config.setBlockchainConfig(mockedConfig);

        PrecompiledContracts precompiledContracts = new PrecompiledContracts(config);
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
        String bridgeFunctionHex = Hex.toHexString(Bridge.GET_MINIMUM_LOCK_TX_VALUE.encode());
        bridgeFunctionHex = String.format("0x%s%s", bridgeFunctionHex, String.join("", Collections.nCopies(32 * 2 - bridgeFunctionHex.length(), "0")));
        String asm = String.format("%s 0x00 MSTORE 0x20 0x30 0x20 0x00 0x0000000000000000000000000000000001000006 0x6000 DELEGATECALL", bridgeFunctionHex);
        int numOps = asm.split(" ").length;
        byte[] code = assembler.assemble(asm);

        // Mock a transaction, all we really need is a hash
        Transaction tx = mock(Transaction.class);
        when(tx.getHash()).thenReturn(new Keccak256("001122334455667788990011223344556677889900112233445566778899aabb"));

        try {
            // Run the program on the VM
            Program program = new Program(config.getVmConfig(), precompiledContracts, mock(BlockchainConfig.class), code, invoke, tx);
            for (int i = 0; i < numOps; i++) {
                vm.step(program);
            }
            Assert.fail();
        } catch (NullPointerException e) {
            Assert.assertNull(e.getMessage());
        }
    }

    @Test
    public void testCallFromContract_afterOrchid() {
        GenesisConfig mockedConfig = spy(new GenesisConfig());
        when(mockedConfig.isRskip88()).thenReturn(true);
        config.setBlockchainConfig(mockedConfig);

        PrecompiledContracts precompiledContracts = new PrecompiledContracts(config);
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
        String bridgeFunctionHex = Hex.toHexString(Bridge.GET_MINIMUM_LOCK_TX_VALUE.encode());
        bridgeFunctionHex = String.format("0x%s%s", bridgeFunctionHex, String.join("", Collections.nCopies(32 * 2 - bridgeFunctionHex.length(), "0")));
        String asm = String.format("%s 0x00 MSTORE 0x20 0x30 0x20 0x00 0x0000000000000000000000000000000001000006 0x6000 DELEGATECALL", bridgeFunctionHex);
        int numOps = asm.split(" ").length;
        byte[] code = assembler.assemble(asm);

        // Mock a transaction, all we really need is a hash
        Transaction tx = mock(Transaction.class);
        when(tx.getHash()).thenReturn(new Keccak256("001122334455667788990011223344556677889900112233445566778899aabb"));

        // Run the program on the VM
        Program program = new Program(config.getVmConfig(), precompiledContracts, mock(BlockchainConfig.class), code, invoke, tx);
        try {
            for (int i = 0; i < numOps; i++) {
                vm.step(program);
            }
            Assert.fail();
        } catch (RuntimeException e) {
            Assert.assertTrue(e.getMessage().contains("Non-local-call"));
        }
    }

    @Test
    public void localCallOnlyMethodsDefinition() {
        // To force initialization
        String foo = Bridge.UPDATE_COLLECTIONS.name;

        // Actual tests
        Arrays.asList(
            BridgeMethods.GET_BTC_BLOCKCHAIN_BEST_CHAIN_HEIGHT,
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
            Assert.assertTrue(m.onlyAllowsLocalCalls());
        });
    }

    @Test
    public void mineableMethodsDefinition() {
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
                BridgeMethods.VOTE_FEE_PER_KB
        ).stream().forEach(m -> {
            Assert.assertFalse(m.onlyAllowsLocalCalls());
        });
    }

    private static Repository createRepository() {
        return new MutableRepository(new MutableTrieCache(new MutableTrieImpl(new TrieImpl())));
    }
}
