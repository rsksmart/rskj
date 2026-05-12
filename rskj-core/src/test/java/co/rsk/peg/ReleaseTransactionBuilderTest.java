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

import static co.rsk.peg.ReleaseTransactionBuilder.BTC_TX_VERSION_1;
import static co.rsk.peg.ReleaseTransactionBuilder.BTC_TX_VERSION_2;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.createHash;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.wallet.SendRequest;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.bitcoin.RedeemScriptCreationException;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.constants.BridgeRegTestConstants;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import co.rsk.peg.federation.*;
import co.rsk.test.builders.UTXOBuilder;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import static co.rsk.RskTestUtils.createRepository;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.COULD_NOT_ADJUST_DOWNWARDS;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.DUSTY_SEND_REQUESTED;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.EXCEED_MAX_TRANSACTION_SIZE;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.INSUFFICIENT_MONEY;
import static co.rsk.peg.ReleaseTransactionBuilder.Response.SUCCESS;
import static co.rsk.peg.ReleaseTransactionBuilderAssertions.*;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT;
import co.rsk.peg.ReleaseTransactionBuilder.BuildResult;
import org.ethereum.core.Repository;

/**
 * Tests for {@link ReleaseTransactionBuilder}: regtest/mock scenarios, mainnet integration pegouts,
 * and empty-wallet flows. Nested classes group unrelated fixtures.
 */
class ReleaseTransactionBuilderTest {

    /**
     * Original regtest and Mockito-based coverage (SVP fund, migration, batched pegouts, mock wallet paths).
     */
    @Nested
    class MockWalletReleaseBuilderTest {
        private final ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(0);
        private final BridgeConstants bridgeMainNetConstants = BridgeMainNetConstants.getInstance();
        private final NetworkParameters btcMainNetParams = bridgeMainNetConstants.getBtcParams();
        private final NetworkParameters regtestParameters =  new BridgeRegTestConstants().getBtcParams();
        private final Address changeAddress = BitcoinTestUtils.createP2PKHAddress(regtestParameters, "changeAddress");
        private final Coin feePerKb = Coin.MILLICOIN.multiply(2);
        private final Federation activeP2shErpFederation = P2shErpFederationBuilder.builder().build();
        private final Address activeP2shErpFederationAddress = activeP2shErpFederation.getAddress();
        private final Federation p2shP2wshErpProposedFederation = P2shP2wshErpFederationBuilder.builder().build();
        private Wallet wallet;
        private ReleaseTransactionBuilder builder;
        private Federation federation;
        private BridgeStorageProvider bridgeStorageProviderMock;


        @BeforeEach
        void setup() {
            wallet = mock(Wallet.class);
            bridgeStorageProviderMock = mock(BridgeStorageProvider.class);
            BridgeConstants bridgeRegTestConstants = new BridgeRegTestConstants();
            federation = FederationTestUtils.getGenesisFederation(bridgeRegTestConstants.getFederationConstants());
            builder = new ReleaseTransactionBuilder(
                regtestParameters,
                wallet,
                federation.getFormatVersion(),
                changeAddress,
                feePerKb,
                activations
            );
            new Context(regtestParameters);
        }

        @Test
        void first_output_pay_fees() {
            Federation activeFederation = P2shP2wshErpFederationBuilder.builder()
                .build();

            int numberOfUtxos = 2;
            List<UTXO> utxos = UTXOBuilder.builder()
                .withScriptPubKey(activeFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));

            Wallet thisWallet = BridgeUtils.getFederationSpendWallet(
                new Context(btcMainNetParams),
                activeFederation,
                utxos,
                false,
                bridgeStorageProviderMock
            );

            Address activeFederationAddress = activeFederation.getAddress();
            ReleaseTransactionBuilder rtb = new ReleaseTransactionBuilder(
                btcMainNetParams,
                thisWallet,
                federation.getFormatVersion(),
                activeFederationAddress,
                Coin.SATOSHI.multiply(1000),
                activations
            );

            Address pegoutRecipient = BitcoinTestUtils.createP2PKHAddress(btcMainNetParams, "destinationAddress");
            Coin pegoutAmount = Coin.COIN.add(Coin.SATOSHI);

            ReleaseTransactionBuilder.BuildResult result = rtb.buildAmountTo(
                pegoutRecipient,
                pegoutAmount
            );
            assertEquals(ReleaseTransactionBuilder.Response.SUCCESS, result.responseCode());

            Coin inputsValue = result.selectedUTXOs().stream().map(UTXO::getValue).reduce(Coin.ZERO, Coin::add);

            TransactionOutput changeOutput = result.btcTx().getOutput(1);

            // Second output should be the change output to the Federation
            assertEquals(activeFederationAddress, changeOutput.getAddressFromP2SH(btcMainNetParams));
            // And if its value is the spent UTXOs summatory minus the requested pegout amount
            // we can ensure the Federation is not paying fees for pegouts
            assertEquals(inputsValue.minus(pegoutAmount), changeOutput.getValue());
        }

        @Test
        void buildSvpFundTransaction_withAFederationWithEnoughUTXOsForTheSvpFundTransaction_shouldReturnACorrectSvpFundTx() {
            // Arrange
            int numberOfUtxos = 2;
            List<UTXO> utxos = UTXOBuilder.builder()
                .withScriptPubKey(activeP2shErpFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = getReleaseTransactionBuilderForMainnet(utxos);
            Keccak256 proposedFlyoverPrefix = bridgeMainNetConstants.getProposedFederationFlyoverPrefix();

            // Act
            Coin svpFundTxOutputsValue = bridgeMainNetConstants.getSvpFundTxOutputsValue();
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
            assertEquals(proposedFederationAddress, actualFirstOutput.getAddressFromP2SH(btcMainNetParams));

            TransactionOutput actualSecondOutput = svpFundTransactionUnsigned.getOutput(1);
            assertEquals(svpFundTxOutputsValue, actualSecondOutput.getValue());

            Address flyoverFederationAddress = PegUtils.getFlyoverFederationAddress(btcMainNetParams, proposedFlyoverPrefix, p2shP2wshErpProposedFederation);
            assertEquals(flyoverFederationAddress, actualSecondOutput.getAddressFromP2SH(btcMainNetParams));

            List<UTXO> selectedUTXOs = svpFundTransactionUnsignedBuildResult.selectedUTXOs();
            List<UTXO> expectedSelectedUTXOs = List.of(utxos.get(0)); // First UTXO is enough to cover the svpFundTxOutputsValue
            assertEquals(expectedSelectedUTXOs, selectedUTXOs);
        }

        @Test
        void buildSvpFundTransaction_withAFederationWithoutUTXOs_shouldThrowInsufficientMoneyResponseCode() {
            // Arrange
            List<UTXO> emptyUtxos = Collections.emptyList();
            ReleaseTransactionBuilder releaseTransactionBuilder = getReleaseTransactionBuilderForMainnet(emptyUtxos);
            Coin svpFundTxOutputsValue = bridgeMainNetConstants.getSvpFundTxOutputsValue();
            Keccak256 proposedFlyoverPrefix = bridgeMainNetConstants.getProposedFederationFlyoverPrefix();

            // Act
            ReleaseTransactionBuilder.BuildResult svpFundTransactionUnsignedBuildResult = releaseTransactionBuilder.buildSvpFundTransaction(
                p2shP2wshErpProposedFederation,
                proposedFlyoverPrefix,
                svpFundTxOutputsValue
            );

            // Assert
            ReleaseTransactionBuilder.Response expectedResponseCode = ReleaseTransactionBuilder.Response.INSUFFICIENT_MONEY;
            ReleaseTransactionBuilder.Response actualResponseCode = svpFundTransactionUnsignedBuildResult.responseCode();
            assertEquals(expectedResponseCode, actualResponseCode);

            List<UTXO> selectedUTXOs = svpFundTransactionUnsignedBuildResult.selectedUTXOs();
            assertNull(selectedUTXOs);

            BtcTransaction btcTx = svpFundTransactionUnsignedBuildResult.btcTx();
            assertNull(btcTx);
        }

        @Test
        void buildSvpFundTransaction_withDustValueAsSvpFundTxOutputsValue_shouldReturnDustySendRequestResponseCode() {
            // Arrange
            int numberOfUtxos = 2;
            List<UTXO> utxos = UTXOBuilder.builder()
                .withScriptPubKey(activeP2shErpFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));

            ReleaseTransactionBuilder releaseTransactionBuilder = getReleaseTransactionBuilderForMainnet(utxos);
            Keccak256 proposedFlyoverPrefix = bridgeMainNetConstants.getProposedFederationFlyoverPrefix();
            Coin svpFundTxOutputsValue = Coin.SATOSHI;

            // Act
            ReleaseTransactionBuilder.BuildResult svpFundTransactionUnsignedBuildResult = releaseTransactionBuilder.buildSvpFundTransaction(
                p2shP2wshErpProposedFederation,
                proposedFlyoverPrefix,
                svpFundTxOutputsValue
            );

            // Assert
            ReleaseTransactionBuilder.Response expectedResponseCode = ReleaseTransactionBuilder.Response.DUSTY_SEND_REQUESTED;
            ReleaseTransactionBuilder.Response actualResponseCode = svpFundTransactionUnsignedBuildResult.responseCode();
            assertEquals(expectedResponseCode, actualResponseCode);

            List<UTXO> selectedUTXOs = svpFundTransactionUnsignedBuildResult.selectedUTXOs();
            assertNull(selectedUTXOs);

            BtcTransaction btcTx = svpFundTransactionUnsignedBuildResult.btcTx();
            assertNull(btcTx);
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

        private ReleaseTransactionBuilder getReleaseTransactionBuilderForMainnet(List<UTXO> utxos) {
            Wallet thisWallet = BridgeUtils.getFederationSpendWallet(
                new Context(btcMainNetParams),
                activeP2shErpFederation,
                utxos,
                false,
                bridgeStorageProviderMock
            );

            return new ReleaseTransactionBuilder(
                btcMainNetParams,
                thisWallet,
                activeP2shErpFederation.getFormatVersion(),
                activeP2shErpFederationAddress,
                feePerKb,
                activations
            );
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
            Keccak256 proposedFlyoverPrefix = bridgeMainNetConstants.getProposedFederationFlyoverPrefix();
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
            Keccak256 proposedFlyoverPrefix = bridgeMainNetConstants.getProposedFederationFlyoverPrefix();

            // Act & Assert
            assertThrows(NullPointerException.class, () -> releaseTransactionBuilder.buildSvpFundTransaction(
                p2shP2wshErpProposedFederation,
                proposedFlyoverPrefix,
                null
            ));
        }

        @Test
        void buildMigrationTransaction_withAFederationWithEnoughUTXOs_beforeRSKIP376_shouldReturnACorrectMigrationTx_withVersion1() {
            // Arrange
            int numberOfUtxos = 2;
            List<UTXO> utxos = UTXOBuilder.builder()
                .withScriptPubKey(activeP2shErpFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            Wallet thisWallet = BridgeUtils.getFederationSpendWallet(
                new Context(btcMainNetParams),
                activeP2shErpFederation,
                utxos,
                false,
                bridgeStorageProviderMock
            );

            ActivationConfig.ForBlock thisActivations = ActivationConfigsForTest.fingerroot500().forBlock(0);
            ReleaseTransactionBuilder releaseTransactionBuilder = new ReleaseTransactionBuilder(
                btcMainNetParams,
                thisWallet,
                activeP2shErpFederation.getFormatVersion(),
                activeP2shErpFederationAddress,
                feePerKb,
                thisActivations
            );

            Coin migrationTxOutputsValue = thisWallet.getBalance();
            Address proposedFederationAddress = p2shP2wshErpProposedFederation.getAddress();

            // Act
            ReleaseTransactionBuilder.BuildResult migrationTransactionUnsignedBuildResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationTxOutputsValue,
                proposedFederationAddress
            );

            // Assert
            ReleaseTransactionBuilder.Response expectedResponseCode = ReleaseTransactionBuilder.Response.SUCCESS;
            ReleaseTransactionBuilder.Response actualResponseCode = migrationTransactionUnsignedBuildResult.responseCode();
            assertEquals(expectedResponseCode, actualResponseCode);

            BtcTransaction migrationTransactionUnsigned = migrationTransactionUnsignedBuildResult.btcTx();

            int expectedNumberOfInputs = utxos.size(); // All UTXOs should be migrated
            assertEquals(expectedNumberOfInputs, migrationTransactionUnsigned.getInputs().size());

            int expectedNumberOfOutputs = 1; // 1 for the new federation
            assertEquals(expectedNumberOfOutputs, migrationTransactionUnsigned.getOutputs().size());

            TransactionOutput actualFirstOutput = migrationTransactionUnsigned.getOutput(0);
            Coin valuePlusFee = actualFirstOutput.getValue().add(migrationTransactionUnsigned.getFee());
            assertEquals(migrationTxOutputsValue, valuePlusFee);
            assertEquals(proposedFederationAddress, actualFirstOutput.getAddressFromP2SH(btcMainNetParams));

            assertEquals(BTC_TX_VERSION_1, migrationTransactionUnsigned.getVersion());
        }

        @Test
        void buildMigrationTransaction_withAFederationWithEnoughUTXOs_afterRSKIP376_shouldReturnACorrectMigrationTx_withVersion2() {
            // Arrange
            int numberOfUtxos = 2;
            List<UTXO> utxos = UTXOBuilder.builder()
                .withScriptPubKey(activeP2shErpFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            Wallet thisWallet = BridgeUtils.getFederationSpendWallet(
                new Context(btcMainNetParams),
                activeP2shErpFederation,
                utxos,
                false,
                bridgeStorageProviderMock
            );

            ReleaseTransactionBuilder releaseTransactionBuilder = new ReleaseTransactionBuilder(
                btcMainNetParams,
                thisWallet,
                activeP2shErpFederation.getFormatVersion(),
                activeP2shErpFederationAddress,
                feePerKb,
                activations
            );

            Coin migrationTxOutputsValue = thisWallet.getBalance();
            Address proposedFederationAddress = p2shP2wshErpProposedFederation.getAddress();

            // Act
            ReleaseTransactionBuilder.BuildResult migrationTransactionUnsignedBuildResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationTxOutputsValue,
                proposedFederationAddress
            );

            // Assert
            ReleaseTransactionBuilder.Response expectedResponseCode = ReleaseTransactionBuilder.Response.SUCCESS;
            ReleaseTransactionBuilder.Response actualResponseCode = migrationTransactionUnsignedBuildResult.responseCode();
            assertEquals(expectedResponseCode, actualResponseCode);

            BtcTransaction migrationTransactionUnsigned = migrationTransactionUnsignedBuildResult.btcTx();

            int expectedNumberOfInputs = utxos.size(); // All UTXOs should be migrated
            assertEquals(expectedNumberOfInputs, migrationTransactionUnsigned.getInputs().size());

            int expectedNumberOfOutputs = 1; // 1 for the new federation
            assertEquals(expectedNumberOfOutputs, migrationTransactionUnsigned.getOutputs().size());

            TransactionOutput actualFirstOutput = migrationTransactionUnsigned.getOutput(0);
            Coin valuePlusFee = actualFirstOutput.getValue().add(migrationTransactionUnsigned.getFee());
            assertEquals(migrationTxOutputsValue, valuePlusFee);
            assertEquals(proposedFederationAddress, actualFirstOutput.getAddressFromP2SH(btcMainNetParams));

            assertEquals(BTC_TX_VERSION_2, migrationTransactionUnsigned.getVersion());
        }

        @Test
        void buildMigrationTransaction_transferringABalance_withAFederationWithoutUTXOs_shouldReturnInsufficientBalance() {
            // Arrange
            List<UTXO> utxos = Collections.emptyList();
            ReleaseTransactionBuilder releaseTransactionBuilder = getReleaseTransactionBuilderForMainnet(utxos);

            Coin migrationTxOutputsValue = Coin.COIN;
            Address proposedFederationAddress = p2shP2wshErpProposedFederation.getAddress();

            // Act
            ReleaseTransactionBuilder.BuildResult migrationTransactionUnsignedBuildResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationTxOutputsValue,
                proposedFederationAddress
            );

            // Assert
            ReleaseTransactionBuilder.Response expectedResponseCode = ReleaseTransactionBuilder.Response.INSUFFICIENT_MONEY;
            ReleaseTransactionBuilder.Response actualResponseCode = migrationTransactionUnsignedBuildResult.responseCode();
            assertEquals(expectedResponseCode, actualResponseCode);

            List<UTXO> selectedUTXOs = migrationTransactionUnsignedBuildResult.selectedUTXOs();
            assertNull(selectedUTXOs);

            BtcTransaction btcTx = migrationTransactionUnsignedBuildResult.btcTx();
            assertNull(btcTx);
        }

        @Test
        void buildMigrationTransaction_transferringZeroBalance_withAFederationWithoutUTXOs_shouldReturnDustySendRequestedResponseCode() {
            // Arrange
            List<UTXO> utxos = Collections.emptyList();
            Wallet thisWallet = BridgeUtils.getFederationSpendWallet(
                new Context(btcMainNetParams),
                activeP2shErpFederation,
                utxos,
                false,
                bridgeStorageProviderMock
            );

            ReleaseTransactionBuilder releaseTransactionBuilder = new ReleaseTransactionBuilder(
                btcMainNetParams,
                thisWallet,
                activeP2shErpFederation.getFormatVersion(),
                activeP2shErpFederationAddress,
                feePerKb,
                activations
            );

            Coin migrationTxOutputsValue = thisWallet.getBalance(); // ZERO
            Address proposedFederationAddress = p2shP2wshErpProposedFederation.getAddress();

            // Act
            ReleaseTransactionBuilder.BuildResult migrationTransactionUnsignedBuildResult = releaseTransactionBuilder.buildMigrationTransaction(
                migrationTxOutputsValue,
                proposedFederationAddress
            );

            // Assert
            ReleaseTransactionBuilder.Response expectedResponseCode = ReleaseTransactionBuilder.Response.DUSTY_SEND_REQUESTED;
            ReleaseTransactionBuilder.Response actualResponseCode = migrationTransactionUnsignedBuildResult.responseCode();
            assertEquals(expectedResponseCode, actualResponseCode);

            List<UTXO> selectedUTXOs = migrationTransactionUnsignedBuildResult.selectedUTXOs();
            assertNull(selectedUTXOs);

            BtcTransaction btcTx = migrationTransactionUnsignedBuildResult.btcTx();
            assertNull(btcTx);
        }

        @Test
        void buildMigrationTransaction_withNullMigrationValue_shouldThrowNullPointerException() {
            // Arrange
            int numberOfUtxos = 2;
            List<UTXO> utxos = UTXOBuilder.builder()
                .withScriptPubKey(activeP2shErpFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = getReleaseTransactionBuilderForMainnet(utxos);
            Address proposedFederationAddress = p2shP2wshErpProposedFederation.getAddress();

            // Act & Assert
            assertThrows(NullPointerException.class, () -> releaseTransactionBuilder.buildMigrationTransaction(
                null,
                proposedFederationAddress
            ));
        }

        @Test
        void buildMigrationTransaction_withNullDestinationAddress_shouldThrowNullPointerException() {
            // Arrange
            int numberOfUtxos = 2;
            List<UTXO> utxos = UTXOBuilder.builder()
                .withScriptPubKey(activeP2shErpFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            Wallet thisWallet = BridgeUtils.getFederationSpendWallet(
                new Context(btcMainNetParams),
                activeP2shErpFederation,
                utxos,
                false,
                bridgeStorageProviderMock
            );

            ReleaseTransactionBuilder releaseTransactionBuilder = new ReleaseTransactionBuilder(
                btcMainNetParams,
                thisWallet,
                activeP2shErpFederation.getFormatVersion(),
                activeP2shErpFederationAddress,
                feePerKb,
                activations
            );

            Coin migrationTxOutputsValue = thisWallet.getBalance();

            // Act & Assert
            assertThrows(NullPointerException.class, () -> releaseTransactionBuilder.buildMigrationTransaction(
                migrationTxOutputsValue,
                null
            ));
        }

        @Test
        void build_pegout_tx_from_non_standard_erp_federation() {
            // Use mainnet constants to test a real situation
            List<FederationMember> members = FederationMember.getFederationMembersFromKeys(
                Arrays.asList(
                    new BtcECKey(),
                    new BtcECKey(),
                    new BtcECKey()
                )
            );
            FederationArgs federationArgs = new FederationArgs(members, Instant.now(), 0, btcMainNetParams);

            ErpFederation nonStandardErpFederation = FederationFactory.buildNonStandardErpFederation(
                federationArgs,
                bridgeMainNetConstants.getFederationConstants().getErpFedPubKeysList(),
                bridgeMainNetConstants.getFederationConstants().getErpFedActivationDelay(),
                activations
            );

            int numberOfUtxos = 2;
            List<UTXO> utxos = UTXOBuilder.builder()
                .withScriptPubKey(nonStandardErpFederation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));

            Wallet thisWallet = BridgeUtils.getFederationSpendWallet(
                new Context(btcMainNetParams),
                nonStandardErpFederation,
                utxos,
                false,
                mock(BridgeStorageProvider.class)
            );

            Address nonStandardErpFederationAddress = nonStandardErpFederation.getAddress();
            ReleaseTransactionBuilder releaseTransactionBuilder = new ReleaseTransactionBuilder(
                btcMainNetParams,
                thisWallet,
                nonStandardErpFederation.getFormatVersion(),
                nonStandardErpFederationAddress,
                Coin.SATOSHI.multiply(1000),
                activations
            );

            Address pegoutRecipient = BitcoinTestUtils.createP2PKHAddress(btcMainNetParams, "destinationAddress");
            Coin pegoutAmount = Coin.COIN.add(Coin.SATOSHI);

            ReleaseTransactionBuilder.BuildResult result = releaseTransactionBuilder.buildAmountTo(
                pegoutRecipient,
                pegoutAmount
            );
            assertEquals(ReleaseTransactionBuilder.Response.SUCCESS, result.responseCode());
        }

        @Test
        void getters() {
            Assertions.assertSame(wallet, builder.getWallet());
            Assertions.assertSame(changeAddress, builder.getChangeAddress());
            assertEquals(Coin.MILLICOIN.multiply(2), builder.getFeePerKb());
        }

        @Test
        void buildAmountTo_ok() throws InsufficientMoneyException, UTXOProviderException {
            Address to = mockAddress(123);
            Coin amount = Coin.CENT.multiply(3);

            List<UTXO> availableUTXOs = Arrays.asList(
                UTXOBuilder.builder()
                    .withTransactionHash(mockUTXOHash("one"))
                    .withOutpointIndex(0)
                    .withValue(Coin.COIN)
                    .build(),
                UTXOBuilder.builder()
                    .withTransactionHash(mockUTXOHash("one"))
                    .withOutpointIndex(1)
                    .withValue(Coin.COIN.multiply(2))
                    .build(),
                UTXOBuilder.builder()
                    .withTransactionHash(mockUTXOHash("two"))
                    .withOutpointIndex(0)
                    .withValue(Coin.MILLICOIN.times(7))
                    .build(),
                UTXOBuilder.builder()
                    .withTransactionHash(mockUTXOHash("two"))
                    .withOutpointIndex(1)
                    .withValue(Coin.COIN.divide(2))
                    .build(),
                UTXOBuilder.builder()
                    .withTransactionHash(mockUTXOHash("two"))
                    .withOutpointIndex(2)
                    .withValue(Coin.FIFTY_COINS)
                    .build(),
                UTXOBuilder.builder()
                    .withTransactionHash(mockUTXOHash("three"))
                    .withOutpointIndex(0)
                    .withValue(Coin.CENT.times(3))
                    .build()
            );

            UTXOProvider utxoProvider = mock(UTXOProvider.class);
            when(wallet.getUTXOProvider()).thenReturn(utxoProvider);
            when(wallet.getWatchedAddresses()).thenReturn(Collections.singletonList(changeAddress));
            when(utxoProvider.getOpenTransactionOutputs(any(List.class))).then((InvocationOnMock m) -> {
                List<Address> addresses = m.<List>getArgument(0);
                assertEquals(Collections.singletonList(changeAddress), addresses);
                return availableUTXOs;
            });

            Mockito.doAnswer((InvocationOnMock m) -> {
                SendRequest sr = m.getArgument(0);

                assertEquals(Coin.MILLICOIN.multiply(2), sr.feePerKb);
                assertEquals(Wallet.MissingSigsMode.USE_OP_ZERO, sr.missingSigsMode);
                assertEquals(changeAddress, sr.changeAddress);
                assertFalse(sr.shuffleOutputs);
                assertTrue(sr.recipientsPayFees);

                BtcTransaction tx = sr.tx;

                assertEquals(1, tx.getOutputs().size());
                assertEquals(amount, tx.getOutput(0).getValue());
                assertEquals(to, tx.getOutput(0).getAddressFromP2PKHScript(NetworkParameters.fromID(NetworkParameters.ID_REGTEST)));

                tx.addInput(mockUTXOHash("two"), 2, mock(Script.class));
                tx.addInput(mockUTXOHash("three"), 0, mock(Script.class));

                return null;
            }).when(wallet).completeTx(any(SendRequest.class));

            ReleaseTransactionBuilder.BuildResult result = builder.buildAmountTo(to, amount);

            assertEquals(ReleaseTransactionBuilder.Response.SUCCESS, result.responseCode());

            BtcTransaction tx = result.btcTx();
            List<UTXO> selectedUTXOs = result.selectedUTXOs();

            assertEquals(1, tx.getOutputs().size());
            assertEquals(amount, tx.getOutput(0).getValue());
            assertEquals(to, tx.getOutput(0).getAddressFromP2PKHScript(NetworkParameters.fromID(NetworkParameters.ID_REGTEST)));

            assertEquals(2, tx.getInputs().size());
            assertEquals(mockUTXOHash("two"), tx.getInput(0).getOutpoint().getHash());
            assertEquals(2, tx.getInput(0).getOutpoint().getIndex());
            assertEquals(mockUTXOHash("three"), tx.getInput(1).getOutpoint().getHash());
            assertEquals(0, tx.getInput(1).getOutpoint().getIndex());

            assertEquals(2, selectedUTXOs.size());
            assertEquals(mockUTXOHash("two"), selectedUTXOs.get(0).getHash());
            assertEquals(2, selectedUTXOs.get(0).getIndex());
            assertEquals(mockUTXOHash("three"), selectedUTXOs.get(1).getHash());
            assertEquals(0, selectedUTXOs.get(1).getIndex());
        }

        @Test
        void buildAmountTo_insufficientMoneyException() throws InsufficientMoneyException {
            Address to = mockAddress(123);
            Coin amount = Coin.CENT.multiply(3);

            mockCompleteTxWithThrowForBuildToAmount(wallet, amount, to, new InsufficientMoneyException(Coin.valueOf(1234)));

            ReleaseTransactionBuilder.BuildResult result = builder.buildAmountTo(to, amount);

            assertEquals(ReleaseTransactionBuilder.Response.INSUFFICIENT_MONEY, result.responseCode());
            verify(wallet, never()).getWatchedAddresses();
            verify(wallet, never()).getUTXOProvider();
        }

        @Test
        void buildAmountTo_walletCouldNotAdjustDownwards() throws InsufficientMoneyException {
            Address to = mockAddress(123);
            Coin amount = Coin.CENT.multiply(3);

            mockCompleteTxWithThrowForBuildToAmount(wallet, amount, to, new Wallet.CouldNotAdjustDownwards());

            ReleaseTransactionBuilder.BuildResult result = builder.buildAmountTo(to, amount);

            assertEquals(ReleaseTransactionBuilder.Response.COULD_NOT_ADJUST_DOWNWARDS, result.responseCode());
            verify(wallet, never()).getWatchedAddresses();
            verify(wallet, never()).getUTXOProvider();
        }

        @Test
        void buildAmountTo_walletExceededMaxTransactionSize() throws InsufficientMoneyException {
            Address to = mockAddress(123);
            Coin amount = Coin.CENT.multiply(3);

            mockCompleteTxWithThrowForBuildToAmount(wallet, amount, to, new Wallet.ExceededMaxTransactionSize());

            ReleaseTransactionBuilder.BuildResult result = builder.buildAmountTo(to, amount);

            assertEquals(ReleaseTransactionBuilder.Response.EXCEED_MAX_TRANSACTION_SIZE, result.responseCode());
            verify(wallet, never()).getWatchedAddresses();
            verify(wallet, never()).getUTXOProvider();
        }

        @Test
        void buildAmountTo_utxoProviderException() throws InsufficientMoneyException, UTXOProviderException {
            Address to = mockAddress(123);
            Coin amount = Coin.CENT.multiply(3);

            UTXOProvider utxoProvider = mock(UTXOProvider.class);
            when(wallet.getUTXOProvider()).thenReturn(utxoProvider);
            when(wallet.getWatchedAddresses()).thenReturn(Collections.singletonList(changeAddress));
            when(utxoProvider.getOpenTransactionOutputs(any(List.class))).then((InvocationOnMock m) -> {
                List<Address> addresses = m.<List>getArgument(0);
                assertEquals(Collections.singletonList(changeAddress), addresses);
                throw new UTXOProviderException();
            });

            Mockito.doAnswer((InvocationOnMock m) -> {
                SendRequest sr = m.getArgument(0);

                assertEquals(Coin.MILLICOIN.multiply(2), sr.feePerKb);
                assertEquals(Wallet.MissingSigsMode.USE_OP_ZERO, sr.missingSigsMode);
                assertEquals(changeAddress, sr.changeAddress);
                assertFalse(sr.shuffleOutputs);
                assertTrue(sr.recipientsPayFees);

                BtcTransaction tx = sr.tx;

                assertEquals(1, tx.getOutputs().size());
                assertEquals(amount, tx.getOutput(0).getValue());
                assertEquals(to, tx.getOutput(0).getAddressFromP2PKHScript(NetworkParameters.fromID(NetworkParameters.ID_REGTEST)));

                tx.addInput(mockUTXOHash("two"), 2, mock(Script.class));
                tx.addInput(mockUTXOHash("three"), 0, mock(Script.class));

                return null;
            }).when(wallet).completeTx(any(SendRequest.class));

            ReleaseTransactionBuilder.BuildResult result = builder.buildAmountTo(to, amount);
            verify(wallet, times(1)).completeTx(any(SendRequest.class));

            assertEquals(ReleaseTransactionBuilder.Response.UTXO_PROVIDER_EXCEPTION, result.responseCode());
        }

        @Test
        void buildAmountTo_illegalStateException() throws InsufficientMoneyException {
            Address to = mockAddress(123);
            Coin amount = Coin.CENT.multiply(3);

            doThrow(new IllegalStateException()).when(wallet).completeTx(any(SendRequest.class));

            assertThrows(IllegalStateException.class, () -> builder.buildAmountTo(to, amount));

            verify(wallet, times(1)).completeTx(any(SendRequest.class));
        }

        @Test
        void buildEmptyWalletTo_ok_before_RSKIP_201_activation() throws
            InsufficientMoneyException, UTXOProviderException {
            ActivationConfig.ForBlock activationConfig = ActivationConfigsForTest.papyrus200().forBlock(0);
            ReleaseTransactionBuilder thisBuilder = new ReleaseTransactionBuilder(
                regtestParameters,
                wallet,
                federation.getFormatVersion(),
                changeAddress,
                feePerKb,
                activationConfig
            );
            test_buildEmptyWalletTo_ok(thisBuilder, 1);
        }

        @Test
        void buildEmptyWalletTo_ok_after_RSKIP_201_activation()
            throws InsufficientMoneyException, UTXOProviderException {
            test_buildEmptyWalletTo_ok(builder, 2);
        }

        @Test
        void buildEmptyWalletTo_insufficientMoneyException() throws InsufficientMoneyException {
            Address to = mockAddress(123);

            mockCompleteTxWithThrowForEmptying(wallet, to, new InsufficientMoneyException(Coin.valueOf(1234)));

            ReleaseTransactionBuilder.BuildResult result = builder.buildEmptyWalletTo(to);

            assertEquals(ReleaseTransactionBuilder.Response.INSUFFICIENT_MONEY, result.responseCode());
            verify(wallet, never()).getWatchedAddresses();
            verify(wallet, never()).getUTXOProvider();
        }

        @Test
        void buildEmptyWalletTo_walletCouldNotAdjustDownwards() throws InsufficientMoneyException {
            Address to = mockAddress(123);

            mockCompleteTxWithThrowForEmptying(wallet, to, new Wallet.CouldNotAdjustDownwards());

            ReleaseTransactionBuilder.BuildResult result = builder.buildEmptyWalletTo(to);

            assertEquals(ReleaseTransactionBuilder.Response.COULD_NOT_ADJUST_DOWNWARDS, result.responseCode());
            verify(wallet, never()).getWatchedAddresses();
            verify(wallet, never()).getUTXOProvider();
        }

        @Test
        void buildEmptyWalletTo_walletExceededMaxTransactionSize() throws InsufficientMoneyException {
            Address to = mockAddress(123);

            mockCompleteTxWithThrowForEmptying(wallet, to, new Wallet.ExceededMaxTransactionSize());

            ReleaseTransactionBuilder.BuildResult result = builder.buildEmptyWalletTo(to);

            assertEquals(ReleaseTransactionBuilder.Response.EXCEED_MAX_TRANSACTION_SIZE, result.responseCode());
            verify(wallet, never()).getWatchedAddresses();
            verify(wallet, never()).getUTXOProvider();
        }

        @Test
        void buildEmptyWalletTo_utxoProviderException() throws InsufficientMoneyException, UTXOProviderException {
            Address to = mockAddress(123);

            UTXOProvider utxoProvider = mock(UTXOProvider.class);
            when(wallet.getUTXOProvider()).thenReturn(utxoProvider);
            when(wallet.getWatchedAddresses()).thenReturn(Collections.singletonList(to));
            when(utxoProvider.getOpenTransactionOutputs(any(List.class))).then((InvocationOnMock m) -> {
                List<Address> addresses = m.<List>getArgument(0);
                assertEquals(Collections.singletonList(to), addresses);
                throw new UTXOProviderException();
            });

            Mockito.doAnswer((InvocationOnMock m) -> {
                SendRequest sr = m.getArgument(0);

                assertEquals(Coin.MILLICOIN.multiply(2), sr.feePerKb);
                assertEquals(Wallet.MissingSigsMode.USE_OP_ZERO, sr.missingSigsMode);
                assertEquals(to, sr.changeAddress);
                assertFalse(sr.shuffleOutputs);
                assertTrue(sr.recipientsPayFees);
                assertTrue(sr.emptyWallet);

                BtcTransaction tx = sr.tx;

                assertEquals(1, tx.getOutputs().size());
                assertEquals(Coin.ZERO, tx.getOutput(0).getValue());
                assertEquals(to, tx.getOutput(0).getAddressFromP2PKHScript(NetworkParameters.fromID(NetworkParameters.ID_REGTEST)));

                tx.addInput(mockUTXOHash("two"), 2, mock(Script.class));
                tx.addInput(mockUTXOHash("three"), 0, mock(Script.class));
                tx.getOutput(0).setValue(Coin.FIFTY_COINS);

                return null;
            }).when(wallet).completeTx(any(SendRequest.class));

            ReleaseTransactionBuilder.BuildResult result = builder.buildEmptyWalletTo(to);
            verify(wallet, times(1)).completeTx(any(SendRequest.class));

            assertEquals(ReleaseTransactionBuilder.Response.UTXO_PROVIDER_EXCEPTION, result.responseCode());
        }

        @Test
        void test_BuildBatchedPegouts_ok() {
            ReleaseRequestQueue.Entry testEntry1 = createTestEntry(123, 2);
            ReleaseRequestQueue.Entry testEntry2 = createTestEntry(456, 4);
            ReleaseRequestQueue.Entry testEntry3 = createTestEntry(789, 5);
            List<ReleaseRequestQueue.Entry> pegoutRequests = Arrays.asList(testEntry1, testEntry2, testEntry3);

            Script outputScript = federation.getP2SHScript();
            List<UTXO> utxos = Arrays.asList(
                UTXOBuilder.builder()
                    .withScriptPubKey(outputScript)
                    .withTransactionHash(mockUTXOHash("1"))
                    .withOutpointIndex(0)
                    .withValue(Coin.COIN)
                    .build(),
                UTXOBuilder.builder()
                    .withScriptPubKey(outputScript)
                    .withTransactionHash(mockUTXOHash("2"))
                    .withOutpointIndex(2)
                    .withValue(Coin.FIFTY_COINS)
                    .build(),
                UTXOBuilder.builder()
                    .withScriptPubKey(outputScript)
                    .withTransactionHash(mockUTXOHash("3"))
                    .withOutpointIndex(0)
                    .withValue(Coin.CENT.times(3))
                    .build()
            );

            Wallet thisWallet = BridgeUtils.getFederationSpendWallet(
                Context.getOrCreate(regtestParameters),
                federation,
                utxos,
                false,
                mock(BridgeStorageProvider.class)
            );

            ReleaseTransactionBuilder rtb = new ReleaseTransactionBuilder(
                regtestParameters,
                thisWallet,
                federation.getFormatVersion(),
                federation.getAddress(),
                Coin.MILLICOIN,
                activations
            );

            ReleaseTransactionBuilder.BuildResult result = rtb.buildBatchedPegouts(pegoutRequests);

            assertEquals(ReleaseTransactionBuilder.Response.SUCCESS, result.responseCode());

            BtcTransaction tx = result.btcTx();
            List<UTXO> selectedUTXOs = result.selectedUTXOs();

            assertEquals(2, selectedUTXOs.size());

            assertEquals(4, tx.getOutputs().size());

            Address firstOutputAddress = testEntry1.getDestination();
            Address secondOutputAddress = testEntry2.getDestination();
            Address thirdOutputAddress = testEntry3.getDestination();
            assertEquals(firstOutputAddress, tx.getOutput(0).getAddressFromP2PKHScript(regtestParameters));
            assertEquals(secondOutputAddress, tx.getOutput(1).getAddressFromP2PKHScript(regtestParameters));
            assertEquals(thirdOutputAddress, tx.getOutput(2).getAddressFromP2PKHScript(regtestParameters));

            Sha256Hash firstUtxoHash = utxos.get(0).getHash();
            Sha256Hash thirdUtxoHash = utxos.get(2).getHash();

            assertEquals(2, tx.getInputs().size());
            assertEquals(firstUtxoHash, tx.getInput(1).getOutpoint().getHash());
            assertEquals(thirdUtxoHash, tx.getInput(0).getOutpoint().getHash());
        }

        @Test
        void test_BuildBatchedPegouts_ok_P2SHAddress() {
            ReleaseRequestQueue.Entry testEntry1 = createTestEntry(123, 2);

            List<BtcECKey> keys = BitcoinTestUtils.getBtcEcKeysFromSeeds(new String[]{"k1", "k2", "k3"}, true);
            ReleaseRequestQueue.Entry testEntry2 = new ReleaseRequestQueue.Entry(
                BitcoinTestUtils.createP2SHMultisigAddress(regtestParameters, keys),
                Coin.COIN
            );
            keys = BitcoinTestUtils.getBtcEcKeysFromSeeds(new String[]{"k4", "k5", "k6"}, true);
            ReleaseRequestQueue.Entry testEntry3 = new ReleaseRequestQueue.Entry(
                BitcoinTestUtils.createP2SHMultisigAddress(regtestParameters, keys),
                Coin.COIN
            );
            List<ReleaseRequestQueue.Entry> pegoutRequests = Arrays.asList(testEntry1, testEntry2, testEntry3);

            Script outputScript = federation.getP2SHScript();
            List<UTXO> utxos = Arrays.asList(
                UTXOBuilder.builder()
                    .withScriptPubKey(outputScript)
                    .withTransactionHash(mockUTXOHash("1"))
                    .withOutpointIndex(0)
                    .withValue(Coin.COIN)
                    .build(),
                UTXOBuilder.builder()
                    .withScriptPubKey(outputScript)
                    .withTransactionHash(mockUTXOHash("2"))
                    .withOutpointIndex(2)
                    .withValue(Coin.FIFTY_COINS)
                    .build(),
                UTXOBuilder.builder()
                    .withScriptPubKey(outputScript)
                    .withTransactionHash(mockUTXOHash("3"))
                    .withOutpointIndex(0)
                    .withValue(Coin.CENT.times(3))
                    .build()
            );

            Wallet thisWallet = BridgeUtils.getFederationSpendWallet(
                Context.getOrCreate(regtestParameters),
                federation,
                utxos,
                false,
                mock(BridgeStorageProvider.class)
            );

            ReleaseTransactionBuilder rtb = new ReleaseTransactionBuilder(
                regtestParameters,
                thisWallet,
                federation.getFormatVersion(),
                federation.getAddress(),
                Coin.MILLICOIN,
                activations
            );

            ReleaseTransactionBuilder.BuildResult result = rtb.buildBatchedPegouts(pegoutRequests);

            assertEquals(ReleaseTransactionBuilder.Response.SUCCESS, result.responseCode());

            BtcTransaction tx = result.btcTx();
            List<UTXO> selectedUTXOs = result.selectedUTXOs();

            assertEquals(3, selectedUTXOs.size());

            assertEquals(4, tx.getOutputs().size());

            Address firstOutputAddress = testEntry1.getDestination();
            Address secondOutputAddress = testEntry2.getDestination();
            Address thirdOutputAddress = testEntry3.getDestination();
            assertEquals(firstOutputAddress, tx.getOutput(0).getAddressFromP2PKHScript(regtestParameters));
            assertEquals(secondOutputAddress, tx.getOutput(1).getAddressFromP2SH(regtestParameters));
            assertEquals(thirdOutputAddress, tx.getOutput(2).getAddressFromP2SH(regtestParameters));

            Sha256Hash firstUtxoHash = utxos.get(0).getHash();
            Sha256Hash secondUtxoHash = utxos.get(1).getHash();
            Sha256Hash thirdUtxoHash = utxos.get(2).getHash();

            assertEquals(3, tx.getInputs().size());
            assertEquals(firstUtxoHash, tx.getInput(1).getOutpoint().getHash());
            assertEquals(secondUtxoHash, tx.getInput(2).getOutpoint().getHash());
            assertEquals(thirdUtxoHash, tx.getInput(0).getOutpoint().getHash());
        }

        @Test
        void test_BuildBatchedPegouts_InsufficientMoneyException() {
            ReleaseRequestQueue.Entry testEntry1 = createTestEntry(123, Coin.COIN);
            ReleaseRequestQueue.Entry testEntry2 = createTestEntry(456, Coin.COIN);
            ReleaseRequestQueue.Entry testEntry3 = createTestEntry(789, Coin.COIN);
            List<ReleaseRequestQueue.Entry> pegoutRequests = Arrays.asList(testEntry1, testEntry2, testEntry3);

            List<UTXO> utxos = new ArrayList<>();
            Script scriptPubKey = federation.getP2SHScript();
            int numberOfUtxos = 2;
            for (int i = 0; i < numberOfUtxos; i++) {
                String seed = String.valueOf(i + 1);
                Sha256Hash transactionHash = mockUTXOHash(seed);
                UTXO utxo = UTXOBuilder.builder()
                    .withScriptPubKey(scriptPubKey)
                    .withValue(Coin.COIN)
                    .withTransactionHash(transactionHash)
                    .build();
                utxos.add(utxo);
            }

            Wallet thisWallet = BridgeUtils.getFederationSpendWallet(
                Context.getOrCreate(regtestParameters),
                federation,
                utxos,
                false,
                mock(BridgeStorageProvider.class)
            );

            ReleaseTransactionBuilder rtb = new ReleaseTransactionBuilder(
                regtestParameters,
                thisWallet,
                federation.getFormatVersion(),
                federation.getAddress(),
                Coin.MILLICOIN,
                activations
            );

            ReleaseTransactionBuilder.BuildResult result = rtb.buildBatchedPegouts(pegoutRequests);

            assertEquals(ReleaseTransactionBuilder.Response.INSUFFICIENT_MONEY, result.responseCode());
        }

        @Test
        void test_BuildBatchedPegouts_WalletCouldNotAdjustDownwardsException() {
            // A user output could not be adjusted downwards to pay tx fees
            ReleaseRequestQueue.Entry testEntry1 = createTestEntry(123, Coin.MILLICOIN);
            ReleaseRequestQueue.Entry testEntry2 = createTestEntry(456, Coin.MILLICOIN);
            ReleaseRequestQueue.Entry testEntry3 = createTestEntry(789, Coin.MILLICOIN);
            List<ReleaseRequestQueue.Entry> pegoutRequests = Arrays.asList(testEntry1, testEntry2, testEntry3);

            Script scriptPubKey = federation.getP2SHScript();
            List<UTXO> utxos = new ArrayList<>();
            int numberOfUtxos = 3;
            for (int i = 0; i < numberOfUtxos; i++) {
                String seed = String.valueOf(i + 1);
                Sha256Hash transactionHash = mockUTXOHash(seed);
                UTXO utxo = UTXOBuilder.builder()
                    .withScriptPubKey(scriptPubKey)
                    .withValue(Coin.MILLICOIN)
                    .withTransactionHash(transactionHash)
                    .build();
                utxos.add(utxo);
            }

            Wallet thisWallet = BridgeUtils.getFederationSpendWallet(
                Context.getOrCreate(regtestParameters),
                federation,
                utxos,
                false,
                mock(BridgeStorageProvider.class)
            );

            ReleaseTransactionBuilder rtb = new ReleaseTransactionBuilder(
                regtestParameters,
                thisWallet,
                federation.getFormatVersion(),
                federation.getAddress(),
                Coin.MILLICOIN.multiply(3),
                activations
            );

            ReleaseTransactionBuilder.BuildResult result = rtb.buildBatchedPegouts(pegoutRequests);

            assertEquals(ReleaseTransactionBuilder.Response.COULD_NOT_ADJUST_DOWNWARDS, result.responseCode());

            List<Coin> outputValues = Arrays.asList(Coin.MILLICOIN, Coin.MILLICOIN, Coin.MILLICOIN, Coin.CENT);
            List<UTXO> newUtxos = new ArrayList<>();
            for (int i = 0; i < outputValues.size(); i++) {
                String seed = String.valueOf(i + 1);
                Sha256Hash transactionHash = mockUTXOHash(seed);
                UTXO utxo = UTXOBuilder.builder()
                    .withScriptPubKey(scriptPubKey)
                    .withValue(outputValues.get(i))
                    .withTransactionHash(transactionHash)
                    .build();
                newUtxos.add(utxo);
            }

            Wallet newWallet = BridgeUtils.getFederationSpendWallet(
                Context.getOrCreate(regtestParameters),
                federation,
                newUtxos,
                false,
                mock(BridgeStorageProvider.class)
            );

            ReleaseTransactionBuilder releaseTransactionBuilder = new ReleaseTransactionBuilder(
                regtestParameters,
                newWallet,
                federation.getFormatVersion(),
                federation.getAddress(),
                Coin.MILLICOIN.multiply(3),
                activations
            );

            ReleaseTransactionBuilder.BuildResult newResult = releaseTransactionBuilder.buildBatchedPegouts(pegoutRequests);

            assertEquals(ReleaseTransactionBuilder.Response.SUCCESS, newResult.responseCode());
        }

        @Test
        void test_BuildBatchedPegouts_WalletExceededMaxTransactionSizeException() {
            List<ReleaseRequestQueue.Entry> pegoutRequests = createTestEntries(600);
            int numberOfUtxos = 600;
            List<UTXO> utxos = UTXOBuilder.builder()
                .withScriptPubKey(federation.getP2SHScript())
                .buildMany(numberOfUtxos, i -> createHash(i + 1));

            Wallet thisWallet = BridgeUtils.getFederationSpendWallet(
                Context.getOrCreate(regtestParameters),
                federation,
                utxos,
                false,
                mock(BridgeStorageProvider.class)
            );

            ReleaseTransactionBuilder rtb = new ReleaseTransactionBuilder(
                regtestParameters,
                thisWallet,
                federation.getFormatVersion(),
                federation.getAddress(),
                Coin.MILLICOIN,
                activations
            );

            ReleaseTransactionBuilder.BuildResult result = rtb.buildBatchedPegouts(pegoutRequests);

            assertEquals(ReleaseTransactionBuilder.Response.EXCEED_MAX_TRANSACTION_SIZE, result.responseCode());
        }

        @Test
        void test_BuildBatchedPegouts_UtxoProviderException() throws InsufficientMoneyException, UTXOProviderException {
            ReleaseRequestQueue.Entry testEntry1 = createTestEntry(123, 2);
            ReleaseRequestQueue.Entry testEntry2 = createTestEntry(456, 4);
            ReleaseRequestQueue.Entry testEntry3 = createTestEntry(789, 5);
            List<ReleaseRequestQueue.Entry> pegoutRequests = Arrays.asList(testEntry1, testEntry2, testEntry3);

            UTXO utxo2 = UTXOBuilder.builder()
                .withTransactionHash(mockUTXOHash("two"))
                .withOutpointIndex(2)
                .withValue(Coin.FIFTY_COINS)
                .build();

            UTXO utxo3 = UTXOBuilder.builder()
                .withTransactionHash(mockUTXOHash("three"))
                .withOutpointIndex(0)
                .withValue(Coin.CENT.times(3))
                .build();

            UTXOProvider utxoProvider = mock(UTXOProvider.class);
            when(wallet.getUTXOProvider()).thenReturn(utxoProvider);
            when(wallet.getWatchedAddresses()).thenReturn(Collections.singletonList(changeAddress));
            when(utxoProvider.getOpenTransactionOutputs(any(List.class))).then((InvocationOnMock m) -> {
                List<Address> addresses = m.<List>getArgument(0);
                assertEquals(Collections.singletonList(changeAddress), addresses);
                throw new UTXOProviderException();
            });

            Mockito.doAnswer((InvocationOnMock m) -> {
                SendRequest sr = m.getArgument(0);

                BtcTransaction tx = sr.tx;

                tx.addInput(utxo2.getHash(), utxo2.getIndex(), mock(Script.class));
                tx.addInput(utxo3.getHash(), utxo3.getIndex(), mock(Script.class));

                return null;
            }).when(wallet).completeTx(any(SendRequest.class));

            ReleaseTransactionBuilder.BuildResult result = builder.buildBatchedPegouts(pegoutRequests);

            assertEquals(ReleaseTransactionBuilder.Response.UTXO_PROVIDER_EXCEPTION, result.responseCode());
            verify(wallet, times(1)).completeTx(any(SendRequest.class));
        }


        @Test
        void test_verifyTXFeeIsSpentEquallyForBatchedPegouts_two_pegouts() {
            Script outputScript = federation.getP2SHScript();
            List<UTXO> utxos = new ArrayList<>();
            int numberOfUtxos = 2;
            for (int i = 0; i < numberOfUtxos; i++) {
                String seed = String.valueOf(i + 1);
                Sha256Hash transactionHash = mockUTXOHash(seed);
                UTXO utxo = UTXOBuilder.builder()
                    .withScriptPubKey(outputScript)
                    .withTransactionHash(transactionHash)
                    .build();
                utxos.add(utxo);
            }

            Wallet thisWallet = BridgeUtils.getFederationSpendWallet(
                Context.getOrCreate(regtestParameters),
                federation,
                utxos,
                false,
                mock(BridgeStorageProvider.class)
            );

            ReleaseTransactionBuilder rtb = new ReleaseTransactionBuilder(
                regtestParameters,
                thisWallet,
                federation.getFormatVersion(),
                federation.getAddress(),
                Coin.SATOSHI.multiply(1000),
                activations
            );

            ReleaseRequestQueue.Entry testEntry1 = createTestEntry(123, 3);
            ReleaseRequestQueue.Entry testEntry2 = createTestEntry(456, 4);
            List<ReleaseRequestQueue.Entry> entries = Arrays.asList(testEntry1, testEntry2);

            ReleaseTransactionBuilder.BuildResult result = rtb.buildBatchedPegouts(entries);

            assertEquals(ReleaseTransactionBuilder.Response.SUCCESS, result.responseCode());

            BtcTransaction btcTx = result.btcTx();

            int outputSize = btcTx.getOutputs().size();
            Coin totalFee = btcTx.getFee();
            Coin feeForEachOutput = totalFee.div(outputSize - 1); // minus change output

            assertEquals(testEntry1.getAmount().minus(feeForEachOutput), btcTx.getOutput(0).getValue());
            assertEquals(testEntry2.getAmount().minus(feeForEachOutput), btcTx.getOutput(1).getValue());
            assertEquals(testEntry1.getAmount().minus(btcTx.getOutput(0).getValue())
                .add(testEntry2.getAmount().minus(btcTx.getOutput(1).getValue())), totalFee);

            Coin inputsValue = result.selectedUTXOs().stream().map(UTXO::getValue).reduce(Coin.ZERO, Coin::add);
            Coin totalPegoutAmount = entries.stream().map(ReleaseRequestQueue.Entry::getAmount).reduce(Coin.ZERO, Coin::add);

            TransactionOutput changeOutput = btcTx.getOutput(outputSize - 1); // last output

            // Last output should be the change output to the Federation
            assertEquals(federation.getAddress(), changeOutput.getAddressFromP2SH(regtestParameters));
            assertEquals(inputsValue.minus(totalPegoutAmount), changeOutput.getValue());
        }

        @Test
        void test_VerifyTXFeeIsSpentEquallyForBatchedPegouts_three_pegouts() {
            Script outputScript = federation.getP2SHScript();
            List<UTXO> utxos = new ArrayList<>();
            int numberOfUtxos = 2;
            for (int i = 0; i < numberOfUtxos; i++) {
                String seed = String.valueOf(i + 1);
                Sha256Hash transactionHash = mockUTXOHash(seed);
                UTXO utxo = UTXOBuilder.builder()
                    .withScriptPubKey(outputScript)
                    .withTransactionHash(transactionHash)
                    .build();
                utxos.add(utxo);
            }

            Wallet thisWallet = BridgeUtils.getFederationSpendWallet(
                Context.getOrCreate(regtestParameters),
                federation,
                utxos,
                false,
                mock(BridgeStorageProvider.class)
            );

            ReleaseTransactionBuilder rtb = new ReleaseTransactionBuilder(
                regtestParameters,
                thisWallet,
                federation.getFormatVersion(),
                federation.getAddress(),
                Coin.SATOSHI.multiply(1000),
                activations
            );

            ReleaseRequestQueue.Entry testEntry1 = createTestEntry(123, 3);
            ReleaseRequestQueue.Entry testEntry2 = createTestEntry(456, 4);
            ReleaseRequestQueue.Entry testEntry3 = createTestEntry(789, 5);
            List<ReleaseRequestQueue.Entry> entries = Arrays.asList(testEntry1, testEntry2, testEntry3);

            ReleaseTransactionBuilder.BuildResult result = rtb.buildBatchedPegouts(entries);

            assertEquals(ReleaseTransactionBuilder.Response.SUCCESS, result.responseCode());

            BtcTransaction btcTx = result.btcTx();

            int outputSize = btcTx.getOutputs().size();
            Coin totalFee = btcTx.getFee();
            Coin feeForEachOutput = totalFee.div(outputSize - 1); // minus change output

            // First Output Pays An Extra Satoshi Because Fee Is Even, And Outputs Is Odd
            assertEquals(testEntry1.getAmount().minus(feeForEachOutput), btcTx.getOutput(0).getValue().add(Coin.valueOf(1)));
            assertEquals(testEntry2.getAmount().minus(feeForEachOutput), btcTx.getOutput(1).getValue());
            assertEquals(testEntry3.getAmount().minus(feeForEachOutput), btcTx.getOutput(2).getValue());
            assertEquals(testEntry1.getAmount().minus(btcTx.getOutput(0).getValue())
                .add(testEntry2.getAmount().minus(btcTx.getOutput(1).getValue()))
                .add(testEntry3.getAmount().minus(btcTx.getOutput(2).getValue())), totalFee);

            Coin inputsValue = result.selectedUTXOs().stream().map(UTXO::getValue).reduce(Coin.ZERO, Coin::add);
            Coin totalPegoutAmount = entries.stream().map(ReleaseRequestQueue.Entry::getAmount).reduce(Coin.ZERO, Coin::add);

            TransactionOutput changeOutput = btcTx.getOutput(outputSize - 1); // last output

            // Last output should be the change output to the Federation
            assertEquals(federation.getAddress(), changeOutput.getAddressFromP2SH(regtestParameters));
            assertEquals(inputsValue.minus(totalPegoutAmount), changeOutput.getValue());
        }

        private void test_buildEmptyWalletTo_ok(ReleaseTransactionBuilder thisBuilder, int expectedTxVersion)
            throws InsufficientMoneyException, UTXOProviderException {
            Address to = mockAddress(123);

            List<UTXO> availableUTXOs = Arrays.asList(
                UTXOBuilder.builder()
                    .withTransactionHash(mockUTXOHash("one"))
                    .withOutpointIndex(0)
                    .withValue(Coin.COIN)
                    .build(),
                UTXOBuilder.builder()
                    .withTransactionHash(mockUTXOHash("two"))
                    .withOutpointIndex(2)
                    .withValue(Coin.FIFTY_COINS)
                    .build(),
                UTXOBuilder.builder()
                    .withTransactionHash(mockUTXOHash("two"))
                    .withOutpointIndex(0)
                    .withValue(Coin.COIN.times(7))
                    .build(),
                UTXOBuilder.builder()
                    .withTransactionHash(mockUTXOHash("three"))
                    .withOutpointIndex(0)
                    .withValue(Coin.COIN.times(3))
                    .build()
            );

            UTXOProvider utxoProvider = mock(UTXOProvider.class);
            when(wallet.getUTXOProvider()).thenReturn(utxoProvider);
            when(wallet.getWatchedAddresses()).thenReturn(Collections.singletonList(to));
            when(utxoProvider.getOpenTransactionOutputs(any(List.class))).then((InvocationOnMock m) -> {
                List<Address> addresses = m.<List>getArgument(0);
                assertEquals(Collections.singletonList(to), addresses);
                return availableUTXOs;
            });

            Mockito.doAnswer((InvocationOnMock m) -> {
                SendRequest sr = m.getArgument(0);

                assertEquals(Coin.MILLICOIN.multiply(2), sr.feePerKb);
                assertEquals(Wallet.MissingSigsMode.USE_OP_ZERO, sr.missingSigsMode);
                assertEquals(to, sr.changeAddress);
                assertFalse(sr.shuffleOutputs);
                assertTrue(sr.recipientsPayFees);
                assertTrue(sr.emptyWallet);

                BtcTransaction tx = sr.tx;

                assertEquals(1, tx.getOutputs().size());
                assertEquals(Coin.ZERO, tx.getOutput(0).getValue());
                assertEquals(to, tx.getOutput(0).getAddressFromP2PKHScript(regtestParameters));

                tx.addInput(mockUTXOHash("one"), 0, mock(Script.class));
                tx.addInput(mockUTXOHash("two"), 2, mock(Script.class));
                tx.addInput(mockUTXOHash("two"), 0, mock(Script.class));
                tx.addInput(mockUTXOHash("three"), 0, mock(Script.class));
                tx.getOutput(0).setValue(Coin.FIFTY_COINS);

                return null;
            }).when(wallet).completeTx(any(SendRequest.class));

            ReleaseTransactionBuilder.BuildResult result = thisBuilder.buildEmptyWalletTo(to);

            assertEquals(ReleaseTransactionBuilder.Response.SUCCESS, result.responseCode());

            BtcTransaction tx = result.btcTx();
            List<UTXO> selectedUTXOs = result.selectedUTXOs();

            assertEquals(1, tx.getOutputs().size());
            assertEquals(Coin.FIFTY_COINS, tx.getOutput(0).getValue());
            assertEquals(to, tx.getOutput(0).getAddressFromP2PKHScript(regtestParameters));

            assertEquals(4, tx.getInputs().size());
            assertEquals(mockUTXOHash("one"), tx.getInput(0).getOutpoint().getHash());
            assertEquals(0, tx.getInput(0).getOutpoint().getIndex());
            assertEquals(mockUTXOHash("two"), tx.getInput(1).getOutpoint().getHash());
            assertEquals(2, tx.getInput(1).getOutpoint().getIndex());
            assertEquals(mockUTXOHash("two"), tx.getInput(2).getOutpoint().getHash());
            assertEquals(0, tx.getInput(2).getOutpoint().getIndex());
            assertEquals(mockUTXOHash("three"), tx.getInput(3).getOutpoint().getHash());
            assertEquals(0, tx.getInput(3).getOutpoint().getIndex());

            assertEquals(4, selectedUTXOs.size());
            assertEquals(mockUTXOHash("one"), selectedUTXOs.get(0).getHash());
            assertEquals(0, selectedUTXOs.get(0).getIndex());
            assertEquals(mockUTXOHash("two"), selectedUTXOs.get(1).getHash());
            assertEquals(2, selectedUTXOs.get(1).getIndex());
            assertEquals(mockUTXOHash("two"), selectedUTXOs.get(2).getHash());
            assertEquals(0, selectedUTXOs.get(2).getIndex());
            assertEquals(mockUTXOHash("three"), selectedUTXOs.get(3).getHash());
            assertEquals(0, selectedUTXOs.get(3).getIndex());

            assertEquals(expectedTxVersion, tx.getVersion());
        }

        private void mockCompleteTxWithThrowForBuildToAmount(Wallet wallet, Coin expectedAmount, Address expectedAddress, Throwable t) throws InsufficientMoneyException {
            Mockito.doAnswer((InvocationOnMock m) -> {
                SendRequest sr = m.getArgument(0);

                assertEquals(Coin.MILLICOIN.multiply(2), sr.feePerKb);
                assertEquals(Wallet.MissingSigsMode.USE_OP_ZERO, sr.missingSigsMode);
                assertEquals(changeAddress, sr.changeAddress);
                assertFalse(sr.shuffleOutputs);
                assertTrue(sr.recipientsPayFees);

                BtcTransaction tx = sr.tx;

                assertEquals(1, tx.getOutputs().size());
                assertEquals(expectedAmount, tx.getOutput(0).getValue());
                assertEquals(expectedAddress, tx.getOutput(0).getAddressFromP2PKHScript(regtestParameters));

                throw t;
            }).when(wallet).completeTx(any(SendRequest.class));
        }

        private void mockCompleteTxWithThrowForEmptying(Wallet wallet, Address expectedAddress, Throwable t) throws InsufficientMoneyException {
            Mockito.doAnswer((InvocationOnMock m) -> {
                SendRequest sr = m.getArgument(0);

                assertEquals(Coin.MILLICOIN.multiply(2), sr.feePerKb);
                assertEquals(Wallet.MissingSigsMode.USE_OP_ZERO, sr.missingSigsMode);
                assertEquals(expectedAddress, sr.changeAddress);
                assertFalse(sr.shuffleOutputs);
                assertTrue(sr.recipientsPayFees);
                assertTrue(sr.emptyWallet);

                BtcTransaction tx = sr.tx;

                assertEquals(1, tx.getOutputs().size());
                assertEquals(Coin.ZERO, tx.getOutput(0).getValue());
                assertEquals(expectedAddress, tx.getOutput(0).getAddressFromP2PKHScript(regtestParameters));

                throw t;
            }).when(wallet).completeTx(any(SendRequest.class));
        }

        private Address mockAddress(int pk) {
            return BtcECKey.fromPrivate(BigInteger.valueOf(pk)).toAddress(NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        }

        private Sha256Hash mockUTXOHash(String generator) {
            return Sha256Hash.of(generator.getBytes(StandardCharsets.UTF_8));
        }

        private ReleaseRequestQueue.Entry createTestEntry(int addressPk, int amount) {
            return createTestEntry(addressPk, Coin.CENT.multiply(amount));
        }

        private ReleaseRequestQueue.Entry createTestEntry(int addressPk, Coin amount) {
            return new ReleaseRequestQueue.Entry(mockAddress(addressPk), amount);
        }

        private List<ReleaseRequestQueue.Entry> createTestEntries(int size) {
            List<ReleaseRequestQueue.Entry> pegoutRequests = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                pegoutRequests.add(createTestEntry(123, Coin.COIN));
            }
            return pegoutRequests;
        }
    }

    @Nested
    class BuildAmountToTest {

        private static final int EXPECTED_NUMBER_OF_CHANGE_OUTPUTS = 1;
        private static final ActivationConfig.ForBlock IRIS_ACTIVATIONS = ActivationConfigsForTest.iris300().forBlock(0);
        private static final ActivationConfig.ForBlock PAPYRUS_ACTIVATIONS = ActivationConfigsForTest.papyrus200().forBlock(0);

        private static final BridgeConstants BRIDGE_MAINNET_CONSTANTS = BridgeMainNetConstants.getInstance();
        private static final NetworkParameters BTC_MAINNET_PARAMS = BRIDGE_MAINNET_CONSTANTS.getBtcParams();

        private static final Coin MINIMUM_PEGOUT_TX_VALUE = BRIDGE_MAINNET_CONSTANTS.getMinimumPegoutTxValue();
        private static final Coin MINIMUM_PEGIN_TX_VALUE = BRIDGE_MAINNET_CONSTANTS.getMinimumPeginTxValue(IRIS_ACTIVATIONS);

        private static final Coin HIGH_FEE_PER_KB = Coin.valueOf(1_000_000);
        private static final Coin DUST_VALUE = MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT.minus(Coin.SATOSHI);
        private static final Address RECIPIENT_ADDRESS = createRecipientAddress();

        protected Federation federation;
        protected int federationFormatVersion;
        protected Address federationAddress;
        protected List<UTXO> federationUTXOs;
        protected Script federationOutputScript;
        protected Script federationRedeemScript;
        protected Wallet wallet;

        private ActivationConfig.ForBlock activations;
        private Coin feePerKb;

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
            assertBuildResultResponseCode(INSUFFICIENT_MONEY, buildResult);
            assertNull(buildResult.btcTx());
            assertNull(buildResult.selectedUTXOs());
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
                .withValue(MINIMUM_PEGIN_TX_VALUE)
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
            Coin amountToSend = MINIMUM_PEGOUT_TX_VALUE.add(Coin.valueOf(1000));

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
            assertBuildResultResponseCode(INSUFFICIENT_MONEY, buildResult);
            assertNull(buildResult.btcTx());
            assertNull(buildResult.selectedUTXOs());
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
            assertBuildResultResponseCode(COULD_NOT_ADJUST_DOWNWARDS, buildResult);
            assertNull(buildResult.btcTx());
            assertNull(buildResult.selectedUTXOs());
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
                .withValue(MINIMUM_PEGIN_TX_VALUE)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder();

            // Act
            BuildResult buildResult = releaseTransactionBuilder.buildAmountTo(RECIPIENT_ADDRESS, DUST_VALUE);

            // Assert
            assertBuildResultResponseCode(DUSTY_SEND_REQUESTED, buildResult);
            assertNull(buildResult.btcTx());
            assertNull(buildResult.selectedUTXOs());
        }

        @Test
        void buildAmountTo_whenEstimatedFeeIsTooHighAndUtxosAreNotEnough_shouldReturnCouldNotAdjustDownwards() {
            // Arrange
            setUpFeePerKb(HIGH_FEE_PER_KB);
            int numberOfUtxos = 3;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(MINIMUM_PEGIN_TX_VALUE)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder();

            // Act
            BuildResult buildResult = releaseTransactionBuilder.buildAmountTo(
                RECIPIENT_ADDRESS,
                MINIMUM_PEGOUT_TX_VALUE
            );

            // Assert
            assertBuildResultResponseCode(COULD_NOT_ADJUST_DOWNWARDS, buildResult);
            assertNull(buildResult.btcTx());
            assertNull(buildResult.selectedUTXOs());
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
            int numberOfUtxos = 277;
            federationUTXOs = UTXOBuilder.builder()
                .withScriptPubKey(federationOutputScript)
                .withValue(Coin.COIN)
                .buildMany(numberOfUtxos, i -> createHash(i + 1));
            ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder();
            Coin amountToSend = wallet.getBalance().subtract(MIN_NON_DUST_VALUE_FOR_P2SH_OUTPUT_SCRIPT);

            // Act
            BuildResult buildResult = releaseTransactionBuilder.buildAmountTo(RECIPIENT_ADDRESS, amountToSend);

            // Assert
            assertBuildResultResponseCode(EXCEED_MAX_TRANSACTION_SIZE, buildResult);
            assertNull(buildResult.btcTx());
            assertNull(buildResult.selectedUTXOs());
        }

        @Test
        void buildAmountTo_whenTxIsAlmostExceedingMaxTxSize_shouldCreatePegoutTx() {
            // Arrange
            int numberOfUtxos = 276;
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

        private void setUpActivations(ActivationConfig.ForBlock activations) {
            this.activations = activations;
        }

        private void setUpFeePerKb(Coin feePerKb) {
            this.feePerKb = feePerKb;
        }

        private static Address createRecipientAddress() {
            int keyOffset = 2100;
            BigInteger seed = BigInteger.valueOf(keyOffset);
            return BtcECKey.fromPrivate(seed).toAddress(BTC_MAINNET_PARAMS);
        }

        private void setUpWallet() {
            Repository repository = createRepository();
            BridgeStorageProvider bridgeStorageProvider = new BridgeStorageProvider(
                repository,
                BTC_MAINNET_PARAMS,
                activations
            );

            Context btcContext = new Context(BTC_MAINNET_PARAMS);
            wallet = BridgeUtils.getFederationSpendWallet(
                btcContext,
                federation,
                federationUTXOs,
                true,
                bridgeStorageProvider
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

            List<TransactionInput> pegoutInputs = pegoutTransaction.getInputs();
            assertEquals(expectedNumberOfInputs, pegoutInputs.size());
            assertReleaseTxInputsHasProperFormatAndBelongsToStandardMultisigFederation(
                pegoutTransaction,
                federationRedeemScript,
                federationUTXOs
            );
            switch (outputExpectation) {
                case NON_DUST_CHANGE ->
                    assertPegoutTxWithUserAndChangeOutputsWhenOriginalChangeIsNonDust(pegoutTransaction, requestedAmount);
                case DUST_CHANGE ->
                    assertPegoutTxWithUserAndChangeOutputsWhenOriginalChangeIsDust(pegoutTransaction, requestedAmount);
                case ONLY_USER_OUTPUTS ->
                    assertPegoutTxWithOnlyUserOutputs(pegoutTransaction, requestedAmount);
            }
            assertSelectedUtxosBelongToTheInputs(buildResult.selectedUTXOs(), pegoutInputs);
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

        private void assertPegoutTxWithUserAndChangeOutputsWhenOriginalChangeIsNonDust(
            BtcTransaction pegoutTransaction,
            Coin requestedAmount
        ) {
            assertNumberOfOutputs(pegoutTransaction);
            List<TransactionOutput> changeOutputs = getChangeOutputs(pegoutTransaction);
            assertUserAndChangeOutputsValuesWhenOriginalChangeIsNonDust(
                pegoutTransaction,
                changeOutputs,
                requestedAmount
            );
        }

        private void assertPegoutTxWithUserAndChangeOutputsWhenOriginalChangeIsDust(
            BtcTransaction pegoutTransaction,
            Coin requestedAmount
        ) {
            assertNumberOfOutputs(pegoutTransaction);
            List<TransactionOutput> changeOutputs = getChangeOutputs(pegoutTransaction);
            assertUserAndChangeOutputsValuesWhenOriginalChangeIsDust(
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

        private void assertPegoutTxWithOnlyUserOutputs(
            BtcTransaction pegoutTransaction,
            Coin requestedAmount
        ) {
            int expectedNumberOfUserOutputs = 1;
            int expectedNumberOfChangeOutputs = 0;
            assertPegoutTxOutputAndChangeOutputsNumbers(pegoutTransaction, expectedNumberOfUserOutputs, expectedNumberOfChangeOutputs);

            List<TransactionOutput> pegoutTransactionOutputs = pegoutTransaction.getOutputs();
            assertDestinationAddress(pegoutTransactionOutputs, RECIPIENT_ADDRESS, BTC_MAINNET_PARAMS);

            assertReleaseTxWithOnlyUserOutputsAmounts(pegoutTransaction, requestedAmount);
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

        private static final ActivationConfig.ForBlock ALL_ACTIVATIONS = ActivationConfigsForTest.all().forBlock(0);

        private static final BridgeConstants BRIDGE_MAINNET_CONSTANTS = BridgeMainNetConstants.getInstance();
        private static final NetworkParameters BTC_MAINNET_PARAMS = BRIDGE_MAINNET_CONSTANTS.getBtcParams();
        private static final Context BTC_CONTEXT = new Context(BTC_MAINNET_PARAMS);

        private static final Coin MINIMUM_PEGIN_TX_VALUE = BRIDGE_MAINNET_CONSTANTS.getMinimumPeginTxValue(ALL_ACTIVATIONS);
        private static final Coin HIGH_FEE_PER_KB = Coin.valueOf(1_000_000);
        private static final int RECIPIENT_ADDRESS_KEY_OFFSET = 3100;
        private static final Address RECIPIENT_ADDRESS = createRecipientAddress();

        protected Federation federation;
        protected int federationFormatVersion;
        protected Address federationAddress;
        protected List<UTXO> federationUTXOs;
        protected Script federationOutputScript;
        protected Script federationRedeemScript;
        protected Wallet wallet;

        private ActivationConfig.ForBlock activations;
        private Coin feePerKb;

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
                int numberOfUtxos = 10;
                federationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(MINIMUM_PEGIN_TX_VALUE)
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
                assertBuildResultResponseCode(COULD_NOT_ADJUST_DOWNWARDS, emptyWalletResult);
                assertNull(emptyWalletResult.btcTx());
                assertNull(emptyWalletResult.selectedUTXOs());
            }

            @Test
            void buildEmptyWalletTo_whenRSKIP201IsNotActive_shouldCreateRefundTxWithBtcVersion1() {
                // Arrange
                setUpActivations(ActivationConfigsForTest.papyrus200().forBlock(0));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

                // Act
                BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

                // Assert
                assertBuildResultResponseCode(SUCCESS, emptyWalletResult);
                BtcTransaction refundTransaction = emptyWalletResult.btcTx();
                assertBtcTxVersionIs1(refundTransaction);

                List<TransactionInput> refundTransactionInputs = refundTransaction.getInputs();
                assertEquals(federationUTXOs.size(), refundTransactionInputs.size());
                assertReleaseTxInputsHasProperFormatAndBelongsToStandardMultisigFederation(
                    refundTransaction,
                    federationRedeemScript,
                    federationUTXOs
                );
                assertRefundTxHasOnlyPegoutOutput(refundTransaction);
                assertSelectedUtxosBelongToTheInputs(emptyWalletResult.selectedUTXOs(), refundTransactionInputs);
            }

            @Test
            void buildEmptyWalletTo_whenSingleUtxoInWallet_shouldCreateRefundTxSpendingSingleUtxo() {
                // Arrange
                federationUTXOs = List.of(
                    UTXOBuilder.builder()
                        .withScriptPubKey(federationOutputScript)
                        .withValue(MINIMUM_PEGIN_TX_VALUE)
                        .build()
                );
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

                // Act
                BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

                // Assert
                assertBuildResultResponseCode(SUCCESS, emptyWalletResult);
                BtcTransaction refundTransaction = emptyWalletResult.btcTx();
                assertBtcTxVersionIs2(refundTransaction);

                List<TransactionInput> refundTransactionInputs = refundTransaction.getInputs();
                assertEquals(federationUTXOs.size(), refundTransactionInputs.size());
                assertReleaseTxInputsHasProperFormatAndBelongsToStandardMultisigFederation(
                    refundTransaction,
                    federationRedeemScript,
                    federationUTXOs
                );
                assertRefundTxHasOnlyPegoutOutput(refundTransaction);
                assertSelectedUtxosBelongToTheInputs(emptyWalletResult.selectedUTXOs(), refundTransactionInputs);
            }

            @Test
            void buildEmptyWalletTo_whenMultipleUtxosInWallet_shouldCreateRefundTxSpendingAllUtxos() {
                // Arrange
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

                // Act
                BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

                // Assert
                assertBuildResultResponseCode(SUCCESS, emptyWalletResult);
                BtcTransaction refundTransaction = emptyWalletResult.btcTx();
                assertBtcTxVersionIs2(refundTransaction);

                List<TransactionInput> refundTransactionInputs = refundTransaction.getInputs();
                assertEquals(federationUTXOs.size(), refundTransactionInputs.size());
                assertReleaseTxInputsHasProperFormatAndBelongsToStandardMultisigFederation(
                    refundTransaction,
                    federationRedeemScript,
                    federationUTXOs
                );
                assertRefundTxHasOnlyPegoutOutput(refundTransaction);
                assertSelectedUtxosBelongToTheInputs(emptyWalletResult.selectedUTXOs(), refundTransactionInputs);
            }

            @Test
            void buildEmptyWalletTo_whenTxExceedsMaxTxSize_shouldReturnExceedMaxTransactionSize() {
                // Arrange
                int numberOfUtxos = 277;
                federationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(Coin.COIN)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

                // Act
                BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

                // Assert
                assertBuildResultResponseCode(EXCEED_MAX_TRANSACTION_SIZE, emptyWalletResult);
                assertNull(emptyWalletResult.btcTx());
                assertNull(emptyWalletResult.selectedUTXOs());
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
                assertBuildResultResponseCode(SUCCESS, emptyWalletResult);
                BtcTransaction refundTransaction = emptyWalletResult.btcTx();
                assertBtcTxVersionIs2(refundTransaction);
                List<TransactionInput> refundTransactionInputs = refundTransaction.getInputs();
                assertEquals(federationUTXOs.size(), refundTransactionInputs.size());
                assertReleaseTxInputsHasProperFormatAndBelongsToStandardMultisigFederation(
                    refundTransaction,
                    federationRedeemScript,
                    federationUTXOs
                );
                assertRefundTxHasOnlyPegoutOutput(refundTransaction);
                assertSelectedUtxosBelongToTheInputs(emptyWalletResult.selectedUTXOs(), refundTransactionInputs);
            }

            @Test
            void buildEmptyWalletTo_whenEstimatedFeeIsHighAndUtxosAreNotEnough_shouldReturnCouldNotAdjustDownwards() {
                // Arrange
                setUpFeePerKb(HIGH_FEE_PER_KB);
                int numberOfUtxos = 3;
                federationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(MINIMUM_PEGIN_TX_VALUE)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

                // Act
                BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

                // Assert
                assertBuildResultResponseCode(COULD_NOT_ADJUST_DOWNWARDS, emptyWalletResult);
                assertNull(emptyWalletResult.btcTx());
                assertNull(emptyWalletResult.selectedUTXOs());
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
                    .withValue(MINIMUM_PEGIN_TX_VALUE)
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
                assertBuildResultResponseCode(COULD_NOT_ADJUST_DOWNWARDS, emptyWalletResult);
                assertNull(emptyWalletResult.btcTx());
                assertNull(emptyWalletResult.selectedUTXOs());
            }

            @Test
            void buildEmptyWalletTo_whenSingleUtxoInWallet_shouldCreateRefundTxSpendingSingleUtxo() {
                // Arrange
                federationUTXOs = List.of(
                    UTXOBuilder.builder()
                        .withScriptPubKey(federationOutputScript)
                        .withValue(MINIMUM_PEGIN_TX_VALUE)
                        .build()
                );
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

                // Act
                BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

                // Assert
                assertBuildResultResponseCode(SUCCESS, emptyWalletResult);
                BtcTransaction refundTransaction = emptyWalletResult.btcTx();
                assertBtcTxVersionIs2(refundTransaction);

                List<TransactionInput> refundTransactionInputs = refundTransaction.getInputs();
                assertEquals(federationUTXOs.size(), refundTransactionInputs.size());
                assertReleaseTxInputsHasProperFormatAndBelongsToP2shErpFederation(
                    refundTransaction,
                    federationRedeemScript,
                    federationUTXOs
                );
                assertRefundTxHasOnlyPegoutOutput(refundTransaction);
                assertSelectedUtxosBelongToTheInputs(emptyWalletResult.selectedUTXOs(), refundTransactionInputs);
            }

            @Test
            void buildEmptyWalletTo_whenMultipleUtxosInWallet_shouldCreateRefundTxSpendingAllUtxos() {
                // Arrange
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

                // Act
                BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

                // Assert
                assertBuildResultResponseCode(SUCCESS, emptyWalletResult);
                BtcTransaction refundTransaction = emptyWalletResult.btcTx();
                assertBtcTxVersionIs2(refundTransaction);

                List<TransactionInput> refundTransactionInputs = refundTransaction.getInputs();
                assertEquals(federationUTXOs.size(), refundTransactionInputs.size());
                assertReleaseTxInputsHasProperFormatAndBelongsToP2shErpFederation(
                    refundTransaction,
                    federationRedeemScript,
                    federationUTXOs
                );
                assertRefundTxHasOnlyPegoutOutput(refundTransaction);
                assertSelectedUtxosBelongToTheInputs(emptyWalletResult.selectedUTXOs(), refundTransactionInputs);
            }

            @Test
            void buildEmptyWalletTo_whenTxExceedsMaxTxSize_shouldReturnExceedMaxTransactionSize() {
                // Arrange
                int numberOfUtxos = 196;
                federationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(Coin.COIN)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

                // Act
                BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

                // Assert
                assertBuildResultResponseCode(EXCEED_MAX_TRANSACTION_SIZE, emptyWalletResult);
                assertNull(emptyWalletResult.btcTx());
                assertNull(emptyWalletResult.selectedUTXOs());
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
                assertBuildResultResponseCode(SUCCESS, emptyWalletResult);
                BtcTransaction refundTransaction = emptyWalletResult.btcTx();
                assertBtcTxVersionIs2(refundTransaction);
                List<TransactionInput> refundTransactionInputs = refundTransaction.getInputs();
                assertEquals(federationUTXOs.size(), refundTransactionInputs.size());
                assertReleaseTxInputsHasProperFormatAndBelongsToP2shErpFederation(
                    refundTransaction,
                    federationRedeemScript,
                    federationUTXOs
                );
                assertRefundTxHasOnlyPegoutOutput(refundTransaction);
                assertSelectedUtxosBelongToTheInputs(emptyWalletResult.selectedUTXOs(), refundTransactionInputs);
            }

            @Test
            void buildEmptyWalletTo_whenEstimatedFeeIsHighAndUtxosAreNotEnough_shouldReturnCouldNotAdjustDownwards() {
                // Arrange
                setUpFeePerKb(HIGH_FEE_PER_KB);
                int numberOfUtxos = 3;
                federationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(MINIMUM_PEGIN_TX_VALUE)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

                // Act
                BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

                // Assert
                assertBuildResultResponseCode(COULD_NOT_ADJUST_DOWNWARDS, emptyWalletResult);
                assertNull(emptyWalletResult.btcTx());
                assertNull(emptyWalletResult.selectedUTXOs());
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
                    .withValue(MINIMUM_PEGIN_TX_VALUE)
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
                assertBuildResultResponseCode(COULD_NOT_ADJUST_DOWNWARDS, emptyWalletResult);
                assertNull(emptyWalletResult.btcTx());
                assertNull(emptyWalletResult.selectedUTXOs());
            }

            @Test
            void buildEmptyWalletTo_whenSingleUtxoInWallet_shouldCreateRefundTxSpendingSingleUtxo() {
                // Arrange
                federationUTXOs = List.of(
                    UTXOBuilder.builder()
                        .withScriptPubKey(federationOutputScript)
                        .withValue(MINIMUM_PEGIN_TX_VALUE)
                        .build()
                );
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

                // Act
                BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

                // Assert
                assertBuildResultResponseCode(SUCCESS, emptyWalletResult);
                BtcTransaction refundTransaction = emptyWalletResult.btcTx();
                assertBtcTxVersionIs2(refundTransaction);

                List<TransactionInput> refundTransactionInputs = refundTransaction.getInputs();
                assertEquals(federationUTXOs.size(), refundTransactionInputs.size());
                assertReleaseTxInputsHasProperFormatAndBelongsToP2shP2wshErpFederation(
                    refundTransaction,
                    federationRedeemScript,
                    federationUTXOs
                );
                assertRefundTxHasOnlyPegoutOutput(refundTransaction);
                assertSelectedUtxosBelongToTheInputs(emptyWalletResult.selectedUTXOs(), refundTransactionInputs);
            }

            @Test
            void buildEmptyWalletTo_whenMultipleUtxosInWallet_shouldCreateRefundTxSpendingAllUtxos() {
                // Arrange
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

                // Act
                BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

                // Assert
                assertBuildResultResponseCode(SUCCESS, emptyWalletResult);
                BtcTransaction refundTransaction = emptyWalletResult.btcTx();
                assertBtcTxVersionIs2(refundTransaction);

                List<TransactionInput> refundTransactionInputs = refundTransaction.getInputs();
                assertEquals(federationUTXOs.size(), refundTransactionInputs.size());
                assertReleaseTxInputsHasProperFormatAndBelongsToP2shP2wshErpFederation(
                    refundTransaction,
                    federationRedeemScript,
                    federationUTXOs
                );
                assertRefundTxHasOnlyPegoutOutput(refundTransaction);
                assertSelectedUtxosBelongToTheInputs(emptyWalletResult.selectedUTXOs(), refundTransactionInputs);
            }

            @Test
            void buildEmptyWalletTo_whenTxExceedsMaxTxSize_shouldReturnExceedMaxTransactionSize() {
                // Arrange
                int numberOfUtxos = 2438;
                federationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(Coin.COIN)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

                // Act
                BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

                // Assert
                assertBuildResultResponseCode(EXCEED_MAX_TRANSACTION_SIZE, emptyWalletResult);
                assertNull(emptyWalletResult.btcTx());
                assertNull(emptyWalletResult.selectedUTXOs());
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
                assertBuildResultResponseCode(SUCCESS, emptyWalletResult);
                BtcTransaction refundTransaction = emptyWalletResult.btcTx();
                assertBtcTxVersionIs2(refundTransaction);
                List<TransactionInput> refundTransactionInputs = refundTransaction.getInputs();
                assertEquals(federationUTXOs.size(), refundTransactionInputs.size());
                assertReleaseTxInputsHasProperFormatAndBelongsToP2shP2wshErpFederation(
                    refundTransaction,
                    federationRedeemScript,
                    federationUTXOs
                );
                assertRefundTxHasOnlyPegoutOutput(refundTransaction);
                assertSelectedUtxosBelongToTheInputs(emptyWalletResult.selectedUTXOs(), refundTransactionInputs);
            }

            @Test
            void buildEmptyWalletTo_whenEstimatedFeeIsHighAndUtxosAreNotEnough_shouldReturnCouldNotAdjustDownwards() {
                // Arrange
                setUpFeePerKb(HIGH_FEE_PER_KB);
                int numberOfUtxos = 3;
                federationUTXOs = UTXOBuilder.builder()
                    .withScriptPubKey(federationOutputScript)
                    .withValue(MINIMUM_PEGIN_TX_VALUE)
                    .buildMany(numberOfUtxos, i -> createHash(i + 1));
                ReleaseTransactionBuilder releaseTransactionBuilder = setupWalletAndCreateReleaseTransactionBuilder(federationUTXOs);

                // Act
                BuildResult emptyWalletResult = releaseTransactionBuilder.buildEmptyWalletTo(RECIPIENT_ADDRESS);

                // Assert
                assertBuildResultResponseCode(COULD_NOT_ADJUST_DOWNWARDS, emptyWalletResult);
                assertNull(emptyWalletResult.btcTx());
                assertNull(emptyWalletResult.selectedUTXOs());
            }
        }

        private void setUpActivations(ActivationConfig.ForBlock activations) {
            this.activations = activations;
        }

        private void setUpFeePerKb(Coin feePerKb) {
            this.feePerKb = feePerKb;
        }

        private static Address createRecipientAddress() {
            BigInteger seed = BigInteger.valueOf(RECIPIENT_ADDRESS_KEY_OFFSET);
            return BtcECKey.fromPrivate(seed).toAddress(BTC_MAINNET_PARAMS);
        }

        private void setUpWallet(List<UTXO> utxos) {
            Repository repository = createRepository();
            BridgeStorageProvider bridgeStorageProvider = new BridgeStorageProvider(
                repository,
                BTC_MAINNET_PARAMS,
                activations
            );

            wallet = BridgeUtils.getFederationSpendWallet(
                BTC_CONTEXT,
                federation,
                utxos,
                true,
                bridgeStorageProvider
            );
        }

        private ReleaseTransactionBuilder setupWalletAndCreateReleaseTransactionBuilder(List<UTXO> utxos) {
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

            assertReleaseTxWithOnlyUserOutputsAmounts(refundTransaction, refundTransaction.getInputSum());
        }

        private boolean isFederationOutput(TransactionOutput output) {
            Address destinationAddress = output.getScriptPubKey().getToAddress(BTC_MAINNET_PARAMS);
            return destinationAddress.equals(federationAddress);
        }
    }
}
