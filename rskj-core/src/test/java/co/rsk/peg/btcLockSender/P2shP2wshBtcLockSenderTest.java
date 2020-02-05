package co.rsk.peg.btcLockSender;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.peg.PegTestUtils;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class P2shP2wshBtcLockSenderTest {

    private static NetworkParameters networkParameters;
    private static BridgeConstants bridgeConstants;

    @BeforeClass
    public static void setup() {
        bridgeConstants = BridgeRegTestConstants.getInstance();
        networkParameters = bridgeConstants.getBtcParams();
    }

    @Test(expected = BtcLockSenderParseException.class)
    public void throws_exception_if_transaction_is_null() throws BtcLockSenderParseException {
        new P2shP2wshBtcLockSender(null);
    }

    @Test(expected = BtcLockSenderParseException.class)
    public void throws_exception_if_transaction_doesnt_have_witness() throws BtcLockSenderParseException {
        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        new P2shP2wshBtcLockSender(btcTx);
    }

    @Test(expected = BtcLockSenderParseException.class)
    public void throws_exception_if_tx_doesnt_have_scriptsig() throws BtcLockSenderParseException {
        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        btcTx.addInput(new TransactionInput(btcTx.getParams(), null, null));
        TransactionWitness witness = new TransactionWitness(1);
        witness.setPush(0, new byte[]{});
        btcTx.setWitness(0, witness);

        new P2shP2wshBtcLockSender(btcTx);
    }

    @Test(expected = BtcLockSenderParseException.class)
    public void throws_exception_if_transaction_witness_doesnt_have_at_least_three_pushes() throws BtcLockSenderParseException {
        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        btcTx.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, new BtcECKey()));
        TransactionWitness witness = new TransactionWitness(2);
        witness.setPush(0, new byte[]{});
        witness.setPush(1, new byte[]{});
        btcTx.setWitness(0, witness);

        new P2shP2wshBtcLockSender(btcTx);
    }

    @Test
    public void gets_p2sh_p2wsh_btc_lock_sender_from_raw_transaction() throws BtcLockSenderParseException {
        String rawTx = RawTransactions.txs.get(BtcLockSender.TxType.P2SHP2WSH);
        BtcTransaction btcTx = new BtcTransaction(networkParameters, Hex.decode(rawTx));

        BtcLockSender lockSender = new P2shP2wshBtcLockSender(btcTx);

        byte[] redeemScript = Hex.decode(
                "5221036a743d486f700d9dcde2190b0639cc7ea4c1893c2a5f123fc79a84f327d53122210377dd4bff81b414195b36a33" +
                        "a6a25cf15a50f55fa3d849e676d263999f93e2bdb2102a3201b3f78f20685ae1a193b4a37fe856d673d9d86f4b58" +
                        "35026079b061acc9153ae"
        );
        byte[] redeemScriptHash = Sha256Hash.hash(redeemScript);
        byte[] merged = ByteUtil.merge(new byte[]{0x00, 0x20}, redeemScriptHash);
        byte[] hashedAgain = Sha256Hash.hash(merged);
        byte[] scriptPubKey = HashUtil.ripemd160(hashedAgain);
        Address btcAddress = new Address(btcTx.getParams(), btcTx.getParams().getP2SHHeader(), scriptPubKey);

        Assert.assertEquals("2MuSnTWG8zPsiGBjPCcbQVd17Ux2PVd5kGa", lockSender.getBTCAddress().toBase58());
        Assert.assertEquals(btcAddress, lockSender.getBTCAddress());
        Assert.assertEquals(BtcLockSender.TxType.P2SHP2WSH, lockSender.getType());
        Assert.assertNull(lockSender.getRskAddress());
    }

    @Test(expected = BtcLockSenderParseException.class)
    public void rejects_p2pkh_transaction() throws BtcLockSenderParseException {
        String rawTx = RawTransactions.txs.get(BtcLockSender.TxType.P2PKH);
        BtcTransaction btcTx = new BtcTransaction(networkParameters, Hex.decode(rawTx));

        new P2shP2wshBtcLockSender(btcTx);
    }

    @Test(expected = BtcLockSenderParseException.class)
    public void rejects_p2sh_p2wpkh_transaction() throws BtcLockSenderParseException {
        String rawTx = RawTransactions.txs.get(BtcLockSender.TxType.P2SHP2WPKH);
        BtcTransaction btcTx = new BtcTransaction(networkParameters, Hex.decode(rawTx));

        new P2shP2wshBtcLockSender(btcTx);
    }

    @Test(expected = BtcLockSenderParseException.class)
    public void rejects_p2sh_multisig_transaction() throws BtcLockSenderParseException {
        String rawTx = RawTransactions.txs.get(BtcLockSender.TxType.P2SHMULTISIG);
        BtcTransaction btcTx = new BtcTransaction(networkParameters, Hex.decode(rawTx));

        new P2shP2wshBtcLockSender(btcTx);
    }
}
