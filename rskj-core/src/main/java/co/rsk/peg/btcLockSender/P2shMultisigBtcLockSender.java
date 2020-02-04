package co.rsk.peg.btcLockSender;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.script.Script;
import org.ethereum.crypto.HashUtil;

public class P2shMultisigBtcLockSender extends BtcLockSender {

    public P2shMultisigBtcLockSender(BtcTransaction btcTx) throws BtcLockSenderParseException {
        super(btcTx);
        this.transactionType = TxType.P2SHMULTISIG;
    }

    @Override
    protected void parse(BtcTransaction btcTx) throws BtcLockSenderParseException {
        if (btcTx == null) {
            throw new BtcLockSenderParseException();
        }
        if (btcTx.getInputs().size() == 0) {
            throw new BtcLockSenderParseException();
        }
        if (btcTx.getInput(0).getScriptBytes() == null) {
            throw new BtcLockSenderParseException();
        }

        Script scriptSig = btcTx.getInput(0).getScriptSig();
        if (scriptSig.getChunks().size() < 3) { //At least 3 chunks: a 0, at least 1 signature, and redeem script
            throw new BtcLockSenderParseException();
        }

        int chunksLength = scriptSig.getChunks().size();
        byte[] redeemScript = scriptSig.getChunks().get(chunksLength - 1).data; //Redeem script is the last chunk of the scriptSig

        Script redeem = new Script(redeemScript);
        if(!redeem.isSentToMultiSig()) {
            throw new BtcLockSenderParseException();
        }

        // Get btc address
        byte[] scriptPubKey = HashUtil.ripemd160(Sha256Hash.hash(redeemScript));
        this.btcAddress = new Address(btcTx.getParams(), btcTx.getParams().getP2SHHeader(), scriptPubKey);
    }
}
