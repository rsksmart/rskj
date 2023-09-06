package co.rsk.peg.btcLockSender;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.LegacyAddress;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.script.Script;
import co.rsk.core.RskAddress;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;

public class P2shP2wshBtcLockSender implements BtcLockSender {

    private TxSenderAddressType txSenderAddressType;
    private LegacyAddress btcAddress;

    public P2shP2wshBtcLockSender() {
        this.txSenderAddressType = TxSenderAddressType.P2SHP2WSH;
    }

    @Override
    public TxSenderAddressType getTxSenderAddressType() {
        return txSenderAddressType;
    }

    @Override
    public LegacyAddress getBTCAddress() {
        return this.btcAddress;
    }

    @Override
    public RskAddress getRskAddress() {
        return null;
    }

    @Override
    public boolean tryParse(BtcTransaction btcTx) {
        if (btcTx == null) {
            return false;
        }
        if (!btcTx.hasWitness()) {
            return false;
        }
        if (btcTx.getInput(0).getScriptBytes() == null) {
            return false;
        }
        if (btcTx.getInput(0).getScriptSig().getChunks().size() != 1) {
            return false;
        }
        if (btcTx.getWitness(0).getPushCount() < 3) { //At least 3 pushes: a 0, at least 1 signature, redeem script
            return false;
        }

        int pushesLength = btcTx.getWitness(0).getPushCount();
        byte[] redeemScript = btcTx.getWitness(0).getPush(pushesLength - 1); //Redeem script is the last push of the witness

        Script redeem = new Script(redeemScript);
        if (!redeem.isSentToMultiSig()) {
            return false;
        }

        try {
            // Get btc address
            // witnessVersion = 0x00
            // push32 = 0x20
            // scriptPubKey = hash160(sha256(witnessVersion push32 redeemScript))
            // Ref: https://github.com/bitcoinbook/bitcoinbook/blob/22a5950abfdd3dbd629c88534e50472822f0e356/ch07.asciidoc#pay-to-witness-public-key-hash-inside-pay-to-script-hash
            byte[] redeemScriptHash = Sha256Hash.hash(redeemScript);
            byte[] merged = ByteUtil.merge(new byte[]{0x00, 0x20}, redeemScriptHash);
            byte[] hashedAgain = Sha256Hash.hash(merged);
            byte[] scriptPubKey = HashUtil.ripemd160(hashedAgain);

            this.btcAddress = new LegacyAddress(btcTx.getParams(), true, scriptPubKey);
        } catch (Exception e) {
            return false;
        }

        return true;
    }
}
