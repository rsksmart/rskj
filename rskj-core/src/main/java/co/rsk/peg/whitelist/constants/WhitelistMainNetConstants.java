package co.rsk.peg.whitelist.constants;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.core.RskAddress;
import co.rsk.peg.vote.AddressBasedAuthorizerFactory;

public class WhitelistMainNetConstants extends WhitelistConstants {

    private static final WhitelistMainNetConstants instance = new WhitelistMainNetConstants();

    private WhitelistMainNetConstants() {
        btcParams = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);

        RskAddress authorizerAddress = new RskAddress("6ba9d41b07da470fe340cbd439a42538795eb75b");
        lockWhitelistChangeAuthorizer = AddressBasedAuthorizerFactory.buildSingleAuthorizer(authorizerAddress);

        genesisWhitelistEnabled = true;
    }

    public static WhitelistMainNetConstants getInstance() {
        return instance;
    }
}
