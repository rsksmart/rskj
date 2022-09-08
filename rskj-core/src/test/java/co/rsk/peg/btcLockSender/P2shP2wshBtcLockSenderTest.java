package co.rsk.peg.btcLockSender;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.peg.PegTestUtils;
import co.rsk.peg.btcLockSender.BtcLockSender.TxSenderAddressType;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class P2shP2wshBtcLockSenderTest {

    private static NetworkParameters networkParameters;
    private static BridgeConstants bridgeConstants;

    @BeforeAll
    public static void setup() {
        bridgeConstants = BridgeRegTestConstants.getInstance();
        networkParameters = bridgeConstants.getBtcParams();
    }

    @Test
    public void doesnt_parse_if_transaction_is_null() {
        BtcLockSender btcLockSender = new P2shP2wshBtcLockSender();
        Assertions.assertFalse(btcLockSender.tryParse(null));
    }

    @Test
    public void doesnt_parse_if_transaction_doesnt_have_witness() {
        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        BtcLockSender btcLockSender = new P2shP2wshBtcLockSender();
        Assertions.assertFalse(btcLockSender.tryParse(btcTx));
    }

    @Test
    public void doesnt_parse_if_tx_doesnt_have_scriptsig() {
        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        btcTx.addInput(new TransactionInput(btcTx.getParams(), null, null));
        TransactionWitness witness = new TransactionWitness(1);
        witness.setPush(0, new byte[]{});
        btcTx.setWitness(0, witness);

        BtcLockSender btcLockSender = new P2shP2wshBtcLockSender();
        Assertions.assertFalse(btcLockSender.tryParse(btcTx));
    }

    @Test
    public void doesnt_parse_if_transaction_witness_doesnt_have_at_least_three_pushes() {
        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        btcTx.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, new BtcECKey()));
        TransactionWitness witness = new TransactionWitness(2);
        witness.setPush(0, new byte[]{});
        witness.setPush(1, new byte[]{});
        btcTx.setWitness(0, witness);

        BtcLockSender btcLockSender = new P2shP2wshBtcLockSender();
        Assertions.assertFalse(btcLockSender.tryParse(btcTx));
    }

    @Test
    public void gets_p2sh_p2wsh_btc_lock_sender_from_raw_transaction() {
        String rawTx = RawTransactions.txs.get(TxSenderAddressType.P2SHP2WSH);
        BtcTransaction btcTx = new BtcTransaction(networkParameters, Hex.decode(rawTx));

        BtcLockSender btcLockSender = new P2shP2wshBtcLockSender();
        Assertions.assertTrue(btcLockSender.tryParse(btcTx));

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

        Assertions.assertEquals("2MuSnTWG8zPsiGBjPCcbQVd17Ux2PVd5kGa", btcLockSender.getBTCAddress().toBase58());
        Assertions.assertEquals(btcAddress, btcLockSender.getBTCAddress());
        Assertions.assertEquals(TxSenderAddressType.P2SHP2WSH, btcLockSender.getTxSenderAddressType());
        Assertions.assertNull(btcLockSender.getRskAddress());
    }

    @Test
    public void rejects_p2pkh_transaction() {
        String rawTx = RawTransactions.txs.get(TxSenderAddressType.P2PKH);
        BtcTransaction btcTx = new BtcTransaction(networkParameters, Hex.decode(rawTx));

        BtcLockSender btcLockSender = new P2shP2wshBtcLockSender();
        Assertions.assertFalse(btcLockSender.tryParse(btcTx));
    }

    @Test
    public void rejects_p2sh_p2wpkh_transaction() {
        String rawTx = RawTransactions.txs.get(TxSenderAddressType.P2SHP2WPKH);
        BtcTransaction btcTx = new BtcTransaction(networkParameters, Hex.decode(rawTx));

        BtcLockSender btcLockSender = new P2shP2wshBtcLockSender();
        Assertions.assertFalse(btcLockSender.tryParse(btcTx));
    }

    @Test
    public void rejects_p2sh_multisig_transaction() {
        String rawTx = RawTransactions.txs.get(TxSenderAddressType.P2SHMULTISIG);
        BtcTransaction btcTx = new BtcTransaction(networkParameters, Hex.decode(rawTx));

        BtcLockSender btcLockSender = new P2shP2wshBtcLockSender();
        Assertions.assertFalse(btcLockSender.tryParse(btcTx));
    }
}
