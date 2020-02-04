package co.rsk.peg.btcLockSender;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.core.RskAddress;

public abstract class BtcLockSender {

    public enum TxType {
        P2PKH,
        P2SHP2WPKH,
        P2SHMULTISIG,
        P2SHP2WSH
    }

    protected BtcLockSender.TxType transactionType;
    protected Address btcAddress;
    protected RskAddress rskAddress;

    public BtcLockSender(BtcTransaction btcTx) throws BtcLockSenderParseException {
        this.parse(btcTx);
    }

    protected abstract void parse(BtcTransaction btcTx) throws BtcLockSenderParseException;

    public BtcLockSender.TxType getType() {
        return transactionType;
    }

    public Address getBTCAddress() {
        return this.btcAddress;
    }

    public RskAddress getRskAddress() {
        return this.rskAddress;
    }
}
