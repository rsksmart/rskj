package co.rsk.peg.btcLockSender;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.core.RskAddress;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;
import org.junit.Assert;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BtcLockSenderProviderTest {

    private NetworkParameters params = BridgeRegTestConstants.getInstance().getBtcParams();

    @Test
    public void gets_null_if_btc_transaction_is_not_valid() {
        BtcLockSenderProvider provider = new BtcLockSenderProvider();

        assertFalse(provider.tryGetBtcLockSender(null).isPresent());
        assertFalse(provider.tryGetBtcLockSender(new BtcTransaction(params)).isPresent());
    }

    @Test
    public void gets_valid_sender_from_p2pkh_btc_transaction() {
        BtcLockSenderProvider provider = new BtcLockSenderProvider();

        String rawTx = "02000000028f7efa7cf43fe0d7e557327f02fbebf72049a28715f3c0747b902b061a464c2f010000006a47304402201c1c7ee" +
                "58d152768e7d71010856e501a979c35ab97168dd2b15d155dbe60b8fe022027c8c7c0d16398df212b3997ac67220bbb0abdacfd89be7020b" +
                "25731c9d401af0121027bb07922f9266efc9eb650d94133b995bfcfa80d49011d52807c81ab700247acffffffff3e185adee7df6eeb167d1eaeed" +
                "3869d6a6e1c72f79fcc9c4c5b9a326961267790000000048473044022027b97e40c064014c08decfe7b0c6111df52c392a96409b6ce45b665255ba2" +
                "97e022067901051359d85760a2649c08c5a230e712c5ba6a7d89be0cd65f52ff352e2bd01feffffff0200e1f505000000001976a9149a81fd1d49be2ee5" +
                "ada2bc96c2e0363f27f2e1dd88ac6c3d7d010000000017a914d7f3aadae6afc7b55c75675a42010c7c67450c6c8700000000";

        BtcTransaction tx = new BtcTransaction(BridgeRegTestConstants.getInstance().getBtcParams(), Hex.decode(rawTx));

        org.ethereum.crypto.ECKey key = org.ethereum.crypto.ECKey.fromPublicOnly(Hex.decode("027bb07922f9266efc9eb650d94133b995bfcfa80d49011d52807c81ab700247ac"));
        RskAddress senderAddress = new RskAddress(key.getAddress());

        Optional<BtcLockSender> result = provider.tryGetBtcLockSender(tx);
        assertTrue(result.isPresent());
        BtcLockSender btcLockSender = result.get();

        Assert.assertEquals(BtcLockSender.TxType.P2PKH, btcLockSender.getType());
        Assert.assertEquals(senderAddress, btcLockSender.getRskAddress());
        Assert.assertEquals("mpgJ8n2NUf23NHcJs59LgEqQ4yCv7MYGU6", btcLockSender.getBTCAddress().toBase58());
    }

    @Test
    public void gets_valid_sender_from_p2sh_p2wpkh_btc_transaction() {
        BtcLockSenderProvider provider = new BtcLockSenderProvider();

        String rawTx = "020000000001017001d967a340069c0b169fcbeb9cb6e0d78a27c94a41acbce762abc695aefab10000000017160014cfa63de9979e2a8005e6cb516b86202860ff3971ffffffff0200c2eb0b0000000017a914291a7ddc558810708149a731f39cd3c3a8782cfd870896e1110000000017a91425a2e67511a0207c4387ce8d3eeef498a4782e64870247304402207e0615f440bbc50351fb5d8839b3fae6c74f652c9ffc9291008f4ea39f9565980220354c734511a0560367b300eecb1a7472317a995462622e06ee91cbe0517c17e1012102e87cd90f3cb0d64eeba797fbb8f8ceaadc09e0128afbaefb0ee9535875ea395400000000";
        BtcTransaction tx = new BtcTransaction(BridgeRegTestConstants.getInstance().getBtcParams(), Hex.decode(rawTx));

        Optional<BtcLockSender> result = provider.tryGetBtcLockSender(tx);
        assertTrue(result.isPresent());
        BtcLockSender btcLockSender = result.get();

        BtcECKey key = BtcECKey.fromPublicOnly(Hex.decode("02e87cd90f3cb0d64eeba797fbb8f8ceaadc09e0128afbaefb0ee9535875ea3954"));
        byte[] scriptHash = Hex.decode("bf79dcd97426a127d4ed39385fa58feeb7272387");
        // "2NAhf36HTnrkKAx6RddHAdgJwPsqEgngUwe"
        Assert.assertEquals(new Address(tx.getParams(), tx.getParams().getP2SHHeader(), scriptHash), btcLockSender.getBTCAddress());
        Assert.assertEquals(new RskAddress(ECKey.fromPublicOnly(key.getPubKey()).getAddress()), btcLockSender.getRskAddress());
        Assert.assertEquals(BtcLockSender.TxType.P2SHP2WPKH, btcLockSender.getType());
    }
}
