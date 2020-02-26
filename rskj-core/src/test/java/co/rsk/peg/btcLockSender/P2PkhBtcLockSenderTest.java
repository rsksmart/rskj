package co.rsk.peg.btcLockSender;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.TransactionInput;
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

    private static NetworkParameters networkParameters;
    private static BridgeConstants bridgeConstants;

    @BeforeClass
    public static void setup() {
        bridgeConstants = BridgeRegTestConstants.getInstance();
        networkParameters = bridgeConstants.getBtcParams();
    }

    @Test
    public void doesnt_parse_if_tx_is_null() {
        BtcLockSender btcLockSender = new P2pkhBtcLockSender();
        Assert.assertFalse(btcLockSender.tryParse(null));
    }

    @Test
    public void doesnt_parse_if_tx_doesnt_have_inputs() {
        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        BtcLockSender btcLockSender = new P2pkhBtcLockSender();
        Assert.assertFalse(btcLockSender.tryParse(btcTx));
    }

    @Test
    public void doesnt_parse_if_tx_doesnt_have_scriptsig() {
        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        btcTx.addInput(new TransactionInput(networkParameters, null, null));

        BtcLockSender btcLockSender = new P2pkhBtcLockSender();
        Assert.assertFalse(btcLockSender.tryParse(btcTx));
    }

    @Test
    public void doesnt_parse_if_tx_scriptsig_doesnt_have_two_chunks() {
        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        btcTx.addInput(new TransactionInput(networkParameters, null, new byte[]{0x00}));

        BtcLockSender btcLockSender = new P2pkhBtcLockSender();
        Assert.assertFalse(btcLockSender.tryParse(btcTx));
    }

    @Test
    public void gets_p2pkh_lock_sender() {
        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        BtcECKey key = new BtcECKey();
        btcTx.addInput(PegTestUtils.createHash(0), 0, ScriptBuilder.createInputScript(null, key));

        BtcLockSender btcLockSender = new P2pkhBtcLockSender();
        Assert.assertTrue(btcLockSender.tryParse(btcTx));

        Assert.assertEquals(BtcLockSender.TxType.P2PKH, btcLockSender.getType());
        Assert.assertEquals(key.toAddress(networkParameters), btcLockSender.getBTCAddress());
        Assert.assertEquals(new RskAddress(org.ethereum.crypto.ECKey.fromPublicOnly(key.getPubKey()).getAddress()), btcLockSender.getRskAddress());
    }

    @Test
    public void gets_p2pkh_lock_sender_from_raw_tx() {
        String rawTx = RawTransactions.txs.get(BtcLockSender.TxType.P2PKH);
        BtcTransaction btcTx = new BtcTransaction(networkParameters, Hex.decode(rawTx));

        BtcLockSender btcLockSender = new P2pkhBtcLockSender();
        Assert.assertTrue(btcLockSender.tryParse(btcTx));

        org.ethereum.crypto.ECKey key = org.ethereum.crypto.ECKey.fromPublicOnly(Hex.decode("03efa4762ccc1358b72f597d002b7fd1cd58cd05db34fe9fa63e43634acf200927"));
        RskAddress senderAddress = new RskAddress(key.getAddress());

        Assert.assertEquals("mxau4qKjj531q6zQ9pKHMSxr4KS3ifNRQJ", btcLockSender.getBTCAddress().toBase58());
        Assert.assertEquals(senderAddress, btcLockSender.getRskAddress());
        Assert.assertEquals(BtcLockSender.TxType.P2PKH, btcLockSender.getType());
    }

    @Test
    public void rejects_p2sh_p2wpkh_transaction() {
        String rawTx = RawTransactions.txs.get(BtcLockSender.TxType.P2SHP2WPKH);
        BtcTransaction btcTx = new BtcTransaction(networkParameters, Hex.decode(rawTx));

        BtcLockSender btcLockSender = new P2pkhBtcLockSender();
        Assert.assertFalse(btcLockSender.tryParse(btcTx));
    }

    @Test
    public void rejects_p2sh_multisig_transaction() {
        String rawTx = RawTransactions.txs.get(BtcLockSender.TxType.P2SHMULTISIG);
        BtcTransaction btcTx = new BtcTransaction(networkParameters, Hex.decode(rawTx));

        BtcLockSender btcLockSender = new P2pkhBtcLockSender();
        Assert.assertFalse(btcLockSender.tryParse(btcTx));
    }

    @Test
    public void rejects_p2sh_p2wsh_transaction() {
        String rawTx = RawTransactions.txs.get(BtcLockSender.TxType.P2SHP2WSH);
        BtcTransaction btcTx = new BtcTransaction(networkParameters, Hex.decode(rawTx));

        BtcLockSender btcLockSender = new P2pkhBtcLockSender();
        Assert.assertFalse(btcLockSender.tryParse(btcTx));
    }
}
