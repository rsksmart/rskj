package co.rsk.peg.whitelist.constants;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.core.RskAddress;
import co.rsk.peg.vote.AddressBasedAuthorizerFactory;

public class WhitelistTestNetConstants extends WhitelistConstants {

    private static final WhitelistTestNetConstants instance = new WhitelistTestNetConstants();

    private WhitelistTestNetConstants() {
        btcParams = NetworkParameters.fromID(NetworkParameters.ID_TESTNET);

        RskAddress authorizerAddress = new RskAddress("54fdb399cf235c9b0d464ab4055af9251883bbfe");
        lockWhitelistChangeAuthorizer = AddressBasedAuthorizerFactory.buildSingleAuthorizer(authorizerAddress);

        genesisWhitelistEnabled = true;
    }

    public static WhitelistTestNetConstants getInstance() {
        return instance;
    }
}
