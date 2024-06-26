package co.rsk.peg;

import static co.rsk.peg.bitcoin.BitcoinTestUtils.createUTXOs;
import static co.rsk.peg.bitcoin.UtxoUtils.extractOutpointValues;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.ReleaseRequestQueue.Entry;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.ErpFederation;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationArgs;
import co.rsk.peg.federation.FederationFactory;
import co.rsk.peg.federation.FederationTestUtils;
import co.rsk.peg.feeperkb.FeePerKbStorageProvider;
import co.rsk.peg.feeperkb.FeePerKbSupport;
import co.rsk.peg.feeperkb.FeePerKbSupportImpl;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.test.builders.BridgeSupportBuilder;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class BridgeSupportPegoutTransactionCreatedEventTest {

    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters btcMainnetParams = bridgeMainnetConstants.getBtcParams();
    private static final ErpFederation ERP_FEDERATION = FederationTestUtils.getErpFederation(btcMainnetParams);

    private BridgeStorageProvider provider;
    private FeePerKbSupport feePerKbSupport;
    private BridgeEventLogger eventLogger;
    private Block rskCurrentBlock;
    private Keccak256 pegoutCreationRskTxHash;
    private Transaction executionRskTx;

    @BeforeEach
    void init() throws IOException {
        List<UTXO> fedUTXOs = createUTXOs(
            10,
            ERP_FEDERATION.getAddress()
        );

        provider = mock(BridgeStorageProvider.class);
        when(provider.getPegoutsWaitingForSignatures()).thenReturn(new TreeMap<>());
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(new HashSet<>()));
        when(provider.getNewFederationBtcUTXOs()).thenReturn(fedUTXOs);
        when(provider.getNewFederation()).thenReturn(ERP_FEDERATION);

        FeePerKbStorageProvider feePerKbStorageProvider = mock(FeePerKbStorageProvider.class);
        when(feePerKbStorageProvider.getFeePerKb()).thenReturn(Optional.of(Coin.MILLICOIN));
        feePerKbSupport = new FeePerKbSupportImpl(
            bridgeMainnetConstants.getFeePerKbConstants(),
            feePerKbStorageProvider
        );

        BlockGenerator blockGenerator = new BlockGenerator();
        rskCurrentBlock = blockGenerator.createBlock(ERP_FEDERATION.getCreationBlockNumber(), 1);

        pegoutCreationRskTxHash = PegTestUtils.createHash3(1);
        executionRskTx = mock(Transaction.class);
        when(executionRskTx.getHash()).thenReturn(pegoutCreationRskTxHash);

        eventLogger = mock(BridgeEventLogger.class);
    }

    private static Stream<Arguments> activationsProvider() {
        return Stream.of(
            Arguments.of(ActivationConfigsForTest.arrowhead600().forBlock(0)),
            Arguments.of(ActivationConfigsForTest.lovell700().forBlock(0))
        );
    }

    @ParameterizedTest
    @MethodSource("activationsProvider")
    void updateCollections_whenPegoutBatchIsCreated_shouldLogPegoutTransactionCreatedEvent(ActivationConfig.ForBlock activations) throws IOException {
        // Arrange
        List<Entry> pegoutRequests = PegTestUtils.createReleaseRequestQueueEntries(3);
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(pegoutRequests));

        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = provider.getPegoutsWaitingForConfirmations();

        BridgeSupport bridgeSupport = new BridgeSupportBuilder()
            .withBridgeConstants(bridgeMainnetConstants)
            .withProvider(provider)
            .withEventLogger(eventLogger)
            .withExecutionBlock(rskCurrentBlock)
            .withActivations(activations)
            .withFeePerKbSupport(feePerKbSupport)
            .build();

        // Act
        bridgeSupport.updateCollections(executionRskTx);

        // Assertions
        // Assert one pegout tx was added to pegoutsWaitingForConfirmations from the creation of a pegout batch
        assertEquals(1, pegoutsWaitingForConfirmations.getEntries().size());

        PegoutsWaitingForConfirmations.Entry pegoutEntry = pegoutsWaitingForConfirmations.getEntries().stream().findFirst().get();
        BtcTransaction pegoutBatchTransaction = pegoutEntry.getBtcTransaction();
        Sha256Hash pegoutTxHash = pegoutBatchTransaction.getHash();
        List<Coin> outpointValues = extractOutpointValues(pegoutBatchTransaction);
        List<Keccak256> pegoutRequestRskTxHashes = pegoutRequests.stream().map(Entry::getRskTxHash).collect(Collectors.toList());
        Coin totalTransactionAmount = pegoutRequests.stream().map(Entry::getAmount).reduce(Coin.ZERO, Coin::add);

        verify(eventLogger, times(1)).logBatchPegoutCreated(pegoutTxHash, pegoutRequestRskTxHashes);
        verify(eventLogger, times(1)).logReleaseBtcRequested(pegoutCreationRskTxHash.getBytes(), pegoutBatchTransaction, totalTransactionAmount);

        if (activations.isActive(ConsensusRule.RSKIP428)){
            verify(eventLogger, times(1)).logPegoutTransactionCreated(pegoutTxHash, outpointValues);
        } else {
            verify(eventLogger, never()).logPegoutTransactionCreated(any(), any());
        }
    }

    @ParameterizedTest
    @MethodSource("activationsProvider")
    void updateCollections_whenPegoutMigrationIsCreated_shouldLogPegoutTransactionCreatedEvent(ActivationConfig.ForBlock activations) throws IOException {
        // Arrange
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Collections.emptyList()));

        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = provider.getPegoutsWaitingForConfirmations();

        Federation oldFederation = ERP_FEDERATION;
        when(provider.getOldFederation()).thenReturn(oldFederation);

        long newFedCreationBlockNumber = 5L;
        FederationArgs newFederationArgs = new FederationArgs(
            FederationTestUtils.getFederationMembers(1),
            Instant.EPOCH,
            newFedCreationBlockNumber,
            btcMainnetParams
        );
        Federation newFederation = FederationFactory.buildStandardMultiSigFederation(newFederationArgs);

        when(provider.getNewFederation()).thenReturn(newFederation);

        // Utxos to migrate
        List<UTXO> utxosToMigrate = createUTXOs(10, oldFederation.getAddress());
        Coin totalTransactionInputAmount = utxosToMigrate.stream().map(UTXO::getValue).reduce(Coin.ZERO, Coin::add);
        when(provider.getOldFederationBtcUTXOs()).thenReturn(utxosToMigrate);

        // Advance blockchain to migration phase. Migration phase starts 1 block after migration age is reached.
        long migrationAge = bridgeMainnetConstants.getFederationActivationAge(activations) +
            bridgeMainnetConstants.getFundsMigrationAgeSinceActivationBegin() +
            newFedCreationBlockNumber + 1;

        BlockGenerator blockGenerator = new BlockGenerator();
        rskCurrentBlock = blockGenerator.createBlock(migrationAge, 1);

        BridgeSupport bridgeSupport = new BridgeSupportBuilder()
            .withBridgeConstants(bridgeMainnetConstants)
            .withProvider(provider)
            .withEventLogger(eventLogger)
            .withExecutionBlock(rskCurrentBlock)
            .withActivations(activations)
            .withFeePerKbSupport(feePerKbSupport)
            .build();

        // Act
        bridgeSupport.updateCollections(executionRskTx);

        // Assertions

        // Assert one migration tx was added to pegoutsWaitingForConfirmations from the creation of a pegout batch
        assertEquals(1, pegoutsWaitingForConfirmations.getEntries().size());

        PegoutsWaitingForConfirmations.Entry pegoutEntry = pegoutsWaitingForConfirmations.
            getEntries().
            stream().
            findFirst().
            get();

        BtcTransaction migrationTransaction = pegoutEntry.getBtcTransaction();
        Sha256Hash btcTxHash = migrationTransaction.getHash();

        List<Coin> outpointValues = extractOutpointValues(migrationTransaction);

        verify(eventLogger, never()).logBatchPegoutCreated(any(), any());
        verify(eventLogger, times(1)).logReleaseBtcRequested(pegoutCreationRskTxHash.getBytes(), migrationTransaction, totalTransactionInputAmount);

        if (activations.isActive(ConsensusRule.RSKIP428)){
            verify(eventLogger, times(1)).logPegoutTransactionCreated(btcTxHash, outpointValues);
        } else {
            verify(eventLogger, never()).logPegoutTransactionCreated(any(), any());
        }
    }

    @ParameterizedTest
    @MethodSource("activationsProvider")
    void updateCollections_whenPegoutMigrationAndBatchAreCreated_shouldLogPegoutTransactionCreatedEvent(ActivationConfig.ForBlock activations) throws IOException {
        // Arrange
        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = provider.getPegoutsWaitingForConfirmations();

        List<Entry> pegoutRequests = PegTestUtils.createReleaseRequestQueueEntries(3);
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(pegoutRequests));

        Federation oldFederation = ERP_FEDERATION;
        when(provider.getOldFederation()).thenReturn(oldFederation);

        long newFedCreationBlockNumber = 5L;
        FederationArgs newFederationArgs = new FederationArgs(
            FederationTestUtils.getFederationMembers(1),
            Instant.EPOCH,
            newFedCreationBlockNumber,
            btcMainnetParams
        );
        Federation newFederation = FederationFactory.buildStandardMultiSigFederation(newFederationArgs);

        when(provider.getNewFederation()).thenReturn(newFederation);

        // Utxos to migrate
        List<UTXO> utxosToMigrate = createUTXOs(10, oldFederation.getAddress());
        when(provider.getOldFederationBtcUTXOs()).thenReturn(utxosToMigrate);
        Coin migrationTotalAmount = utxosToMigrate.stream().map(UTXO::getValue).reduce(Coin.ZERO, Coin::add);

        List<UTXO> utxosNewFederation = createUTXOs(10, newFederation.getAddress());
        when(provider.getNewFederationBtcUTXOs()).thenReturn(utxosNewFederation);

        // Advance blockchain to migration phase. Migration phase starts 1 block after migration age is reached.
        long migrationAge = bridgeMainnetConstants.getFederationActivationAge(activations) +
            bridgeMainnetConstants.getFundsMigrationAgeSinceActivationBegin() +
            newFedCreationBlockNumber + 1;

        BlockGenerator blockGenerator = new BlockGenerator();
        rskCurrentBlock = blockGenerator.createBlock(migrationAge, 1);

        BridgeSupport bridgeSupport = new BridgeSupportBuilder()
            .withBridgeConstants(bridgeMainnetConstants)
            .withProvider(provider)
            .withEventLogger(eventLogger)
            .withExecutionBlock(rskCurrentBlock)
            .withActivations(activations)
            .withFeePerKbSupport(feePerKbSupport)
            .build();

        Transaction rskTx = mock(Transaction.class);
        when(rskTx.getHash()).thenReturn(PegTestUtils.createHash3(1));

        // Act
        bridgeSupport.updateCollections(rskTx);

        // Assertions

        // Assert two pegouts was added to pegoutsWaitingForConfirmations from the creation of each pegout batch for the migration and pegout
        assertEquals(2, pegoutsWaitingForConfirmations.getEntries().size());

        Optional<PegoutsWaitingForConfirmations.Entry> migrationEntry = Optional.empty();
        Optional<PegoutsWaitingForConfirmations.Entry> pegoutEntry = Optional.empty();

        // Get new fed wallet to identify the migration tx
        Wallet fedWallet = BridgeUtils.getFederationNoSpendWallet(
            new Context(btcMainnetParams),
            newFederation,
            false,
            null
        );

        // If all outputs are sent to the active fed then it's the migration tx; if not, it's the peg-out batch
        for (PegoutsWaitingForConfirmations.Entry entry : pegoutsWaitingForConfirmations.getEntries()) {
            List<TransactionOutput> walletOutputs = entry.getBtcTransaction().getWalletOutputs(fedWallet);
            if (walletOutputs.size() == entry.getBtcTransaction().getOutputs().size()){
                migrationEntry = Optional.of(entry);
            } else {
                pegoutEntry = Optional.of(entry);
            }
        }
        assertTrue(migrationEntry.isPresent());
        assertTrue(pegoutEntry.isPresent());

        BtcTransaction pegoutBatchTx = pegoutEntry.get().getBtcTransaction();
        Keccak256 pegoutBatchCreationRskTxHash = pegoutEntry.get().getPegoutCreationRskTxHash();
        Sha256Hash pegoutBatchBtcTxHash = pegoutBatchTx.getHash();

        Coin pegoutBatchTotalAmount = pegoutRequests.stream().map(Entry::getAmount).reduce(Coin.ZERO, Coin::add);
        List<Coin> pegoutBatchTxOutpointValues = extractOutpointValues(pegoutBatchTx);

        List<Keccak256> pegoutRequestRskTxHashes = pegoutRequests.stream().map(Entry::getRskTxHash).collect(Collectors.toList());

        verify(eventLogger, times(1)).logBatchPegoutCreated(pegoutBatchBtcTxHash, pegoutRequestRskTxHashes);
        verify(eventLogger, times(1)).logReleaseBtcRequested(pegoutBatchCreationRskTxHash.getBytes(), pegoutBatchTx, pegoutBatchTotalAmount);

        Keccak256 migrationCreationRskTxHash = migrationEntry.get().getPegoutCreationRskTxHash();
        BtcTransaction migrationTx = migrationEntry.get().getBtcTransaction();
        Sha256Hash migrationTxHash = migrationTx.getHash();

        List<Coin> migrationTxOutpointValues = extractOutpointValues(migrationTx);

        verify(eventLogger, times(1)).logReleaseBtcRequested(migrationCreationRskTxHash.getBytes(), migrationTx, migrationTotalAmount);

        if (activations.isActive(ConsensusRule.RSKIP428)){
            verify(eventLogger, times(1)).logPegoutTransactionCreated(pegoutBatchBtcTxHash, pegoutBatchTxOutpointValues);
            verify(eventLogger, times(1)).logPegoutTransactionCreated(migrationTxHash, migrationTxOutpointValues);
        } else {
            verify(eventLogger, never()).logPegoutTransactionCreated(any(), any());
        }
    }
}
