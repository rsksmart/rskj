package co.rsk.peg;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionOutput;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.peg.bitcoin.BitcoinUtils;
import co.rsk.test.builders.BridgeSupportBuilder;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Transaction;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BridgeSupportSigHashTest {

    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters btcMainnetParams = bridgeMainnetConstants.getBtcParams();

    private static Stream<Arguments> pegoutTxIndexArgsProvider() {
        return Stream.of(
            Arguments.of(ActivationConfigsForTest.fingerroot500().forBlock(0)),
            Arguments.of(ActivationConfigsForTest.tbd600().forBlock(0))
        );
    }
    @ParameterizedTest
    @MethodSource("pegoutTxIndexArgsProvider")
    void test_pegoutTxIndex_when_pegout_batch_is_created(ActivationConfig.ForBlock activations) throws IOException {
        // Arrange
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        List<UTXO> fedUTXOs = PegTestUtils.createUTXOs(10, bridgeMainnetConstants.getGenesisFederation().getAddress());
        when(provider.getNewFederationBtcUTXOs())
            .thenReturn(fedUTXOs);

        when(provider.getReleaseRequestQueue())
            .thenReturn(new ReleaseRequestQueue(PegTestUtils.createReleaseRequestQueueEntries(3)));

        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = new PegoutsWaitingForConfirmations(new HashSet<>());
        when(provider.getPegoutsWaitingForConfirmations())
            .thenReturn(pegoutsWaitingForConfirmations);

        when(provider.getPegoutsWaitingForSignatures())
            .thenReturn(new TreeMap<>());

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
            verify(provider, times(1)).setPegoutTxSigHash(
                firstInputSigHash.get()
            );
        } else {
            verify(provider, never()).hasPegoutTxSigHash(
                any()
            );
            // verify no sigHash was added to sigHashes list before RSKIP379
            verify(provider, never()).setPegoutTxSigHash(
                any()
            );
        }
    }

    @ParameterizedTest
    @MethodSource("pegoutTxIndexArgsProvider")
    void test_pegoutTxIndex_when_migration_tx_is_created(ActivationConfig.ForBlock activations) throws IOException {
        // Arrange
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        when(provider.getFeePerKb())
            .thenReturn(Coin.MILLICOIN);

        when(provider.getReleaseRequestQueue())
            .thenReturn(new ReleaseRequestQueue(Collections.emptyList()));

        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = new PegoutsWaitingForConfirmations(new HashSet<>());
        when(provider.getPegoutsWaitingForConfirmations())
            .thenReturn(pegoutsWaitingForConfirmations);

        when(provider.getPegoutsWaitingForSignatures())
            .thenReturn(new TreeMap<>());

        Federation oldFederation = bridgeMainnetConstants.getGenesisFederation();
        Federation newFederation = new Federation(
            FederationTestUtils.getFederationMembers(1),
            Instant.EPOCH,
            5L,
            btcMainnetParams
        );
        when(provider.getOldFederation())
            .thenReturn(oldFederation);
        when(provider.getNewFederation())
            .thenReturn(newFederation);

        // Utxos to migrate
        List<UTXO> utxos = PegTestUtils.createUTXOs(10, oldFederation.getAddress());
        when(provider.getOldFederationBtcUTXOs())
            .thenReturn(utxos);

        // Advance blockchain to migration phase
        long migrationAge = bridgeMainnetConstants.getFederationActivationAge(activations) +
                                bridgeMainnetConstants.getFundsMigrationAgeSinceActivationBegin() + 6;
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
            PegoutsWaitingForConfirmations.Entry migrationTx = pegoutsWaitingForConfirmations.getEntries().stream().findFirst().get();
            Optional<Sha256Hash> firstInputSigHash = BitcoinUtils.getFirstInputSigHash(migrationTx.getBtcTransaction());
            assertTrue(firstInputSigHash.isPresent());
            verify(provider, times(1)).setPegoutTxSigHash(
                firstInputSigHash.get()
            );
        } else {
            verify(provider, never()).hasPegoutTxSigHash(
                any()
            );
            // verify no sigHash was added to sigHashes list before RSKIP379
            verify(provider, never()).setPegoutTxSigHash(
                any()
            );
        }
    }

    @ParameterizedTest
    @MethodSource("pegoutTxIndexArgsProvider")
    void test_pegoutTxIndex_when_migration_and_pegout_batch_tx_are_created(ActivationConfig.ForBlock activations) throws IOException {
        // Arrange
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        when(provider.getFeePerKb())
            .thenReturn(Coin.MILLICOIN);

        when(provider.getReleaseRequestQueue())
            .thenReturn(new ReleaseRequestQueue(PegTestUtils.createReleaseRequestQueueEntries(3)));

        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = new PegoutsWaitingForConfirmations(new HashSet<>());
        when(provider.getPegoutsWaitingForConfirmations())
            .thenReturn(pegoutsWaitingForConfirmations);

        when(provider.getPegoutsWaitingForSignatures())
            .thenReturn(new TreeMap<>());

        Federation oldFederation = bridgeMainnetConstants.getGenesisFederation();
        Federation newFederation = new Federation(
            FederationTestUtils.getFederationMembers(1),
            Instant.EPOCH,
            5L,
            btcMainnetParams
        );
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

        // Advance blockchain to migration phase
        long migrationAge = bridgeMainnetConstants.getFederationActivationAge(activations) +
                                bridgeMainnetConstants.getFundsMigrationAgeSinceActivationBegin() + 6;

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

        // Assert one pegout was added to pegoutsWaitingForConfirmations from the creation of a pegout batch
        assertEquals(2, pegoutsWaitingForConfirmations.getEntries().size());

        if (activations.isActive(ConsensusRule.RSKIP379)){
            PegoutsWaitingForConfirmations.Entry migrationTx = null;
            PegoutsWaitingForConfirmations.Entry pegoutBatchTx = null;

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
                    migrationTx = entry;
                } else {
                    pegoutBatchTx = entry;
                }
            }

            Optional<Sha256Hash> migrationTxSigHash = BitcoinUtils.getFirstInputSigHash(migrationTx.getBtcTransaction());
            assertTrue(migrationTxSigHash.isPresent());

            verify(provider, times(1)).setPegoutTxSigHash(
                migrationTxSigHash.get()
            );

            Optional<Sha256Hash> pegoutBatchTxSigHash = BitcoinUtils.getFirstInputSigHash(pegoutBatchTx.getBtcTransaction());
            assertTrue(pegoutBatchTxSigHash.isPresent());

            verify(provider, times(1)).setPegoutTxSigHash(
                pegoutBatchTxSigHash.get()
            );
        } else {
            verify(provider, never()).hasPegoutTxSigHash(
                any()
            );
            // verify no sigHash was added to sigHashes list before RSKIP379
            verify(provider, never()).setPegoutTxSigHash(
                any()
            );
        }
    }
}
