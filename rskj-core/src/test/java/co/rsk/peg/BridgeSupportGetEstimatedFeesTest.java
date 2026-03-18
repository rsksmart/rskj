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
import co.rsk.test.builders.UTXOBuilder;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

import static co.rsk.RskTestUtils.createRepository;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.createHash;
import static co.rsk.peg.federation.FederationStorageIndexKey.NEW_FEDERATION_BTC_UTXOS_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BridgeSupportGetEstimatedFeesTest {

    private static final BridgeConstants BRIDGE_CONSTANTS = BridgeMainNetConstants.getInstance();
    private static final FederationConstants FEDERATION_CONSTANTS = BRIDGE_CONSTANTS.getFederationConstants();
    private static final NetworkParameters NETWORK_PARAMETERS = BRIDGE_CONSTANTS.getBtcParams();
    private static final ActivationConfig.ForBlock IRIS300_ACTIVATION = ActivationConfigsForTest.iris300().forBlock(0L);
    private static final ActivationConfig.ForBlock HOP400_ACTIVATION = ActivationConfigsForTest.hop400().forBlock(0L);
    private static final ActivationConfig.ForBlock FINGERROOT_ACTIVATION = ActivationConfigsForTest.fingerroot500().forBlock(0L);
    private static final ActivationConfig.ForBlock REED_ACTIVATION = ActivationConfigsForTest.reed800().forBlock(0L);
    private static final ActivationConfig.ForBlock VETIVER_ACTIVATION = ActivationConfigsForTest.all().forBlock(0L);

    private static final Federation STANDARD_MULTISIG_FEDERATION = StandardMultiSigFederationBuilder.builder()
        .withNetworkParameters(NETWORK_PARAMETERS)
        .build();
    private static final ErpFederation P2SH_ERP_FEDERATION = P2shErpFederationBuilder.builder()
        .withNetworkParameters(NETWORK_PARAMETERS)
        .build();
    private static final ErpFederation P2SH_P2WSH_ERP_FEDERATION = P2shP2wshErpFederationBuilder.builder()
        .withNetworkParameters(NETWORK_PARAMETERS)
        .build();

    private static final Coin EIGHT_BTCS = Coin.valueOf(8, 0);
    private static final Coin TEN_BTCS = Coin.valueOf(10, 0);
    private static final Coin MIN_PEGOUT_TX_VALUE = BRIDGE_CONSTANTS.getMinimumPegoutTxValue();
    private static final List<UTXO> THREE_STANDARD_MULTISIG_UTXOS_OF_EIGHT_BTCS = UTXOBuilder.builder()
        .withValue(EIGHT_BTCS)
        .withScriptPubKey(STANDARD_MULTISIG_FEDERATION.getP2SHScript())
        .buildMany(3, i -> createHash(i + 1));

    private static final List<UTXO> ONE_P2SH_UTXO_OF_ONE_BTC = List.of(
        UTXOBuilder.builder()
            .withValue(EIGHT_BTCS)
            .withScriptPubKey(P2SH_ERP_FEDERATION.getP2SHScript())
            .build()
    );
    private static final List<UTXO> FOUR_P2SH_P2WSH_UTXOS_OF_ONE_BTC = UTXOBuilder.builder()
        .withValue(Coin.COIN)
        .withScriptPubKey(P2SH_P2WSH_ERP_FEDERATION.getP2SHScript())
        .buildMany(4, i -> createHash(i + 1));
    private static final List<UTXO> TWO_P2SH_P2WSH_UTXOS_OF_EIGHT_BTCS = UTXOBuilder.builder()
        .withValue(EIGHT_BTCS)
        .withScriptPubKey(P2SH_P2WSH_ERP_FEDERATION.getP2SHScript())
        .buildMany(2, i -> createHash(i + 1));
    private static final List<UTXO> TWO_P2SH_P2WSH_UTXOS_OF_ONE_BTC = UTXOBuilder.builder()
        .withValue(Coin.COIN)
        .withScriptPubKey(P2SH_P2WSH_ERP_FEDERATION.getP2SHScript())
        .buildMany(2, i -> createHash(i + 1));
    private static final List<UTXO> TWO_P2SH_P2WSH_UTXOS_OF_ONE_BTC_AND_MIN_PEGOUT_VALUE = List.of(
        UTXOBuilder.builder()
            .withValue(Coin.COIN)
            .withScriptPubKey(P2SH_P2WSH_ERP_FEDERATION.getP2SHScript())
            .build(),
        UTXOBuilder.builder()
            .withValue(MIN_PEGOUT_TX_VALUE)
            .withScriptPubKey(P2SH_P2WSH_ERP_FEDERATION.getP2SHScript())
            .build()
    );

    private FeePerKbSupport feePerKbSupport;
    private StorageAccessor bridgeStorageAccessor;
    private BridgeStorageProvider bridgeStorageProvider;
    private FederationStorageProvider federationStorageProvider;
    private BridgeSupport bridgeSupport;

    @Nested
    class PreHop400Activation {

        @BeforeEach
        void setUp() {
            setUpBridgeAndFederationSupport(IRIS300_ACTIVATION);
            // Before Hop activation, the only federation is the legacy standard multisig federation.
            federationStorageProvider.setNewFederation(STANDARD_MULTISIG_FEDERATION);
            addUtxosToActiveFederation(THREE_STANDARD_MULTISIG_UTXOS_OF_EIGHT_BTCS);

        }

        @ParameterizedTest
        @ValueSource(ints = {0, 1, 150})
        void getEstimatedFeesForNextPegOutEvent_preHop_shouldReturnZeroFees(int pegoutRequestsCount) throws IOException {
            // Arrange
            addPegoutRequests(pegoutRequestsCount, Coin.COIN);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.ZERO, estimatedFeesForNextPegout);
        }
    }

    @Nested
    class PostHop400PreFingerrootActivations {

        @BeforeEach
        void setUp() {
            // Between Hop 400 and Fingerroot, there are two federation types: Standard Multisig and P2SH ERP.
            // Hop 401 activation marks the transition from Standard Multisig to P2SH ERP federations.
            setUpBridgeAndFederationSupport(HOP400_ACTIVATION);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withStandardFederation_withNoPegoutRequests_shouldEstimateZeroFees() throws IOException {
            // Arrange
            federationStorageProvider.setNewFederation(STANDARD_MULTISIG_FEDERATION);
            addUtxosToActiveFederation(THREE_STANDARD_MULTISIG_UTXOS_OF_EIGHT_BTCS);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.ZERO, estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withStandardFederation_withOnePegoutRequest_shouldEstimateFeesFromInputAndOutputCount() throws IOException {
            // Arrange
            federationStorageProvider.setNewFederation(STANDARD_MULTISIG_FEDERATION);
            addUtxosToActiveFederation(THREE_STANDARD_MULTISIG_UTXOS_OF_EIGHT_BTCS);
            addPegoutRequests(1, Coin.COIN);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(237_000L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withStandardFederation_withManyPegoutRequests_shouldEstimateFeesFromInputAndOutputCount() throws IOException {
            // Arrange
            federationStorageProvider.setNewFederation(STANDARD_MULTISIG_FEDERATION);
            addUtxosToActiveFederation(THREE_STANDARD_MULTISIG_UTXOS_OF_EIGHT_BTCS);
            addPegoutRequests(150, Coin.COIN);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(713_800L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shErpFederation_withNoPegoutRequests_shouldEstimateZeroFees() throws IOException {
            // Arrange
            federationStorageProvider.setNewFederation(P2SH_ERP_FEDERATION);
            addUtxosToActiveFederation(ONE_P2SH_UTXO_OF_ONE_BTC);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.ZERO, estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shErpFederation_withOnePegoutRequest_shouldEstimateFeesFromInputAndOutputCount() throws IOException {
            // Arrange
            federationStorageProvider.setNewFederation(P2SH_ERP_FEDERATION);
            addUtxosToActiveFederation(ONE_P2SH_UTXO_OF_ONE_BTC);
            addPegoutRequests(1, Coin.COIN);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(182_600L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shErpFederation_withManyPegoutRequests_shouldEstimateFeesFromInputAndOutputCount() throws IOException {
            // Arrange
            federationStorageProvider.setNewFederation(P2SH_ERP_FEDERATION);
            addUtxosToActiveFederation(ONE_P2SH_UTXO_OF_ONE_BTC);
            addPegoutRequests(150, Coin.COIN);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(659_400L), estimatedFeesForNextPegout);
        }
    }

    @Nested
    class PostFingerrootPreReedActivations {

        @BeforeEach
        void setUp() {
            setUpBridgeAndFederationSupport(FINGERROOT_ACTIVATION);
            // After Hop401, which is before Fingerroot the federation is P2SH ERP.
            // Therefore, after Fingerroot the federation is P2SH ERP.
            federationStorageProvider.setNewFederation(P2SH_ERP_FEDERATION);
            addUtxosToActiveFederation(ONE_P2SH_UTXO_OF_ONE_BTC);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shErpFederation_withNoPegoutRequests_shouldEstimateFeesFromInputAndOutputCount() throws IOException {
            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(179_400L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shErpFederation_withOnePegoutRequest_shouldEstimateFeesFromInputAndOutputCount() throws IOException {
            // Arrange
            addPegoutRequests(1, Coin.COIN);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(182_600L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shErpFederation_withManyPegoutRequests_shouldEstimateFeesFromInputAndOutputCount() throws IOException {
            // Arrange
            addPegoutRequests(150, Coin.COIN);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(659_400L), estimatedFeesForNextPegout);
        }
    }

    @Nested
    class PostReedPreVetiverActivations {

        @BeforeEach
        void setUp() {
            setUpBridgeAndFederationSupport(REED_ACTIVATION);
            // Reed activation marks the transition from P2SH ERP to P2SH-P2WSH ERP federations.
            federationStorageProvider.setNewFederation(P2SH_P2WSH_ERP_FEDERATION);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shP2wshErpFederation_withNoPegoutRequests_shouldEstimateFeesFromTransactionSimulation() throws IOException {
            // Arrange
            addUtxosToActiveFederation(TWO_P2SH_P2WSH_UTXOS_OF_EIGHT_BTCS);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(56_700L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shP2wshErpFederation_withNoUtxos_withNoPegoutRequests_shouldFallBackToEstimateFeesFromInputAndOutputCount() throws IOException {
            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(95_800L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shP2wshErpFederation_withNoUtxos_withOnePegoutRequest_shouldFallBackToEstimateFeesFromInputAndOutputCount() throws IOException {
            // Arrange
            addPegoutRequests(1, Coin.COIN);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(99_000L), estimatedFeesForNextPegout);
        }

        @ParameterizedTest
        @CsvSource({
            "1, 109200",
            "2, 112600",
            "3, 116000"
        })
        void getEstimatedFeesForNextPegOutEvent_withP2shP2wshErpFederation_withMinPegoutValueRequests_shouldEstimateFeesFromTransactionSimulation(int pegoutRequestCount, long expectedEstimatedFees) throws IOException {
            // Arrange
            addUtxosToActiveFederation(FOUR_P2SH_P2WSH_UTXOS_OF_ONE_BTC);
            addPegoutRequests(pegoutRequestCount, MIN_PEGOUT_TX_VALUE);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(expectedEstimatedFees), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shP2wshErpFederation_withTwoBTCs_withPegoutRequestOfOneBtc_shouldEstimateFeesFromTransactionSimulation() throws IOException {
            // Arrange
            addUtxosToActiveFederation(TWO_P2SH_P2WSH_UTXOS_OF_ONE_BTC);
            addPegoutRequests(1, Coin.COIN);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(106_000L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shP2wshErpFederation_withTwoBTCs_withPegoutRequestGreaterThanOneBtc_shouldFallBackToEstimateFeesFromInputAndOutputCount() throws IOException {
            // Arrange
            addUtxosToActiveFederation(TWO_P2SH_P2WSH_UTXOS_OF_ONE_BTC);
            addPegoutRequests(1, Coin.COIN.add(Coin.SATOSHI));

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(99_000L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shP2wshErpFederation_withTwoUtxos_withTwoPegoutRequests_shouldEstimateFeesFromTransactionSimulation() throws IOException {
            // Arrange
            addUtxosToActiveFederation(TWO_P2SH_P2WSH_UTXOS_OF_EIGHT_BTCS);
            addPegoutRequests(1, Coin.COIN.add(Coin.SATOSHI));
            addPegoutRequests(1, TEN_BTCS);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(112_600L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shP2wshErpFederation_withTwoUtxos_withThreePegoutRequests_shouldEstimateFeesFromTransactionSimulation() throws IOException {
            // Arrange
            addUtxosToActiveFederation(TWO_P2SH_P2WSH_UTXOS_OF_EIGHT_BTCS);
            addPegoutRequests(2, Coin.COIN);
            addPegoutRequests(1, TEN_BTCS);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(116_000L), estimatedFeesForNextPegout);
        }
    }

    @Nested
    class PostVetiverActivation {

        @BeforeEach
        void setUp() {
            setUpBridgeAndFederationSupport(VETIVER_ACTIVATION);
            // After Reed activation, which is before Vetiver, the federation is P2SH-P2WSH ERP.
            // Therefore, after Vetiver the federation is P2SH-P2WSH ERP.
            federationStorageProvider.setNewFederation(P2SH_P2WSH_ERP_FEDERATION);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shP2wshErpFederation_withNoPegoutRequests_shouldEstimateFeesFromTransactionSimulation() throws IOException {
            // Arrange
            addUtxosToActiveFederation(TWO_P2SH_P2WSH_UTXOS_OF_EIGHT_BTCS);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(56_700L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shP2wshErpFederation_withNoUtxos_withNoPegoutRequests_shouldFallBackToEstimateFeesFromInputAndOutputCount() throws IOException {
            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(95_800L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shP2wshErpFederation_withNoUtxos_withOnePegoutRequest_shouldFallBackToEstimateFeesFromInputAndOutputCount() throws IOException {
            // Arrange
            addPegoutRequests(1, Coin.COIN);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(99_000L), estimatedFeesForNextPegout);
        }

        @ParameterizedTest
        @CsvSource({
            "1, 60100",
            "2, 63500",
            "3, 66900"
        })
        void getEstimatedFeesForNextPegOutEvent_withP2shP2wshErpFederation_withMinPegoutValueRequests_shouldEstimateFeesFromTransactionSimulation(
            int pegoutRequestCount, long expectedEstimatedFees) throws IOException {
            // Arrange
            addUtxosToActiveFederation(FOUR_P2SH_P2WSH_UTXOS_OF_ONE_BTC);
            addPegoutRequests(pegoutRequestCount, MIN_PEGOUT_TX_VALUE);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(expectedEstimatedFees), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shP2wshErpFederation_withTwoBTCs_withPegoutRequestOfOneBtc_shouldEstimateFeesFromTransactionSimulation() throws IOException {
            // Arrange
            addUtxosToActiveFederation(TWO_P2SH_P2WSH_UTXOS_OF_ONE_BTC);
            addPegoutRequests(1, Coin.COIN);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(109_200L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shP2wshErpFederation_withTwoBTCs_withPegoutRequestGreaterThanOneBtc_shouldEstimateFeesFromTransactionSimulation() throws IOException {
            // Arrange
            addUtxosToActiveFederation(TWO_P2SH_P2WSH_UTXOS_OF_ONE_BTC);
            addPegoutRequests(1, Coin.COIN.add(Coin.SATOSHI));

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(109_200L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shP2wshErpFederationHavingOneBTCAndMinPegoutValue_withOneBTCPegoutRequest_shouldEstimateFeesFromTransactionSimulation() throws IOException {
            // Arrange
            addUtxosToActiveFederation(TWO_P2SH_P2WSH_UTXOS_OF_ONE_BTC_AND_MIN_PEGOUT_VALUE);
            addPegoutRequests(1, Coin.COIN);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(106_000L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shP2wshErpFederationHavingOneBTCAndMinPegoutValue_withPegoutRequestGreaterThanOneBtc_shouldFallBackToEstimateFeesFromInputAndOutputCount() throws IOException {
            // Arrange
            addUtxosToActiveFederation(TWO_P2SH_P2WSH_UTXOS_OF_ONE_BTC_AND_MIN_PEGOUT_VALUE);
            addPegoutRequests(1, Coin.COIN.add(Coin.SATOSHI));

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(99_000L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shP2wshErpFederation_withTwoUtxos_withTwoPegoutRequests_shouldEstimateFeesFromTransactionSimulation() throws IOException {
            // Arrange
            addUtxosToActiveFederation(TWO_P2SH_P2WSH_UTXOS_OF_EIGHT_BTCS);
            addPegoutRequests(1, Coin.COIN.add(Coin.SATOSHI));
            addPegoutRequests(1, TEN_BTCS);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(112_600L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForNextPegOutEvent_withP2shP2wshErpFederation_withTwoUtxos_withThreePegoutRequests_shouldEstimateFeesFromTransactionSimulation() throws IOException {
            // Arrange
            addUtxosToActiveFederation(TWO_P2SH_P2WSH_UTXOS_OF_EIGHT_BTCS);
            addPegoutRequests(2, Coin.COIN);
            addPegoutRequests(1, TEN_BTCS);

            // Act
            Coin estimatedFeesForNextPegout = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

            // Assert
            assertEquals(Coin.valueOf(116_000L), estimatedFeesForNextPegout);
        }

        @Test
        void getEstimatedFeesForPegOutAmount_withMinPegoutTxValue_withP2shP2wshErpFederation_withNoPegoutRequests_shouldEstimateFeesFromTransactionSimulation() throws Exception {
            // Arrange
            addUtxosToActiveFederation(TWO_P2SH_P2WSH_UTXOS_OF_EIGHT_BTCS);

            // Act
            Coin estimatedFeesForPegout = bridgeSupport.getEstimatedFeesForPegOutAmount(toWeis(MIN_PEGOUT_TX_VALUE));

            // Assert
            assertEquals(Coin.valueOf(56_700L), estimatedFeesForPegout);
        }

        @Test
        void getEstimatedFeesForPegOutAmount_withOneBtc_withP2shP2wshErpFederation_withNoPegoutRequests_shouldEstimateFeesFromTransactionSimulation() throws Exception {
            // Arrange
            addUtxosToActiveFederation(TWO_P2SH_P2WSH_UTXOS_OF_EIGHT_BTCS);

            // Act
            Coin estimatedFeesForPegout = bridgeSupport.getEstimatedFeesForPegOutAmount(toWeis(Coin.COIN));

            // Assert
            assertEquals(Coin.valueOf(56_700L), estimatedFeesForPegout);
        }

        @ParameterizedTest
        @CsvSource({
            "1, 109200",
            "2, 112600",
            "3, 116000"
        })
        void getEstimatedFeesForPegOutAmount_withP2shP2wshErpFederation_withPegoutRequestsOfMinPegoutValue_shouldEstimateFeesFromTransactionSimulation(
            int pegoutRequestCount, long expectedEstimatedFees) throws Exception {
            // Arrange
            addUtxosToActiveFederation(FOUR_P2SH_P2WSH_UTXOS_OF_ONE_BTC);
            addPegoutRequests(pegoutRequestCount, MIN_PEGOUT_TX_VALUE);

            // Act
            Coin estimatedFeesForPegout = bridgeSupport.getEstimatedFeesForPegOutAmount(toWeis(Coin.COIN));

            // Assert
            assertEquals(Coin.valueOf(expectedEstimatedFees), estimatedFeesForPegout);
        }

        @ParameterizedTest
        @CsvSource({
            "1, 60100",
            "2, 63500",
            "3, 66900"
        })
        void getEstimatedFeesForPegOutAmount_withP2shP2wshErpFederation_withPegoutRequestOfMinPegoutValue_shouldEstimateFeesFromTransactionSimulation(
            int pegoutRequestCount, long expectedEstimatedFees) throws Exception {
            // Arrange
            addUtxosToActiveFederation(FOUR_P2SH_P2WSH_UTXOS_OF_ONE_BTC);
            addPegoutRequests(pegoutRequestCount, MIN_PEGOUT_TX_VALUE);

            // Act
            Coin estimatedFeesForPegout = bridgeSupport.getEstimatedFeesForPegOutAmount(toWeis(MIN_PEGOUT_TX_VALUE));

            // Assert
            assertEquals(Coin.valueOf(expectedEstimatedFees), estimatedFeesForPegout);
        }

        @Test
        void getEstimatedFeesForPegOutAmount_withP2shP2wshErpFederation_withTwoMinPegoutRequestsOfThreeTimesMinPegoutValue_shouldEstimateFeesFromTransactionSimulation() throws Exception {
            // Arrange
            addUtxosToActiveFederation(FOUR_P2SH_P2WSH_UTXOS_OF_ONE_BTC);
            addPegoutRequests(2, MIN_PEGOUT_TX_VALUE);

            // Act
            Coin estimatedFeesForPegout = bridgeSupport.getEstimatedFeesForPegOutAmount(toWeis(MIN_PEGOUT_TX_VALUE.multiply(3)));

            // Assert
            assertEquals(Coin.valueOf(63_500L), estimatedFeesForPegout);
        }

        @Test
        void getEstimatedFeesForPegOutAmount_withP2shP2wshErpFederation_withTwoMinPegoutRequestsOfTwoBtc_shouldEstimateFeesFromTransactionSimulation() throws Exception {
            // Arrange
            addUtxosToActiveFederation(FOUR_P2SH_P2WSH_UTXOS_OF_ONE_BTC);
            addPegoutRequests(2, MIN_PEGOUT_TX_VALUE);

            // Act
            Coin estimatedFeesForPegout = bridgeSupport.getEstimatedFeesForPegOutAmount(toWeis(Coin.COIN.multiply(2)));

            // Assert
            assertEquals(Coin.valueOf(161_700L), estimatedFeesForPegout);
        }

        @Test
        void getEstimatedFeesForPegOutAmount_withP2shP2wshErpFederation_withTwoMinPegoutRequestsOfMinPegoutValuePlusOneThousandSatoshis_shouldEstimateFeesFromTransactionSimulation() throws Exception {
            // Arrange
            addUtxosToActiveFederation(FOUR_P2SH_P2WSH_UTXOS_OF_ONE_BTC);
            addPegoutRequests(2, MIN_PEGOUT_TX_VALUE);

            // Act
            Coin estimatedFeesForPegout = bridgeSupport.getEstimatedFeesForPegOutAmount(toWeis(MIN_PEGOUT_TX_VALUE.add(Coin.valueOf(1_000L))));

            // Assert
            assertEquals(Coin.valueOf(63_500L), estimatedFeesForPegout);
        }

        @Test
        void getEstimatedFeesForPegOutAmount_withP2shP2wshErpFederation_withAmountBelowMinPegoutValue_shouldThrowBridgeIllegalArgumentException() {
            // Arrange
            addUtxosToActiveFederation(FOUR_P2SH_P2WSH_UTXOS_OF_ONE_BTC);

            // Act & Assert
            assertThrows(
                BridgeIllegalArgumentException.class,
                () -> bridgeSupport.getEstimatedFeesForPegOutAmount(toWeis(MIN_PEGOUT_TX_VALUE.subtract(Coin.SATOSHI)))
            );
        }

        @Test
        void getEstimatedFeesForPegOutAmount_withP2shP2wshErpFederation_withAmountBelowMinPegoutValueForOneWei_shouldThrowBridgeIllegalArgumentException() {
            // Arrange
            addUtxosToActiveFederation(FOUR_P2SH_P2WSH_UTXOS_OF_ONE_BTC);
            co.rsk.core.Coin oneWei = new co.rsk.core.Coin(BigInteger.ONE);

            // Act & Assert
            assertThrows(
                BridgeIllegalArgumentException.class,
                () -> bridgeSupport.getEstimatedFeesForPegOutAmount(toWeis(MIN_PEGOUT_TX_VALUE).subtract(oneWei))
            );
        }

        @Test
        void getEstimatedFeesForPegOutAmount_withP2shP2wshErpFederation_whenPegoutTransactionCannotBeBuilt_shouldThrowBridgeIllegalArgumentException() {
            // Act & Assert
            assertThrows(
                BridgeIllegalArgumentException.class,
                () -> bridgeSupport.getEstimatedFeesForPegOutAmount(toWeis(MIN_PEGOUT_TX_VALUE))
            );
        }
    }

    void setUpBridgeAndFederationSupport(ActivationConfig.ForBlock activationConfig) {
        setUpFeePerKb();
        Repository repository = createRepository();
        bridgeStorageProvider = new BridgeStorageProvider(repository, NETWORK_PARAMETERS, activationConfig);
        bridgeStorageAccessor = new InMemoryStorage();
        federationStorageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);
        FederationSupport federationSupport = FederationSupportBuilder.builder()
            .withFederationConstants(FEDERATION_CONSTANTS)
            .withActivations(activationConfig)
            .withFederationStorageProvider(federationStorageProvider)
            .build();
        bridgeSupport = BridgeSupportBuilder.builder()
            .withBridgeConstants(BRIDGE_CONSTANTS)
            .withProvider(bridgeStorageProvider)
            .withActivations(activationConfig)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .build();
    }

    private void addUtxosToActiveFederation(List<UTXO> utxos) {
        bridgeStorageAccessor.saveToRepository(NEW_FEDERATION_BTC_UTXOS_KEY.getKey(), utxos, BridgeSerializationUtils::serializeUTXOList);
    }

    private void setUpFeePerKb() {
        feePerKbSupport = mock(FeePerKbSupport.class);
        Coin feePerKb = Coin.MILLICOIN;
        when(feePerKbSupport.getFeePerKb()).thenReturn(feePerKb);
    }

    private void addPegoutRequests(int pegoutRequestCount, Coin value) throws IOException {
        ReleaseRequestQueue releaseRequestQueue = bridgeStorageProvider.getReleaseRequestQueue();
        for (int i = 0; i < pegoutRequestCount; i++) {
            Address receiver = BitcoinTestUtils.createP2PKHAddress(NETWORK_PARAMETERS, "receiver" + i);
            releaseRequestQueue.add(receiver, value);
        }
    }

    private co.rsk.core.Coin toWeis(Coin bitcoinAmount) {
        return co.rsk.core.Coin.fromBitcoin(bitcoinAmount);
    }

}
