package co.rsk.peg.btcLockSender;

import co.rsk.bitcoinj.core.BtcTransaction;

import java.util.Optional;

public class BtcLockSenderProvider {

    public Optional<BtcLockSender> tryGetBtcLockSender(BtcTransaction tx) {
        if (tx == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(new P2pkhBtcLockSender(tx));
        } catch(BtcLockSenderParseException e) {
            // Nothing to do...
        }
        try {
            return Optional.of(new P2shP2wpkhBtcLockSender(tx));
        } catch(BtcLockSenderParseException e) {
            // Nothing to do...
        }
        try {
            return Optional.of(new P2shMultisigBtcLockSender(tx));
        } catch(BtcLockSenderParseException e) {
            // Nothing to do...
        }
        try {
            return Optional.of(new P2shP2wshBtcLockSender(tx));
        } catch(BtcLockSenderParseException e) {
            // Nothing to do...
        }

        return Optional.empty();
    }
}
