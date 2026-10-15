package co.rsk.peg;

import static co.rsk.RskTestUtils.createRepository;
import static co.rsk.RskTestUtils.createRskBlock;
import static co.rsk.peg.BridgeSupportTestUtil.addPegoutRequestsToQueue;
import static co.rsk.peg.BridgeSupportTestUtil.assertEventWasNotEmitted;
import static co.rsk.peg.BridgeSupportTestUtil.assertLogUtxosRegistered;
import static co.rsk.peg.BridgeSupportTestUtil.assertTransactionWasProcessed;
import static co.rsk.peg.BridgeSupportTestUtil.buildExpectedUtxosRegistered;
import static co.rsk.peg.BridgeSupportTestUtil.buildPMTAndRecreateChainForTxIdRegistration;
import static co.rsk.peg.BridgeSupportTestUtil.buildUpdateCollectionsTransaction;
import static co.rsk.peg.BridgeSupportTestUtil.createValidPmtForTransactionsHashes;
import static co.rsk.peg.BridgeSupportTestUtil.getReleaseFromPegoutsWFC;
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
import co.rsk.bitcoinj.core.TransactionOutput;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationStorageProvider;
import co.rsk.peg.federation.FederationStorageProviderImpl;
import co.rsk.peg.federation.FederationSupport;
import co.rsk.peg.federation.P2shP2wshErpFederationBuilder;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.feeperkb.FeePerKbStorageProviderImpl;
import co.rsk.peg.feeperkb.FeePerKbSupport;
import co.rsk.peg.feeperkb.FeePerKbSupportImpl;
import co.rsk.peg.storage.InMemoryStorage;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.utils.BridgeEventLoggerImpl;
import co.rsk.test.builders.BridgeSupportBuilder;
import co.rsk.test.builders.FederationSupportBuilder;
import co.rsk.test.builders.PegoutTransactionBuilder;
import co.rsk.test.builders.UTXOBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
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
    private static final ActivationConfig.ForBlock vetiverActivations = ActivationConfigsForTest.vetiver900().forBlock(0);
    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final FederationConstants federationMainnetConstants = bridgeMainnetConstants.getFederationConstants();
    private static final NetworkParameters btcMainnetParams = bridgeMainnetConstants.getBtcParams();

    private static final int BTC_BLOCK_WITH_RELEASE_HEIGHT = 1_000;
    private static final int MINIMUM_ACCEPTABLE_CONFIRMATIONS = bridgeMainnetConstants.getBtc2RskMinimumAcceptableConfirmations();
    // one block below the release, so it has one confirmation more than the release block
    private static final int BTC_BLOCK_WITH_ANOTHER_PEGOUT_HEIGHT = BTC_BLOCK_WITH_RELEASE_HEIGHT - 1;
    private static final long ACTIVE_FEDERATION_CREATION_BLOCK_NUMBER = 1L;
    // the active federation must be active for the pegout to be created
    private static final long PEGOUT_CREATION_BLOCK_NUMBER = ACTIVE_FEDERATION_CREATION_BLOCK_NUMBER
        + federationMainnetConstants.getFederationActivationAge(allActivations);
    private static final long PEGOUT_REGISTRATION_BLOCK_NUMBER = PEGOUT_CREATION_BLOCK_NUMBER + 1;
    private static final long SVP_FUND_TX_CREATION_BLOCK_NUMBER = PEGOUT_REGISTRATION_BLOCK_NUMBER + 1;
    private static final long SVP_ONGOING_BLOCK_NUMBER = SVP_FUND_TX_CREATION_BLOCK_NUMBER + 1;
    private static final long SVP_SPEND_TX_REGISTRATION_BLOCK_NUMBER = SVP_ONGOING_BLOCK_NUMBER + 1;
    // the proposed federation is created along with the svp fund tx, so the svp ends one validation period later
    private static final long SVP_VALIDATION_PERIOD_END_BLOCK_NUMBER = SVP_FUND_TX_CREATION_BLOCK_NUMBER
        + federationMainnetConstants.getValidationPeriodDurationInBlocks();
    private static final long MIGRATION_CREATION_BLOCK_NUMBER = PEGOUT_CREATION_BLOCK_NUMBER
        + federationMainnetConstants.getFundsMigrationAgeSinceActivationBegin() + 1;
    private static final long MIGRATION_REGISTRATION_BLOCK_NUMBER = MIGRATION_CREATION_BLOCK_NUMBER + 1;
    private static final int FIXED_VALUE_MIGRATION_OUTPUTS = 3;
    private static final Coin FIXED_VALUE_MIGRATION_RETIRING_FEDERATION_BALANCE = bridgeMainnetConstants.getMigrationValueForMultipleOutputs()
        .multiply(FIXED_VALUE_MIGRATION_OUTPUTS);
    private static final int MAX_OUTPUTS_PER_MIGRATION_TRANSACTION = bridgeMainnetConstants.getMaxOutputsPerMigrationTransaction();
    private static final Coin EVENLY_DISTRIBUTED_MIGRATION_RETIRING_FEDERATION_BALANCE = bridgeMainnetConstants.getMigrationValueForMultipleOutputs()
        .multiply(MAX_OUTPUTS_PER_MIGRATION_TRANSACTION);
    private static final int MAXIMUM_MIGRATION_INPUTS = bridgeMainnetConstants.getMaxInputsPerMigrationTransaction(allActivations);
    private static final Coin EVENLY_DISTRIBUTED_MIGRATION_RETIRING_FEDERATION_UTXO_VALUE = EVENLY_DISTRIBUTED_MIGRATION_RETIRING_FEDERATION_BALANCE
        .divide(MAXIMUM_MIGRATION_INPUTS)
        .add(Coin.SATOSHI);

    private static final Coin INPUT_VALUE = Coin.COIN;
    private static final Coin PEGOUT_OUTPOINT_VALUE = Coin.COIN.div(2);
    private static final Coin CHANGE_VALUE = Coin.COIN.div(4);
    // the pegout recipients pay the fees, so the change is the input value minus the requested value
    private static final Coin PEGOUT_REQUEST_VALUE = INPUT_VALUE.subtract(CHANGE_VALUE);
    private static final Keccak256 PEGOUT_REQUEST_RSK_TX_HASH = RskTestUtils.createHash(2);
    private static final Coin CHANGE_BELOW_MINIMUM_PEGIN_VALUE = bridgeMainnetConstants.getMinimumPeginTxValue(allActivations)
        .subtract(Coin.SATOSHI);
    private static final int MAXIMUM_PEGOUT_INPUTS = 180;
    private static final int MAXIMUM_PEGOUT_REQUESTS = 57;
    // requesting all the inputs value but the change forces every utxo to be spent; the division remainder adds to the change
    private static final Coin MAXIMUM_PEGOUT_REQUEST_VALUE = INPUT_VALUE.multiply(MAXIMUM_PEGOUT_INPUTS)
        .subtract(CHANGE_VALUE)
        .divide(MAXIMUM_PEGOUT_REQUESTS);

    // the pegout change output is added last, after the user output
    private static final long OUTPUT_INDEX_FOR_PEGOUT_CHANGE = 1L;
    // the svp fund tx change output is added last, after the proposed and flyover proposed federations outputs
    private static final long OUTPUT_INDEX_FOR_SVP_FUND_TX_CHANGE = 2L;

    private static final List<BtcECKey> activeFederationKeys = BitcoinTestUtils.getBtcEcKeys(20);
    private static final Federation activeFederation = P2shP2wshErpFederationBuilder.builder()
        .withMembersBtcPublicKeys(activeFederationKeys)
        .withCreationBlockNumber(ACTIVE_FEDERATION_CREATION_BLOCK_NUMBER)
        .build();
    private static final Federation retiringFederation = P2shP2wshErpFederationBuilder.builder()
        .withCreationBlockNumber(0L)
        .build();
    private static final Address userAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "userAddress");
    private static final Address anotherUserAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "anotherUserAddress");
    private static final BtcTransaction anotherPegout = buildPegoutWithChangeFromActiveFedPayingTo(anotherUserAddress);
    private static final BtcTransaction pegin = buildPeginFromUserToActiveFed();
    private static final Keccak256 FLYOVER_DERIVATION_HASH = RskTestUtils.createHash(3);
    private static final BtcTransaction flyoverPegin = buildFlyoverPeginFromUserToActiveFed();

    private Repository repository;
    private BridgeStorageProvider bridgeStorageProvider;
    private FederationStorageProvider federationStorageProvider;
    private FederationSupport federationSupport;
    private FeePerKbSupport feePerKbSupport;
    private BtcBlockStoreWithCache.Factory btcBlockStoreFactory;
    private BtcBlockStoreWithCache btcBlockStore;
    private BridgeSupport bridgeSupport;
    private Block rskExecutionBlock;
    private Transaction rskTx;
    private List<LogInfo> logs;

    @BeforeEach
    void setUp() {
        repository = createRepository();
        logs = new ArrayList<>();

        StorageAccessor bridgeStorageAccessor = new InMemoryStorage();
        federationStorageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);
        federationStorageProvider.setNewFederation(activeFederation);
        feePerKbSupport = new FeePerKbSupportImpl(
            bridgeMainnetConstants.getFeePerKbConstants(),
            new FeePerKbStorageProviderImpl(bridgeStorageAccessor)
        );

        bridgeStorageProvider = new BridgeStorageProvider(repository, btcMainnetParams, allActivations);

        btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(btcMainnetParams, 100, 100);
        btcBlockStore = btcBlockStoreFactory.newInstance(repository, bridgeMainnetConstants, bridgeStorageProvider, allActivations);

        rskTx = mock(Transaction.class);
        when(rskTx.getHash()).thenReturn(RskTestUtils.createHash(1));
    }

    @Test
    void registerPegoutTransaction_withPegoutWithChangeToActiveFed_shouldRegisterChangeInActiveFed() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        // arrange
        BtcTransaction pegout = setupPegoutFromActiveFedPayingToUser();
        setUpBridgeAndFederationSupportForExecutionBlock(PEGOUT_REGISTRATION_BLOCK_NUMBER);
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
        setUpBridgeAndFederationSupportForExecutionBlock(PEGOUT_REGISTRATION_BLOCK_NUMBER);
        // another pegout was not created by the bridge, so it has no pending utxos
        PartialMerkleTree pmt = buildPMTAndRecreateChainForTxIdRegistration(
            bridgeStorageProvider,
            bridgeMainnetConstants,
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            anotherPegout,
            btcBlockStore
        );

        // act
        bridgeSupport.registerPegoutTransaction(
            rskTx,
            anotherPegout.getHash(),
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            pmt.bitcoinSerialize()
        );
        bridgeSupport.save();

        // assert
        assertPegoutWasNotRegistered(anotherPegout);
    }

    @Test
    void registerPegoutTransaction_withPegin_shouldNotRegister() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        // arrange
        setUpBridgeAndFederationSupportForExecutionBlock(PEGOUT_REGISTRATION_BLOCK_NUMBER);
        PartialMerkleTree pmt = buildPMTAndRecreateChainForTxIdRegistration(
            bridgeStorageProvider,
            bridgeMainnetConstants,
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            pegin,
            btcBlockStore
        );

        // act
        bridgeSupport.registerPegoutTransaction(
            rskTx,
            pegin.getHash(),
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            pmt.bitcoinSerialize()
        );
        bridgeSupport.save();

        // assert
        assertPegoutWasNotRegistered(pegin);
        assertFalse(federationSupport.hasFederationsPendingBtcUTXOs(pegin.getHash()));
    }

    @Test
    void registerPegoutTransaction_withFlyoverPegin_shouldNotRegister() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        // arrange
        setUpBridgeAndFederationSupportForExecutionBlock(PEGOUT_REGISTRATION_BLOCK_NUMBER);
        PartialMerkleTree pmt = buildPMTAndRecreateChainForTxIdRegistration(
            bridgeStorageProvider,
            bridgeMainnetConstants,
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            flyoverPegin,
            btcBlockStore
        );

        // act
        bridgeSupport.registerPegoutTransaction(
            rskTx,
            flyoverPegin.getHash(),
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            pmt.bitcoinSerialize()
        );
        bridgeSupport.save();

        // assert
        assertPegoutWasNotRegistered(flyoverPegin);
        assertFalse(federationSupport.hasFederationsPendingBtcUTXOs(flyoverPegin.getHash()));
    }

    @Test
    void registerPegoutTransaction_withPegoutCreatedForVetiver_shouldNotRegister() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        // arrange
        // before RSKIP643 the pegout sighash is saved instead of its change as federations pending utxos
        BtcTransaction pegout = setupPegoutForVetiverFromActiveFedPayingToUser();
        setUpBridgeAndFederationSupportForExecutionBlock(PEGOUT_REGISTRATION_BLOCK_NUMBER);
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
        assertFalse(federationSupport.hasFederationsPendingBtcUTXOs(pegout.getHash()));
    }

    @Test
    void registerPegoutTransaction_withPegoutWithoutChange_shouldNotRegister() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        // arrange
        // with no output sent to the live federations, no federations pending utxos are saved
        BtcTransaction pegout = setupPegoutWithoutChangeFromActiveFedPayingToUser();
        setUpBridgeAndFederationSupportForExecutionBlock(PEGOUT_REGISTRATION_BLOCK_NUMBER);
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
        assertFalse(federationSupport.hasFederationsPendingBtcUTXOs(pegout.getHash()));
    }

    @Test
    void registerPegoutTransaction_withPegoutWithMaximumInputsAndOutputs_shouldRegisterChangeInActiveFed() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        // arrange
        BtcTransaction pegout = setupPegoutWithMaximumInputsAndOutputsFromActiveFed();
        setUpBridgeAndFederationSupportForExecutionBlock(PEGOUT_REGISTRATION_BLOCK_NUMBER);
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
        assertEquals(MAXIMUM_PEGOUT_INPUTS, pegout.getInputs().size());

        int actualOutputsWithoutChange = pegout.getOutputs().size() - 1;
        assertEquals(MAXIMUM_PEGOUT_REQUESTS, actualOutputsWithoutChange);
        assertChangeWasRegisteredInActiveFed(pegout, MAXIMUM_PEGOUT_REQUESTS);
    }

    @Test
    void registerPegoutTransaction_withPegoutWithChangeBelowMinimumPeginValue_shouldRegisterChangeInActiveFed() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        // arrange
        BtcTransaction pegout = setupPegoutWithChangeBelowMinimumPeginValueFromActiveFedPayingToUser();
        setUpBridgeAndFederationSupportForExecutionBlock(PEGOUT_REGISTRATION_BLOCK_NUMBER);
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
        assertEquals(CHANGE_BELOW_MINIMUM_PEGIN_VALUE, pegout.getOutput(OUTPUT_INDEX_FOR_PEGOUT_CHANGE).getValue());
        assertChangeWasRegisteredInActiveFed(pegout, OUTPUT_INDEX_FOR_PEGOUT_CHANGE);
    }

    @Test
    void registerPegoutTransaction_withMigrationWithOneOutput_shouldRegisterMigratedUtxosInActiveFed() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        // arrange
        BtcTransaction migrationTransaction = setupMigrationFromRetiringFedToActiveFed(INPUT_VALUE);
        setUpBridgeAndFederationSupportForExecutionBlock(MIGRATION_REGISTRATION_BLOCK_NUMBER);
        PartialMerkleTree pmt = buildPMTAndRecreateChainForTxIdRegistration(
            bridgeStorageProvider,
            bridgeMainnetConstants,
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            migrationTransaction,
            btcBlockStore
        );

        // act
        bridgeSupport.registerPegoutTransaction(
            rskTx,
            migrationTransaction.getHash(),
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            pmt.bitcoinSerialize()
        );
        bridgeSupport.save();

        // assert
        assertEquals(1, migrationTransaction.getOutputs().size());
        assertMigrationWasRegisteredInActiveFed(migrationTransaction);
    }

    @Test
    void registerPegoutTransaction_withMigrationWithFixedValueOutputs_shouldRegisterMigratedUtxosInActiveFed() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        // arrange
        BtcTransaction migrationTransaction = setupMigrationFromRetiringFedToActiveFed(FIXED_VALUE_MIGRATION_RETIRING_FEDERATION_BALANCE);
        setUpBridgeAndFederationSupportForExecutionBlock(MIGRATION_REGISTRATION_BLOCK_NUMBER);
        PartialMerkleTree pmt = buildPMTAndRecreateChainForTxIdRegistration(
            bridgeStorageProvider,
            bridgeMainnetConstants,
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            migrationTransaction,
            btcBlockStore
        );

        // act
        bridgeSupport.registerPegoutTransaction(
            rskTx,
            migrationTransaction.getHash(),
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            pmt.bitcoinSerialize()
        );
        bridgeSupport.save();

        // assert
        assertEquals(FIXED_VALUE_MIGRATION_OUTPUTS, migrationTransaction.getOutputs().size());
        assertMigrationWasRegisteredInActiveFed(migrationTransaction);
    }

    @Test
    void registerPegoutTransaction_withMigrationWithEvenlyDistributedOutputs_withMaximumInputAndOutputs_shouldRegisterMigratedUtxosInActiveFed() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        // arrange
        BtcTransaction migrationTransaction = setupMigrationWithMaximumInputsFromRetiringFedToActiveFed(
            EVENLY_DISTRIBUTED_MIGRATION_RETIRING_FEDERATION_UTXO_VALUE
        );
        setUpBridgeAndFederationSupportForExecutionBlock(MIGRATION_REGISTRATION_BLOCK_NUMBER);
        PartialMerkleTree pmt = buildPMTAndRecreateChainForTxIdRegistration(
            bridgeStorageProvider,
            bridgeMainnetConstants,
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            migrationTransaction,
            btcBlockStore
        );

        // act
        bridgeSupport.registerPegoutTransaction(
            rskTx,
            migrationTransaction.getHash(),
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            pmt.bitcoinSerialize()
        );
        bridgeSupport.save();

        // assert
        assertEquals(MAXIMUM_MIGRATION_INPUTS, migrationTransaction.getInputs().size());
        assertEquals(MAX_OUTPUTS_PER_MIGRATION_TRANSACTION, migrationTransaction.getOutputs().size());
        assertMigrationWasRegisteredInActiveFed(migrationTransaction);
    }

    @Test
    void registerPegoutTransaction_withTxIdAlreadyProcessed_shouldNotRegister() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        // arrange
        BtcTransaction pegout = setupPegoutFromActiveFedPayingToUser();
        setUpBridgeAndFederationSupportForExecutionBlock(PEGOUT_REGISTRATION_BLOCK_NUMBER);
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
        BtcTransaction svpFundTx = setupSvpFundTxFromActiveFed();
        setUpBridgeAndFederationSupportForExecutionBlock(SVP_ONGOING_BLOCK_NUMBER);
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
    void registerPegoutTransaction_withSvpSpendTx_whileSvpIsOngoing_shouldNotRegister() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        // arrange
        BtcTransaction svpSpendTx = setupSvpSpendTxFromProposedFed();
        setUpBridgeAndFederationSupportForExecutionBlock(SVP_SPEND_TX_REGISTRATION_BLOCK_NUMBER);
        PartialMerkleTree pmt = buildPMTAndRecreateChainForTxIdRegistration(
            bridgeStorageProvider,
            bridgeMainnetConstants,
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            svpSpendTx,
            btcBlockStore
        );

        // act
        bridgeSupport.registerPegoutTransaction(
            rskTx,
            svpSpendTx.getHash(),
            BTC_BLOCK_WITH_RELEASE_HEIGHT,
            pmt.bitcoinSerialize()
        );
        bridgeSupport.save();

        // assert
        assertPegoutWasNotRegistered(svpSpendTx);
        assertFalse(federationSupport.hasFederationsPendingBtcUTXOs(svpSpendTx.getHash()));
        assertEquals(Optional.of(svpSpendTx.getHash()), bridgeStorageProvider.getSvpSpendTxHashUnsigned());
    }

    @Test
    void registerPegoutTransaction_withPegoutTx_whileSvpIsOngoing_shouldRegisterChangeInActiveFed() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        // arrange
        BtcTransaction pegout = setupPegoutFromActiveFedPayingToUser();
        BtcTransaction svpFundTx = setupSvpFundTxFromActiveFed();
        setUpBridgeAndFederationSupportForExecutionBlock(SVP_ONGOING_BLOCK_NUMBER);
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
        BtcTransaction svpFundTx = setupSvpFundTxFromActiveFed();
        setUpBridgeAndFederationSupportForExecutionBlock(SVP_VALIDATION_PERIOD_END_BLOCK_NUMBER);

        // the svp fails since its validation period ended, clearing the proposed federation and the svp values
        Transaction updateCollectionsTransaction = buildUpdateCollectionsTransaction(2);
        bridgeSupport.updateCollections(updateCollectionsTransaction);
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
        BtcTransaction pegout = setupPegoutFromActiveFedPayingToUser();
        setUpBridgeAndFederationSupportForExecutionBlock(PEGOUT_REGISTRATION_BLOCK_NUMBER);
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
        BtcTransaction pegout = setupPegoutFromActiveFedPayingToUser();
        setUpBridgeAndFederationSupportForExecutionBlock(PEGOUT_REGISTRATION_BLOCK_NUMBER);
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
        BtcTransaction pegout = setupPegoutFromActiveFedPayingToUser();
        setUpBridgeAndFederationSupportForExecutionBlock(PEGOUT_REGISTRATION_BLOCK_NUMBER);
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
        BtcTransaction pegout = setupPegoutFromActiveFedPayingToUser();
        setUpBridgeAndFederationSupportForExecutionBlock(PEGOUT_REGISTRATION_BLOCK_NUMBER);
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
        BtcTransaction pegout = setupPegoutFromActiveFedPayingToUser();
        setUpBridgeAndFederationSupportForExecutionBlock(PEGOUT_REGISTRATION_BLOCK_NUMBER);
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
        BtcTransaction pegout = setupPegoutFromActiveFedPayingToUser();
        setUpBridgeAndFederationSupportForExecutionBlock(PEGOUT_REGISTRATION_BLOCK_NUMBER);
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
        BtcTransaction pegout = setupPegoutFromActiveFedPayingToUser();
        setUpBridgeAndFederationSupportForExecutionBlock(PEGOUT_REGISTRATION_BLOCK_NUMBER);
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
        BtcTransaction pegout = setupPegoutFromActiveFedPayingToUser();
        setUpBridgeAndFederationSupportForExecutionBlock(PEGOUT_REGISTRATION_BLOCK_NUMBER);
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
        BtcTransaction pegout = setupPegoutFromActiveFedPayingToUser();
        setUpBridgeAndFederationSupportForExecutionBlock(PEGOUT_REGISTRATION_BLOCK_NUMBER);
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
        BtcTransaction pegout = setupPegoutFromActiveFedPayingToUser();
        setUpBridgeAndFederationSupportForExecutionBlock(PEGOUT_REGISTRATION_BLOCK_NUMBER);
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
        Coin changeValue = releaseTransaction.getOutput(outputIndexForChange).getValue();
        assertOutputsWereRegisteredInActiveFed(
            releaseTransaction,
            List.of(changeValue),
            List.of(outputIndexForChange)
        );
    }

    private void assertMigrationWasRegisteredInActiveFed(BtcTransaction migrationTransaction) throws IOException {
        // every migration output is sent to the active federation
        List<Coin> migratedValues = migrationTransaction.getOutputs().stream()
            .map(TransactionOutput::getValue)
            .toList();
        List<Long> migrationOutputIndexes = LongStream.range(0, migratedValues.size())
            .boxed()
            .toList();
        assertOutputsWereRegisteredInActiveFed(
            migrationTransaction,
            migratedValues,
            migrationOutputIndexes
        );
    }

    private void assertOutputsWereRegisteredInActiveFed(
        BtcTransaction releaseTransaction,
        List<Coin> expectedValues,
        List<Long> expectedOutputIndexes
    ) throws IOException {
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
            expectedValues,
            expectedOutputIndexes,
            activeFederation.getAddress()
        );
    }

    private void assertPegoutWasNotRegistered(BtcTransaction release) throws IOException {
        assertTrue(federationSupport.getActiveFederationBtcUTXOs().isEmpty());
        assertTrue(bridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(release.getHash()).isEmpty());
        assertEventWasNotEmitted(logs, BridgeEvents.UTXOS_REGISTERED.getEvent());
    }

    private void setUpBridgeAndFederationSupportForExecutionBlock(long executionBlockNumber) {
        setUpBridgeAndFederationSupportWithActivations(executionBlockNumber, allActivations);
    }

    private void setUpBridgeAndFederationSupportForVetiverExecutionBlock(long executionBlockNumber) {
        setUpBridgeAndFederationSupportWithActivations(executionBlockNumber, vetiverActivations);
    }

    private void setUpBridgeAndFederationSupportWithActivations(long executionBlockNumber, ActivationConfig.ForBlock activations) {
        rskExecutionBlock = createRskBlock(executionBlockNumber);

        federationSupport = FederationSupportBuilder.builder()
            .withFederationConstants(federationMainnetConstants)
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(rskExecutionBlock)
            .withActivations(activations)
            .build();

        bridgeSupport = BridgeSupportBuilder.builder()
            .withActivations(activations)
            .withExecutionBlock(rskExecutionBlock)
            .withBridgeConstants(bridgeMainnetConstants)
            .withProvider(bridgeStorageProvider)
            .withRepository(repository)
            .withEventLogger(new BridgeEventLoggerImpl(bridgeMainnetConstants, activations, logs))
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .build();
    }

    private BtcTransaction setupPegoutFromActiveFedPayingToUser() throws IOException {
        setUpBridgeAndFederationSupportForExecutionBlock(PEGOUT_CREATION_BLOCK_NUMBER);
        return createPegoutFromActiveFedPayingToUser(PEGOUT_REQUEST_VALUE);
    }

    private BtcTransaction setupPegoutForVetiverFromActiveFedPayingToUser() throws IOException {
        setUpBridgeAndFederationSupportForVetiverExecutionBlock(PEGOUT_CREATION_BLOCK_NUMBER);
        return createPegoutFromActiveFedPayingToUser(PEGOUT_REQUEST_VALUE);
    }

    private BtcTransaction setupPegoutWithoutChangeFromActiveFedPayingToUser() throws IOException {
        setUpBridgeAndFederationSupportForExecutionBlock(PEGOUT_CREATION_BLOCK_NUMBER);
        return createPegoutFromActiveFedPayingToUser(INPUT_VALUE);
    }

    private BtcTransaction setupPegoutWithChangeBelowMinimumPeginValueFromActiveFedPayingToUser() throws IOException {
        setUpBridgeAndFederationSupportForExecutionBlock(PEGOUT_CREATION_BLOCK_NUMBER);
        return createPegoutFromActiveFedPayingToUser(INPUT_VALUE.subtract(CHANGE_BELOW_MINIMUM_PEGIN_VALUE));
    }

    private BtcTransaction setupPegoutWithMaximumInputsAndOutputsFromActiveFed() throws IOException {
        setUpBridgeAndFederationSupportForExecutionBlock(PEGOUT_CREATION_BLOCK_NUMBER);
        List<UTXO> activeFederationUtxos = UTXOBuilder.builder()
            .withValue(INPUT_VALUE)
            .withScriptPubKey(activeFederation.getP2SHScript())
            .buildMany(MAXIMUM_PEGOUT_INPUTS, i -> BitcoinTestUtils.createHash(i + 1));
        federationStorageProvider.getNewFederationBtcUTXOs(btcMainnetParams, allActivations).addAll(activeFederationUtxos);

        addPegoutRequestsToQueue(
            bridgeStorageProvider.getReleaseRequestQueue(),
            MAXIMUM_PEGOUT_REQUESTS,
            MAXIMUM_PEGOUT_REQUEST_VALUE,
            btcMainnetParams
        );

        Transaction updateCollectionsTransaction = buildUpdateCollectionsTransaction();
        bridgeSupport.updateCollections(updateCollectionsTransaction);

        return getReleaseFromPegoutsWFC(bridgeStorageProvider);
    }

    private BtcTransaction createPegoutFromActiveFedPayingToUser(Coin pegoutRequestValue) throws IOException {
        addActiveFederationUtxo(BitcoinTestUtils.createHash(1));

        bridgeStorageProvider.getReleaseRequestQueue().add(
            userAddress,
            pegoutRequestValue,
            PEGOUT_REQUEST_RSK_TX_HASH
        );

        // settles the release request, storing the pegout change as federations pending utxos
        Transaction updateCollectionsTransaction = buildUpdateCollectionsTransaction();
        bridgeSupport.updateCollections(updateCollectionsTransaction);

        return getReleaseFromPegoutsWFC(bridgeStorageProvider);
    }

    private BtcTransaction setupMigrationFromRetiringFedToActiveFed(Coin retiringFederationBalance) throws IOException {
        UTXO retiringFederationUtxo = UTXOBuilder.builder()
            .withTransactionHash(BitcoinTestUtils.createHash(3))
            .withValue(retiringFederationBalance)
            .withScriptPubKey(retiringFederation.getP2SHScript())
            .build();
        return createMigrationFromRetiringFedToActiveFed(List.of(retiringFederationUtxo));
    }

    private BtcTransaction setupMigrationWithMaximumInputsFromRetiringFedToActiveFed(Coin retiringFederationUtxoValue) throws IOException {
        List<UTXO> retiringFederationUtxos = UTXOBuilder.builder()
            .withValue(retiringFederationUtxoValue)
            .withScriptPubKey(retiringFederation.getP2SHScript())
            .buildMany(MAXIMUM_MIGRATION_INPUTS, i -> BitcoinTestUtils.createHash(i + 1));
        return createMigrationFromRetiringFedToActiveFed(retiringFederationUtxos);
    }

    private BtcTransaction createMigrationFromRetiringFedToActiveFed(List<UTXO> retiringFederationUtxos) throws IOException {
        setUpBridgeAndFederationSupportForExecutionBlock(MIGRATION_CREATION_BLOCK_NUMBER);
        federationStorageProvider.setOldFederation(retiringFederation);
        federationStorageProvider.getOldFederationBtcUTXOs().addAll(retiringFederationUtxos);

        Transaction updateCollectionsTransaction = buildUpdateCollectionsTransaction();
        bridgeSupport.updateCollections(updateCollectionsTransaction);

        return getReleaseFromPegoutsWFC(bridgeStorageProvider);
    }

    private BtcTransaction setupSvpFundTxFromActiveFed() throws IOException {
        setUpBridgeAndFederationSupportForExecutionBlock(SVP_FUND_TX_CREATION_BLOCK_NUMBER);

        Federation proposedFederation = P2shP2wshErpFederationBuilder.builder()
            .withCreationBlockNumber(SVP_FUND_TX_CREATION_BLOCK_NUMBER)
            .build();
        federationStorageProvider.setProposedFederation(proposedFederation);
        addActiveFederationUtxo(BitcoinTestUtils.createHash(2));

        // with the svp ongoing, creates the svp fund tx, storing its change as federations pending utxos
        Transaction updateCollectionsTransaction = buildUpdateCollectionsTransaction(1);
        bridgeSupport.updateCollections(updateCollectionsTransaction);

        Sha256Hash svpFundTxHash = bridgeStorageProvider.getSvpFundTxHashUnsigned().orElseThrow();
        return bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries(allActivations).stream()
            .map(PegoutsWaitingForConfirmations.Entry::getBtcTransaction)
            .filter(releaseTransaction -> releaseTransaction.getHash().equals(svpFundTxHash))
            .findFirst()
            .orElseThrow();
    }

    private BtcTransaction setupSvpSpendTxFromProposedFed() throws IOException {
        BtcTransaction svpFundTx = setupSvpFundTxFromActiveFed();
        setUpBridgeAndFederationSupportForExecutionBlock(SVP_ONGOING_BLOCK_NUMBER);
        bridgeStorageProvider.setSvpFundTxSigned(svpFundTx);

        Transaction updateCollectionsTransaction = buildUpdateCollectionsTransaction(2);
        bridgeSupport.updateCollections(updateCollectionsTransaction);

        return bridgeStorageProvider.getSvpSpendTxWaitingForSignatures().orElseThrow().getValue();
    }

    private void addActiveFederationUtxo(Sha256Hash btcTxHash) {
        UTXO activeFederationUtxo = UTXOBuilder.builder()
            .withTransactionHash(btcTxHash)
            .withValue(INPUT_VALUE)
            .withScriptPubKey(activeFederation.getP2SHScript())
            .build();
        federationStorageProvider.getNewFederationBtcUTXOs(btcMainnetParams, allActivations).add(activeFederationUtxo);
    }

    private static BtcTransaction buildPegoutWithChangeFromActiveFedPayingTo(Address user) {
        Sha256Hash spendTxHash = BitcoinTestUtils.createHash(1);
        return PegoutTransactionBuilder.builder()
            .withNetworkParameters(btcMainnetParams)
            .withActiveFederation(activeFederation)
            .withInput(spendTxHash, 0, INPUT_VALUE)
            .withOutput(PEGOUT_OUTPOINT_VALUE, user)
            .withChangeAmount(CHANGE_VALUE)
            .withSignatures(activeFederationKeys)
            .build();
    }

    private static BtcTransaction buildPeginFromUserToActiveFed() {
        return buildPeginFromUserTo(activeFederation.getP2SHScript());
    }

    private static BtcTransaction buildFlyoverPeginFromUserToActiveFed() {
        Script flyoverRedeemScript = PegUtils.getFlyoverFederationRedeemScript(
            FLYOVER_DERIVATION_HASH,
            activeFederation.getRedeemScript()
        );
        Script flyoverOutputScript = PegUtils.getFlyoverFederationOutputScript(
            flyoverRedeemScript,
            activeFederation.getFormatVersion()
        );
        return buildPeginFromUserTo(flyoverOutputScript);
    }

    private static BtcTransaction buildPeginFromUserTo(Script outputScript) {
        BtcTransaction peginTransaction = new BtcTransaction(btcMainnetParams);
        BtcECKey userKey = BitcoinTestUtils.getBtcEcKeyFromSeed("userAddress");
        peginTransaction.addInput(
            BitcoinTestUtils.createHash(4),
            0,
            ScriptBuilder.createInputScript(null, userKey)
        );
        peginTransaction.addOutput(INPUT_VALUE, outputScript);
        return peginTransaction;
    }
}
