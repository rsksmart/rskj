package co.rsk.peg;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.*;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.feeperkb.FeePerKbSupport;
import co.rsk.peg.storage.InMemoryStorage;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.test.builders.BridgeSupportBuilder;
import co.rsk.test.builders.FederationSupportBuilder;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Repository;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.List;

import static co.rsk.RskTestUtils.createRepository;
import static co.rsk.peg.PegTestUtils.createUTXO;
import static co.rsk.peg.federation.FederationStorageIndexKey.NEW_FEDERATION_BTC_UTXOS_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BridgeSupportGetEstimatedFeesForNextPegOutEventTest {

    private static final BridgeConstants BRIDGE_CONSTANTS = BridgeMainNetConstants.getInstance();
    private static final FederationConstants FEDERATION_CONSTANTS = BRIDGE_CONSTANTS.getFederationConstants();
    private static final NetworkParameters NETWORK_PARAMETERS = BRIDGE_CONSTANTS.getBtcParams();
    private static final ActivationConfig.ForBlock BEFORE_HOP400_ACTIVATION = ActivationConfigsForTest.iris300().forBlock(0L);
    private static final ActivationConfig.ForBlock POST_HOP400_PRE_FINGERROOT_ACTIVATIONS = ActivationConfigsForTest.hop400().forBlock(0L);
    private static final ActivationConfig.ForBlock POST_FINGERROOT_PRE_REED_ACTIVATIONS = ActivationConfigsForTest.fingerroot500().forBlock(0L);
    private static final ActivationConfig.ForBlock POST_REED_ACTIVATION = ActivationConfigsForTest.all().forBlock(0L);

    private static final Federation STANDARD_MULTISIG_FEDERATION = FederationTestUtils.getGenesisFederation(FEDERATION_CONSTANTS);
    private static final ErpFederation P2SH_ERP_FEDERATION = P2shErpFederationBuilder.builder()
        .withNetworkParameters(NETWORK_PARAMETERS)
        .build();
    private static final ErpFederation P2SH_P2WSH_ERP_FEDERATION = P2shP2wshErpFederationBuilder.builder()
        .withNetworkParameters(NETWORK_PARAMETERS)
        .build();

    private static final Address RECEIVER_1 = BitcoinTestUtils.createP2PKHAddress(NETWORK_PARAMETERS, "address1");
    private static final Address RECEIVER_2 = BitcoinTestUtils.createP2PKHAddress(NETWORK_PARAMETERS, "address2");
    private static final Address RECEIVER_3 = BitcoinTestUtils.createP2PKHAddress(NETWORK_PARAMETERS, "address3");
    private static final Address RECEIVER_4 = BitcoinTestUtils.createP2PKHAddress(NETWORK_PARAMETERS, "address4");

    private static final Coin EIGHT_BTCS = Coin.valueOf(8, 0);
    private static final List<UTXO> STANDARD_MULTISIG_FED_SINGLE_INPUT_UTXOS = List.of(
        createUTXO(EIGHT_BTCS, STANDARD_MULTISIG_FEDERATION.getAddress())
    );

    private static final List<UTXO> P2SH_SINGLE_INPUT_UTXOS = List.of(
        createUTXO(EIGHT_BTCS, P2SH_ERP_FEDERATION.getAddress())
    );
    private static final List<UTXO> ONE_P2SH_P2WSH_EIGHT_BTCS_UTXO = List.of(
        createUTXO(EIGHT_BTCS, P2SH_P2WSH_ERP_FEDERATION.getAddress())
    );
    private static final List<UTXO> TWO_P2SH_P2WSH_EIGHT_BTCS_UTXOS = List.of(
        createUTXO(EIGHT_BTCS, P2SH_P2WSH_ERP_FEDERATION.getAddress()),
        createUTXO(EIGHT_BTCS, P2SH_P2WSH_ERP_FEDERATION.getAddress())
    );
    public static final Coin TEN_BTCS_PEGOUT_TX_VALUE = Coin.COIN.multiply(10);
    public static final Coin MINIMUM_PEGOUT_TX_VALUE = BRIDGE_CONSTANTS.getMinimumPegoutTxValue();

    private BridgeStorageProvider bridgeStorageProvider;
    private FederationSupport federationSupport;
    private FeePerKbSupport feePerKbSupport;
    private BridgeSupport bridgeSupport;
    private StorageAccessor bridgeStorageAccessor;

    @Nested
    class PreHop400Activation {

        @BeforeEach
        void setUp() {
            Repository repository = createRepository();
            setUpFeePerKb();
            bridgeStorageAccessor = new InMemoryStorage();
            bridgeStorageProvider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, NETWORK_PARAMETERS, BEFORE_HOP400_ACTIVATION);
            FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);
            federationStorageProvider.setNewFederation(STANDARD_MULTISIG_FEDERATION);
            addUtxosToActiveFederation(STANDARD_MULTISIG_FED_SINGLE_INPUT_UTXOS);
            federationSupport = FederationSupportBuilder.builder()
                .withFederationConstants(FEDERATION_CONSTANTS)
                .withActivations(BEFORE_HOP400_ACTIVATION)
                .withFederationStorageProvider(federationStorageProvider)
                .build();
            bridgeSupport = BridgeSupportBuilder.builder()
                .withBridgeConstants(BRIDGE_CONSTANTS)
                .withProvider(bridgeStorageProvider)
                .withActivations(BEFORE_HOP400_ACTIVATION)
                .withFederationSupport(federationSupport)
                .withFeePerKbSupport(feePerKbSupport)
                .build();

        }

        @ParameterizedTest
        @ValueSource(ints = {0, 1, 150})
        void getEstimatedFeesForNextPegOutEvent_withStandardFederation_shouldReturnZeroFees(int pegoutRequestsCount) throws IOException {
            // Arrange
            addPegoutRequests(pegoutRequestsCount);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.ZERO, estimatedFeesForNextPegout);
        }
    }

    @Nested
    class PostHop400PreFingerrootActivations {
        private final Repository repository = createRepository();

        private FederationStorageProvider federationStorageProvider;

        @BeforeEach
        void setUp() {
            setUpFeePerKb();
            bridgeStorageAccessor = new InMemoryStorage();
            bridgeStorageProvider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, NETWORK_PARAMETERS, POST_HOP400_PRE_FINGERROOT_ACTIVATIONS);
            federationStorageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);
            federationSupport = FederationSupportBuilder.builder()
                .withFederationConstants(FEDERATION_CONSTANTS)
                .withActivations(POST_HOP400_PRE_FINGERROOT_ACTIVATIONS)
                .withFederationStorageProvider(federationStorageProvider)
                .build();
            bridgeSupport = BridgeSupportBuilder.builder()
                .withBridgeConstants(BRIDGE_CONSTANTS)
                .withProvider(bridgeStorageProvider)
                .withActivations(POST_HOP400_PRE_FINGERROOT_ACTIVATIONS)
                .withFederationSupport(federationSupport)
                .withFeePerKbSupport(feePerKbSupport)
                .build();
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withStandardFederation_withNoPegoutRequests_shouldEstimateZeroFees() throws IOException {
            // Arrange
            federationStorageProvider.setNewFederation(STANDARD_MULTISIG_FEDERATION);
            addUtxosToActiveFederation(STANDARD_MULTISIG_FED_SINGLE_INPUT_UTXOS);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.ZERO, estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withStandardFederation_withOnePegoutRequest_shouldEstimateFeesFromInputAndOutputCount() throws IOException {
            // Arrange
            federationStorageProvider.setNewFederation(STANDARD_MULTISIG_FEDERATION);
            addUtxosToActiveFederation(STANDARD_MULTISIG_FED_SINGLE_INPUT_UTXOS);
            addPegoutRequests(1);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(237_000L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withStandardFederation_withManyPegoutRequests_shouldEstimateFeesFromInputAndOutputCount() throws IOException {
            // Arrange
            federationStorageProvider.setNewFederation(STANDARD_MULTISIG_FEDERATION);
            addUtxosToActiveFederation(STANDARD_MULTISIG_FED_SINGLE_INPUT_UTXOS);
            addPegoutRequests(150);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(713_800L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shErpFederation_withNoPegoutRequests_shouldEstimateZeroFees() throws IOException {
            // Arrange
            federationStorageProvider.setNewFederation(P2SH_ERP_FEDERATION);
            addUtxosToActiveFederation(P2SH_SINGLE_INPUT_UTXOS);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.ZERO, estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shErpFederation_withOnePegoutRequest_shouldEstimateFeesFromInputAndOutputCount() throws IOException {
            // Arrange
            federationStorageProvider.setNewFederation(P2SH_ERP_FEDERATION);
            addUtxosToActiveFederation(P2SH_SINGLE_INPUT_UTXOS);
            addPegoutRequests(1);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(182_600L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shErpFederation_withManyPegoutRequests_shouldEstimateFeesFromInputAndOutputCount() throws IOException {
            // Arrange
            federationStorageProvider.setNewFederation(P2SH_ERP_FEDERATION);
            addUtxosToActiveFederation(P2SH_SINGLE_INPUT_UTXOS);
            addPegoutRequests(150);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(659_400L), estimatedFeesForNextPegout);
        }
    }

    @Nested
    class PostFingerrootPreReedActivations {
        private final Repository repository = createRepository();

        private FederationStorageProvider federationStorageProvider;

        @BeforeEach
        void setUp() {
            setUpFeePerKb();
            bridgeStorageAccessor = new InMemoryStorage();
            bridgeStorageProvider =  new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, NETWORK_PARAMETERS, POST_FINGERROOT_PRE_REED_ACTIVATIONS);
            federationStorageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);
            federationSupport = FederationSupportBuilder.builder()
                .withFederationConstants(FEDERATION_CONSTANTS)
                .withActivations(POST_FINGERROOT_PRE_REED_ACTIVATIONS)
                .withFederationStorageProvider(federationStorageProvider)
                .build();
            bridgeSupport = BridgeSupportBuilder.builder()
                .withBridgeConstants(BRIDGE_CONSTANTS)
                .withProvider(bridgeStorageProvider)
                .withActivations(POST_FINGERROOT_PRE_REED_ACTIVATIONS)
                .withFederationSupport(federationSupport)
                .withFeePerKbSupport(feePerKbSupport)
                .build();
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withStandardFederation_withNoPegoutRequests_shouldEstimateFeesFromInputAndOutputCount() throws IOException {
            // Arrange
            federationStorageProvider.setNewFederation(STANDARD_MULTISIG_FEDERATION);
            addUtxosToActiveFederation(STANDARD_MULTISIG_FED_SINGLE_INPUT_UTXOS);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(233_800L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withStandardFederation_withOnePegoutRequest_shouldEstimateFeesFromInputAndOutputCount() throws IOException {
            // Arrange
            federationStorageProvider.setNewFederation(STANDARD_MULTISIG_FEDERATION);
            addUtxosToActiveFederation(STANDARD_MULTISIG_FED_SINGLE_INPUT_UTXOS);
            addPegoutRequests(1);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(237_000L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withStandardFederation_withManyPegoutRequests_shouldEstimateFeesFromInputAndOutputCount() throws IOException {
            // Arrange
            federationStorageProvider.setNewFederation(STANDARD_MULTISIG_FEDERATION);
            addUtxosToActiveFederation(STANDARD_MULTISIG_FED_SINGLE_INPUT_UTXOS);
            addPegoutRequests(150);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(713_800L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shErpFederation_withNoPegoutRequests_shouldEstimateFeesFromInputAndOutputCount() throws IOException {
            // Arrange
            federationStorageProvider.setNewFederation(P2SH_ERP_FEDERATION);
            addUtxosToActiveFederation(P2SH_SINGLE_INPUT_UTXOS);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(179_400L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shErpFederation_withOnePegoutRequest_shouldEstimateFeesFromInputAndOutputCount() throws IOException {
            // Arrange
            federationStorageProvider.setNewFederation(P2SH_ERP_FEDERATION);
            addUtxosToActiveFederation(P2SH_SINGLE_INPUT_UTXOS);
            addPegoutRequests(1);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(182_600L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shErpFederation_withManyPegoutRequests_shouldEstimateFeesFromInputAndOutputCount() throws IOException {
            // Arrange
            federationStorageProvider.setNewFederation(P2SH_ERP_FEDERATION);
            addUtxosToActiveFederation(P2SH_SINGLE_INPUT_UTXOS);
            addPegoutRequests(150);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(659_400L), estimatedFeesForNextPegout);
        }
    }

    @Nested
    class PostReedActivation {

        @BeforeEach
        void setUp() {
            Repository repository = createRepository();
            setUpFeePerKb();
            bridgeStorageAccessor = new InMemoryStorage();
            bridgeStorageProvider =  new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, NETWORK_PARAMETERS, POST_REED_ACTIVATION);
            FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);
            federationStorageProvider.setNewFederation(P2SH_P2WSH_ERP_FEDERATION);
            federationSupport = FederationSupportBuilder.builder()
                .withFederationConstants(FEDERATION_CONSTANTS)
                .withActivations(POST_REED_ACTIVATION)
                .withFederationStorageProvider(federationStorageProvider)
                .build();
            bridgeSupport = BridgeSupportBuilder.builder()
                .withBridgeConstants(BRIDGE_CONSTANTS)
                .withProvider(bridgeStorageProvider)
                .withActivations(POST_REED_ACTIVATION)
                .withFederationSupport(federationSupport)
                .withFeePerKbSupport(feePerKbSupport)
                .build();
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shP2wshErpFederation_withNoPegoutRequests_shouldEstimateFeesFromTransactionSimulation() throws IOException {
            // Arrange
            addUtxosToActiveFederation(ONE_P2SH_P2WSH_EIGHT_BTCS_UTXO);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(56_700L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shP2wshErpFederationWithNoUtxos_withNoPegoutRequests_shouldFallBackToEstimateFeesFromInputAndOutputCount() throws IOException {
            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(95_800L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shP2wshErpFederationWithNoUtxos_withOnePegoutRequest_shouldFallBackToEstimateFeesFromInputAndOutputCount() throws IOException {
            // Arrange
            ReleaseRequestQueue releaseRequestQueue = bridgeStorageProvider.getReleaseRequestQueue();
            releaseRequestQueue.add(RECEIVER_1, MINIMUM_PEGOUT_TX_VALUE);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(99_000L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shP2wshErpFederation_withOnePegoutRequest_shouldEstimateFeesFromTransactionSimulation() throws IOException {
            // Arrange
            addUtxosToActiveFederation(ONE_P2SH_P2WSH_EIGHT_BTCS_UTXO);

            ReleaseRequestQueue releaseRequestQueue = bridgeStorageProvider.getReleaseRequestQueue();
            releaseRequestQueue.add(RECEIVER_1, MINIMUM_PEGOUT_TX_VALUE);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(60_100L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shP2wshErpFederationWithTwoBTCs_withOneBtcPegoutRequest_shouldEstimateFeesFromTransactionSimulation() throws IOException {
            // Arrange
            UTXO oneBtcUtxo = createUTXO(Coin.COIN, P2SH_P2WSH_ERP_FEDERATION.getAddress());
            List<UTXO> federationUtxos = List.of(oneBtcUtxo, oneBtcUtxo);
            addUtxosToActiveFederation(federationUtxos);

            ReleaseRequestQueue releaseRequestQueue = bridgeStorageProvider.getReleaseRequestQueue();
            releaseRequestQueue.add(RECEIVER_1, Coin.COIN);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(106_000L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shP2wshErpFederationWithTwoBTCs_withMoreThanOneBtcPegoutRequest_shouldFallBackToEstimateFeesFromInputAndOutputCount() throws IOException {
            // Arrange
            UTXO oneBtcUtxo = createUTXO(Coin.COIN, P2SH_P2WSH_ERP_FEDERATION.getAddress());
            List<UTXO> federationUtxos = List.of(oneBtcUtxo, oneBtcUtxo);
            addUtxosToActiveFederation(federationUtxos);

            ReleaseRequestQueue releaseRequestQueue = bridgeStorageProvider.getReleaseRequestQueue();
            releaseRequestQueue.add(RECEIVER_1, Coin.COIN.add(Coin.SATOSHI));

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(99_000L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shP2wshErpFederation_withTwoPegoutRequests_shouldEstimateFeesFromTransactionSimulation() throws IOException {
            // Arrange
            addUtxosToActiveFederation(ONE_P2SH_P2WSH_EIGHT_BTCS_UTXO);

            ReleaseRequestQueue releaseRequestQueue = bridgeStorageProvider.getReleaseRequestQueue();
            releaseRequestQueue.add(RECEIVER_1, MINIMUM_PEGOUT_TX_VALUE);
            releaseRequestQueue.add(RECEIVER_2, MINIMUM_PEGOUT_TX_VALUE.add(Coin.valueOf(1_000L)));

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(63_500L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shP2wshErpFederation_withThreePegoutRequests_shouldEstimateFeesFromTransactionSimulation() throws IOException {
            // Arrange
            addUtxosToActiveFederation(ONE_P2SH_P2WSH_EIGHT_BTCS_UTXO);

            ReleaseRequestQueue releaseRequestQueue = bridgeStorageProvider.getReleaseRequestQueue();
            releaseRequestQueue.add(RECEIVER_1, MINIMUM_PEGOUT_TX_VALUE);
            releaseRequestQueue.add(RECEIVER_2, MINIMUM_PEGOUT_TX_VALUE.add(Coin.valueOf(1_000L)));
            releaseRequestQueue.add(RECEIVER_3, MINIMUM_PEGOUT_TX_VALUE.add(Coin.valueOf(2_000L)));

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(66_900L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shP2wshErpFederationWithTwoUtxos_withTwoPegoutRequests_shouldEstimateFeesFromTransactionSimulationUsingTwoInputs() throws IOException {
            // Arrange
            addUtxosToActiveFederation(TWO_P2SH_P2WSH_EIGHT_BTCS_UTXOS);

            ReleaseRequestQueue releaseRequestQueue = bridgeStorageProvider.getReleaseRequestQueue();
            releaseRequestQueue.add(RECEIVER_1, MINIMUM_PEGOUT_TX_VALUE);
            releaseRequestQueue.add(RECEIVER_2, TEN_BTCS_PEGOUT_TX_VALUE);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(112_600L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shP2wshErpFederationWithTwoUtxos_withThreePegoutRequests_shouldEstimateFeesFromTransactionSimulationUsingTwoInputs() throws IOException {
            // Arrange
            addUtxosToActiveFederation(TWO_P2SH_P2WSH_EIGHT_BTCS_UTXOS);

            ReleaseRequestQueue releaseRequestQueue = bridgeStorageProvider.getReleaseRequestQueue();
            releaseRequestQueue.add(RECEIVER_1, MINIMUM_PEGOUT_TX_VALUE);
            releaseRequestQueue.add(RECEIVER_2, MINIMUM_PEGOUT_TX_VALUE.add(Coin.valueOf(1_000L)));
            releaseRequestQueue.add(RECEIVER_3, TEN_BTCS_PEGOUT_TX_VALUE);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(116_000L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shP2wshErpFederationWithTwoUtxos_withFourPegoutRequests_shouldEstimateFeesFromTransactionSimulationUsingTwoInputs() throws IOException {
            // Arrange
            addUtxosToActiveFederation(TWO_P2SH_P2WSH_EIGHT_BTCS_UTXOS);

            ReleaseRequestQueue releaseRequestQueue = bridgeStorageProvider.getReleaseRequestQueue();
            releaseRequestQueue.add(RECEIVER_1, MINIMUM_PEGOUT_TX_VALUE);
            releaseRequestQueue.add(RECEIVER_2, MINIMUM_PEGOUT_TX_VALUE.add(Coin.valueOf(1_000L)));
            releaseRequestQueue.add(RECEIVER_3, MINIMUM_PEGOUT_TX_VALUE.add(Coin.valueOf(2_000L)));
            releaseRequestQueue.add(RECEIVER_4, TEN_BTCS_PEGOUT_TX_VALUE);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(119_400L), estimatedFeesForNextPegout);
        }
    }

    private void addUtxosToActiveFederation(List<UTXO> utxos) {
        bridgeStorageAccessor.saveToRepository(NEW_FEDERATION_BTC_UTXOS_KEY.getKey(), utxos, BridgeSerializationUtils::serializeUTXOList);
    }

    private void setUpFeePerKb() {
        feePerKbSupport = mock(FeePerKbSupport.class);
        Coin feePerKb = Coin.MILLICOIN;
        when(feePerKbSupport.getFeePerKb()).thenReturn(feePerKb);
    }

    private void addPegoutRequests(int pegOutRequestCount) throws IOException {
        ReleaseRequestQueue releaseRequestQueue = bridgeStorageProvider.getReleaseRequestQueue();
        for (int i = 0; i < pegOutRequestCount; i++) {
            Address receiver = BitcoinTestUtils.createP2PKHAddress(NETWORK_PARAMETERS, "receiver" + i);
            releaseRequestQueue.add(receiver, Coin.COIN);
        }
    }

}
