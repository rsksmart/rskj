package co.rsk.peg;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.peg.constants.BridgeRegTestConstants;
import co.rsk.config.RskSystemProperties;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.core.genesis.TestGenesisLoader;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.*;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static co.rsk.peg.federation.FederationTestUtils.REGTEST_FEDERATION_PRIVATE_KEYS;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP124;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP132;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class BridgeCostsTest {
    private static BridgeRegTestConstants bridgeConstants;
    private TestSystemProperties config = new TestSystemProperties();
    private Constants constants;
    private ActivationConfig activationConfig;
    private BridgeSupportFactory bridgeSupportFactory;
    private SignatureCache signatureCache;

    @BeforeAll
     static void setUpBeforeClass() {
        bridgeConstants = new BridgeRegTestConstants();
    }

    @BeforeEach
    void resetConfigToRegTest() {
        config = spy(new TestSystemProperties());
        constants = Constants.regtest();
        when(config.getNetworkConstants()).thenReturn(constants);
        activationConfig = spy(ActivationConfigsForTest.genesis());
        when(config.getActivationConfig()).thenReturn(activationConfig);
        signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        bridgeSupportFactory = new BridgeSupportFactory(
            new RepositoryBtcBlockStoreWithCache.Factory(constants.getBridgeConstants().getBtcParams()),
            constants.getBridgeConstants(),
            activationConfig,
            signatureCache
        );
    }

    @Test
    void receiveHeadersGasCost_beforeDynamicCost() {
        doReturn(false).when(activationConfig).isActive(eq(RSKIP124), anyLong());

        Transaction txMock = mock(Transaction.class);
        when(txMock.getReceiveAddress()).thenReturn(RskAddress.nullAddress());
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig, bridgeSupportFactory, signatureCache);
        bridge.init(txMock, getGenesisBlock(), null, null, null, null);

        for (int numberOfHeaders = 0; numberOfHeaders < 10; numberOfHeaders++) {
            byte[][] headers = new byte[numberOfHeaders][];
            for (int i = 0; i < numberOfHeaders; i++) headers[i] = Hex.decode("00112233445566778899");

            byte[] data = BridgeMethods.RECEIVE_HEADERS.getFunction().encode(new Object[]{ headers });

            Assertions.assertEquals(22000L + 2 * data.length, bridge.getGasForData(data));
        }
    }

    @Test
    void receiveHeadersGasCost_afterDynamicCost() {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP124), anyLong());

        Transaction txMock = mock(Transaction.class);
        when(txMock.getReceiveAddress()).thenReturn(RskAddress.nullAddress());
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig, bridgeSupportFactory, signatureCache);
        bridge.init(txMock, getGenesisBlock(), null, null, null, null);

        final long BASE_COST = 66_000L;
        for (int numberOfHeaders = 0; numberOfHeaders < 10; numberOfHeaders++) {
            byte[][] headers = new byte[numberOfHeaders][];
            for (int i = 0; i < numberOfHeaders; i++) headers[i] = Hex.decode("00112233445566778899");

            byte[] data = BridgeMethods.RECEIVE_HEADERS.getFunction().encode(new Object[]{ headers });

            long cost = BASE_COST + 2 * data.length;
            if (numberOfHeaders > 1) {
                cost += 1650L * (numberOfHeaders - 1);
            }
            Assertions.assertEquals(cost, bridge.getGasForData(data));
        }
    }

    @Test
    void receiveHeadersGasCost_afterDynamicCost_review() {
        doReturn(true).when(activationConfig).isActive(eq(RSKIP124), anyLong());
        doReturn(true).when(activationConfig).isActive(eq(RSKIP132), anyLong());

        Transaction txMock = mock(Transaction.class);
        when(txMock.getReceiveAddress()).thenReturn(RskAddress.nullAddress());
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig, bridgeSupportFactory, signatureCache);
        bridge.init(txMock, getGenesisBlock(), null, null, null, null);

        final long BASE_COST = 25_000L;
        for (int numberOfHeaders = 0; numberOfHeaders < 10; numberOfHeaders++) {
            byte[][] headers = new byte[numberOfHeaders][];
            for (int i = 0; i < numberOfHeaders; i++) headers[i] = Hex.decode("00112233445566778899");

            byte[] data = BridgeMethods.RECEIVE_HEADERS.getFunction().encode(new Object[]{ headers });

            long cost = BASE_COST + 2 * data.length;
            if (numberOfHeaders > 1) {
                cost += 3500L * (numberOfHeaders - 1);
            }
            Assertions.assertEquals(cost, bridge.getGasForData(data));
        }
    }

    @Test
    void getGasForDataFreeTx() {
        activationConfig = ActivationConfigsForTest.bridgeUnitTest();

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig,
                signatureCache);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig, bridgeSupportFactory, signatureCache);

        org.ethereum.core.Transaction rskTx = CallTransaction.createCallTransaction(
                0,
                1,
                1,
                PrecompiledContracts.BRIDGE_ADDR,
                0,
                Bridge.UPDATE_COLLECTIONS,
                Constants.REGTEST_CHAIN_ID);
        rskTx.sign(REGTEST_FEDERATION_PRIVATE_KEYS.get(0).getPrivKeyBytes());

        Block rskExecutionBlock = new BlockGenerator().createChildBlock(getGenesisInstance(config));

        Repository mockRepository = mock(Repository.class);
        when(mockRepository.getCode(any(RskAddress.class))).thenReturn(null);

        bridge.init(rskTx, rskExecutionBlock, mockRepository, null, null, null);
        Assertions.assertEquals(0, bridge.getGasForData(rskTx.getData()));
    }

    @Test
    void getGasForDataInvalidFunction() {
        getGasForDataPaidTx(23000, null);
    }

    @Test
    void getGasForDataUpdateCollections() {
        getGasForDataPaidTx(48000 + 8, Bridge.UPDATE_COLLECTIONS);
    }

    @Test
    void getGasForDataReceiveHeaders() {
        getGasForDataPaidTx(22000 + 8, Bridge.RECEIVE_HEADERS);
    }

    @Test
    void getGasForDataRegisterBtcTransaction() {
        getGasForDataPaidTx(22000 + 228*2, Bridge.REGISTER_BTC_TRANSACTION, new byte[3], 1, new byte[3]);
    }

    @Test
    void getGasForDataReleaseBtc() {
        getGasForDataPaidTx(23000 + 8, Bridge.RELEASE_BTC);
    }

    @Test
    void getGasForDataAddSignature() {
        getGasForDataPaidTx(70000 + 548*2, Bridge.ADD_SIGNATURE, new byte[3], new byte[3][2], new byte[3]);
    }
    @Test
    void getGasForDataGSFBRC() {
        getGasForDataPaidTx(4000 + 8, Bridge.GET_STATE_FOR_BTC_RELEASE_CLIENT);
    }

    @Test
    void getGasForDataGSFD() {
        getGasForDataPaidTx(3_000_000 + 8, Bridge.GET_STATE_FOR_DEBUGGING);
    }

    @Test
    void getGasForDataGBBBCH() {
        getGasForDataPaidTx(19000 + 8, Bridge.GET_BTC_BLOCKCHAIN_BEST_CHAIN_HEIGHT);
    }

    @Test
    void getGasForDataGBBBL() {
        getGasForDataPaidTx(76000 + 8, Bridge.GET_BTC_BLOCKCHAIN_BLOCK_LOCATOR);
    }

    @Test
    void getGasForDataGetFederationAddress() {
        getGasForDataPaidTx(11000 + 8, Bridge.GET_FEDERATION_ADDRESS);
    }

    @Test
    void getGasForDataGetMinimumLockTxValue() {
        getGasForDataPaidTx(2000 + 8, Bridge.GET_MINIMUM_LOCK_TX_VALUE);
    }

    private void getGasForDataPaidTx(int expected, CallTransaction.Function function, Object... funcArgs) {
        activationConfig = ActivationConfigsForTest.bridgeUnitTest();

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams()),
                bridgeConstants,
                activationConfig,
                signatureCache);
        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig, bridgeSupportFactory, signatureCache);
        org.ethereum.core.Transaction rskTx;
        if (function==null) {
            rskTx = CallTransaction.createRawTransaction(
                    0,
                    1,
                    1,
                    PrecompiledContracts.BRIDGE_ADDR,
                    0,
                    new byte[]{1,2,3},
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

        rskTx.sign(REGTEST_FEDERATION_PRIVATE_KEYS.get(0).getPrivKeyBytes());

        BlockGenerator blockGenerator = new BlockGenerator();
        Block rskExecutionBlock = blockGenerator.createChildBlock(getGenesisInstance(config));
        for (int i = 0; i < 20; i++) {
            rskExecutionBlock = blockGenerator.createChildBlock(rskExecutionBlock);
        }

        Repository mockRepository = mock(Repository.class);
        when(mockRepository.getCode(any(RskAddress.class))).thenReturn(null);

        bridge.init(rskTx, rskExecutionBlock, mockRepository, null, null, null);
        Assertions.assertEquals(expected, bridge.getGasForData(rskTx.getData()));
    }

    private Block getGenesisBlock() {
        return new BlockGenerator().getGenesisBlock();
    }

    public static Genesis getGenesisInstance(RskSystemProperties config) {
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        return new TestGenesisLoader(trieStore, config.genesisInfo(), config.getNetworkConstants().getInitialNonce(), false, false, false).load();
    }
}
