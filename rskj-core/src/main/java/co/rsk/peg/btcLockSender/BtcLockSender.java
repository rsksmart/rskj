package co.rsk.peg.btcLockSender;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.LegacyAddress;
import co.rsk.core.RskAddress;

public interface BtcLockSender {

    enum TxSenderAddressType {
        P2PKH,
        P2SHP2WPKH,
        P2SHMULTISIG,
        P2SHP2WSH,
        UNKNOWN
    }

    boolean tryParse(BtcTransaction btcTx);

    TxSenderAddressType getTxSenderAddressType();

    LegacyAddress getBTCAddress();

    RskAddress getRskAddress();
}
