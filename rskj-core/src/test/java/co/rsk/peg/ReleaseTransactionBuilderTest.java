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
import static co.rsk.peg.ReleaseTransactionBuilder.BTC_TX_VERSION_1;
import static co.rsk.peg.ReleaseTransactionBuilder.BTC_TX_VERSION_2;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.COULD_NOT_ADJUST_DOWNWARDS;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.DUSTY_SEND_REQUESTED;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.EXCEED_MAX_TRANSACTION_SIZE;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.INSUFFICIENT_MONEY;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.SUCCESS;
import static co.rsk.peg.bitcoin.BitcoinTestAssertions.assertP2shP2wshWitnessWithoutSignaturesHasProperFormat;
import static co.rsk.peg.bitcoin.BitcoinTestAssertions.assertScriptSigFromP2shErpWithoutSignaturesHasProperFormat;
import static co.rsk.peg.bitcoin.BitcoinTestAssertions.assertScriptSigFromStandardMultisigWithoutSignaturesHasProperFormat;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.createHash;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.core.InsufficientMoneyException;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionInput;
import co.rsk.bitcoinj.core.TransactionOutput;
import co.rsk.bitcoinj.core.TransactionWitness;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.bitcoinj.core.UTXOProvider;
import co.rsk.bitcoinj.core.UTXOProviderException;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.wallet.SendRequest;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.ReleaseRequestQueue.Entry;
import co.rsk.peg.ReleaseTransactionBuilder.BuildResult;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.bitcoin.RedeemScriptCreationException;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.constants.BridgeRegTestConstants;
import co.rsk.peg.federation.ErpFederation;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationArgs;
import co.rsk.peg.federation.FederationFactory;
import co.rsk.peg.federation.FederationMember;
import co.rsk.peg.federation.FederationTestUtils;
import co.rsk.peg.federation.P2shErpFederationBuilder;
import co.rsk.peg.federation.P2shP2wshErpFederationBuilder;
import co.rsk.peg.federation.StandardMultiSigFederationBuilder;
import co.rsk.test.builders.UTXOBuilder;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

class ReleaseTransactionBuilderTest {
    private static final BridgeConstants BRIDGE_MAINNET_CONSTANTS = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters BTC_MAINNET_PARAMS = BRIDGE_MAINNET_CONSTANTS.getBtcParams();
    private static final NetworkParameters REGTEST_BTC_PARAMS = new BridgeRegTestConstants().getBtcParams();

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

    private ActivationConfig.ForBlock activations;
    private Coin feePerKb;

    private void setUpActivations(ActivationConfig.ForBlock activations) {
        this.activations = activations;
    }

    private void setUpFeePerKb(Coin feePerKb) {
        this.feePerKb = feePerKb;
    }

    private Address getRegtestAddressFromPrivateKey(int pk) {
        return BtcECKey.fromPrivate(BigInteger.valueOf(pk)).toAddress(REGTEST_BTC_PARAMS);
    }

    private Sha256Hash getSha256HashFromString(String generator) {
        return Sha256Hash.of(generator.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void releaseTransactionBuilder_shouldExposeWalletChangeAddressAndFeePerKbFromConstruction() {
        Address changeAddress = BitcoinTestUtils.createP2PKHAddress(REGTEST_BTC_PARAMS, "changeAddress");
        Wallet mockWallet = mock(Wallet.class);
        BridgeConstants bridgeRegTestConstants = new BridgeRegTestConstants();
        Federation genesisFederation = FederationTestUtils.getGenesisFederation(
            bridgeRegTestConstants.getFederationConstants());
        ReleaseTransactionBuilder releaseTransactionBuilder = new ReleaseTransactionBuilder(
            REGTEST_BTC_PARAMS,
            mockWallet,
            genesisFederation.getFormatVersion(),
            changeAddress,
            MOCK_FEE_PER_KB,
            ALL_ACTIVATIONS
        );
        new Context(REGTEST_BTC_PARAMS);

        assertSame(mockWallet, releaseTransactionBuilder.getWallet());
        assertSame(changeAddress, releaseTransactionBuilder.getChangeAddress());
        assertEquals(MOCK_FEE_PER_KB, releaseTransactionBuilder.getFeePerKb());
    }

    private static Address recipientAddressFromPrivateKeyOffset(int keyOffset) {
        BigInteger seed = BigInteger.valueOf(keyOffset);
        return BtcECKey.fromPrivate(seed).toAddress(BTC_MAINNET_PARAMS);
    }

    private static Wallet createMainnetFederationSpendWallet(
        Federation federation,
        List<UTXO> utxos,
        ActivationConfig.ForBlock activationsForBridgeStorage,
        Context btcContext
    ) {
        Repository repository = createRepository();
        BridgeStorageProvider bridgeStorageProvider = new BridgeStorageProvider(
            repository,
            BTC_MAINNET_PARAMS,
            activationsForBridgeStorage
        );
        return BridgeUtils.getFederationSpendWallet(
            btcContext,
            federation,
            utxos,
            true,
            bridgeStorageProvider
        );
    }

    /**
     * Mainnet {@link Wallet} + {@link ReleaseTransactionBuilder#buildSvpFundTransaction} success and
     * failure paths (UTXO selection, dust, invalid flyover prefix, null args).
     */
    @Nested
    class BuildSvpFundTransactionTest {
        private final Federation activeP2shErpFederation = P2shErpFederationBuilder.builder().build();
        private final Address activeP2shErpFederationAddress = activeP2shErpFederation.getAddress();
        private final Federation p2shP2wshErpProposedFederation = P2shP2wshErpFederationBuilder.builder().build();
        private BridgeStorageProvider bridgeStorageProviderMock;

        @BeforeEach
        void setup() {
            bridgeStorageProviderMock = mock(BridgeStorageProvider.class);
        }

        @Test
        void buildSvpFundTransaction_withAFederationWithEnoughUTXOsForTheSvpFundTransaction_shouldReturnACorrectSvpFundTx() {
            // Arrange
            int numberOfUtxos = 2;
            List<UTXO> utxos = UTXOBuilder.builder()
                .withScriptPubKey(activeP2shErpFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = getReleaseTransactionBuilderForMainnet(utxos);
            Keccak256 proposedFlyoverPrefix = BRIDGE_MAINNET_CONSTANTS.getProposedFederationFlyoverPrefix();

            // Act
            Coin svpFundTxOutputsValue = BRIDGE_MAINNET_CONSTANTS.getSvpFundTxOutputsValue();
            ReleaseTransactionBuilder.BuildResult svpFundTransactionUnsignedBuildResult = releaseTransactionBuilder.buildSvpFundTransaction(
                p2shP2wshErpProposedFederation,
                proposedFlyoverPrefix,
                svpFundTxOutputsValue
            );

            // Assert
            ReleaseTransactionBuilder.Response expectedResponseCode = ReleaseTransactionBuilder.Response.SUCCESS;
            ReleaseTransactionBuilder.Response actualResponseCode = svpFundTransactionUnsignedBuildResult.responseCode();
            assertEquals(expectedResponseCode, actualResponseCode);

            BtcTransaction svpFundTransactionUnsigned = svpFundTransactionUnsignedBuildResult.btcTx();
            int numberOfOutputs = 3; // 1 for the federation, 1 for the flyover federation, and 1 for the change
            assertEquals(numberOfOutputs, svpFundTransactionUnsigned.getOutputs().size());
            TransactionOutput actualFirstOutput = svpFundTransactionUnsigned.getOutput(0);
            assertEquals(svpFundTxOutputsValue, actualFirstOutput.getValue());
            Address proposedFederationAddress = p2shP2wshErpProposedFederation.getAddress();
            assertEquals(proposedFederationAddress, actualFirstOutput.getAddressFromP2SH(BTC_MAINNET_PARAMS));

            TransactionOutput actualSecondOutput = svpFundTransactionUnsigned.getOutput(1);
            assertEquals(svpFundTxOutputsValue, actualSecondOutput.getValue());

            Address flyoverFederationAddress = PegUtils.getFlyoverFederationAddress(BTC_MAINNET_PARAMS, proposedFlyoverPrefix, p2shP2wshErpProposedFederation);
            assertEquals(flyoverFederationAddress, actualSecondOutput.getAddressFromP2SH(BTC_MAINNET_PARAMS));

            List<UTXO> selectedUTXOs = svpFundTransactionUnsignedBuildResult.selectedUTXOs();
            List<UTXO> expectedSelectedUTXOs = List.of(utxos.get(0)); // First UTXO is enough to cover the svpFundTxOutputsValue
            assertEquals(expectedSelectedUTXOs, selectedUTXOs);
        }

        @Test
        void buildSvpFundTransaction_withAFederationWithoutUTXOs_shouldThrowInsufficientMoneyResponseCode() {
            // Arrange
            List<UTXO> emptyUtxos = Collections.emptyList();
            ReleaseTransactionBuilder releaseTransactionBuilder = getReleaseTransactionBuilderForMainnet(emptyUtxos);
            Coin svpFundTxOutputsValue = BRIDGE_MAINNET_CONSTANTS.getSvpFundTxOutputsValue();
            Keccak256 proposedFlyoverPrefix = BRIDGE_MAINNET_CONSTANTS.getProposedFederationFlyoverPrefix();

            // Act
            ReleaseTransactionBuilder.BuildResult svpFundTransactionUnsignedBuildResult = releaseTransactionBuilder.buildSvpFundTransaction(
                p2shP2wshErpProposedFederation,
                proposedFlyoverPrefix,
                svpFundTxOutputsValue
            );

            // Assert
            assertFailedBuildResult(INSUFFICIENT_MONEY, svpFundTransactionUnsignedBuildResult);
        }

        @Test
        void buildSvpFundTransaction_withDustValueAsSvpFundTxOutputsValue_shouldReturnDustySendRequestResponseCode() {
            // Arrange
            int numberOfUtxos = 2;
            List<UTXO> utxos = UTXOBuilder.builder()
                .withScriptPubKey(activeP2shErpFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));

            ReleaseTransactionBuilder releaseTransactionBuilder = getReleaseTransactionBuilderForMainnet(utxos);
            Keccak256 proposedFlyoverPrefix = BRIDGE_MAINNET_CONSTANTS.getProposedFederationFlyoverPrefix();
            Coin svpFundTxOutputsValue = Coin.SATOSHI;

            // Act
            ReleaseTransactionBuilder.BuildResult svpFundTransactionUnsignedBuildResult = releaseTransactionBuilder.buildSvpFundTransaction(
                p2shP2wshErpProposedFederation,
                proposedFlyoverPrefix,
                svpFundTxOutputsValue
            );

            // Assert
            assertFailedBuildResult(DUSTY_SEND_REQUESTED, svpFundTransactionUnsignedBuildResult);
        }

        @Test
        void buildSvpFundTransaction_withNullProposedFlyoverPrefix_shouldThrowRedeemScriptCreationException() {
            // Arrange
            int numberOfUtxos = 2;
            List<UTXO> utxos = UTXOBuilder.builder()
                .withScriptPubKey(activeP2shErpFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = getReleaseTransactionBuilderForMainnet(utxos);
            Coin svpFundTxOutputsValue = Coin.SATOSHI;

            // Act & Assert
            assertThrows(RedeemScriptCreationException.class, () -> releaseTransactionBuilder.buildSvpFundTransaction(
                p2shP2wshErpProposedFederation,
                null,
                svpFundTxOutputsValue
            ));
        }

        @Test
        void buildSvpFundTransaction_withInvalidProposedFlyoverPrefix_shouldThrowRedeemScriptCreationException() {
            // Arrange
            int numberOfUtxos = 2;
            List<UTXO> utxos = UTXOBuilder.builder()
                .withScriptPubKey(activeP2shErpFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = getReleaseTransactionBuilderForMainnet(utxos);
            Keccak256 proposedFlyoverPrefix = Keccak256.ZERO_HASH;
            Coin svpFundTxOutputsValue = Coin.SATOSHI;

            // Act & Assert
            assertThrows(RedeemScriptCreationException.class, () -> releaseTransactionBuilder.buildSvpFundTransaction(
                p2shP2wshErpProposedFederation,
                proposedFlyoverPrefix,
                svpFundTxOutputsValue
            ));
        }

        @Test
        void buildSvpFundTransaction_withNullProposedFederation_shouldThrowNullPointerException() {
            // Arrange
            int numberOfUtxos = 2;
            List<UTXO> utxos = UTXOBuilder.builder()
                .withScriptPubKey(activeP2shErpFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = getReleaseTransactionBuilderForMainnet(utxos);
            Keccak256 proposedFlyoverPrefix = BRIDGE_MAINNET_CONSTANTS.getProposedFederationFlyoverPrefix();
            Coin svpFundTxOutputsValue = Coin.SATOSHI;

            // Act & Assert
            assertThrows(NullPointerException.class, () -> releaseTransactionBuilder.buildSvpFundTransaction(
                null,
                proposedFlyoverPrefix,
                svpFundTxOutputsValue
            ));
        }

        @Test
        void buildSvpFundTransaction_withNullAsValueTransferred_shouldThrowNullPointerException() {
            // Arrange
            int numberOfUtxos = 2;
            List<UTXO> utxos = UTXOBuilder.builder()
                .withScriptPubKey(activeP2shErpFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = getReleaseTransactionBuilderForMainnet(utxos);
            Keccak256 proposedFlyoverPrefix = BRIDGE_MAINNET_CONSTANTS.getProposedFederationFlyoverPrefix();

            // Act & Assert
            assertThrows(NullPointerException.class, () -> releaseTransactionBuilder.buildSvpFundTransaction(
                p2shP2wshErpProposedFederation,
                proposedFlyoverPrefix,
                null
            ));
        }

        private ReleaseTransactionBuilder getReleaseTransactionBuilderForMainnet(List<UTXO> utxos) {
            Wallet thisWallet = BridgeUtils.getFederationSpendWallet(
                new Context(BTC_MAINNET_PARAMS),
                activeP2shErpFederation,
                utxos,
                false,
                bridgeStorageProviderMock
            );

            return new ReleaseTransactionBuilder(
                BTC_MAINNET_PARAMS,
                thisWallet,
                activeP2shErpFederation.getFormatVersion(),
                activeP2shErpFederationAddress,
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

        @BeforeEach
        void setup() {
            setUpActivations(IRIS_ACTIVATIONS);
            setUpFeePerKb(BtcTransaction.DEFAULT_TX_FEE);
            federation = StandardMultiSigFederationBuilder.builder().build();
            federationFormatVersion = federation.getFormatVersion();
            federationAddress = federation.getAddress();
            federationOutputScript = federation.getP2SHScript();
            federationRedeemScript = federation.getRedeemScript();
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
            int numberOfUtxos = STANDARD_MULTISIG_UTXO_COUNT_OVER_MAX_TX_SIZE;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(Coin.COIN)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
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
            int numberOfUtxos = UTXO_COUNT_JUST_UNDER_MAX_STANDARD_TX_SIZE;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(Coin.COIN)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
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

        @Test
        void buildAmountTo_whenRecipientsPayFeesWithP2shP2wshErp_shouldLeaveFederationChangeAsInputsMinusPegout() {
            BridgeConstants bridgeRegTestConstants = new BridgeRegTestConstants();
            Federation genesisFederation = FederationTestUtils.getGenesisFederation(
                bridgeRegTestConstants.getFederationConstants());
            BridgeStorageProvider bridgeStorageProviderMock = mock(BridgeStorageProvider.class);

            Federation activeFederation = P2shP2wshErpFederationBuilder.builder().build();
            int numberOfUtxos = 2;
            List<UTXO> utxos = UTXOBuilder.builder()
                .withScriptPubKey(activeFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));

            Wallet spendWallet = BridgeUtils.getFederationSpendWallet(
                new Context(BTC_MAINNET_PARAMS),
                activeFederation,
                utxos,
                false,
                bridgeStorageProviderMock
            );

            Address activeFederationAddress = activeFederation.getAddress();
            ReleaseTransactionBuilder releaseTransactionBuilder = new ReleaseTransactionBuilder(
                BTC_MAINNET_PARAMS,
                spendWallet,
                genesisFederation.getFormatVersion(),
                activeFederationAddress,
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

            Coin inputsValue = result.selectedUTXOs().stream().map(UTXO::getValue).reduce(Coin.ZERO, Coin::add);

            TransactionOutput changeOutput = result.btcTx().getOutput(1);

            assertEquals(activeFederationAddress, changeOutput.getAddressFromP2SH(BTC_MAINNET_PARAMS));
            assertEquals(inputsValue.minus(pegoutAmount), changeOutput.getValue());
        }

        @Test
        void buildAmountTo_whenNonStandardErpFederation_shouldSucceed() {
            List<FederationMember> members = FederationMember.getFederationMembersFromKeys(
                Arrays.asList(
                    new BtcECKey(),
                    new BtcECKey(),
                    new BtcECKey()
                )
            );
            FederationArgs federationArgs = new FederationArgs(members, Instant.now(), 0, BTC_MAINNET_PARAMS);

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
                new Context(BTC_MAINNET_PARAMS),
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

        @Test
        void buildAmountTo_whenUtxoProviderFails_shouldReturnUtxoProviderException()
            throws InsufficientMoneyException, UTXOProviderException {
            Address changeAddress = BitcoinTestUtils.createP2PKHAddress(REGTEST_BTC_PARAMS, "changeAddress");
            Wallet mockWallet = mock(Wallet.class);
            BridgeConstants bridgeRegTestConstants = new BridgeRegTestConstants();
            Federation genesisFederation = FederationTestUtils.getGenesisFederation(
                bridgeRegTestConstants.getFederationConstants());
            ReleaseTransactionBuilder releaseTransactionBuilder = new ReleaseTransactionBuilder(
                REGTEST_BTC_PARAMS,
                mockWallet,
                genesisFederation.getFormatVersion(),
                changeAddress,
                MOCK_FEE_PER_KB,
                ALL_ACTIVATIONS
            );
            new Context(REGTEST_BTC_PARAMS);

            Address to = getRegtestAddressFromPrivateKey(123);
            Coin amount = Coin.CENT.multiply(3);

            UTXOProvider utxoProvider = mock(UTXOProvider.class);
            when(mockWallet.getUTXOProvider()).thenReturn(utxoProvider);
            when(mockWallet.getWatchedAddresses()).thenReturn(Collections.singletonList(changeAddress));
            when(utxoProvider.getOpenTransactionOutputs(anyList())).then((InvocationOnMock m) -> {
                List<Address> addresses = m.getArgument(0);
                assertEquals(Collections.singletonList(changeAddress), addresses);
                throw new UTXOProviderException();
            });

            Mockito.doAnswer((InvocationOnMock m) -> {
                SendRequest sr = m.getArgument(0);

                assertEquals(MOCK_FEE_PER_KB, sr.feePerKb);
                assertEquals(Wallet.MissingSigsMode.USE_OP_ZERO, sr.missingSigsMode);
                assertEquals(changeAddress, sr.changeAddress);
                assertFalse(sr.shuffleOutputs);
                assertTrue(sr.recipientsPayFees);

                BtcTransaction tx = sr.tx;

                assertEquals(1, tx.getOutputs().size());
                assertEquals(amount, tx.getOutput(0).getValue());
                assertEquals(to, tx.getOutput(0).getAddressFromP2PKHScript(REGTEST_BTC_PARAMS));

                tx.addInput(getSha256HashFromString("two"), 2, mock(Script.class));
                tx.addInput(getSha256HashFromString("three"), 0, mock(Script.class));

                return null;
            }).when(mockWallet).completeTx(any(SendRequest.class));

            ReleaseTransactionBuilder.BuildResult result = releaseTransactionBuilder.buildAmountTo(to, amount);
            verify(mockWallet, times(1)).completeTx(any(SendRequest.class));

            assertEquals(ReleaseTransactionBuilder.Response.UTXO_PROVIDER_EXCEPTION, result.responseCode());
        }

        @Test
        void buildAmountTo_whenCompleteTxFailsWithIllegalState_shouldPropagate() throws InsufficientMoneyException {
            Address changeAddress = BitcoinTestUtils.createP2PKHAddress(REGTEST_BTC_PARAMS, "changeAddress");
            Wallet mockWallet = mock(Wallet.class);
            BridgeConstants bridgeRegTestConstants = new BridgeRegTestConstants();
            Federation genesisFederation = FederationTestUtils.getGenesisFederation(
                bridgeRegTestConstants.getFederationConstants());
            ReleaseTransactionBuilder releaseTransactionBuilder = new ReleaseTransactionBuilder(
                REGTEST_BTC_PARAMS,
                mockWallet,
                genesisFederation.getFormatVersion(),
                changeAddress,
                MOCK_FEE_PER_KB,
                ALL_ACTIVATIONS
            );
            new Context(REGTEST_BTC_PARAMS);

            Address to = getRegtestAddressFromPrivateKey(123);
            Coin amount = Coin.CENT.multiply(3);

            doThrow(new IllegalStateException()).when(mockWallet).completeTx(any(SendRequest.class));

            assertThrows(IllegalStateException.class, () -> releaseTransactionBuilder.buildAmountTo(to, amount));

            verify(mockWallet, times(1)).completeTx(any(SendRequest.class));
        }

        private void setUpWallet() {
            wallet = ReleaseTransactionBuilderTest.createMainnetFederationSpendWallet(
                federation,
                federationUTXOs,
                activations,
                new Context(BTC_MAINNET_PARAMS)
            );
        }

        private ReleaseTransactionBuilder setupWalletAndCreateReleaseTransactionBuilder() {
            setUpWallet();
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
            assertBuildResultResponseCode(SUCCESS, buildResult);
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
            ReleaseTransactionBuilderTest.assertOutputsWithNonDustChange(
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
            ReleaseTransactionBuilderTest.assertOutputsWithDustChange(
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

            ReleaseTransactionBuilderTest.assertOutputsWithNoChange(pegoutTransaction, requestedAmount);
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

        private static final Context BTC_CONTEXT = new Context(BTC_MAINNET_PARAMS);

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

        @BeforeEach
        void setUp() {
            setUpActivations(ALL_ACTIVATIONS);
            setUpFeePerKb(BtcTransaction.DEFAULT_TX_FEE);
        }

        @Test
        void buildEmptyWalletTo_whenUtxoProviderFails_shouldReturnUtxoProviderException()
            throws InsufficientMoneyException, UTXOProviderException {
            Address changeAddress = BitcoinTestUtils.createP2PKHAddress(REGTEST_BTC_PARAMS, "changeAddress");
            Wallet mockWallet = mock(Wallet.class);
            BridgeConstants bridgeRegTestConstants = new BridgeRegTestConstants();
            Federation genesisFederation = FederationTestUtils.getGenesisFederation(
                bridgeRegTestConstants.getFederationConstants());
            ReleaseTransactionBuilder releaseTransactionBuilder = new ReleaseTransactionBuilder(
                REGTEST_BTC_PARAMS,
                mockWallet,
                genesisFederation.getFormatVersion(),
                changeAddress,
                MOCK_FEE_PER_KB,
                ALL_ACTIVATIONS
            );
            new Context(REGTEST_BTC_PARAMS);

            Address to = getRegtestAddressFromPrivateKey(123);

            UTXOProvider utxoProvider = mock(UTXOProvider.class);
            when(mockWallet.getUTXOProvider()).thenReturn(utxoProvider);
            when(mockWallet.getWatchedAddresses()).thenReturn(Collections.singletonList(to));
            when(utxoProvider.getOpenTransactionOutputs(anyList())).then((InvocationOnMock m) -> {
                List<Address> addresses = m.getArgument(0);
                assertEquals(Collections.singletonList(to), addresses);
                throw new UTXOProviderException();
            });

            Mockito.doAnswer((InvocationOnMock m) -> {
                SendRequest sr = m.getArgument(0);

                assertEquals(MOCK_FEE_PER_KB, sr.feePerKb);
                assertEquals(Wallet.MissingSigsMode.USE_OP_ZERO, sr.missingSigsMode);
                assertEquals(to, sr.changeAddress);
                assertFalse(sr.shuffleOutputs);
                assertTrue(sr.recipientsPayFees);
                assertTrue(sr.emptyWallet);

                BtcTransaction tx = sr.tx;

                assertEquals(1, tx.getOutputs().size());
                assertEquals(Coin.ZERO, tx.getOutput(0).getValue());
                assertEquals(to, tx.getOutput(0).getAddressFromP2PKHScript(REGTEST_BTC_PARAMS));

                tx.addInput(getSha256HashFromString("two"), 2, mock(Script.class));
                tx.addInput(getSha256HashFromString("three"), 0, mock(Script.class));
                tx.getOutput(0).setValue(Coin.FIFTY_COINS);

                return null;
            }).when(mockWallet).completeTx(any(SendRequest.class));

            ReleaseTransactionBuilder.BuildResult result = releaseTransactionBuilder.buildEmptyWalletTo(to);
            verify(mockWallet, times(1)).completeTx(any(SendRequest.class));

            assertEquals(ReleaseTransactionBuilder.Response.UTXO_PROVIDER_EXCEPTION, result.responseCode());
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
                assertBuildResultResponseCode(SUCCESS, emptyWalletResult);
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
            void buildEmptyWalletTo_whenTxExceedsMaxTxSize_shouldReturnExceedMaxTransactionSize() {
                // Arrange
                int numberOfUtxos = STANDARD_MULTISIG_UTXO_COUNT_OVER_MAX_TX_SIZE;
                federationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(Coin.COIN)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
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
            void buildEmptyWalletTo_whenTxExceedsMaxTxSize_shouldReturnExceedMaxTransactionSize() {
                // Arrange
                int numberOfUtxos = P2SH_ERP_UTXO_COUNT_OVER_MAX_TX;
                federationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(Coin.COIN)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
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
                    (tx, res) -> assertReleaseTxInputsP2shP2wshErp(
                        tx, federationUTXOs.size(), federationRedeemScript, federationUTXOs, res.selectedUTXOs())
                );
            }

            @Test
            void buildEmptyWalletTo_whenTxExceedsMaxTxSize_shouldReturnExceedMaxTransactionSize() {
                // Arrange
                int numberOfUtxos = P2SH_P2WSH_ERP_UTXO_COUNT_OVER_MAX_TX;
                federationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(Coin.COIN)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
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

        @FunctionalInterface
        private interface RefundTxInputsAssertion {
            void run(BtcTransaction refundTransaction, BuildResult emptyWalletResult);
        }

        private void assertSuccessfulEmptyWalletRefundWithBtcVersion2(
            BuildResult emptyWalletResult,
            RefundTxInputsAssertion assertRefundInputs
        ) {
            assertBuildResultResponseCode(SUCCESS, emptyWalletResult);
            BtcTransaction refundTransaction = emptyWalletResult.btcTx();
            assertBtcTxVersionIs2(refundTransaction);
            assertRefundInputs.run(refundTransaction, emptyWalletResult);
            assertRefundTxHasOnlyPegoutOutput(refundTransaction);
        }

        private void setUpWallet(List<UTXO> utxos) {
            wallet = ReleaseTransactionBuilderTest.createMainnetFederationSpendWallet(
                federation,
                utxos,
                activations,
                BTC_CONTEXT
            );
        }

        private ReleaseTransactionBuilder setupWalletAndCreateReleaseTransactionBuilder(List<UTXO> utxos) {
            setUpWallet(utxos);
            return createReleaseTransactionBuilder();
        }

        private ReleaseTransactionBuilder createReleaseTransactionBuilder() {
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

        private ActivationConfig.ForBlock activationConfig;
        private Coin transactionFeePerKb;
        private Address newFederationAddress;

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
                    migrationValue, newFederationAddress);

                // Assert
                assertBuildResultResponseCode(DUSTY_SEND_REQUESTED, migrationTransactionResult);
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
                    migrationValue, newFederationAddress);

                // Assert
                assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
                BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
                assertBtcTxVersionIs1(migrationTransaction);

                assertMigrationReleaseTxInputsStandardMultisig(
                    migrationTransaction,
                    retiringFederationRedeemScript,
                    retiringFederationUTXOs,
                    migrationTransactionResult.selectedUTXOs());
                assertMigrationTxWithOnlyMigrationOutputs(migrationTransaction, migrationValue);
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
                    migrationValue, newFederationAddress);

                // Assert
                assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
                BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
                assertBtcTxVersionIs2(migrationTransaction);

                assertMigrationReleaseTxInputsStandardMultisig(
                    migrationTransaction,
                    retiringFederationRedeemScript,
                    retiringFederationUTXOs,
                    migrationTransactionResult.selectedUTXOs());
                assertMigrationTxWithOnlyMigrationOutputs(migrationTransaction, migrationValue);
            }

            @Test
            void buildMigrationTransaction_whenMultipleUTXOsToMigrate_shouldCreateMigrationTx() {
                // Arrange
                int numberOfUtxos = 10;
                retiringFederationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(retiringFederationOutputScript)
                    .withValue(MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(retiringFederationUTXOs);
                Coin migrationValue = wallet.getBalance();

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    migrationValue, newFederationAddress);

                // Assert
                assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
                BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
                assertBtcTxVersionIs2(migrationTransaction);

                assertMigrationReleaseTxInputsStandardMultisig(
                    migrationTransaction,
                    retiringFederationRedeemScript,
                    retiringFederationUTXOs,
                    migrationTransactionResult.selectedUTXOs());
                assertMigrationTxWithOnlyMigrationOutputs(migrationTransaction, migrationValue);
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
                    migrationValue, newFederationAddress);

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
                    migrationValue, newFederationAddress);

                // Assert
                assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
                BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
                assertBtcTxVersionIs2(migrationTransaction);

                assertMigrationReleaseTxInputsStandardMultisig(
                    migrationTransaction,
                    retiringFederationRedeemScript,
                    retiringFederationUTXOs,
                    migrationTransactionResult.selectedUTXOs());
                assertMigrationTxWithOnlyMigrationOutputs(migrationTransaction, migrationValue);
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
                    migrationValue, newFederationAddress);

                // Assert
                assertFailedBuildResult(COULD_NOT_ADJUST_DOWNWARDS, migrationTransactionResult);
            }

            /**
             * Tests an unrealistic scenario where the federation's balance differs from the value being migrated. Although
             * unreal, the method {@link ReleaseTransactionBuilder#buildMigrationTransaction(Coin, Address)} receives the
             * value to migrate as a parameter, and permits it to be less than the federation's balance. In reality, there's
             * no partial migration. Instead, all the UTXOs available for migration are migrated.
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
                Coin migrationValue = MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS.subtract(THOUSAND_SATOSHIS).multiply(numberOfUtxos);

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    migrationValue, newFederationAddress);

                // Assert
                assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
                BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
                assertBtcTxVersionIs2(migrationTransaction);

                assertMigrationReleaseTxInputsStandardMultisig(
                    migrationTransaction,
                    retiringFederationRedeemScript,
                    retiringFederationUTXOs,
                    migrationTransactionResult.selectedUTXOs());
                assertMigrationTxWithTwoMigrationOutputs(migrationTransaction, migrationValue);
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
                    migrationValue, newFederationAddress);

                // Assert
                assertFailedBuildResult(COULD_NOT_ADJUST_DOWNWARDS, migrationTransactionResult);
            }

            @Test
            void buildMigrationTransaction_whenTxExceedMaxTxSize_shouldReturnExceedMaxTransactionSize() {
                // Arrange
                int numberOfUtxos = STANDARD_MULTISIG_UTXO_COUNT_OVER_MAX_TX_SIZE;
                retiringFederationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(retiringFederationOutputScript)
                    .withValue(Coin.COIN)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(retiringFederationUTXOs);
                Coin migrationValue = wallet.getBalance();

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    migrationValue, newFederationAddress);

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
                    migrationValue, newFederationAddress);

                // Assert
                assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
                BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
                assertBtcTxVersionIs2(migrationTransaction);

                assertMigrationReleaseTxInputsStandardMultisig(
                    migrationTransaction,
                    retiringFederationRedeemScript,
                    retiringFederationUTXOs,
                    migrationTransactionResult.selectedUTXOs());
                assertMigrationTxWithOnlyMigrationOutputs(migrationTransaction, migrationValue);
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
                    migrationValue, newFederationAddress);

                // Assert
                assertBuildResultResponseCode(DUSTY_SEND_REQUESTED, migrationTransactionResult);
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
                    migrationValue, newFederationAddress);

                // Assert
                assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
                BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
                assertBtcTxVersionIs1(migrationTransaction);

                assertMigrationReleaseTxInputsP2shErp(
                    migrationTransaction,
                    retiringFederationRedeemScript,
                    retiringFederationUTXOs,
                    migrationTransactionResult.selectedUTXOs());
                assertMigrationTxWithOnlyMigrationOutputs(migrationTransaction, migrationValue);
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
                    migrationValue, newFederationAddress);

                // Assert
                assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
                BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
                assertBtcTxVersionIs2(migrationTransaction);

                assertMigrationReleaseTxInputsP2shErp(
                    migrationTransaction,
                    retiringFederationRedeemScript,
                    retiringFederationUTXOs,
                    migrationTransactionResult.selectedUTXOs());
                assertMigrationTxWithOnlyMigrationOutputs(migrationTransaction, migrationValue);
            }

            @Test
            void buildMigrationTransaction_whenMultipleUTXOsToMigrate_shouldCreateMigrationTx() {
                // Arrange
                int numberOfUtxos = 10;
                retiringFederationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(retiringFederationOutputScript)
                    .withValue(MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(retiringFederationUTXOs);
                Coin migrationValue = wallet.getBalance();

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    migrationValue, newFederationAddress);

                // Assert
                assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
                BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
                assertBtcTxVersionIs2(migrationTransaction);

                assertMigrationReleaseTxInputsP2shErp(
                    migrationTransaction,
                    retiringFederationRedeemScript,
                    retiringFederationUTXOs,
                    migrationTransactionResult.selectedUTXOs());
                assertMigrationTxWithOnlyMigrationOutputs(migrationTransaction, migrationValue);
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
                    migrationValue, newFederationAddress);

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
                    migrationValue, newFederationAddress);

                // Assert
                assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
                BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
                assertBtcTxVersionIs2(migrationTransaction);

                assertMigrationReleaseTxInputsP2shErp(
                    migrationTransaction,
                    retiringFederationRedeemScript,
                    retiringFederationUTXOs,
                    migrationTransactionResult.selectedUTXOs());
                assertMigrationTxWithOnlyMigrationOutputs(migrationTransaction, migrationValue);
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
                    migrationValue, newFederationAddress);

                // Assert
                assertFailedBuildResult(COULD_NOT_ADJUST_DOWNWARDS, migrationTransactionResult);
            }

            /**
             * Tests an unrealistic scenario where the federation's balance differs from the value being migrated. Although
             * unreal, the method {@link ReleaseTransactionBuilder#buildMigrationTransaction(Coin, Address)} receives the
             * value to migrate as a parameter, and permits it to be less than the federation's balance. In reality, there's
             * no partial migration. Instead, all the UTXOs available for migration are migrated.
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
                Coin migrationValue = MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS.subtract(THOUSAND_SATOSHIS).multiply(numberOfUtxos);

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    migrationValue, newFederationAddress);

                // Assert
                assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
                BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
                assertBtcTxVersionIs2(migrationTransaction);

                assertMigrationReleaseTxInputsP2shErp(
                    migrationTransaction,
                    retiringFederationRedeemScript,
                    retiringFederationUTXOs,
                    migrationTransactionResult.selectedUTXOs());
                assertMigrationTxWithTwoMigrationOutputs(migrationTransaction, migrationValue);
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
                    migrationValue, newFederationAddress);

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
                    migrationValue, newFederationAddress);

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
                    migrationValue, newFederationAddress);

                // Assert
                assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
                BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
                assertBtcTxVersionIs2(migrationTransaction);

                assertMigrationReleaseTxInputsP2shErp(
                    migrationTransaction,
                    retiringFederationRedeemScript,
                    retiringFederationUTXOs,
                    migrationTransactionResult.selectedUTXOs());
                assertMigrationTxWithOnlyMigrationOutputs(migrationTransaction, migrationValue);
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
                    migrationValue, newFederationAddress);

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
                    migrationValue, newFederationAddress);

                // Assert
                assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
                BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
                assertBtcTxVersionIs2(migrationTransaction);

                assertMigrationReleaseTxInputsP2shP2wshErp(
                    migrationTransaction,
                    retiringFederationRedeemScript,
                    retiringFederationUTXOs,
                    migrationTransactionResult.selectedUTXOs());
                assertMigrationTxWithOnlyMigrationOutputs(migrationTransaction, migrationValue);
            }

            @Test
            void buildMigrationTransaction_whenMultipleUTXOsToMigrate_shouldCreateMigrationTx() {
                // Arrange
                int numberOfUtxos = 10;
                retiringFederationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(retiringFederationOutputScript)
                    .withValue(MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(retiringFederationUTXOs);
                Coin migrationValue = wallet.getBalance();

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    migrationValue, newFederationAddress);

                // Assert
                assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
                BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
                assertBtcTxVersionIs2(migrationTransaction);

                assertMigrationReleaseTxInputsP2shP2wshErp(
                    migrationTransaction,
                    retiringFederationRedeemScript,
                    retiringFederationUTXOs,
                    migrationTransactionResult.selectedUTXOs());
                assertMigrationTxWithOnlyMigrationOutputs(migrationTransaction, migrationValue);
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
                    migrationValue, newFederationAddress);

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
                    migrationValue, newFederationAddress);

                // Assert
                assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
                BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
                assertBtcTxVersionIs2(migrationTransaction);

                assertMigrationReleaseTxInputsP2shP2wshErp(
                    migrationTransaction,
                    retiringFederationRedeemScript,
                    retiringFederationUTXOs,
                    migrationTransactionResult.selectedUTXOs());
                assertMigrationTxWithOnlyMigrationOutputs(migrationTransaction, migrationValue);
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
                    migrationValue, newFederationAddress);

                // Assert
                assertFailedBuildResult(COULD_NOT_ADJUST_DOWNWARDS, migrationTransactionResult);
            }

            /**
             * Tests an unrealistic scenario where the federation's balance differs from the value being migrated. Although
             * unreal, the method {@link ReleaseTransactionBuilder#buildMigrationTransaction(Coin, Address)} receives the
             * value to migrate as a parameter, and permits it to be less than the federation's balance. In reality, there's
             * no partial migration. Instead, all the UTXOs available for migration are migrated.
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
                Coin migrationValue = MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS.subtract(THOUSAND_SATOSHIS).multiply(numberOfUtxos);

                // Act
                BuildResult migrationTransactionResult = releaseTransactionBuilder.buildMigrationTransaction(
                    migrationValue, newFederationAddress);

                // Assert
                assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
                BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
                assertBtcTxVersionIs2(migrationTransaction);

                assertMigrationReleaseTxInputsP2shP2wshErp(
                    migrationTransaction,
                    retiringFederationRedeemScript,
                    retiringFederationUTXOs,
                    migrationTransactionResult.selectedUTXOs());
                assertMigrationTxWithTwoMigrationOutputs(migrationTransaction, migrationValue);
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
                    migrationValue, newFederationAddress);

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
                    migrationValue, newFederationAddress);

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
                    migrationValue, newFederationAddress);

                // Assert
                assertBuildResultResponseCode(SUCCESS, migrationTransactionResult);
                BtcTransaction migrationTransaction = migrationTransactionResult.btcTx();
                assertBtcTxVersionIs2(migrationTransaction);

                assertMigrationReleaseTxInputsP2shP2wshErp(
                    migrationTransaction,
                    retiringFederationRedeemScript,
                    retiringFederationUTXOs,
                    migrationTransactionResult.selectedUTXOs());

                assertMigrationTxWithOnlyMigrationOutputs(migrationTransaction, migrationValue);
            }
        }

        /**
         * Used only in unrealistic scenarios where the requested migration value differs from the total value
         * available in the retiring federation UTXOs. In that case, the change is also sent to the
         * migration destination address implying that the total value sent is greater than the requested migration value.
         */
        private void assertMigrationTxWithTwoMigrationOutputs(
            BtcTransaction migrationTransaction,
            Coin migratedValue) {
            int expectedNumberOfOutputs = 2;
            List<TransactionOutput> migrationTransactionOutputs = migrationTransaction.getOutputs();
            assertReleaseTxNumberOfOutputs(expectedNumberOfOutputs, migrationTransactionOutputs);
            assertDestinationAddress(migrationTransactionOutputs, newFederationAddress, BTC_MAINNET_PARAMS);
            assertMigrationTransactionIsMigratingMoreThanRequestedValue(migratedValue, migrationTransaction);
        }

        private void assertMigrationTxWithOnlyMigrationOutputs(
            BtcTransaction migrationTransaction,
            Coin migratedAmount
        ) {
            int expectedNumberOfChangeOutputs = 0;
            int expectedNumberOfMigrationOutputs = 1;
            int expectedNumberOfOutputs = expectedNumberOfMigrationOutputs + expectedNumberOfChangeOutputs;
            List<TransactionOutput> migrationTransactionOutputs = migrationTransaction.getOutputs();
            assertReleaseTxNumberOfOutputs(expectedNumberOfOutputs, migrationTransactionOutputs);
            assertDestinationAddress(migrationTransactionOutputs, newFederationAddress, BTC_MAINNET_PARAMS);

            List<TransactionOutput> migrationTransactionChangeOutputs = getChangeOutputs(migrationTransaction);
            assertEquals(expectedNumberOfChangeOutputs, migrationTransactionChangeOutputs.size());
            assertOutputsWithNoChange(migrationTransaction, migratedAmount);
        }

        private List<TransactionOutput> getChangeOutputs(BtcTransaction migrationTransaction) {
            return migrationTransaction.getOutputs().stream()
                .filter(this::isFederationOutput)
                .toList();
        }

        private boolean isFederationOutput(TransactionOutput output) {
            Address destination = output.getScriptPubKey().getToAddress(BTC_MAINNET_PARAMS);
            return destination.equals(retiringFederationAddress);
        }

        private void setUpActivationConfig(ActivationConfig.ForBlock activationConfig) {
            this.activationConfig = activationConfig;
        }

        private void setUpFeePerKb(Coin transactionFeePerKb) {
            this.transactionFeePerKb = transactionFeePerKb;
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

        private void setUpWallet(List<UTXO> utxos) {
            wallet = ReleaseTransactionBuilderTest.createMainnetFederationSpendWallet(
                retiringFederation,
                utxos,
                activationConfig,
                new Context(BTC_MAINNET_PARAMS)
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
                retiringFederationFormatVersion,
                retiringFederationAddress,
                transactionFeePerKb,
                activationConfig
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

        @BeforeEach
        void setUp() {
            setUpActivations(ALL_ACTIVATIONS);
            setUpFeePerKb(BtcTransaction.DEFAULT_TX_FEE);
        }

        @Nested
        class StandardMultiSigFederationTests {

            @BeforeEach
            void setUp() {
                federation = StandardMultiSigFederationBuilder.builder().build();
                federationFormatVersion = federation.getFormatVersion();
                federationAddress = federation.getAddress();
                federationOutputScript = federation.getP2SHScript();
                federationRedeemScript = federation.getRedeemScript();
                int numberOfUtxos = 10;
                federationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(MINIMUM_PEGIN_TX_VALUE_WITH_ALL_ACTIVATIONS)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                setUpWallet(federationUTXOs);
            }

            @Test
            void buildBatchedPegouts_whenNoPegoutRequests_shouldThrowIllegalStateException() {
                // Arrange
                ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();

                // Act & Assert
                assertThrows(IllegalStateException.class,
                    () -> releaseTransactionBuilder.buildBatchedPegouts(NO_PEGOUT_REQUESTS));
            }

            @Test
            void buildBatchedPegouts_whenRSKIP201IsNotActive_shouldCreateBatchedPegoutsTxWithBtcVersion1() {
                // Arrange
                setUpActivations(PAPYRUS_ACTIVATIONS);
                Coin minimumPeginTxValue = BRIDGE_MAINNET_CONSTANTS.getMinimumPeginTxValue(PAPYRUS_ACTIVATIONS);
                int numberOfUtxos = 10;
                federationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(minimumPeginTxValue)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);
                List<ReleaseRequestQueue.Entry> pegoutRequests = createPegoutRequests(1,
                    MINIMUM_PEGOUT_TX_VALUE);

                // Act
                BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                    pegoutRequests);

                // Assert
                assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

                BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
                assertBtcTxVersionIs1(batchedPegoutsTransaction);
                assertReleaseTxInputsStandardMultisig(
                    batchedPegoutsTransaction,
                    1,
                    federationRedeemScript,
                    federationUTXOs,
                    batchedPegoutsResult.selectedUTXOs());
                assertOutputsWithNonDustChange(batchedPegoutsTransaction, pegoutRequests);
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
                assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

                BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
                assertBtcTxVersionIs2(batchedPegoutsTransaction);
                assertReleaseTxInputsStandardMultisig(
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
                assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

                BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
                assertBtcTxVersionIs2(batchedPegoutsTransaction);

                assertReleaseTxInputsStandardMultisig(
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
                assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

                BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
                assertBtcTxVersionIs2(batchedPegoutsTransaction);

                assertReleaseTxInputsStandardMultisig(
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
                assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

                BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
                assertBtcTxVersionIs2(batchedPegoutsTransaction);

                assertReleaseTxInputsStandardMultisig(
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
                assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

                BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
                assertBtcTxVersionIs2(batchedPegoutsTransaction);

                assertReleaseTxInputsStandardMultisig(
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
                assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

                BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
                assertBtcTxVersionIs2(batchedPegoutsTransaction);

                assertReleaseTxInputsStandardMultisig(
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
                "277, 1",
                "276, 10",
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
                "276, 1",
                "276, 9",
                "275, 10",
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
                assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);
                BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();

                assertBtcTxVersionIs2(batchedPegoutsTransaction);
                assertReleaseTxInputsStandardMultisig(
                    batchedPegoutsTransaction,
                    expectedNumberOfUtxos,
                    federationRedeemScript,
                    federationUTXOs,
                    batchedPegoutsResult.selectedUTXOs());
                assertOutputsWithNonDustChange(batchedPegoutsTransaction, pegoutRequests);
            }
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
                assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

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
                assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

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
                assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

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
                assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

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
                assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

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
                assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

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
                assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);
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
                setUpWallet(federationUTXOs);
            }

            @Test
            void buildBatchedPegouts_whenNoPegoutRequests_returnsAnEmptyTransaction() {
                // Arrange
                ReleaseTransactionBuilder releaseTransactionBuilder = createReleaseTransactionBuilder();

                // Act & Assert
                BuildResult batchedPegoutsResult = releaseTransactionBuilder.buildBatchedPegouts(
                    NO_PEGOUT_REQUESTS);
                assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

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
                assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

                BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
                assertBtcTxVersionIs2(batchedPegoutsTransaction);

                assertReleaseTxInputsP2shP2wshErp(
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
                assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

                BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
                assertBtcTxVersionIs2(batchedPegoutsTransaction);

                assertReleaseTxInputsP2shP2wshErp(
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
                assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

                BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
                assertBtcTxVersionIs2(batchedPegoutsTransaction);
                assertReleaseTxInputsP2shP2wshErp(
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
                assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

                BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
                assertBtcTxVersionIs2(batchedPegoutsTransaction);
                assertReleaseTxInputsP2shP2wshErp(
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
                assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

                BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
                assertBtcTxVersionIs2(batchedPegoutsTransaction);
                assertReleaseTxInputsP2shP2wshErp(
                    batchedPegoutsTransaction,
                    1,
                    federationRedeemScript,
                    federationUTXOs,
                    batchedPegoutsResult.selectedUTXOs());
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
                assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);

                BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();
                assertBtcTxVersionIs2(batchedPegoutsTransaction);
                assertReleaseTxInputsP2shP2wshErp(
                    batchedPegoutsTransaction,
                    1,
                    federationRedeemScript,
                    federationUTXOs,
                    batchedPegoutsResult.selectedUTXOs());
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
                assertBuildResultResponseCode(SUCCESS, batchedPegoutsResult);
                BtcTransaction batchedPegoutsTransaction = batchedPegoutsResult.btcTx();

                assertBtcTxVersionIs2(batchedPegoutsTransaction);
                assertReleaseTxInputsP2shP2wshErp(
                    batchedPegoutsTransaction,
                    expectedNumberOfUtxos,
                    federationRedeemScript,
                    federationUTXOs,
                    batchedPegoutsResult.selectedUTXOs());
                assertOutputsWithNonDustChange(batchedPegoutsTransaction, pegoutRequests);
            }
        }

        private void setUpWallet(List<UTXO> utxos) {
            wallet = ReleaseTransactionBuilderTest.createMainnetFederationSpendWallet(
                federation,
                utxos,
                activations,
                new Context(BTC_MAINNET_PARAMS)
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

        private void assertOutputsWithNonDustChange(BtcTransaction batchedPegoutsTransaction,
                                                                                                List<Entry> pegoutRequests) {
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
            ReleaseTransactionBuilderTest.assertOutputsWithNonDustChange(
                batchedPegoutsTransaction,
                batchedPegoutsTransactionChangeOutputs,
                totalPegoutRequestsAmount
            );
        }

        private void assertOutputsWithDustChange(BtcTransaction batchedPegoutsTransaction,
                                                                                             List<Entry> pegoutRequests) {
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
            ReleaseTransactionBuilderTest.assertOutputsWithDustChange(
                batchedPegoutsTransaction,
                batchedPegoutsTransactionChangeOutputs,
                totalPegoutRequestsAmount
            );
        }

        private static Coin getTotalPegoutRequestsAmount(List<Entry> pegoutRequests) {
            return pegoutRequests.stream().map(Entry::getAmount)
                .reduce(Coin.ZERO, Coin::add);
        }

        private void assertOutputsWithNoChange(BtcTransaction batchedPegoutsTransaction,
                                                                List<Entry> pegoutRequests) {
            int expectedNumberOfChangeOutputs = 0;
            int expectedNumberOfOutputs = pegoutRequests.size();
            assertBatchedPegoutsTxOutputAndChangeOutputsNumbers(
                batchedPegoutsTransaction,
                expectedNumberOfOutputs,
                expectedNumberOfChangeOutputs
            );

            assertPegoutRequestsAreIncludedInBatchedPegoutsTx(batchedPegoutsTransaction, pegoutRequests);
            Coin totalPegoutRequestsAmount = getTotalPegoutRequestsAmount(pegoutRequests);
            ReleaseTransactionBuilderTest.assertOutputsWithNoChange(batchedPegoutsTransaction, totalPegoutRequestsAmount);
        }
    }

    private static void assertReleaseTxInputsHasProperFormatAndBelongsToStandardMultisigFederation(
        BtcTransaction releaseTransaction,
        Script federationRedeemScript,
        List<UTXO> federationUTXOs) {
        for (TransactionInput input : releaseTransaction.getInputs()) {
            Script scriptSig = input.getScriptSig();
            assertScriptSigFromStandardMultisigWithoutSignaturesHasProperFormat(scriptSig, federationRedeemScript);
            assertInputIsFromFederationUTXOsWallet(input, federationUTXOs);
        }
    }

    private static void assertReleaseTxInputsHasProperFormatAndBelongsToP2shErpFederation(
        BtcTransaction releaseTransaction,
        Script federationRedeemScript,
        List<UTXO> federationUTXOs) {
        for (TransactionInput input : releaseTransaction.getInputs()) {
            Script scriptSig = input.getScriptSig();
            assertScriptSigFromP2shErpWithoutSignaturesHasProperFormat(scriptSig, federationRedeemScript);
            assertInputIsFromFederationUTXOsWallet(input, federationUTXOs);
        }
    }

    private static void assertReleaseTxInputsHasProperFormatAndBelongsToP2shP2wshErpFederation(
        BtcTransaction releaseTransaction,
        Script federationRedeemScript,
        List<UTXO> federationUTXOs) {
        List<TransactionInput> releaseTransactionInputs = releaseTransaction.getInputs();
        for (int inputIndex = 0; inputIndex < releaseTransactionInputs.size(); inputIndex++) {
            TransactionWitness witness = releaseTransaction.getWitness(inputIndex);
            assertP2shP2wshWitnessWithoutSignaturesHasProperFormat(witness, federationRedeemScript);
            TransactionInput input = releaseTransactionInputs.get(inputIndex);
            assertInputIsFromFederationUTXOsWallet(input, federationUTXOs);
        }
    }

    private static void assertInputIsFromFederationUTXOsWallet(TransactionInput input, List<UTXO> federationUtxos) {
        Predicate<UTXO> isUTXOAndReleaseInputFromTheSameOutpoint = utxo ->
            utxo.getHash().equals(input.getOutpoint().getHash())
                && utxo.getIndex() == input.getOutpoint().getIndex();
        List<UTXO> matchingUtxos = federationUtxos.stream()
            .filter(isUTXOAndReleaseInputFromTheSameOutpoint).toList();
        int expectedNumberOfUtxos = 1;
        assertEquals(expectedNumberOfUtxos, matchingUtxos.size());
    }

    private static void assertSelectedUtxosBelongToTheInputs(List<UTXO> selectedUtxos,
        List<TransactionInput> releaseTransactionInputs) {
        assertEquals(releaseTransactionInputs.size(), selectedUtxos.size());
        for (UTXO utxo : selectedUtxos) {
            List<TransactionInput> matchingInputs = releaseTransactionInputs.stream().
                filter(input -> input.getOutpoint().getHash().equals(utxo.getHash())
                    && input.getOutpoint().getIndex() == utxo.getIndex()).toList();
            assertEquals(1, matchingInputs.size());
        }
    }

    /** Input count, script format, and selected-UTXO alignment for peg-out / batched flows. */
    private static void assertReleaseTxInputsStandardMultisig(
        BtcTransaction tx,
        int expectedInputCount,
        Script federationRedeemScript,
        List<UTXO> federationUtxos,
        List<UTXO> selectedUtxos) {
        List<TransactionInput> inputs = tx.getInputs();
        assertEquals(expectedInputCount, inputs.size());
        assertReleaseTxInputsHasProperFormatAndBelongsToStandardMultisigFederation(
            tx, federationRedeemScript, federationUtxos);
        assertSelectedUtxosBelongToTheInputs(selectedUtxos, inputs);
    }

    private static void assertReleaseTxInputsP2shErp(
        BtcTransaction tx,
        int expectedInputCount,
        Script federationRedeemScript,
        List<UTXO> federationUtxos,
        List<UTXO> selectedUtxos) {
        List<TransactionInput> inputs = tx.getInputs();
        assertEquals(expectedInputCount, inputs.size());
        assertReleaseTxInputsHasProperFormatAndBelongsToP2shErpFederation(
            tx, federationRedeemScript, federationUtxos);
        assertSelectedUtxosBelongToTheInputs(selectedUtxos, inputs);
    }

    private static void assertReleaseTxInputsP2shP2wshErp(
        BtcTransaction tx,
        int expectedInputCount,
        Script federationRedeemScript,
        List<UTXO> federationUtxos,
        List<UTXO> selectedUtxos) {
        List<TransactionInput> inputs = tx.getInputs();
        assertEquals(expectedInputCount, inputs.size());
        assertReleaseTxInputsHasProperFormatAndBelongsToP2shP2wshErpFederation(
            tx, federationRedeemScript, federationUtxos);
        assertSelectedUtxosBelongToTheInputs(selectedUtxos, inputs);
    }

    /**
     * Like {@link #assertReleaseTxInputsStandardMultisig} but for migration: all retiring federation UTXOs
     * are spent and {@code selectedUtxos} must match that set exactly.
     */
    private static void assertMigrationReleaseTxInputsStandardMultisig(
        BtcTransaction migrationTransaction,
        Script retiringFederationRedeemScript,
        List<UTXO> retiringFederationUtxos,
        List<UTXO> selectedUtxos) {
        List<TransactionInput> inputs = migrationTransaction.getInputs();
        assertEquals(retiringFederationUtxos.size(), inputs.size());
        assertReleaseTxInputsHasProperFormatAndBelongsToStandardMultisigFederation(
            migrationTransaction, retiringFederationRedeemScript, retiringFederationUtxos);
        assertEquals(retiringFederationUtxos, selectedUtxos);
        assertSelectedUtxosBelongToTheInputs(selectedUtxos, inputs);
    }

    private static void assertMigrationReleaseTxInputsP2shErp(
        BtcTransaction migrationTransaction,
        Script retiringFederationRedeemScript,
        List<UTXO> retiringFederationUtxos,
        List<UTXO> selectedUtxos) {
        List<TransactionInput> inputs = migrationTransaction.getInputs();
        assertEquals(retiringFederationUtxos.size(), inputs.size());
        assertReleaseTxInputsHasProperFormatAndBelongsToP2shErpFederation(
            migrationTransaction, retiringFederationRedeemScript, retiringFederationUtxos);
        assertEquals(retiringFederationUtxos, selectedUtxos);
        assertSelectedUtxosBelongToTheInputs(selectedUtxos, inputs);
    }

    private static void assertMigrationReleaseTxInputsP2shP2wshErp(
        BtcTransaction migrationTransaction,
        Script retiringFederationRedeemScript,
        List<UTXO> retiringFederationUtxos,
        List<UTXO> selectedUtxos) {
        List<TransactionInput> inputs = migrationTransaction.getInputs();
        assertEquals(retiringFederationUtxos.size(), inputs.size());
        assertReleaseTxInputsHasProperFormatAndBelongsToP2shP2wshErpFederation(
            migrationTransaction, retiringFederationRedeemScript, retiringFederationUtxos);
        assertEquals(retiringFederationUtxos, selectedUtxos);
        assertSelectedUtxosBelongToTheInputs(selectedUtxos, inputs);
    }

    private static void assertBuildResultResponseCode(ReleaseTransactionBuilder.Response expectedResponseCode,
        ReleaseTransactionBuilder.BuildResult buildResult) {
        ReleaseTransactionBuilder.Response actualResponseCode = buildResult.responseCode();
        assertEquals(expectedResponseCode, actualResponseCode);
    }

    private static void assertBtcTxVersionIs1(BtcTransaction releaseTransaction) {
        assertEquals(BTC_TX_VERSION_1, releaseTransaction.getVersion());
    }

    private static void assertBtcTxVersionIs2(BtcTransaction releaseTransaction) {
        assertEquals(BTC_TX_VERSION_2, releaseTransaction.getVersion());
    }

    private static void assertDestinationAddress(List<TransactionOutput> releaseTransactionOutputs,
        Address expectedDestinationAddress,
        NetworkParameters networkParameters) {
        for (TransactionOutput output : releaseTransactionOutputs) {
            Address destinationAddress = output.getScriptPubKey().getToAddress(networkParameters);
            assertEquals(expectedDestinationAddress, destinationAddress);
        }
    }

    private static void assertOutputsWithDustChange(BtcTransaction releaseTransaction,
        List<TransactionOutput> releaseTransactionChangeOutputs,
        Coin requestedAmount) {
        Coin inputTotalAmount = releaseTransaction.getInputSum();
        Coin originalChangeAmount = inputTotalAmount.subtract(requestedAmount);
        assertTrue(isDust(originalChangeAmount));

        Coin amountToGetNonDustValue = MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT.subtract(originalChangeAmount);
        Coin amountToSend = requestedAmount.subtract(amountToGetNonDustValue);

        assertOutputsUserAndChangeValues(releaseTransaction, releaseTransactionChangeOutputs, amountToSend, MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT);
    }

    private static void assertOutputsWithNonDustChange(BtcTransaction releaseTransaction,
        List<TransactionOutput> releaseTransactionChangeOutputs,
        Coin requestedAmount) {
        Coin inputTotalAmount = releaseTransaction.getInputSum();
        Coin expectedChangeAmount = inputTotalAmount.subtract(requestedAmount);
        assertOutputsUserAndChangeValues(releaseTransaction, releaseTransactionChangeOutputs, requestedAmount, expectedChangeAmount);
    }

    private static void assertOutputsUserAndChangeValues(BtcTransaction releaseTransaction,
        List<TransactionOutput> releaseTransactionChangeOutputs,
        Coin amountToSend,
        Coin expectedChangeAmount) {
        Coin changeOutputsAmount = getOutputsAmount(releaseTransactionChangeOutputs);
        assertEquals(expectedChangeAmount, changeOutputsAmount);

        Coin userOutputsAmount = releaseTransaction.getOutputSum().subtract(changeOutputsAmount);
        Coin releaseTransactionFees = releaseTransaction.getFee();
        Coin userOutputsAndFeesAmount = releaseTransactionFees.add(userOutputsAmount);
        assertEquals(amountToSend, userOutputsAndFeesAmount);
        Coin inputTotalAmount = releaseTransaction.getInputSum();
        assertEquals(inputTotalAmount, userOutputsAndFeesAmount.add(changeOutputsAmount));
    }

    private static Coin getOutputsAmount(List<TransactionOutput> outputs) {
        return outputs.stream()
            .map(TransactionOutput::getValue)
            .reduce(Coin::add)
            .orElse(Coin.ZERO);
    }

    private static boolean isDust(Coin expectedChangeAmount) {
        return expectedChangeAmount.compareTo(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT) < 0;
    }

    private static void assertOutputsWithNoChange(BtcTransaction releaseTransaction,
        Coin expectedSentAmount) {
        Coin outputsAmount = releaseTransaction.getOutputSum();
        Coin fees = releaseTransaction.getFee();
        Coin totalAmountSent = fees.add(outputsAmount);
        assertEquals(expectedSentAmount, totalAmountSent);

        Coin inputTotalAmount = releaseTransaction.getInputSum();
        assertEquals(inputTotalAmount, totalAmountSent);
    }

    private static void assertReleaseTxNumberOfOutputs(int expectedNumberOfOutputs,
        List<TransactionOutput> releaseTransactionOutputs) {
        int actualNumberOfOutputs = releaseTransactionOutputs.size();
        assertEquals(expectedNumberOfOutputs, actualNumberOfOutputs);
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
