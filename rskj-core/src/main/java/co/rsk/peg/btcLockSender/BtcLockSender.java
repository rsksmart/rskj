package co.rsk.peg.btcLockSender;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.core.RskAddress;

public interface BtcLockSender {

    enum TxType {
        P2PKH,
        P2SHP2WPKH,
        P2SHMULTISIG,
        P2SHP2WSH
    }

    boolean tryParse(BtcTransaction btcTx);

    BtcLockSender.TxType getType();

    Address getBTCAddress();

    RskAddress getRskAddress();
}
