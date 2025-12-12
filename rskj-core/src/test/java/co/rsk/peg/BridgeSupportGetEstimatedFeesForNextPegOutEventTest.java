package co.rsk.peg;

import static co.rsk.peg.PegTestUtils.createUTXO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.constants.BridgeRegTestConstants;
import co.rsk.peg.federation.ErpFederation;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationArgs;
import co.rsk.peg.federation.FederationFactory;
import co.rsk.peg.federation.FederationMember;
import co.rsk.peg.federation.FederationStorageProvider;
import co.rsk.peg.federation.FederationTestUtils;
import co.rsk.peg.federation.P2shErpFederationBuilder;
import co.rsk.peg.federation.P2shP2wshErpFederationBuilder;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.feeperkb.FeePerKbSupport;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class BridgeSupportGetEstimatedFeesForNextPegOutEventTest {
    private static Stream<Arguments> getEstimatedFeesForNextPegOutEventArgsProvider_pre_RSKIP271(
        BridgeConstants bridgeConstants) {
        List<FederationMember> members = FederationMember.getFederationMembersFromKeys(
            PegTestUtils.createRandomBtcECKeys(7)
        );

        FederationConstants federationConstants = bridgeConstants.getFederationConstants();
        Federation standardFederation = FederationTestUtils.getGenesisFederation(federationConstants);

        ActivationConfig.ForBlock irisActivations = ActivationConfigsForTest.iris300().forBlock(0L);

        FederationArgs p2shFedArgs = new FederationArgs(members, Instant.now(), 1L, bridgeConstants.getBtcParams());
        Federation p2shFed =
            FederationFactory.buildP2shErpFederation(p2shFedArgs, federationConstants.getErpFedPubKeysList(), federationConstants.getErpFedActivationDelay());

        return Stream.of(
            // active fed is standard and pegoutRequestsCount is equal to zero
            Arguments.of(
                irisActivations,
                standardFederation,
                0,
                Coin.valueOf(0L)
            ),
            // active fed is standard and pegoutRequestsCount is equal to one
            Arguments.of(
                irisActivations,
                standardFederation,
                1,
                Coin.valueOf(0L)
            ),
            // active fed is standard and there are many pegout requests
            Arguments.of(
                irisActivations,
                standardFederation,
                150,
                Coin.valueOf(0L)
            ),
            // active fed is p2sh and there are zero pegout requests
            Arguments.of(
                irisActivations,
                p2shFed,
                0,
                Coin.valueOf(0L)
            ),
            // active fed is p2sh and there is one pegout request
            Arguments.of(
                irisActivations,
                p2shFed,
                1,
                Coin.valueOf(0L)
            ),
            // active fed is p2sh and there are many pegout requests
            Arguments.of(
                irisActivations,
                p2shFed,
                150,
                Coin.valueOf(0L)
            )
        );
    }

    private static Stream<Arguments> getEstimatedFeesForNextPegOutEventArgsProvider_pre_RSKIP385(BridgeConstants bridgeConstants) {
        List<FederationMember> members = FederationMember.getFederationMembersFromKeys(
            PegTestUtils.createRandomBtcECKeys(7)
        );

        FederationConstants federationConstants = bridgeConstants.getFederationConstants();
        Federation standardFederation = FederationTestUtils.getGenesisFederation(federationConstants);

        ActivationConfig.ForBlock hopActivations = ActivationConfigsForTest.hop400().forBlock(0L);

        FederationArgs p2shFedArgs = new FederationArgs(members,
            Instant.now(),
            1L,
            bridgeConstants.getBtcParams()
        );
        Federation p2shFed = FederationFactory.buildP2shErpFederation(
            p2shFedArgs,
            federationConstants.getErpFedPubKeysList(),
            federationConstants.getErpFedActivationDelay()
        );

        return Stream.of(
            // active fed is standard and pegoutRequestsCount is equal to zero
            Arguments.of(
                hopActivations,
                standardFederation,
                0,
                Coin.valueOf(0L)
            ),
            // active fed is standard and pegoutRequestsCount is equal to one
            Arguments.of(
                hopActivations,
                standardFederation,
                1,
                Coin.valueOf(bridgeConstants instanceof BridgeMainNetConstants ? 237000L: 68600L)
            ),
            // active fed is standard and there are many pegout requests
            Arguments.of(
                hopActivations,
                standardFederation,
                150,
                Coin.valueOf(bridgeConstants instanceof BridgeMainNetConstants? 713800L: 545400L)
            ),
            // active fed is p2sh and there are zero pegout requests
            Arguments.of(
                hopActivations,
                p2shFed,
                0,
                Coin.valueOf(0L)
            ),
            // active fed is p2sh and there is one pegout request
            Arguments.of(
                hopActivations,
                p2shFed,
                1,
                Coin.valueOf(bridgeConstants instanceof BridgeMainNetConstants? 154600L: 154400L)
            ),
            // active fed is p2sh and there are many pegout requests
            Arguments.of(
                hopActivations,
                p2shFed,
                150,
                Coin.valueOf(bridgeConstants instanceof BridgeMainNetConstants? 631400L: 631200L)
            )
        );
    }

    private static Stream<Arguments> getEstimatedFeesForNextPegOutEventArgsProvider_post_RSKIP385(BridgeConstants bridgeConstants) {
        ActivationConfig.ForBlock fingerrootActivations = ActivationConfigsForTest.fingerroot500().forBlock(0L);

        FederationConstants federationConstants = bridgeConstants.getFederationConstants();
        Federation standardFederation = FederationTestUtils.getGenesisFederation(federationConstants);
        List<FederationMember> members = FederationMember.getFederationMembersFromKeys(
            PegTestUtils.createRandomBtcECKeys(7)
        );

        FederationArgs p2shFedArgs = new FederationArgs(members,
            Instant.now(),
            1L,
            bridgeConstants.getBtcParams()
        );
        ErpFederation p2shFed =
            FederationFactory.buildP2shErpFederation(p2shFedArgs, federationConstants.getErpFedPubKeysList(), federationConstants.getErpFedActivationDelay());

        return Stream.of(
            // active fed is standard and pegoutRequestsCount is equal to zero
            Arguments.of(
                fingerrootActivations,
                standardFederation,
                0,
                Coin.valueOf(bridgeConstants instanceof BridgeMainNetConstants? 233800L: 65400L)
            ),
            // active fed is standard and pegoutRequestsCount is equal to one
            Arguments.of(
                fingerrootActivations,
                standardFederation,
                1,
                Coin.valueOf(bridgeConstants instanceof BridgeMainNetConstants? 237000L: 68600L)
            ),
            // active fed is standard and there are many pegout requests
            Arguments.of(
                fingerrootActivations,
                standardFederation,
                150,
                Coin.valueOf(bridgeConstants instanceof BridgeMainNetConstants? 713800L: 545400L)
            ),
            // active fed is p2sh and there are zero pegout requests
            Arguments.of(
                fingerrootActivations,
                p2shFed,
                0,
                Coin.valueOf(bridgeConstants instanceof BridgeMainNetConstants? 151400L: 151200L)
            ),
            // active fed is p2sh and there is one pegout request
            Arguments.of(
                fingerrootActivations,
                p2shFed,
                1,
                Coin.valueOf(bridgeConstants instanceof BridgeMainNetConstants? 154600L: 154400L)
            ),
            // active fed is p2sh and there are many pegout requests
            Arguments.of(
                fingerrootActivations,
                p2shFed,
                150,
                Coin.valueOf(bridgeConstants instanceof BridgeMainNetConstants? 631400L: 631200L)
            )
        );
    }

    private static Stream<Arguments> getEstimatedFeesForNextPegOutEventArgsProvider_forSegwit() {
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(0L);
        BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();
        NetworkParameters networkParameters = bridgeConstants.getBtcParams();

        FederationConstants federationConstants = bridgeConstants.getFederationConstants();
        List<BtcECKey> erpFedPubKeys = federationConstants.getErpFedPubKeysList();
        long creationBlockNumber = 1L;
        Instant creationTime = Instant.now();
        long activationDelay = federationConstants.getErpFedActivationDelay();

        ErpFederation p2shFed = P2shErpFederationBuilder.builder()
            .withCreationTime(creationTime)
            .withCreationBlockNumber(creationBlockNumber)
            .withNetworkParameters(networkParameters)
            .withErpPublicKeys(erpFedPubKeys)
            .withErpActivationDelay(activationDelay)
            .build();

        ErpFederation p2shP2wshFed = P2shP2wshErpFederationBuilder.builder()
            .withCreationTime(Instant.now())
            .withCreationBlockNumber(1L)
            .withNetworkParameters(bridgeConstants.getBtcParams())
            .withErpPublicKeys(erpFedPubKeys)
            .withErpActivationDelay(federationConstants.getErpFedActivationDelay())
            .build();

        Address pegoutDestinationAddress1 = BitcoinTestUtils.createP2PKHAddress(networkParameters, "address1");
        Address pegoutDestinationAddress2 = BitcoinTestUtils.createP2PKHAddress(networkParameters, "address2");
        Address pegoutDestinationAddress3 = BitcoinTestUtils.createP2PKHAddress(networkParameters, "address3");
        Address pegoutDestinationAddress4 = BitcoinTestUtils.createP2PKHAddress(networkParameters, "address4");
        Coin minPegoutValue = bridgeConstants.getMinimumPegoutTxValue();

        ReleaseRequestQueue.Entry pegoutRequest1 = new ReleaseRequestQueue.Entry(pegoutDestinationAddress1, minPegoutValue);
        ReleaseRequestQueue.Entry pegoutRequest2 = new ReleaseRequestQueue.Entry(pegoutDestinationAddress2, minPegoutValue.add(Coin.valueOf(1000)));
        ReleaseRequestQueue.Entry pegoutRequest3 = new ReleaseRequestQueue.Entry(pegoutDestinationAddress3, minPegoutValue.add(Coin.valueOf(2000)));
        ReleaseRequestQueue.Entry bigPegoutRequest = new ReleaseRequestQueue.Entry(pegoutDestinationAddress4, Coin.COIN.multiply(10));

        UTXO p2shFedUtxo1 = createUTXO(Coin.valueOf(8, 0), p2shFed.getAddress());
        UTXO p2shFedBigUtxoUtxo = createUTXO(Coin.valueOf(13, 0), p2shFed.getAddress());

        UTXO p2shP2wshFedUtxo1 = createUTXO(Coin.valueOf(8, 0), p2shP2wshFed.getAddress());
        UTXO p2shP2wshFedBigUtxo = createUTXO(Coin.valueOf(13, 0), p2shP2wshFed.getAddress());

        return Stream.of(
            // active fed is p2sh and there are 0 pegout requests
            Arguments.of(
                activations,
                p2shFed,
                Coin.valueOf(9490L),
                List.of(),
                List.of(p2shFedUtxo1)
            ),
            // active fed is p2sh and there are 1 pegout requests
            // when there are no utxos available, the old logic should be executed and return the expected fee
            Arguments.of(
                activations,
                p2shFed,
                Coin.valueOf(17940L),
                List.of(pegoutRequest1),
                List.of()
            ),
            // active fed is p2sh and there is 1 pegout request
            // when there are utxos available, the new logic is used.
            Arguments.of(
                activations,
                p2shFed,
                Coin.valueOf(9830L),
                List.of(pegoutRequest1),
                List.of(p2shFedUtxo1)
            ),
            // active fed is p2sh and there are 2 pegout requests
            Arguments.of(
                activations,
                p2shFed,
                Coin.valueOf(10170L),
                List.of(pegoutRequest1, pegoutRequest2),
                List.of(p2shFedUtxo1)
            ),
            // active fed is p2sh and there are 3 pegout requests
            Arguments.of(
                activations,
                p2shFed,
                Coin.valueOf(10510L),
                List.of(pegoutRequest1, pegoutRequest2, pegoutRequest3),
                List.of(p2shFedUtxo1)
            ),

            // 2 inputs
            // active fed is p2sh and there is 2 pegout requests
            Arguments.of(
                activations,
                p2shFed,
                Coin.valueOf(18900L),
                List.of(pegoutRequest1, bigPegoutRequest),
                List.of(p2shFedUtxo1, p2shFedBigUtxoUtxo)
            ),
            // active fed is p2sh and there are 2 pegout requests
            Arguments.of(
                activations,
                p2shFed,
                Coin.valueOf(19240L),
                List.of(pegoutRequest1, pegoutRequest2, bigPegoutRequest),
                List.of(p2shFedUtxo1, p2shFedBigUtxoUtxo)
            ),
            // active fed is p2sh and there are 3 pegout requests
            Arguments.of(
                activations,
                p2shFed,
                Coin.valueOf(19580L),
                List.of(pegoutRequest1, pegoutRequest2, pegoutRequest3, bigPegoutRequest),
                List.of(p2shFedUtxo1, p2shFedBigUtxoUtxo)
            ),
            // active fed is p2sh p2wsh and there are 0 pegout requests
            Arguments.of(
                activations,
                p2shP2wshFed,
                Coin.valueOf(5670L), // Savings: 59.75%
                List.of(),
                List.of(p2shP2wshFedUtxo1)
            ),
            // active fed is p2sh p2wsh and there is 1 pegout requests
            // when there are no utxos available, it will call `calculatePegoutTxSize` and return the expected value for a segwit federation
            Arguments.of(
                activations,
                p2shP2wshFed,
                Coin.valueOf(9580L),
                List.of(pegoutRequest1),
                List.of()
            ),
            // active fed is p2sh p2wsh and there is 1 pegout requests
            Arguments.of(
                activations,
                p2shP2wshFed,
                Coin.valueOf(6010L), // Savings: 61.12%
                List.of(pegoutRequest1),
                List.of(p2shP2wshFedUtxo1)
            ),
            // active fed is p2sh p2wsh and there are 2 pegout requests
            Arguments.of(
                activations,
                p2shP2wshFed,
                Coin.valueOf(6350L), // Savings: 62.42%
                List.of(pegoutRequest1, pegoutRequest2),
                List.of(p2shP2wshFedUtxo1)
            ),
            // active fed is p2sh p2wsh and there are 3 pegout requests
            Arguments.of(
                activations,
                p2shP2wshFed,
                Coin.valueOf(6690L), // Savings: 63.66%
                List.of(pegoutRequest1, pegoutRequest2, pegoutRequest3),
                List.of(p2shP2wshFedUtxo1)
            ),

            // 2 inputs
            // active fed is segwit and there is 1 pegout requests
            Arguments.of(
                activations,
                p2shP2wshFed,
                Coin.valueOf(11260L), // Savings: 59.58%
                List.of(pegoutRequest1, bigPegoutRequest),
                List.of(p2shP2wshFedUtxo1, p2shP2wshFedBigUtxo)
            ),
            // active fed is p2sh p2wsh and there are 2 pegout requests
            Arguments.of(
                activations,
                p2shP2wshFed,
                Coin.valueOf(11600L), // Savings: 60.29%
                List.of(pegoutRequest1, pegoutRequest2, bigPegoutRequest),
                List.of(p2shP2wshFedUtxo1, p2shP2wshFedBigUtxo)
            ),
            // active fed is p2sh p2wsh and there are 3 pegout requests
            Arguments.of(
                activations,
                p2shP2wshFed,
                Coin.valueOf(11940L), // Savings: 60.97%
                List.of(pegoutRequest1, pegoutRequest2, pegoutRequest3, bigPegoutRequest),
                List.of(p2shP2wshFedUtxo1, p2shP2wshFedBigUtxo)
            )
        );
    }

    private static Stream<Arguments> getEstimatedFeesForNextPegOutEventArgsProvider() {
        BridgeRegTestConstants bridgeConstantsRegtest = new BridgeRegTestConstants();

        Stream<Arguments> preRskip271RegTest = getEstimatedFeesForNextPegOutEventArgsProvider_pre_RSKIP271(bridgeConstantsRegtest);
        Stream<Arguments> preRskip385RegTest = getEstimatedFeesForNextPegOutEventArgsProvider_pre_RSKIP385(bridgeConstantsRegtest);
        Stream<Arguments> postRskip385Regtest = getEstimatedFeesForNextPegOutEventArgsProvider_post_RSKIP385(bridgeConstantsRegtest);

        BridgeMainNetConstants bridgeMainNetConstants = BridgeMainNetConstants.getInstance();

        Stream<Arguments> preRskip271Mainnet = getEstimatedFeesForNextPegOutEventArgsProvider_pre_RSKIP271(bridgeMainNetConstants);
        Stream<Arguments> preRskip385Mainnet = getEstimatedFeesForNextPegOutEventArgsProvider_pre_RSKIP385(bridgeMainNetConstants);
        Stream<Arguments> postRskip385Mainnet = getEstimatedFeesForNextPegOutEventArgsProvider_post_RSKIP385(bridgeMainNetConstants);

        return Stream.of(
            preRskip271RegTest,
            preRskip385RegTest,
            postRskip385Regtest,
            preRskip271Mainnet,
            preRskip385Mainnet,
            postRskip385Mainnet
        ).flatMap(Function.identity());
    }

    @ParameterizedTest
    @MethodSource("getEstimatedFeesForNextPegOutEventArgsProvider")
    void getEstimatedFeesForNextPegOutEvent(
        ActivationConfig.ForBlock activations,
        Federation federation,
        int pegoutRequestsCount,
        Coin expectedEstimatedFee
    ) throws IOException {
        // Arrange
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        FederationStorageProvider federationStorageProviderMock = mock(FederationStorageProvider.class);

        when(provider.getReleaseRequestQueueSize()).thenReturn(pegoutRequestsCount);
        when(federationStorageProviderMock.getNewFederation(any(), any())).thenReturn(federation);

        feePerKbSupport = mock(FeePerKbSupport.class);
        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.MILLICOIN);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withFederationStorageProvider(federationStorageProviderMock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withActivations(activations)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .build();

        // Act
        Coin estimatedFeesForNextPegOutEvent = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

        // Assert
        assertEquals(expectedEstimatedFee, estimatedFeesForNextPegOutEvent);
    }

    @ParameterizedTest
    @MethodSource("getEstimatedFeesForNextPegOutEventArgsProvider_forSegwit")
    void getEstimatedFeesForNextPegOutEvent_forSegwit(
        ActivationConfig.ForBlock activations,
        Federation federation,
        Coin expectedEstimatedFee,
        List<ReleaseRequestQueue.Entry> releaseRequestQueueEntries,
        List<UTXO> utxos
    ) throws IOException {
        // Arrange
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        FederationStorageProvider federationStorageProviderMock = mock(FederationStorageProvider.class);

        ReleaseRequestQueue releaseRequestQueue = new ReleaseRequestQueue(releaseRequestQueueEntries);

        when(provider.getReleaseRequestQueue()).thenReturn(releaseRequestQueue);
        when(federationStorageProviderMock.getNewFederation(any(), any())).thenReturn(federation);

        when(federationStorageProviderMock.getNewFederationBtcUTXOs(any(), any())).thenReturn(utxos);

        feePerKbSupport = mock(FeePerKbSupport.class);
        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.valueOf(10_000));

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withFederationStorageProvider(federationStorageProviderMock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withActivations(activations)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .withBridgeConstants(bridgeConstantsRegtest)
            .build();

        // Act
        Coin estimatedFeesForNextPegOutEvent = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

        // Assert
        assertEquals(expectedEstimatedFee, estimatedFeesForNextPegOutEvent);
    }
}
