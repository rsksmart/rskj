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
            "02f94dade3b1fbb7d066e6280852f33d29917a8edc4306b4a5d77c0eeb1566afa2"
        )).getAddress()));

        lockWhitelistChangeAuthorizer = AddressBasedAuthorizerFactory.buildOneOfManyAuthorizer(authorizerAddress);
    }

    public static WhitelistTestNetConstants getInstance() {
        return instance;
    }
}
