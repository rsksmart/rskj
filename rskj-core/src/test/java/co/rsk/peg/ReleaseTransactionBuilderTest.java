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
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

class ReleaseTransactionBuilderTest {
    private Wallet wallet;

    private ReleaseTransactionBuilder builder;
    private ActivationConfig.ForBlock activations;
    private Federation federation;
    private final Address changeAddress =  mockAddress(1000);;
    private final NetworkParameters regtestParameters =  new BridgeRegTestConstants().getBtcParams();
    private final Coin feePerKb = Coin.MILLICOIN.multiply(2);
    private final BridgeConstants bridgeMainNetConstants = BridgeMainNetConstants.getInstance();
    private final NetworkParameters btcMainNetParams = bridgeMainNetConstants.getBtcParams();
    private final Federation activeP2shErpFederation = P2shErpFederationBuilder.builder().build();
    private final Script p2SHScript = activeP2shErpFederation.getP2SHScript();

    @BeforeEach
    void setup() {
        wallet = mock(Wallet.class);
        activations = mock(ActivationConfig.ForBlock.class);
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
        List<FederationMember> members = FederationMember.getFederationMembersFromKeys(
            Arrays.asList(
                new BtcECKey(),
                new BtcECKey(),
                new BtcECKey()
            )
        );
        FederationArgs federationArgs = new FederationArgs(
            members,
            Instant.now(),
            0,
            regtestParameters
        );
        Federation standardMultisigFederation = FederationFactory.buildStandardMultiSigFederation(
            federationArgs
        );

        Script p2SHScript = standardMultisigFederation.getP2SHScript();
        List<UTXO> utxos = getUtxos(p2SHScript, 2, Coin.COIN);

        Wallet thisWallet = BridgeUtils.getFederationSpendWallet(
            new Context(regtestParameters),
            standardMultisigFederation,
            utxos,
            false,
            mock(BridgeStorageProvider.class)
        );

        ReleaseTransactionBuilder rtb = new ReleaseTransactionBuilder(
            regtestParameters,
            thisWallet,
            federation.getFormatVersion(),
            standardMultisigFederation.getAddress(),
            Coin.SATOSHI.multiply(1000),
            activations
        );

        Address pegoutRecipient = mockAddress(123);
        Coin pegoutAmount = Coin.COIN.add(Coin.SATOSHI);

        ReleaseTransactionBuilder.BuildResult result = rtb.buildAmountTo(
            pegoutRecipient,
            pegoutAmount
        );
        assertEquals(ReleaseTransactionBuilder.Response.SUCCESS, result.getResponseCode());

        Coin inputsValue = result.getSelectedUTXOs().stream().map(UTXO::getValue).reduce(Coin.ZERO, Coin::add);

        TransactionOutput changeOutput = result.getBtcTx().getOutput(1);

        // Second output should be the change output to the Federation
        assertEquals(standardMultisigFederation.getAddress(), changeOutput.getAddressFromP2SH(regtestParameters));
        // And if its value is the spent UTXOs summatory minus the requested pegout amount
        // we can ensure the Federation is not paying fees for pegouts
        assertEquals(inputsValue.minus(pegoutAmount), changeOutput.getValue());
    }

    @Test
    void buildSvpFundTransaction_withAFederationWithEnoughUTXOsForTheSvpFundTransaction_shouldReturnACorrectReleaseTx() {
        Federation activeFederation = P2shErpFederationBuilder.builder().build();
        ActivationConfig.ForBlock thisActivations = ActivationConfigsForTest.all().forBlock(0);

        Script p2SHScript = activeFederation.getP2SHScript();
        List<UTXO> utxos = getUtxos(p2SHScript, 2, Coin.COIN);
        Wallet thisWallet = BridgeUtils.getFederationSpendWallet(
            new Context(bridgeMainNetConstants.getBtcParams()),
            activeFederation,
            utxos,
            false,
            mock(BridgeStorageProvider.class)
        );

        ReleaseTransactionBuilder releaseTransactionBuilder = new ReleaseTransactionBuilder(
            btcMainNetParams,
            thisWallet,
            activeFederation.getFormatVersion(),
            activeFederation.getAddress(),
            feePerKb,
            thisActivations
        );

        Federation proposedFederation = P2shP2wshErpFederationBuilder.builder().build();
        Coin svpFundTxOutputsValue = bridgeMainNetConstants.getSvpFundTxOutputsValue();
        Keccak256 proposedFlyoverPrefix = bridgeMainNetConstants.getProposedFederationFlyoverPrefix();
        ReleaseTransactionBuilder.BuildResult svpFundTransactionUnsignedBuildResult = releaseTransactionBuilder.buildSvpFundTransaction(
            proposedFederation,
            proposedFlyoverPrefix,
            svpFundTxOutputsValue
        );

        ReleaseTransactionBuilder.Response expectedResponseCode = ReleaseTransactionBuilder.Response.SUCCESS;
        ReleaseTransactionBuilder.Response actualResponseCode = svpFundTransactionUnsignedBuildResult.getResponseCode();
        assertEquals(expectedResponseCode, actualResponseCode);

        BtcTransaction svpFundTransactionUnsigned = svpFundTransactionUnsignedBuildResult.getBtcTx();
        int numberOfOutputs = 3; // 1 for the federation, 1 for the flyover federation, and 1 for the change
        assertEquals(numberOfOutputs, svpFundTransactionUnsigned.getOutputs().size());
        TransactionOutput actualFirstOutput = svpFundTransactionUnsigned.getOutput(0);
        assertEquals(svpFundTxOutputsValue, actualFirstOutput.getValue());
        assertEquals(proposedFederation.getAddress(), actualFirstOutput.getAddressFromP2SH(btcMainNetParams));

        TransactionOutput actualSecondOutput = svpFundTransactionUnsigned.getOutput(1);
        assertEquals(svpFundTxOutputsValue, actualSecondOutput.getValue());

        Address flyoverFederationAddress = PegUtils.getFlyoverFederationAddress(btcMainNetParams, proposedFlyoverPrefix, proposedFederation);
        assertEquals(flyoverFederationAddress, actualSecondOutput.getAddressFromP2SH(btcMainNetParams));

        List<UTXO> selectedUTXOs = svpFundTransactionUnsignedBuildResult.getSelectedUTXOs();
        assertEquals(utxos, selectedUTXOs);
    }

    @Test
    void buildSvpFundTransaction_withAFederationWithoutUTXOs_shouldThrowInsufficientMoneyResponseCode() {
        Federation activeFederation = P2shErpFederationBuilder.builder().build();

        List<UTXO> emptyUtxos = Collections.emptyList();
        Wallet thisWallet = BridgeUtils.getFederationSpendWallet(
            new Context(bridgeMainNetConstants.getBtcParams()),
            activeFederation,
            emptyUtxos,
            false,
            mock(BridgeStorageProvider.class)
        );

        ActivationConfig.ForBlock thisActivations = ActivationConfigsForTest.all().forBlock(0);
        ReleaseTransactionBuilder releaseTransactionBuilder = new ReleaseTransactionBuilder(
            btcMainNetParams,
            thisWallet,
            activeFederation.getFormatVersion(),
            activeFederation.getAddress(),
            feePerKb,
            thisActivations
        );

        Federation proposedFederation = P2shP2wshErpFederationBuilder.builder().build();
        Coin svpFundTxOutputsValue = bridgeMainNetConstants.getSvpFundTxOutputsValue();
        Keccak256 proposedFlyoverPrefix = bridgeMainNetConstants.getProposedFederationFlyoverPrefix();
        ReleaseTransactionBuilder.BuildResult buildResult = releaseTransactionBuilder.buildSvpFundTransaction(
            proposedFederation,
            proposedFlyoverPrefix,
            svpFundTxOutputsValue
        );

        ReleaseTransactionBuilder.Response expectedResponseCode = ReleaseTransactionBuilder.Response.INSUFFICIENT_MONEY;
        ReleaseTransactionBuilder.Response actualResponseCode = buildResult.getResponseCode();
        assertEquals(expectedResponseCode, actualResponseCode);

        List<UTXO> selectedUTXOs = buildResult.getSelectedUTXOs();
        assertNull(selectedUTXOs);

        BtcTransaction btcTx = buildResult.getBtcTx();
        assertNull(btcTx);
    }

    @Test
    void buildSvpFundTransaction_withDustValueAsSvpFundTxOutputsValue_shouldReturnDustySendRequestResponseCode() {
        Federation activeFederation = P2shErpFederationBuilder.builder().build();
        Script p2SHScript = activeFederation.getP2SHScript();

        Coin value = Coin.COIN.add(Coin.SATOSHI);
        List<UTXO> utxos = getUtxos(p2SHScript, 2, value);

        Wallet thisWallet = BridgeUtils.getFederationSpendWallet(
            new Context(bridgeMainNetConstants.getBtcParams()),
            activeFederation,
            utxos,
            false,
            mock(BridgeStorageProvider.class)
        );

        ActivationConfig.ForBlock thisActivations = ActivationConfigsForTest.all().forBlock(0);
        ReleaseTransactionBuilder releaseTransactionBuilder = new ReleaseTransactionBuilder(
            btcMainNetParams,
            thisWallet,
            activeFederation.getFormatVersion(),
            activeFederation.getAddress(),
            feePerKb,
            thisActivations
        );

        Federation proposedFederation = P2shP2wshErpFederationBuilder.builder().build();
        Keccak256 proposedFlyoverPrefix = bridgeMainNetConstants.getProposedFederationFlyoverPrefix();
        Coin svpFundTxOutputsValue = Coin.SATOSHI;
        ReleaseTransactionBuilder.BuildResult buildResult = releaseTransactionBuilder.buildSvpFundTransaction(
            proposedFederation,
            proposedFlyoverPrefix,
            svpFundTxOutputsValue
        );

        ReleaseTransactionBuilder.Response expectedResponseCode = ReleaseTransactionBuilder.Response.DUSTY_SEND_REQUESTED;
        ReleaseTransactionBuilder.Response actualResponseCode = buildResult.getResponseCode();
        assertEquals(expectedResponseCode, actualResponseCode);

        List<UTXO> selectedUTXOs = buildResult.getSelectedUTXOs();
        assertNull(selectedUTXOs);

        BtcTransaction btcTx = buildResult.getBtcTx();
        assertNull(btcTx);
    }

    @Test
    void buildSvpFundTransaction_withNullProposedFlyoverPrefix_shouldThrowRedeemScriptCreationException() {
        Federation activeFederation = P2shErpFederationBuilder.builder().build();
        Script p2SHScript = activeFederation.getP2SHScript();

        Coin value = Coin.COIN.add(Coin.SATOSHI);
        List<UTXO> utxos = getUtxos(p2SHScript, 2, value);

        Wallet thisWallet = BridgeUtils.getFederationSpendWallet(
            new Context(bridgeMainNetConstants.getBtcParams()),
            activeFederation,
            utxos,
            false,
            mock(BridgeStorageProvider.class)
        );

        ActivationConfig.ForBlock thisActivations = ActivationConfigsForTest.all().forBlock(0);
        ReleaseTransactionBuilder releaseTransactionBuilder = new ReleaseTransactionBuilder(
            btcMainNetParams,
            thisWallet,
            activeFederation.getFormatVersion(),
            activeFederation.getAddress(),
            feePerKb,
            thisActivations
        );

        Federation proposedFederation = P2shP2wshErpFederationBuilder.builder().build();
        Coin svpFundTxOutputsValue = Coin.SATOSHI;
        assertThrows(RedeemScriptCreationException.class, () -> releaseTransactionBuilder.buildSvpFundTransaction(
            proposedFederation,
            null,
            svpFundTxOutputsValue
        ));
    }

    @Test
    void buildSvpFundTransaction_withInvalidProposedFlyoverPrefix_shouldThrowRedeemScriptCreationException() {
        Federation activeFederation = P2shErpFederationBuilder.builder().build();
        Script p2SHScript = activeFederation.getP2SHScript();

        Coin value = Coin.COIN.add(Coin.SATOSHI);
        List<UTXO> utxos = getUtxos(p2SHScript, 2, value);

        Wallet thisWallet = BridgeUtils.getFederationSpendWallet(
            new Context(bridgeMainNetConstants.getBtcParams()),
            activeFederation,
            utxos,
            false,
            mock(BridgeStorageProvider.class)
        );

        ActivationConfig.ForBlock thisActivations = ActivationConfigsForTest.all().forBlock(0);
        ReleaseTransactionBuilder releaseTransactionBuilder = new ReleaseTransactionBuilder(
            btcMainNetParams,
            thisWallet,
            activeFederation.getFormatVersion(),
            activeFederation.getAddress(),
            feePerKb,
            thisActivations
        );

        Federation proposedFederation = P2shP2wshErpFederationBuilder.builder().build();
        Keccak256 proposedFlyoverPrefix = Keccak256.ZERO_HASH;
        Coin svpFundTxOutputsValue = Coin.SATOSHI;
        assertThrows(RedeemScriptCreationException.class, () -> releaseTransactionBuilder.buildSvpFundTransaction(
            proposedFederation,
            proposedFlyoverPrefix,
            svpFundTxOutputsValue
        ));
    }

    @Test
    void buildSvpFundTransaction_withNullProposedFederation_shouldReturnNullPointerException() {
        Federation activeFederation = P2shErpFederationBuilder.builder().build();
        Script p2SHScript = activeFederation.getP2SHScript();

        Coin value = Coin.COIN.add(Coin.SATOSHI);
        List<UTXO> utxos = getUtxos(p2SHScript, 2, value);

        Wallet thisWallet = BridgeUtils.getFederationSpendWallet(
            new Context(bridgeMainNetConstants.getBtcParams()),
            activeFederation,
            utxos,
            false,
            mock(BridgeStorageProvider.class)
        );

        ActivationConfig.ForBlock thisActivations = ActivationConfigsForTest.all().forBlock(0);
        ReleaseTransactionBuilder releaseTransactionBuilder = new ReleaseTransactionBuilder(
            btcMainNetParams,
            thisWallet,
            activeFederation.getFormatVersion(),
            activeFederation.getAddress(),
            feePerKb,
            thisActivations
        );

        Keccak256 proposedFlyoverPrefix = bridgeMainNetConstants.getProposedFederationFlyoverPrefix();
        Coin svpFundTxOutputsValue = Coin.SATOSHI;
        assertThrows(NullPointerException.class, () -> releaseTransactionBuilder.buildSvpFundTransaction(
            null,
            proposedFlyoverPrefix,
            svpFundTxOutputsValue
        ));
    }

    @Test
    void buildSvpFundTransaction_withNullAsValueTransferred_shouldXXX() {
        Coin value = Coin.COIN.add(Coin.SATOSHI);
        List<UTXO> utxos = getUtxos(p2SHScript, 2, value);

        Wallet thisWallet = BridgeUtils.getFederationSpendWallet(
            new Context(bridgeMainNetConstants.getBtcParams()),
            activeP2shErpFederation,
            utxos,
            false,
            mock(BridgeStorageProvider.class)
        );

        ActivationConfig.ForBlock thisActivations = ActivationConfigsForTest.all().forBlock(0);
        ReleaseTransactionBuilder releaseTransactionBuilder = new ReleaseTransactionBuilder(
            btcMainNetParams,
            thisWallet,
            activeP2shErpFederation.getFormatVersion(),
            activeP2shErpFederation.getAddress(),
            feePerKb,
            thisActivations
        );

        Keccak256 proposedFlyoverPrefix = bridgeMainNetConstants.getProposedFederationFlyoverPrefix();
        Federation proposedFederation = P2shP2wshErpFederationBuilder.builder().build();
        assertThrows(NullPointerException.class, () -> releaseTransactionBuilder.buildSvpFundTransaction(
            proposedFederation,
            proposedFlyoverPrefix,
            null
        ));
    }

    @Test
    void build_pegout_tx_from_non_standard_erp_federation() {
        ActivationConfig.ForBlock activationsForBlock = mock(ActivationConfig.ForBlock.class);
        when(activationsForBlock.isActive(ConsensusRule.RSKIP201)).thenReturn(true);
        when(activationsForBlock.isActive(ConsensusRule.RSKIP284)).thenReturn(true);

        // Use mainnet constants to test a real situation
        BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();

        List<FederationMember> members = FederationMember.getFederationMembersFromKeys(
            Arrays.asList(
                new BtcECKey(),
                new BtcECKey(),
                new BtcECKey()
            )
        );
        FederationArgs federationArgs = new FederationArgs(members, Instant.now(), 0, bridgeConstants.getBtcParams());

        ErpFederation nonStandardErpFederation = FederationFactory.buildNonStandardErpFederation(federationArgs,
            bridgeConstants.getFederationConstants().getErpFedPubKeysList(),
            bridgeConstants.getFederationConstants().getErpFedActivationDelay(), activationsForBlock);

        Script p2SHScript = nonStandardErpFederation.getP2SHScript();
        List<UTXO> utxos = getUtxos(p2SHScript, 2, Coin.COIN);

        Wallet thisWallet = BridgeUtils.getFederationSpendWallet(
            new Context(bridgeConstants.getBtcParams()),
            nonStandardErpFederation,
            utxos,
            false,
            mock(BridgeStorageProvider.class)
        );

        ReleaseTransactionBuilder releaseTransactionBuilder = new ReleaseTransactionBuilder(
            bridgeConstants.getBtcParams(),
            thisWallet,
            nonStandardErpFederation.getFormatVersion(),
            nonStandardErpFederation.getAddress(),
            Coin.SATOSHI.multiply(1000),
            activationsForBlock
        );

        Address pegoutRecipient = mockAddress(123);
        Coin pegoutAmount = Coin.COIN.add(Coin.SATOSHI);

        ReleaseTransactionBuilder.BuildResult result = releaseTransactionBuilder.buildAmountTo(
            pegoutRecipient,
            pegoutAmount
        );
        assertEquals(ReleaseTransactionBuilder.Response.SUCCESS, result.getResponseCode());
    }

    private static List<UTXO> getUtxos(Script outputScript, int numberOfUtxos, Coin value) {
        ArrayList<UTXO> utxos = new ArrayList<>(numberOfUtxos);
        for (int i = 0; i < numberOfUtxos; i++) {
            UTXO utxo = new UTXO(
                Sha256Hash.of(new byte[]{1}),
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

        assertEquals(ReleaseTransactionBuilder.Response.SUCCESS, result.getResponseCode());

        BtcTransaction tx = result.getBtcTx();
        List<UTXO> selectedUTXOs = result.getSelectedUTXOs();

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

        assertEquals(ReleaseTransactionBuilder.Response.INSUFFICIENT_MONEY, result.getResponseCode());
        verify(wallet, never()).getWatchedAddresses();
        verify(wallet, never()).getUTXOProvider();
    }

    @Test
    void buildAmountTo_walletCouldNotAdjustDownwards() throws InsufficientMoneyException {
        Address to = mockAddress(123);
        Coin amount = Coin.CENT.multiply(3);

        mockCompleteTxWithThrowForBuildToAmount(wallet, amount, to, new Wallet.CouldNotAdjustDownwards());

        ReleaseTransactionBuilder.BuildResult result = builder.buildAmountTo(to, amount);

        assertEquals(ReleaseTransactionBuilder.Response.COULD_NOT_ADJUST_DOWNWARDS, result.getResponseCode());
        verify(wallet, never()).getWatchedAddresses();
        verify(wallet, never()).getUTXOProvider();
    }

    @Test
    void buildAmountTo_walletExceededMaxTransactionSize() throws InsufficientMoneyException {
        Address to = mockAddress(123);
        Coin amount = Coin.CENT.multiply(3);

        mockCompleteTxWithThrowForBuildToAmount(wallet, amount, to, new Wallet.ExceededMaxTransactionSize());

        ReleaseTransactionBuilder.BuildResult result = builder.buildAmountTo(to, amount);

        assertEquals(ReleaseTransactionBuilder.Response.EXCEED_MAX_TRANSACTION_SIZE, result.getResponseCode());
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

        assertEquals(ReleaseTransactionBuilder.Response.UTXO_PROVIDER_EXCEPTION, result.getResponseCode());
    }

    @Test
    void buildAmountTo_illegalStateException() throws InsufficientMoneyException {
        Address to = mockAddress(123);
        Coin amount = Coin.CENT.multiply(3);

        doThrow(new IllegalStateException()).when(wallet).completeTx(any(SendRequest.class));

        ReleaseTransactionBuilder.BuildResult result = null;
        try {
            result = builder.buildAmountTo(to, amount);
        } catch (Exception e) {
            assertInstanceOf(IllegalStateException.class, e);
        }
        verify(wallet, times(1)).completeTx(any(SendRequest.class));
        Assertions.assertNull(result);
    }

    @Test
    void buildEmptyWalletTo_ok_before_RSKIP_199_activation() throws
        InsufficientMoneyException, UTXOProviderException {
        test_buildEmptyWalletTo_ok(false, 1);
    }

    @Test
    void buildEmptyWalletTo_ok_after_RSKIP_199_activation()
        throws InsufficientMoneyException, UTXOProviderException {
        test_buildEmptyWalletTo_ok(true, 2);
    }

    @Test
    void buildEmptyWalletTo_insufficientMoneyException() throws InsufficientMoneyException {
        Address to = mockAddress(123);

        mockCompleteTxWithThrowForEmptying(wallet, to, new InsufficientMoneyException(Coin.valueOf(1234)));

        ReleaseTransactionBuilder.BuildResult result = builder.buildEmptyWalletTo(to);

        assertEquals(ReleaseTransactionBuilder.Response.INSUFFICIENT_MONEY, result.getResponseCode());
        verify(wallet, never()).getWatchedAddresses();
        verify(wallet, never()).getUTXOProvider();
    }

    @Test
    void buildEmptyWalletTo_walletCouldNotAdjustDownwards() throws InsufficientMoneyException {
        Address to = mockAddress(123);

        mockCompleteTxWithThrowForEmptying(wallet, to, new Wallet.CouldNotAdjustDownwards());

        ReleaseTransactionBuilder.BuildResult result = builder.buildEmptyWalletTo(to);

        assertEquals(ReleaseTransactionBuilder.Response.COULD_NOT_ADJUST_DOWNWARDS, result.getResponseCode());
        verify(wallet, never()).getWatchedAddresses();
        verify(wallet, never()).getUTXOProvider();
    }

    @Test
    void buildEmptyWalletTo_walletExceededMaxTransactionSize() throws InsufficientMoneyException {
        Address to = mockAddress(123);

        mockCompleteTxWithThrowForEmptying(wallet, to, new Wallet.ExceededMaxTransactionSize());

        ReleaseTransactionBuilder.BuildResult result = builder.buildEmptyWalletTo(to);

        assertEquals(ReleaseTransactionBuilder.Response.EXCEED_MAX_TRANSACTION_SIZE, result.getResponseCode());
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

        assertEquals(ReleaseTransactionBuilder.Response.UTXO_PROVIDER_EXCEPTION, result.getResponseCode());
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

        assertEquals(ReleaseTransactionBuilder.Response.SUCCESS, result.getResponseCode());

        BtcTransaction tx = result.getBtcTx();
        List<UTXO> selectedUTXOs = result.getSelectedUTXOs();

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

        assertEquals(ReleaseTransactionBuilder.Response.SUCCESS, result.getResponseCode());

        BtcTransaction tx = result.getBtcTx();
        List<UTXO> selectedUTXOs = result.getSelectedUTXOs();

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

        assertEquals(ReleaseTransactionBuilder.Response.INSUFFICIENT_MONEY, result.getResponseCode());
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

        assertEquals(ReleaseTransactionBuilder.Response.COULD_NOT_ADJUST_DOWNWARDS, result.getResponseCode());

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

        assertEquals(ReleaseTransactionBuilder.Response.SUCCESS, newResult.getResponseCode());
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

        assertEquals(ReleaseTransactionBuilder.Response.EXCEED_MAX_TRANSACTION_SIZE, result.getResponseCode());
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
            SendRequest sr = m.<SendRequest>getArgument(0);

            BtcTransaction tx = sr.tx;

            tx.addInput(utxo2.getHash(), utxo2.getIndex(), mock(Script.class));
            tx.addInput(utxo3.getHash(), utxo3.getIndex(), mock(Script.class));

            return null;
        }).when(wallet).completeTx(any(SendRequest.class));

        ReleaseTransactionBuilder.BuildResult result = builder.buildBatchedPegouts(pegoutRequests);

        assertEquals(ReleaseTransactionBuilder.Response.UTXO_PROVIDER_EXCEPTION, result.getResponseCode());
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

        assertEquals(ReleaseTransactionBuilder.Response.SUCCESS, result.getResponseCode());

        BtcTransaction btcTx = result.getBtcTx();

        int outputSize = btcTx.getOutputs().size();
        Coin totalFee = btcTx.getFee();
        Coin feeForEachOutput = totalFee.div(outputSize - 1); // minus change output

        assertEquals(testEntry1.getAmount().minus(feeForEachOutput), btcTx.getOutput(0).getValue());
        assertEquals(testEntry2.getAmount().minus(feeForEachOutput), btcTx.getOutput(1).getValue());
        assertEquals(testEntry1.getAmount().minus(btcTx.getOutput(0).getValue())
            .add(testEntry2.getAmount().minus(btcTx.getOutput(1).getValue())), totalFee);

        Coin inputsValue = result.getSelectedUTXOs().stream().map(UTXO::getValue).reduce(Coin.ZERO, Coin::add);
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

        assertEquals(ReleaseTransactionBuilder.Response.SUCCESS, result.getResponseCode());

        BtcTransaction btcTx = result.getBtcTx();

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

        Coin inputsValue = result.getSelectedUTXOs().stream().map(UTXO::getValue).reduce(Coin.ZERO, Coin::add);
        Coin totalPegoutAmount = entries.stream().map(ReleaseRequestQueue.Entry::getAmount).reduce(Coin.ZERO, Coin::add);

        TransactionOutput changeOutput = btcTx.getOutput(outputSize - 1); // last output

        // Last output should be the change output to the Federation
        assertEquals(federation.getAddress(), changeOutput.getAddressFromP2SH(regtestParameters));
        assertEquals(inputsValue.minus(totalPegoutAmount), changeOutput.getValue());
    }

    private void test_buildEmptyWalletTo_ok(boolean isRSKIPActive, int expectedTxVersion)
        throws InsufficientMoneyException, UTXOProviderException {
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(isRSKIPActive);
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

        ReleaseTransactionBuilder.BuildResult result = builder.buildEmptyWalletTo(to);

        assertEquals(ReleaseTransactionBuilder.Response.SUCCESS, result.getResponseCode());

        BtcTransaction tx = result.getBtcTx();
        List<UTXO> selectedUTXOs = result.getSelectedUTXOs();

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
