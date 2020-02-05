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
    public void gets_p2pkh_lock_sender() throws BtcLockSenderParseException {
        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        BtcECKey key = new BtcECKey();
        btcTx.addInput(PegTestUtils.createHash(0), 0, ScriptBuilder.createInputScript(null, key));

        P2pkhBtcLockSender p2PKHBtcLockSender = new P2pkhBtcLockSender(btcTx);

        Assert.assertEquals(BtcLockSender.TxType.P2PKH, p2PKHBtcLockSender.getType());
        Assert.assertEquals(key.toAddress(networkParameters), p2PKHBtcLockSender.getBTCAddress());
        Assert.assertEquals(new RskAddress(org.ethereum.crypto.ECKey.fromPublicOnly(key.getPubKey()).getAddress()), p2PKHBtcLockSender.getRskAddress());
    }

    @Test
    public void gets_p2pkh_lock_sender_from_raw_tx() throws BtcLockSenderParseException {
        String rawTx = RawTransactions.txs.get(BtcLockSender.TxType.P2PKH);
        BtcTransaction btcTx = new BtcTransaction(networkParameters, Hex.decode(rawTx));

        P2pkhBtcLockSender lockSender = new P2pkhBtcLockSender(btcTx);

        org.ethereum.crypto.ECKey key = org.ethereum.crypto.ECKey.fromPublicOnly(Hex.decode("03efa4762ccc1358b72f597d002b7fd1cd58cd05db34fe9fa63e43634acf200927"));
        RskAddress senderAddress = new RskAddress(key.getAddress());

        Assert.assertEquals("mxau4qKjj531q6zQ9pKHMSxr4KS3ifNRQJ", lockSender.getBTCAddress().toBase58());
        Assert.assertEquals(senderAddress, lockSender.getRskAddress());
        Assert.assertEquals(BtcLockSender.TxType.P2PKH, lockSender.getType());
    }

    @Test(expected = BtcLockSenderParseException.class)
    public void rejects_p2sh_p2wpkh_transaction() throws BtcLockSenderParseException {
        String rawTx = RawTransactions.txs.get(BtcLockSender.TxType.P2SHP2WPKH);
        BtcTransaction btcTx = new BtcTransaction(networkParameters, Hex.decode(rawTx));

        new P2pkhBtcLockSender(btcTx);
    }

    @Test(expected = BtcLockSenderParseException.class)
    public void rejects_p2sh_multisig_transaction() throws BtcLockSenderParseException {
        String rawTx = RawTransactions.txs.get(BtcLockSender.TxType.P2SHMULTISIG);
        BtcTransaction btcTx = new BtcTransaction(networkParameters, Hex.decode(rawTx));

        new P2pkhBtcLockSender(btcTx);
    }

    @Test(expected = BtcLockSenderParseException.class)
    public void rejects_p2sh_p2wsh_transaction() throws BtcLockSenderParseException {
        String rawTx = RawTransactions.txs.get(BtcLockSender.TxType.P2SHP2WSH);
        BtcTransaction btcTx = new BtcTransaction(networkParameters, Hex.decode(rawTx));

        new P2pkhBtcLockSender(btcTx);
    }
}
