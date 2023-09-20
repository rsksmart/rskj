package co.rsk.bridge.btcLockSender;

import co.rsk.bitcoinj.core.BtcTransaction;

import java.util.Optional;

public class BtcLockSenderProvider {

    public Optional<BtcLockSender> tryGetBtcLockSender(BtcTransaction tx) {
        if (tx == null) {
            return Optional.empty();
        }

        BtcLockSender result;

        result = new P2pkhBtcLockSender();
        if (result.tryParse(tx)) {
            return Optional.of(result);
        }

        result = new P2shP2wpkhBtcLockSender();
        if (result.tryParse(tx)) {
            return Optional.of(result);
        }

        result = new P2shMultisigBtcLockSender();
        if (result.tryParse(tx)) {
            return Optional.of(result);
        }

        result = new P2shP2wshBtcLockSender();
        if (result.tryParse(tx)) {
            return Optional.of(result);
        }

        return Optional.empty();
    }
}
