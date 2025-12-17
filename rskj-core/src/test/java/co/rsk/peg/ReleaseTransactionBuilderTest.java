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
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

class ReleaseTransactionBuilderTest {
    private final ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(0);
    private final BridgeConstants bridgeMainNetConstants = BridgeMainNetConstants.getInstance();
    private final NetworkParameters btcMainNetParams = bridgeMainNetConstants.getBtcParams();
    private final NetworkParameters regtestParameters =  new BridgeRegTestConstants().getBtcParams();
    private final Address changeAddress = BitcoinTestUtils.createP2PKHAddress(regtestParameters, "changeAddress");
    private final Coin feePerKb = Coin.MILLICOIN.multiply(2);
    private final Federation activeP2shErpFederation = P2shErpFederationBuilder.builder().build();
    private final Script activeFederationP2SHScript = activeP2shErpFederation.getP2SHScript();
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

        Script p2SHScript = activeFederation.getP2SHScript();
        List<UTXO> utxos = getUtxos(p2SHScript);

        Wallet thisWallet = BridgeUtils.getFederationSpendWallet(
            new Context(btcMainNetParams),
            activeFederation,
            utxos,
            false,
            bridgeStorageProviderMock
        );

        ReleaseTransactionBuilder rtb = new ReleaseTransactionBuilder(
            btcMainNetParams,
            thisWallet,
            federation.getFormatVersion(),
            activeFederation.getAddress(),
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
        assertEquals(activeFederation.getAddress(), changeOutput.getAddressFromP2SH(btcMainNetParams));
        // And if its value is the spent UTXOs summatory minus the requested pegout amount
        // we can ensure the Federation is not paying fees for pegouts
        assertEquals(inputsValue.minus(pegoutAmount), changeOutput.getValue());
    }

    @Test
    void buildSvpFundTransaction_withAFederationWithEnoughUTXOsForTheSvpFundTransaction_shouldReturnACorrectSvpFundTx() {
        // Arrange
        List<UTXO> utxos = getUtxos(activeFederationP2SHScript);
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
        List<UTXO> utxos = getUtxos(activeFederationP2SHScript);
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
        List<UTXO> utxos = getUtxos(activeFederationP2SHScript);
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
            activeP2shErpFederation.getAddress(),
            feePerKb,
            activations
        );
    }

    @Test
    void buildSvpFundTransaction_withInvalidProposedFlyoverPrefix_shouldThrowRedeemScriptCreationException() {
        // Arrange
        List<UTXO> utxos = getUtxos(activeFederationP2SHScript);
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
        List<UTXO> utxos = getUtxos(activeFederationP2SHScript);
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
        List<UTXO> utxos = getUtxos(activeFederationP2SHScript);
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
        List<UTXO> utxos = getUtxos(activeFederationP2SHScript);
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
            activeP2shErpFederation.getAddress(),
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
        List<UTXO> utxos = getUtxos(activeFederationP2SHScript);
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
            activeP2shErpFederation.getAddress(),
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
            activeP2shErpFederation.getAddress(),
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
        List<UTXO> utxos = getUtxos(activeFederationP2SHScript);
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
        List<UTXO> utxos = getUtxos(activeFederationP2SHScript);
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
            activeP2shErpFederation.getAddress(),
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

        Script p2SHScript = nonStandardErpFederation.getP2SHScript();
        List<UTXO> utxos = getUtxos(p2SHScript);

        Wallet thisWallet = BridgeUtils.getFederationSpendWallet(
            new Context(btcMainNetParams),
            nonStandardErpFederation,
            utxos,
            false,
            mock(BridgeStorageProvider.class)
        );

        ReleaseTransactionBuilder releaseTransactionBuilder = new ReleaseTransactionBuilder(
            btcMainNetParams,
            thisWallet,
            nonStandardErpFederation.getFormatVersion(),
            nonStandardErpFederation.getAddress(),
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

    private static List<UTXO> getUtxos(Script outputScript) {
        int numberOfUtxos = 2;
        Coin value = Coin.COIN;
        ArrayList<UTXO> utxos = new ArrayList<>(numberOfUtxos);
        for (int i = 0; i < numberOfUtxos; i++) {
            UTXO utxo = new UTXO(
                BitcoinTestUtils.createHash(i+1),
                0,
                value,
                0,
                false,
                outputScript
            );
            utxos.add(utxo);
        }
        return utxos;
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
            mockUTXO("one", 0, Coin.COIN),
            mockUTXO("one", 1, Coin.COIN.multiply(2)),
            mockUTXO("two", 1, Coin.COIN.divide(2)),
            mockUTXO("two", 2, Coin.FIFTY_COINS),
            mockUTXO("two", 0, Coin.MILLICOIN.times(7)),
            mockUTXO("three", 0, Coin.CENT.times(3))
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

        List<UTXO> utxos = Arrays.asList(
            new UTXO(mockUTXOHash("1"), 0, Coin.COIN, 0, false, federation.getP2SHScript()),
            new UTXO(mockUTXOHash("2"), 2, Coin.FIFTY_COINS, 0, false, federation.getP2SHScript()),
            new UTXO(mockUTXOHash("3"), 0, Coin.CENT.times(3), 0, false, federation.getP2SHScript())
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

        List<UTXO> utxos = Arrays.asList(
            new UTXO(mockUTXOHash("1"), 0, Coin.COIN, 0, false, federation.getP2SHScript()),
            new UTXO(mockUTXOHash("2"), 2, Coin.FIFTY_COINS, 0, false, federation.getP2SHScript()),
            new UTXO(mockUTXOHash("3"), 0, Coin.CENT.times(3), 0, false, federation.getP2SHScript())
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

        List<UTXO> utxos = Arrays.asList(
            new UTXO(mockUTXOHash("1"), 0, Coin.COIN, 0, false, federation.getP2SHScript()),
            new UTXO(mockUTXOHash("2"), 0, Coin.COIN, 0, false, federation.getP2SHScript())
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

        assertEquals(ReleaseTransactionBuilder.Response.INSUFFICIENT_MONEY, result.responseCode());
    }

    @Test
    void test_BuildBatchedPegouts_WalletCouldNotAdjustDownwardsException() {
        // A user output could not be adjusted downwards to pay tx fees
        ReleaseRequestQueue.Entry testEntry1 = createTestEntry(123, Coin.MILLICOIN);
        ReleaseRequestQueue.Entry testEntry2 = createTestEntry(456, Coin.MILLICOIN);
        ReleaseRequestQueue.Entry testEntry3 = createTestEntry(789, Coin.MILLICOIN);
        List<ReleaseRequestQueue.Entry> pegoutRequests = Arrays.asList(testEntry1, testEntry2, testEntry3);

        List<UTXO> utxos = Arrays.asList(
            new UTXO(mockUTXOHash("1"), 0, Coin.MILLICOIN, 0, false, federation.getP2SHScript()),
            new UTXO(mockUTXOHash("2"), 0, Coin.MILLICOIN, 0, false, federation.getP2SHScript()),
            new UTXO(mockUTXOHash("3"), 0, Coin.MILLICOIN, 0, false, federation.getP2SHScript())
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
            Coin.MILLICOIN.multiply(3),
            activations
        );

        ReleaseTransactionBuilder.BuildResult result = rtb.buildBatchedPegouts(pegoutRequests);

        assertEquals(ReleaseTransactionBuilder.Response.COULD_NOT_ADJUST_DOWNWARDS, result.responseCode());

        List<UTXO> newUtxos = Arrays.asList(
            new UTXO(mockUTXOHash("1"), 0, Coin.MILLICOIN, 0, false, federation.getP2SHScript()),
            new UTXO(mockUTXOHash("2"), 0, Coin.MILLICOIN, 0, false, federation.getP2SHScript()),
            new UTXO(mockUTXOHash("3"), 0, Coin.MILLICOIN, 0, false, federation.getP2SHScript()),
            new UTXO(mockUTXOHash("4"), 0, Coin.CENT, 0, false, federation.getP2SHScript())
        );

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

        List<UTXO> utxos = PegTestUtils.createUTXOs(600, federation.getAddress());

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

        UTXO utxo2 = mockUTXO("two", 2, Coin.FIFTY_COINS);
        UTXO utxo3 = mockUTXO("three", 0, Coin.CENT.times(3));

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
        List<UTXO> utxos = Arrays.asList(
            new UTXO(mockUTXOHash("1"), 0, Coin.COIN, 0, false, federation.getP2SHScript()),
            new UTXO(mockUTXOHash("2"), 0, Coin.COIN, 0, false, federation.getP2SHScript())
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
        List<UTXO> utxos = Arrays.asList(
            new UTXO(mockUTXOHash("1"), 0, Coin.COIN, 0, false, federation.getP2SHScript()),
            new UTXO(mockUTXOHash("2"), 0, Coin.COIN, 0, false, federation.getP2SHScript())
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
            mockUTXO("one", 0, Coin.COIN),
            mockUTXO("two", 2, Coin.FIFTY_COINS),
            mockUTXO("two", 0, Coin.COIN.times(7)),
            mockUTXO("three", 0, Coin.CENT.times(3))
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

    private UTXO mockUTXO(String generator, long index, Coin value) {
        return new UTXO(
            mockUTXOHash(generator),
            index,
            value,
            10,
            false,
            null
        );
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
