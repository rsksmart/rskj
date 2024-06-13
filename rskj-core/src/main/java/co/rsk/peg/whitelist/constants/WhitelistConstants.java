package co.rsk.peg.whitelist.constants;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.vote.AddressBasedAuthorizer;

public class WhitelistConstants {
    protected NetworkParameters btcParams;
    protected AddressBasedAuthorizer lockWhitelistChangeAuthorizer;

    public NetworkParameters getBtcParams() {
        return btcParams;
    }

    public AddressBasedAuthorizer getLockWhitelistChangeAuthorizer() {
        return lockWhitelistChangeAuthorizer;
    }
}
