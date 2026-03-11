package co.rsk.peg;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.peg.ReleaseRequestQueue.Entry;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.ErpFederation;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationSupport;
import co.rsk.peg.federation.FederationTestUtils;
import co.rsk.peg.federation.P2shErpFederationBuilder;
import co.rsk.peg.federation.P2shP2wshErpFederationBuilder;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.feeperkb.FeePerKbSupport;
import co.rsk.test.builders.BridgeSupportBuilder;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static co.rsk.peg.PegTestUtils.createUTXO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BridgeSupportGetEstimatedFeesForNextPegOutEventTest {

    private static final BridgeConstants BRIDGE_CONSTANTS = BridgeMainNetConstants.getInstance();
    private static final FederationConstants FEDERATION_CONSTANTS = BRIDGE_CONSTANTS.getFederationConstants();
    private static final NetworkParameters NETWORK_PARAMETERS = BRIDGE_CONSTANTS.getBtcParams();
    private static final ActivationConfig.ForBlock BEFORE_RSKIP271_ACTIVATIONS = ActivationConfigsForTest.iris300().forBlock(0L);
    private static final ActivationConfig.ForBlock POST_RSKIP271_PRE_RSKIP385_ACTIVATIONS = ActivationConfigsForTest.hop400().forBlock(0L);
    private static final ActivationConfig.ForBlock POST_RSKIP385_PRE_RSKIP305_ACTIVATIONS = ActivationConfigsForTest.fingerroot500().forBlock(0L);
    private static final ActivationConfig.ForBlock POST_RSKIP305_ACTIVATIONS = ActivationConfigsForTest.reed800().forBlock(0L);
    private static final Coin FEE_PER_KB = Coin.MILLICOIN;

    private static final Federation standardMultisigFederation = FederationTestUtils.getGenesisFederation(FEDERATION_CONSTANTS);
    private static final ErpFederation p2shErpFederation = P2shErpFederationBuilder.builder()
        .withNetworkParameters(NETWORK_PARAMETERS)
        .build();
    private static final ErpFederation p2shP2wshErpFederation = P2shP2wshErpFederationBuilder.builder()
        .withNetworkParameters(NETWORK_PARAMETERS)
        .build();

    private static final Address RECEIVER_1 = BitcoinTestUtils.createP2PKHAddress(NETWORK_PARAMETERS, "address1");
    private static final Address RECEIVER_2 = BitcoinTestUtils.createP2PKHAddress(NETWORK_PARAMETERS, "address2");
    private static final Address RECEIVER_3 = BitcoinTestUtils.createP2PKHAddress(NETWORK_PARAMETERS, "address3");
    private static final Address RECEIVER_4 = BitcoinTestUtils.createP2PKHAddress(NETWORK_PARAMETERS, "address4");
    private static final Entry MIN_PEGOUT_VALUE_REQUEST = new Entry(RECEIVER_1, BRIDGE_CONSTANTS.getMinimumPegoutTxValue());
    private static final Entry ONE_THOUSAND_SATS_REQUEST = new Entry(RECEIVER_2, BRIDGE_CONSTANTS.getMinimumPegoutTxValue().add(Coin.valueOf(1_000L)));
    private static final Entry TWO_THOUSANDS_SATS_REQUEST = new Entry(RECEIVER_3, BRIDGE_CONSTANTS.getMinimumPegoutTxValue().add(Coin.valueOf(2_000L)));
    private static final Entry BIG_REQUEST = new Entry(RECEIVER_4, Coin.COIN.multiply(10));

    private static final List<UTXO> STANDARD_MULTISIG_FED_SINGLE_INPUT_UTXOS = List.of(
        createUTXO(Coin.valueOf(8, 0), standardMultisigFederation.getAddress())
    );

    private static final List<UTXO> P2SH_SINGLE_INPUT_UTXOS = List.of(
        createUTXO(Coin.valueOf(8, 0), p2shErpFederation.getAddress())
    );
    private static final List<UTXO> P2SH_TWO_INPUT_UTXOS = List.of(
        createUTXO(Coin.valueOf(8, 0), p2shErpFederation.getAddress()),
        createUTXO(Coin.valueOf(13, 0), p2shErpFederation.getAddress())
    );
    private static final List<UTXO> P2SH_P2WSH_SINGLE_INPUT_UTXOS = List.of(
        createUTXO(Coin.valueOf(8, 0), p2shP2wshErpFederation.getAddress())
    );
    private static final List<UTXO> P2SH_P2WSH_TWO_INPUT_UTXOS = List.of(
        createUTXO(Coin.valueOf(8, 0), p2shP2wshErpFederation.getAddress()),
        createUTXO(Coin.valueOf(13, 0), p2shP2wshErpFederation.getAddress())
    );

    @Nested
    class PreRskip271 {
        private BridgeStorageProvider provider;
        private FederationSupport federationSupport;
        private FeePerKbSupport feePerKbSupport;

        @BeforeEach
        void setUp() throws IOException {
            provider = mock(BridgeStorageProvider.class);
            federationSupport = mock(FederationSupport.class);
            feePerKbSupport = mock(FeePerKbSupport.class);
            when(feePerKbSupport.getFeePerKb()).thenReturn(FEE_PER_KB);
            when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(List.of()));
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withStandardFederation_withZeroPegoutRequests_shouldReturnZeroFees() throws IOException {
            // Arrange
            when(provider.getReleaseRequestQueueSize()).thenReturn(0);
            when(federationSupport.getActiveFederation()).thenReturn(standardMultisigFederation);
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(STANDARD_MULTISIG_FED_SINGLE_INPUT_UTXOS);
            BridgeSupport bridgeSupport = buildBridgeSupport(
                BEFORE_RSKIP271_ACTIVATIONS,
                provider,
                federationSupport,
                feePerKbSupport
            );

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.ZERO, estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withStandardFederation_withOnePegoutRequest_shouldReturnZeroFees() throws IOException {
            // Arrange
            when(provider.getReleaseRequestQueueSize()).thenReturn(1);
            when(federationSupport.getActiveFederation()).thenReturn(standardMultisigFederation);
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(STANDARD_MULTISIG_FED_SINGLE_INPUT_UTXOS);
            BridgeSupport bridgeSupport = buildBridgeSupport(
                BEFORE_RSKIP271_ACTIVATIONS,
                provider,
                federationSupport,
                feePerKbSupport
            );

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.ZERO, estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withStandardFederation_withManyPegoutRequests_shouldReturnZeroFees() throws IOException {
            // Arrange
            when(provider.getReleaseRequestQueueSize()).thenReturn(150);
            when(federationSupport.getActiveFederation()).thenReturn(standardMultisigFederation);
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(STANDARD_MULTISIG_FED_SINGLE_INPUT_UTXOS);
            BridgeSupport bridgeSupport = buildBridgeSupport(
                BEFORE_RSKIP271_ACTIVATIONS,
                provider,
                federationSupport,
                feePerKbSupport
            );

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.ZERO, estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shErpFederation_withZeroPegoutRequests_shouldReturnZeroFees() throws IOException {
            // Arrange
            when(provider.getReleaseRequestQueueSize()).thenReturn(0);
            when(federationSupport.getActiveFederation()).thenReturn(p2shErpFederation);
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(STANDARD_MULTISIG_FED_SINGLE_INPUT_UTXOS);
            BridgeSupport bridgeSupport = buildBridgeSupport(
                BEFORE_RSKIP271_ACTIVATIONS,
                provider,
                federationSupport,
                feePerKbSupport
            );

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.ZERO, estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shErpFederation_withOnePegoutRequest_shouldReturnZeroFees() throws IOException {
            // Arrange
            when(provider.getReleaseRequestQueueSize()).thenReturn(1);
            when(federationSupport.getActiveFederation()).thenReturn(p2shErpFederation);
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(STANDARD_MULTISIG_FED_SINGLE_INPUT_UTXOS);
            BridgeSupport bridgeSupport = buildBridgeSupport(
                BEFORE_RSKIP271_ACTIVATIONS,
                provider,
                federationSupport,
                feePerKbSupport
            );

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.ZERO, estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shErpFederation_withManyPegoutRequests_shouldReturnZeroFees() throws IOException {
            // Arrange
            when(provider.getReleaseRequestQueueSize()).thenReturn(150);
            when(federationSupport.getActiveFederation()).thenReturn(p2shErpFederation);
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(STANDARD_MULTISIG_FED_SINGLE_INPUT_UTXOS);
            BridgeSupport bridgeSupport = buildBridgeSupport(
                BEFORE_RSKIP271_ACTIVATIONS,
                provider,
                federationSupport,
                feePerKbSupport
            );

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.ZERO, estimatedFeesForNextPegout);
        }
    }

    @Nested
    class PostRskip271PreRskip385 {
        private BridgeStorageProvider provider;
        private FederationSupport federationSupport;
        private FeePerKbSupport feePerKbSupport;

        @BeforeEach
        void setUp() throws IOException {
            provider = mock(BridgeStorageProvider.class);
            federationSupport = mock(FederationSupport.class);
            feePerKbSupport = mock(FeePerKbSupport.class);
            when(feePerKbSupport.getFeePerKb()).thenReturn(FEE_PER_KB);
            when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(List.of()));
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withStandardFederation_withZeroPegoutRequests_shouldEstimateZeroFees() throws IOException {
            // Arrange
            when(provider.getReleaseRequestQueueSize()).thenReturn(0);
            when(federationSupport.getActiveFederation()).thenReturn(standardMultisigFederation);
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(STANDARD_MULTISIG_FED_SINGLE_INPUT_UTXOS);
            BridgeSupport bridgeSupport = buildBridgeSupport(
                POST_RSKIP271_PRE_RSKIP385_ACTIVATIONS,
                provider,
                federationSupport,
                feePerKbSupport
            );

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.ZERO, estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withStandardFederation_withOnePegoutRequest_shouldEstimateFeesFromInputAndOutputCount() throws IOException {
            // Arrange
            when(provider.getReleaseRequestQueueSize()).thenReturn(1);
            when(federationSupport.getActiveFederation()).thenReturn(standardMultisigFederation);
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(STANDARD_MULTISIG_FED_SINGLE_INPUT_UTXOS);
            BridgeSupport bridgeSupport = buildBridgeSupport(
                POST_RSKIP271_PRE_RSKIP385_ACTIVATIONS,
                provider,
                federationSupport,
                feePerKbSupport
            );

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(237_000L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withStandardFederation_withManyPegoutRequests_shouldEstimateFeesFromInputAndOutputCount() throws IOException {
            // Arrange
            when(provider.getReleaseRequestQueueSize()).thenReturn(150);
            when(federationSupport.getActiveFederation()).thenReturn(standardMultisigFederation);
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(STANDARD_MULTISIG_FED_SINGLE_INPUT_UTXOS);
            BridgeSupport bridgeSupport = buildBridgeSupport(
                POST_RSKIP271_PRE_RSKIP385_ACTIVATIONS,
                provider,
                federationSupport,
                feePerKbSupport
            );

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(713_800L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shErpFederation_withZeroPegoutRequests_shouldEstimateZeroFees() throws IOException {
            // Arrange
            when(provider.getReleaseRequestQueueSize()).thenReturn(0);
            when(federationSupport.getActiveFederation()).thenReturn(p2shErpFederation);
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(P2SH_SINGLE_INPUT_UTXOS);
            BridgeSupport bridgeSupport = buildBridgeSupport(
                POST_RSKIP271_PRE_RSKIP385_ACTIVATIONS,
                provider,
                federationSupport,
                feePerKbSupport
            );

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.ZERO, estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shErpFederation_withOnePegoutRequest_shouldEstimateFeesFromInputAndOutputCount() throws IOException {
            // Arrange
            when(provider.getReleaseRequestQueueSize()).thenReturn(1);
            when(federationSupport.getActiveFederation()).thenReturn(p2shErpFederation);
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(P2SH_SINGLE_INPUT_UTXOS);
            BridgeSupport bridgeSupport = buildBridgeSupport(
                POST_RSKIP271_PRE_RSKIP385_ACTIVATIONS,
                provider,
                federationSupport,
                feePerKbSupport
            );

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(182_600L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shErpFederation_withManyPegoutRequests_shouldEstimateFeesFromInputAndOutputCount() throws IOException {
            // Arrange
            when(provider.getReleaseRequestQueueSize()).thenReturn(150);
            when(federationSupport.getActiveFederation()).thenReturn(p2shErpFederation);
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(P2SH_SINGLE_INPUT_UTXOS);
            BridgeSupport bridgeSupport = buildBridgeSupport(
                POST_RSKIP271_PRE_RSKIP385_ACTIVATIONS,
                provider,
                federationSupport,
                feePerKbSupport
            );

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(659_400L), estimatedFeesForNextPegout);
        }
    }

    @Nested
    class PostRskip385PreRskip305 {
        private BridgeStorageProvider provider;
        private FederationSupport federationSupport;
        private FeePerKbSupport feePerKbSupport;

        @BeforeEach
        void setUp() throws IOException {
            provider = mock(BridgeStorageProvider.class);
            federationSupport = mock(FederationSupport.class);
            feePerKbSupport = mock(FeePerKbSupport.class);
            when(feePerKbSupport.getFeePerKb()).thenReturn(FEE_PER_KB);
            when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(List.of()));
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withStandardFederation_withZeroPegoutRequests_shouldEstimateFeesFromInputAndOutputCount() throws IOException {
            // Arrange
            when(provider.getReleaseRequestQueueSize()).thenReturn(0);
            when(federationSupport.getActiveFederation()).thenReturn(standardMultisigFederation);
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(STANDARD_MULTISIG_FED_SINGLE_INPUT_UTXOS);
            BridgeSupport bridgeSupport = buildBridgeSupport(
                POST_RSKIP385_PRE_RSKIP305_ACTIVATIONS,
                provider,
                federationSupport,
                feePerKbSupport
            );

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(233_800L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withStandardFederation_withOnePegoutRequest_shouldEstimateFeesFromInputAndOutputCount() throws IOException {
            // Arrange
            when(provider.getReleaseRequestQueueSize()).thenReturn(1);
            when(federationSupport.getActiveFederation()).thenReturn(standardMultisigFederation);
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(STANDARD_MULTISIG_FED_SINGLE_INPUT_UTXOS);
            BridgeSupport bridgeSupport = buildBridgeSupport(
                POST_RSKIP385_PRE_RSKIP305_ACTIVATIONS,
                provider,
                federationSupport,
                feePerKbSupport
            );

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(237_000L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withStandardFederation_withManyPegoutRequests_shouldEstimateFeesFromInputAndOutputCount() throws IOException {
            // Arrange
            when(provider.getReleaseRequestQueueSize()).thenReturn(150);
            when(federationSupport.getActiveFederation()).thenReturn(standardMultisigFederation);
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(STANDARD_MULTISIG_FED_SINGLE_INPUT_UTXOS);
            BridgeSupport bridgeSupport = buildBridgeSupport(
                POST_RSKIP385_PRE_RSKIP305_ACTIVATIONS,
                provider,
                federationSupport,
                feePerKbSupport
            );

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(713_800L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shErpFederation_withZeroPegoutRequests_shouldEstimateFeesFromInputAndOutputCount() throws IOException {
            // Arrange
            when(provider.getReleaseRequestQueueSize()).thenReturn(0);
            when(federationSupport.getActiveFederation()).thenReturn(p2shErpFederation);
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(P2SH_SINGLE_INPUT_UTXOS);
            BridgeSupport bridgeSupport = buildBridgeSupport(
                POST_RSKIP385_PRE_RSKIP305_ACTIVATIONS,
                provider,
                federationSupport,
                feePerKbSupport
            );

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(179_400L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shErpFederation_withOnePegoutRequest_shouldEstimateFeesFromInputAndOutputCount() throws IOException {
            // Arrange
            when(provider.getReleaseRequestQueueSize()).thenReturn(1);
            when(federationSupport.getActiveFederation()).thenReturn(p2shErpFederation);
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(P2SH_SINGLE_INPUT_UTXOS);
            BridgeSupport bridgeSupport = buildBridgeSupport(
                POST_RSKIP385_PRE_RSKIP305_ACTIVATIONS,
                provider,
                federationSupport,
                feePerKbSupport
            );

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(182_600L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shErpFederation_withManyPegoutRequests_shouldEstimateFeesFromInputAndOutputCount() throws IOException {
            // Arrange
            when(provider.getReleaseRequestQueueSize()).thenReturn(150);
            when(federationSupport.getActiveFederation()).thenReturn(p2shErpFederation);
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(P2SH_SINGLE_INPUT_UTXOS);
            BridgeSupport bridgeSupport = buildBridgeSupport(
                POST_RSKIP385_PRE_RSKIP305_ACTIVATIONS,
                provider,
                federationSupport,
                feePerKbSupport
            );

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(659_400L), estimatedFeesForNextPegout);
        }
    }

    @Nested
    class PostRskip305Segwit {
        private BridgeStorageProvider provider;
        private FederationSupport federationSupport;
        private FeePerKbSupport feePerKbSupport;

        @BeforeEach
        void setUp() throws IOException {
            provider = mock(BridgeStorageProvider.class);
            federationSupport = mock(FederationSupport.class);
            feePerKbSupport = mock(FeePerKbSupport.class);
            when(feePerKbSupport.getFeePerKb()).thenReturn(FEE_PER_KB);
            when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(List.of()));
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shErpFederation_withNoPegoutRequests_shouldEstimateFeesFromTransactionSimulation() throws IOException {
            // Arrange
            List<Entry> pegoutRequests = List.of();
            when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(pegoutRequests));
            when(federationSupport.getActiveFederation()).thenReturn(p2shErpFederation);
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(P2SH_SINGLE_INPUT_UTXOS);
            BridgeSupport bridgeSupport = buildBridgeSupport(
                POST_RSKIP305_ACTIVATIONS,
                provider,
                federationSupport,
                feePerKbSupport
            );

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(94_900L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shErpFederationWithNoUtxos_withNoPegoutRequests_shouldFallBackToLegacyFeesCalculation() throws IOException {
            // Arrange
            List<Entry> pegoutRequests = List.of();
            when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(pegoutRequests));
            when(provider.getReleaseRequestQueueSize()).thenReturn(0);
            when(federationSupport.getActiveFederation()).thenReturn(p2shErpFederation);
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(List.of());
            BridgeSupport bridgeSupport = buildBridgeSupport(
                POST_RSKIP305_ACTIVATIONS,
                provider,
                federationSupport,
                feePerKbSupport
            );

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(179_400L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shErpFederationWithNoUtxos_withOnePegoutRequest_shouldFallBackToLegacyFeesCalculation() throws IOException {
            // Arrange
            List<Entry> pegoutRequests = List.of(MIN_PEGOUT_VALUE_REQUEST);
            when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(pegoutRequests));
            when(provider.getReleaseRequestQueueSize()).thenReturn(1);
            when(federationSupport.getActiveFederation()).thenReturn(p2shErpFederation);
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(List.of());
            BridgeSupport bridgeSupport = buildBridgeSupport(
                POST_RSKIP305_ACTIVATIONS,
                provider,
                federationSupport,
                feePerKbSupport
            );

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(182_600L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shErpFederation_withOnePegoutRequest_shouldEstimateFeesFromTransactionSimulation() throws IOException {
            // Arrange
            List<Entry> pegoutRequests = List.of(MIN_PEGOUT_VALUE_REQUEST);
            when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(pegoutRequests));
            when(federationSupport.getActiveFederation()).thenReturn(p2shErpFederation);
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(P2SH_SINGLE_INPUT_UTXOS);
            BridgeSupport bridgeSupport = buildBridgeSupport(
                POST_RSKIP305_ACTIVATIONS,
                provider,
                federationSupport,
                feePerKbSupport
            );

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(98_300L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shErpFederation_withTwoPegoutRequests_shouldEstimateFeesFromTransactionSimulation() throws IOException {
            // Arrange
            List<Entry> pegoutRequests = List.of(MIN_PEGOUT_VALUE_REQUEST, ONE_THOUSAND_SATS_REQUEST);
            when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(pegoutRequests));
            when(federationSupport.getActiveFederation()).thenReturn(p2shErpFederation);
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(P2SH_SINGLE_INPUT_UTXOS);
            BridgeSupport bridgeSupport = buildBridgeSupport(
                POST_RSKIP305_ACTIVATIONS,
                provider,
                federationSupport,
                feePerKbSupport
            );

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(101_700L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shErpFederation_withThreePegoutRequests_shouldEstimateFeesFromTransactionSimulation() throws IOException {
            // Arrange
            List<Entry> pegoutRequests = List.of(MIN_PEGOUT_VALUE_REQUEST, ONE_THOUSAND_SATS_REQUEST, TWO_THOUSANDS_SATS_REQUEST);
            when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(pegoutRequests));
            when(federationSupport.getActiveFederation()).thenReturn(p2shErpFederation);
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(P2SH_SINGLE_INPUT_UTXOS);
            BridgeSupport bridgeSupport = buildBridgeSupport(
                POST_RSKIP305_ACTIVATIONS,
                provider,
                federationSupport,
                feePerKbSupport
            );

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(105_100L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shErpFederation_withTwoPegoutRequests_shouldEstimateFeesFromTransactionSimulationUsingTwoInputs() throws IOException {
            // Arrange
            List<Entry> pegoutRequests = List.of(MIN_PEGOUT_VALUE_REQUEST, BIG_REQUEST);
            when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(pegoutRequests));
            when(federationSupport.getActiveFederation()).thenReturn(p2shErpFederation);
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(P2SH_TWO_INPUT_UTXOS);
            BridgeSupport bridgeSupport = buildBridgeSupport(
                POST_RSKIP305_ACTIVATIONS,
                provider,
                federationSupport,
                feePerKbSupport
            );

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(189_000L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shErpFederation_withThreePegoutRequests_shouldEstimateFeesFromTransactionSimulationUsingTwoInputs() throws IOException {
            // Arrange
            List<Entry> pegoutRequests = List.of(MIN_PEGOUT_VALUE_REQUEST, ONE_THOUSAND_SATS_REQUEST, BIG_REQUEST);
            when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(pegoutRequests));
            when(federationSupport.getActiveFederation()).thenReturn(p2shErpFederation);
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(P2SH_TWO_INPUT_UTXOS);
            BridgeSupport bridgeSupport = buildBridgeSupport(
                POST_RSKIP305_ACTIVATIONS,
                provider,
                federationSupport,
                feePerKbSupport
            );

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(192_400L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shErpFederation_withFourPegoutRequests_shouldEstimateFeesFromTransactionSimulationUsingTwoInputs() throws IOException {
            // Arrange
            List<Entry> pegoutRequests = List.of(MIN_PEGOUT_VALUE_REQUEST, ONE_THOUSAND_SATS_REQUEST, TWO_THOUSANDS_SATS_REQUEST, BIG_REQUEST);
            when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(pegoutRequests));
            when(federationSupport.getActiveFederation()).thenReturn(p2shErpFederation);
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(P2SH_TWO_INPUT_UTXOS);
            BridgeSupport bridgeSupport = buildBridgeSupport(
                POST_RSKIP305_ACTIVATIONS,
                provider,
                federationSupport,
                feePerKbSupport
            );

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(195_800L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shP2wshErpFederation_withNoPegoutRequests_shouldEstimateFeesFromTransactionSimulation() throws IOException {
            // Arrange
            List<Entry> pegoutRequests = List.of();
            when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(pegoutRequests));
            when(federationSupport.getActiveFederation()).thenReturn(p2shP2wshErpFederation);
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(P2SH_P2WSH_SINGLE_INPUT_UTXOS);
            BridgeSupport bridgeSupport = buildBridgeSupport(
                POST_RSKIP305_ACTIVATIONS,
                provider,
                federationSupport,
                feePerKbSupport
            );

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(56_700L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shP2wshErpFederationWithNoUtxos_withNoPegoutRequests_shouldFallBackToLegacyFeesCalculation() throws IOException {
            // Arrange
            List<Entry> pegoutRequests = List.of();
            when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(pegoutRequests));
            when(provider.getReleaseRequestQueueSize()).thenReturn(0);
            when(federationSupport.getActiveFederation()).thenReturn(p2shP2wshErpFederation);
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(List.of());
            BridgeSupport bridgeSupport = buildBridgeSupport(
                POST_RSKIP305_ACTIVATIONS,
                provider,
                federationSupport,
                feePerKbSupport
            );

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(95_800L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shP2wshErpFederationWithNoUtxos_withOnePegoutRequest_shouldFallBackToLegacyFeesCalculation() throws IOException {
            // Arrange
            List<Entry> pegoutRequests = List.of(MIN_PEGOUT_VALUE_REQUEST);
            when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(pegoutRequests));
            when(provider.getReleaseRequestQueueSize()).thenReturn(1);
            when(federationSupport.getActiveFederation()).thenReturn(p2shP2wshErpFederation);
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(List.of());
            BridgeSupport bridgeSupport = buildBridgeSupport(
                POST_RSKIP305_ACTIVATIONS,
                provider,
                federationSupport,
                feePerKbSupport
            );

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(99_000L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shP2wshErpFederation_withOnePegoutRequest_shouldEstimateFeesFromTransactionSimulation() throws IOException {
            // Arrange
            List<Entry> pegoutRequests = List.of(MIN_PEGOUT_VALUE_REQUEST);
            when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(pegoutRequests));
            when(federationSupport.getActiveFederation()).thenReturn(p2shP2wshErpFederation);
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(P2SH_P2WSH_SINGLE_INPUT_UTXOS);
            BridgeSupport bridgeSupport = buildBridgeSupport(
                POST_RSKIP305_ACTIVATIONS,
                provider,
                federationSupport,
                feePerKbSupport
            );

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(60_100L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shP2wshErpFederation_withTwoPegoutRequests_shouldEstimateFeesFromTransactionSimulation() throws IOException {
            // Arrange
            List<Entry> pegoutRequests = List.of(MIN_PEGOUT_VALUE_REQUEST, ONE_THOUSAND_SATS_REQUEST);
            when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(pegoutRequests));
            when(federationSupport.getActiveFederation()).thenReturn(p2shP2wshErpFederation);
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(P2SH_P2WSH_SINGLE_INPUT_UTXOS);
            BridgeSupport bridgeSupport = buildBridgeSupport(
                POST_RSKIP305_ACTIVATIONS,
                provider,
                federationSupport,
                feePerKbSupport
            );

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(63_500L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shP2wshErpFederation_withThreePegoutRequests_shouldEstimateFeesFromTransactionSimulation() throws IOException {
            // Arrange
            List<Entry> pegoutRequests = List.of(MIN_PEGOUT_VALUE_REQUEST, ONE_THOUSAND_SATS_REQUEST, TWO_THOUSANDS_SATS_REQUEST);
            when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(pegoutRequests));
            when(federationSupport.getActiveFederation()).thenReturn(p2shP2wshErpFederation);
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(P2SH_P2WSH_SINGLE_INPUT_UTXOS);
            BridgeSupport bridgeSupport = buildBridgeSupport(
                POST_RSKIP305_ACTIVATIONS,
                provider,
                federationSupport,
                feePerKbSupport
            );

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(66_900L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shP2wshErpFederation_withTwoPegoutRequests_shouldEstimateFeesFromTransactionSimulationUsingTwoInputs() throws IOException {
            // Arrange
            List<Entry> pegoutRequests = List.of(MIN_PEGOUT_VALUE_REQUEST, BIG_REQUEST);
            when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(pegoutRequests));
            when(federationSupport.getActiveFederation()).thenReturn(p2shP2wshErpFederation);
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(P2SH_P2WSH_TWO_INPUT_UTXOS);
            BridgeSupport bridgeSupport = buildBridgeSupport(
                POST_RSKIP305_ACTIVATIONS,
                provider,
                federationSupport,
                feePerKbSupport
            );

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(112_600L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shP2wshErpFederation_withThreePegoutRequests_shouldEstimateFeesFromTransactionSimulationUsingTwoInputs() throws IOException {
            // Arrange
            List<Entry> pegoutRequests = List.of(MIN_PEGOUT_VALUE_REQUEST, ONE_THOUSAND_SATS_REQUEST, BIG_REQUEST);
            when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(pegoutRequests));
            when(federationSupport.getActiveFederation()).thenReturn(p2shP2wshErpFederation);
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(P2SH_P2WSH_TWO_INPUT_UTXOS);
            BridgeSupport bridgeSupport = buildBridgeSupport(
                POST_RSKIP305_ACTIVATIONS,
                provider,
                federationSupport,
                feePerKbSupport
            );

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(116_000L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shP2wshErpFederation_withFourPegoutRequests_shouldEstimateFeesFromTransactionSimulationUsingTwoInputs() throws IOException {
            // Arrange
            List<Entry> pegoutRequests = List.of(MIN_PEGOUT_VALUE_REQUEST, ONE_THOUSAND_SATS_REQUEST, TWO_THOUSANDS_SATS_REQUEST, BIG_REQUEST);
            when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(pegoutRequests));
            when(federationSupport.getActiveFederation()).thenReturn(p2shP2wshErpFederation);
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(P2SH_P2WSH_TWO_INPUT_UTXOS);
            BridgeSupport bridgeSupport = buildBridgeSupport(
                POST_RSKIP305_ACTIVATIONS,
                provider,
                federationSupport,
                feePerKbSupport
            );

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(119_400L), estimatedFeesForNextPegout);
        }
    }

    private static BridgeSupport buildBridgeSupport(
        ActivationConfig.ForBlock activations,
        BridgeStorageProvider provider,
        FederationSupport federationSupport,
        FeePerKbSupport feePerKbSupport
    ) {
        return BridgeSupportBuilder.builder()
            .withBridgeConstants(BRIDGE_CONSTANTS)
            .withProvider(provider)
            .withActivations(activations)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .build();
    }

}
