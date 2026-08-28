package co.rsk.peg;

import static co.rsk.RskTestUtils.createRepository;
import static co.rsk.peg.BridgeEventsTestUtils.getEncodedData;
import static co.rsk.peg.BridgeEventsTestUtils.getEncodedTopics;
import static co.rsk.peg.BridgeEventsTestUtils.getLogsTopics;
import static co.rsk.peg.BridgeSupportTestUtil.buildPegoutRequestTransaction;
import static co.rsk.peg.BridgeSupportTestUtil.buildUpdateCollectionsTransaction;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.createHash;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.rsk.RskTestUtils;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.PegoutsWaitingForConfirmations.Entry;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.constants.BridgeMainNetConstantsWithHistoricalPegoutSelection;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationStorageProvider;
import co.rsk.peg.federation.FederationStorageProviderImpl;
import co.rsk.peg.federation.FederationSupport;
import co.rsk.peg.federation.P2shErpFederationBuilder;
import co.rsk.peg.federation.P2shP2wshErpFederationBuilder;
import co.rsk.peg.federation.StandardMultiSigFederationBuilder;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.feeperkb.FeePerKbStorageIndexKey;
import co.rsk.peg.feeperkb.FeePerKbStorageProviderImpl;
import co.rsk.peg.feeperkb.FeePerKbSupport;
import co.rsk.peg.feeperkb.FeePerKbSupportImpl;
import co.rsk.peg.storage.InMemoryStorage;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.utils.BridgeEventLoggerImpl;
import co.rsk.test.builders.BridgeSupportBuilder;
import co.rsk.test.builders.FederationSupportBuilder;
import co.rsk.test.builders.UTXOBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Block;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.Repository;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the confirmed pegouts processing of {@code updateCollections} end to end on mainnet constants:
 * real pegouts, built by the bridge itself out of real pegout requests, are confirmed against a real
 * storage provider, and every resulting state change is asserted.
 *
 * <p>The pegout that each call confirms is the concern under test. From RSKIP559 on it is the first one in
 * the deterministic btc tx order; before RSKIP559 it is the one the network historically selected for that
 * {@code updateCollections}.</p>
 */
class BridgeSupportProcessConfirmedPegoutsTest {
    private static final BridgeConstants BRIDGE_CONSTANTS = BridgeMainNetConstants.getInstance();
    private static final FederationConstants FEDERATION_CONSTANTS = BRIDGE_CONSTANTS.getFederationConstants();
    private static final NetworkParameters NETWORK_PARAMETERS = BRIDGE_CONSTANTS.getBtcParams();

    private static final ActivationConfig.ForBlock ALL_ACTIVATIONS = ActivationConfigsForTest.all().forBlock(0L);
    private static final ActivationConfig.ForBlock VETIVER_ACTIVATIONS = ActivationConfigsForTest.vetiver900().forBlock(0L);
    private static final ActivationConfig.ForBlock FINGERROOT_ACTIVATIONS = ActivationConfigsForTest.fingerroot500().forBlock(0L);
    private static final ActivationConfig.ForBlock HOP_ACTIVATIONS = ActivationConfigsForTest.hop400().forBlock(0L);
    private static final ActivationConfig.ForBlock IRIS_ACTIVATIONS = ActivationConfigsForTest.iris300().forBlock(0L);
    private static final ActivationConfig.ForBlock PAPYRUS_ACTIVATIONS = ActivationConfigsForTest.papyrus200().forBlock(0L);
    private static final ActivationConfig.ForBlock WASABI_ACTIVATIONS = ActivationConfigsForTest.wasabi100().forBlock(0L);

    // The active federation of each era, since the fixtures build real pegout txs by spending its utxos.
    private static final Federation STANDARD_MULTISIG_FEDERATION = StandardMultiSigFederationBuilder.builder()
        .withNetworkParameters(NETWORK_PARAMETERS)
        .build();
    private static final Federation P2SH_ERP_FEDERATION = P2shErpFederationBuilder.builder()
        .withNetworkParameters(NETWORK_PARAMETERS)
        .build();
    private static final Federation P2SH_P2WSH_ERP_FEDERATION = P2shP2wshErpFederationBuilder.builder()
        .withNetworkParameters(NETWORK_PARAMETERS)
        .build();

    private static final long ACTIVE_FEDERATION_CREATION_BLOCK = 100L;
    private static final int RETIRING_FEDERATION_UTXOS = 2;

    private static final int FEDERATION_UTXOS = 10;
    private static final Coin FEDERATION_UTXO_VALUE = Coin.COIN;
    private static final Coin FEE_PER_KB = Coin.valueOf(8_000L);

    private static final List<Coin> PEGOUT_REQUEST_VALUES = List.of(
        Coin.valueOf(250_000_000L), // 2.5 btc, three utxos
        Coin.valueOf(50_000_000L),  // 0.5 btc, one utxo
        Coin.valueOf(150_000_000L)  // 1.5 btc, two utxos
    );

    private static final int MINIMUM_CONFIRMATIONS = BRIDGE_CONSTANTS.getRsk2BtcMinimumAcceptableConfirmations();
    private static final int BLOCKS_BETWEEN_PEGOUTS = BRIDGE_CONSTANTS.getNumberOfBlocksBetweenPegouts();
    private static final long FIRST_PEGOUT_CREATION_BLOCK = 1_000L;
    private static final int SEVERAL_PEGOUTS = PEGOUT_REQUEST_VALUES.size();
    private static final long LAST_PEGOUT_CREATION_BLOCK =
        FIRST_PEGOUT_CREATION_BLOCK + (long) (SEVERAL_PEGOUTS - 1) * BLOCKS_BETWEEN_PEGOUTS;
    private static final long BLOCK_WITH_EVERY_PEGOUT_CONFIRMABLE = LAST_PEGOUT_CREATION_BLOCK + MINIMUM_CONFIRMATIONS;

    private static final long FIRST_CONFIRMING_UPDATE_COLLECTIONS_NONCE = 100L;

    private static final Sha256Hash BTC_TX_HASH_OF_NO_PEGOUT =
        Sha256Hash.wrap("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");

    private static final CallTransaction.Function PEGOUT_CONFIRMED_EVENT = BridgeEvents.PEGOUT_CONFIRMED.getEvent();

    private final List<LogInfo> logs = new ArrayList<>();

    private Repository repository;
    private BridgeStorageProvider bridgeStorageProvider;
    private FederationStorageProvider federationStorageProvider;
    private FeePerKbSupport feePerKbSupport;
    private Federation activeFederation;
    private SignatureCache signatureCache;
    private ActivationConfig.ForBlock activations;
    private BridgeSupport bridgeSupport;

    @BeforeEach
    void setUp() {
        repository = createRepository();
        signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());

        StorageAccessor bridgeStorageAccessor = new InMemoryStorage();
        federationStorageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);
        feePerKbSupport = new FeePerKbSupportImpl(
            BRIDGE_CONSTANTS.getFeePerKbConstants(),
            new FeePerKbStorageProviderImpl(bridgeStorageAccessor)
        );
        bridgeStorageAccessor.saveToRepository(
            FeePerKbStorageIndexKey.FEE_PER_KB.getKey(),
            FEE_PER_KB,
            BridgeSerializationUtils::serializeCoin
        );
    }

    @Test
    void updateCollections_whenPegoutReachesTheMinimumConfirmations_shouldConfirmIt() throws IOException {
        // Arrange
        List<Entry> pegouts = createPegoutsWaitingForConfirmations(1, P2SH_P2WSH_ERP_FEDERATION, ALL_ACTIVATIONS);
        Entry pegout = pegouts.get(0);
        setUpBridgeSupport(BRIDGE_CONSTANTS, ALL_ACTIVATIONS, blockWithEnoughConfirmations(pegout));

        // Act
        bridgeSupport.updateCollections(confirmingUpdateCollectionsTransaction(0));

        // Assert
        assertPegoutWasConfirmed(pegout, pegouts);
    }

    @Test
    void updateCollections_whenPegoutIsOneConfirmationShort_shouldNotConfirmIt() throws IOException {
        // Arrange
        List<Entry> pegouts = createPegoutsWaitingForConfirmations(1, P2SH_P2WSH_ERP_FEDERATION, ALL_ACTIVATIONS);
        Entry pegout = pegouts.get(0);
        setUpBridgeSupport(BRIDGE_CONSTANTS, ALL_ACTIVATIONS, blockWithEnoughConfirmations(pegout) - 1);

        // Act
        bridgeSupport.updateCollections(confirmingUpdateCollectionsTransaction(0));

        // Assert
        assertNoPegoutWasConfirmed(pegouts);
    }

    @Test
    void updateCollections_fromRskip559_whenSeveralPegoutsAreConfirmable_shouldConfirmTheFirstInTheDeterministicOrder() throws IOException {
        // Arrange
        List<Entry> pegouts = createPegoutsWaitingForConfirmations(SEVERAL_PEGOUTS, P2SH_P2WSH_ERP_FEDERATION, ALL_ACTIVATIONS);
        Entry expectedPegout = firstInDeterministicOrder(pegouts);
        assertNotEquals(
            pegouts.get(0),
            expectedPegout,
            "the deterministic pick must not be the oldest pegout, or the test cannot tell the two rules apart"
        );

        setUpBridgeSupport(BRIDGE_CONSTANTS, ALL_ACTIVATIONS, BLOCK_WITH_EVERY_PEGOUT_CONFIRMABLE);

        // Act
        bridgeSupport.updateCollections(confirmingUpdateCollectionsTransaction(0));

        // Assert
        assertPegoutWasConfirmed(expectedPegout, pegouts);
    }

    @Test
    void updateCollections_fromRskip559_whenCalledRepeatedly_shouldConfirmOnePegoutPerCallInTheDeterministicOrder() throws IOException {
        // Arrange
        List<Entry> pegouts = createPegoutsWaitingForConfirmations(SEVERAL_PEGOUTS, P2SH_P2WSH_ERP_FEDERATION, ALL_ACTIVATIONS);
        List<Entry> expectedOrder = pegouts.stream().sorted(Entry.BTC_TX_COMPARATOR).toList();
        assertNotEquals(
            pegouts,
            expectedOrder,
            "the deterministic order must not be the creation order, or the test cannot tell the two rules apart"
        );

        for (int call = 0; call < expectedOrder.size(); call++) {
            Entry expectedPegout = expectedOrder.get(call);
            setUpBridgeSupport(
                BRIDGE_CONSTANTS,
                ALL_ACTIVATIONS,
                BLOCK_WITH_EVERY_PEGOUT_CONFIRMABLE + call
            );
            logs.clear();

            // Act
            bridgeSupport.updateCollections(confirmingUpdateCollectionsTransaction(call));

            // Assert
            assertEquals(
                expectedPegout.getBtcTransaction(),
                pegoutsWaitingForSignatures().get(expectedPegout.getPegoutCreationRskTxHash())
            );
            assertEquals(
                call + 1,
                pegoutsWaitingForSignatures().size(),
                "each call must confirm exactly one pegout"
            );
            assertEquals(
                pegouts.size() - call - 1,
                pegoutsWaitingForConfirmations().size()
            );
            assertPegoutConfirmedEventWasLogged(expectedPegout);
        }

        // Arrange
        setUpBridgeSupport(BRIDGE_CONSTANTS, ALL_ACTIVATIONS, BLOCK_WITH_EVERY_PEGOUT_CONFIRMABLE + pegouts.size());
        logs.clear();

        // Act
        bridgeSupport.updateCollections(confirmingUpdateCollectionsTransaction(pegouts.size()));

        // Assert
        assertTrue(pegoutsWaitingForConfirmations().isEmpty());
        assertEquals(pegouts.size(), pegoutsWaitingForSignatures().size(), "a call with nothing left to confirm must not touch the map");
        assertNoPegoutConfirmedEventWasLogged(pegouts);
    }

    @Test
    void updateCollections_fromRskip559_whenTheCallHasARecordedSelection_shouldIgnoreItAndConfirmTheFirstInTheDeterministicOrder() throws IOException {
        // Arrange
        List<Entry> pegouts = createPegoutsWaitingForConfirmations(SEVERAL_PEGOUTS, P2SH_P2WSH_ERP_FEDERATION, ALL_ACTIVATIONS);
        Entry expectedPegout = firstInDeterministicOrder(pegouts);
        Entry recordedPegout = lastInDeterministicOrder(pegouts);
        assertNotEquals(
            expectedPegout,
            recordedPegout,
            "the recorded selection must differ from the deterministic pick"
        );

        Transaction confirmingTransaction = confirmingUpdateCollectionsTransaction(0);
        BridgeConstants bridgeConstants = constantsRecording(confirmingTransaction, recordedPegout.getBtcTransaction().getHash());
        setUpBridgeSupport(bridgeConstants, ALL_ACTIVATIONS, BLOCK_WITH_EVERY_PEGOUT_CONFIRMABLE);

        // Act
        bridgeSupport.updateCollections(confirmingTransaction);

        // Assert
        assertPegoutWasConfirmed(expectedPegout, pegouts);
    }

    @Test
    void updateCollections_beforeRskip559_whenTheCallHasARecordedSelection_shouldConfirmTheRecordedPegout() throws IOException {
        // Arrange
        List<Entry> pegouts = createPegoutsWaitingForConfirmations(SEVERAL_PEGOUTS, P2SH_P2WSH_ERP_FEDERATION, VETIVER_ACTIVATIONS);
        Entry recordedPegout = lastInDeterministicOrder(pegouts);
        assertNotEquals(
            firstInDeterministicOrder(pegouts),
            recordedPegout,
            "the recorded selection must differ from the deterministic pick, or the test proves nothing"
        );

        Transaction confirmingTransaction = confirmingUpdateCollectionsTransaction(0);
        BridgeConstants bridgeConstants = constantsRecording(confirmingTransaction, recordedPegout.getBtcTransaction().getHash());
        setUpBridgeSupport(bridgeConstants, VETIVER_ACTIVATIONS, BLOCK_WITH_EVERY_PEGOUT_CONFIRMABLE);

        // Act
        bridgeSupport.updateCollections(confirmingTransaction);

        // Assert
        assertPegoutWasConfirmed(recordedPegout, pegouts);
    }

    @Test
    void updateCollections_beforeRskip559_whenTheRecordedSelectionBelongsToAnotherCall_shouldNotApplyIt() throws IOException {
        // Arrange
        // The table is keyed by the confirming updateCollections tx, never by the tx that created the pegout.
        // A selection recorded under the creation tx must miss, leaving the ordinary flow untouched: were it
        // consulted, its btc tx hash would not be among the pegouts waiting for confirmations, and the call
        // would fail.
        List<Entry> pegouts = createPegoutsWaitingForConfirmations(1, P2SH_P2WSH_ERP_FEDERATION, VETIVER_ACTIVATIONS);
        Entry pegout = pegouts.get(0);
        BridgeConstants bridgeConstants = new BridgeMainNetConstantsWithHistoricalPegoutSelection(
            pegout.getPegoutCreationRskTxHash(),
            BTC_TX_HASH_OF_NO_PEGOUT
        );
        setUpBridgeSupport(bridgeConstants, VETIVER_ACTIVATIONS, blockWithEnoughConfirmations(pegout));

        // Act
        bridgeSupport.updateCollections(confirmingUpdateCollectionsTransaction(0));

        // Assert
        assertPegoutWasConfirmed(pegout, pegouts);
    }

    @Test
    void updateCollections_beforeRskip559_whenTheRecordedSelectionIsNotWaitingForConfirmations_shouldFailAndConfirmNoPegout() throws IOException {
        // Arrange
        List<Entry> pegouts = createPegoutsWaitingForConfirmations(SEVERAL_PEGOUTS, P2SH_P2WSH_ERP_FEDERATION, VETIVER_ACTIVATIONS);
        Transaction confirmingTransaction = confirmingUpdateCollectionsTransaction(0);
        BridgeConstants bridgeConstants = constantsRecording(confirmingTransaction, BTC_TX_HASH_OF_NO_PEGOUT);
        setUpBridgeSupport(bridgeConstants, VETIVER_ACTIVATIONS, BLOCK_WITH_EVERY_PEGOUT_CONFIRMABLE);

        // Act
        IllegalStateException thrown = assertThrows(
            IllegalStateException.class,
            () -> bridgeSupport.updateCollections(confirmingTransaction)
        );

        // Assert
        assertTrue(thrown.getMessage().contains(BTC_TX_HASH_OF_NO_PEGOUT.toString()));
        assertNoPegoutWasConfirmed(pegouts);
    }

    @Test
    void updateCollections_beforeRskip146_shouldKeyTheConfirmedPegoutByTheUpdateCollectionsTx() throws IOException {
        // Arrange
        List<Entry> pegouts = createPegoutsWaitingForConfirmations(1, STANDARD_MULTISIG_FEDERATION, WASABI_ACTIVATIONS);
        Entry pegout = pegouts.get(0);
        assertNull(
            pegout.getPegoutCreationRskTxHash(),
            "before RSKIP146 the bridge records no creation tx hash, which is what leaves the updateCollections tx as the only possible key"
        );

        Transaction confirmingTransaction = confirmingUpdateCollectionsTransaction(0);
        setUpBridgeSupport(BRIDGE_CONSTANTS, WASABI_ACTIVATIONS, blockWithEnoughConfirmations(pegout));

        // Act
        bridgeSupport.updateCollections(confirmingTransaction);

        // Assert
        assertPegoutWasConfirmedUnder(pegout, confirmingTransaction.getHash(), pegouts);
        assertNoPegoutConfirmedEventWasLogged(pegouts);
    }

    @Test
    void updateCollections_fromRskip146_beforeRskip176_shouldKeyTheConfirmedPegoutByItsCreationTx() throws IOException {
        // Arrange
        Transaction pegoutRequest = pegoutRequestTransaction(0);
        List<Entry> pegouts = createPegoutsWaitingForConfirmations(1, STANDARD_MULTISIG_FEDERATION, PAPYRUS_ACTIVATIONS);
        Entry pegout = pegouts.get(0);
        assertEquals(
            pegoutRequest.getHash(),
            pegout.getPegoutCreationRskTxHash(),
            "before RSKIP271 a pegout is built for one request, so its creation tx is the releaseBtc tx"
        );

        setUpBridgeSupport(BRIDGE_CONSTANTS, PAPYRUS_ACTIVATIONS, blockWithEnoughConfirmations(pegout));

        // Act
        bridgeSupport.updateCollections(confirmingUpdateCollectionsTransaction(0));

        // Assert
        assertPegoutWasConfirmedUnder(pegout, pegout.getPegoutCreationRskTxHash(), pegouts);
        assertNoPegoutConfirmedEventWasLogged(pegouts);
    }

    @Test
    void updateCollections_fromRskip146_whenThePegoutWasCreatedBeforeRskip146_shouldKeyItByTheUpdateCollectionsTx() throws IOException {
        // Arrange
        // A pegout created before RSKIP146 carries no creation tx hash, and it is still waiting when the fork
        // activates. Saving it under the old format and reading it back under the new one is what a node
        // crossing the fork does.
        List<Entry> pegouts = createPegoutsWaitingForConfirmations(1, STANDARD_MULTISIG_FEDERATION, WASABI_ACTIVATIONS);
        Entry pegout = pegouts.get(0);
        saveAndReloadStorageAt(PAPYRUS_ACTIVATIONS);
        assertTrue(
            pegoutsWaitingForConfirmations().contains(pegout),
            "the pegout must survive the fork crossing"
        );

        Transaction confirmingTransaction = confirmingUpdateCollectionsTransaction(0);
        setUpBridgeSupport(BRIDGE_CONSTANTS, PAPYRUS_ACTIVATIONS, blockWithEnoughConfirmations(pegout));

        // Act
        bridgeSupport.updateCollections(confirmingTransaction);

        // Assert
        assertPegoutWasConfirmedUnder(pegout, confirmingTransaction.getHash(), pegouts);
        assertNoPegoutConfirmedEventWasLogged(pegouts);
    }

    @Test
    void updateCollections_fromRskip176_beforeRskip375_shouldKeyTheConfirmedPegoutByTheUpdateCollectionsTx() throws IOException {
        // Arrange
        List<Entry> pegouts = createPegoutsWaitingForConfirmations(1, STANDARD_MULTISIG_FEDERATION, IRIS_ACTIVATIONS);
        Entry pegout = pegouts.get(0);
        Transaction confirmingTransaction = confirmingUpdateCollectionsTransaction(0);
        setUpBridgeSupport(BRIDGE_CONSTANTS, IRIS_ACTIVATIONS, blockWithEnoughConfirmations(pegout));

        // Act
        bridgeSupport.updateCollections(confirmingTransaction);

        // Assert
        assertPegoutWasConfirmedUnder(pegout, confirmingTransaction.getHash(), pegouts);
        assertNull(
            pegoutsWaitingForSignatures().get(pegout.getPegoutCreationRskTxHash()),
            "RSKIP176 goes back to the updateCollections tx as the key, even though the pegout has a creation tx hash"
        );
        assertNoPegoutConfirmedEventWasLogged(pegouts);
    }

    @Test
    void updateCollections_beforeRskip326_shouldConfirmThePegoutWithoutLoggingIt() throws IOException {
        // Arrange
        List<Entry> pegouts = createPegoutsWaitingForConfirmations(1, P2SH_ERP_FEDERATION, HOP_ACTIVATIONS);
        Entry pegout = pegouts.get(0);
        Transaction confirmingTransaction = confirmingUpdateCollectionsTransaction(0);
        setUpBridgeSupport(BRIDGE_CONSTANTS, HOP_ACTIVATIONS, blockWithEnoughConfirmations(pegout));

        // Act
        bridgeSupport.updateCollections(confirmingTransaction);

        // Assert
        assertPegoutWasConfirmedUnder(pegout, confirmingTransaction.getHash(), pegouts);
        assertNoPegoutConfirmedEventWasLogged(pegouts);
    }

    @Test
    void updateCollections_beforeRskip375_whenTheKeyIsAlreadyTaken_shouldOverrideTheEntry() throws IOException {
        // Arrange
        // Two entries under the same key cannot arise on a live chain in this era, since the key is the
        // confirming updateCollections tx. The scenario pins the absence of the guard that RSKIP375 adds:
        // before the fork the bridge overwrites, losing the btc tx that was already waiting.
        List<Entry> pegouts = createPegoutsWaitingForConfirmations(1, STANDARD_MULTISIG_FEDERATION, IRIS_ACTIVATIONS);
        Entry pegout = pegouts.get(0);
        Transaction confirmingTransaction = confirmingUpdateCollectionsTransaction(0);

        BtcTransaction alreadyWaitingForSignatures = anotherBtcTransaction();
        pegoutsWaitingForSignatures().put(confirmingTransaction.getHash(), alreadyWaitingForSignatures);
        setUpBridgeSupport(BRIDGE_CONSTANTS, IRIS_ACTIVATIONS, blockWithEnoughConfirmations(pegout));

        // Act
        bridgeSupport.updateCollections(confirmingTransaction);

        // Assert
        assertPegoutWasConfirmedUnder(pegout, confirmingTransaction.getHash(), pegouts);
        assertNotEquals(
            alreadyWaitingForSignatures,
            pegoutsWaitingForSignatures().get(confirmingTransaction.getHash()),
            "the btc tx that was already waiting must have been overridden"
        );
    }

    @Test
    void updateCollections_fromRskip375_whenTheKeyIsAlreadyTaken_shouldFailAndConfirmNoPegout() throws IOException {
        // Arrange
        List<Entry> pegouts = createPegoutsWaitingForConfirmations(1, P2SH_ERP_FEDERATION, FINGERROOT_ACTIVATIONS);
        Entry pegout = pegouts.get(0);
        Transaction confirmingTransaction = confirmingUpdateCollectionsTransaction(0);

        BtcTransaction alreadyWaitingForSignatures = anotherBtcTransaction();
        pegoutsWaitingForSignatures().put(pegout.getPegoutCreationRskTxHash(), alreadyWaitingForSignatures);
        setUpBridgeSupport(BRIDGE_CONSTANTS, FINGERROOT_ACTIVATIONS, blockWithEnoughConfirmations(pegout));

        // Act
        assertThrows(
            IllegalStateException.class,
            () -> bridgeSupport.updateCollections(confirmingTransaction)
        );

        // Assert
        assertEquals(1, pegoutsWaitingForSignatures().size());
        assertEquals(
            alreadyWaitingForSignatures,
            pegoutsWaitingForSignatures().get(pegout.getPegoutCreationRskTxHash()),
            "the btc tx that was already waiting must be left untouched"
        );
        assertTrue(pegoutsWaitingForConfirmations().contains(pegout));
        assertNoPegoutConfirmedEventWasLogged(pegouts);
    }

    @Test
    void updateCollections_fromRskip375_whenTwoPegoutsShareTheirCreationTx_shouldConfirmOneAndThenFail() throws IOException {
        // Arrange
        // One updateCollections that both migrates funds and batches a pegout leaves two entries carrying its
        // own hash as their creation tx. From RSKIP375 on that hash is the key, so the second confirmation
        // has nowhere to go: it must fail rather than override the first one and lose a pegout.
        Federation retiringFederation = STANDARD_MULTISIG_FEDERATION;
        Federation activeFederation = P2shErpFederationBuilder.builder()
            .withNetworkParameters(NETWORK_PARAMETERS)
            .withCreationBlockNumber(ACTIVE_FEDERATION_CREATION_BLOCK)
            .build();

        bridgeStorageProvider = new BridgeStorageProvider(repository, NETWORK_PARAMETERS, FINGERROOT_ACTIVATIONS);
        setUpActiveFederation(activeFederation, FINGERROOT_ACTIVATIONS);
        setUpRetiringFederation(retiringFederation);

        long migrationBlockNumber = duringMigrationBlockNumber(FINGERROOT_ACTIVATIONS);
        setUpBridgeSupport(BRIDGE_CONSTANTS, FINGERROOT_ACTIVATIONS, migrationBlockNumber);

        Transaction creatingTransaction = buildUpdateCollectionsTransaction(0);
        bridgeSupport.releaseBtc(pegoutRequestTransaction(1));
        bridgeSupport.updateCollections(creatingTransaction);
        logs.clear();

        List<Entry> pegouts = List.copyOf(pegoutsWaitingForConfirmations());
        assertEquals(2, pegouts.size(), "the fixture must leave a migration tx and a batched pegout waiting");
        pegouts.forEach(pegout -> assertEquals(
            creatingTransaction.getHash(),
            pegout.getPegoutCreationRskTxHash(),
            "both entries must carry the creating updateCollections tx as their creation tx"
        ));

        setUpBridgeSupport(BRIDGE_CONSTANTS, FINGERROOT_ACTIVATIONS, migrationBlockNumber + MINIMUM_CONFIRMATIONS);

        // Act
        bridgeSupport.updateCollections(confirmingUpdateCollectionsTransaction(0));

        // Assert
        assertEquals(1, pegoutsWaitingForSignatures().size(), "the first confirmation must go through");
        assertEquals(1, pegoutsWaitingForConfirmations().size());
        BtcTransaction confirmedBtcTx = pegoutsWaitingForSignatures().get(creatingTransaction.getHash());

        // Arrange
        setUpBridgeSupport(BRIDGE_CONSTANTS, FINGERROOT_ACTIVATIONS, migrationBlockNumber + MINIMUM_CONFIRMATIONS + 1);

        // Act
        assertThrows(
            IllegalStateException.class,
            () -> bridgeSupport.updateCollections(confirmingUpdateCollectionsTransaction(1))
        );

        // Assert
        assertEquals(1, pegoutsWaitingForSignatures().size());
        assertEquals(
            confirmedBtcTx,
            pegoutsWaitingForSignatures().get(creatingTransaction.getHash()),
            "the pegout confirmed first must be left untouched"
        );
        assertEquals(1, pegoutsWaitingForConfirmations().size(), "the pegout that could not be confirmed must keep waiting");
    }

    /**
     * Leaves {@code numberOfPegouts} pegouts waiting for confirmations, each one created by the bridge out
     * of its own pegout request, in its own {@code updateCollections} call, one pegout creation period
     * apart. Every entry therefore carries the rsk tx hash and block number of the call that created it,
     * just as it would on a live network.
     *
     * @return the pegouts in creation order.
     */
    private List<Entry> createPegoutsWaitingForConfirmations(
        int numberOfPegouts,
        Federation activeFederation,
        ActivationConfig.ForBlock activations
    ) throws IOException {
        bridgeStorageProvider = new BridgeStorageProvider(repository, NETWORK_PARAMETERS, activations);
        setUpActiveFederation(activeFederation, activations);

        List<Entry> pegouts = new ArrayList<>();
        for (int i = 0; i < numberOfPegouts; i++) {
            long pegoutCreationBlockNumber = FIRST_PEGOUT_CREATION_BLOCK + (long) i * BLOCKS_BETWEEN_PEGOUTS;
            setUpBridgeSupport(BRIDGE_CONSTANTS, activations, pegoutCreationBlockNumber);

            Collection<Entry> pegoutsBeforeTheCall = List.copyOf(pegoutsWaitingForConfirmations());
            bridgeSupport.releaseBtc(pegoutRequestTransaction(i));
            bridgeSupport.updateCollections(buildUpdateCollectionsTransaction(i));

            pegouts.add(pegoutCreatedSince(pegoutsBeforeTheCall));
        }

        assertEquals(
            numberOfPegouts,
            pegoutsWaitingForConfirmations().size(),
            "the fixture must leave one pegout waiting for confirmations per request"
        );
        logs.clear();

        return List.copyOf(pegouts);
    }

    /**
     * The one entry the last call added. Identifying it by difference works in every era: the creation tx
     * hash an entry carries is the releaseBtc tx before RSKIP271, the updateCollections tx from RSKIP271 on,
     * and nothing at all before RSKIP146.
     */
    private Entry pegoutCreatedSince(Collection<Entry> pegoutsBeforeTheCall) throws IOException {
        List<Entry> created = pegoutsWaitingForConfirmations().stream()
            .filter(pegout -> !pegoutsBeforeTheCall.contains(pegout))
            .toList();

        assertEquals(1, created.size(), "the call must have created exactly one pegout");
        return created.get(0);
    }

    private void setUpActiveFederation(Federation federation, ActivationConfig.ForBlock activations) {
        activeFederation = federation;
        federationStorageProvider.setNewFederation(federation);

        List<UTXO> utxos = UTXOBuilder.builder()
            .withValue(FEDERATION_UTXO_VALUE)
            .withScriptPubKey(federation.getP2SHScript())
            .buildMany(FEDERATION_UTXOS, i -> createHash(i + 1));

        federationStorageProvider.getNewFederationBtcUTXOs(NETWORK_PARAMETERS, activations).addAll(utxos);
    }

    /**
     * Sets up a federation being retired, funded with just enough utxos for a single migration transaction,
     * so that no later call in the same test creates another one.
     */
    private void setUpRetiringFederation(Federation federation) {
        federationStorageProvider.setOldFederation(federation);

        List<UTXO> utxos = UTXOBuilder.builder()
            .withValue(FEDERATION_UTXO_VALUE)
            .withScriptPubKey(federation.getP2SHScript())
            .buildMany(RETIRING_FEDERATION_UTXOS, i -> createHash(FEDERATION_UTXOS + i + 1));

        federationStorageProvider.getOldFederationBtcUTXOs().addAll(utxos);
    }

    /**
     * Persists the bridge state and reads it back at {@code activations}, which is what a node crossing a
     * fork does. The pegouts waiting for confirmations are stored under a different key from RSKIP146 on,
     * so entries written before the fork are only reachable through this path.
     */
    private void saveAndReloadStorageAt(ActivationConfig.ForBlock activations) {
        bridgeStorageProvider.save();
        bridgeStorageProvider = new BridgeStorageProvider(repository, NETWORK_PARAMETERS, activations);
    }

    private static long duringMigrationBlockNumber(ActivationConfig.ForBlock activations) {
        return ACTIVE_FEDERATION_CREATION_BLOCK
            + FEDERATION_CONSTANTS.getFederationActivationAge(activations)
            + FEDERATION_CONSTANTS.getFundsMigrationAgeSinceActivationBegin()
            + 1;
    }

    /**
     * A btc transaction that belongs to no pegout, to stand for one already waiting for signatures.
     */
    private BtcTransaction anotherBtcTransaction() {
        BtcTransaction btcTransaction = new BtcTransaction(NETWORK_PARAMETERS);
        btcTransaction.addOutput(FEDERATION_UTXO_VALUE, activeFederation.getAddress());

        return btcTransaction;
    }

    private void setUpBridgeSupport(BridgeConstants bridgeConstants, ActivationConfig.ForBlock executionActivations, long executionBlockNumber) {
        activations = executionActivations;
        Block executionBlock = new BlockGenerator().createBlock(executionBlockNumber, 1);

        FederationSupport federationSupport = FederationSupportBuilder.builder()
            .withFederationConstants(FEDERATION_CONSTANTS)
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(executionBlock)
            .withActivations(executionActivations)
            .build();

        bridgeSupport = BridgeSupportBuilder.builder()
            .withBridgeConstants(bridgeConstants)
            .withProvider(bridgeStorageProvider)
            .withRepository(repository)
            .withEventLogger(new BridgeEventLoggerImpl(bridgeConstants, executionActivations, logs))
            .withExecutionBlock(executionBlock)
            .withActivations(executionActivations)
            .withSignatureCache(signatureCache)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .build();
    }

    private static BridgeConstants constantsRecording(Transaction confirmingTransaction, Sha256Hash selectedPegoutBtcTxHash) {
        return new BridgeMainNetConstantsWithHistoricalPegoutSelection(confirmingTransaction.getHash(), selectedPegoutBtcTxHash);
    }

    private static long blockWithEnoughConfirmations(Entry pegout) {
        return pegout.getPegoutCreationRskBlockNumber() + MINIMUM_CONFIRMATIONS;
    }

    private static Transaction confirmingUpdateCollectionsTransaction(int call) {
        return buildUpdateCollectionsTransaction(FIRST_CONFIRMING_UPDATE_COLLECTIONS_NONCE + call);
    }

    private static Transaction pegoutRequestTransaction(int index) {
        ECKey requesterKey = RskTestUtils.getEcKeyFromSeed("pegout-requester " + index);
        return buildPegoutRequestTransaction(
            co.rsk.core.Coin.fromBitcoin(PEGOUT_REQUEST_VALUES.get(index)),
            index,
            requesterKey
        );
    }

    private static Entry firstInDeterministicOrder(List<Entry> pegouts) {
        return pegouts.stream().min(Entry.BTC_TX_COMPARATOR).orElseThrow();
    }

    private static Entry lastInDeterministicOrder(List<Entry> pegouts) {
        return pegouts.stream().max(Entry.BTC_TX_COMPARATOR).orElseThrow();
    }

    private SortedMap<Keccak256, BtcTransaction> pegoutsWaitingForSignatures() throws IOException {
        return bridgeStorageProvider.getPegoutsWaitingForSignatures();
    }

    private Collection<Entry> pegoutsWaitingForConfirmations() throws IOException {
        return bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries(activations);
    }

    /**
     * From RSKIP375 on the key is the pegout's creation tx, and from RSKIP326 on the confirmation is logged.
     */
    private void assertPegoutWasConfirmed(Entry confirmedPegout, List<Entry> pegouts) throws IOException {
        assertPegoutWasConfirmedUnder(confirmedPegout, confirmedPegout.getPegoutCreationRskTxHash(), pegouts);

        assertPegoutConfirmedEventWasLogged(confirmedPegout);
        assertNoPegoutConfirmedEventWasLogged(pegoutsWaitingForConfirmations());
    }

    private void assertPegoutWasConfirmedUnder(Entry confirmedPegout, Keccak256 expectedKey, List<Entry> pegouts) throws IOException {
        SortedMap<Keccak256, BtcTransaction> waitingForSignatures = pegoutsWaitingForSignatures();
        assertEquals(
            1,
            waitingForSignatures.size(),
            "updateCollections must confirm one pegout at a time"
        );
        assertEquals(
            confirmedPegout.getBtcTransaction(),
            waitingForSignatures.get(expectedKey),
            "the confirmed pegout is not waiting for signatures under the expected key"
        );

        Collection<Entry> stillWaitingForConfirmations = pegoutsWaitingForConfirmations();
        assertFalse(
            stillWaitingForConfirmations.contains(confirmedPegout),
            "the confirmed pegout must leave the waiting for confirmations set"
        );
        assertEquals(pegouts.size() - 1, stillWaitingForConfirmations.size());
    }

    private void assertNoPegoutWasConfirmed(List<Entry> pegouts) throws IOException {
        assertTrue(pegoutsWaitingForSignatures().isEmpty());
        assertTrue(pegoutsWaitingForConfirmations().containsAll(pegouts));
        assertEquals(pegouts.size(), pegoutsWaitingForConfirmations().size());
        assertNoPegoutConfirmedEventWasLogged(pegouts);
    }

    private void assertPegoutConfirmedEventWasLogged(Entry pegout) {
        Sha256Hash btcTxHash = pegout.getBtcTransaction().getHash();
        Optional<LogInfo> pegoutConfirmedLog = getLogsTopics(logs, pegoutConfirmedTopics(pegout));
        assertTrue(
            pegoutConfirmedLog.isPresent(),
            "no pegout confirmed event was logged for btc tx " + btcTxHash
        );

        byte[] expectedData = getEncodedData(PEGOUT_CONFIRMED_EVENT, pegout.getPegoutCreationRskBlockNumber());
        assertArrayEquals(
            expectedData,
            pegoutConfirmedLog.get().getData(),
            "the event must carry the pegout creation block number"
        );
    }

    private void assertNoPegoutConfirmedEventWasLogged(Collection<Entry> pegouts) {
        pegouts.forEach(pegout -> assertTrue(
            getLogsTopics(logs, pegoutConfirmedTopics(pegout)).isEmpty(),
            "an unexpected pegout confirmed event was logged for btc tx " + pegout.getBtcTransaction().getHash()
        ));
    }

    private static List<DataWord> pegoutConfirmedTopics(Entry pegout) {
        return getEncodedTopics(PEGOUT_CONFIRMED_EVENT, pegout.getBtcTransaction().getHash().getBytes());
    }
}
