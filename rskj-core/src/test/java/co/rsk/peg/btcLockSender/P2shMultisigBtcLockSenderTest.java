package co.rsk.peg.btcLockSender;

import co.rsk.bitcoinj.core.*;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.HashUtil;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class P2shMultisigBtcLockSenderTest {

    private static NetworkParameters networkParameters;
    private static BridgeConstants bridgeConstants;

    @BeforeClass
    public static void setup() {
        bridgeConstants = BridgeRegTestConstants.getInstance();
        networkParameters = bridgeConstants.getBtcParams();
    }

    @Test(expected = BtcLockSenderParseException.class)
    public void throws_exception_if_transaction_is_null() throws BtcLockSenderParseException {
        new P2shMultisigBtcLockSender(null);
    }

    @Test(expected = BtcLockSenderParseException.class)
    public void throws_exception_if_tx_doesnt_have_inputs() throws BtcLockSenderParseException {
        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        new P2shMultisigBtcLockSender(btcTx);
    }

    @Test(expected = BtcLockSenderParseException.class)
    public void throws_exception_if_tx_doesnt_have_scriptsig() throws BtcLockSenderParseException {
        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        btcTx.addInput(new TransactionInput(networkParameters, null, null));
        new P2shMultisigBtcLockSender(btcTx);
    }

    @Test
    public void gets_p2sh_multisig_btc_lock_sender_from_raw_transaction() throws BtcLockSenderParseException {
        String rawTx = RawTransactions.txs.get(BtcLockSender.TxType.P2SHMULTISIG);
        BtcTransaction btcTx = new BtcTransaction(networkParameters, Hex.decode(rawTx));

        BtcLockSender lockSender = new P2shMultisigBtcLockSender(btcTx);

        byte[] redeemScript = Hex.decode(
                "52210379e85ce9fe428abaf25783b00fd39490789a5da74c83ba79ee4af734c18e58b22103045c37a5a34ec12b5768dbe" +
                        "05b78d00096304c94c3440fa2381ec669bd176d7321030e7f5032122058b4db9261312b29834c16cdbb2a4960bd0" +
                        "c3d7e7520dc69968253ae"
        );
        byte[] scriptPubKey = HashUtil.ripemd160(Sha256Hash.hash(redeemScript));
        Address btcAddress = new Address(btcTx.getParams(), btcTx.getParams().getP2SHHeader(), scriptPubKey);

        Assert.assertEquals("2NCSzuju8gU5Ly5Fp9q9SZwt34dhUVEb3ZJ", lockSender.getBTCAddress().toBase58());
        Assert.assertEquals(btcAddress, lockSender.getBTCAddress());
        Assert.assertEquals(BtcLockSender.TxType.P2SHMULTISIG, lockSender.getType());
        Assert.assertNull(lockSender.getRskAddress());
    }

    @Test(expected = BtcLockSenderParseException.class)
    public void rejects_p2pkh_transaction() throws BtcLockSenderParseException {
        String rawTx = RawTransactions.txs.get(BtcLockSender.TxType.P2PKH);
        BtcTransaction btcTx = new BtcTransaction(networkParameters, Hex.decode(rawTx));

        new P2shMultisigBtcLockSender(btcTx);
    }

    @Test(expected = BtcLockSenderParseException.class)
    public void rejects_p2sh_p2wpkh_transaction() throws BtcLockSenderParseException {
        String rawTx = RawTransactions.txs.get(BtcLockSender.TxType.P2SHP2WPKH);
        BtcTransaction btcTx = new BtcTransaction(networkParameters, Hex.decode(rawTx));

        new P2shMultisigBtcLockSender(btcTx);
    }

    @Test(expected = BtcLockSenderParseException.class)
    public void rejects_p2sh_p2wsh_transaction() throws BtcLockSenderParseException {
        String rawTx = RawTransactions.txs.get(BtcLockSender.TxType.P2SHP2WSH);
        BtcTransaction btcTx = new BtcTransaction(networkParameters, Hex.decode(rawTx));

        new P2shMultisigBtcLockSender(btcTx);
    }
}
