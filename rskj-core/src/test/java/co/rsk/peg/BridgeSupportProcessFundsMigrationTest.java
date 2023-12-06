package co.rsk.peg;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.config.BridgeTestNetConstants;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationFactory;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.test.builders.BridgeSupportBuilder;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Transaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static co.rsk.peg.PegTestUtils.BTC_TX_LEGACY_VERSION;
import static co.rsk.peg.ReleaseTransactionBuilder.BTC_TX_VERSION_2;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BridgeSupportProcessFundsMigrationTest {

    private static Stream<Arguments> processFundMigrationArgsProvider() {
        BridgeMainNetConstants bridgeMainNetConstants = BridgeMainNetConstants.getInstance();
        BridgeTestNetConstants bridgeTestNetConstants = BridgeTestNetConstants.getInstance();

        ActivationConfig.ForBlock wasabiActivations = ActivationConfigsForTest.wasabi100().forBlock(0);
        Stream<Arguments> wasabiTests = Stream.of(
            Arguments.of(bridgeTestNetConstants, wasabiActivations, false),
            Arguments.of(bridgeTestNetConstants, wasabiActivations, true),
            Arguments.of(bridgeMainNetConstants, wasabiActivations, false),
            Arguments.of(bridgeMainNetConstants, wasabiActivations, true)
        );

        ActivationConfig.ForBlock papyrusActivations = ActivationConfigsForTest.papyrus200().forBlock(0);
        Stream<Arguments> papyrusTests = Stream.of(
            Arguments.of(bridgeTestNetConstants, papyrusActivations, false),
            Arguments.of(bridgeTestNetConstants, papyrusActivations, true),
            Arguments.of(bridgeMainNetConstants, papyrusActivations, false),
            Arguments.of(bridgeMainNetConstants, papyrusActivations, true)
        );

        ActivationConfig.ForBlock irisActivations = ActivationConfigsForTest.iris300().forBlock(0);
        Stream<Arguments> irisTests = Stream.of(
            Arguments.of(bridgeTestNetConstants, irisActivations, false),
            Arguments.of(bridgeTestNetConstants, irisActivations, true),
            Arguments.of(bridgeMainNetConstants, irisActivations, false),
            Arguments.of(bridgeMainNetConstants, irisActivations, true)
        );

        ActivationConfig.ForBlock hopActivations = ActivationConfigsForTest.hop400().forBlock(0);
        Stream<Arguments> hopTests = Stream.of(
            Arguments.of(bridgeTestNetConstants, hopActivations, false),
            Arguments.of(bridgeTestNetConstants, hopActivations, true),
            Arguments.of(bridgeMainNetConstants, hopActivations, false),
            Arguments.of(bridgeMainNetConstants, hopActivations, true)
        );


        ActivationConfig.ForBlock hop401Activations = ActivationConfigsForTest.hop401().forBlock(0);
        Stream<Arguments> hop401Tests = Stream.of(
            Arguments.of(bridgeTestNetConstants, hop401Activations, false),
            Arguments.of(bridgeTestNetConstants, hop401Activations, true),
            Arguments.of(bridgeMainNetConstants, hop401Activations, false),
            Arguments.of(bridgeMainNetConstants, hop401Activations, true)
        );

        ActivationConfig.ForBlock fingerrootActivations = ActivationConfigsForTest.fingerroot500().forBlock(0);
        Stream<Arguments> fingerrootTests = Stream.of(
            Arguments.of(bridgeTestNetConstants, fingerrootActivations, false),
            Arguments.of(bridgeTestNetConstants, fingerrootActivations, true),
            Arguments.of(bridgeMainNetConstants, fingerrootActivations, false),
            Arguments.of(bridgeMainNetConstants, fingerrootActivations, true)
        );

        ActivationConfig.ForBlock tbdActivations = ActivationConfigsForTest.arrowhead600().forBlock(0);
        Stream<Arguments> arrowheadTests = Stream.of(
            Arguments.of(bridgeTestNetConstants, tbdActivations, false),
            Arguments.of(bridgeTestNetConstants, tbdActivations, true),
            Arguments.of(bridgeMainNetConstants, tbdActivations, false),
            Arguments.of(bridgeMainNetConstants, tbdActivations, true)
        );

        return Stream.of(
            wasabiTests,
            papyrusTests,
            irisTests,
            hopTests,
            hop401Tests,
            fingerrootTests,
            arrowheadTests
        ).flatMap(Function.identity());
    }

    @ParameterizedTest
    @MethodSource("processFundMigrationArgsProvider")
    void test_processFundsMigration(
        BridgeConstants bridgeConstants,
        ActivationConfig.ForBlock activations,
        boolean inMigrationAge
    ) throws IOException {
        BridgeEventLogger bridgeEventLogger = mock(BridgeEventLogger.class);

        Federation oldFederation = bridgeConstants.getGenesisFederation();
        long federationActivationAge = bridgeConstants.getFederationActivationAge(activations);

        long federationCreationBlockNumber = 5L;
        long federationInMigrationAgeHeight = federationCreationBlockNumber + federationActivationAge +
            bridgeConstants.getFundsMigrationAgeSinceActivationBegin() + 1;
        long federationPastMigrationAgeHeight = federationCreationBlockNumber +
            federationActivationAge +
            bridgeConstants.getFundsMigrationAgeSinceActivationEnd(activations) + 1;

        Federation newFederation = FederationFactory.buildStandardMultiSigFederation(
            FederationTestUtils.getFederationMembers(1),
            Instant.EPOCH,
            federationCreationBlockNumber,
            bridgeConstants.getBtcParams()
        );

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));
        when(provider.getOldFederation()).thenReturn(oldFederation);
        when(provider.getNewFederation()).thenReturn(newFederation);

        BlockGenerator blockGenerator = new BlockGenerator();

        long updateCollectionsCallHeight = inMigrationAge ? federationInMigrationAgeHeight : federationPastMigrationAgeHeight;
        org.ethereum.core.Block rskCurrentBlock = blockGenerator.createBlock(updateCollectionsCallHeight, 1);

        BridgeSupport bridgeSupport = new BridgeSupportBuilder()
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .withEventLogger(bridgeEventLogger)
            .withExecutionBlock(rskCurrentBlock)
            .withActivations(activations)
            .build();

        List<UTXO> sufficientUTXOsForMigration = new ArrayList<>();
        sufficientUTXOsForMigration.add(PegTestUtils.createUTXO(
            0,
            0,
            Coin.COIN,
            oldFederation.getAddress()
        ));
        when(provider.getOldFederationBtcUTXOs()).thenReturn(sufficientUTXOsForMigration);

        Transaction updateCollectionsTx = buildUpdateCollectionsTransaction();
        bridgeSupport.updateCollections(updateCollectionsTx);

        Assertions.assertEquals(activations.isActive(ConsensusRule.RSKIP146)? 0 : 1, provider.getPegoutsWaitingForConfirmations().getEntriesWithoutHash().size());
        Assertions.assertEquals(activations.isActive(ConsensusRule.RSKIP146)? 1 : 0, provider.getPegoutsWaitingForConfirmations().getEntriesWithHash().size());

        Assertions.assertTrue(sufficientUTXOsForMigration.isEmpty());
        if (inMigrationAge){
            verify(provider, never()).setOldFederation(null);
        } else {
            verify(provider, times(1)).setOldFederation(null);
        }

        if (activations.isActive(ConsensusRule.RSKIP146)) {
            // Should have been logged with the migrated UTXO
            PegoutsWaitingForConfirmations.Entry entry = (PegoutsWaitingForConfirmations.Entry) provider.getPegoutsWaitingForConfirmations()
                .getEntriesWithHash()
                .toArray()[0];
            verify(bridgeEventLogger, times(1)).logReleaseBtcRequested(
                updateCollectionsTx.getHash().getBytes(),
                entry.getBtcTransaction(),
                Coin.COIN
            );

            if (activations.isActive(ConsensusRule.RSKIP376)){
                Assertions.assertEquals(BTC_TX_VERSION_2, entry.getBtcTransaction().getVersion());
            } else {
                Assertions.assertEquals(BTC_TX_LEGACY_VERSION, entry.getBtcTransaction().getVersion());
            }
        } else {
            verify(bridgeEventLogger, never()).logReleaseBtcRequested(
                any(byte[].class),
                any(BtcTransaction.class),
                any(Coin.class)
            );
        }
    }

    private Transaction buildUpdateCollectionsTransaction() {
        final String TO_ADDRESS = "0000000000000000000000000000000000000006";
        final BigInteger DUST_AMOUNT = new BigInteger("1");
        final BigInteger NONCE = new BigInteger("0");
        final BigInteger GAS_PRICE = new BigInteger("100");
        final BigInteger GAS_LIMIT = new BigInteger("1000");
        final String DATA = "80af2871";

        return Transaction
            .builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(Hex.decode(TO_ADDRESS))
            .data(Hex.decode(DATA))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(DUST_AMOUNT)
            .build();
    }
}
