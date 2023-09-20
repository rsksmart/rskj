package co.rsk.bridge.btcLockSender;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.script.Script;
import co.rsk.core.RskAddress;
import org.ethereum.crypto.HashUtil;

public class P2shMultisigBtcLockSender implements BtcLockSender {

    private TxSenderAddressType txSenderAddressType;
    private Address btcAddress;

    public P2shMultisigBtcLockSender() {
        this.txSenderAddressType = TxSenderAddressType.P2SHMULTISIG;
    }

    @Override
    public TxSenderAddressType getTxSenderAddressType() {
        return txSenderAddressType;
    }

    @Override
    public Address getBTCAddress() {
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
        if (btcTx.getInputs().size() == 0) {
            return false;
        }
        if (btcTx.getInput(0).getScriptBytes() == null) {
            return false;
        }

        Script scriptSig = btcTx.getInput(0).getScriptSig();
        if (scriptSig.getChunks().size() < 3) { //At least 3 chunks: a 0, at least 1 signature, and redeem script
            return false;
        }

        int chunksLength = scriptSig.getChunks().size();
        byte[] redeemScript = scriptSig.getChunks().get(chunksLength - 1).data; //Redeem script is the last chunk of the scriptSig

        Script redeem = new Script(redeemScript);
        if(!redeem.isSentToMultiSig()) {
            return false;
        }

        try {
            // Get btc address
            byte[] scriptPubKey = HashUtil.ripemd160(Sha256Hash.hash(redeemScript));
            this.btcAddress = new Address(btcTx.getParams(), btcTx.getParams().getP2SHHeader(), scriptPubKey);

        } catch(Exception e) {
            return false;
        }

        return true;
    }
}
