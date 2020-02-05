package co.rsk.peg.btcLockSender;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.script.Script;
import co.rsk.core.RskAddress;

public class P2pkhBtcLockSender extends BtcLockSender {

    public P2pkhBtcLockSender(BtcTransaction btcTx) throws BtcLockSenderParseException {
        super(btcTx);
        this.transactionType = TxType.P2PKH;
    }

    @Override
    protected void parse (BtcTransaction btcTx) throws BtcLockSenderParseException {
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
        if (scriptSig.getChunks().size() != 2) {
            throw new BtcLockSenderParseException();
        }

        byte[] data = scriptSig.getChunks().get(1).data;

        //Looking for btcAddress
        BtcECKey senderBtcKey = BtcECKey.fromPublicOnly(data);
        this.btcAddress = new Address(btcTx.getParams(), senderBtcKey.getPubKeyHash());

        //Looking for rskAddress
        org.ethereum.crypto.ECKey key = org.ethereum.crypto.ECKey.fromPublicOnly(data);
        this.rskAddress = new RskAddress(key.getAddress());
    }
}
