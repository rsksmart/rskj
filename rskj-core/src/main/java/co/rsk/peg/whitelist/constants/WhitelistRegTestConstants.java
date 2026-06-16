package co.rsk.peg.whitelist.constants;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.core.RskAddress;
import co.rsk.peg.vote.AddressBasedAuthorizerFactory;

public class WhitelistRegTestConstants extends WhitelistConstants {

    private static final WhitelistRegTestConstants instance = new WhitelistRegTestConstants();

    private WhitelistRegTestConstants() {
        btcParams = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);

        RskAddress authorizerAddress = new RskAddress("87d2a0f33744929da08b65fd62b627ea52b25f8e");
        lockWhitelistChangeAuthorizer = AddressBasedAuthorizerFactory.buildSingleAuthorizer(authorizerAddress);

        genesisWhitelistEnabled = false;
    }

    public static WhitelistRegTestConstants getInstance() {
        return instance;
    }
}
