package co.rsk.peg.btcLockSender;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.script.Script;
import co.rsk.core.RskAddress;

public class P2pkhBtcLockSender implements BtcLockSender {

    private BtcLockSender.TxType transactionType;
    private Address btcAddress;
    private RskAddress rskAddress;

    public P2pkhBtcLockSender() {
        this.transactionType = TxType.P2PKH;
    }

    public BtcLockSender.TxType getType() {
        return transactionType;
    }

    public Address getBTCAddress() {
        return this.btcAddress;
    }

    public RskAddress getRskAddress() {
        return this.rskAddress;
    }

    public boolean tryParse (BtcTransaction btcTx) {
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
        if (scriptSig.getChunks().size() != 2) {
            return false;
        }

        try {
            byte[] data = scriptSig.getChunks().get(1).data;

            //Looking for btcAddress
            BtcECKey senderBtcKey = BtcECKey.fromPublicOnly(data);
            this.btcAddress = new Address(btcTx.getParams(), senderBtcKey.getPubKeyHash());

            //Looking for rskAddress
            org.ethereum.crypto.ECKey key = org.ethereum.crypto.ECKey.fromPublicOnly(data);
            this.rskAddress = new RskAddress(key.getAddress());
        } catch(Exception e) {
            return false;
        }
        return true;
    }
}
