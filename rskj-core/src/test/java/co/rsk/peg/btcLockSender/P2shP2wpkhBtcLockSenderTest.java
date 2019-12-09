package co.rsk.peg.btcLockSender;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.core.RskAddress;
import co.rsk.peg.PegTestUtils;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.junit.Assert;
import org.junit.Test;

public class P2shP2wpkhBtcLockSenderTest {

    @Test(expected = BtcLockSenderParseException.class)
    public void throws_exception_if_transaction_is_null() throws BtcLockSenderParseException {
        new P2shP2wpkhBtcLockSender(null);
    }

    @Test(expected = BtcLockSenderParseException.class)
    public void throws_exception_if_transaction_doesnt_have_witness() throws BtcLockSenderParseException {
        new P2shP2wpkhBtcLockSender(new BtcTransaction(BridgeRegTestConstants.getInstance().getBtcParams()));
    }

    @Test(expected = BtcLockSenderParseException.class)
    public void throws_exception_if_transaction_has_scriptsig_with_more_than_one_chunk() throws BtcLockSenderParseException {
        BtcTransaction tx = new BtcTransaction(BridgeRegTestConstants.getInstance().getBtcParams());
        tx.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, new BtcECKey()));

        new P2shP2wpkhBtcLockSender(tx);
    }

    @Test(expected = BtcLockSenderParseException.class)
    public void throws_exception_if_transaction_has_null_scriptsig() throws BtcLockSenderParseException {
        BtcTransaction tx = new BtcTransaction(BridgeRegTestConstants.getInstance().getBtcParams());
        tx.addInput(new TransactionInput(tx.getParams(), null, null));
        TransactionWitness witness = new TransactionWitness(1);
        witness.setPush(0, new byte[]{});
        tx.setWitness(0, witness);

        new P2shP2wpkhBtcLockSender(tx);
    }

    @Test(expected = BtcLockSenderParseException.class)
    public void throws_exception_if_transaction_witness_doesnt_have_two_pushes() throws BtcLockSenderParseException {
        BtcTransaction tx = new BtcTransaction(BridgeRegTestConstants.getInstance().getBtcParams());
        tx.addInput(PegTestUtils.createHash(1), 0, new Script(new byte[]{}));
        TransactionWitness witness = new TransactionWitness(1);
        witness.setPush(0, new byte[]{});
        tx.setWitness(0, witness);

        new P2shP2wpkhBtcLockSender(tx);
    }

    @Test(expected = BtcLockSenderParseException.class)
    public void gets_p2sh_p2wpkh_btc_lock_sender() throws BtcLockSenderParseException {
        NetworkParameters networkParameters = BridgeRegTestConstants.getInstance().getBtcParams();
        BtcECKey key = new BtcECKey();

        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addInput(PegTestUtils.createHash(1), 0, new Script(new byte[]{0x00}));
        TransactionWitness witness = new TransactionWitness(2);
        witness.setPush(1, key.decompress().getPubKey());
        tx.setWitness(0, witness);

        new P2shP2wpkhBtcLockSender(tx);
    }

    @Test(expected = BtcLockSenderParseException.class)
    public void reject_p2wpkh_transaction() throws BtcLockSenderParseException {
        String rawTx = "020000000001017d6228912c3d2dfe0f054913f00722e4ff5fe002e73d09ae57b30414ed079ff40000000000ffffffff02d4d5f5050000000017a914ae3f6da3f010a5a107cecf8e477719498092497c8700e1f5050000000017a9145cad76d21f9aa75daf3e0846571fb720d64418e5870247304402207225fdb8bd03762588587732aafe559d155c04135ab4ea8f6e7f07ee5d7d34230220576a21b784d1dc5789c7e814f0d8b2882b7e8b09538bfb95132c17602769f223012103a83a3a251030faf3a9080aac7bb84fb289b6e17ba7805655a62bb6b29ec9048b00000000";

        BtcTransaction tx = new BtcTransaction(BridgeRegTestConstants.getInstance().getBtcParams(), Hex.decode(rawTx));

        new P2shP2wpkhBtcLockSender(tx);
    }

    @Test
    public void gets_p2sh_p2wpkh_btc_lock_sender_from_raw_transaction() throws BtcLockSenderParseException {
        String rawTx = "020000000001017001d967a340069c0b169fcbeb9cb6e0d78a27c94a41acbce762abc695aefab10000000017160014cfa63de9979e2a8005e6cb516b86202860ff3971ffffffff0200c2eb0b0000000017a914291a7ddc558810708149a731f39cd3c3a8782cfd870896e1110000000017a91425a2e67511a0207c4387ce8d3eeef498a4782e64870247304402207e0615f440bbc50351fb5d8839b3fae6c74f652c9ffc9291008f4ea39f9565980220354c734511a0560367b300eecb1a7472317a995462622e06ee91cbe0517c17e1012102e87cd90f3cb0d64eeba797fbb8f8ceaadc09e0128afbaefb0ee9535875ea395400000000";
        BtcTransaction tx = new BtcTransaction(BridgeRegTestConstants.getInstance().getBtcParams(), Hex.decode(rawTx));

        BtcLockSender lockSender = new P2shP2wpkhBtcLockSender(tx);

        BtcECKey key = BtcECKey.fromPublicOnly(Hex.decode("02e87cd90f3cb0d64eeba797fbb8f8ceaadc09e0128afbaefb0ee9535875ea3954"));
        byte[] scriptHash = Hex.decode("bf79dcd97426a127d4ed39385fa58feeb7272387");
        // "2NAhf36HTnrkKAx6RddHAdgJwPsqEgngUwe"
        Assert.assertEquals(new Address(tx.getParams(), tx.getParams().getP2SHHeader(), scriptHash), lockSender.getBTCAddress());
        Assert.assertEquals(new RskAddress(ECKey.fromPublicOnly(key.getPubKey()).getAddress()), lockSender.getRskAddress());
        Assert.assertEquals(BtcLockSender.TxType.P2SHP2WPKH, lockSender.getType());
    }

}
