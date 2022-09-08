/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

package co.rsk.peg.utils;

import static org.hamcrest.CoreMatchers.is;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionWitness;
import co.rsk.bitcoinj.script.Script;
import co.rsk.config.BridgeRegTestConstants;
import org.bouncycastle.util.encoders.Hex;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

public class BtcTransactionFormatUtilsTest {
    private final NetworkParameters params = BridgeRegTestConstants.getInstance().getBtcParams();

    @Test
    public void calculateBtcTxHash() {
        BtcTransaction btcTransaction = new BtcTransaction(params);
        MatcherAssert.assertThat(BtcTransactionFormatUtils.calculateBtcTxHash(btcTransaction.bitcoinSerialize()), is(btcTransaction.getHash()));

        byte[] rawBtcTransaction = Hex.decode("020000000418bc858998739dbb7e7676435178dba5e71157b1537d415518d5c1fce6349018000000006a47304402204317903e40f8736858f87758e6" +
                "8bf18372bc075bc928fd82aa8e6c03ae8ce9fb022074a59d7449cc753c5a6b10e70db20469076e2a8b950aa44624ee7ff70633f73201210316101490" +
                "2a3984b695c41627f1403f56b0e631152ff265ddb42e36ba0d57b796feffffff6b853f36edb3a55c419792d3923790147b3c429bb6082d11846ff563" +
                "edcdae05010000006b483045022100e202a463722821875bcecea315041623b4f4b7c615bc63c85ddcb4185035cc0502201beb9c556c1a672d326e66" +
                "c4d4b44ac189b7f3296c5ce6128bf9e52f96cfcabc012103a6ba50eaba8d2fc9a638123cf3fe155610cf162253e8cf672f70945fe00fd317feffffff" +
                "ac932fbdbb882a3947652710b6c9117729962efb30f77779265436f804a5f4bc010000006b483045022100ddc4be4b2d61eb6bdecbb76002cc85c304" +
                "630465807039f7c9eaf5583d5c6cce02203bc3dc7429a17a92c63b6ec517d82f3964beda7f3c375a388c0326e3db3a455101210366d0e8c0c72ea7e8" +
                "a48ae9fe525fb51bcea39702b9ba2903758a582e26a7d0b9feffffffe89b208401d4eb6fc01deef1393fe00c1f56e2b86b77268629491894f560adb6" +
                "010000006b483045022100ac01733d947bf43ad97a5792864766c6c6d9963e359a6e0ab470b68565d679b6022003d4afeb917e7711e797b665f5a958" +
                "93dea2f53d07e2840b6441a72702f88412012102b005a7d4368c02dc8e5f171765db281f546b99b921eac18b2910c82d38f820f7feffffff02441427" +
                "010000000017a914056bce3306ec98a0247cebb654809943045d6b51877ff21500000000001976a914f7da7f0f7669bce303cfc48921bb7303e3918b" +
                "1288acdfdc0700");
        MatcherAssert.assertThat(BtcTransactionFormatUtils.calculateBtcTxHash(rawBtcTransaction), is(Sha256Hash.wrap("4d63ac307e0daba3597a0d8075facb4e6cba3908a60920259b7447e28a151576")));
    }


    @Test
    public void getInputsCount() {
        BtcTransaction btcTransaction = new BtcTransaction(params);
        MatcherAssert.assertThat(BtcTransactionFormatUtils.getInputsCount(btcTransaction.bitcoinSerialize()), is(0L));

        byte[] rawBtcTransaction = Hex.decode("020000000418bc858998739dbb7e7676435178dba5e71157b1537d415518d5c1fce6349018000000006a47304402204317903e40f8736858f87758e6" +
                "8bf18372bc075bc928fd82aa8e6c03ae8ce9fb022074a59d7449cc753c5a6b10e70db20469076e2a8b950aa44624ee7ff70633f73201210316101490" +
                "2a3984b695c41627f1403f56b0e631152ff265ddb42e36ba0d57b796feffffff6b853f36edb3a55c419792d3923790147b3c429bb6082d11846ff563" +
                "edcdae05010000006b483045022100e202a463722821875bcecea315041623b4f4b7c615bc63c85ddcb4185035cc0502201beb9c556c1a672d326e66" +
                "c4d4b44ac189b7f3296c5ce6128bf9e52f96cfcabc012103a6ba50eaba8d2fc9a638123cf3fe155610cf162253e8cf672f70945fe00fd317feffffff" +
                "ac932fbdbb882a3947652710b6c9117729962efb30f77779265436f804a5f4bc010000006b483045022100ddc4be4b2d61eb6bdecbb76002cc85c304" +
                "630465807039f7c9eaf5583d5c6cce02203bc3dc7429a17a92c63b6ec517d82f3964beda7f3c375a388c0326e3db3a455101210366d0e8c0c72ea7e8" +
                "a48ae9fe525fb51bcea39702b9ba2903758a582e26a7d0b9feffffffe89b208401d4eb6fc01deef1393fe00c1f56e2b86b77268629491894f560adb6" +
                "010000006b483045022100ac01733d947bf43ad97a5792864766c6c6d9963e359a6e0ab470b68565d679b6022003d4afeb917e7711e797b665f5a958" +
                "93dea2f53d07e2840b6441a72702f88412012102b005a7d4368c02dc8e5f171765db281f546b99b921eac18b2910c82d38f820f7feffffff02441427" +
                "010000000017a914056bce3306ec98a0247cebb654809943045d6b51877ff21500000000001976a914f7da7f0f7669bce303cfc48921bb7303e3918b" +
                "1288acdfdc0700");
        MatcherAssert.assertThat(BtcTransactionFormatUtils.getInputsCount(rawBtcTransaction), is(4L));
    }

    @Test
    public void getInputsCountFromSegwitTx() {
        String rawTx = "020000000001017001d967a340069c0b169fcbeb9cb6e0d78a27c94a41acbce762abc695aefab10000000017160014c" +
                "fa63de9979e2a8005e6cb516b86202860ff3971ffffffff0200c2eb0b0000000017a914291a7ddc558810708149a731f39cd3c3" +
                "a8782cfd870896e1110000000017a91425a2e67511a0207c4387ce8d3eeef498a4782e64870247304402207e0615f440bbc5035" +
                "1fb5d8839b3fae6c74f652c9ffc9291008f4ea39f9565980220354c734511a0560367b300eecb1a7472317a995462622e06ee91" +
                "cbe0517c17e1012102e87cd90f3cb0d64eeba797fbb8f8ceaadc09e0128afbaefb0ee9535875ea395400000000";

        BtcTransaction tx = new BtcTransaction(params, Hex.decode(rawTx));
        MatcherAssert.assertThat(tx.getInputs().size(), is(1));
        MatcherAssert.assertThat(BtcTransactionFormatUtils.getInputsCountForSegwit(Hex.decode(rawTx)), is(1L));
    }

    @Test
    public void getInputsCountFromSegwitTxWithWitness() {
        String rawTx = "020000000001017001d967a340069c0b169fcbeb9cb6e0d78a27c94a41acbce762abc695aefab10000000017160014c" +
                "fa63de9979e2a8005e6cb516b86202860ff3971ffffffff0200c2eb0b0000000017a914291a7ddc558810708149a731f39cd3c3" +
                "a8782cfd870896e1110000000017a91425a2e67511a0207c4387ce8d3eeef498a4782e64870247304402207e0615f440bbc5035" +
                "1fb5d8839b3fae6c74f652c9ffc9291008f4ea39f9565980220354c734511a0560367b300eecb1a7472317a995462622e06ee91" +
                "cbe0517c17e1012102e87cd90f3cb0d64eeba797fbb8f8ceaadc09e0128afbaefb0ee9535875ea395400000000";

        BtcTransaction otherTx = new BtcTransaction(params);
        otherTx.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        TransactionWitness txWit = new TransactionWitness(1);
        txWit.setPush(0, new byte[]{});
        otherTx.setWitness(0, txWit);
        otherTx.addOutput(Coin.COIN, Address.fromBase58(params, "mvbnrCX3bg1cDRUu8pkecrvP6vQkSLDSou"));

        BtcTransaction tx = new BtcTransaction(params, Hex.decode(rawTx));

        MatcherAssert.assertThat(tx.getInputs().size(), is(1));
        MatcherAssert.assertThat(BtcTransactionFormatUtils.getInputsCountForSegwit(otherTx.bitcoinSerialize()), is(1L));
    }
}
