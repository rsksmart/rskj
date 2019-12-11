package co.rsk.peg.btcLockSender;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.core.RskAddress;
import co.rsk.peg.PegTestUtils;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class P2PkhBtcLockSenderTest {
    protected static NetworkParameters networkParameters;
    protected static BridgeConstants bridgeConstants;

    @BeforeClass
    public static void setup() {
        bridgeConstants = BridgeRegTestConstants.getInstance();
        networkParameters = bridgeConstants.getBtcParams();
    }

    @Test(expected = BtcLockSenderParseException.class)
    public void throws_exception_if_tx_is_null() throws BtcLockSenderParseException {
        new P2pkhBtcLockSender(null);
    }

    @Test(expected = BtcLockSenderParseException.class)
    public void throws_exception_if_tx_doesnt_have_inputs() throws BtcLockSenderParseException {
        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        new P2pkhBtcLockSender(btcTx);
    }

    @Test(expected = BtcLockSenderParseException.class)
    public void throws_exception_if_tx_doesnt_have_scriptsig() throws BtcLockSenderParseException {
        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        btcTx.addInput(new TransactionInput(networkParameters, null, null));
        new P2pkhBtcLockSender(btcTx);
    }

    @Test(expected = BtcLockSenderParseException.class)
    public void throws_exception_if_tx_scriptsig_doesnt_have_two_chunks() throws BtcLockSenderParseException {
        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        btcTx.addInput(new TransactionInput(networkParameters, null, new byte[]{0x00}));
        new P2pkhBtcLockSender(btcTx);
    }

    @Test
    public void get_p2pkh_lock_sender() throws BtcLockSenderParseException {
        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        BtcECKey key = new BtcECKey();

        btcTx.addInput(PegTestUtils.createHash(0), 0, ScriptBuilder.createInputScript(null, key));
        P2pkhBtcLockSender p2PKHBtcLockSender = new P2pkhBtcLockSender(btcTx);

        Assert.assertEquals(BtcLockSender.TxType.P2PKH, p2PKHBtcLockSender.getType());
        Assert.assertEquals(key.toAddress(networkParameters), p2PKHBtcLockSender.getBTCAddress());
        Assert.assertEquals(new RskAddress(org.ethereum.crypto.ECKey.fromPublicOnly(key.getPubKey()).getAddress()), p2PKHBtcLockSender.getRskAddress());
    }

    @Test
    public void get_p2pkh_lock_sender_from_raw_tx() throws BtcLockSenderParseException {
        String rawTx = "02000000028f7efa7cf43fe0d7e557327f02fbebf72049a28715f3c0747b902b061a464c2f010000006a47304402201c1c7ee" +
                "58d152768e7d71010856e501a979c35ab97168dd2b15d155dbe60b8fe022027c8c7c0d16398df212b3997ac67220bbb0abdacfd89be7020b" +
                "25731c9d401af0121027bb07922f9266efc9eb650d94133b995bfcfa80d49011d52807c81ab700247acffffffff3e185adee7df6eeb167d1eaeed" +
                "3869d6a6e1c72f79fcc9c4c5b9a326961267790000000048473044022027b97e40c064014c08decfe7b0c6111df52c392a96409b6ce45b665255ba2" +
                "97e022067901051359d85760a2649c08c5a230e712c5ba6a7d89be0cd65f52ff352e2bd01feffffff0200e1f505000000001976a9149a81fd1d49be2ee5" +
                "ada2bc96c2e0363f27f2e1dd88ac6c3d7d010000000017a914d7f3aadae6afc7b55c75675a42010c7c67450c6c8700000000";

        BtcTransaction btcTx = new BtcTransaction(networkParameters, Hex.decode(rawTx));
        P2pkhBtcLockSender p2PKHBtcLockSender = new P2pkhBtcLockSender(btcTx);

        org.ethereum.crypto.ECKey key = org.ethereum.crypto.ECKey.fromPublicOnly(Hex.decode("027bb07922f9266efc9eb650d94133b995bfcfa80d49011d52807c81ab700247ac"));
        RskAddress senderAddress = new RskAddress(key.getAddress());

        Assert.assertEquals(senderAddress, p2PKHBtcLockSender.getRskAddress());
        Assert.assertEquals("mpgJ8n2NUf23NHcJs59LgEqQ4yCv7MYGU6", p2PKHBtcLockSender.getBTCAddress().toBase58());
    }
}
