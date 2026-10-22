package co.rsk.peg.whitelist.constants;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.core.RskAddress;
import co.rsk.peg.vote.AddressBasedAuthorizerFactory;
import java.util.Set;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;

public class WhitelistTestNetConstants extends WhitelistConstants {

    private static final WhitelistTestNetConstants instance = new WhitelistTestNetConstants();

    private WhitelistTestNetConstants() {
        btcParams = NetworkParameters.fromID(NetworkParameters.ID_TESTNET);

        Set<RskAddress> authorizerAddress = Set.of(new RskAddress(ECKey.fromPublicOnly(Hex.decode(
            "04bf7e3bca7f7c58326382ed9c2516a8773c21f1b806984bb1c5c33bd18046502d97b28c0ea5b16433fbb2b23f14e95b36209f304841e814017f1ede1ecbdcfce3"
        )).getAddress()));

        lockWhitelistChangeAuthorizer = AddressBasedAuthorizerFactory.buildOneOfManyAuthorizer(authorizerAddress);
    }

    public static WhitelistTestNetConstants getInstance() {
        return instance;
    }
}
