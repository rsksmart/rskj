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
import co.rsk.bitcoinj.wallet.SendRequest;
import co.rsk.bitcoinj.wallet.Wallet;
import org.ethereum.core.CallTransaction;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.powermock.modules.junit4.PowerMockRunner;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
public class ReleaseTransactionBuilderTest {
    private Wallet wallet;
    private Address changeAddress;
    private ReleaseTransactionBuilder builder;

    @Before
    public void createBuilder() {
        wallet = mock(Wallet.class);
        changeAddress = mockAddress(1000);
        builder = new ReleaseTransactionBuilder(wallet, changeAddress, Coin.MILLICOIN.multiply(2));
    }

    @Test
    public void getters() {
        Assert.assertSame(wallet, builder.getWallet());
        Assert.assertSame(changeAddress, builder.getChangeAddress());
        Assert.assertEquals(Coin.MILLICOIN.multiply(2), builder.getFeePerKb());
    }

    @Test
    public void build_ok() throws InsufficientMoneyException, UTXOProviderException {
        Context btcContext = new Context(NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        Address to = mockAddress(123);
        Coin amount = Coin.CENT.multiply(3);

        UTXOProvider utxoProvider = mock(UTXOProvider.class);
        when(wallet.getUTXOProvider()).thenReturn(utxoProvider);
        when(wallet.getWatchedAddresses()).thenReturn(Arrays.asList(changeAddress));
        when(utxoProvider.getOpenTransactionOutputs(any(List.class))).then((InvocationOnMock m) -> {
            List<Address> addresses = m.getArgumentAt(0, List.class);
            Assert.assertEquals(Arrays.asList(changeAddress), addresses);
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

            return null;
        }).when(wallet).completeTx(any(SendRequest.class));

        Optional<ReleaseTransactionBuilder.BuildResult> result = builder.build(to, amount);
    }

    private Address mockAddress(int pk) {
        return BtcECKey.fromPrivate(BigInteger.valueOf(pk)).toAddress(NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
    }
}
