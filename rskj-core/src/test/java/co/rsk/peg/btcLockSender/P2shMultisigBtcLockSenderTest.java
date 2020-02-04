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
        //Transaction sent from a 2/3 p2sh multisig address to a p2sh address
        String rawTx = "020000000121a60536cf6f03529354deec02aaf39ef64478b6062471c7f6aed0651264df1501000000fc004730440" +
                "220585ff74ad48186c9479034df7cbc848b81f33ded80c6e509006319dedc99735902204b504cddabbe54f6aa1f2a43360c9" +
                "f49910d31711c5ea3c6bbfb986e9532cb3101473044022003bf97235b0680890ddd6bbd6723f7b4a6d75081ed17ded267728" +
                "88de0542857022044f16937ecb93ef2bca9659526fc1b718a5cc632ed0606aea26f4ed89eb89d74014c695221027ff663e0a" +
                "8460907e4fce3ce7eefc5773725e7051c7a65be1beb9903a70792ea2103f613b584f17cbb1e5348114653d50dff49e8d6fd1" +
                "3eb71c622517cbbe38cb2442103d70f421d3f9d5a81cd4333a35b111e793ddcf9dc9ee405f803158e501a5fece053aefffff" +
                "fff022ca5eb0b000000001976a914f2ec1f16bb466a2b8a7ecdf81c2a4df5ace94d7b88ac00e1f5050000000017a914e49cc" +
                "787b8f316472d3bd9894809c9bbc2647bdf8700000000";
        BtcTransaction btcTx = new BtcTransaction(networkParameters, Hex.decode(rawTx));

        BtcLockSender lockSender = new P2shMultisigBtcLockSender(btcTx);

        byte[] redeemScript = Hex.decode("5221027ff663e0a8460907e4fce3ce7eefc5773725e7051c7a65be1beb9903a70792ea2" +
                "103f613b584f17cbb1e5348114653d50dff49e8d6fd13eb71c622517cbbe38cb2442103d70f421d3f9d5a81cd4333a35b111" +
                "e793ddcf9dc9ee405f803158e501a5fece053ae");
        byte[] scriptPubKey = HashUtil.ripemd160(Sha256Hash.hash(redeemScript));
        Address btcAddress = new Address(btcTx.getParams(), btcTx.getParams().getP2SHHeader(), scriptPubKey);

        Assert.assertEquals("2NF9zWoNArueBbVfMMLXNomGKQMSmKyyQ6g", lockSender.getBTCAddress().toBase58());
        Assert.assertEquals(btcAddress, lockSender.getBTCAddress());
        Assert.assertEquals(BtcLockSender.TxType.P2SHMULTISIG, lockSender.getType());
        Assert.assertNull(lockSender.getRskAddress());
    }

    @Test(expected = BtcLockSenderParseException.class)
    public void rejects_transaction_sent_from_p2sh_address() throws BtcLockSenderParseException {
        //Transaction sent from a p2sh address to a p2pkh address
        String rawTx = "020000000001026ed536ff9bfae172ddd806870997df460d2ed870e2b53e35f250f06d94e66b18000000006a47304" +
                "4022024e735297d4f6885f9a630c4cd2fea95b9070cda79ac7b3b63950a3da3061e3902204782fc993c6001a6db8986fba08" +
                "336186e069f69ff81b2236522b12e9748c160012103942c830dea3bffbbd0cf06da53ad585942445f0242b502a43ef82eb8a" +
                "27f7fe1ffffffff01f3afd9130695737da3691c98175e12b1585ac7ba1a0cae170507630725083e00000000171600149ab09" +
                "3195cfd6bab72b891af1402423467c6bbb5feffffff027230e608000000001976a9148f8c508cd319abdfd6d3d198bce1134" +
                "92c6b66ec88ac00c2eb0b000000001976a914ccc6715b9f0da0b1c4d955224ed7846219dee8fe88ac00024730440220380eb" +
                "d9f90c29aa191a1fc7031c0fa30350a7219a56a1bddbc01316fc5db980702201997990bfa98abcda84945e01bda8f2b621ad" +
                "a414af6ede6e405f6560f98a1e6012102e178b3d08ef8f21f63d74108a82436e536626d5988bd0ef6650994f40ae6a80d000" +
                "00000";
        BtcTransaction btcTx = new BtcTransaction(networkParameters, Hex.decode(rawTx));

        new P2shMultisigBtcLockSender(btcTx);
    }

    @Test(expected = BtcLockSenderParseException.class)
    public void rejects_transaction_sent_from_p2pkh_address() throws BtcLockSenderParseException {
        //Transaction sent from a p2pkh address to a p2pkh address
        String rawTx = "020000000287ed6fe270738eb7869310a4a91fd3e1296b7a2a123c3932494fc74889855b1a000000006a473044022" +
                "02ff51ed4c1670ca33e276625c7c6fe66c2b9a0d244301b2e9463dbf5736a00b3022039effabd424bba858db2f51d711505e" +
                "764eaef89c482931607bbea405d8bb08701210349a2337e7bb894f7644e5d2690f343c436a1d46926f1d74b537e5e4ef8f76" +
                "a61ffffffff5e51eee2d634da2a057bddc968e54aea68002f582d0501a6302c1cd9229a509d000000006a4730440220722d0" +
                "9b0a4e7078499f8b77ad9a637269f8d01e0745d8516cdf58000eac7681b022061c751be8d2b6695ddbd539c61322ee30660c" +
                "9e2cc012b00a498189d2b9890170121024505e3d2cc2d7b4d1461f9563601e2c7590038cea68965e3c1d48358c2472edefef" +
                "fffff0200c2eb0b000000001976a9144b88ece64c4cb895d5ee234739f784e420551a0b88accabdea05000000001976a914d" +
                "54961819c27c28d6940f19995de76c2e44566a988ac00000000";
        BtcTransaction btcTx = new BtcTransaction(networkParameters, Hex.decode(rawTx));

        new P2shMultisigBtcLockSender(btcTx);
    }

    //TODO: Add tests that should fail using the raw transactions from other BtcLockSenders successful  tests
}
