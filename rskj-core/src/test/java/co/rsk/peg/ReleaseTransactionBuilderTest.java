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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import co.rsk.bitcoinj.core.TransactionOutput;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.bitcoinj.core.UTXOProvider;
import co.rsk.bitcoinj.core.UTXOProviderException;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.wallet.SendRequest;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

class ReleaseTransactionBuilderTest {
    private Wallet wallet;
    private Address changeAddress;
    private ReleaseTransactionBuilder builder;
    private ActivationConfig.ForBlock activations;
    private Context btcContext;
    private NetworkParameters networkParameters;
    private BridgeConstants bridgeConstants;
    private Federation federation;

    @BeforeEach
    void setup() {
        wallet = mock(Wallet.class);
        changeAddress = mockAddress(1000);
        activations = mock(ActivationConfig.ForBlock.class);
        bridgeConstants = BridgeRegTestConstants.getInstance();
        networkParameters = bridgeConstants.getBtcParams();
        btcContext = new Context(networkParameters);
        federation = bridgeConstants.getGenesisFederation();
        builder = new ReleaseTransactionBuilder(
            networkParameters,
            wallet,
            changeAddress,
            Coin.MILLICOIN.multiply(2),
            activations
        );
    }

    @Test
    void first_output_pay_fees() {
        Federation federation = new StandardMultisigFederation(
            FederationMember.getFederationMembersFromKeys(Arrays.asList(
                new BtcECKey(),
                new BtcECKey(),
                new BtcECKey())
            ),
            Instant.now(),
            0,
            networkParameters
        );

        List<UTXO> utxos = Arrays.asList(
            new UTXO(
                Sha256Hash.of(new byte[]{1}),
                0,
                Coin.COIN,
                0,
                false,
                federation.getP2SHScript()
            ),
            new UTXO(
                Sha256Hash.of(new byte[]{1}),
                0,
                Coin.COIN,
                0,
                false,
                federation.getP2SHScript()
            )
        );

        Wallet thisWallet = BridgeUtils.getFederationSpendWallet(
            new Context(networkParameters),
            federation,
            utxos,
            false,
            mock(BridgeStorageProvider.class)
        );

        ReleaseTransactionBuilder rtb = new ReleaseTransactionBuilder(
            networkParameters,
            thisWallet,
            federation.getAddress(),
            Coin.SATOSHI.multiply(1000),
            activations
        );

        Address pegoutRecipient = mockAddress(123);
        Coin pegoutAmount = Coin.COIN.add(Coin.SATOSHI);

        ReleaseTransactionBuilder.BuildResult result = rtb.buildAmountTo(
            pegoutRecipient,
            pegoutAmount
        );
        Assertions.assertEquals(ReleaseTransactionBuilder.Response.SUCCESS, result.getResponseCode());

        Coin inputsValue = result.getSelectedUTXOs().stream().map(UTXO::getValue).reduce(Coin.ZERO, Coin::add);

        TransactionOutput changeOutput = result.getBtcTx().getOutput(1);

        // Second output should be the change output to the Federation
        Assertions.assertEquals(federation.getAddress(), changeOutput.getAddressFromP2SH(networkParameters));
        // And if its value is the spent UTXOs summatory minus the requested pegout amount
        // we can ensure the Federation is not paying fees for pegouts
        Assertions.assertEquals(inputsValue.minus(pegoutAmount), changeOutput.getValue());
    }

    @Test
    void build_pegout_tx_from_erp_federation() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);

        // Use mainnet constants to test a real situation
        BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();

        Federation erpFederation = new ErpFederation(
            FederationMember.getFederationMembersFromKeys(Arrays.asList(
                new BtcECKey(),
                new BtcECKey(),
                new BtcECKey())
            ),
            Instant.now(),
            0,
            bridgeConstants.getBtcParams(),
            bridgeConstants.getErpFedPubKeysList(),
            bridgeConstants.getErpFedActivationDelay(),
            activations,
            new NonStandardErpRedeemScriptBuilder()
        );

        List<UTXO> utxos = Arrays.asList(
            new UTXO(
                Sha256Hash.of(new byte[]{1}),
                0,
                Coin.COIN,
                0,
                false,
                erpFederation.getP2SHScript()
            ),
            new UTXO(
                Sha256Hash.of(new byte[]{1}),
                0,
                Coin.COIN,
                0,
                false,
                erpFederation.getP2SHScript()
            )
        );

        Wallet thisWallet = BridgeUtils.getFederationSpendWallet(
            new Context(bridgeConstants.getBtcParams()),
            erpFederation,
            utxos,
            false,
            mock(BridgeStorageProvider.class)
        );

        ReleaseTransactionBuilder releaseTransactionBuilder = new ReleaseTransactionBuilder(
            bridgeConstants.getBtcParams(),
            thisWallet,
            erpFederation.address,
            Coin.SATOSHI.multiply(1000),
            activations
        );

        Address pegoutRecipient = mockAddress(123);
        Coin pegoutAmount = Coin.COIN.add(Coin.SATOSHI);

        ReleaseTransactionBuilder.BuildResult result = releaseTransactionBuilder.buildAmountTo(
            pegoutRecipient,
            pegoutAmount
        );
        Assertions.assertEquals(ReleaseTransactionBuilder.Response.SUCCESS, result.getResponseCode());
    }

    @Test
    void getters() {
        Assertions.assertSame(wallet, builder.getWallet());
        Assertions.assertSame(changeAddress, builder.getChangeAddress());
        Assertions.assertEquals(Coin.MILLICOIN.multiply(2), builder.getFeePerKb());
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
        when(wallet.getWatchedAddresses()).thenReturn(Arrays.asList(changeAddress));
        when(utxoProvider.getOpenTransactionOutputs(any(List.class))).then((InvocationOnMock m) -> {
            List<Address> addresses = m.<List>getArgument(0);
            Assertions.assertEquals(Arrays.asList(changeAddress), addresses);
            return availableUTXOs;
        });

        Mockito.doAnswer((InvocationOnMock m) -> {
            SendRequest sr = m.<SendRequest>getArgument(0);

            Assertions.assertEquals(Coin.MILLICOIN.multiply(2), sr.feePerKb);
            Assertions.assertEquals(Wallet.MissingSigsMode.USE_OP_ZERO, sr.missingSigsMode);
            Assertions.assertEquals(changeAddress, sr.changeAddress);
            Assertions.assertFalse(sr.shuffleOutputs);
            Assertions.assertTrue(sr.recipientsPayFees);

            BtcTransaction tx = sr.tx;

            Assertions.assertEquals(1, tx.getOutputs().size());
            Assertions.assertEquals(amount, tx.getOutput(0).getValue());
            Assertions.assertEquals(to, tx.getOutput(0).getAddressFromP2PKHScript(NetworkParameters.fromID(NetworkParameters.ID_REGTEST)));

            tx.addInput(mockUTXOHash("two"), 2, mock(Script.class));
            tx.addInput(mockUTXOHash("three"), 0, mock(Script.class));

            return null;
        }).when(wallet).completeTx(any(SendRequest.class));

        ReleaseTransactionBuilder.BuildResult result = builder.buildAmountTo(to, amount);

        Assertions.assertEquals(ReleaseTransactionBuilder.Response.SUCCESS, result.getResponseCode());

        BtcTransaction tx = result.getBtcTx();
        List<UTXO> selectedUTXOs = result.getSelectedUTXOs();

        Assertions.assertEquals(1, tx.getOutputs().size());
        Assertions.assertEquals(amount, tx.getOutput(0).getValue());
        Assertions.assertEquals(to, tx.getOutput(0).getAddressFromP2PKHScript(NetworkParameters.fromID(NetworkParameters.ID_REGTEST)));

        Assertions.assertEquals(2, tx.getInputs().size());
        Assertions.assertEquals(mockUTXOHash("two"), tx.getInput(0).getOutpoint().getHash());
        Assertions.assertEquals(2, tx.getInput(0).getOutpoint().getIndex());
        Assertions.assertEquals(mockUTXOHash("three"), tx.getInput(1).getOutpoint().getHash());
        Assertions.assertEquals(0, tx.getInput(1).getOutpoint().getIndex());

        Assertions.assertEquals(2, selectedUTXOs.size());
        Assertions.assertEquals(mockUTXOHash("two"), selectedUTXOs.get(0).getHash());
        Assertions.assertEquals(2, selectedUTXOs.get(0).getIndex());
        Assertions.assertEquals(mockUTXOHash("three"), selectedUTXOs.get(1).getHash());
        Assertions.assertEquals(0, selectedUTXOs.get(1).getIndex());
    }

    @Test
    void buildAmountTo_insufficientMoneyException() throws InsufficientMoneyException {
        Address to = mockAddress(123);
        Coin amount = Coin.CENT.multiply(3);

        mockCompleteTxWithThrowForBuildToAmount(wallet, amount, to, new InsufficientMoneyException(Coin.valueOf(1234)));

        ReleaseTransactionBuilder.BuildResult result = builder.buildAmountTo(to, amount);

        Assertions.assertEquals(ReleaseTransactionBuilder.Response.INSUFFICIENT_MONEY, result.getResponseCode());
        verify(wallet, never()).getWatchedAddresses();
        verify(wallet, never()).getUTXOProvider();
    }

    @Test
    void buildAmountTo_walletCouldNotAdjustDownwards() throws InsufficientMoneyException {
        Address to = mockAddress(123);
        Coin amount = Coin.CENT.multiply(3);

        mockCompleteTxWithThrowForBuildToAmount(wallet, amount, to, new Wallet.CouldNotAdjustDownwards());

        ReleaseTransactionBuilder.BuildResult result = builder.buildAmountTo(to, amount);

        Assertions.assertEquals(ReleaseTransactionBuilder.Response.COULD_NOT_ADJUST_DOWNWARDS, result.getResponseCode());
        verify(wallet, never()).getWatchedAddresses();
        verify(wallet, never()).getUTXOProvider();
    }

    @Test
    void buildAmountTo_walletExceededMaxTransactionSize() throws InsufficientMoneyException {
        Address to = mockAddress(123);
        Coin amount = Coin.CENT.multiply(3);

        mockCompleteTxWithThrowForBuildToAmount(wallet, amount, to, new Wallet.ExceededMaxTransactionSize());

        ReleaseTransactionBuilder.BuildResult result = builder.buildAmountTo(to, amount);

        Assertions.assertEquals(ReleaseTransactionBuilder.Response.EXCEED_MAX_TRANSACTION_SIZE, result.getResponseCode());
        verify(wallet, never()).getWatchedAddresses();
        verify(wallet, never()).getUTXOProvider();
    }

    @Test
    void buildAmountTo_utxoProviderException() throws InsufficientMoneyException, UTXOProviderException {
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
        when(wallet.getWatchedAddresses()).thenReturn(Arrays.asList(changeAddress));
        when(utxoProvider.getOpenTransactionOutputs(any(List.class))).then((InvocationOnMock m) -> {
            List<Address> addresses = m.<List>getArgument(0);
            Assertions.assertEquals(Arrays.asList(changeAddress), addresses);
            throw new UTXOProviderException();
        });

        Mockito.doAnswer((InvocationOnMock m) -> {
            SendRequest sr = m.<SendRequest>getArgument(0);

            Assertions.assertEquals(Coin.MILLICOIN.multiply(2), sr.feePerKb);
            Assertions.assertEquals(Wallet.MissingSigsMode.USE_OP_ZERO, sr.missingSigsMode);
            Assertions.assertEquals(changeAddress, sr.changeAddress);
            Assertions.assertFalse(sr.shuffleOutputs);
            Assertions.assertTrue(sr.recipientsPayFees);

            BtcTransaction tx = sr.tx;

            Assertions.assertEquals(1, tx.getOutputs().size());
            Assertions.assertEquals(amount, tx.getOutput(0).getValue());
            Assertions.assertEquals(to, tx.getOutput(0).getAddressFromP2PKHScript(NetworkParameters.fromID(NetworkParameters.ID_REGTEST)));

            tx.addInput(mockUTXOHash("two"), 2, mock(Script.class));
            tx.addInput(mockUTXOHash("three"), 0, mock(Script.class));

            return null;
        }).when(wallet).completeTx(any(SendRequest.class));

        ReleaseTransactionBuilder.BuildResult result = builder.buildAmountTo(to, amount);
        verify(wallet, times(1)).completeTx(any(SendRequest.class));

        Assertions.assertEquals(ReleaseTransactionBuilder.Response.UTXO_PROVIDER_EXCEPTION, result.getResponseCode());
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
            Assertions.assertTrue(e instanceof IllegalStateException);
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

        Assertions.assertEquals(ReleaseTransactionBuilder.Response.INSUFFICIENT_MONEY, result.getResponseCode());
        verify(wallet, never()).getWatchedAddresses();
        verify(wallet, never()).getUTXOProvider();
    }

    @Test
    void buildEmptyWalletTo_walletCouldNotAdjustDownwards() throws InsufficientMoneyException {
        Address to = mockAddress(123);

        mockCompleteTxWithThrowForEmptying(wallet, to, new Wallet.CouldNotAdjustDownwards());

        ReleaseTransactionBuilder.BuildResult result = builder.buildEmptyWalletTo(to);

        Assertions.assertEquals(ReleaseTransactionBuilder.Response.COULD_NOT_ADJUST_DOWNWARDS, result.getResponseCode());
        verify(wallet, never()).getWatchedAddresses();
        verify(wallet, never()).getUTXOProvider();
    }

    @Test
    void buildEmptyWalletTo_walletExceededMaxTransactionSize() throws InsufficientMoneyException {
        Address to = mockAddress(123);

        mockCompleteTxWithThrowForEmptying(wallet, to, new Wallet.ExceededMaxTransactionSize());

        ReleaseTransactionBuilder.BuildResult result = builder.buildEmptyWalletTo(to);

        Assertions.assertEquals(ReleaseTransactionBuilder.Response.EXCEED_MAX_TRANSACTION_SIZE, result.getResponseCode());
        verify(wallet, never()).getWatchedAddresses();
        verify(wallet, never()).getUTXOProvider();
    }

    @Test
    void buildEmptyWalletTo_utxoProviderException() throws InsufficientMoneyException, UTXOProviderException {
        Address to = mockAddress(123);

        List<UTXO> availableUTXOs = Arrays.asList(
            mockUTXO("two", 2, Coin.FIFTY_COINS),
            mockUTXO("three", 0, Coin.CENT.times(3))
        );

        UTXOProvider utxoProvider = mock(UTXOProvider.class);
        when(wallet.getUTXOProvider()).thenReturn(utxoProvider);
        when(wallet.getWatchedAddresses()).thenReturn(Arrays.asList(to));
        when(utxoProvider.getOpenTransactionOutputs(any(List.class))).then((InvocationOnMock m) -> {
            List<Address> addresses = m.<List>getArgument(0);
            Assertions.assertEquals(Arrays.asList(to), addresses);
            throw new UTXOProviderException();
        });

        Mockito.doAnswer((InvocationOnMock m) -> {
            SendRequest sr = m.<SendRequest>getArgument(0);

            Assertions.assertEquals(Coin.MILLICOIN.multiply(2), sr.feePerKb);
            Assertions.assertEquals(Wallet.MissingSigsMode.USE_OP_ZERO, sr.missingSigsMode);
            Assertions.assertEquals(to, sr.changeAddress);
            Assertions.assertFalse(sr.shuffleOutputs);
            Assertions.assertTrue(sr.recipientsPayFees);
            Assertions.assertTrue(sr.emptyWallet);

            BtcTransaction tx = sr.tx;

            Assertions.assertEquals(1, tx.getOutputs().size());
            Assertions.assertEquals(Coin.ZERO, tx.getOutput(0).getValue());
            Assertions.assertEquals(to, tx.getOutput(0).getAddressFromP2PKHScript(NetworkParameters.fromID(NetworkParameters.ID_REGTEST)));

            tx.addInput(mockUTXOHash("two"), 2, mock(Script.class));
            tx.addInput(mockUTXOHash("three"), 0, mock(Script.class));
            tx.getOutput(0).setValue(Coin.FIFTY_COINS);

            return null;
        }).when(wallet).completeTx(any(SendRequest.class));

        ReleaseTransactionBuilder.BuildResult result = builder.buildEmptyWalletTo(to);
        verify(wallet, times(1)).completeTx(any(SendRequest.class));

        Assertions.assertEquals(ReleaseTransactionBuilder.Response.UTXO_PROVIDER_EXCEPTION, result.getResponseCode());
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
            Context.getOrCreate(networkParameters),
            federation,
            utxos,
            false,
            mock(BridgeStorageProvider.class)
        );

        ReleaseTransactionBuilder rtb = new ReleaseTransactionBuilder(
            networkParameters,
            thisWallet,
            federation.getAddress(),
            Coin.MILLICOIN,
            activations
        );

        ReleaseTransactionBuilder.BuildResult result = rtb.buildBatchedPegouts(pegoutRequests);

        Assertions.assertEquals(ReleaseTransactionBuilder.Response.SUCCESS, result.getResponseCode());

        BtcTransaction tx = result.getBtcTx();
        List<UTXO> selectedUTXOs = result.getSelectedUTXOs();

        Assertions.assertEquals(2, selectedUTXOs.size());

        Assertions.assertEquals(4, tx.getOutputs().size());

        Address firstOutputAddress = testEntry1.getDestination();
        Address secondOutputAddress = testEntry2.getDestination();
        Address thirdOutputAddress = testEntry3.getDestination();
        Assertions.assertEquals(firstOutputAddress, tx.getOutput(0).getAddressFromP2PKHScript(networkParameters));
        Assertions.assertEquals(secondOutputAddress, tx.getOutput(1).getAddressFromP2PKHScript(networkParameters));
        Assertions.assertEquals(thirdOutputAddress, tx.getOutput(2).getAddressFromP2PKHScript(networkParameters));

        Sha256Hash firstUtxoHash = utxos.get(0).getHash();
        Sha256Hash thirdUtxoHash = utxos.get(2).getHash();

        Assertions.assertEquals(2, tx.getInputs().size());
        Assertions.assertEquals(firstUtxoHash, tx.getInput(1).getOutpoint().getHash());
        Assertions.assertEquals(thirdUtxoHash, tx.getInput(0).getOutpoint().getHash());
    }

    @Test
    void test_BuildBatchedPegouts_ok_P2SHAddress() {
        ReleaseRequestQueue.Entry testEntry1 = createTestEntry(123, 2);

        List<BtcECKey> keys = BitcoinTestUtils.getBtcEcKeysFromSeeds(new String[]{"k1", "k2", "k3"}, true);
        ReleaseRequestQueue.Entry testEntry2 = new ReleaseRequestQueue.Entry(
            BitcoinTestUtils.createP2SHMultisigAddress(networkParameters, keys),
            Coin.COIN
        );
        keys = BitcoinTestUtils.getBtcEcKeysFromSeeds(new String[]{"k4", "k5", "k6"}, true);
        ReleaseRequestQueue.Entry testEntry3 = new ReleaseRequestQueue.Entry(
            BitcoinTestUtils.createP2SHMultisigAddress(networkParameters, keys),
            Coin.COIN
        );
        List<ReleaseRequestQueue.Entry> pegoutRequests = Arrays.asList(testEntry1, testEntry2, testEntry3);

        List<UTXO> utxos = Arrays.asList(
            new UTXO(mockUTXOHash("1"), 0, Coin.COIN, 0, false, federation.getP2SHScript()),
            new UTXO(mockUTXOHash("2"), 2, Coin.FIFTY_COINS, 0, false, federation.getP2SHScript()),
            new UTXO(mockUTXOHash("3"), 0, Coin.CENT.times(3), 0, false, federation.getP2SHScript())
        );

        Wallet thisWallet = BridgeUtils.getFederationSpendWallet(
            Context.getOrCreate(networkParameters),
            federation,
            utxos,
            false,
            mock(BridgeStorageProvider.class)
        );

        ReleaseTransactionBuilder rtb = new ReleaseTransactionBuilder(
            networkParameters,
            thisWallet,
            federation.getAddress(),
            Coin.MILLICOIN,
            activations
        );

        ReleaseTransactionBuilder.BuildResult result = rtb.buildBatchedPegouts(pegoutRequests);

        Assertions.assertEquals(ReleaseTransactionBuilder.Response.SUCCESS, result.getResponseCode());

        BtcTransaction tx = result.getBtcTx();
        List<UTXO> selectedUTXOs = result.getSelectedUTXOs();

        Assertions.assertEquals(3, selectedUTXOs.size());

        Assertions.assertEquals(4, tx.getOutputs().size());

        Address firstOutputAddress = testEntry1.getDestination();
        Address secondOutputAddress = testEntry2.getDestination();
        Address thirdOutputAddress = testEntry3.getDestination();
        Assertions.assertEquals(firstOutputAddress, tx.getOutput(0).getAddressFromP2PKHScript(networkParameters));
        Assertions.assertEquals(secondOutputAddress, tx.getOutput(1).getAddressFromP2SH(networkParameters));
        Assertions.assertEquals(thirdOutputAddress, tx.getOutput(2).getAddressFromP2SH(networkParameters));

        Sha256Hash firstUtxoHash = utxos.get(0).getHash();
        Sha256Hash secondUtxoHash = utxos.get(1).getHash();
        Sha256Hash thirdUtxoHash = utxos.get(2).getHash();

        Assertions.assertEquals(3, tx.getInputs().size());
        Assertions.assertEquals(firstUtxoHash, tx.getInput(1).getOutpoint().getHash());
        Assertions.assertEquals(secondUtxoHash, tx.getInput(2).getOutpoint().getHash());
        Assertions.assertEquals(thirdUtxoHash, tx.getInput(0).getOutpoint().getHash());
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
            Context.getOrCreate(networkParameters),
            federation,
            utxos,
            false,
            mock(BridgeStorageProvider.class)
        );

        ReleaseTransactionBuilder rtb = new ReleaseTransactionBuilder(
            networkParameters,
            thisWallet,
            federation.getAddress(),
            Coin.MILLICOIN,
            activations
        );

        ReleaseTransactionBuilder.BuildResult result = rtb.buildBatchedPegouts(pegoutRequests);

        Assertions.assertEquals(ReleaseTransactionBuilder.Response.INSUFFICIENT_MONEY, result.getResponseCode());
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
            Context.getOrCreate(networkParameters),
            federation,
            utxos,
            false,
            mock(BridgeStorageProvider.class)
        );

        ReleaseTransactionBuilder rtb = new ReleaseTransactionBuilder(
            networkParameters,
            thisWallet,
            federation.getAddress(),
            Coin.MILLICOIN.multiply(3),
            activations
        );

        ReleaseTransactionBuilder.BuildResult result = rtb.buildBatchedPegouts(pegoutRequests);

        Assertions.assertEquals(ReleaseTransactionBuilder.Response.COULD_NOT_ADJUST_DOWNWARDS, result.getResponseCode());

        List<UTXO> newUtxos = Arrays.asList(
            new UTXO(mockUTXOHash("1"), 0, Coin.MILLICOIN, 0, false, federation.getP2SHScript()),
            new UTXO(mockUTXOHash("2"), 0, Coin.MILLICOIN, 0, false, federation.getP2SHScript()),
            new UTXO(mockUTXOHash("3"), 0, Coin.MILLICOIN, 0, false, federation.getP2SHScript()),
            new UTXO(mockUTXOHash("4"), 0, Coin.CENT, 0, false, federation.getP2SHScript())
        );

        Wallet newWallet = BridgeUtils.getFederationSpendWallet(
            Context.getOrCreate(networkParameters),
            federation,
            newUtxos,
            false,
            mock(BridgeStorageProvider.class)
        );

        ReleaseTransactionBuilder releaseTransactionBuilder = new ReleaseTransactionBuilder(
            networkParameters,
            newWallet,
            federation.getAddress(),
            Coin.MILLICOIN.multiply(3),
            activations
        );

        ReleaseTransactionBuilder.BuildResult newResult = releaseTransactionBuilder.buildBatchedPegouts(pegoutRequests);

        Assertions.assertEquals(ReleaseTransactionBuilder.Response.SUCCESS, newResult.getResponseCode());
    }

    @Test
    void test_BuildBatchedPegouts_WalletExceededMaxTransactionSizeException() {

        List<ReleaseRequestQueue.Entry> pegoutRequests = createTestEntries(600);

        List<UTXO> utxos = PegTestUtils.createUTXOs(600, federation.getAddress());

        Wallet thisWallet = BridgeUtils.getFederationSpendWallet(
            Context.getOrCreate(networkParameters),
            federation,
            utxos,
            false,
            mock(BridgeStorageProvider.class)
        );

        ReleaseTransactionBuilder rtb = new ReleaseTransactionBuilder(
            networkParameters,
            thisWallet,
            federation.getAddress(),
            Coin.MILLICOIN,
            activations
        );

        ReleaseTransactionBuilder.BuildResult result = rtb.buildBatchedPegouts(pegoutRequests);

        Assertions.assertEquals(ReleaseTransactionBuilder.Response.EXCEED_MAX_TRANSACTION_SIZE, result.getResponseCode());
    }

    @Test
    void test_BuildBatchedPegouts_UtxoProviderException() throws InsufficientMoneyException, UTXOProviderException {
        ReleaseRequestQueue.Entry testEntry1 = createTestEntry(123, 2);
        ReleaseRequestQueue.Entry testEntry2 = createTestEntry(456, 4);
        ReleaseRequestQueue.Entry testEntry3 = createTestEntry(789, 5);
        List<ReleaseRequestQueue.Entry> pegoutRequests = Arrays.asList(testEntry1, testEntry2, testEntry3);

        UTXO utxo1 = mockUTXO("one", 0, Coin.COIN);
        UTXO utxo2 = mockUTXO("two", 2, Coin.FIFTY_COINS);
        UTXO utxo3 = mockUTXO("three", 0, Coin.CENT.times(3));
        List<UTXO> availableUTXOs = Arrays.asList(utxo1, utxo2, utxo3);

        UTXOProvider utxoProvider = mock(UTXOProvider.class);
        when(wallet.getUTXOProvider()).thenReturn(utxoProvider);
        when(wallet.getWatchedAddresses()).thenReturn(Collections.singletonList(changeAddress));
        when(utxoProvider.getOpenTransactionOutputs(any(List.class))).then((InvocationOnMock m) -> {
            List<Address> addresses = m.<List>getArgument(0);
            Assertions.assertEquals(Collections.singletonList(changeAddress), addresses);
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

        Assertions.assertEquals(ReleaseTransactionBuilder.Response.UTXO_PROVIDER_EXCEPTION, result.getResponseCode());
        verify(wallet, times(1)).completeTx(any(SendRequest.class));
    }


    @Test
    void test_verifyTXFeeIsSpentEquallyForBatchedPegouts_two_pegouts() {
        List<UTXO> utxos = Arrays.asList(
            new UTXO(mockUTXOHash("1"), 0, Coin.COIN, 0, false, federation.getP2SHScript()),
            new UTXO(mockUTXOHash("2"), 0, Coin.COIN, 0, false, federation.getP2SHScript())
        );

        Wallet thisWallet = BridgeUtils.getFederationSpendWallet(
            Context.getOrCreate(networkParameters),
            federation,
            utxos,
            false,
            mock(BridgeStorageProvider.class)
        );

        ReleaseTransactionBuilder rtb = new ReleaseTransactionBuilder(
            networkParameters,
            thisWallet,
            federation.getAddress(),
            Coin.SATOSHI.multiply(1000),
            activations
        );

        ReleaseRequestQueue.Entry testEntry1 = createTestEntry(123, 3);
        ReleaseRequestQueue.Entry testEntry2 = createTestEntry(456, 4);
        List<ReleaseRequestQueue.Entry> entries = Arrays.asList(testEntry1, testEntry2);

        ReleaseTransactionBuilder.BuildResult result = rtb.buildBatchedPegouts(entries);

        Assertions.assertEquals(ReleaseTransactionBuilder.Response.SUCCESS, result.getResponseCode());

        BtcTransaction btcTx = result.getBtcTx();

        int outputSize = btcTx.getOutputs().size();
        Coin totalFee = btcTx.getFee();
        Coin feeForEachOutput = totalFee.div(outputSize - 1); // minus change output

        Assertions.assertEquals(testEntry1.getAmount().minus(feeForEachOutput), btcTx.getOutput(0).getValue());
        Assertions.assertEquals(testEntry2.getAmount().minus(feeForEachOutput), btcTx.getOutput(1).getValue());
        Assertions.assertEquals(testEntry1.getAmount().minus(btcTx.getOutput(0).getValue())
            .add(testEntry2.getAmount().minus(btcTx.getOutput(1).getValue())), totalFee);

        Coin inputsValue = result.getSelectedUTXOs().stream().map(UTXO::getValue).reduce(Coin.ZERO, Coin::add);
        Coin totalPegoutAmount = entries.stream().map(ReleaseRequestQueue.Entry::getAmount).reduce(Coin.ZERO, Coin::add);

        TransactionOutput changeOutput = btcTx.getOutput(outputSize - 1); // last output

        // Last output should be the change output to the Federation
        Assertions.assertEquals(federation.getAddress(), changeOutput.getAddressFromP2SH(networkParameters));
        Assertions.assertEquals(inputsValue.minus(totalPegoutAmount), changeOutput.getValue());
    }

    @Test
    void test_VerifyTXFeeIsSpentEquallyForBatchedPegouts_three_pegouts() {
        List<UTXO> utxos = Arrays.asList(
            new UTXO(mockUTXOHash("1"), 0, Coin.COIN, 0, false, federation.getP2SHScript()),
            new UTXO(mockUTXOHash("2"), 0, Coin.COIN, 0, false, federation.getP2SHScript())
        );

        Wallet thisWallet = BridgeUtils.getFederationSpendWallet(
            Context.getOrCreate(networkParameters),
            federation,
            utxos,
            false,
            mock(BridgeStorageProvider.class)
        );

        ReleaseTransactionBuilder rtb = new ReleaseTransactionBuilder(
            networkParameters,
            thisWallet,
            federation.getAddress(),
            Coin.SATOSHI.multiply(1000),
            activations
        );

        ReleaseRequestQueue.Entry testEntry1 = createTestEntry(123, 3);
        ReleaseRequestQueue.Entry testEntry2 = createTestEntry(456, 4);
        ReleaseRequestQueue.Entry testEntry3 = createTestEntry(789, 5);
        List<ReleaseRequestQueue.Entry> entries = Arrays.asList(testEntry1, testEntry2, testEntry3);

        ReleaseTransactionBuilder.BuildResult result = rtb.buildBatchedPegouts(entries);

        Assertions.assertEquals(ReleaseTransactionBuilder.Response.SUCCESS, result.getResponseCode());

        BtcTransaction btcTx = result.getBtcTx();

        int outputSize = btcTx.getOutputs().size();
        Coin totalFee = btcTx.getFee();
        Coin feeForEachOutput = totalFee.div(outputSize - 1); // minus change output

        // First Output Pays An Extra Satoshi Because Fee Is Even, And Outputs Is Odd
        Assertions.assertEquals(testEntry1.getAmount().minus(feeForEachOutput), btcTx.getOutput(0).getValue().add(Coin.valueOf(1)));
        Assertions.assertEquals(testEntry2.getAmount().minus(feeForEachOutput), btcTx.getOutput(1).getValue());
        Assertions.assertEquals(testEntry3.getAmount().minus(feeForEachOutput), btcTx.getOutput(2).getValue());
        Assertions.assertEquals(testEntry1.getAmount().minus(btcTx.getOutput(0).getValue())
            .add(testEntry2.getAmount().minus(btcTx.getOutput(1).getValue()))
            .add(testEntry3.getAmount().minus(btcTx.getOutput(2).getValue())), totalFee);

        Coin inputsValue = result.getSelectedUTXOs().stream().map(UTXO::getValue).reduce(Coin.ZERO, Coin::add);
        Coin totalPegoutAmount = entries.stream().map(ReleaseRequestQueue.Entry::getAmount).reduce(Coin.ZERO, Coin::add);

        TransactionOutput changeOutput = btcTx.getOutput(outputSize - 1); // last output

        // Last output should be the change output to the Federation
        Assertions.assertEquals(federation.getAddress(), changeOutput.getAddressFromP2SH(networkParameters));
        Assertions.assertEquals(inputsValue.minus(totalPegoutAmount), changeOutput.getValue());
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
            Assertions.assertEquals(Collections.singletonList(to), addresses);
            return availableUTXOs;
        });

        Mockito.doAnswer((InvocationOnMock m) -> {
            SendRequest sr = m.<SendRequest>getArgument(0);

            Assertions.assertEquals(Coin.MILLICOIN.multiply(2), sr.feePerKb);
            Assertions.assertEquals(Wallet.MissingSigsMode.USE_OP_ZERO, sr.missingSigsMode);
            Assertions.assertEquals(to, sr.changeAddress);
            Assertions.assertFalse(sr.shuffleOutputs);
            Assertions.assertTrue(sr.recipientsPayFees);
            Assertions.assertTrue(sr.emptyWallet);

            BtcTransaction tx = sr.tx;

            Assertions.assertEquals(1, tx.getOutputs().size());
            Assertions.assertEquals(Coin.ZERO, tx.getOutput(0).getValue());
            Assertions.assertEquals(to, tx.getOutput(0).getAddressFromP2PKHScript(networkParameters));

            tx.addInput(mockUTXOHash("one"), 0, mock(Script.class));
            tx.addInput(mockUTXOHash("two"), 2, mock(Script.class));
            tx.addInput(mockUTXOHash("two"), 0, mock(Script.class));
            tx.addInput(mockUTXOHash("three"), 0, mock(Script.class));
            tx.getOutput(0).setValue(Coin.FIFTY_COINS);

            return null;
        }).when(wallet).completeTx(any(SendRequest.class));

        ReleaseTransactionBuilder.BuildResult result = builder.buildEmptyWalletTo(to);

        Assertions.assertEquals(ReleaseTransactionBuilder.Response.SUCCESS, result.getResponseCode());

        BtcTransaction tx = result.getBtcTx();
        List<UTXO> selectedUTXOs = result.getSelectedUTXOs();

        Assertions.assertEquals(1, tx.getOutputs().size());
        Assertions.assertEquals(Coin.FIFTY_COINS, tx.getOutput(0).getValue());
        Assertions.assertEquals(to, tx.getOutput(0).getAddressFromP2PKHScript(networkParameters));

        Assertions.assertEquals(4, tx.getInputs().size());
        Assertions.assertEquals(mockUTXOHash("one"), tx.getInput(0).getOutpoint().getHash());
        Assertions.assertEquals(0, tx.getInput(0).getOutpoint().getIndex());
        Assertions.assertEquals(mockUTXOHash("two"), tx.getInput(1).getOutpoint().getHash());
        Assertions.assertEquals(2, tx.getInput(1).getOutpoint().getIndex());
        Assertions.assertEquals(mockUTXOHash("two"), tx.getInput(2).getOutpoint().getHash());
        Assertions.assertEquals(0, tx.getInput(2).getOutpoint().getIndex());
        Assertions.assertEquals(mockUTXOHash("three"), tx.getInput(3).getOutpoint().getHash());
        Assertions.assertEquals(0, tx.getInput(3).getOutpoint().getIndex());

        Assertions.assertEquals(4, selectedUTXOs.size());
        Assertions.assertEquals(mockUTXOHash("one"), selectedUTXOs.get(0).getHash());
        Assertions.assertEquals(0, selectedUTXOs.get(0).getIndex());
        Assertions.assertEquals(mockUTXOHash("two"), selectedUTXOs.get(1).getHash());
        Assertions.assertEquals(2, selectedUTXOs.get(1).getIndex());
        Assertions.assertEquals(mockUTXOHash("two"), selectedUTXOs.get(2).getHash());
        Assertions.assertEquals(0, selectedUTXOs.get(2).getIndex());
        Assertions.assertEquals(mockUTXOHash("three"), selectedUTXOs.get(3).getHash());
        Assertions.assertEquals(0, selectedUTXOs.get(3).getIndex());

        Assertions.assertEquals(expectedTxVersion, tx.getVersion());
    }

    private void mockCompleteTxWithThrowForBuildToAmount(Wallet wallet, Coin expectedAmount, Address expectedAddress, Throwable t) throws InsufficientMoneyException {
        Mockito.doAnswer((InvocationOnMock m) -> {
            SendRequest sr = m.<SendRequest>getArgument(0);

            Assertions.assertEquals(Coin.MILLICOIN.multiply(2), sr.feePerKb);
            Assertions.assertEquals(Wallet.MissingSigsMode.USE_OP_ZERO, sr.missingSigsMode);
            Assertions.assertEquals(changeAddress, sr.changeAddress);
            Assertions.assertFalse(sr.shuffleOutputs);
            Assertions.assertTrue(sr.recipientsPayFees);

            BtcTransaction tx = sr.tx;

            Assertions.assertEquals(1, tx.getOutputs().size());
            Assertions.assertEquals(expectedAmount, tx.getOutput(0).getValue());
            Assertions.assertEquals(expectedAddress, tx.getOutput(0).getAddressFromP2PKHScript(networkParameters));

            throw t;
        }).when(wallet).completeTx(any(SendRequest.class));
    }

    private void mockCompleteTxWithThrowForEmptying(Wallet wallet, Address expectedAddress, Throwable t) throws InsufficientMoneyException {
        Mockito.doAnswer((InvocationOnMock m) -> {
            SendRequest sr = m.<SendRequest>getArgument(0);

            Assertions.assertEquals(Coin.MILLICOIN.multiply(2), sr.feePerKb);
            Assertions.assertEquals(Wallet.MissingSigsMode.USE_OP_ZERO, sr.missingSigsMode);
            Assertions.assertEquals(expectedAddress, sr.changeAddress);
            Assertions.assertFalse(sr.shuffleOutputs);
            Assertions.assertTrue(sr.recipientsPayFees);
            Assertions.assertTrue(sr.emptyWallet);

            BtcTransaction tx = sr.tx;

            Assertions.assertEquals(1, tx.getOutputs().size());
            Assertions.assertEquals(Coin.ZERO, tx.getOutput(0).getValue());
            Assertions.assertEquals(expectedAddress, tx.getOutput(0).getAddressFromP2PKHScript(networkParameters));

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
