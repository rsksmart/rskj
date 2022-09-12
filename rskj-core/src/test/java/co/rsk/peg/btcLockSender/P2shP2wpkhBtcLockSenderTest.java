package co.rsk.peg.btcLockSender;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.core.RskAddress;
import co.rsk.peg.PegTestUtils;
import co.rsk.peg.btcLockSender.BtcLockSender.TxSenderAddressType;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class P2shP2wpkhBtcLockSenderTest {

    private static NetworkParameters networkParameters;
    private static BridgeConstants bridgeConstants;

    @BeforeAll
     static void setup() {
        bridgeConstants = BridgeRegTestConstants.getInstance();
        networkParameters = bridgeConstants.getBtcParams();
    }

    @Test
    void doesnt_parse_if_transaction_is_null() {
        BtcLockSender btcLockSender = new P2shP2wpkhBtcLockSender();
        Assertions.assertFalse(btcLockSender.tryParse(null));
    }

    @Test
    void doesnt_parse_if_transaction_doesnt_have_witness() {
        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        BtcLockSender btcLockSender = new P2shP2wpkhBtcLockSender();
        Assertions.assertFalse(btcLockSender.tryParse(btcTx));
    }

    @Test
    void doesnt_parse_if_transaction_has_scriptsig_with_more_than_one_chunk() {
        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        btcTx.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, new BtcECKey()));

        BtcLockSender btcLockSender = new P2shP2wpkhBtcLockSender();
        Assertions.assertFalse(btcLockSender.tryParse(btcTx));
    }

    @Test
    void doesnt_parse_if_transaction_has_null_scriptsig() {
        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        btcTx.addInput(new TransactionInput(btcTx.getParams(), null, null));
        TransactionWitness witness = new TransactionWitness(1);
        witness.setPush(0, new byte[]{});
        btcTx.setWitness(0, witness);

        BtcLockSender btcLockSender = new P2shP2wpkhBtcLockSender();
        Assertions.assertFalse(btcLockSender.tryParse(btcTx));
    }

    @Test
    void doesnt_parse_if_transaction_witness_doesnt_have_two_pushes() {
        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        btcTx.addInput(PegTestUtils.createHash(1), 0, new Script(new byte[]{}));
        TransactionWitness witness = new TransactionWitness(1);
        witness.setPush(0, new byte[]{});
        btcTx.setWitness(0, witness);

        BtcLockSender btcLockSender = new P2shP2wpkhBtcLockSender();
        Assertions.assertFalse(btcLockSender.tryParse(btcTx));
    }

    @Test
    void gets_p2sh_p2wpkh_btc_lock_sender_from_raw_transaction() {
        String rawTx = RawTransactions.txs.get(TxSenderAddressType.P2SHP2WPKH);
        BtcTransaction btcTx = new BtcTransaction(networkParameters, Hex.decode(rawTx));

        BtcLockSender btcLockSender = new P2shP2wpkhBtcLockSender();
        Assertions.assertTrue(btcLockSender.tryParse(btcTx));

        BtcECKey key = BtcECKey.fromPublicOnly(Hex.decode("02adeef95a8ffc5d1c4b1a480fd6d68e8b6cf14a65c903b147922150fbfbad91bc"));
        Assertions.assertEquals("2NBdCxoCY6wx1NHpwGWfJThHk9K2tVdNx1A", btcLockSender.getBTCAddress().toBase58());
        Assertions.assertEquals(new RskAddress(ECKey.fromPublicOnly(key.getPubKey()).getAddress()), btcLockSender.getRskAddress());
        Assertions.assertEquals(TxSenderAddressType.P2SHP2WPKH, btcLockSender.getTxSenderAddressType());
    }

    @Test
    void rejects_p2pkh_transaction() {
        String rawTx = RawTransactions.txs.get(TxSenderAddressType.P2PKH);
        BtcTransaction btcTx = new BtcTransaction(networkParameters, Hex.decode(rawTx));

        BtcLockSender btcLockSender = new P2shP2wpkhBtcLockSender();
        Assertions.assertFalse(btcLockSender.tryParse(btcTx));
    }

    @Test
    void rejects_p2sh_multisig_transaction() {
        String rawTx = RawTransactions.txs.get(TxSenderAddressType.P2SHMULTISIG);
        BtcTransaction btcTx = new BtcTransaction(networkParameters, Hex.decode(rawTx));

        BtcLockSender btcLockSender = new P2shP2wpkhBtcLockSender();
        Assertions.assertFalse(btcLockSender.tryParse(btcTx));
    }

    @Test
    void rejects_p2sh_p2wsh_transaction() {
        String rawTx = RawTransactions.txs.get(TxSenderAddressType.P2SHP2WSH);
        BtcTransaction btcTx = new BtcTransaction(networkParameters, Hex.decode(rawTx));

        BtcLockSender btcLockSender = new P2shP2wpkhBtcLockSender();
        Assertions.assertFalse(btcLockSender.tryParse(btcTx));
    }
}
