/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.peg;

import static co.rsk.RskTestUtils.createRepository;
import static co.rsk.peg.BridgeSupport.MAX_OUTPUTS_NUMBER;
import static co.rsk.peg.BridgeSupportTestUtil.setUpFlyoverUtxoInStorage;
import static co.rsk.peg.ReleaseTransactionAssertions.*;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.COULD_NOT_ADJUST_DOWNWARDS;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.DUSTY_SEND_REQUESTED;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.EXCEED_MAX_TRANSACTION_SIZE;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.INSUFFICIENT_MONEY;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.SUCCESS;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.createHash;
import static co.rsk.peg.bitcoin.BitcoinUtils.BTC_TX_VERSION_1;
import static co.rsk.peg.bitcoin.BitcoinUtils.BTC_TX_VERSION_2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.ReleaseRequestQueue.Entry;
import co.rsk.peg.ReleaseTransactionBuilder.BuildResult;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.bitcoin.FlyoverRedeemScriptBuilderImpl;
import co.rsk.peg.bitcoin.RedeemScriptCreationException;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.ErpFederation;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationArgs;
import co.rsk.peg.federation.FederationFactory;
import co.rsk.peg.federation.FederationMember;
import co.rsk.peg.federation.P2shErpFederationBuilder;
import co.rsk.peg.federation.P2shP2wshErpFederationBuilder;
import co.rsk.peg.federation.StandardMultiSigFederationBuilder;
import co.rsk.test.builders.UTXOBuilder;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ReleaseTransactionBuilderTest {
    private static final BridgeConstants BRIDGE_MAINNET_CONSTANTS = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters BTC_MAINNET_PARAMS = BRIDGE_MAINNET_CONSTANTS.getBtcParams();
    private static final Context BTC_MAINNET_CONTEXT = new Context(BTC_MAINNET_PARAMS);

    private static final ActivationConfig.ForBlock ALL_ACTIVATIONS = ActivationConfigsForTest.all().forBlock(0);
    private static final ActivationConfig.ForBlock FINGERROOT_ACTIVATIONS =
        ActivationConfigsForTest.fingerroot500().forBlock(0);
    private static final ActivationConfig.ForBlock IRIS_ACTIVATIONS = ActivationConfigsForTest.iris300().forBlock(0);
    private static final ActivationConfig.ForBlock PAPYRUS_ACTIVATIONS =
        ActivationConfigsForTest.papyrus200().forBlock(0);

    private static final Coin DUST_VALUE = MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT.minus(Coin.SATOSHI);
    private static final Coin FEE_PER_KB_1000_SATOSHIS = Coin.SATOSHI.multiply(1000);
    private static final Coin HIGH_FEE_PER_KB = Coin.valueOf(1_000_000);
    private static final Coin MINIMUM_PEGIN_TX_VALUE_IRIS =
        BRIDGE_MAINNET_CONSTANTS.getMinimumPeginTxValue(IRIS_ACTIVATIONS);
    private static final Coin MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS =
        BRIDGE_MAINNET_CONSTANTS.getMinimumPeginTxValue(ALL_ACTIVATIONS);
    private static final Coin MINIMUM_PEGOUT_TX_VALUE = BRIDGE_MAINNET_CONSTANTS.getMinimumPegoutTxValue();
    private static final Coin MOCK_FEE_PER_KB = Coin.MILLICOIN.multiply(2);
    private static final Coin THOUSAND_SATOSHIS = Coin.valueOf(1000);

    private static final int EXPECTED_NUMBER_OF_CHANGE_OUTPUTS = 1;
    private static final int STANDARD_MULTISIG_UTXO_COUNT_OVER_MAX_TX_SIZE = 277;
    private static final int UTXO_COUNT_JUST_UNDER_MAX_STANDARD_TX_SIZE = 276;
    private static final Keccak256 FLYOVER_DERIVATION_HASH = BRIDGE_MAINNET_CONSTANTS.getProposedFederationFlyoverPrefix();
    private static final Sha256Hash BTC_TX_HASH_FLYOVER_UTXO = createHash(10_000);

    private ActivationConfig.ForBlock activations;
    private Coin feePerKb;

    private void setUpActivations(ActivationConfig.ForBlock activations) {
        this.activations = activations;
    }

    private void setUpFeePerKb(Coin feePerKb) {
        this.feePerKb = feePerKb;
    }

    private static Address recipientAddressFromPrivateKeyOffset(int keyOffset) {
        BigInteger seed = BigInteger.valueOf(keyOffset);
        return BtcECKey.fromPrivate(seed).toAddress(BTC_MAINNET_PARAMS);
    }

    private static void setUpFlyoverUtxosInStorage(List<UTXO> flyoverUtxos, Script flyoverOutputScript, Federation federation, BridgeStorageProvider provider) {
        for (UTXO flyoverUtxo : flyoverUtxos) {
            setUpFlyoverUtxoInStorage(flyoverUtxo, flyoverOutputScript, federation, provider, FLYOVER_DERIVATION_HASH);
        }
    }

    /**
     * Mainnet {@link Wallet} + {@link ReleaseTransactionBuilder#buildSvpFundTransaction} success and
     * failure paths (UTXO selection, dust, invalid flyover prefix, null args).
     */
    @Nested
    class BuildSvpFundTransactionTest {
        private static final int DEFAULT_UTXO_COUNT = 2;
        private static final int MULTI_UTXO_COUNT = 4;

        private final Federation activeP2shErpFederation = P2shErpFederationBuilder.builder().build();
        private final Address activeP2shErpFederationAddress = activeP2shErpFederation.getAddress();
        private final Federation activeP2shP2wshErpFederation = P2shP2wshErpFederationBuilder.builder().build();
        private final Address activeP2shP2wshErpFederationAddress = activeP2shP2wshErpFederation.getAddress();
        private final Federation p2shP2wshErpProposedFederation = P2shP2wshErpFederationBuilder.builder().build();
        private final Keccak256 proposedFlyoverPrefix = BRIDGE_MAINNET_CONSTANTS.getProposedFederationFlyoverPrefix();
        private final Coin svpFundTxOutputsValue = BRIDGE_MAINNET_CONSTANTS.getSvpFundTxOutputsValue();
        private final Coin totalSvpFundPaymentOutputsValue = svpFundTxOutputsValue.multiply(2);
        private BridgeStorageProvider bridgeStorageProvider;

        @BeforeEach
        void setUp() {
            setUpActivations(ALL_ACTIVATIONS);
            Repository repository = createRepository();
            bridgeStorageProvider = new BridgeStorageProvider(
                repository,
                BTC_MAINNET_PARAMS,
                activations
            );
        }

        @Test
        void buildSvpFundTransaction_withAFederationWithEnoughUTXOs_shouldCreateSvpFundTx() {
            // Arrange
            List<UTXO> utxos = UTXOBuilder.builder()
                .withScriptPubKey(activeP2shErpFederation.getP2SHScript())
                .withValue(Coin.COIN)
                .buildMany(DEFAULT_UTXO_COUNT, i -> BitcoinTestUtils.createHash(i + 1));

            // Act
            ReleaseTransactionBuilder.BuildResult buildResult = buildSvpFundTransaction(activeP2shErpFederation, utxos);

            // Assert
            assertSuccessfulSvpFundTransaction(
                buildResult,
                activeP2shErpFederationAddress,
                List.of(utxos.get(0))
            );
        }

        @Test
        void buildSvpFundTransaction_withAFederationWithOneFlyoverUTXO_shouldCreateSvpFundTx() {
            // Arrange
            Script flyoverRedeemScript = FlyoverRedeemScriptBuilderImpl.builder().of(
                FLYOVER_DERIVATION_HASH,
                activeP2shErpFederation.getRedeemScript()
            );
            Script flyoverOutputScript = PegUtils.getFlyoverFederationOutputScript(flyoverRedeemScript, activeP2shErpFederation.getFormatVersion());
            UTXO flyoverUtxo = UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(flyoverOutputScript)
                .withTransactionHash(BTC_TX_HASH_FLYOVER_UTXO)
                .build();
            setUpFlyoverUtxoInStorage(flyoverUtxo, flyoverOutputScript, activeP2shErpFederation, bridgeStorageProvider, FLYOVER_DERIVATION_HASH);
            List<UTXO> utxos = List.of(flyoverUtxo);

            // Act
            ReleaseTransactionBuilder.BuildResult buildResult = buildSvpFundTransaction(activeP2shErpFederation, utxos);

            // Assert
            assertSuccessfulSvpFundTransaction(
                buildResult,
                activeP2shErpFederationAddress,
                utxos
            );
        }

        @Test
        void buildSvpFundTransaction_whenActiveFederationIsP2shP2wshErp_shouldSpendUtxosWithExpectedWitnessFormat() {
            // Arrange
            List<UTXO> utxos = UTXOBuilder.builder()
                .withScriptPubKey(activeP2shP2wshErpFederation.getP2SHScript())
                .withValue(Coin.COIN)
                .buildMany(DEFAULT_UTXO_COUNT, i -> BitcoinTestUtils.createHash(i + 1));

            // Act
            ReleaseTransactionBuilder.BuildResult buildResult = buildSvpFundTransaction(activeP2shP2wshErpFederation, utxos);

            // Assert
            assertSuccessfulSvpFundTransaction(
                buildResult,
                activeP2shP2wshErpFederationAddress,
                List.of(utxos.get(0))
            );

            BtcTransaction svpFundTransaction = buildResult.btcTx();

            assertReleaseTxInputsP2shP2wshErp(
                svpFundTransaction,
                activeP2shP2wshErpFederation.getRedeemScript(),
                utxos,
                buildResult.selectedUTXOs(),
                1
            );
        }

        @Test
        void buildSvpFundTransaction_whenNoSingleUtxoCoversTotalSpend_shouldCreateSvpFundTxSelectingMultipleInputs() {
            // Arrange
            List<UTXO> utxos = UTXOBuilder.builder()
                .withScriptPubKey(activeP2shP2wshErpFederation.getP2SHScript())
                .withValue(svpFundTxOutputsValue)
                .buildMany(MULTI_UTXO_COUNT, i -> BitcoinTestUtils.createHash(i + 1));
            utxos.forEach(utxo -> assertTrue(
                utxo.getValue().compareTo(totalSvpFundPaymentOutputsValue) < 0,
                "Each UTXO should be insufficient on its own to fund both SVP payment outputs"
            ));

            // Act
            ReleaseTransactionBuilder.BuildResult buildResult = buildSvpFundTransaction(activeP2shP2wshErpFederation, utxos);

            // Assert
            List<UTXO> expectedSelectedUtxos = utxos.subList(0, 3);

            assertSuccessfulSvpFundTransaction(
                buildResult,
                activeP2shP2wshErpFederationAddress,
                expectedSelectedUtxos
            );

            List<UTXO> selectedUtxos = buildResult.selectedUTXOs();

            BtcTransaction svpFundTransaction = buildResult.btcTx();
            assertReleaseTxInputsP2shP2wshErp(
                svpFundTransaction,
                activeP2shP2wshErpFederation.getRedeemScript(),
                utxos,
                selectedUtxos,
                expectedSelectedUtxos.size()
            );
        }

        @Test
        void buildSvpFundTransaction_withAFederationWithoutUTXOs_shouldThrowInsufficientMoneyResponseCode() {
            // Act
            ReleaseTransactionBuilder.BuildResult buildResult = buildSvpFundTransaction(
                activeP2shP2wshErpFederation,
                Collections.emptyList()
            );

            // Assert
            assertFailedBuildResult(INSUFFICIENT_MONEY, buildResult);
        }

        @Test
        void buildSvpFundTransaction_withAFederationWith1DustUTXO_shouldThrowInsufficientMoneyResponseCode() {
            // Arrange
            Coin dustUtxoValue = MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS.subtract(Coin.SATOSHI);
            assertTrue(
                dustUtxoValue.compareTo(MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS) < 0,
                "UTXO value should be below the minimum pegin threshold"
            );
            List<UTXO> utxos = List.of(
                UTXOBuilder.builder()
                    .withScriptPubKey(activeP2shP2wshErpFederation.getP2SHScript())
                    .withValue(dustUtxoValue)
                    .build()
            );

            // Act
            ReleaseTransactionBuilder.BuildResult buildResult = buildSvpFundTransaction(activeP2shP2wshErpFederation, utxos);

            // Assert
            assertFailedBuildResult(INSUFFICIENT_MONEY, buildResult);
        }

        @Test
        void buildSvpFundTransaction_withDustValueAsSvpFundTxOutputsValue_shouldReturnDustySendRequestResponseCode() {
            // Arrange
            List<UTXO> utxos = UTXOBuilder.builder()
                .withScriptPubKey(activeP2shP2wshErpFederation.getP2SHScript())
                .withValue(Coin.COIN)
                .buildMany(DEFAULT_UTXO_COUNT, i -> BitcoinTestUtils.createHash(i + 1));

            // Act
            ReleaseTransactionBuilder.BuildResult buildResult = buildSvpFundTransaction(
                activeP2shP2wshErpFederation,
                utxos,
                Coin.SATOSHI
            );

            // Assert
            assertFailedBuildResult(DUSTY_SEND_REQUESTED, buildResult);
        }

        @Test
        void buildSvpFundTransaction_withNullProposedFlyoverPrefix_shouldThrowRedeemScriptCreationException() {
            // Arrange
            ReleaseTransactionBuilder releaseTransactionBuilder = getReleaseTransactionBuilderForMainnet(
                activeP2shP2wshErpFederation,
                UTXOBuilder.builder()
                    .withScriptPubKey(activeP2shP2wshErpFederation.getP2SHScript())
                    .withValue(Coin.COIN)
                    .buildMany(DEFAULT_UTXO_COUNT, i -> BitcoinTestUtils.createHash(i + 1))
            );

            // Act & Assert
            assertThrows(RedeemScriptCreationException.class, () -> releaseTransactionBuilder.buildSvpFundTransaction(
                p2shP2wshErpProposedFederation,
                null,
                Coin.SATOSHI
            ));
        }

        @Test
        void buildSvpFundTransaction_withInvalidProposedFlyoverPrefix_shouldThrowRedeemScriptCreationException() {
            // Arrange
            ReleaseTransactionBuilder releaseTransactionBuilder = getReleaseTransactionBuilderForMainnet(
                activeP2shP2wshErpFederation,
                UTXOBuilder.builder()
                    .withScriptPubKey(activeP2shP2wshErpFederation.getP2SHScript())
                    .withValue(Coin.COIN)
                    .buildMany(DEFAULT_UTXO_COUNT, i -> BitcoinTestUtils.createHash(i + 1))
            );

            // Act & Assert
            assertThrows(RedeemScriptCreationException.class, () -> releaseTransactionBuilder.buildSvpFundTransaction(
                p2shP2wshErpProposedFederation,
                Keccak256.ZERO_HASH,
                Coin.SATOSHI
            ));
        }

        @Test
        void buildSvpFundTransaction_withNullProposedFederation_shouldThrowNullPointerException() {
            // Arrange
            ReleaseTransactionBuilder releaseTransactionBuilder = getReleaseTransactionBuilderForMainnet(
                activeP2shP2wshErpFederation,
                UTXOBuilder.builder()
                    .withScriptPubKey(activeP2shP2wshErpFederation.getP2SHScript())
                    .withValue(Coin.COIN)
                    .buildMany(DEFAULT_UTXO_COUNT, i -> BitcoinTestUtils.createHash(i + 1))
            );

            // Act & Assert
            assertThrows(NullPointerException.class, () -> releaseTransactionBuilder.buildSvpFundTransaction(
                null,
                proposedFlyoverPrefix,
                Coin.SATOSHI
            ));
        }

        @Test
        void buildSvpFundTransaction_withNullAsValueTransferred_shouldThrowNullPointerException() {
            // Arrange
            ReleaseTransactionBuilder releaseTransactionBuilder = getReleaseTransactionBuilderForMainnet(
                activeP2shP2wshErpFederation,
                UTXOBuilder.builder()
                    .withScriptPubKey(activeP2shP2wshErpFederation.getP2SHScript())
                    .withValue(Coin.COIN)
                    .buildMany(DEFAULT_UTXO_COUNT, i -> BitcoinTestUtils.createHash(i + 1))
            );

            // Act & Assert
            assertThrows(NullPointerException.class, () -> releaseTransactionBuilder.buildSvpFundTransaction(
                p2shP2wshErpProposedFederation,
                proposedFlyoverPrefix,
                null
            ));
        }

        private ReleaseTransactionBuilder.BuildResult buildSvpFundTransaction(
            Federation activeFederation,
            List<UTXO> utxos
        ) {
            return buildSvpFundTransaction(activeFederation, utxos, svpFundTxOutputsValue);
        }

        private ReleaseTransactionBuilder.BuildResult buildSvpFundTransaction(
            Federation activeFederation,
            List<UTXO> utxos,
            Coin outputsValue
        ) {
            return getReleaseTransactionBuilderForMainnet(activeFederation, utxos)
                .buildSvpFundTransaction(p2shP2wshErpProposedFederation, proposedFlyoverPrefix, outputsValue);
        }

        private void assertSvpFundTransactionPaymentOutputs(BtcTransaction svpFundTransaction) {
            TransactionOutput outputToProposedFederation = svpFundTransaction.getOutput(0);
            assertEquals(
                svpFundTxOutputsValue,
                outputToProposedFederation.getValue(),
                "SVP fund output to proposed federation should match configured value"
            );
            assertEquals(
                p2shP2wshErpProposedFederation.getAddress(),
                outputToProposedFederation.getAddressFromP2SH(BTC_MAINNET_PARAMS),
                "SVP fund first output should be sent to the proposed federation address"
            );

            TransactionOutput outputToFlyoverProposedFederation = svpFundTransaction.getOutput(1);
            assertEquals(
                svpFundTxOutputsValue,
                outputToFlyoverProposedFederation.getValue(),
                "SVP fund output to flyover proposed federation should match configured value"
            );
            Address flyoverFederationAddress = PegUtils.getFlyoverFederationAddress(
                BTC_MAINNET_PARAMS,
                proposedFlyoverPrefix,
                p2shP2wshErpProposedFederation
            );
            assertEquals(
                flyoverFederationAddress,
                outputToFlyoverProposedFederation.getAddressFromP2SH(BTC_MAINNET_PARAMS),
                "SVP fund second output should be sent to the flyover proposed federation address"
            );
            assertEquals(
                totalSvpFundPaymentOutputsValue,
                outputToProposedFederation.getValue().add(outputToFlyoverProposedFederation.getValue()),
                "SVP fund payment outputs should keep their full value when recipientsPayFees is false"
            );
        }

        private void assertSvpFundTransactionChangeOutput(
            BtcTransaction svpFundTransaction,
            Address activeFederationAddress
        ) {
            Coin inputTotal = svpFundTransaction.getInputSum();
            TransactionOutput changeOutput = svpFundTransaction.getOutput(2);
            assertEquals(
                activeFederationAddress,
                changeOutput.getAddressFromP2SH(BTC_MAINNET_PARAMS),
                "SVP fund change output should be sent back to the active federation address"
            );

            Coin fee = svpFundTransaction.getFee();
            Coin expectedChangeValue = inputTotal.subtract(totalSvpFundPaymentOutputsValue).subtract(fee);
            assertEquals(
                expectedChangeValue,
                changeOutput.getValue(),
                "SVP fund change output value should equal inputs minus payment outputs minus fee"
            );
            assertEquals(
                inputTotal,
                totalSvpFundPaymentOutputsValue.add(changeOutput.getValue()).add(fee),
                "SVP fund transaction inputs should equal payment outputs plus change plus fee"
            );
        }

        private void assertSuccessfulSvpFundTransaction(
            BuildResult buildResult,
            Address activeFederationAddress,
            List<UTXO> expectedSelectedUtxos
        ) {
            assertEquals(SUCCESS, buildResult.responseCode());

            BtcTransaction svpFundTransaction = buildResult.btcTx();
            assertBtcTxVersionIs2(svpFundTransaction);
            assertEquals(
                3,
                svpFundTransaction.getOutputs().size(),
                "SVP fund transaction should have two payment outputs and one change output"
            );
            assertSvpFundTransactionPaymentOutputs(svpFundTransaction);

            List<UTXO> selectedUtxos = buildResult.selectedUTXOs();
            assertEquals(
                expectedSelectedUtxos,
                selectedUtxos,
                "SVP fund transaction should select the minimum sufficient UTXO set"
            );
            assertSvpFundTransactionChangeOutput(svpFundTransaction, activeFederationAddress);
        }

        private ReleaseTransactionBuilder getReleaseTransactionBuilderForMainnet(
            Federation activeFederation,
            List<UTXO> utxos
        ) {
            Wallet thisWallet = BridgeUtils.getFederationSpendWallet(
                BTC_MAINNET_CONTEXT,
                activeFederation,
                utxos,
                true,
                bridgeStorageProvider
            );

            return new ReleaseTransactionBuilder(
                BTC_MAINNET_PARAMS,
                thisWallet,
                activeFederation.getFormatVersion(),
                activeFederation.getAddress(),
                MOCK_FEE_PER_KB,
                ALL_ACTIVATIONS
            );
        }
    }

    @Nested
    class BuildAmountToTest {

        private static final Address RECIPIENT_ADDRESS = ReleaseTransactionBuilderTest.recipientAddressFromPrivateKeyOffset(2100);

        private Federation federation;
        private int federationFormatVersion;
        private Address federationAddress;
        private List<UTXO> federationUTXOs;
        private Script federationOutputScript;
        private Script federationRedeemScript;
        private Wallet wallet;
        private BridgeStorageProvider bridgeStorageProvider;
        private UTXO flyoverUtxo;

        @BeforeEach
        void setup() {
            setUpActivations(IRIS_ACTIVATIONS);
            setUpFeePerKb(BtcTransaction.DEFAULT_TX_FEE);
            federation = StandardMultiSigFederationBuilder.builder().build();
            federationFormatVersion = federation.getFormatVersion();
            federationAddress = federation.getAddress();
            federationOutputScript = federation.getP2SHScript();
            federationRedeemScript = federation.getRedeemScript();
            Repository repository = createRepository();
            bridgeStorageProvider = new BridgeStorageProvider(
                repository,
                BTC_MAINNET_PARAMS,
                activations
            );

            Script flyoverRedeemScript = FlyoverRedeemScriptBuilderImpl.builder().of(
                FLYOVER_DERIVATION_HASH,
                federation.getRedeemScript()
            );
            Script flyoverOutputScript = PegUtils.getFlyoverFederationOutputScript(flyoverRedeemScript, federation.getFormatVersion());
            flyoverUtxo = UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(flyoverOutputScript)
                .withTransactionHash(BTC_TX_HASH_FLYOVER_UTXO)
                .build();
            setUpFlyoverUtxoInStorage(flyoverUtxo, flyoverOutputScript, federation, bridgeStorageProvider, FLYOVER_DERIVATION_HASH);
        }

        @Test
        void buildAmountTo_whenFedHasNoUTXOs_shouldReturnInsufficientMoney() {
            // Arrange
            federationUTXOs = new ArrayList<>();
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder();

            // Act
            BuildResult buildResult = releaseTransactionBuilder.buildAmountTo(RECIPIENT_ADDRESS, MINIMUM_PEGOUT_TX_VALUE);

            // Assert
            assertFailedBuildResult(INSUFFICIENT_MONEY, buildResult);
        }

        @Test
        void buildAmountTo_whenRSKIP201IsNotActive_shouldCreatePegoutTxWithBtcVersion1() {
            // Arrange
            setUpActivations(PAPYRUS_ACTIVATIONS);
            int numberOfUtxos = 10;
            Coin minimumPeginTxValue = BRIDGE_MAINNET_CONSTANTS.getMinimumPeginTxValue(PAPYRUS_ACTIVATIONS);
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(minimumPeginTxValue)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder();

            // Act
            BuildResult buildResult = releaseTransactionBuilder.buildAmountTo(RECIPIENT_ADDRESS, MINIMUM_PEGOUT_TX_VALUE);

            // Assert
            assertSuccessfulStandardMultisigPegout(
                buildResult,
                BTC_TX_VERSION_1,
                1,
                MINIMUM_PEGOUT_TX_VALUE,
                PegoutChangeOutputExpectation.NON_DUST_CHANGE
            );
        }

        @Test
        void buildAmountTo_whenSingleUtxoCoversRequestedAmount_shouldCreatePegoutTx() {
            // Arrange
            int numberOfUtxos = 10;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MINIMUM_PEGIN_TX_VALUE_IRIS)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder();

            // Act
            BuildResult buildResult = releaseTransactionBuilder.buildAmountTo(RECIPIENT_ADDRESS, MINIMUM_PEGOUT_TX_VALUE);

            // Assert
            assertSuccessfulStandardMultisigPegout(
                buildResult,
                BTC_TX_VERSION_2,
                1,
                MINIMUM_PEGOUT_TX_VALUE,
                PegoutChangeOutputExpectation.NON_DUST_CHANGE
            );
        }

        @Test
        void buildAmountTo_whenMultipleUtxosCoverRequestedAmount_shouldCreatePegoutTx() {
            // Arrange
            int numberOfUtxos = 10;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MINIMUM_PEGOUT_TX_VALUE)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            federationUTXOs.add(flyoverUtxo);

            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder();
            Coin amountToSend = MINIMUM_PEGOUT_TX_VALUE.add(THOUSAND_SATOSHIS);

            // Act
            BuildResult buildResult = releaseTransactionBuilder.buildAmountTo(RECIPIENT_ADDRESS, amountToSend);

            // Assert
            assertSuccessfulStandardMultisigPegout(
                buildResult,
                BTC_TX_VERSION_2,
                2,
                amountToSend,
                PegoutChangeOutputExpectation.NON_DUST_CHANGE
            );
        }

        @Test
        void buildAmountTo_whenWalletHasExactFundsForPegoutRequest_shouldCreatePegoutTxWithNoChangeOutput() {
            // Arrange
            federationUTXOs = List.of(
                UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(MINIMUM_PEGOUT_TX_VALUE)
                    .build()
            );
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder();

            // Act
            BuildResult buildResult = releaseTransactionBuilder.buildAmountTo(RECIPIENT_ADDRESS, MINIMUM_PEGOUT_TX_VALUE);

            // Assert
            assertSuccessfulStandardMultisigPegout(
                buildResult,
                BTC_TX_VERSION_2,
                federationUTXOs.size(),
                MINIMUM_PEGOUT_TX_VALUE,
                PegoutChangeOutputExpectation.ONLY_USER_OUTPUTS
            );
        }

        @Test
        void buildAmountTo_whenInsufficientFundsForPegoutRequest_shouldReturnInsufficientMoney() {
            // Arrange
            federationUTXOs = List.of(UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MINIMUM_PEGOUT_TX_VALUE)
                .build()
            );
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder();
            Coin amountExceedingFederationBalance = MINIMUM_PEGOUT_TX_VALUE.add(Coin.SATOSHI);

            // Act
            BuildResult buildResult = releaseTransactionBuilder.buildAmountTo(RECIPIENT_ADDRESS, amountExceedingFederationBalance);

            // Assert
            assertFailedBuildResult(INSUFFICIENT_MONEY, buildResult);
        }

        @Test
        void buildAmountTo_whenOriginalChangeIsMaxDustValue_shouldCreatePegoutTxDecrementingFirstOutputAndSettingNonDustChange() {
            // Arrange
            Coin utxoAmount = MINIMUM_PEGOUT_TX_VALUE.add(DUST_VALUE);
            federationUTXOs = List.of(
                UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(utxoAmount)
                .build()
            );
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder();

            // Act
            BuildResult buildResult = releaseTransactionBuilder.buildAmountTo(RECIPIENT_ADDRESS, MINIMUM_PEGOUT_TX_VALUE);

            // Assert
            assertSuccessfulStandardMultisigPegout(
                buildResult,
                BTC_TX_VERSION_2,
                1,
                MINIMUM_PEGOUT_TX_VALUE,
                PegoutChangeOutputExpectation.DUST_CHANGE
            );
        }

        @Test
        void buildAmountTo_whenChangeIsMinNonDustValue_shouldCreatePegoutTxWithNoModificationInTheValues() {
            // Arrange
            federationUTXOs = List.of(UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MINIMUM_PEGOUT_TX_VALUE.add(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT))
                .build()
            );
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder();

            // Act
            BuildResult buildResult = releaseTransactionBuilder.buildAmountTo(RECIPIENT_ADDRESS, MINIMUM_PEGOUT_TX_VALUE);

            // Assert
            assertSuccessfulStandardMultisigPegout(
                buildResult,
                BTC_TX_VERSION_2,
                1,
                MINIMUM_PEGOUT_TX_VALUE,
                PegoutChangeOutputExpectation.NON_DUST_CHANGE
            );
        }

        @Test
        void buildAmountTo_whenOriginalChangeIsOneSatoshi_shouldCreatePegoutTxDecrementingFirstOutputAndSettingNonDustChange() {
            // Arrange
            federationUTXOs = List.of(UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MINIMUM_PEGOUT_TX_VALUE.add(Coin.SATOSHI))
                .build()
            );
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder();

            // Act
            BuildResult buildResult = releaseTransactionBuilder.buildAmountTo(RECIPIENT_ADDRESS, MINIMUM_PEGOUT_TX_VALUE);

            // Assert
            assertSuccessfulStandardMultisigPegout(
                buildResult,
                BTC_TX_VERSION_2,
                1,
                MINIMUM_PEGOUT_TX_VALUE,
                PegoutChangeOutputExpectation.DUST_CHANGE
            );
        }

        @Test
        void buildAmountTo_whenUtxosAreMinimumNonDustValue_shouldReturnCouldNotAdjustDownwards() {
            // Spending an input with a p2sh script costs more than MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT.
            // Therefore, if the federation has only UTXOs with that minimum non-dust value,
            // it won't be possible to adjust downwards the pegout amount to avoid creating a dust output.
            // Arrange
            int numberOfUtxos = 10;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder();
            Coin amountToSend = MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT.multiply(numberOfUtxos);

            // Act
            BuildResult buildResult = releaseTransactionBuilder.buildAmountTo(RECIPIENT_ADDRESS, amountToSend);

            // Assert
            assertFailedBuildResult(COULD_NOT_ADJUST_DOWNWARDS, buildResult);
        }

        /*
         * DUST_VALUE is unrealistic; real pegouts must be at least
         * {@link BridgeConstants#getMinimumPegoutTxValue()}, but we use it to exercise the
         * DUSTY_SEND_REQUESTED path.
         */
        @Test
        void buildAmountTo_whenAmountIsTooSmall_shouldReturnDustySendRequested() {
            // Arrange
            int numberOfUtxos = 10;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MINIMUM_PEGIN_TX_VALUE_IRIS)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder();

            // Act
            BuildResult buildResult = releaseTransactionBuilder.buildAmountTo(RECIPIENT_ADDRESS, DUST_VALUE);

            // Assert
            assertFailedBuildResult(DUSTY_SEND_REQUESTED, buildResult);
        }

        @Test
        void buildAmountTo_whenEstimatedFeeIsTooHighAndUtxosAreNotEnough_shouldReturnCouldNotAdjustDownwards() {
            // Arrange
            setUpFeePerKb(HIGH_FEE_PER_KB);
            int numberOfUtxos = 3;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MINIMUM_PEGIN_TX_VALUE_IRIS)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder();

            // Act
            BuildResult buildResult = releaseTransactionBuilder.buildAmountTo(
                RECIPIENT_ADDRESS,
                MINIMUM_PEGOUT_TX_VALUE
            );

            // Assert
            assertFailedBuildResult(COULD_NOT_ADJUST_DOWNWARDS, buildResult);
        }

        @Test
        void buildAmountTo_whenEstimatedFeeIsHighAndUtxosAreEnough_shouldCreatePegoutTx() {
            // Arrange
            setUpFeePerKb(HIGH_FEE_PER_KB);
            int numberOfUtxos = 10;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(Coin.COIN)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder();
            Coin requestedAmount = Coin.COIN.subtract(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT);

            // Act
            BuildResult buildResult = releaseTransactionBuilder.buildAmountTo(RECIPIENT_ADDRESS, requestedAmount);

            // Assert
            assertSuccessfulStandardMultisigPegout(
                buildResult,
                BTC_TX_VERSION_2,
                1,
                requestedAmount,
                PegoutChangeOutputExpectation.NON_DUST_CHANGE
            );
        }

        @Test
        void buildAmountTo_whenTxExceedsMaxTxSize_shouldReturnExceedMaxTransactionSize() {
            // Arrange
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(Coin.COIN)
                .buildMany(STANDARD_MULTISIG_UTXO_COUNT_OVER_MAX_TX_SIZE, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder();
            Coin amountToSend = wallet.getBalance().subtract(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT);

            // Act
            BuildResult buildResult = releaseTransactionBuilder.buildAmountTo(RECIPIENT_ADDRESS, amountToSend);

            // Assert
            assertFailedBuildResult(EXCEED_MAX_TRANSACTION_SIZE, buildResult);
        }

        @Test
        void buildAmountTo_whenTxIsAlmostExceedingMaxTxSize_shouldCreatePegoutTx() {
            // Arrange
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(Coin.COIN)
                .buildMany(UTXO_COUNT_JUST_UNDER_MAX_STANDARD_TX_SIZE, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder();
            Coin requestedAmount = wallet.getBalance().subtract(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT);

            // Act
            BuildResult buildResult = releaseTransactionBuilder.buildAmountTo(RECIPIENT_ADDRESS, requestedAmount);

            // Assert
            assertSuccessfulStandardMultisigPegout(
                buildResult,
                BTC_TX_VERSION_2,
                federationUTXOs.size(),
                requestedAmount,
                PegoutChangeOutputExpectation.NON_DUST_CHANGE
            );
        }

        /**
         * Legacy scenario: no new Non-Standard ERP Federation is expected to be built in the future.
         * Since a Non-Standard ERP Federation is already part of the chain history, this test documents
         * that it was able to send pegouts while it was active.
         */
        @Test
        void buildAmountTo_whenNonStandardErpFederation_shouldCreatePegoutTx() {
            List<FederationMember> members = FederationMember.getFederationMembersFromKeys(
                Arrays.asList(
                    new BtcECKey(),
                    new BtcECKey(),
                    new BtcECKey()
                )
            );

            Instant creationTime = Instant.ofEpochMilli(1000L);
            FederationArgs federationArgs = new FederationArgs(members, creationTime, 0, BTC_MAINNET_PARAMS);

            ErpFederation nonStandardErpFederation = FederationFactory.buildNonStandardErpFederation(
                federationArgs,
                BRIDGE_MAINNET_CONSTANTS.getFederationConstants().getErpFedPubKeysList(),
                BRIDGE_MAINNET_CONSTANTS.getFederationConstants().getErpFedActivationDelay(),
                ALL_ACTIVATIONS
            );

            int numberOfUtxos = 2;
            List<UTXO> utxos = UTXOBuilder.builder()
                .withScriptPubKey(nonStandardErpFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));

            Wallet spendWallet = BridgeUtils.getFederationSpendWallet(
                BTC_MAINNET_CONTEXT,
                nonStandardErpFederation,
                utxos,
                false,
                mock(BridgeStorageProvider.class)
            );

            Address nonStandardErpFederationAddress = nonStandardErpFederation.getAddress();
            ReleaseTransactionBuilder releaseTransactionBuilder = new ReleaseTransactionBuilder(
                BTC_MAINNET_PARAMS,
                spendWallet,
                nonStandardErpFederation.getFormatVersion(),
                nonStandardErpFederationAddress,
                FEE_PER_KB_1000_SATOSHIS,
                ALL_ACTIVATIONS
            );

            Address pegoutRecipient = BitcoinTestUtils.createP2PKHAddress(BTC_MAINNET_PARAMS, "destinationAddress");
            Coin pegoutAmount = Coin.COIN.add(Coin.SATOSHI);

            ReleaseTransactionBuilder.BuildResult result = releaseTransactionBuilder.buildAmountTo(
                pegoutRecipient,
                pegoutAmount
            );
            assertEquals(ReleaseTransactionBuilder.Response.SUCCESS, result.responseCode());
        }

        private ReleaseTransactionBuilder setupWalletAndCreateReleaseTransactionBuilder() {
            wallet = BridgeUtils.getFederationSpendWallet(
                BTC_MAINNET_CONTEXT,
                federation,
                federationUTXOs,
                true,
                bridgeStorageProvider
            );
            return new ReleaseTransactionBuilder(
                BTC_MAINNET_PARAMS,
                wallet,
                federationFormatVersion,
                federationAddress,
                feePerKb,
                activations
            );
        }

        private enum PegoutChangeOutputExpectation {
            NON_DUST_CHANGE,
            DUST_CHANGE,
            ONLY_USER_OUTPUTS
        }

        /**
         * Shared assertions for successful {@link ReleaseTransactionBuilder#buildAmountTo} results using the
         * standard multisig federation wallet: BTC version, input count, input scripts, peg-out output economics,
         * and selected UTXOs.
         */
        private void assertSuccessfulStandardMultisigPegout(
            BuildResult buildResult,
            int expectedBtcTxVersion,
            int expectedNumberOfInputs,
            Coin requestedAmount,
            PegoutChangeOutputExpectation outputExpectation
        ) {
            assertSuccessBuildResult(buildResult);
            BtcTransaction pegoutTransaction = buildResult.btcTx();
            assertEquals(expectedBtcTxVersion, pegoutTransaction.getVersion());

            assertReleaseTxInputsStandardMultisig(
                pegoutTransaction,
                expectedNumberOfInputs,
                federationRedeemScript,
                federationUTXOs,
                buildResult.selectedUTXOs());
            switch (outputExpectation) {
                case NON_DUST_CHANGE ->
                    assertOutputsWithNonDustChange(pegoutTransaction, requestedAmount);
                case DUST_CHANGE ->
                    assertOutputsWithDustChange(pegoutTransaction, requestedAmount);
                case ONLY_USER_OUTPUTS ->
                    assertOutputsWithNoChange(pegoutTransaction, requestedAmount);
            }
        }

        private void assertPegoutTxOutputAndChangeOutputsNumbers(
            BtcTransaction pegoutTransaction,
            int expectedNumberOfUserOutputs,
            int expectedNumberOfChangeOutputs
        ) {
            List<TransactionOutput> userOutputs = getUserOutputs(pegoutTransaction);
            assertReleaseTxNumberOfOutputs(expectedNumberOfUserOutputs, userOutputs);

            List<TransactionOutput> pegoutTransactionChangeOutputs = getChangeOutputs(pegoutTransaction);
            assertReleaseTxNumberOfOutputs(expectedNumberOfChangeOutputs, pegoutTransactionChangeOutputs);

            int expectedNumberOfOutputs = expectedNumberOfUserOutputs + expectedNumberOfChangeOutputs;
            assertReleaseTxNumberOfOutputs(expectedNumberOfOutputs, pegoutTransaction.getOutputs());
        }

        private void assertOutputsWithNonDustChange(
            BtcTransaction pegoutTransaction,
            Coin requestedAmount
        ) {
            assertNumberOfOutputs(pegoutTransaction);
            List<TransactionOutput> changeOutputs = getChangeOutputs(pegoutTransaction);
            ReleaseTransactionAssertions.assertOutputsWithNonDustChange(
                pegoutTransaction,
                changeOutputs,
                requestedAmount
            );
        }

        private void assertOutputsWithDustChange(
            BtcTransaction pegoutTransaction,
            Coin requestedAmount
        ) {
            assertNumberOfOutputs(pegoutTransaction);
            List<TransactionOutput> changeOutputs = getChangeOutputs(pegoutTransaction);
            ReleaseTransactionAssertions.assertOutputsWithDustChange(
                pegoutTransaction,
                changeOutputs,
                requestedAmount
            );
        }

        private void assertNumberOfOutputs(BtcTransaction pegoutTransaction) {
            int expectedNumberOfUserOutputs = 1;
            assertPegoutTxOutputAndChangeOutputsNumbers(pegoutTransaction, expectedNumberOfUserOutputs, EXPECTED_NUMBER_OF_CHANGE_OUTPUTS);

            List<TransactionOutput> userOutputs = getUserOutputs(pegoutTransaction);
            assertDestinationAddress(userOutputs, RECIPIENT_ADDRESS, BTC_MAINNET_PARAMS);

            List<TransactionOutput> changeOutputs = getChangeOutputs(pegoutTransaction);
            assertDestinationAddress(changeOutputs, federationAddress, BTC_MAINNET_PARAMS);
        }

        private void assertOutputsWithNoChange(
            BtcTransaction pegoutTransaction,
            Coin requestedAmount
        ) {
            int expectedNumberOfUserOutputs = 1;
            int expectedNumberOfChangeOutputs = 0;
            assertPegoutTxOutputAndChangeOutputsNumbers(pegoutTransaction, expectedNumberOfUserOutputs, expectedNumberOfChangeOutputs);

            List<TransactionOutput> pegoutTransactionOutputs = pegoutTransaction.getOutputs();
            assertDestinationAddress(pegoutTransactionOutputs, RECIPIENT_ADDRESS, BTC_MAINNET_PARAMS);

            ReleaseTransactionAssertions.assertOutputsWithNoChange(pegoutTransaction, requestedAmount);
        }

        private List<TransactionOutput> getUserOutputs(BtcTransaction pegoutTransaction) {
            return pegoutTransaction.getOutputs().stream()
                .filter(output ->
                    output.getScriptPubKey().getToAddress(BTC_MAINNET_PARAMS).equals(RECIPIENT_ADDRESS))
                .toList();
        }

        private List<TransactionOutput> getChangeOutputs(BtcTransaction pegoutTransaction) {
            return pegoutTransaction.getOutputs().stream()
                .filter(this::isFederationOutput)
                .toList();
        }

        private boolean isFederationOutput(TransactionOutput output) {
            Address destination = output.getScriptPubKey().getToAddress(BTC_MAINNET_PARAMS);
            return destination.equals(federationAddress);
        }
    }

    @Nested
    class BuildEmptyWalletToTest {

        private static final int RECIPIENT_ADDRESS_KEY_OFFSET = 3100;
        private static final Address RECIPIENT_ADDRESS = ReleaseTransactionBuilderTest.recipientAddressFromPrivateKeyOffset(
            RECIPIENT_ADDRESS_KEY_OFFSET);
        /**
         * UTXO counts chosen so {@link ReleaseTransactionBuilder#buildEmptyWalletTo} exceeds max standard tx size
         * for each federation script layout (witness-heavy P2WSH needs far more inputs than bare P2SH).
         */
        private static final int P2SH_ERP_UTXO_COUNT_OVER_MAX_TX = 196;
        private static final int P2SH_P2WSH_ERP_UTXO_COUNT_OVER_MAX_TX = 2438;

        private Federation federation;
        private int federationFormatVersion;
        private Address federationAddress;
        private List<UTXO> federationUTXOs;
        private Script federationOutputScript;
        private Script federationRedeemScript;
        private Wallet wallet;
        private BridgeStorageProvider bridgeStorageProvider;

        @BeforeEach
        void setUp() {
            setUpActivations(ALL_ACTIVATIONS);
            setUpFeePerKb(BtcTransaction.DEFAULT_TX_FEE);
        }

        @Nested
        class StandardMultiSigFederationTest {

            @BeforeEach
            void setup() {
                federation = StandardMultiSigFederationBuilder.builder().build();
                federationFormatVersion = federation.getFormatVersion();
                federationAddress = federation.getAddress();
                federationOutputScript = federation.getP2SHScript();
                federationRedeemScript = federation.getRedeemScript();

                Repository repository = createRepository();
                bridgeStorageProvider = new BridgeStorageProvider(
                    repository,
                    BTC_MAINNET_PARAMS,
                    activations
                );

                int numberOfUtxos = 10;
                federationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                setUpWallet(federationUTXOs);
            }

            /**
             * This is an unrealistic scenario. The federation wallet is built from the peg-in UTXOs, so it cannot
             * be empty. If that were the case, the peg-in transaction would fail at the validation point
             * in {@link BridgeSupport#registerBtcTransaction(org.ethereum.core.Transaction, byte[], int, byte[])}.
             */
            @Test
            void buildEmptyWalletTo_whenFedHasNoUTXOs_shouldReturnCouldNotAdjustDownwards() {
                // Arrange
                federationUTXOs = List.of();
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

                // Act
                BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

                // Assert
                assertFailedBuildResult(COULD_NOT_ADJUST_DOWNWARDS, emptyWalletResult);
            }

            @Test
            void buildEmptyWalletTo_whenRSKIP201IsNotActive_shouldCreateRefundTxWithBtcVersion1() {
                // Arrange
                setUpActivations(PAPYRUS_ACTIVATIONS);
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

                // Act
                BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

                // Assert
                assertSuccessBuildResult(emptyWalletResult);
                BtcTransaction refundTransaction = emptyWalletResult.btcTx();
                assertBtcTxVersionIs1(refundTransaction);

                assertReleaseTxInputsStandardMultisig(
                    refundTransaction,
                    federationUTXOs.size(),
                    federationRedeemScript,
                    federationUTXOs,
                    emptyWalletResult.selectedUTXOs());
                assertRefundTxHasOnlyPegoutOutput(refundTransaction);
            }

            @Test
            void buildEmptyWalletTo_whenSingleUtxoInWallet_shouldCreateRefundTxSpendingSingleUtxo() {
                // Arrange
                federationUTXOs = List.of(
                    UTXOBuilder.builder()
                        .withScriptPubKey(federationOutputScript)
                        .withValue(MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS)
                        .build()
                );
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

                // Act
                BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

                // Assert
                assertSuccessfulEmptyWalletRefundWithBtcVersion2(
                    emptyWalletResult,
                    (tx, res) -> assertReleaseTxInputsStandardMultisig(
                        tx, federationUTXOs.size(), federationRedeemScript, federationUTXOs, res.selectedUTXOs())
                );
            }

            @Test
            void buildEmptyWalletTo_whenMultipleUtxosInWallet_shouldCreateRefundTxSpendingAllUtxos() {
                // Arrange
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

                // Act
                BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

                // Assert
                assertSuccessfulEmptyWalletRefundWithBtcVersion2(
                    emptyWalletResult,
                    (tx, res) -> assertReleaseTxInputsStandardMultisig(
                        tx, federationUTXOs.size(), federationRedeemScript, federationUTXOs, res.selectedUTXOs())
                );
            }

            @Test
            void buildEmptyWalletTo_whenMultipleFlyoverUtxosInWallet_shouldCreateRefundTxSpendingAllUtxos() {
                // Arrange
                Script flyoverRedeemScript = FlyoverRedeemScriptBuilderImpl.builder().of(
                    FLYOVER_DERIVATION_HASH,
                    federation.getRedeemScript()
                );
                Script flyoverOutputScript = PegUtils.getFlyoverFederationOutputScript(flyoverRedeemScript, federation.getFormatVersion());
                int numberOfUtxos = 2;
                List<UTXO> flyoverUtxos = UTXOBuilder.builder()
                    .withValue(Coin.COIN)
                    .withScriptPubKey(flyoverOutputScript)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));

                setUpFlyoverUtxosInStorage(flyoverUtxos, flyoverOutputScript, federation, bridgeStorageProvider);

                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(flyoverUtxos);

                // Act
                BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

                // Assert
                assertSuccessfulEmptyWalletRefundWithBtcVersion2(
                    emptyWalletResult,
                    (tx, res) -> assertReleaseTxInputsStandardMultisig(
                        tx, flyoverUtxos.size(), federationRedeemScript, flyoverUtxos, res.selectedUTXOs())
                );
            }

            @Test
            void buildEmptyWalletTo_whenTxExceedsMaxTxSize_shouldReturnExceedMaxTransactionSize() {
                // Arrange
                federationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(Coin.COIN)
                    .buildMany(STANDARD_MULTISIG_UTXO_COUNT_OVER_MAX_TX_SIZE, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

                // Act
                BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

                // Assert
                assertFailedBuildResult(EXCEED_MAX_TRANSACTION_SIZE, emptyWalletResult);
            }

            @Test
            void buildEmptyWalletTo_whenEstimatedFeeIsHighAndUtxosAreEnough_shouldCreateRefundTx() {
                // Arrange
                setUpFeePerKb(HIGH_FEE_PER_KB);
                int numberOfUtxos = 10;
                federationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(Coin.COIN)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

                // Act
                BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

                // Assert
                assertSuccessfulEmptyWalletRefundWithBtcVersion2(
                    emptyWalletResult,
                    (tx, res) -> assertReleaseTxInputsStandardMultisig(
                        tx, federationUTXOs.size(), federationRedeemScript, federationUTXOs, res.selectedUTXOs())
                );
            }

            @Test
            void buildEmptyWalletTo_whenEstimatedFeeIsHighAndUtxosAreNotEnough_shouldReturnCouldNotAdjustDownwards() {
                // Arrange
                setUpFeePerKb(HIGH_FEE_PER_KB);
                int numberOfUtxos = 3;
                federationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

                // Act
                BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

                // Assert
                assertFailedBuildResult(COULD_NOT_ADJUST_DOWNWARDS, emptyWalletResult);
            }
        }

        @Nested
        class P2shFederationTest {

            @BeforeEach
            void setup() {
                federation = P2shErpFederationBuilder.builder().build();
                federationFormatVersion = federation.getFormatVersion();
                federationAddress = federation.getAddress();
                federationOutputScript = federation.getP2SHScript();
                federationRedeemScript = federation.getRedeemScript();

                Repository repository = createRepository();
                bridgeStorageProvider = new BridgeStorageProvider(
                    repository,
                    BTC_MAINNET_PARAMS,
                    activations
                );

                int numberOfUtxos = 10;
                federationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                setUpWallet(federationUTXOs);
            }

            /**
             * This is an unrealistic scenario. The federation wallet is built from the peg-in UTXOs, so it cannot
             * be empty. If that were the case, the peg-in transaction would fail at the validation point
             * in {@link BridgeSupport#registerBtcTransaction(org.ethereum.core.Transaction, byte[], int, byte[])}.
             */
            @Test
            void buildEmptyWalletTo_whenFedHasNoUTXOs_shouldReturnCouldNotAdjustDownwards() {
                // Arrange
                federationUTXOs = List.of();
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

                // Act
                BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

                // Assert
                assertFailedBuildResult(COULD_NOT_ADJUST_DOWNWARDS, emptyWalletResult);
            }

            @Test
            void buildEmptyWalletTo_whenSingleUtxoInWallet_shouldCreateRefundTxSpendingSingleUtxo() {
                // Arrange
                federationUTXOs = List.of(
                    UTXOBuilder.builder()
                        .withScriptPubKey(federationOutputScript)
                        .withValue(MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS)
                        .build()
                );
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

                // Act
                BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

                // Assert
                assertSuccessfulEmptyWalletRefundWithBtcVersion2(
                    emptyWalletResult,
                    (tx, res) -> assertReleaseTxInputsP2shErp(
                        tx, federationUTXOs.size(), federationRedeemScript, federationUTXOs, res.selectedUTXOs())
                );
            }

            @Test
            void buildEmptyWalletTo_whenMultipleUtxosInWallet_shouldCreateRefundTxSpendingAllUtxos() {
                // Arrange
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

                // Act
                BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

                // Assert
                assertSuccessfulEmptyWalletRefundWithBtcVersion2(
                    emptyWalletResult,
                    (tx, res) -> assertReleaseTxInputsP2shErp(
                        tx, federationUTXOs.size(), federationRedeemScript, federationUTXOs, res.selectedUTXOs())
                );
            }

            @Test
            void buildEmptyWalletTo_whenMultipleFlyoverUtxosInWallet_shouldCreateRefundTxSpendingAllUtxos() {
                // Arrange
                Script flyoverRedeemScript = FlyoverRedeemScriptBuilderImpl.builder().of(
                    FLYOVER_DERIVATION_HASH,
                    federation.getRedeemScript()
                );
                Script flyoverOutputScript = PegUtils.getFlyoverFederationOutputScript(flyoverRedeemScript, federation.getFormatVersion());
                int numberOfUtxos = 2;
                List<UTXO> flyoverUtxos = UTXOBuilder.builder()
                    .withValue(Coin.COIN)
                    .withScriptPubKey(flyoverOutputScript)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                setUpFlyoverUtxosInStorage(flyoverUtxos, flyoverOutputScript, federation, bridgeStorageProvider);

                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(flyoverUtxos);

                // Act
                BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

                // Assert
                assertSuccessfulEmptyWalletRefundWithBtcVersion2(
                    emptyWalletResult,
                    (tx, res) -> assertReleaseTxInputsP2shErp(
                        tx, flyoverUtxos.size(), federationRedeemScript, flyoverUtxos, res.selectedUTXOs())
                );
            }

            @Test
            void buildEmptyWalletTo_whenTxExceedsMaxTxSize_shouldReturnExceedMaxTransactionSize() {
                // Arrange
                federationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(Coin.COIN)
                    .buildMany(P2SH_ERP_UTXO_COUNT_OVER_MAX_TX, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

                // Act
                BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

                // Assert
                assertFailedBuildResult(EXCEED_MAX_TRANSACTION_SIZE, emptyWalletResult);
            }

            @Test
            void buildEmptyWalletTo_whenEstimatedFeeIsHighAndUtxosAreEnough_shouldCreateRefundTx() {
                // Arrange
                setUpFeePerKb(HIGH_FEE_PER_KB);
                int numberOfUtxos = 10;
                federationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(Coin.COIN)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

                // Act
                BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

                // Assert
                assertSuccessfulEmptyWalletRefundWithBtcVersion2(
                    emptyWalletResult,
                    (tx, res) -> assertReleaseTxInputsP2shErp(
                        tx, federationUTXOs.size(), federationRedeemScript, federationUTXOs, res.selectedUTXOs())
                );
            }

            @Test
            void buildEmptyWalletTo_whenEstimatedFeeIsHighAndUtxosAreNotEnough_shouldReturnCouldNotAdjustDownwards() {
                // Arrange
                setUpFeePerKb(HIGH_FEE_PER_KB);
                int numberOfUtxos = 3;
                federationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

                // Act
                BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

                // Assert
                assertFailedBuildResult(COULD_NOT_ADJUST_DOWNWARDS, emptyWalletResult);
            }
        }

        @Nested
        class P2shP2wshFederationTest {

            @BeforeEach
            void setup() {
                federation = P2shP2wshErpFederationBuilder.builder().build();
                federationFormatVersion = federation.getFormatVersion();
                federationAddress = federation.getAddress();
                federationOutputScript = federation.getP2SHScript();
                federationRedeemScript = federation.getRedeemScript();

                Repository repository = createRepository();
                bridgeStorageProvider = new BridgeStorageProvider(
                    repository,
                    BTC_MAINNET_PARAMS,
                    activations
                );

                int numberOfUtxos = 10;
                federationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                setUpWallet(federationUTXOs);
            }

            /**
             * This is an unrealistic scenario. The federation wallet is built from the peg-in UTXOs, so it cannot
             * be empty. If that were the case, the peg-in transaction would fail at the validation point
             * in {@link BridgeSupport#registerBtcTransaction(org.ethereum.core.Transaction, byte[], int, byte[])}.
             */
            @Test
            void buildEmptyWalletTo_whenFedHasNoUTXOs_shouldReturnCouldNotAdjustDownwards() {
                // Arrange
                federationUTXOs = List.of();
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

                // Act
                BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

                // Assert
                assertFailedBuildResult(COULD_NOT_ADJUST_DOWNWARDS, emptyWalletResult);
            }

            @Test
            void buildEmptyWalletTo_whenSingleUtxoInWallet_shouldCreateRefundTxSpendingSingleUtxo() {
                // Arrange
                federationUTXOs = List.of(
                    UTXOBuilder.builder()
                        .withScriptPubKey(federationOutputScript)
                        .withValue(MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS)
                        .build()
                );
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

                // Act
                BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

                // Assert
                assertSuccessfulEmptyWalletRefundWithBtcVersion2(
                    emptyWalletResult,
                    (tx, res) -> assertReleaseTxInputsP2shP2wshErp(
                        tx, federationRedeemScript, federationUTXOs, res.selectedUTXOs(), federationUTXOs.size())
                );
            }

            @Test
            void buildEmptyWalletTo_whenMultipleUtxosInWallet_shouldCreateRefundTxSpendingAllUtxos() {
                // Arrange
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

                // Act
                BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

                // Assert
                assertSuccessfulEmptyWalletRefundWithBtcVersion2(
                    emptyWalletResult,
                    (tx, res) -> assertReleaseTxInputsP2shP2wshErp(
                        tx, federationRedeemScript, federationUTXOs, res.selectedUTXOs(), federationUTXOs.size())
                );
            }

            @Test
            void buildEmptyWalletTo_whenMultipleFlyoverUtxosInWallet_shouldCreateRefundTxSpendingAllUtxos() {
                // Arrange
                Script flyoverRedeemScript = FlyoverRedeemScriptBuilderImpl.builder().of(
                    FLYOVER_DERIVATION_HASH,
                    federation.getRedeemScript()
                );
                Script flyoverOutputScript = PegUtils.getFlyoverFederationOutputScript(flyoverRedeemScript, federation.getFormatVersion());
                int numberOfUtxos = 2;
                List<UTXO> flyoverUtxos = UTXOBuilder.builder()
                    .withValue(Coin.COIN)
                    .withScriptPubKey(flyoverOutputScript)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                setUpFlyoverUtxosInStorage(flyoverUtxos, flyoverOutputScript, federation, bridgeStorageProvider);

                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(flyoverUtxos);

                // Act
                BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

                // Assert
                assertSuccessfulEmptyWalletRefundWithBtcVersion2(
                    emptyWalletResult,
                    (tx, res) -> assertReleaseTxInputsP2shP2wshErp(
                        tx, federationRedeemScript, flyoverUtxos, res.selectedUTXOs(), flyoverUtxos.size())
                );
            }

            @Test
            void buildEmptyWalletTo_whenTxExceedsMaxTxSize_shouldReturnExceedMaxTransactionSize() {
                // Arrange
                federationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(Coin.COIN)
                    .buildMany(P2SH_P2WSH_ERP_UTXO_COUNT_OVER_MAX_TX, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

                // Act
                BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

                // Assert
                assertFailedBuildResult(EXCEED_MAX_TRANSACTION_SIZE, emptyWalletResult);
            }

            @Test
            void buildEmptyWalletTo_whenEstimatedFeeIsHighAndUtxosAreEnough_shouldCreateRefundTx() {
                // Arrange
                setUpFeePerKb(HIGH_FEE_PER_KB);
                int numberOfUtxos = 10;
                federationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(Coin.COIN)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

                // Act
                BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

                // Assert
                assertSuccessfulEmptyWalletRefundWithBtcVersion2(
                    emptyWalletResult,
                    (tx, res) -> assertReleaseTxInputsP2shP2wshErp(
                        tx, federationRedeemScript, federationUTXOs, res.selectedUTXOs(), federationUTXOs.size())
                );
            }

            @Test
            void buildEmptyWalletTo_whenEstimatedFeeIsHighAndUtxosAreNotEnough_shouldReturnCouldNotAdjustDownwards() {
                // Arrange
                setUpFeePerKb(HIGH_FEE_PER_KB);
                int numberOfUtxos = 3;
                federationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

                // Act
                BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

                // Assert
                assertFailedBuildResult(COULD_NOT_ADJUST_DOWNWARDS, emptyWalletResult);
            }
        }

        @FunctionalInterface
        private interface RefundTxInputsAssertion {
            void run(BtcTransaction refundTransaction, BuildResult emptyWalletResult);
        }

        private void assertSuccessfulEmptyWalletRefundWithBtcVersion2(
            BuildResult emptyWalletResult,
            RefundTxInputsAssertion assertRefundInputs
        ) {
            assertSuccessBuildResult(emptyWalletResult);
            BtcTransaction refundTransaction = emptyWalletResult.btcTx();
            assertBtcTxVersionIs2(refundTransaction);
            assertRefundInputs.run(refundTransaction, emptyWalletResult);
            assertRefundTxHasOnlyPegoutOutput(refundTransaction);
        }

        private void setUpWallet(List<UTXO> utxos) {
            wallet = BridgeUtils.getFederationSpendWallet(
                BTC_MAINNET_CONTEXT,
                federation,
                utxos,
                true,
                bridgeStorageProvider
            );
        }

        private ReleaseTransactionBuilder setupWalletAndCreateReleaseTransactionBuilder(List<UTXO> utxos) {
            setUpWallet(utxos);
            return new ReleaseTransactionBuilder(
                BTC_MAINNET_PARAMS,
                wallet,
                federationFormatVersion,
                federationAddress,
                feePerKb,
                activations
            );
        }

        private void assertRefundTxHasOnlyPegoutOutput(BtcTransaction refundTransaction) {
            List<TransactionOutput> outputs = refundTransaction.getOutputs();
            int expectedNumberOfOutputs = 1;
            assertEquals(expectedNumberOfOutputs, refundTransaction.getOutputs().size());

            TransactionOutput onlyOutput = outputs.get(0);
            assertDestinationAddress(outputs, RECIPIENT_ADDRESS, BTC_MAINNET_PARAMS);
            assertTrue(onlyOutput.getValue().isPositive());

            List<TransactionOutput> changeOutputs = outputs.stream()
                .filter(this::isFederationOutput)
                .toList();
            int expectedNumberOfChangeOutputs = 0;
            assertEquals(expectedNumberOfChangeOutputs, changeOutputs.size());

            assertOutputsWithNoChange(refundTransaction, refundTransaction.getInputSum());
        }

        private boolean isFederationOutput(TransactionOutput output) {
            Address destinationAddress = output.getScriptPubKey().getToAddress(BTC_MAINNET_PARAMS);
            return destinationAddress.equals(federationAddress);
        }
    }

    /**
     * Mainnet integration tests for {@link ReleaseTransactionBuilder#buildMigrationTransaction}.
     */
    @Nested
    class BuildMigrationTransactionTest {
        protected Federation retiringFederation;
        protected int retiringFederationFormatVersion;
        protected Address retiringFederationAddress;
        protected List<UTXO> retiringFederationUTXOs;
        protected Script retiringFederationOutputScript;
        private Script retiringFederationRedeemScript;
        protected Wallet wallet;

        private ActivationConfig.ForBlock activations;
        private Coin feePerKb;
        private Address newFederationAddress;
        private BridgeStorageProvider bridgeStorageProvider;
        private UTXO flyoverUtxo;

        @BeforeEach
        void setUp() {
            setUpActivationConfig(ALL_ACTIVATIONS);
            setUpFeePerKb(BtcTransaction.DEFAULT_TX_FEE);
        }

        @Nested
        class StandardMultiSigFederationTests {

            @BeforeEach
            void setUp() {
                retiringFederation = StandardMultiSigFederationBuilder.builder().build();
                retiringFederationFormatVersion = retiringFederation.getFormatVersion();
                retiringFederationAddress = retiringFederation.getAddress();
                retiringFederationOutputScript = retiringFederation.getP2SHScript();
                retiringFederationRedeemScript = retiringFederation.getRedeemScript();
                Federation newFederation = P2shErpFederationBuilder.builder().build();
                newFederationAddress = newFederation.getAddress();

                Repository repository = createRepository();
                bridgeStorageProvider = new BridgeStorageProvider(
                    repository,
                    BTC_MAINNET_PARAMS,
                    activations
                );

                Script flyoverRedeemScript = FlyoverRedeemScriptBuilderImpl.builder().of(
                    FLYOVER_DERIVATION_HASH,
                    retiringFederationRedeemScript
                );
                Script flyoverOutputScript = PegUtils.getFlyoverFederationOutputScript(flyoverRedeemScript, retiringFederationFormatVersion);
                flyoverUtxo = UTXOBuilder.builder()
                    .withValue(Coin.COIN)
                    .withScriptPubKey(flyoverOutputScript)
                    .withTransactionHash(BTC_TX_HASH_FLYOVER_UTXO)
                    .build();
                setUpFlyoverUtxoInStorage(flyoverUtxo, flyoverOutputScript, retiringFederation, bridgeStorageProvider, FLYOVER_DERIVATION_HASH);
            }

            @Test
            void buildMigrationTransaction_whenNoUTXOsToMigrate_shouldReturnDustySendRequested() {
                // Arrange
                retiringFederationUTXOs = Collections.emptyList();
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                    retiringFederationUTXOs);
                Coin migrationValue = wallet.getBalance();

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    List.of(migrationValue),
                    newFederationAddress
                );

                // Assert
                assertFailedBuildResult(DUSTY_SEND_REQUESTED, migrationTransactionResult);
            }

            @Test
            void buildMigrationTransaction_whenRSKIP376IsNotActive_shouldCreateMigrationTxWithBtcVersion1() {
                // Arrange
                setUpActivationConfig(FINGERROOT_ACTIVATIONS);
                int numberOfUtxos = 10;
                retiringFederationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(retiringFederationOutputScript)
                    .withValue(MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                    retiringFederationUTXOs);
                Coin migrationValue = wallet.getBalance();

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    List.of(migrationValue),
                    newFederationAddress
                );

                // Assert
                assertSuccessBuildResult(migrationTransactionResult);
                BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
                assertBtcTxVersionIs1(migrationTransaction);

                assertMigrationReleaseTxInputsStandardMultisig(
                    migrationTransaction,
                    retiringFederationRedeemScript,
                    retiringFederationUTXOs,
                    migrationTransactionResult.selectedUTXOs());
                assertOneMigrationTxOutput(
                    migrationTransaction,
                    migrationValue,
                    newFederationAddress,
                    BTC_MAINNET_PARAMS
                );
            }

            @Test
            void buildMigrationTransaction_whenSingleUTXOToMigrate_shouldCreateMigrationTx() {
                // Arrange
                retiringFederationUTXOs = List.of(
                    UTXOBuilder.builder()
                    .withScriptPubKey(retiringFederationOutputScript)
                    .withValue(MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS)
                    .build()
                );
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                    retiringFederationUTXOs);
                Coin migrationValue = wallet.getBalance();

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    List.of(migrationValue),
                    newFederationAddress
                );

                // Assert
                assertSuccessBuildResult(migrationTransactionResult);
                BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
                assertBtcTxVersionIs2(migrationTransaction);

                assertMigrationReleaseTxInputsStandardMultisig(
                    migrationTransaction,
                    retiringFederationRedeemScript,
                    retiringFederationUTXOs,
                    migrationTransactionResult.selectedUTXOs());
                assertOneMigrationTxOutput(
                    migrationTransaction,
                    migrationValue,
                    newFederationAddress,
                    BTC_MAINNET_PARAMS
                );
            }

            @Test
            void buildMigrationTransaction_whenMultipleUTXOsToMigrate_shouldCreateMigrationTx() {
                // Arrange
                int numberOfUtxos = 10;
                retiringFederationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(retiringFederationOutputScript)
                    .withValue(MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                retiringFederationUTXOs.add(flyoverUtxo);
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(retiringFederationUTXOs);
                Coin migrationValue = wallet.getBalance();

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    List.of(migrationValue),
                    newFederationAddress
                );

                // Assert
                assertSuccessBuildResult(migrationTransactionResult);
                BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
                assertBtcTxVersionIs2(migrationTransaction);

                assertMigrationReleaseTxInputsStandardMultisig(
                    migrationTransaction,
                    retiringFederationRedeemScript,
                    retiringFederationUTXOs,
                    migrationTransactionResult.selectedUTXOs());
                assertOneMigrationTxOutput(
                    migrationTransaction,
                    migrationValue,
                    newFederationAddress,
                    BTC_MAINNET_PARAMS
                );
            }

            /** DUSTY_AMOUNT_SEND_REQUESTED is unrealistic; the minimum UTXO the Federation
             * may hold is {@link co.rsk.peg.bitcoin.BitcoinTestUtils#MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT}
             * but we use it to exercise the DUSTY_SEND_REQUESTED path.
             */
            @Test
            void buildMigrationTransaction_whenMigrationValueIsTooSmall_shouldReturnDustySendRequested() {
                // Arrange
                retiringFederationUTXOs = List.of(
                    UTXOBuilder.builder()
                    .withScriptPubKey(retiringFederationOutputScript)
                    .withValue(DUST_VALUE)
                    .build()
                );
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                    retiringFederationUTXOs);
                Coin migrationValue = wallet.getBalance();

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    List.of(migrationValue),
                    newFederationAddress
                );

                // Assert
                assertFailedBuildResult(DUSTY_SEND_REQUESTED, migrationTransactionResult);
            }

            @Test
            void buildMigrationTransaction_whenEstimatedFeeIsTooHighAndThereAreEnoughUtxosToPay_shouldCreateMigrationTx() {
                // Arrange
                setUpFeePerKb(HIGH_FEE_PER_KB);
                int numberOfUtxos = 10;
                retiringFederationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(retiringFederationOutputScript)
                    .withValue(Coin.COIN)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                    retiringFederationUTXOs);
                Coin migrationValue = wallet.getBalance();

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    List.of(migrationValue),
                    newFederationAddress
                );

                // Assert
                assertSuccessBuildResult(migrationTransactionResult);
                BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
                assertBtcTxVersionIs2(migrationTransaction);

                assertMigrationReleaseTxInputsStandardMultisig(
                    migrationTransaction,
                    retiringFederationRedeemScript,
                    retiringFederationUTXOs,
                    migrationTransactionResult.selectedUTXOs());
                assertOneMigrationTxOutput(
                    migrationTransaction,
                    migrationValue,
                    newFederationAddress,
                    BTC_MAINNET_PARAMS
                );
            }

            @Test
            void buildMigrationTransaction_whenEstimatedFeeIsTooHighAndThereAreNotEnoughUtxosToPay_shouldReturnCouldNotAdjustDownwards() {
                // Arrange
                setUpFeePerKb(HIGH_FEE_PER_KB);
                retiringFederationUTXOs = List.of(
                    UTXOBuilder.builder()
                        .withScriptPubKey(retiringFederationOutputScript)
                        .withValue(MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS)
                        .build()
                );
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                    retiringFederationUTXOs);
                Coin migrationValue = wallet.getBalance();

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    List.of(migrationValue),
                    newFederationAddress
                );

                // Assert
                assertFailedBuildResult(COULD_NOT_ADJUST_DOWNWARDS, migrationTransactionResult);
            }

            /**
             * Tests an unrealistic scenario post RSKIP455 where the federation's balance differs from the value being migrated.
             * Although unrealistic post RSKIP455, the method {@link ReleaseTransactionBuilder#buildMigrationTransaction(List, Address)}
             * receives the migration outputs as a parameter and permits their total value to differ from the federation's balance.
             * Before RSKIP455, this was realistic — the method could receive a value to migrate different from the federation's total UTXO value.
             * After RSKIP455, the method always receives migration output values computed from the full balance available for migration.
             */
            @Test
            void buildMigrationTransaction_whenFederationBalanceDiffersWithValueMigrated_shouldCreateMigrationTxWithTwoOutputs() {
                // Arrange
                int numberOfUtxos = 10;
                retiringFederationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(retiringFederationOutputScript)
                    .withValue(MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                    retiringFederationUTXOs);
                Coin migrationValueRequested = MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS
                    .subtract(THOUSAND_SATOSHIS)
                    .multiply(numberOfUtxos);

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    List.of(migrationValueRequested),
                    newFederationAddress
                );

                // Assert
                assertSuccessBuildResult(migrationTransactionResult);
                BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
                assertBtcTxVersionIs2(migrationTransaction);

                assertMigrationReleaseTxInputsStandardMultisig(
                    migrationTransaction,
                    retiringFederationRedeemScript,
                    retiringFederationUTXOs,
                    migrationTransactionResult.selectedUTXOs()
                );
                assertMigrationTxWithTwoMigrationOutputs(migrationTransaction, migrationValueRequested);
            }

            @ParameterizedTest
            @ValueSource(ints = {1, 10})
            void buildMigrationTransaction_whenUtxosAreMinimumNonDustValue_shouldReturnCouldNotAdjustDownwards(int numberOfUtxos) {
                // Arrange
                retiringFederationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(retiringFederationOutputScript)
                    .withValue(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                    retiringFederationUTXOs);
                Coin migrationValue = wallet.getBalance();

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    List.of(migrationValue),
                    newFederationAddress
                );

                // Assert
                assertFailedBuildResult(COULD_NOT_ADJUST_DOWNWARDS, migrationTransactionResult);
            }

            @Test
            void buildMigrationTransaction_whenTxExceedMaxTxSize_shouldReturnExceedMaxTransactionSize() {
                // Arrange
                retiringFederationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(retiringFederationOutputScript)
                    .withValue(Coin.COIN)
                    .buildMany(STANDARD_MULTISIG_UTXO_COUNT_OVER_MAX_TX_SIZE, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(retiringFederationUTXOs);
                Coin migrationValue = wallet.getBalance();

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    List.of(migrationValue),
                    newFederationAddress
                );

                // Assert
                assertFailedBuildResult(EXCEED_MAX_TRANSACTION_SIZE, migrationTransactionResult);
            }

            @Test
            void buildMigrationTransaction_whenTxIsAlmostExceedingMaxTxSize_shouldCreateMigrationTx() {
                // Arrange
                int numberOfUtxos = 276;
                retiringFederationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(retiringFederationOutputScript)
                    .withValue(Coin.COIN)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(retiringFederationUTXOs);
                Coin migrationValue = wallet.getBalance();

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    List.of(migrationValue),
                    newFederationAddress
                );

                // Assert
                assertSuccessBuildResult(migrationTransactionResult);
                BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
                assertBtcTxVersionIs2(migrationTransaction);

                assertMigrationReleaseTxInputsStandardMultisig(
                    migrationTransaction,
                    retiringFederationRedeemScript,
                    retiringFederationUTXOs,
                    migrationTransactionResult.selectedUTXOs());
                assertOneMigrationTxOutput(
                    migrationTransaction,
                    migrationValue,
                    newFederationAddress,
                    BTC_MAINNET_PARAMS
                );
            }
        }

        @Nested
        class P2shErpFederationTests {

            @BeforeEach
            void setUp() {
                retiringFederation = P2shErpFederationBuilder.builder().build();
                retiringFederationFormatVersion = retiringFederation.getFormatVersion();
                retiringFederationAddress = retiringFederation.getAddress();
                retiringFederationOutputScript = retiringFederation.getP2SHScript();
                retiringFederationRedeemScript = retiringFederation.getRedeemScript();
                Federation newFederation = P2shP2wshErpFederationBuilder.builder().build();
                newFederationAddress = newFederation.getAddress();

                Repository repository = createRepository();
                bridgeStorageProvider = new BridgeStorageProvider(
                    repository,
                    BTC_MAINNET_PARAMS,
                    activations
                );

                Script flyoverRedeemScript = FlyoverRedeemScriptBuilderImpl.builder().of(
                    FLYOVER_DERIVATION_HASH,
                    retiringFederationRedeemScript
                );
                Script flyoverOutputScript = PegUtils.getFlyoverFederationOutputScript(flyoverRedeemScript, retiringFederationFormatVersion);
                flyoverUtxo = UTXOBuilder.builder()
                    .withValue(Coin.COIN)
                    .withScriptPubKey(flyoverOutputScript)
                    .withTransactionHash(BTC_TX_HASH_FLYOVER_UTXO)
                    .build();
                setUpFlyoverUtxoInStorage(flyoverUtxo, flyoverOutputScript, retiringFederation, bridgeStorageProvider, FLYOVER_DERIVATION_HASH);
            }

            @Test
            void buildMigrationTransaction_whenNoUTXOsToMigrate_shouldReturnDustySendRequested() {
                // Arrange
                retiringFederationUTXOs = Collections.emptyList();
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                    retiringFederationUTXOs);
                Coin migrationValue = wallet.getBalance();

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    List.of(migrationValue),
                    newFederationAddress
                );

                // Assert
                assertFailedBuildResult(DUSTY_SEND_REQUESTED, migrationTransactionResult);
            }

            @Test
            void buildMigrationTransaction_whenRSKIP376IsNotActive_shouldCreateMigrationTxWithBtcVersion1() {
                // Arrange
                setUpActivationConfig(FINGERROOT_ACTIVATIONS);
                int numberOfUtxos = 10;
                retiringFederationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(retiringFederationOutputScript)
                    .withValue(MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                    retiringFederationUTXOs);
                Coin migrationValue = wallet.getBalance();

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    List.of(migrationValue),
                    newFederationAddress
                );

                // Assert
                assertSuccessBuildResult(migrationTransactionResult);
                BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
                assertBtcTxVersionIs1(migrationTransaction);

                assertMigrationReleaseTxInputsP2shErp(
                    migrationTransaction,
                    retiringFederationRedeemScript,
                    retiringFederationUTXOs,
                    migrationTransactionResult.selectedUTXOs());
                assertOneMigrationTxOutput(
                    migrationTransaction,
                    migrationValue,
                    newFederationAddress,
                    BTC_MAINNET_PARAMS
                );
            }

            @Test
            void buildMigrationTransaction_whenSingleUTXOToMigrate_shouldCreateMigrationTx() {
                // Arrange
                retiringFederationUTXOs = List.of(
                    UTXOBuilder.builder()
                    .withScriptPubKey(retiringFederationOutputScript)
                    .withValue(MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS)
                    .build()
                );
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                    retiringFederationUTXOs);
                Coin migrationValue = wallet.getBalance();

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    List.of(migrationValue),
                    newFederationAddress
                );

                // Assert
                assertSuccessBuildResult(migrationTransactionResult);
                BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
                assertBtcTxVersionIs2(migrationTransaction);

                assertMigrationReleaseTxInputsP2shErp(
                    migrationTransaction,
                    retiringFederationRedeemScript,
                    retiringFederationUTXOs,
                    migrationTransactionResult.selectedUTXOs());
                assertOneMigrationTxOutput(
                    migrationTransaction,
                    migrationValue,
                    newFederationAddress,
                    BTC_MAINNET_PARAMS
                );
            }

            @Test
            void buildMigrationTransaction_whenMultipleUTXOsToMigrate_shouldCreateMigrationTx() {
                // Arrange
                int numberOfUtxos = 10;
                retiringFederationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(retiringFederationOutputScript)
                    .withValue(MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                retiringFederationUTXOs.add(flyoverUtxo);
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(retiringFederationUTXOs);
                Coin migrationValue = wallet.getBalance();

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    List.of(migrationValue),
                    newFederationAddress
                );

                // Assert
                assertSuccessBuildResult(migrationTransactionResult);
                BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
                assertBtcTxVersionIs2(migrationTransaction);

                assertMigrationReleaseTxInputsP2shErp(
                    migrationTransaction,
                    retiringFederationRedeemScript,
                    retiringFederationUTXOs,
                    migrationTransactionResult.selectedUTXOs());
                assertOneMigrationTxOutput(
                    migrationTransaction,
                    migrationValue,
                    newFederationAddress,
                    BTC_MAINNET_PARAMS
                );
            }

            /** DUSTY_AMOUNT_SEND_REQUESTED is unrealistic; the minimum UTXO the Federation
             * may hold is {@link co.rsk.peg.bitcoin.BitcoinTestUtils#MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT}
             * but we use it to exercise the DUSTY_SEND_REQUESTED path.
             */
            @Test
            void buildMigrationTransaction_whenMigrationValueIsTooSmall_shouldReturnDustySendRequested() {
                // Arrange
                retiringFederationUTXOs = List.of(
                    UTXOBuilder.builder()
                    .withScriptPubKey(retiringFederationOutputScript)
                    .withValue(DUST_VALUE)
                    .build()
                );
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                    retiringFederationUTXOs);
                Coin migrationValue = wallet.getBalance();

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    List.of(migrationValue),
                    newFederationAddress
                );

                // Assert
                assertFailedBuildResult(DUSTY_SEND_REQUESTED, migrationTransactionResult);
            }

            @Test
            void buildMigrationTransaction_whenEstimatedFeeIsTooHighAndThereAreEnoughUtxosToPay_shouldCreateMigrationTx() {
                // Arrange
                setUpFeePerKb(HIGH_FEE_PER_KB);
                int numberOfUtxos = 10;
                retiringFederationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(retiringFederationOutputScript)
                    .withValue(Coin.COIN)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                    retiringFederationUTXOs);
                Coin migrationValue = wallet.getBalance();

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    List.of(migrationValue),
                    newFederationAddress
                );

                // Assert
                assertSuccessBuildResult(migrationTransactionResult);
                BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
                assertBtcTxVersionIs2(migrationTransaction);

                assertMigrationReleaseTxInputsP2shErp(
                    migrationTransaction,
                    retiringFederationRedeemScript,
                    retiringFederationUTXOs,
                    migrationTransactionResult.selectedUTXOs());
                assertOneMigrationTxOutput(
                    migrationTransaction,
                    migrationValue,
                    newFederationAddress,
                    BTC_MAINNET_PARAMS
                );
            }

            @Test
            void buildMigrationTransaction_whenEstimatedFeeIsTooHighAndThereAreNotEnoughUtxosToPay_shouldReturnCouldNotAdjustDownwards() {
                // Arrange
                setUpFeePerKb(HIGH_FEE_PER_KB);
                retiringFederationUTXOs = List.of(
                    UTXOBuilder.builder()
                        .withScriptPubKey(retiringFederationOutputScript)
                        .withValue(MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS)
                        .build()
                );
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                    retiringFederationUTXOs);
                Coin migrationValue = wallet.getBalance();

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    List.of(migrationValue),
                    newFederationAddress
                );

                // Assert
                assertFailedBuildResult(COULD_NOT_ADJUST_DOWNWARDS, migrationTransactionResult);
            }

            /**
             * Tests an unrealistic scenario post RSKIP455 where the federation's balance differs from the value being migrated.
             * Although unrealistic post RSKIP455, the method {@link ReleaseTransactionBuilder#buildMigrationTransaction(List, Address)}
             * receives the migration outputs as a parameter and permits their total value to differ from the federation's balance.
             * Before RSKIP455, this was realistic — the method could receive a value to migrate different from the federation's total UTXO value.
             * After RSKIP455, the method always receives migration output values computed from the full balance available for migration.
             */
            @Test
            void buildMigrationTransaction_whenFederationBalanceDiffersWithValueMigrated_shouldCreateMigrationTxWithTwoOutputs() {
                // Arrange
                int numberOfUtxos = 10;
                retiringFederationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(retiringFederationOutputScript)
                    .withValue(MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                    retiringFederationUTXOs
                );
                Coin migrationValueRequested = MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS
                    .subtract(THOUSAND_SATOSHIS)
                    .multiply(numberOfUtxos);

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    List.of(migrationValueRequested),
                    newFederationAddress
                );

                // Assert
                assertSuccessBuildResult(migrationTransactionResult);
                BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
                assertBtcTxVersionIs2(migrationTransaction);

                assertMigrationReleaseTxInputsP2shErp(
                    migrationTransaction,
                    retiringFederationRedeemScript,
                    retiringFederationUTXOs,
                    migrationTransactionResult.selectedUTXOs()
                );
                assertMigrationTxWithTwoMigrationOutputs(migrationTransaction, migrationValueRequested);
            }

            @ParameterizedTest
            @ValueSource(ints = {1, 10})
            void buildMigrationTransaction_whenUtxosAreMinimumNonDustValue_shouldReturnCouldNotAdjustDownwards(int numberOfUtxos) {
                // Arrange
                retiringFederationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(retiringFederationOutputScript)
                    .withValue(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                    retiringFederationUTXOs);
                Coin migrationValue = wallet.getBalance();

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    List.of(migrationValue),
                    newFederationAddress
                );

                // Assert
                assertFailedBuildResult(COULD_NOT_ADJUST_DOWNWARDS, migrationTransactionResult);
            }

            @Test
            void buildMigrationTransaction_whenTxExceedMaxTxSize_shouldReturnExceedMaxTransactionSize() {
                // Arrange
                int numberOfUtxos = 196;
                retiringFederationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(retiringFederationOutputScript)
                    .withValue(Coin.COIN)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(retiringFederationUTXOs);
                Coin migrationValue = wallet.getBalance();

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    List.of(migrationValue),
                    newFederationAddress
                );

                // Assert
                assertFailedBuildResult(EXCEED_MAX_TRANSACTION_SIZE, migrationTransactionResult);
            }

            @Test
            void buildMigrationTransaction_whenTxIsAlmostExceedingMaxTxSize_shouldCreateMigrationTx() {
                // Arrange
                int numberOfUtxos = 195;
                retiringFederationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(retiringFederationOutputScript)
                    .withValue(Coin.COIN)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(retiringFederationUTXOs);
                Coin migrationValue = wallet.getBalance();

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    List.of(migrationValue),
                    newFederationAddress
                );

                // Assert
                assertSuccessBuildResult(migrationTransactionResult);
                BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
                assertBtcTxVersionIs2(migrationTransaction);

                assertMigrationReleaseTxInputsP2shErp(
                    migrationTransaction,
                    retiringFederationRedeemScript,
                    retiringFederationUTXOs,
                    migrationTransactionResult.selectedUTXOs());
                assertOneMigrationTxOutput(
                    migrationTransaction,
                    migrationValue,
                    newFederationAddress,
                    BTC_MAINNET_PARAMS
                );
            }
        }

        @Nested
        class P2shP2wshErpFederationTests {

            @BeforeEach
            void setUp() {
                retiringFederation = P2shP2wshErpFederationBuilder.builder().build();
                retiringFederationFormatVersion = retiringFederation.getFormatVersion();
                retiringFederationAddress = retiringFederation.getAddress();
                retiringFederationOutputScript = retiringFederation.getP2SHScript();
                retiringFederationRedeemScript = retiringFederation.getRedeemScript();
                List<BtcECKey> newFederationMembersKeys = BitcoinTestUtils.getBtcEcKeys(20);
                Federation newFederation = P2shP2wshErpFederationBuilder.builder().withMembersBtcPublicKeys(newFederationMembersKeys).build();
                newFederationAddress =  newFederation.getAddress();

                Repository repository = createRepository();
                bridgeStorageProvider = new BridgeStorageProvider(
                    repository,
                    BTC_MAINNET_PARAMS,
                    activations
                );

                Script flyoverRedeemScript = FlyoverRedeemScriptBuilderImpl.builder().of(
                    FLYOVER_DERIVATION_HASH,
                    retiringFederationRedeemScript
                );
                Script flyoverOutputScript = PegUtils.getFlyoverFederationOutputScript(flyoverRedeemScript, retiringFederationFormatVersion);
                flyoverUtxo = UTXOBuilder.builder()
                    .withValue(Coin.COIN)
                    .withScriptPubKey(flyoverOutputScript)
                    .withTransactionHash(BTC_TX_HASH_FLYOVER_UTXO)
                    .build();
                setUpFlyoverUtxoInStorage(flyoverUtxo, flyoverOutputScript, retiringFederation, bridgeStorageProvider, FLYOVER_DERIVATION_HASH);
            }

            @Test
            void buildMigrationTransaction_whenNoUTXOsToMigrate_shouldReturnDustySendRequested() {
                // Arrange
                retiringFederationUTXOs = Collections.emptyList();
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                    retiringFederationUTXOs);
                Coin migrationValue = wallet.getBalance();

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    List.of(migrationValue),
                    newFederationAddress
                );

                // Assert
                assertFailedBuildResult(DUSTY_SEND_REQUESTED, migrationTransactionResult);
            }

            @Test
            void buildMigrationTransaction_whenSingleUTXOToMigrate_shouldCreateMigrationTx() {
                // Arrange
                retiringFederationUTXOs = List.of(
                    UTXOBuilder.builder()
                    .withScriptPubKey(retiringFederationOutputScript)
                    .withValue(MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS)
                    .build()
                );
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                    retiringFederationUTXOs);
                Coin migrationValue = wallet.getBalance();

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    List.of(migrationValue),
                    newFederationAddress
                );

                // Assert
                assertSuccessBuildResult(migrationTransactionResult);
                BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
                assertBtcTxVersionIs2(migrationTransaction);

                assertMigrationReleaseTxInputsP2shP2wshErp(
                    migrationTransaction,
                    retiringFederationRedeemScript,
                    retiringFederationUTXOs,
                    migrationTransactionResult.selectedUTXOs());
                assertOneMigrationTxOutput(
                    migrationTransaction,
                    migrationValue,
                    newFederationAddress,
                    BTC_MAINNET_PARAMS
                );
            }

            @Test
            void buildMigrationTransaction_whenMultipleUTXOsToMigrate_shouldCreateMigrationTx() {
                // Arrange
                int numberOfUtxos = 10;
                retiringFederationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(retiringFederationOutputScript)
                    .withValue(MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                retiringFederationUTXOs.add(flyoverUtxo);
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(retiringFederationUTXOs);
                Coin migrationValue = wallet.getBalance();

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    List.of(migrationValue),
                    newFederationAddress
                );

                // Assert
                assertSuccessBuildResult(migrationTransactionResult);
                BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
                assertBtcTxVersionIs2(migrationTransaction);

                assertMigrationReleaseTxInputsP2shP2wshErp(
                    migrationTransaction,
                    retiringFederationRedeemScript,
                    retiringFederationUTXOs,
                    migrationTransactionResult.selectedUTXOs());
                assertOneMigrationTxOutput(
                    migrationTransaction,
                    migrationValue,
                    newFederationAddress,
                    BTC_MAINNET_PARAMS
                );
            }

            @Test
            void buildMigrationTransaction_whenUTXOsAboveMTMU_shouldCreateMigrationTxWithMultipleOutputs() {
                // Arrange
                Coin migrationOutputBtcValue = BRIDGE_MAINNET_CONSTANTS.getMigrationValueForMultipleOutputsInBtc();
                int numberOfUtxos = 3;
                retiringFederationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(retiringFederationOutputScript)
                    .withValue(migrationOutputBtcValue)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(retiringFederationUTXOs);
                List<Coin> migrationValues = List.of(migrationOutputBtcValue, migrationOutputBtcValue, migrationOutputBtcValue);

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    migrationValues,
                    newFederationAddress
                );

                // Assert
                assertSuccessBuildResult(migrationTransactionResult);
                BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
                assertBtcTxVersionIs2(migrationTransaction);

                assertMigrationReleaseTxInputsP2shP2wshErp(
                    migrationTransaction,
                    retiringFederationRedeemScript,
                    retiringFederationUTXOs,
                    migrationTransactionResult.selectedUTXOs());
                Coin migratedValue = migrationValues.stream().reduce(Coin.ZERO, Coin::add);
                assertFixedValueMigrationTxOutputs(
                    migrationTransaction,
                    migratedValue,
                    newFederationAddress,
                    BTC_MAINNET_PARAMS,
                    numberOfUtxos,
                    BRIDGE_MAINNET_CONSTANTS
                );
            }

            @Test
            void buildMigrationTransaction_withP2shP2wshErpFederation_with150UtxosAboveLargeMTMUThreshold_shouldBuildTxWith50Outputs() {
                // Arrange
                int numberOfUtxos = 150;
                Coin utxoValue = Coin.COIN.multiply(7);
                retiringFederationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(retiringFederationOutputScript)
                    .withValue(utxoValue)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));

                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(retiringFederationUTXOs);

                Coin totalValue = utxoValue.multiply(numberOfUtxos);
                Coin[] parts = totalValue.divideAndRemainder(MAX_OUTPUTS_NUMBER);
                List<Coin> migrationValues = new ArrayList<>(MAX_OUTPUTS_NUMBER);
                for (int i = 0; i < MAX_OUTPUTS_NUMBER - 1; i++) {
                    migrationValues.add(parts[0]);
                }
                Coin lastOutputValue = parts[0].add(parts[1]);
                migrationValues.add(lastOutputValue);

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    migrationValues,
                    newFederationAddress
                );

                // Assert
                assertSuccessBuildResult(migrationTransactionResult);
                BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
                assertBtcTxVersionIs2(migrationTransaction);

                assertMigrationReleaseTxInputsP2shP2wshErp(
                    migrationTransaction,
                    retiringFederationRedeemScript,
                    retiringFederationUTXOs,
                    migrationTransactionResult.selectedUTXOs()
                );
                assertEvenlyDistributedMigrationTxOutputs(
                    migrationTransaction,
                    totalValue,
                    newFederationAddress,
                    BTC_MAINNET_PARAMS
                );
                int migrationTransactionSize = BridgeUtils.calculatePegoutTxSize(
                    ALL_ACTIVATIONS,
                    retiringFederation,
                    migrationTransaction.getInputs().size(),
                    migrationTransaction.getOutputs().size()
                );
                assertTrue(migrationTransactionSize <= BtcTransaction.MAX_STANDARD_TX_SIZE);
            }

            /** DUSTY_AMOUNT_SEND_REQUESTED is unrealistic; the minimum UTXO the Federation
             * may hold is {@link co.rsk.peg.bitcoin.BitcoinTestUtils#MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT}
             * but we use it to exercise the DUSTY_SEND_REQUESTED path.
             */
            @Test
            void buildMigrationTransaction_whenMigrationValueIsTooSmall_shouldReturnDustySendRequested() {
                // Arrange
                retiringFederationUTXOs = List.of(
                    UTXOBuilder.builder()
                    .withScriptPubKey(retiringFederationOutputScript)
                    .withValue(DUST_VALUE)
                    .build()
                );
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                    retiringFederationUTXOs);
                Coin migrationValue = wallet.getBalance();

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    List.of(migrationValue),
                    newFederationAddress
                );

                // Assert
                assertFailedBuildResult(DUSTY_SEND_REQUESTED, migrationTransactionResult);
            }

            @Test
            void buildMigrationTransaction_whenEstimatedFeeIsTooHighAndThereAreEnoughUtxosToPay_shouldCreateMigrationTx() {
                // Arrange
                setUpFeePerKb(HIGH_FEE_PER_KB);
                int numberOfUtxos = 10;
                retiringFederationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(retiringFederationOutputScript)
                    .withValue(Coin.COIN)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                    retiringFederationUTXOs);
                Coin migrationValue = wallet.getBalance();

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    List.of(migrationValue),
                    newFederationAddress
                );

                // Assert
                assertSuccessBuildResult(migrationTransactionResult);
                BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
                assertBtcTxVersionIs2(migrationTransaction);

                assertMigrationReleaseTxInputsP2shP2wshErp(
                    migrationTransaction,
                    retiringFederationRedeemScript,
                    retiringFederationUTXOs,
                    migrationTransactionResult.selectedUTXOs());
                assertOneMigrationTxOutput(
                    migrationTransaction,
                    migrationValue,
                    newFederationAddress,
                    BTC_MAINNET_PARAMS
                );
            }

            @Test
            void buildMigrationTransaction_whenEstimatedFeeIsTooHighAndThereAreNotEnoughUtxosToPay_shouldReturnCouldNotAdjustDownwards() {
                // Arrange
                setUpFeePerKb(HIGH_FEE_PER_KB);
                retiringFederationUTXOs = List.of(
                    UTXOBuilder.builder()
                        .withScriptPubKey(retiringFederationOutputScript)
                        .withValue(MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS)
                        .build()
                );
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                    retiringFederationUTXOs);
                Coin migrationValue = wallet.getBalance();

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    List.of(migrationValue),
                    newFederationAddress
                );

                // Assert
                assertFailedBuildResult(COULD_NOT_ADJUST_DOWNWARDS, migrationTransactionResult);
            }

            /**
             * Tests an unrealistic scenario post RSKIP455 where the federation's balance differs from the value being migrated.
             * Although unrealistic post RSKIP455, the method {@link ReleaseTransactionBuilder#buildMigrationTransaction(List, Address)}
             * receives the migration outputs as a parameter and permits their total value to differ from the federation's balance.
             * Before RSKIP455, this was realistic — the method could receive a value to migrate different from the federation's total UTXO value.
             * After RSKIP455, the method always receives migration output values computed from the full balance available for migration.
             */
            @Test
            void buildMigrationTransaction_whenFederationBalanceDiffersWithValueMigrated_shouldCreateMigrationTxWithTwoOutputs() {
                // Arrange
                int numberOfUtxos = 10;
                retiringFederationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(retiringFederationOutputScript)
                    .withValue(MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                    retiringFederationUTXOs
                );
                Coin migrationValueRequested = MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS
                    .subtract(THOUSAND_SATOSHIS)
                    .multiply(numberOfUtxos);

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    List.of(migrationValueRequested),
                    newFederationAddress
                );

                // Assert
                assertSuccessBuildResult(migrationTransactionResult);
                BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
                assertBtcTxVersionIs2(migrationTransaction);

                assertMigrationReleaseTxInputsP2shP2wshErp(
                    migrationTransaction,
                    retiringFederationRedeemScript,
                    retiringFederationUTXOs,
                    migrationTransactionResult.selectedUTXOs()
                );
                assertMigrationTxWithTwoMigrationOutputs(migrationTransaction, migrationValueRequested);
            }

            @ParameterizedTest
            @ValueSource(ints = {1, 10})
            void buildMigrationTransaction_whenUtxosAreMinimumNonDustValue_shouldReturnCouldNotAdjustDownwards(int numberOfUtxos) {
                // Arrange
                retiringFederationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(retiringFederationOutputScript)
                    .withValue(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                    retiringFederationUTXOs);
                Coin migrationValue = wallet.getBalance();

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    List.of(migrationValue),
                    newFederationAddress
                );

                // Assert
                assertFailedBuildResult(COULD_NOT_ADJUST_DOWNWARDS, migrationTransactionResult);
            }

            @Test
            void buildMigrationTransaction_whenTxExceedMaxTxSize_shouldReturnExceedMaxTransactionSize() {
                // Arrange
                int numberOfUtxos = 2438;
                retiringFederationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(retiringFederationOutputScript)
                    .withValue(Coin.COIN)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(retiringFederationUTXOs);
                Coin migrationValue = wallet.getBalance();

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    List.of(migrationValue),
                    newFederationAddress
                );

                // Assert
                assertFailedBuildResult(EXCEED_MAX_TRANSACTION_SIZE, migrationTransactionResult);
            }

            @Test
            void buildMigrationTransaction_whenTxIsAlmostExceedingMaxTxSize_shouldCreateMigrationTx() {
                // Arrange
                int numberOfUtxos = 2437;
                retiringFederationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(retiringFederationOutputScript)
                    .withValue(Coin.COIN)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(retiringFederationUTXOs);
                Coin migrationValue = wallet.getBalance();

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    List.of(migrationValue),
                    newFederationAddress
                );

                // Assert
                assertSuccessBuildResult(migrationTransactionResult);
                BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
                assertBtcTxVersionIs2(migrationTransaction);

                assertMigrationReleaseTxInputsP2shP2wshErp(
                    migrationTransaction,
                    retiringFederationRedeemScript,
                    retiringFederationUTXOs,
                    migrationTransactionResult.selectedUTXOs());
                assertOneMigrationTxOutput(
                    migrationTransaction,
                    migrationValue,
                    newFederationAddress,
                    BTC_MAINNET_PARAMS
                );
            }
        }

        /**
         * Used only in unrealistic scenarios where the requested migration value differs from the total value
         * available in the retiring federation UTXOs. In that case, the change is also sent to the
         * migration destination address implying that the total value sent is greater than the requested migration value.
         */
        private void assertMigrationTxWithTwoMigrationOutputs(
            BtcTransaction migrationTransaction,
            Coin migrationValueRequested
        ) {
            int expectedNumberOfOutputs = 2;
            List<TransactionOutput> migrationTransactionOutputs = migrationTransaction.getOutputs();
            assertReleaseTxNumberOfOutputs(expectedNumberOfOutputs, migrationTransactionOutputs);
            assertDestinationAddress(migrationTransactionOutputs, newFederationAddress, BTC_MAINNET_PARAMS);
            assertMigrationTransactionIsMigratingMoreThanRequestedValue(migrationValueRequested, migrationTransaction);
        }

        private void setUpActivationConfig(ActivationConfig.ForBlock activationConfig) {
            this.activations = activationConfig;
        }

        private void setUpFeePerKb(Coin transactionFeePerKb) {
            this.feePerKb = transactionFeePerKb;
        }

        private static void assertMigrationTransactionIsMigratingMoreThanRequestedValue(Coin migrationValueRequested, BtcTransaction migrationTransaction) {
            Coin migratedValue = getMigrationTransactionValueSent(migrationTransaction);
            Coin fee = migrationTransaction.getFee();
            Coin totalValueSent = migratedValue.add(fee);
            assertTrue(totalValueSent.isGreaterThan(migrationValueRequested));
        }

        private static Coin getMigrationTransactionValueSent(BtcTransaction migrationTransaction) {
            return migrationTransaction.getOutputs().stream().map(TransactionOutput::getValue).reduce(Coin.ZERO, Coin::add);
        }

        private ReleaseTransactionBuilder setupWalletAndCreateReleaseTransactionBuilder(List<UTXO> utxos) {
            wallet = BridgeUtils.getFederationSpendWallet(
                BTC_MAINNET_CONTEXT,
                retiringFederation,
                utxos,
                true,
                bridgeStorageProvider
            );
            return new ReleaseTransactionBuilder(
                BTC_MAINNET_PARAMS,
                wallet,
                retiringFederationFormatVersion,
                retiringFederationAddress,
                feePerKb,
                activations
            );
        }
    }

    /**
     * Mainnet integration tests for {@link ReleaseTransactionBuilder#buildBatchedPegouts}.
     */
    @Nested
    class BuildBatchedPegoutsTest {

        private static final List<ReleaseRequestQueue.Entry> NO_PEGOUT_REQUESTS = Collections.emptyList();

        protected Federation federation;
        protected int federationFormatVersion;
        protected Address federationAddress;
        protected List<UTXO> federationUTXOs;
        protected Script federationOutputScript;
        protected Wallet wallet;

        private Script federationRedeemScript;
        private BridgeStorageProvider bridgeStorageProvider;

        @BeforeEach
        void setUp() {
            setUpActivations(ALL_ACTIVATIONS);
            setUpFeePerKb(BtcTransaction.DEFAULT_TX_FEE);
        }

        @Nested
        class P2shErpFederationTests {

            @BeforeEach
            void setup() {
                federation = P2shErpFederationBuilder.builder().build();
                federationFormatVersion = federation.getFormatVersion();
                federationAddress = federation.getAddress();
                federationOutputScript = federation.getP2SHScript();
                federationRedeemScript = federation.getRedeemScript();
                int numberOfUtxos = 10;
                federationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                Repository repository = createRepository();
                bridgeStorageProvider = new BridgeStorageProvider(
                    repository,
                    BTC_MAINNET_PARAMS,
                    activations
                );
                addFlyoverUtxoToFederationUtxos();
                setUpWallet(federationUTXOs);
            }

            @Test
            void buildBatchedPegouts_whenNoPegoutRequests_shouldThrowIllegalStateException() {
                // Arrange
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                    federationUTXOs);

                // Act & Assert
                assertThrows(IllegalStateException.class,
                    () -> releaseTransactionBuilder.buildBatchedPegouts(NO_PEGOUT_REQUESTS));
            }

            @Test
            void buildBatchedPegouts_whenSinglePegoutRequest_shouldCreateBatchedPegoutsTx() {
                // Arrange
                ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();
                List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(
                    1, MINIMUM_PEGOUT_TX_VALUE);

                // Act
                BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                    pegoutRequests);

                // Assert
                assertSuccessBuildResult(batchedPegoutsResult);

                BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
                assertBtcTxVersionIs2(batchedPegoutsTransaction);

                assertReleaseTxInputsP2shErp(
                    batchedPegoutsTransaction,
                    1,
                    federationRedeemScript,
                    federationUTXOs,
                    batchedPegoutsResult.selectedUTXOs());
                assertOutputsWithNonDustChange(batchedPegoutsTransaction, pegoutRequests);
            }

            @Test
            void buildBatchedPegouts_whenMultiplePegoutRequest_shouldCreateBatchedPegoutsTx() {
                // Arrange
                ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();
                List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(3,
                    MINIMUM_PEGOUT_TX_VALUE);

                // Act
                BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                    pegoutRequests);

                // Assert
                assertSuccessBuildResult(batchedPegoutsResult);

                BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
                assertBtcTxVersionIs2(batchedPegoutsTransaction);
                assertReleaseTxInputsP2shErp(
                    batchedPegoutsTransaction,
                    3,
                    federationRedeemScript,
                    federationUTXOs,
                    batchedPegoutsResult.selectedUTXOs());
                assertOutputsWithNonDustChange(batchedPegoutsTransaction, pegoutRequests);
            }

            @Test
            void buildBatchedPegouts_whenWalletHasExactFundsForPegoutRequests_shouldCreateBatchedPegoutsTxWithNoChangeOutput() {
                // Arrange
                federationUTXOs = List.of(
                    UTXOBuilder.builder()
                        .withScriptPubKey(federationOutputScript)
                        .withValue(MINIMUM_PEGOUT_TX_VALUE)
                        .build()
                );
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                    federationUTXOs);
                List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                    MINIMUM_PEGOUT_TX_VALUE);

                // Act
                BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                    pegoutRequests);

                // Assert
                assertSuccessBuildResult(batchedPegoutsResult);

                BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
                assertBtcTxVersionIs2(batchedPegoutsTransaction);

                assertReleaseTxInputsP2shErp(
                    batchedPegoutsTransaction,
                    1,
                    federationRedeemScript,
                    federationUTXOs,
                    batchedPegoutsResult.selectedUTXOs());
                assertOutputsWithNoChange(batchedPegoutsTransaction, pegoutRequests);
            }

            @Test
            void buildBatchedPegouts_whenInsufficientFundsForPegoutRequests_shouldReturnInsufficientMoney() {
                // Arrange
                federationUTXOs = List.of(
                    UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(MINIMUM_PEGOUT_TX_VALUE)
                    .build()
                );
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                    federationUTXOs);
                Coin pegoutRequestAmountExceedingFederationBalance = MINIMUM_PEGOUT_TX_VALUE.add(Coin.SATOSHI);
                List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1, pegoutRequestAmountExceedingFederationBalance);

                // Act
                BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                    pegoutRequests);

                // Assert
                assertFailedBuildResult(INSUFFICIENT_MONEY, batchedPegoutsResult);
            }

            @Test
            void buildBatchedPegouts_whenOriginalChangeIsMaxDustValue_shouldCreateBatchedPegoutsTxDecrementingFirstOutputAndSettingNonDustChange() {
                // Arrange
                Coin utxoAmount = MINIMUM_PEGOUT_TX_VALUE.add(DUST_VALUE);
                federationUTXOs = List.of(
                    UTXOBuilder.builder()
                        .withScriptPubKey(federationOutputScript)
                        .withValue(utxoAmount)
                        .build()
                );

                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                    federationUTXOs);
                List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                    MINIMUM_PEGOUT_TX_VALUE);

                // Act
                BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                    pegoutRequests);

                // Assert
                assertSuccessBuildResult(batchedPegoutsResult);

                BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
                assertBtcTxVersionIs2(batchedPegoutsTransaction);

                assertReleaseTxInputsP2shErp(
                    batchedPegoutsTransaction,
                    1,
                    federationRedeemScript,
                    federationUTXOs,
                    batchedPegoutsResult.selectedUTXOs());
                assertOutputsWithDustChange(batchedPegoutsTransaction, pegoutRequests);
            }

            @Test
            void buildBatchedPegouts_whenChangeIsMinNonDustValue_shouldCreateBatchedPegoutsTxWithNoModificationInTheValues() {
                // Arrange
                federationUTXOs = List.of(
                    UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(MINIMUM_PEGOUT_TX_VALUE.add(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT))
                    .build()
                );
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
                List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                    MINIMUM_PEGOUT_TX_VALUE);

                // Act
                BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                    pegoutRequests);

                // Assert
                assertSuccessBuildResult(batchedPegoutsResult);

                BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
                assertBtcTxVersionIs2(batchedPegoutsTransaction);

                assertReleaseTxInputsP2shErp(
                    batchedPegoutsTransaction,
                    1,
                    federationRedeemScript,
                    federationUTXOs,
                    batchedPegoutsResult.selectedUTXOs());
                assertOutputsWithNonDustChange(batchedPegoutsTransaction, pegoutRequests);
            }

            @Test
            void buildBatchedPegouts_whenOriginalChangeIsOneSatoshi_shouldCreateBatchedPegoutsTxDecrementingFirstOutputAndSettingNonDustChange() {
                // Arrange
                federationUTXOs = List.of(
                    UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(MINIMUM_PEGOUT_TX_VALUE.add(Coin.SATOSHI))
                    .build()
                );
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
                List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                    MINIMUM_PEGOUT_TX_VALUE);

                // Act
                BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                    pegoutRequests);

                // Assert
                assertSuccessBuildResult(batchedPegoutsResult);

                BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
                assertBtcTxVersionIs2(batchedPegoutsTransaction);

                assertReleaseTxInputsP2shErp(
                    batchedPegoutsTransaction,
                    1,
                    federationRedeemScript,
                    federationUTXOs,
                    batchedPegoutsResult.selectedUTXOs());
                assertOutputsWithDustChange(batchedPegoutsTransaction, pegoutRequests);
            }

            @Test
            void buildBatchedPegouts_whenFedHasOnlyMinimumNonDustUtxos_shouldReturnCouldNotAdjustDownwards() {
                // Spending an input with a p2sh script costs more than MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT.
                // Therefore, if the federation has only UTXOs with that minimum non-dust value,
                // it won't be possible to adjust downwards the pegout amount to avoid creating a dust output.
                // Arrange
                int numberOfUtxos = 10;
                federationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
                Coin valueRequested = MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT.multiply(numberOfUtxos);
                List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1, valueRequested);

                // Act
                BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                    pegoutRequests);

                // Assert
                assertFailedBuildResult(COULD_NOT_ADJUST_DOWNWARDS, batchedPegoutsResult);
            }

            /** DUSTY_AMOUNT_SEND_REQUESTED is unrealistic; real pegouts must be at least
             * {@link BridgeConstants#getMinimumPegoutTxValue()}, but we use it to exercise the
             * DUSTY_SEND_REQUESTED path.
             */
            @Test
            void buildBatchedPegouts_whenPegoutRequestAmountIsTooSmall_shouldReturnDustySendRequested() {
                // Arrange
                ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();
                List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                    DUST_VALUE);

                // Act
                BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                    pegoutRequests);

                // Assert
                assertFailedBuildResult(DUSTY_SEND_REQUESTED, batchedPegoutsResult);
            }

            @Test
            void buildBatchedPegouts_whenEstimatedFeeIsTooHighAndUtxosAreNotEnough_shouldReturnCouldNotAdjustDownwards() {
                // Arrange
                setUpFeePerKb(HIGH_FEE_PER_KB);
                int numberOfUtxos = 3;
                federationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                    federationUTXOs);
                List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(2,
                    MINIMUM_PEGOUT_TX_VALUE);

                // Act
                BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                    pegoutRequests);

                // Assert
                assertFailedBuildResult(COULD_NOT_ADJUST_DOWNWARDS, batchedPegoutsResult);
            }

            @ParameterizedTest
            @CsvSource({
                "196, 1",
                "195, 15",
            })
            void buildBatchedPegouts_whenTxExceedsMaxTxSize_shouldReturnExceedMaxTransactionSize(int numberOfUtxos, int numberOfPegoutRequests) {
                // Arrange
                federationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(Coin.COIN)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

                Coin utxosTotalAmount = Coin.COIN.multiply(numberOfUtxos);
                Coin pegoutRequestAmount = utxosTotalAmount.divide(numberOfPegoutRequests).subtract(THOUSAND_SATOSHIS);
                List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(numberOfPegoutRequests, pegoutRequestAmount);

                // Act
                BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                    pegoutRequests);

                // Assert
                assertFailedBuildResult(EXCEED_MAX_TRANSACTION_SIZE, batchedPegoutsResult);
            }

            @ParameterizedTest
            @CsvSource({
                "195, 1",
                "195, 14",
                "194, 15",
            })
            void buildBatchedPegouts_whenTxIsAlmostExceedingMaxTxSize_shouldCreateBatchedPegoutsTx(int expectedNumberOfUtxos, int numberOfPegoutRequests) {
                // Arrange
                federationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(Coin.COIN)
                    .buildMany(expectedNumberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

                Coin utxosTotalAmount = Coin.COIN.multiply(expectedNumberOfUtxos);
                Coin pegoutRequestAmount = utxosTotalAmount.divide(numberOfPegoutRequests).subtract(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT);

                List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(numberOfPegoutRequests, pegoutRequestAmount);

                // Act
                BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                    pegoutRequests);

                // Assert
                assertSuccessBuildResult(batchedPegoutsResult);
                BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();

                assertBtcTxVersionIs2(batchedPegoutsTransaction);
                assertReleaseTxInputsP2shErp(
                    batchedPegoutsTransaction,
                    expectedNumberOfUtxos,
                    federationRedeemScript,
                    federationUTXOs,
                    batchedPegoutsResult.selectedUTXOs());
                assertOutputsWithNonDustChange(batchedPegoutsTransaction, pegoutRequests);
            }
        }

        @Nested
        class P2shP2wshErpFederationTests {

            @BeforeEach
            void setup() {
                federation = P2shP2wshErpFederationBuilder.builder().build();
                federationFormatVersion = federation.getFormatVersion();
                federationAddress = federation.getAddress();
                federationOutputScript = federation.getP2SHScript();
                federationRedeemScript = federation.getRedeemScript();
                int numberOfUtxos = 10;
                federationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                Repository repository = createRepository();
                bridgeStorageProvider = new BridgeStorageProvider(
                    repository,
                    BTC_MAINNET_PARAMS,
                    activations
                );
                addFlyoverUtxoToFederationUtxos();
                setUpWallet(federationUTXOs);
            }

            @Test
            void buildBatchedPegouts_whenNoPegoutRequests_returnsAnEmptyTransaction() {
                // Arrange
                ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();

                // Act & Assert
                BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                    NO_PEGOUT_REQUESTS);
                assertSuccessBuildResult(batchedPegoutsResult);

                BtcTransaction batchedPegoutsTx = batchedPegoutsResult.btcTx();
                assertTrue(batchedPegoutsTx.getOutputs().isEmpty());
                assertTrue(batchedPegoutsTx.getInputs().isEmpty());
            }

            @Test
            void buildBatchedPegouts_whenSinglePegoutRequest_shouldCreateBatchedPegoutsTx() {
                // Arrange
                ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();
                List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                    MINIMUM_PEGOUT_TX_VALUE);

                // Act
                BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                    pegoutRequests);

                // Assert
                assertSuccessBuildResult(batchedPegoutsResult);

                BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
                assertBtcTxVersionIs2(batchedPegoutsTransaction);

                assertReleaseTxInputsP2shP2wshErp(
                    batchedPegoutsTransaction,
                    federationRedeemScript,
                    federationUTXOs,
                    batchedPegoutsResult.selectedUTXOs(),
                    1
                );
                assertOutputsWithNonDustChange(batchedPegoutsTransaction, pegoutRequests);
            }

            @Test
            void buildBatchedPegouts_whenMultiplePegoutRequest_shouldCreateBatchedPegoutsTx() {
                // Arrange
                ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();
                List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(3,
                    MINIMUM_PEGOUT_TX_VALUE);

                // Act
                BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                    pegoutRequests);

                // Assert
                assertSuccessBuildResult(batchedPegoutsResult);

                BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
                assertBtcTxVersionIs2(batchedPegoutsTransaction);

                assertReleaseTxInputsP2shP2wshErp(
                    batchedPegoutsTransaction,
                    federationRedeemScript,
                    federationUTXOs,
                    batchedPegoutsResult.selectedUTXOs(),
                    3
                );
                assertOutputsWithNonDustChange(batchedPegoutsTransaction, pegoutRequests);
            }

            @Test
            void buildBatchedPegouts_whenWalletHasExactFundsForPegoutRequests_shouldCreateBatchedPegoutsTxWithNoChangeOutput() {
                // Arrange
                federationUTXOs = List.of(
                    UTXOBuilder.builder()
                        .withScriptPubKey(federationOutputScript)
                        .withValue(MINIMUM_PEGOUT_TX_VALUE)
                        .build()
                );
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                    federationUTXOs);
                List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                    MINIMUM_PEGOUT_TX_VALUE);

                // Act
                BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                    pegoutRequests);

                // Assert
                assertSuccessBuildResult(batchedPegoutsResult);

                BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
                assertBtcTxVersionIs2(batchedPegoutsTransaction);
                assertReleaseTxInputsP2shP2wshErp(
                    batchedPegoutsTransaction,
                    federationRedeemScript,
                    federationUTXOs,
                    batchedPegoutsResult.selectedUTXOs(),
                    1
                );
                assertOutputsWithNoChange(batchedPegoutsTransaction, pegoutRequests);
            }

            @Test
            void buildBatchedPegouts_whenInsufficientFundsForPegoutRequests_shouldReturnInsufficientMoney() {
                // Arrange
                federationUTXOs = List.of(
                    UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(MINIMUM_PEGOUT_TX_VALUE)
                    .build()
                );
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                    federationUTXOs);

                Coin pegoutRequestAmountExceedingFederationBalance = MINIMUM_PEGOUT_TX_VALUE.add(Coin.SATOSHI);
                List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1, pegoutRequestAmountExceedingFederationBalance);

                // Act
                BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                    pegoutRequests);

                // Assert
                assertFailedBuildResult(INSUFFICIENT_MONEY, batchedPegoutsResult);
            }

            @Test
            void buildBatchedPegouts_whenOriginalChangeIsMaxDust_shouldCreateBatchedPegoutsTxDecrementingFirstOutputAndSettingNonDustChange() {
                // Arrange
                Coin utxoAmount = MINIMUM_PEGOUT_TX_VALUE.add(DUST_VALUE);
                federationUTXOs = List.of(
                    UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(utxoAmount)
                    .build()
                );
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                    federationUTXOs);
                List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                    MINIMUM_PEGOUT_TX_VALUE);

                // Act
                BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                    pegoutRequests);

                // Assert
                assertSuccessBuildResult(batchedPegoutsResult);

                BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
                assertBtcTxVersionIs2(batchedPegoutsTransaction);
                assertReleaseTxInputsP2shP2wshErp(
                    batchedPegoutsTransaction,
                    federationRedeemScript,
                    federationUTXOs,
                    batchedPegoutsResult.selectedUTXOs(),
                    1
                );
                assertOutputsWithDustChange(batchedPegoutsTransaction, pegoutRequests);
            }

            @Test
            void buildBatchedPegouts_whenChangeIsMinNonDustValue_shouldCreateBatchedPegoutsTxWithNoModificationInTheValues() {
                // Arrange
                federationUTXOs = List.of(
                    UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(MINIMUM_PEGOUT_TX_VALUE.add(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT))
                    .build()
                );
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
                List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                    MINIMUM_PEGOUT_TX_VALUE);

                // Act
                BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                    pegoutRequests);

                // Assert
                assertSuccessBuildResult(batchedPegoutsResult);

                BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
                assertBtcTxVersionIs2(batchedPegoutsTransaction);
                assertReleaseTxInputsP2shP2wshErp(
                    batchedPegoutsTransaction,
                    federationRedeemScript,
                    federationUTXOs,
                    batchedPegoutsResult.selectedUTXOs(),
                    1
                );
                assertOutputsWithNonDustChange(batchedPegoutsTransaction, pegoutRequests);
            }

            @Test
            void buildBatchedPegouts_whenChangeIsOneSatoshi_shouldCreateBatchedPegoutsTxDecrementingFirstOutputAndSettingNonDustChange() {
                // Arrange
                federationUTXOs = List.of(
                    UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(MINIMUM_PEGOUT_TX_VALUE.add(Coin.SATOSHI))
                    .build()
                );
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
                List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                    MINIMUM_PEGOUT_TX_VALUE);

                // Act
                BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                    pegoutRequests);

                // Assert
                assertSuccessBuildResult(batchedPegoutsResult);

                BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
                assertBtcTxVersionIs2(batchedPegoutsTransaction);
                assertReleaseTxInputsP2shP2wshErp(
                    batchedPegoutsTransaction,
                    federationRedeemScript,
                    federationUTXOs,
                    batchedPegoutsResult.selectedUTXOs(),
                    1
                );
                assertOutputsWithDustChange(batchedPegoutsTransaction, pegoutRequests);
            }

            @Test
            void buildBatchedPegouts_whenFedHasOnlyMinimumNonDustUtxos_shouldReturnCouldNotAdjustDownwards() {
                // Spending an input with a p2sh-p2wsh script costs more than MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT.
                // Therefore, if the federation has only UTXOs with that minimum non-dust value,
                // it won't be possible to adjust downwards the pegout amount to avoid creating a dust output.
                // Arrange
                int numberOfUtxos = 10;
                federationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
                Coin valueRequested = MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT.multiply(numberOfUtxos);
                List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1, valueRequested);

                // Act
                BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                    pegoutRequests);

                // Assert
                assertFailedBuildResult(COULD_NOT_ADJUST_DOWNWARDS, batchedPegoutsResult);
            }

            /** DUSTY_AMOUNT_SEND_REQUESTED is unrealistic; real pegouts must be at least
             * {@link BridgeConstants#getMinimumPegoutTxValue()}, but we use it to exercise the
             * DUSTY_SEND_REQUESTED path.
             */
            @Test
            void buildBatchedPegouts_whenPegoutRequestAmountIsTooSmall_shouldReturnDustySendRequested() {
                // Arrange
                ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();
                List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                    DUST_VALUE);

                // Act
                BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                    pegoutRequests);

                // Assert
                assertFailedBuildResult(DUSTY_SEND_REQUESTED, batchedPegoutsResult);
            }

            @Test
            void buildBatchedPegouts_whenEstimatedFeeIsTooHighAndUtxosAreNotEnough_shouldReturnCouldNotAdjustDownwards() {
                // Arrange
                setUpFeePerKb(HIGH_FEE_PER_KB);
                int numberOfUtxos = 3;
                federationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(
                    federationUTXOs);
                List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(2,
                    MINIMUM_PEGOUT_TX_VALUE);

                // Act
                BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                    pegoutRequests);

                // Assert
                assertFailedBuildResult(COULD_NOT_ADJUST_DOWNWARDS, batchedPegoutsResult);
            }

            @ParameterizedTest
            @CsvSource({
                "2438, 1",
                "2437, 2",
                "2436, 3"
            })
            void buildBatchedPegouts_whenTxExceedsMaxTxSize_shouldReturnExceedMaxTransactionSize(int numberOfUtxos, int numberOfPegoutRequests) {
                // Arrange
                federationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(Coin.COIN)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

                Coin utxosTotalAmount = Coin.COIN.multiply(numberOfUtxos);
                Coin pegoutRequestAmount = utxosTotalAmount.divide(numberOfPegoutRequests).subtract(THOUSAND_SATOSHIS);
                List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(numberOfPegoutRequests, pegoutRequestAmount);

                // Act
                BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                    pegoutRequests);

                // Assert
                assertFailedBuildResult(EXCEED_MAX_TRANSACTION_SIZE, batchedPegoutsResult);
            }

            @ParameterizedTest
            @CsvSource({
                "2437, 1",
                "2436, 2",
                "2435, 3",
            })
            void buildBatchedPegouts_whenTxIsAlmostExceedingMaxTxSize_shouldCreateBatchedPegoutsTx(
                int expectedNumberOfUtxos, int numberOfPegoutRequests) {
                // Arrange
                federationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(Coin.COIN)
                    .buildMany(expectedNumberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

                Coin utxosTotalAmount = Coin.COIN.multiply(expectedNumberOfUtxos);
                Coin pegoutRequestAmount = utxosTotalAmount.divide(numberOfPegoutRequests).subtract(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT);

                List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(numberOfPegoutRequests, pegoutRequestAmount);

                // Act
                BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                    pegoutRequests);

                // Assert
                assertSuccessBuildResult(batchedPegoutsResult);
                BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();

                assertBtcTxVersionIs2(batchedPegoutsTransaction);
                assertReleaseTxInputsP2shP2wshErp(
                    batchedPegoutsTransaction,
                    federationRedeemScript,
                    federationUTXOs,
                    batchedPegoutsResult.selectedUTXOs(),
                    expectedNumberOfUtxos
                );
                assertOutputsWithNonDustChange(batchedPegoutsTransaction, pegoutRequests);
            }
        }

        private void addFlyoverUtxoToFederationUtxos() {
            Script flyoverRedeemScript = FlyoverRedeemScriptBuilderImpl.builder().of(
                FLYOVER_DERIVATION_HASH,
                federation.getRedeemScript()
            );
            Script flyoverOutputScript = PegUtils.getFlyoverFederationOutputScript(flyoverRedeemScript, federation.getFormatVersion());
            UTXO flyoverUtxo = UTXOBuilder.builder()
                .withValue(Coin.COIN)
                .withScriptPubKey(flyoverOutputScript)
                .withTransactionHash(BTC_TX_HASH_FLYOVER_UTXO)
                .build();
            setUpFlyoverUtxoInStorage(flyoverUtxo, flyoverOutputScript, federation, bridgeStorageProvider, FLYOVER_DERIVATION_HASH);
            federationUTXOs.add(flyoverUtxo);
        }

        private void setUpWallet(List<UTXO> utxos) {
            wallet = BridgeUtils.getFederationSpendWallet(
                BTC_MAINNET_CONTEXT,
                federation,
                utxos,
                true,
                bridgeStorageProvider
            );
        }

        private ReleaseTransactionBuilder setupWalletAndCreateReleaseTransactionBuilder(
            List<UTXO> utxos) {
            setUpWallet(utxos);
            return createReleaseTransactionBuilder();
        }

        protected ReleaseTransactionBuilder createReleaseTransactionBuilder() {
            return new ReleaseTransactionBuilder(
                BTC_MAINNET_PARAMS,
                wallet,
                federationFormatVersion,
                federationAddress,
                feePerKb,
                activations
            );
        }

        private List<Entry> createPegoutRequests(int count, Coin amount) {
            List<ReleaseRequestQueue.Entry> pegoutRequests = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                BigInteger seed = BigInteger.valueOf(i + 1100);
                Address recipientAddress = BtcECKey.fromPrivate(seed).toAddress(BTC_MAINNET_PARAMS);
                Entry pegoutEntry = new Entry(
                    recipientAddress,
                    amount
                );
                pegoutRequests.add(pegoutEntry);
            }
            return pegoutRequests;
        }

        private List<TransactionOutput> getChangeOutputs(BtcTransaction batchedPegoutsTransaction) {
            return batchedPegoutsTransaction.getOutputs().stream()
                .filter(this::isFederationOutput)
                .toList();
        }

        private void assertPegoutRequestsAreIncludedInBatchedPegoutsTx(BtcTransaction batchedPegoutsTransaction,
                                                                       List<Entry> pegoutRequests) {
            List<TransactionOutput> userOutputs = getUserOutputs(batchedPegoutsTransaction);
            assertPegoutRequestsAreIncludedAsUserOutputs(pegoutRequests, userOutputs);
        }

        private List<TransactionOutput> getUserOutputs(BtcTransaction batchedPegoutsTransaction) {
            return batchedPegoutsTransaction.getOutputs().stream()
                .filter(this::isUserOutput)
                .toList();
        }

        private boolean isUserOutput(TransactionOutput output) {
            return !isFederationOutput(output);
        }

        private boolean isFederationOutput(TransactionOutput output) {
            Address recipientAddress = getDestinationAddress(output);
            return recipientAddress.equals(federationAddress);
        }

        private Address getDestinationAddress(TransactionOutput output) {
            return output.getScriptPubKey().getToAddress(BTC_MAINNET_PARAMS);
        }

        private void assertPegoutRequestsAreIncludedAsUserOutputs(List<Entry> pegoutRequests,
                                                                  List<TransactionOutput> outputs) {
            Map<Address, ArrayDeque<Entry>> byDestination = new HashMap<>();
            for (Entry request : pegoutRequests) {
                byDestination
                    .computeIfAbsent(request.getDestination(), k -> new ArrayDeque<>())
                    .addLast(request);
            }

            for (TransactionOutput output : outputs) {
                Address outputDestination = getDestinationAddress(output);
                Coin outputAmount = output.getValue();

                ArrayDeque<Entry> queue = byDestination.get(outputDestination);
                Entry pegoutRequest = queue.removeFirst();
                // ensure the transaction output does not exceed the original pegout request amount
                // (outputAmount <= pegoutRequestAmount). The output is below/equal because fees are
                // discounted from the pegout request amount when building the transaction.
                boolean outputIsBelowPegoutRequest = outputAmount.compareTo(pegoutRequest.getAmount()) <= 0;
                assertTrue(outputIsBelowPegoutRequest);
            }

            for (ArrayDeque<Entry> remaining : byDestination.values()) {
                assertTrue(remaining.isEmpty());
            }
        }

        private void assertBatchedPegoutsTxOutputAndChangeOutputsNumbers(BtcTransaction pegoutTransaction,
                                                                         int expectedNumberOfUserOutputs,
                                                                         int expectedNumberOfChangeOutputs) {
            List<TransactionOutput> userOutputs = getUserOutputs(pegoutTransaction);
            assertReleaseTxNumberOfOutputs(expectedNumberOfUserOutputs, userOutputs);

            List<TransactionOutput> pegoutTransactionChangeOutputs = getChangeOutputs(pegoutTransaction);
            assertReleaseTxNumberOfOutputs(expectedNumberOfChangeOutputs, pegoutTransactionChangeOutputs);

            int expectedNumberOfOutputs = expectedNumberOfUserOutputs + expectedNumberOfChangeOutputs;
            assertReleaseTxNumberOfOutputs(expectedNumberOfOutputs, pegoutTransaction.getOutputs());
        }

        private void assertOutputsWithNonDustChange(
            BtcTransaction batchedPegoutsTransaction,
            List<Entry> pegoutRequests
        ) {
            int pegoutRequestsNumber = pegoutRequests.size();
            assertBatchedPegoutsTxOutputAndChangeOutputsNumbers(
                batchedPegoutsTransaction,
                pegoutRequestsNumber,
                EXPECTED_NUMBER_OF_CHANGE_OUTPUTS
            );

            assertPegoutRequestsAreIncludedInBatchedPegoutsTx(batchedPegoutsTransaction, pegoutRequests);

            List<TransactionOutput> batchedPegoutsTransactionChangeOutputs = getChangeOutputs(batchedPegoutsTransaction);
            assertDestinationAddress(batchedPegoutsTransactionChangeOutputs, federationAddress, BTC_MAINNET_PARAMS);

            Coin totalPegoutRequestsAmount = getTotalPegoutRequestsAmount(pegoutRequests);
            ReleaseTransactionAssertions.assertOutputsWithNonDustChange(
                batchedPegoutsTransaction,
                batchedPegoutsTransactionChangeOutputs,
                totalPegoutRequestsAmount
            );
        }

        private void assertOutputsWithDustChange(
            BtcTransaction batchedPegoutsTransaction,
            List<Entry> pegoutRequests
        ) {
            int pegoutRequestsNumber = pegoutRequests.size();
            assertBatchedPegoutsTxOutputAndChangeOutputsNumbers(
                batchedPegoutsTransaction,
                pegoutRequestsNumber,
                EXPECTED_NUMBER_OF_CHANGE_OUTPUTS
            );

            assertPegoutRequestsAreIncludedInBatchedPegoutsTx(batchedPegoutsTransaction, pegoutRequests);

            List<TransactionOutput> batchedPegoutsTransactionChangeOutputs = getChangeOutputs(batchedPegoutsTransaction);
            assertDestinationAddress(batchedPegoutsTransactionChangeOutputs, federationAddress, BTC_MAINNET_PARAMS);

            Coin totalPegoutRequestsAmount = getTotalPegoutRequestsAmount(pegoutRequests);
            ReleaseTransactionAssertions.assertOutputsWithDustChange(
                batchedPegoutsTransaction,
                batchedPegoutsTransactionChangeOutputs,
                totalPegoutRequestsAmount
            );
        }

        private static Coin getTotalPegoutRequestsAmount(List<Entry> pegoutRequests) {
            return pegoutRequests.stream().map(Entry::getAmount)
                .reduce(Coin.ZERO, Coin::add);
        }

        public void assertOutputsWithNoChange(
            BtcTransaction batchedPegoutsTransaction,
            List<Entry> pegoutRequests
        ) {
            int expectedNumberOfChangeOutputs = 0;
            int expectedNumberOfOutputs = pegoutRequests.size();
            assertBatchedPegoutsTxOutputAndChangeOutputsNumbers(
                batchedPegoutsTransaction,
                expectedNumberOfOutputs,
                expectedNumberOfChangeOutputs
            );

            assertPegoutRequestsAreIncludedInBatchedPegoutsTx(batchedPegoutsTransaction, pegoutRequests);
            Coin totalPegoutRequestsAmount = getTotalPegoutRequestsAmount(pegoutRequests);
            ReleaseTransactionAssertions.assertOutputsWithNoChange(batchedPegoutsTransaction, totalPegoutRequestsAmount);
        }
    }

    private static void assertSuccessBuildResult(ReleaseTransactionBuilder.BuildResult buildResult) {
        ReleaseTransactionBuilder.Response actualResponseCode = buildResult.responseCode();
        assertEquals(SUCCESS, actualResponseCode);
    }

    private static void assertFailedBuildResult(
        ReleaseTransactionBuilder.Response expectedResponseCode,
        ReleaseTransactionBuilder.BuildResult buildResult
    ) {
        assertEquals(expectedResponseCode, buildResult.responseCode());
        assertNull(buildResult.btcTx());
        assertNull(buildResult.selectedUTXOs());
    }
}
