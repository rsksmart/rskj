package co.rsk.peg;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.config.BridgeTestNetConstants;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.bitcoin.BitcoinUtils;
import co.rsk.test.builders.BridgeSupportBuilder;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Transaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        SortedMap<Keccak256, BtcTransaction> pegoutWaitingForSignatures = new TreeMap<>();
        when(provider.getPegoutsWaitingForSignatures())
            .thenReturn(pegoutWaitingForSignatures);

        BridgeSupport bridgeSupport = new BridgeSupportBuilder()
            .withBridgeConstants(bridgeMainnetConstants)
            .withProvider(provider)
            .withActivations(activations)
            .build();

        // Act
        bridgeSupport.updateCollections(mock(Transaction.class));

        // Assert

        // Assert one pegout was added to pegoutsWaitingForConfirmations from the creation of a pegout batch
        assertEquals(1, pegoutsWaitingForConfirmations.getEntries().size());

        if (activations.isActive(ConsensusRule.RSKIP379)){
            PegoutsWaitingForConfirmations.Entry pegoutBatch = pegoutsWaitingForConfirmations.getEntries().stream().findFirst().get();
            verify(provider, times(1)).setPegoutTxSigHash(
                BitcoinUtils.getFirstInputSigHash(pegoutBatch.getBtcTransaction()).get()
            );
        } else {
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

        SortedMap<Keccak256, BtcTransaction> pegoutWaitingForSignatures = new TreeMap<>();
        when(provider.getPegoutsWaitingForSignatures())
            .thenReturn(pegoutWaitingForSignatures);

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

        // Assert

        // Assert one pegout was added to pegoutsWaitingForConfirmations from the creation of a pegout batch
        assertEquals(1, pegoutsWaitingForConfirmations.getEntries().size());

        if (activations.isActive(ConsensusRule.RSKIP379)){
            PegoutsWaitingForConfirmations.Entry pegoutBatch = pegoutsWaitingForConfirmations.getEntries().stream().findFirst().get();
            verify(provider, times(1)).setPegoutTxSigHash(
                BitcoinUtils.getFirstInputSigHash(pegoutBatch.getBtcTransaction()).get()
            );
        } else {
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

        SortedMap<Keccak256, BtcTransaction> pegoutWaitingForSignatures = new TreeMap<>();
        when(provider.getPegoutsWaitingForSignatures())
            .thenReturn(pegoutWaitingForSignatures);

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

        // Assert

        // Assert one pegout was added to pegoutsWaitingForConfirmations from the creation of a pegout batch
        assertEquals(2, pegoutsWaitingForConfirmations.getEntries().size());

        if (activations.isActive(ConsensusRule.RSKIP379)){
            PegoutsWaitingForConfirmations.Entry pegoutBatch = pegoutsWaitingForConfirmations.getEntries().stream().findFirst().get();
            verify(provider, times(2)).setPegoutTxSigHash(
                any(Sha256Hash.class)
            );
        } else {
            // verify no sigHash was added to sigHashes list before RSKIP379
            verify(provider, never()).setPegoutTxSigHash(
                any()
            );
        }
    }
}
