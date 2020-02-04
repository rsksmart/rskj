package co.rsk.peg.btcLockSender;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.script.Script;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;

public class P2shP2wshBtcLockSender extends BtcLockSender {

    public P2shP2wshBtcLockSender(BtcTransaction btcTx) throws BtcLockSenderParseException {
        super(btcTx);
        this.transactionType = TxType.P2SHP2WSH;
    }

    @Override
    protected void parse(BtcTransaction btcTx) throws BtcLockSenderParseException {
        if (btcTx == null) {
            throw new BtcLockSenderParseException();
        }
        if (!btcTx.hasWitness()) {
            throw new BtcLockSenderParseException();
        }
        if (btcTx.getInput(0).getScriptBytes() == null) {
            throw new BtcLockSenderParseException();
        }
        if (btcTx.getInput(0).getScriptSig().getChunks().size() != 1) {
            throw new BtcLockSenderParseException();
        }
        if (btcTx.getWitness(0).getPushCount() < 3) { //At least 3 pushes: a 0, at least 1 signature, redeem script
            throw new BtcLockSenderParseException();
        }

        int pushesLength = btcTx.getWitness(0).getPushCount();
        byte[] redeemScript = btcTx.getWitness(0).getPush(pushesLength - 1); //Redeem script is the last push of the witness

        Script redeem = new Script(redeemScript);
        if (!redeem.isSentToMultiSig()) {
            throw new BtcLockSenderParseException();
        }

        // Get btc address
        // witnessVersion = 0x00
        // push32 = 0x20
        // scriptPubKey = hash160(sha256(witnessVersion push32 redeemScript))
        // Ref: https://github.com/bitcoinbook/bitcoinbook/blob/22a5950abfdd3dbd629c88534e50472822f0e356/ch07.asciidoc#pay-to-witness-public-key-hash-inside-pay-to-script-hash
        byte[] redeemScriptHash = Sha256Hash.hash(redeemScript);
        byte[] merged = ByteUtil.merge(new byte[]{0x00, 0x20}, redeemScriptHash);
        byte[] hashedAgain = Sha256Hash.hash(merged);
        byte[] scriptPubKey = HashUtil.ripemd160(hashedAgain);
        //TODO: rename variables?

        this.btcAddress = new Address(btcTx.getParams(), btcTx.getParams().getP2SHHeader(), scriptPubKey);
    }
}
