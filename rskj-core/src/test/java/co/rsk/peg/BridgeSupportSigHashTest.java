package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.peg.bitcoin.BitcoinUtils;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationArgs;
import co.rsk.peg.federation.FederationFactory;
import co.rsk.peg.federation.FederationTestUtils;
import co.rsk.test.builders.BridgeSupportBuilder;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BridgeSupportSigHashTest {

    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters btcMainnetParams = bridgeMainnetConstants.getBtcParams();

    private BridgeStorageProvider provider;

    @BeforeEach
    void init() throws IOException {
        provider = mock(BridgeStorageProvider.class);

        when(provider.getPegoutsWaitingForSignatures())
            .thenReturn(new TreeMap<>());

        when(provider.getPegoutsWaitingForConfirmations())
            .thenReturn(new PegoutsWaitingForConfirmations(new HashSet<>()));
    }

    private static Stream<Arguments> pegoutTxIndexArgsProvider() {
        return Stream.of(
            Arguments.of(ActivationConfigsForTest.fingerroot500().forBlock(0)),
            Arguments.of(ActivationConfigsForTest.arrowhead600().forBlock(0))
        );
    }
    @ParameterizedTest
    @MethodSource("pegoutTxIndexArgsProvider")
    void test_pegoutTxIndex_when_pegout_batch_is_created(ActivationConfig.ForBlock activations) throws IOException {
        Federation genesisFederation = FederationTestUtils.getGenesisFederation(bridgeMainnetConstants);
        Address federationAddress = genesisFederation.getAddress();
        // Arrange
        List<UTXO> fedUTXOs = PegTestUtils.createUTXOs(
            10,
            federationAddress
        );
        when(provider.getNewFederationBtcUTXOs())
            .thenReturn(fedUTXOs);

        when(provider.getReleaseRequestQueue())
            .thenReturn(new ReleaseRequestQueue(PegTestUtils.createReleaseRequestQueueEntries(3)));

        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = provider.getPegoutsWaitingForConfirmations();

        BridgeSupport bridgeSupport = new BridgeSupportBuilder()
            .withBridgeConstants(bridgeMainnetConstants)
            .withProvider(provider)
            .withActivations(activations)
            .build();

        // Act
        bridgeSupport.updateCollections(mock(Transaction.class));

        // Assertions

        // Assert one pegout tx was added to pegoutsWaitingForConfirmations from the creation of a pegout batch
        assertEquals(1, pegoutsWaitingForConfirmations.getEntries().size());

        if (activations.isActive(ConsensusRule.RSKIP379)){
            PegoutsWaitingForConfirmations.Entry pegoutBatchTx = pegoutsWaitingForConfirmations.getEntries().stream().findFirst().get();
            Optional<Sha256Hash> firstInputSigHash = BitcoinUtils.getFirstInputSigHash(pegoutBatchTx.getBtcTransaction());
            assertTrue(firstInputSigHash.isPresent());
            verify(provider, times(1)).setPegoutTxSigHash(firstInputSigHash.get());
        } else {
            verify(provider, never()).hasPegoutTxSigHash(any());
            // verify no sigHash was added to sigHashes list before RSKIP379
            verify(provider, never()).setPegoutTxSigHash(any());
        }
    }

    @ParameterizedTest
    @MethodSource("pegoutTxIndexArgsProvider")
    void test_pegoutTxIndex_when_migration_tx_is_created(ActivationConfig.ForBlock activations) throws IOException {
        // Arrange
        when(provider.getReleaseRequestQueue())
            .thenReturn(new ReleaseRequestQueue(Collections.emptyList()));

        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = provider.getPegoutsWaitingForConfirmations();

        Federation oldFederation = FederationTestUtils.getGenesisFederation(bridgeMainnetConstants);
        long newFedCreationBlockNumber = 5L;

        FederationArgs newFederationArgs = new FederationArgs(FederationTestUtils.getFederationMembers(1),
            Instant.EPOCH, newFedCreationBlockNumber, btcMainnetParams);
        Federation newFederation = FederationFactory.buildStandardMultiSigFederation(newFederationArgs);
        when(provider.getOldFederation())
            .thenReturn(oldFederation);
        when(provider.getNewFederation())
            .thenReturn(newFederation);

        // Utxos to migrate
        List<UTXO> utxos = PegTestUtils.createUTXOs(10, oldFederation.getAddress());
        when(provider.getOldFederationBtcUTXOs())
            .thenReturn(utxos);

        // Advance blockchain to migration phase. Migration phase starts 1 block after migration age is reached.
        long migrationAge = bridgeMainnetConstants.getFederationActivationAge(activations) +
                                bridgeMainnetConstants.getFundsMigrationAgeSinceActivationBegin() +
                                newFedCreationBlockNumber + 1;
        BlockGenerator blockGenerator = new BlockGenerator();
        org.ethereum.core.Block rskCurrentBlock = blockGenerator.createBlock(migrationAge, 1);

        BridgeSupport bridgeSupport = new BridgeSupportBuilder()
            .withBridgeConstants(bridgeMainnetConstants)
            .withProvider(provider)
            .withExecutionBlock(rskCurrentBlock)
            .withActivations(activations)
            .build();

        Transaction rskTx = mock(Transaction.class);
        when(rskTx.getHash()).thenReturn(PegTestUtils.createHash3(1));

        // Act
        bridgeSupport.updateCollections(rskTx);

        // Assertions

        // Assert one migration tx was added to pegoutsWaitingForConfirmations from the creation of a pegout batch
        assertEquals(1, pegoutsWaitingForConfirmations.getEntries().size());

        if (activations.isActive(ConsensusRule.RSKIP379)){
            PegoutsWaitingForConfirmations.Entry migrationTx = pegoutsWaitingForConfirmations.
                getEntries().
                stream().
                findFirst().
                get();
            Optional<Sha256Hash> firstInputSigHash = BitcoinUtils.getFirstInputSigHash(migrationTx.getBtcTransaction());
            assertTrue(firstInputSigHash.isPresent());
            verify(provider, times(1)).setPegoutTxSigHash(firstInputSigHash.get());
        } else {
            verify(provider, never()).hasPegoutTxSigHash(any());
            // verify no sigHash was added to sigHashes list before RSKIP379
            verify(provider, never()).setPegoutTxSigHash(any());
        }
    }

    @ParameterizedTest
    @MethodSource("pegoutTxIndexArgsProvider")
    void test_pegoutTxIndex_when_migration_and_pegout_batch_tx_are_created(ActivationConfig.ForBlock activations) throws IOException {
        // Arrange
        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = provider.getPegoutsWaitingForConfirmations();

        when(provider.getReleaseRequestQueue())
            .thenReturn(new ReleaseRequestQueue(PegTestUtils.createReleaseRequestQueueEntries(3)));

        Federation oldFederation = FederationTestUtils.getGenesisFederation(bridgeMainnetConstants);

        long newFedCreationBlockNumber = 5L;
        FederationArgs newFederationArgs = new FederationArgs(FederationTestUtils.getFederationMembers(1),
            Instant.EPOCH, newFedCreationBlockNumber, btcMainnetParams);
        Federation newFederation = FederationFactory.buildStandardMultiSigFederation(
            newFederationArgs);
        when(provider.getOldFederation())
            .thenReturn(oldFederation);
        when(provider.getNewFederation())
            .thenReturn(newFederation);

        // Utxos to migrate
        List<UTXO> utxos = PegTestUtils.createUTXOs(10, oldFederation.getAddress());
        when(provider.getOldFederationBtcUTXOs())
            .thenReturn(utxos);

        List<UTXO> utxosNew = PegTestUtils.createUTXOs(10, newFederation.getAddress());
        when(provider.getNewFederationBtcUTXOs())
            .thenReturn(utxosNew);

        // Advance blockchain to migration phase. Migration phase starts 1 block after migration age is reached.
        long migrationAge = bridgeMainnetConstants.getFederationActivationAge(activations) +
                                bridgeMainnetConstants.getFundsMigrationAgeSinceActivationBegin() +
                                newFedCreationBlockNumber + 1;

        BlockGenerator blockGenerator = new BlockGenerator();
        org.ethereum.core.Block rskCurrentBlock = blockGenerator.createBlock(migrationAge, 1);

        BridgeSupport bridgeSupport = new BridgeSupportBuilder()
            .withBridgeConstants(bridgeMainnetConstants)
            .withProvider(provider)
            .withExecutionBlock(rskCurrentBlock)
            .withActivations(activations)
            .build();

        Transaction rskTx = mock(Transaction.class);
        when(rskTx.getHash()).thenReturn(PegTestUtils.createHash3(1));

        // Act
        bridgeSupport.updateCollections(rskTx);

        // Assertions

        // Assert two pegouts was added to pegoutsWaitingForConfirmations from the creation of each pegout batch for the migration and pegout
        assertEquals(2, pegoutsWaitingForConfirmations.getEntries().size());

        if (activations.isActive(ConsensusRule.RSKIP379)){
            Optional<PegoutsWaitingForConfirmations.Entry> migrationTx = Optional.empty();
            Optional<PegoutsWaitingForConfirmations.Entry> pegoutBatchTx = Optional.empty();

            // Get new fed wallet to identify the migration tx
            Wallet newFedWallet = BridgeUtils.getFederationNoSpendWallet(
                new Context(btcMainnetParams),
                newFederation,
                false,
                null
            );

            // If all outputs are sent to the active fed then it's the migration tx; if not, it's the peg-out batch
            for (PegoutsWaitingForConfirmations.Entry entry : pegoutsWaitingForConfirmations.getEntries()) {
                List<TransactionOutput> walletOutputs = entry.getBtcTransaction().getWalletOutputs(newFedWallet);
                if (walletOutputs.size() == entry.getBtcTransaction().getOutputs().size()){
                    migrationTx = Optional.of(entry);
                } else {
                    pegoutBatchTx = Optional.of(entry);
                }
            }
            assertTrue(migrationTx.isPresent());
            assertTrue(pegoutBatchTx.isPresent());

            Optional<Sha256Hash> migrationTxSigHash = BitcoinUtils.getFirstInputSigHash(migrationTx.get().getBtcTransaction());
            assertTrue(migrationTxSigHash.isPresent());
            verify(provider, times(1)).setPegoutTxSigHash(migrationTxSigHash.get());

            Optional<Sha256Hash> pegoutBatchTxSigHash = BitcoinUtils.getFirstInputSigHash(pegoutBatchTx.get().getBtcTransaction());
            assertTrue(pegoutBatchTxSigHash.isPresent());
            verify(provider, times(1)).setPegoutTxSigHash(pegoutBatchTxSigHash.get());
        } else {
            verify(provider, never()).hasPegoutTxSigHash(any());
            // verify no sigHash was added to sigHashes list before RSKIP379
            verify(provider, never()).setPegoutTxSigHash(any());
        }
    }
}
