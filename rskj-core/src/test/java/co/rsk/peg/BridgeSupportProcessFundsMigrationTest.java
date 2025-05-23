package co.rsk.peg;

import co.rsk.RskTestUtils;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.constants.BridgeTestNetConstants;
import co.rsk.peg.federation.*;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.feeperkb.FeePerKbSupport;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.test.builders.BridgeSupportBuilder;
import co.rsk.test.builders.FederationSupportBuilder;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        FederationConstants federationConstants = bridgeConstants.getFederationConstants();

        BridgeEventLogger bridgeEventLogger = mock(BridgeEventLogger.class);
        Federation oldFederation = FederationTestUtils.getGenesisFederation(bridgeConstants.getFederationConstants());
        long federationActivationAge = federationConstants.getFederationActivationAge(activations);

        long federationCreationBlockNumber = 5L;
        long federationInMigrationAgeHeight = federationCreationBlockNumber + federationActivationAge +
            federationConstants.getFundsMigrationAgeSinceActivationBegin() + 1;
        long federationPastMigrationAgeHeight = federationCreationBlockNumber +
            federationActivationAge +
            federationConstants.getFundsMigrationAgeSinceActivationEnd(activations) + 1;

        FederationArgs newFederationArgs = new FederationArgs(
            FederationTestUtils.getFederationMembers(1),
            Instant.EPOCH,
            federationCreationBlockNumber,
            bridgeConstants.getBtcParams()
        );
        Federation newFederation = FederationFactory.buildStandardMultiSigFederation(newFederationArgs);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));

        FederationStorageProvider federationStorageProvider = mock(FederationStorageProvider.class);
        when(federationStorageProvider.getOldFederation(federationConstants, activations)).thenReturn(oldFederation);
        when(federationStorageProvider.getNewFederation(federationConstants, activations)).thenReturn(newFederation);

        BlockGenerator blockGenerator = new BlockGenerator();

        long updateCollectionsCallHeight = inMigrationAge ? federationInMigrationAgeHeight : federationPastMigrationAgeHeight;
        org.ethereum.core.Block rskCurrentBlock = blockGenerator.createBlock(updateCollectionsCallHeight, 1);

        FeePerKbSupport feePerKbSupport = mock(FeePerKbSupport.class);
        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.MILLICOIN);

        FederationSupport federationSupport = FederationSupportBuilder.builder()
            .withFederationConstants(federationConstants)
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(rskCurrentBlock)
            .withActivations(activations)
            .build();

        BridgeSupport bridgeSupport = BridgeSupportBuilder.builder()
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .withEventLogger(bridgeEventLogger)
            .withExecutionBlock(rskCurrentBlock)
            .withActivations(activations)
            .withFeePerKbSupport(feePerKbSupport)
            .withFederationSupport(federationSupport)
            .build();

        List<UTXO> sufficientUTXOsForMigration = new ArrayList<>();
        sufficientUTXOsForMigration.add(PegTestUtils.createUTXO(
            0,
            0,
            Coin.COIN,
            oldFederation.getAddress()
        ));
        when(federationStorageProvider.getOldFederationBtcUTXOs()).thenReturn(sufficientUTXOsForMigration);

        Transaction updateCollectionsTx = buildUpdateCollectionsTransaction();
        ECKey senderKey = RskTestUtils.getEcKeyFromSeed("sender");
        updateCollectionsTx.sign(senderKey.getPrivKeyBytes());
        bridgeSupport.updateCollections(updateCollectionsTx);

        assertEquals(activations.isActive(ConsensusRule.RSKIP146)? 0 : 1, provider.getPegoutsWaitingForConfirmations().getEntriesWithoutHash().size());
        assertEquals(activations.isActive(ConsensusRule.RSKIP146)? 1 : 0, provider.getPegoutsWaitingForConfirmations().getEntriesWithHash().size());

        assertTrue(sufficientUTXOsForMigration.isEmpty());
        if (inMigrationAge){
            verify(federationStorageProvider, never()).setOldFederation(null);
        } else {
            verify(federationStorageProvider, times(1)).setOldFederation(null);
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
                assertEquals(BTC_TX_VERSION_2, entry.getBtcTransaction().getVersion());
            } else {
                assertEquals(BTC_TX_LEGACY_VERSION, entry.getBtcTransaction().getVersion());
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
        final BigInteger dustAmount = new BigInteger("1");
        final BigInteger nonce = new BigInteger("0");
        final BigInteger gasPrice = new BigInteger("100");
        final BigInteger gasLimit = new BigInteger("1000");
        final String DATA = "80af2871";

        return Transaction
            .builder()
            .nonce(nonce)
            .gasPrice(gasPrice)
            .gasLimit(gasLimit)
            .destination(Hex.decode(TO_ADDRESS))
            .data(Hex.decode(DATA))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(dustAmount)
            .build();
    }
}
