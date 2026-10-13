package co.rsk.peg;

import static co.rsk.RskTestUtils.createRepository;
import static co.rsk.RskTestUtils.createRskBlock;
import static co.rsk.peg.BridgeSupportTestUtil.assertEventWasNotEmitted;
import static co.rsk.peg.BridgeSupportTestUtil.assertLogUtxosRegistered;
import static co.rsk.peg.BridgeSupportTestUtil.assertTransactionWasProcessed;
import static co.rsk.peg.BridgeSupportTestUtil.buildExpectedUtxosRegistered;
import static co.rsk.peg.BridgeSupportTestUtil.buildPMTAndRecreateChainForSegwitTransactionRegistration;
import static co.rsk.peg.bitcoin.BitcoinTestAssertions.assertUtxosAreEqual;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.RskTestUtils;
import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.PartialMerkleTree;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationStorageProvider;
import co.rsk.peg.federation.FederationStorageProviderImpl;
import co.rsk.peg.federation.FederationSupport;
import co.rsk.peg.federation.P2shP2wshErpFederationBuilder;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.storage.InMemoryStorage;
import co.rsk.peg.utils.BridgeEventLoggerImpl;
import co.rsk.test.builders.BridgeSupportBuilder;
import co.rsk.test.builders.FederationSupportBuilder;
import co.rsk.test.builders.PegoutTransactionBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.vm.LogInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BridgeSupportRegisterPegoutTransactionTest {
    private static final ActivationConfig.ForBlock allActivations = ActivationConfigsForTest.all().forBlock(0);
    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final FederationConstants federationMainnetConstants = bridgeMainnetConstants.getFederationConstants();
    private static final NetworkParameters btcMainnetParams = bridgeMainnetConstants.getBtcParams();

    private static final int BTC_BLOCK_WITH_RELEASE_HEIGHT = 1_000;
    private static final long ACTIVE_FEDERATION_CREATION_BLOCK_NUMBER = 1L;
    private static final int PENDING_UTXOS_HEIGHT = 0;

    private static final Coin RELEASE_INPUT_VALUE = Coin.COIN;
    private static final Coin PEGOUT_VALUE = Coin.COIN.div(2);
    private static final Coin CHANGE_VALUE = Coin.COIN.div(4);

    // the pegout change output is added last, after the user output
    private static final long OUTPUT_INDEX_FOR_PEGOUT_CHANGE = 1L;
    private static final Coin SVP_FUND_TX_OUTPUTS_VALUE = Coin.COIN.div(8);

    private static final List<BtcECKey> activeFederationKeys = BitcoinTestUtils.getBtcEcKeys(20);
    private static final Address userAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "userAddress");

    private Federation activeFederation;
    private BtcTransaction pegout;
    private BridgeStorageProvider bridgeStorageProvider;
    private FederationStorageProvider federationStorageProvider;
    private FederationSupport federationSupport;
    private BtcBlockStoreWithCache btcBlockStore;
    private BridgeSupport bridgeSupport;
    private Block rskExecutionBlock;
    private Transaction rskTx;
    private List<LogInfo> logs;

    @BeforeEach
    void setUp() {
        activeFederation = P2shP2wshErpFederationBuilder.builder()
            .withMembersBtcPublicKeys(activeFederationKeys)
            .withCreationBlockNumber(ACTIVE_FEDERATION_CREATION_BLOCK_NUMBER)
            .build();
        pegout = buildPegoutWithChangeFromActiveFed();

        Repository repository = createRepository();
        logs = new ArrayList<>();

        federationStorageProvider = new FederationStorageProviderImpl(new InMemoryStorage());
        federationStorageProvider.setNewFederation(activeFederation);

        bridgeStorageProvider = new BridgeStorageProvider(repository, btcMainnetParams, allActivations);

        // Move the required blocks ahead for the active federation to be active
        long rskExecutionBlockNumber = activeFederation.getCreationBlockNumber()
            + federationMainnetConstants.getFederationActivationAge(allActivations);
        rskExecutionBlock = createRskBlock(rskExecutionBlockNumber);

        federationSupport = FederationSupportBuilder.builder()
            .withFederationConstants(federationMainnetConstants)
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(rskExecutionBlock)
            .withActivations(allActivations)
            .build();

        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(btcMainnetParams, 100, 100);
        btcBlockStore = btcBlockStoreFactory.newInstance(repository, bridgeMainnetConstants, bridgeStorageProvider, allActivations);

        bridgeSupport = BridgeSupportBuilder.builder()
            .withActivations(allActivations)
            .withExecutionBlock(rskExecutionBlock)
            .withBridgeConstants(bridgeMainnetConstants)
            .withProvider(bridgeStorageProvider)
            .withRepository(repository)
            .withEventLogger(new BridgeEventLoggerImpl(bridgeMainnetConstants, allActivations, logs))
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .withFederationSupport(federationSupport)
            .build();

        rskTx = mock(Transaction.class);
        when(rskTx.getHash()).thenReturn(RskTestUtils.createHash(1));
    }

    @Test
    void registerPegoutTransaction_withPegoutHasChangeToActiveFed_shouldRegisterChangeInActiveFed() throws BlockStoreException, IOException {
        // arrange
        storePendingUtxosSentToActiveFed(pegout);
        PartialMerkleTree pmt = buildPMTAndRecreateChainForSegwitTransactionRegistration(
            bridgeStorageProvider,
            bridgeMainnetConstants,
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            pegout,
            btcBlockStore
        );

        // act
        bridgeSupport.registerPegoutTransaction(
            rskTx,
            pegout.getHash(),
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            pmt.bitcoinSerialize()
        );
        bridgeSupport.save();

        // assert
        List<UTXO> expectedUtxosRegistered = buildExpectedUtxosRegistered(
            pegout,
            activeFederation,
            BTC_BLOCK_WITH_RELEASE_HEIGHT
        );
        assertUtxosAreEqual(expectedUtxosRegistered, federationSupport.getActiveFederationBtcUTXOs());
        assertFalse(federationSupport.hasFederationsPendingBtcUTXOs(pegout.getHash()));
        assertTransactionWasProcessed(
            bridgeStorageProvider,
            pegout.getHash(),
            (int) rskExecutionBlock.getNumber()
        );
        assertLogUtxosRegistered(
            logs,
            pegout.getHash(),
            List.of(CHANGE_VALUE),
            List.of(OUTPUT_INDEX_FOR_PEGOUT_CHANGE),
            activeFederation.getAddress()
        );
    }

    @Test
    void registerPegoutTransaction_withNoPendingUtxosForTxId_shouldNotRegister() throws BlockStoreException, IOException {
        // arrange
        PartialMerkleTree pmt = buildPMTAndRecreateChainForSegwitTransactionRegistration(
            bridgeStorageProvider,
            bridgeMainnetConstants,
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            pegout,
            btcBlockStore
        );

        // act
        bridgeSupport.registerPegoutTransaction(
            rskTx,
            pegout.getHash(),
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            pmt.bitcoinSerialize()
        );
        bridgeSupport.save();

        // assert
        assertPegoutWasNotRegistered(pegout);
    }

    @Test
    void registerPegoutTransaction_withTxIdAlreadyProcessed_shouldNotRegister() throws BlockStoreException, IOException {
        // arrange
        storePendingUtxosSentToActiveFed(pegout);
        long rskBlockNumberWherePegoutWasProcessed = rskExecutionBlock.getNumber() - 1;
        bridgeStorageProvider.setHeightBtcTxhashAlreadyProcessed(pegout.getHash(), rskBlockNumberWherePegoutWasProcessed);
        PartialMerkleTree pmt = buildPMTAndRecreateChainForSegwitTransactionRegistration(
            bridgeStorageProvider,
            bridgeMainnetConstants,
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            pegout,
            btcBlockStore
        );

        // act
        bridgeSupport.registerPegoutTransaction(
            rskTx,
            pegout.getHash(),
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            pmt.bitcoinSerialize()
        );
        bridgeSupport.save();

        // assert
        assertTrue(federationSupport.getActiveFederationBtcUTXOs().isEmpty());
        assertTrue(federationSupport.hasFederationsPendingBtcUTXOs(pegout.getHash()));
        assertTransactionWasProcessed(
            bridgeStorageProvider,
            pegout.getHash(),
            (int) rskBlockNumberWherePegoutWasProcessed
        );
        assertEventWasNotEmitted(logs, BridgeEvents.UTXOS_REGISTERED.getEvent());
    }

    private void assertPegoutWasNotRegistered(BtcTransaction release) throws IOException {
        assertTrue(federationSupport.getActiveFederationBtcUTXOs().isEmpty());
        assertTrue(bridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(release.getHash()).isEmpty());
        assertEventWasNotEmitted(logs, BridgeEvents.UTXOS_REGISTERED.getEvent());
    }

    private BtcTransaction buildPegoutWithChangeFromActiveFed() {
        return PegoutTransactionBuilder.builder()
            .withNetworkParameters(btcMainnetParams)
            .withActiveFederation(activeFederation)
            .withInput(BitcoinTestUtils.createHash(1), 0, RELEASE_INPUT_VALUE)
            .withOutput(PEGOUT_VALUE, userAddress)
            .withChangeAmount(CHANGE_VALUE)
            .withSignatures(activeFederationKeys)
            .build();
    }

    private void storePendingUtxosSentToActiveFed(BtcTransaction release) {
        List<UTXO> pendingUtxos = buildExpectedUtxosRegistered(
            release,
            activeFederation,
            PENDING_UTXOS_HEIGHT
        );
        federationSupport.storeFederationsPendingBtcUTXOs(release.getHash(), pendingUtxos);
    }
}
