package co.rsk.bridge.btcLockSender;

import co.rsk.bitcoinj.core.*;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.bridge.btcLockSender.BtcLockSender.TxSenderAddressType;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.HashUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class P2shMultisigBtcLockSenderTest {

    private static NetworkParameters networkParameters;
    private static BridgeConstants bridgeConstants;

    @BeforeAll
     static void setup() {
        bridgeConstants = BridgeRegTestConstants.getInstance();
        networkParameters = bridgeConstants.getBtcParams();
    }

    @Test
    void doesnt_parse_if_transaction_is_null() {
        BtcLockSender btcLockSender = new P2shMultisigBtcLockSender();
        Assertions.assertFalse(btcLockSender.tryParse(null));
    }

    @Test
    void doesnt_parse_if_tx_doesnt_have_inputs() {
        BtcTransaction btcTx = new BtcTransaction(networkParameters);

        BtcLockSender btcLockSender = new P2shMultisigBtcLockSender();
        Assertions.assertFalse(btcLockSender.tryParse(btcTx));
    }

    @Test
    void doesnt_parse_if_tx_doesnt_have_scriptsig() {
        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        btcTx.addInput(new TransactionInput(networkParameters, null, null));

        BtcLockSender btcLockSender = new P2shMultisigBtcLockSender();
        Assertions.assertFalse(btcLockSender.tryParse(btcTx));
    }

    @Test
    void gets_p2sh_multisig_btc_lock_sender_from_raw_transaction() {
        String rawTx = RawTransactions.txs.get(TxSenderAddressType.P2SHMULTISIG);
        BtcTransaction btcTx = new BtcTransaction(networkParameters, Hex.decode(rawTx));

        BtcLockSender btcLockSender = new P2shMultisigBtcLockSender();
        Assertions.assertTrue(btcLockSender.tryParse(btcTx));

        byte[] redeemScript = Hex.decode(
                "52210379e85ce9fe428abaf25783b00fd39490789a5da74c83ba79ee4af734c18e58b22103045c37a5a34ec12b5768dbe" +
                        "05b78d00096304c94c3440fa2381ec669bd176d7321030e7f5032122058b4db9261312b29834c16cdbb2a4960bd0" +
                        "c3d7e7520dc69968253ae"
        );
        byte[] scriptPubKey = HashUtil.ripemd160(Sha256Hash.hash(redeemScript));
        Address btcAddress = new Address(btcTx.getParams(), btcTx.getParams().getP2SHHeader(), scriptPubKey);

        Assertions.assertEquals("2NCSzuju8gU5Ly5Fp9q9SZwt34dhUVEb3ZJ", btcLockSender.getBTCAddress().toBase58());
        Assertions.assertEquals(btcAddress, btcLockSender.getBTCAddress());
        Assertions.assertEquals(TxSenderAddressType.P2SHMULTISIG, btcLockSender.getTxSenderAddressType());
        Assertions.assertNull(btcLockSender.getRskAddress());
    }

    @Test
    void rejects_p2pkh_transaction() {
        String rawTx = RawTransactions.txs.get(TxSenderAddressType.P2PKH);
        BtcTransaction btcTx = new BtcTransaction(networkParameters, Hex.decode(rawTx));


        BtcLockSender btcLockSender = new P2shMultisigBtcLockSender();
        Assertions.assertFalse(btcLockSender.tryParse(btcTx));
    }

    @Test
    void rejects_p2sh_p2wpkh_transaction() {
        String rawTx = RawTransactions.txs.get(TxSenderAddressType.P2SHP2WPKH);
        BtcTransaction btcTx = new BtcTransaction(networkParameters, Hex.decode(rawTx));


        BtcLockSender btcLockSender = new P2shMultisigBtcLockSender();
        Assertions.assertFalse(btcLockSender.tryParse(btcTx));
    }

    @Test
    void rejects_p2sh_p2wsh_transaction() {
        String rawTx = RawTransactions.txs.get(TxSenderAddressType.P2SHP2WSH);
        BtcTransaction btcTx = new BtcTransaction(networkParameters, Hex.decode(rawTx));

        BtcLockSender btcLockSender = new P2shMultisigBtcLockSender();
        Assertions.assertFalse(btcLockSender.tryParse(btcTx));
    }
}
