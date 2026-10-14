package co.rsk.peg;

import static co.rsk.RskTestUtils.createRepository;
import static co.rsk.RskTestUtils.createRskBlock;
import static co.rsk.peg.BridgeSupportTestUtil.assertEventWasNotEmitted;
import static co.rsk.peg.BridgeSupportTestUtil.assertLogUtxosRegistered;
import static co.rsk.peg.BridgeSupportTestUtil.assertTransactionWasProcessed;
import static co.rsk.peg.BridgeSupportTestUtil.buildExpectedUtxosRegistered;
import static co.rsk.peg.BridgeSupportTestUtil.buildPMTAndRecreateChainForTxIdRegistration;
import static co.rsk.peg.BridgeSupportTestUtil.createValidPmtForTransactionsHashes;
import static co.rsk.peg.BridgeSupportTestUtil.recreateChainFromPmt;
import static co.rsk.peg.bitcoin.BitcoinTestAssertions.assertUtxosAreEqual;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import co.rsk.bitcoinj.core.Sha256Hash;
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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
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
    private static final int MINIMUM_ACCEPTABLE_CONFIRMATIONS = bridgeMainnetConstants.getBtc2RskMinimumAcceptableConfirmations();
    // one block below the release, so it has one confirmation more than the release block
    private static final int BTC_BLOCK_WITH_ANOTHER_PEGOUT_HEIGHT = BTC_BLOCK_WITH_RELEASE_HEIGHT - 1;
    private static final long ACTIVE_FEDERATION_CREATION_BLOCK_NUMBER = 1L;
    private static final int PENDING_UTXOS_HEIGHT = 0;

    private static final Coin PEGOUT_INPUT_VALUE = Coin.COIN;
    private static final Coin PEGOUT_OUTPOINT_VALUE = Coin.COIN.div(2);
    private static final Coin CHANGE_VALUE = Coin.COIN.div(4);

    // the pegout change output is added last, after the user output
    private static final long OUTPUT_INDEX_FOR_PEGOUT_CHANGE = 1L;
    // the svp fund tx change output is added last, after the proposed and flyover proposed federations outputs
    private static final long OUTPUT_INDEX_FOR_SVP_FUND_TX_CHANGE = 2L;
    private static final Coin SVP_FUND_TX_OUTPUTS_VALUE = Coin.COIN.div(8);

    private static final List<BtcECKey> activeFederationKeys = BitcoinTestUtils.getBtcEcKeys(20);
    private static final Address userAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "userAddress");
    private static final Address anotherUserAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "anotherUserAddress");

    private Federation activeFederation;
    private BtcTransaction pegout;
    private BtcTransaction anotherPegout;
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
        pegout = buildPegoutWithChangeFromActiveFedPayingTo(userAddress);
        anotherPegout = buildPegoutWithChangeFromActiveFedPayingTo(anotherUserAddress);

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
    void registerPegoutTransaction_withPegoutWithChangeToActiveFed_shouldRegisterChangeInActiveFed() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        // arrange
        storePendingUtxosSentToActiveFed(pegout);
        PartialMerkleTree pmt = buildPMTAndRecreateChainForTxIdRegistration(
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
        assertChangeWasRegisteredInActiveFed(pegout, OUTPUT_INDEX_FOR_PEGOUT_CHANGE);
    }

    @Test
    void registerPegoutTransaction_withNoPendingUtxosForTxId_shouldNotRegister() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        // arrange
        PartialMerkleTree pmt = buildPMTAndRecreateChainForTxIdRegistration(
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
    void registerPegoutTransaction_withTxIdAlreadyProcessed_shouldNotRegister() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        // arrange
        storePendingUtxosSentToActiveFed(pegout);
        long rskBlockNumberWherePegoutWasProcessed = rskExecutionBlock.getNumber() - 1;
        bridgeStorageProvider.setHeightBtcTxhashAlreadyProcessed(pegout.getHash(), rskBlockNumberWherePegoutWasProcessed);
        PartialMerkleTree pmt = buildPMTAndRecreateChainForTxIdRegistration(
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

    @Test
    void registerPegoutTransaction_withSvpFundTx_whileSvpIsOngoing_shouldNotRegister() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        // arrange
        BtcTransaction svpFundTx = buildSvpFundTxWithSvpOngoing();
        storePendingUtxosSentToActiveFed(svpFundTx);
        PartialMerkleTree pmt = buildPMTAndRecreateChainForTxIdRegistration(
            bridgeStorageProvider,
            bridgeMainnetConstants,
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            svpFundTx,
            btcBlockStore
        );

        // act
        bridgeSupport.registerPegoutTransaction(
            rskTx,
            svpFundTx.getHash(),
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            pmt.bitcoinSerialize()
        );
        bridgeSupport.save();

        // assert
        assertPegoutWasNotRegistered(svpFundTx);
        assertTrue(federationSupport.hasFederationsPendingBtcUTXOs(svpFundTx.getHash()));
        assertEquals(Optional.of(svpFundTx.getHash()), bridgeStorageProvider.getSvpFundTxHashUnsigned());
        assertTrue(bridgeStorageProvider.getSvpFundTxSigned().isEmpty());
    }

    @Test
    void registerPegoutTransaction_withPegoutTx_whileSvpIsOngoing_shouldRegisterChangeInActiveFed() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        // arrange
        BtcTransaction svpFundTx = buildSvpFundTxWithSvpOngoing();
        storePendingUtxosSentToActiveFed(svpFundTx);
        storePendingUtxosSentToActiveFed(pegout);
        PartialMerkleTree pmt = buildPMTAndRecreateChainForTxIdRegistration(
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
        assertChangeWasRegisteredInActiveFed(pegout, OUTPUT_INDEX_FOR_PEGOUT_CHANGE);
        assertTrue(federationSupport.hasFederationsPendingBtcUTXOs(svpFundTx.getHash()));
        assertEquals(Optional.of(svpFundTx.getHash()), bridgeStorageProvider.getSvpFundTxHashUnsigned());
    }

    @Test
    void registerPegoutTransaction_withSvpFundTx_afterSvpProcessedFinished_shouldRegisterChangeInActiveFed() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        // arrange
        BtcTransaction svpFundTx = buildSvpFundTxFromActiveFed(buildProposedFederation());
        storePendingUtxosSentToActiveFed(svpFundTx);
        PartialMerkleTree pmt = buildPMTAndRecreateChainForTxIdRegistration(
            bridgeStorageProvider,
            bridgeMainnetConstants,
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            svpFundTx,
            btcBlockStore
        );

        // act
        bridgeSupport.registerPegoutTransaction(
            rskTx,
            svpFundTx.getHash(),
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            pmt.bitcoinSerialize()
        );
        bridgeSupport.save();

        // assert
        assertChangeWasRegisteredInActiveFed(svpFundTx, OUTPUT_INDEX_FOR_SVP_FUND_TX_CHANGE);
    }

    @Test
    void registerPegoutTransaction_withNegativeHeight_shouldNotRegister() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        // arrange
        storePendingUtxosSentToActiveFed(pegout);
        PartialMerkleTree pmt = buildPMTAndRecreateChainForTxIdRegistration(
            bridgeStorageProvider,
            bridgeMainnetConstants,
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            pegout,
            btcBlockStore
        );

        // act
        int negativeHeight = -1;
        bridgeSupport.registerPegoutTransaction(
            rskTx,
            pegout.getHash(),
            negativeHeight,
            pmt.bitcoinSerialize()
        );
        bridgeSupport.save();

        // assert
        assertPegoutWasNotRegistered(pegout);
        assertTrue(federationSupport.hasFederationsPendingBtcUTXOs(pegout.getHash()));
    }

    @Test
    void registerPegoutTransaction_withNotEnoughConfirmations_shouldNotRegister() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        // arrange
        storePendingUtxosSentToActiveFed(pegout);
        PartialMerkleTree pmt = createValidPmtForTxId(pegout);
        recreateChainWithOneConfirmationLessThanAcceptable(pmt);

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
        assertTrue(federationSupport.hasFederationsPendingBtcUTXOs(pegout.getHash()));
    }

    @Test
    void registerPegoutTransaction_withHeightOfOtherBlock_shouldNotRegister() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        // arrange
        storePendingUtxosSentToActiveFed(pegout);
        PartialMerkleTree pmtForAnotherPegout = createValidPmtForTxId(anotherPegout);
        recreateChainWithAnotherPegoutBlockWithExactlyMinimumAcceptableConfirmations(pmtForAnotherPegout);
        PartialMerkleTree pmt = createValidPmtForTxId(pegout);
        recreateChainWithOneConfirmationLessThanAcceptable(pmt);

        // act
        // the older block has enough confirmations, but its merkle root is not the pegout pmt one
        bridgeSupport.registerPegoutTransaction(
            rskTx,
            pegout.getHash(),
            BTC_BLOCK_WITH_ANOTHER_PEGOUT_HEIGHT,
            pmt.bitcoinSerialize()
        );
        bridgeSupport.save();

        // assert
        assertPegoutWasNotRegistered(pegout);
        assertTrue(federationSupport.hasFederationsPendingBtcUTXOs(pegout.getHash()));
    }

    @Test
    void registerPegoutTransaction_withExactlyMinimumConfirmations_shouldRegisterChangeInActiveFed() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        // arrange
        storePendingUtxosSentToActiveFed(pegout);
        PartialMerkleTree pmt = createValidPmtForTxId(pegout);
        recreateChainWithExactlyMinimumAcceptableConfirmations(pmt);

        // act
        bridgeSupport.registerPegoutTransaction(
            rskTx,
            pegout.getHash(),
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            pmt.bitcoinSerialize()
        );
        bridgeSupport.save();

        // assert
        assertChangeWasRegisteredInActiveFed(pegout, OUTPUT_INDEX_FOR_PEGOUT_CHANGE);
    }

    @Test
    void registerPegoutTransaction_withHeightAboveBestChainHeight_shouldNotRegister() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        // arrange
        storePendingUtxosSentToActiveFed(pegout);
        PartialMerkleTree pmt = buildPMTAndRecreateChainForTxIdRegistration(
            bridgeStorageProvider,
            bridgeMainnetConstants,
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            pegout,
            btcBlockStore
        );
        int heightAboveBestChainHeight = bridgeSupport.getBtcBlockchainBestChainHeight() + 1;

        // act
        bridgeSupport.registerPegoutTransaction(
            rskTx,
            pegout.getHash(),
            heightAboveBestChainHeight,
            pmt.bitcoinSerialize()
        );
        bridgeSupport.save();

        // assert
        assertPegoutWasNotRegistered(pegout);
        assertTrue(federationSupport.hasFederationsPendingBtcUTXOs(pegout.getHash()));
    }

    @Test
    void registerPegoutTransaction_withPmtNotContainingTxId_shouldNotRegister() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        // arrange
        storePendingUtxosSentToActiveFed(pegout);
        storePendingUtxosSentToActiveFed(anotherPegout);
        PartialMerkleTree pmtForAnotherPegout = buildPMTAndRecreateChainForTxIdRegistration(
            bridgeStorageProvider,
            bridgeMainnetConstants,
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            anotherPegout,
            btcBlockStore
        );

        // act
        bridgeSupport.registerPegoutTransaction(
            rskTx,
            pegout.getHash(),
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            pmtForAnotherPegout.bitcoinSerialize()
        );
        bridgeSupport.save();

        // assert
        assertPegoutWasNotRegistered(pegout);
        assertTrue(federationSupport.hasFederationsPendingBtcUTXOs(pegout.getHash()));
    }

    @Test
    void registerPegoutTransaction_withPmtOfUnexpectedSize_shouldThrowBridgeIllegalArgumentException() throws BlockStoreException, IOException {
        // arrange
        storePendingUtxosSentToActiveFed(pegout);
        PartialMerkleTree pmt = buildPMTAndRecreateChainForTxIdRegistration(
            bridgeStorageProvider,
            bridgeMainnetConstants,
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            pegout,
            btcBlockStore
        );
        byte[] pmtSerialized = pmt.bitcoinSerialize();
        // one byte more than the size declared by the pmt hashes and flag bits counts
        byte[] pmtWithUnexpectedSize = Arrays.copyOf(pmtSerialized, pmtSerialized.length + 1);

        // act & assert
        assertThrows(BridgeIllegalArgumentException.class, () -> bridgeSupport.registerPegoutTransaction(
            rskTx,
            pegout.getHash(),
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            pmtWithUnexpectedSize
        ));

        bridgeSupport.save();
        assertPegoutWasNotRegistered(pegout);
        assertTrue(federationSupport.hasFederationsPendingBtcUTXOs(pegout.getHash()));
    }

    @Test
    void registerPegoutTransaction_withMalformedPmt_shouldThrowBridgeIllegalArgumentException() throws BlockStoreException, IOException {
        // arrange
        storePendingUtxosSentToActiveFed(pegout);
        PartialMerkleTree pmt = buildPMTAndRecreateChainForTxIdRegistration(
            bridgeStorageProvider,
            bridgeMainnetConstants,
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            pegout,
            btcBlockStore
        );
        byte[] malformedPmt = pmt.bitcoinSerialize();

        // zeroing the leading 4 bytes transaction count keeps the expected size,
        // but a pmt without transactions can't be parsed into a merkle root
        int pmtTransactionCountLength = 4;
        Arrays.fill(malformedPmt, 0, pmtTransactionCountLength, (byte) 0);

        // act & assert
        assertThrows(BridgeIllegalArgumentException.class, () -> bridgeSupport.registerPegoutTransaction(
            rskTx,
            pegout.getHash(),
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            malformedPmt
        ));

        bridgeSupport.save();
        assertPegoutWasNotRegistered(pegout);
        assertTrue(federationSupport.hasFederationsPendingBtcUTXOs(pegout.getHash()));
    }

    @Test
    void registerPegoutTransaction_withWtxid_shouldNotRegister() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        // arrange
        storePendingUtxosSentToActiveFed(pegout);
        PartialMerkleTree pmt = buildPMTAndRecreateChainForTxIdRegistration(
            bridgeStorageProvider,
            bridgeMainnetConstants,
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            pegout,
            btcBlockStore
        );
        Sha256Hash pegoutWtxid = pegout.getHash(true);

        // act
        bridgeSupport.registerPegoutTransaction(
            rskTx,
            pegoutWtxid,
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            pmt.bitcoinSerialize()
        );
        bridgeSupport.save();

        // assert
        assertPegoutWasNotRegistered(pegout);
        assertTrue(bridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(pegoutWtxid).isEmpty());
        assertTrue(federationSupport.hasFederationsPendingBtcUTXOs(pegout.getHash()));
    }

    @Test
    void registerPegoutTransaction_withHeightTooDeepToSearch_shouldThrowBlockStoreException() throws BlockStoreException, IOException {
        // arrange
        storePendingUtxosSentToActiveFed(pegout);
        PartialMerkleTree pmt = createValidPmtForTxId(pegout);
        recreateChainWithTxBlockOneBlockBelowSearchDepthLimit(pmt);

        // act & assert
        assertThrows(BlockStoreException.class, () -> bridgeSupport.registerPegoutTransaction(
            rskTx,
            pegout.getHash(),
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            pmt.bitcoinSerialize()
        ));

        bridgeSupport.save();
        assertPegoutWasNotRegistered(pegout);
        assertTrue(federationSupport.hasFederationsPendingBtcUTXOs(pegout.getHash()));
    }

    private PartialMerkleTree createValidPmtForTxId(BtcTransaction releaseTransaction) {
        return createValidPmtForTransactionsHashes(List.of(releaseTransaction.getHash()), btcMainnetParams);
    }

    private void recreateChainWithOneConfirmationLessThanAcceptable(PartialMerkleTree pmt) throws BlockStoreException {
        // confirmations are counted as bestChainHeight - height + 1
        int bestChainHeight = BTC_BLOCK_WITH_RELEASE_HEIGHT + MINIMUM_ACCEPTABLE_CONFIRMATIONS - 2;
        recreateChainFromPmt(
            btcBlockStore,
            bestChainHeight,
            pmt,
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            btcMainnetParams
        );
        bridgeStorageProvider.save();
    }

    private void recreateChainWithAnotherPegoutBlockWithExactlyMinimumAcceptableConfirmations(PartialMerkleTree pmtForAnotherPegout) throws BlockStoreException {
        // confirmations are counted as bestChainHeight - height + 1
        int bestChainHeight = BTC_BLOCK_WITH_ANOTHER_PEGOUT_HEIGHT + MINIMUM_ACCEPTABLE_CONFIRMATIONS - 1;
        recreateChainFromPmt(
            btcBlockStore,
            bestChainHeight,
            pmtForAnotherPegout,
            BTC_BLOCK_WITH_ANOTHER_PEGOUT_HEIGHT,
            btcMainnetParams
        );
        bridgeStorageProvider.save();
    }

    private void recreateChainWithExactlyMinimumAcceptableConfirmations(PartialMerkleTree pmt) throws BlockStoreException {
        // confirmations are counted as bestChainHeight - height + 1
        int bestChainHeight = BTC_BLOCK_WITH_RELEASE_HEIGHT + MINIMUM_ACCEPTABLE_CONFIRMATIONS - 1;
        recreateChainFromPmt(
            btcBlockStore,
            bestChainHeight,
            pmt,
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            btcMainnetParams
        );
        bridgeStorageProvider.save();
    }

    private void recreateChainWithTxBlockOneBlockBelowSearchDepthLimit(PartialMerkleTree pmt) throws BlockStoreException {
        // with the chain head below the block index activation height, blocks are only searched
        // down to bestChainHeight - maxDepthToSearchBlocksBelowIndexActivation
        int bestChainHeight = BTC_BLOCK_WITH_RELEASE_HEIGHT + bridgeMainnetConstants.getMaxDepthToSearchBlocksBelowIndexActivation() + 1;
        recreateChainFromPmt(
            btcBlockStore,
            bestChainHeight,
            pmt,
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            btcMainnetParams
        );
        bridgeStorageProvider.save();
    }

    private void assertChangeWasRegisteredInActiveFed(BtcTransaction releaseTransaction, long outputIndexForChange) throws IOException {
        List<UTXO> expectedUtxosRegistered = buildExpectedUtxosRegistered(
            releaseTransaction,
            activeFederation,
            BTC_BLOCK_WITH_RELEASE_HEIGHT
        );
        assertUtxosAreEqual(expectedUtxosRegistered, federationSupport.getActiveFederationBtcUTXOs());
        assertFalse(federationSupport.hasFederationsPendingBtcUTXOs(releaseTransaction.getHash()));
        assertTransactionWasProcessed(
            bridgeStorageProvider,
            releaseTransaction.getHash(),
            (int) rskExecutionBlock.getNumber()
        );
        assertLogUtxosRegistered(
            logs,
            releaseTransaction.getHash(),
            List.of(CHANGE_VALUE),
            List.of(outputIndexForChange),
            activeFederation.getAddress()
        );
    }

    private void assertPegoutWasNotRegistered(BtcTransaction release) throws IOException {
        assertTrue(federationSupport.getActiveFederationBtcUTXOs().isEmpty());
        assertTrue(bridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(release.getHash()).isEmpty());
        assertEventWasNotEmitted(logs, BridgeEvents.UTXOS_REGISTERED.getEvent());
    }

    private BtcTransaction buildPegoutWithChangeFromActiveFedPayingTo(Address user) {
        Sha256Hash spendTxHash = BitcoinTestUtils.createHash(1);
        return PegoutTransactionBuilder.builder()
            .withNetworkParameters(btcMainnetParams)
            .withActiveFederation(activeFederation)
            .withInput(spendTxHash, 0, PEGOUT_INPUT_VALUE)
            .withOutput(PEGOUT_OUTPOINT_VALUE, user)
            .withChangeAmount(CHANGE_VALUE)
            .withSignatures(activeFederationKeys)
            .build();
    }

    private BtcTransaction buildSvpFundTxWithSvpOngoing() {
        Federation proposedFederation = buildProposedFederation();
        federationStorageProvider.setProposedFederation(proposedFederation);

        BtcTransaction svpFundTx = buildSvpFundTxFromActiveFed(proposedFederation);
        bridgeStorageProvider.setSvpFundTxHashUnsigned(svpFundTx.getHash());
        return svpFundTx;
    }

    private Federation buildProposedFederation() {
        return P2shP2wshErpFederationBuilder.builder()
            .withCreationBlockNumber(rskExecutionBlock.getNumber())
            .build();
    }

    private BtcTransaction buildSvpFundTxFromActiveFed(Federation proposedFederation) {
        Address flyoverProposedFederationAddress = PegUtils.getFlyoverFederationAddress(
            btcMainnetParams,
            bridgeMainnetConstants.getProposedFederationFlyoverPrefix(),
            proposedFederation
        );

        Sha256Hash spendTxHash = BitcoinTestUtils.createHash(2);
        return PegoutTransactionBuilder.builder()
            .withNetworkParameters(btcMainnetParams)
            .withActiveFederation(activeFederation)
            .withInput(spendTxHash, 0, PEGOUT_INPUT_VALUE)
            .withOutput(SVP_FUND_TX_OUTPUTS_VALUE, proposedFederation.getAddress())
            .withOutput(SVP_FUND_TX_OUTPUTS_VALUE, flyoverProposedFederationAddress)
            .withChangeAmount(CHANGE_VALUE)
            .withSignatures(activeFederationKeys)
            .build();
    }

    private void storePendingUtxosSentToActiveFed(BtcTransaction releaseTransaction) {
        List<UTXO> pendingUtxos = buildExpectedUtxosRegistered(
            releaseTransaction,
            activeFederation,
            PENDING_UTXOS_HEIGHT
        );
        federationSupport.storeFederationsPendingBtcUTXOs(releaseTransaction.getHash(), pendingUtxos);
    }
}
