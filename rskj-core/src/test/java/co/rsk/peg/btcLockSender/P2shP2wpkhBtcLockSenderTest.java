package co.rsk.peg.btcLockSender;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.core.RskAddress;
import co.rsk.peg.PegTestUtils;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class P2shP2wpkhBtcLockSenderTest {

    private static NetworkParameters networkParameters;
    private static BridgeConstants bridgeConstants;

    @BeforeClass
    public static void setup() {
        bridgeConstants = BridgeRegTestConstants.getInstance();
        networkParameters = bridgeConstants.getBtcParams();
    }

    @Test(expected = BtcLockSenderParseException.class)
    public void throws_exception_if_transaction_is_null() throws BtcLockSenderParseException {
        new P2shP2wpkhBtcLockSender(null);
    }

    @Test(expected = BtcLockSenderParseException.class)
    public void throws_exception_if_transaction_doesnt_have_witness() throws BtcLockSenderParseException {
        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        new P2shP2wpkhBtcLockSender(btcTx);
    }

    @Test(expected = BtcLockSenderParseException.class)
    public void throws_exception_if_transaction_has_scriptsig_with_more_than_one_chunk() throws BtcLockSenderParseException {
        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        btcTx.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, new BtcECKey()));

        new P2shP2wpkhBtcLockSender(btcTx);
    }

    @Test(expected = BtcLockSenderParseException.class)
    public void throws_exception_if_transaction_has_null_scriptsig() throws BtcLockSenderParseException {
        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        btcTx.addInput(new TransactionInput(btcTx.getParams(), null, null));
        TransactionWitness witness = new TransactionWitness(1);
        witness.setPush(0, new byte[]{});
        btcTx.setWitness(0, witness);

        new P2shP2wpkhBtcLockSender(btcTx);
    }

    @Test(expected = BtcLockSenderParseException.class)
    public void throws_exception_if_transaction_witness_doesnt_have_two_pushes() throws BtcLockSenderParseException {
        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        btcTx.addInput(PegTestUtils.createHash(1), 0, new Script(new byte[]{}));
        TransactionWitness witness = new TransactionWitness(1);
        witness.setPush(0, new byte[]{});
        btcTx.setWitness(0, witness);

        new P2shP2wpkhBtcLockSender(btcTx);
    }

    @Test
    public void gets_p2sh_p2wpkh_btc_lock_sender_from_raw_transaction() throws BtcLockSenderParseException {
        String rawTx = RawTransactions.txs.get(BtcLockSender.TxType.P2SHP2WPKH);
        BtcTransaction btcTx = new BtcTransaction(networkParameters, Hex.decode(rawTx));

        BtcLockSender lockSender = new P2shP2wpkhBtcLockSender(btcTx);

        BtcECKey key = BtcECKey.fromPublicOnly(Hex.decode("02adeef95a8ffc5d1c4b1a480fd6d68e8b6cf14a65c903b147922150fbfbad91bc"));
        Assert.assertEquals("2NBdCxoCY6wx1NHpwGWfJThHk9K2tVdNx1A", lockSender.getBTCAddress().toBase58());
        Assert.assertEquals(new RskAddress(ECKey.fromPublicOnly(key.getPubKey()).getAddress()), lockSender.getRskAddress());
        Assert.assertEquals(BtcLockSender.TxType.P2SHP2WPKH, lockSender.getType());
    }

    @Test(expected = BtcLockSenderParseException.class)
    public void rejects_p2pkh_transaction() throws BtcLockSenderParseException {
        String rawTx = RawTransactions.txs.get(BtcLockSender.TxType.P2PKH);
        BtcTransaction btcTx = new BtcTransaction(networkParameters, Hex.decode(rawTx));

        new P2shP2wpkhBtcLockSender(btcTx);
    }

    @Test(expected = BtcLockSenderParseException.class)
    public void rejects_p2sh_multisig_transaction() throws BtcLockSenderParseException {
        String rawTx = RawTransactions.txs.get(BtcLockSender.TxType.P2SHMULTISIG);
        BtcTransaction btcTx = new BtcTransaction(networkParameters, Hex.decode(rawTx));

        new P2shP2wpkhBtcLockSender(btcTx);
    }

    @Test(expected = BtcLockSenderParseException.class)
    public void rejects_p2sh_p2wsh_transaction() throws BtcLockSenderParseException {
        String rawTx = RawTransactions.txs.get(BtcLockSender.TxType.P2SHP2WSH);
        BtcTransaction btcTx = new BtcTransaction(networkParameters, Hex.decode(rawTx));

        new P2shP2wpkhBtcLockSender(btcTx);
    }
}
