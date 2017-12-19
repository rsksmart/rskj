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

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.wallet.SendRequest;
import co.rsk.bitcoinj.wallet.Wallet;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.powermock.modules.junit4.PowerMockRunner;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
public class ReleaseTransactionBuilderTest {
    private Wallet wallet;
    private Address changeAddress;
    private ReleaseTransactionBuilder builder;

    @Before
    public void createBuilder() {
        wallet = mock(Wallet.class);
        changeAddress = mockAddress(1000);
        builder = new ReleaseTransactionBuilder(NetworkParameters.fromID(NetworkParameters.ID_REGTEST), wallet, changeAddress, Coin.MILLICOIN.multiply(2));
    }

    @Test
    public void getters() {
        Assert.assertSame(wallet, builder.getWallet());
        Assert.assertSame(changeAddress, builder.getChangeAddress());
        Assert.assertEquals(Coin.MILLICOIN.multiply(2), builder.getFeePerKb());
    }

    @Test
    public void buildAmountTo_ok() throws InsufficientMoneyException, UTXOProviderException {
        Context btcContext = new Context(NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
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
            List<Address> addresses = m.getArgumentAt(0, List.class);
            Assert.assertEquals(Arrays.asList(changeAddress), addresses);
            return availableUTXOs;
        });

        Mockito.doAnswer((InvocationOnMock m) -> {
            SendRequest sr = m.getArgumentAt(0, SendRequest.class);

            Assert.assertEquals(Coin.MILLICOIN.multiply(2), sr.feePerKb);
            Assert.assertEquals(Wallet.MissingSigsMode.USE_OP_ZERO, sr.missingSigsMode);
            Assert.assertEquals(changeAddress, sr.changeAddress);
            Assert.assertFalse(sr.shuffleOutputs);
            Assert.assertTrue(sr.recipientsPayFees);

            BtcTransaction tx = sr.tx;

            Assert.assertEquals(1, tx.getOutputs().size());
            Assert.assertEquals(amount, tx.getOutput(0).getValue());
            Assert.assertEquals(to, tx.getOutput(0).getAddressFromP2PKHScript(NetworkParameters.fromID(NetworkParameters.ID_REGTEST)));

            tx.addInput(mockUTXOHash("two"), 2, mock(Script.class));
            tx.addInput(mockUTXOHash("three"), 0, mock(Script.class));

            return null;
        }).when(wallet).completeTx(any(SendRequest.class));

        Optional<ReleaseTransactionBuilder.BuildResult> result = builder.buildAmountTo(to, amount);

        Assert.assertTrue(result.isPresent());

        BtcTransaction tx = result.get().getBtcTx();
        List<UTXO> selectedUTXOs = result.get().getSelectedUTXOs();

        Assert.assertEquals(1, tx.getOutputs().size());
        Assert.assertEquals(amount, tx.getOutput(0).getValue());
        Assert.assertEquals(to, tx.getOutput(0).getAddressFromP2PKHScript(NetworkParameters.fromID(NetworkParameters.ID_REGTEST)));

        Assert.assertEquals(2, tx.getInputs().size());
        Assert.assertEquals(mockUTXOHash("two"), tx.getInput(0).getOutpoint().getHash());
        Assert.assertEquals(2, tx.getInput(0).getOutpoint().getIndex());
        Assert.assertEquals(mockUTXOHash("three"), tx.getInput(1).getOutpoint().getHash());
        Assert.assertEquals(0, tx.getInput(1).getOutpoint().getIndex());

        Assert.assertEquals(2, selectedUTXOs.size());
        Assert.assertEquals(mockUTXOHash("two"), selectedUTXOs.get(0).getHash());
        Assert.assertEquals(2, selectedUTXOs.get(0).getIndex());
        Assert.assertEquals(mockUTXOHash("three"), selectedUTXOs.get(1).getHash());
        Assert.assertEquals(0, selectedUTXOs.get(1).getIndex());
    }

    @Test
    public void buildAmountTo_insufficientMoneyException() throws InsufficientMoneyException, UTXOProviderException {
        Context btcContext = new Context(NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        Address to = mockAddress(123);
        Coin amount = Coin.CENT.multiply(3);

        mockCompleteTxWithThrowForBuildToAmount(wallet, amount, to, new InsufficientMoneyException(Coin.valueOf(1234)));

        Optional<ReleaseTransactionBuilder.BuildResult> result = builder.buildAmountTo(to, amount);

        Assert.assertFalse(result.isPresent());
        verify(wallet, never()).getWatchedAddresses();
        verify(wallet, never()).getUTXOProvider();
    }

    @Test
    public void buildAmountTo_walletCouldNotAdjustDownwards() throws InsufficientMoneyException, UTXOProviderException {
        Context btcContext = new Context(NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        Address to = mockAddress(123);
        Coin amount = Coin.CENT.multiply(3);

        mockCompleteTxWithThrowForBuildToAmount(wallet, amount, to, new Wallet.CouldNotAdjustDownwards());

        Optional<ReleaseTransactionBuilder.BuildResult> result = builder.buildAmountTo(to, amount);

        Assert.assertFalse(result.isPresent());
        verify(wallet, never()).getWatchedAddresses();
        verify(wallet, never()).getUTXOProvider();
    }

    @Test
    public void buildAmountTo_walletExceededMaxTransactionSize() throws InsufficientMoneyException, UTXOProviderException {
        Context btcContext = new Context(NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        Address to = mockAddress(123);
        Coin amount = Coin.CENT.multiply(3);

        mockCompleteTxWithThrowForBuildToAmount(wallet, amount, to, new Wallet.ExceededMaxTransactionSize());

        Optional<ReleaseTransactionBuilder.BuildResult> result = builder.buildAmountTo(to, amount);

        Assert.assertFalse(result.isPresent());
        verify(wallet, never()).getWatchedAddresses();
        verify(wallet, never()).getUTXOProvider();
    }

    @Test
    public void buildAmountTo_utxoProviderException() throws InsufficientMoneyException, UTXOProviderException {
        Context btcContext = new Context(NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
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
            List<Address> addresses = m.getArgumentAt(0, List.class);
            Assert.assertEquals(Arrays.asList(changeAddress), addresses);
            throw new UTXOProviderException();
        });

        Mockito.doAnswer((InvocationOnMock m) -> {
            SendRequest sr = m.getArgumentAt(0, SendRequest.class);

            Assert.assertEquals(Coin.MILLICOIN.multiply(2), sr.feePerKb);
            Assert.assertEquals(Wallet.MissingSigsMode.USE_OP_ZERO, sr.missingSigsMode);
            Assert.assertEquals(changeAddress, sr.changeAddress);
            Assert.assertFalse(sr.shuffleOutputs);
            Assert.assertTrue(sr.recipientsPayFees);

            BtcTransaction tx = sr.tx;

            Assert.assertEquals(1, tx.getOutputs().size());
            Assert.assertEquals(amount, tx.getOutput(0).getValue());
            Assert.assertEquals(to, tx.getOutput(0).getAddressFromP2PKHScript(NetworkParameters.fromID(NetworkParameters.ID_REGTEST)));

            tx.addInput(mockUTXOHash("two"), 2, mock(Script.class));
            tx.addInput(mockUTXOHash("three"), 0, mock(Script.class));

            return null;
        }).when(wallet).completeTx(any(SendRequest.class));

        Optional<ReleaseTransactionBuilder.BuildResult> result = builder.buildAmountTo(to, amount);
        verify(wallet, times(1)).completeTx(any(SendRequest.class));

        Assert.assertFalse(result.isPresent());
    }

    @Test
    public void buildEmptyWalletTo_ok() throws InsufficientMoneyException, UTXOProviderException {
        Context btcContext = new Context(NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        Address to = mockAddress(123);

        List<UTXO> availableUTXOs = Arrays.asList(
                mockUTXO("one", 0, Coin.COIN),
                mockUTXO("two", 2, Coin.FIFTY_COINS),
                mockUTXO("two", 0, Coin.COIN.times(7)),
                mockUTXO("three", 0, Coin.CENT.times(3))
        );

        UTXOProvider utxoProvider = mock(UTXOProvider.class);
        when(wallet.getUTXOProvider()).thenReturn(utxoProvider);
        when(wallet.getWatchedAddresses()).thenReturn(Arrays.asList(to));
        when(utxoProvider.getOpenTransactionOutputs(any(List.class))).then((InvocationOnMock m) -> {
            List<Address> addresses = m.getArgumentAt(0, List.class);
            Assert.assertEquals(Arrays.asList(to), addresses);
            return availableUTXOs;
        });

        Mockito.doAnswer((InvocationOnMock m) -> {
            SendRequest sr = m.getArgumentAt(0, SendRequest.class);

            Assert.assertEquals(Coin.MILLICOIN.multiply(2), sr.feePerKb);
            Assert.assertEquals(Wallet.MissingSigsMode.USE_OP_ZERO, sr.missingSigsMode);
            Assert.assertEquals(to, sr.changeAddress);
            Assert.assertFalse(sr.shuffleOutputs);
            Assert.assertTrue(sr.recipientsPayFees);
            Assert.assertTrue(sr.emptyWallet);

            BtcTransaction tx = sr.tx;

            Assert.assertEquals(1, tx.getOutputs().size());
            Assert.assertEquals(Coin.ZERO, tx.getOutput(0).getValue());
            Assert.assertEquals(to, tx.getOutput(0).getAddressFromP2PKHScript(NetworkParameters.fromID(NetworkParameters.ID_REGTEST)));

            tx.addInput(mockUTXOHash("one"), 0, mock(Script.class));
            tx.addInput(mockUTXOHash("two"), 2, mock(Script.class));
            tx.addInput(mockUTXOHash("two"), 0, mock(Script.class));
            tx.addInput(mockUTXOHash("three"), 0, mock(Script.class));
            tx.getOutput(0).setValue(Coin.FIFTY_COINS);

            return null;
        }).when(wallet).completeTx(any(SendRequest.class));

        Optional<ReleaseTransactionBuilder.BuildResult> result = builder.buildEmptyWalletTo(to);

        Assert.assertTrue(result.isPresent());

        BtcTransaction tx = result.get().getBtcTx();
        List<UTXO> selectedUTXOs = result.get().getSelectedUTXOs();

        Assert.assertEquals(1, tx.getOutputs().size());
        Assert.assertEquals(Coin.FIFTY_COINS, tx.getOutput(0).getValue());
        Assert.assertEquals(to, tx.getOutput(0).getAddressFromP2PKHScript(NetworkParameters.fromID(NetworkParameters.ID_REGTEST)));

        Assert.assertEquals(4, tx.getInputs().size());
        Assert.assertEquals(mockUTXOHash("one"), tx.getInput(0).getOutpoint().getHash());
        Assert.assertEquals(0, tx.getInput(0).getOutpoint().getIndex());
        Assert.assertEquals(mockUTXOHash("two"), tx.getInput(1).getOutpoint().getHash());
        Assert.assertEquals(2, tx.getInput(1).getOutpoint().getIndex());
        Assert.assertEquals(mockUTXOHash("two"), tx.getInput(2).getOutpoint().getHash());
        Assert.assertEquals(0, tx.getInput(2).getOutpoint().getIndex());
        Assert.assertEquals(mockUTXOHash("three"), tx.getInput(3).getOutpoint().getHash());
        Assert.assertEquals(0, tx.getInput(3).getOutpoint().getIndex());

        Assert.assertEquals(4, selectedUTXOs.size());
        Assert.assertEquals(mockUTXOHash("one"), selectedUTXOs.get(0).getHash());
        Assert.assertEquals(0, selectedUTXOs.get(0).getIndex());
        Assert.assertEquals(mockUTXOHash("two"), selectedUTXOs.get(1).getHash());
        Assert.assertEquals(2, selectedUTXOs.get(1).getIndex());
        Assert.assertEquals(mockUTXOHash("two"), selectedUTXOs.get(2).getHash());
        Assert.assertEquals(0, selectedUTXOs.get(2).getIndex());
        Assert.assertEquals(mockUTXOHash("three"), selectedUTXOs.get(3).getHash());
        Assert.assertEquals(0, selectedUTXOs.get(3).getIndex());
    }

    @Test
    public void buildEmptyWalletTo_insufficientMoneyException() throws InsufficientMoneyException, UTXOProviderException {
        Context btcContext = new Context(NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        Address to = mockAddress(123);

        mockCompleteTxWithThrowForEmptying(wallet, to, new InsufficientMoneyException(Coin.valueOf(1234)));

        Optional<ReleaseTransactionBuilder.BuildResult> result = builder.buildEmptyWalletTo(to);

        Assert.assertFalse(result.isPresent());
        verify(wallet, never()).getWatchedAddresses();
        verify(wallet, never()).getUTXOProvider();
    }

    @Test
    public void buildEmptyWalletTo_walletCouldNotAdjustDownwards() throws InsufficientMoneyException, UTXOProviderException {
        Context btcContext = new Context(NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        Address to = mockAddress(123);

        mockCompleteTxWithThrowForEmptying(wallet, to, new Wallet.CouldNotAdjustDownwards());

        Optional<ReleaseTransactionBuilder.BuildResult> result = builder.buildEmptyWalletTo(to);

        Assert.assertFalse(result.isPresent());
        verify(wallet, never()).getWatchedAddresses();
        verify(wallet, never()).getUTXOProvider();
    }

    @Test
    public void buildEmptyWalletTo_walletExceededMaxTransactionSize() throws InsufficientMoneyException, UTXOProviderException {
        Context btcContext = new Context(NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        Address to = mockAddress(123);

        mockCompleteTxWithThrowForEmptying(wallet, to, new Wallet.ExceededMaxTransactionSize());

        Optional<ReleaseTransactionBuilder.BuildResult> result = builder.buildEmptyWalletTo(to);

        Assert.assertFalse(result.isPresent());
        verify(wallet, never()).getWatchedAddresses();
        verify(wallet, never()).getUTXOProvider();
    }

    @Test
    public void buildEmptyWalletTo_utxoProviderException() throws InsufficientMoneyException, UTXOProviderException {
        Context btcContext = new Context(NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        Address to = mockAddress(123);

        List<UTXO> availableUTXOs = Arrays.asList(
                mockUTXO("two", 2, Coin.FIFTY_COINS),
                mockUTXO("three", 0, Coin.CENT.times(3))
        );

        UTXOProvider utxoProvider = mock(UTXOProvider.class);
        when(wallet.getUTXOProvider()).thenReturn(utxoProvider);
        when(wallet.getWatchedAddresses()).thenReturn(Arrays.asList(to));
        when(utxoProvider.getOpenTransactionOutputs(any(List.class))).then((InvocationOnMock m) -> {
            List<Address> addresses = m.getArgumentAt(0, List.class);
            Assert.assertEquals(Arrays.asList(to), addresses);
            throw new UTXOProviderException();
        });

        Mockito.doAnswer((InvocationOnMock m) -> {
            SendRequest sr = m.getArgumentAt(0, SendRequest.class);

            Assert.assertEquals(Coin.MILLICOIN.multiply(2), sr.feePerKb);
            Assert.assertEquals(Wallet.MissingSigsMode.USE_OP_ZERO, sr.missingSigsMode);
            Assert.assertEquals(to, sr.changeAddress);
            Assert.assertFalse(sr.shuffleOutputs);
            Assert.assertTrue(sr.recipientsPayFees);
            Assert.assertTrue(sr.emptyWallet);

            BtcTransaction tx = sr.tx;

            Assert.assertEquals(1, tx.getOutputs().size());
            Assert.assertEquals(Coin.ZERO, tx.getOutput(0).getValue());
            Assert.assertEquals(to, tx.getOutput(0).getAddressFromP2PKHScript(NetworkParameters.fromID(NetworkParameters.ID_REGTEST)));

            tx.addInput(mockUTXOHash("two"), 2, mock(Script.class));
            tx.addInput(mockUTXOHash("three"), 0, mock(Script.class));
            tx.getOutput(0).setValue(Coin.FIFTY_COINS);

            return null;
        }).when(wallet).completeTx(any(SendRequest.class));

        Optional<ReleaseTransactionBuilder.BuildResult> result = builder.buildEmptyWalletTo(to);
        verify(wallet, times(1)).completeTx(any(SendRequest.class));

        Assert.assertFalse(result.isPresent());
    }

    private void mockCompleteTxWithThrowForBuildToAmount(Wallet wallet, Coin expectedAmount, Address expectedAddress, Throwable t) throws InsufficientMoneyException {
        Mockito.doAnswer((InvocationOnMock m) -> {
            SendRequest sr = m.getArgumentAt(0, SendRequest.class);

            Assert.assertEquals(Coin.MILLICOIN.multiply(2), sr.feePerKb);
            Assert.assertEquals(Wallet.MissingSigsMode.USE_OP_ZERO, sr.missingSigsMode);
            Assert.assertEquals(changeAddress, sr.changeAddress);
            Assert.assertFalse(sr.shuffleOutputs);
            Assert.assertTrue(sr.recipientsPayFees);

            BtcTransaction tx = sr.tx;

            Assert.assertEquals(1, tx.getOutputs().size());
            Assert.assertEquals(expectedAmount, tx.getOutput(0).getValue());
            Assert.assertEquals(expectedAddress, tx.getOutput(0).getAddressFromP2PKHScript(NetworkParameters.fromID(NetworkParameters.ID_REGTEST)));

            throw t;
        }).when(wallet).completeTx(any(SendRequest.class));
    }

    private void mockCompleteTxWithThrowForEmptying(Wallet wallet, Address expectedAddress, Throwable t) throws InsufficientMoneyException {
        Mockito.doAnswer((InvocationOnMock m) -> {
            SendRequest sr = m.getArgumentAt(0, SendRequest.class);

            Assert.assertEquals(Coin.MILLICOIN.multiply(2), sr.feePerKb);
            Assert.assertEquals(Wallet.MissingSigsMode.USE_OP_ZERO, sr.missingSigsMode);
            Assert.assertEquals(expectedAddress, sr.changeAddress);
            Assert.assertFalse(sr.shuffleOutputs);
            Assert.assertTrue(sr.recipientsPayFees);
            Assert.assertTrue(sr.emptyWallet);

            BtcTransaction tx = sr.tx;

            Assert.assertEquals(1, tx.getOutputs().size());
            Assert.assertEquals(Coin.ZERO, tx.getOutput(0).getValue());
            Assert.assertEquals(expectedAddress, tx.getOutput(0).getAddressFromP2PKHScript(NetworkParameters.fromID(NetworkParameters.ID_REGTEST)));

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
}
