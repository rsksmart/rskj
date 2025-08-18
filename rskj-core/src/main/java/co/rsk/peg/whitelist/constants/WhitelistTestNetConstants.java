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
            "03fcf11ef18d377b345571cb71d533aee40354020d3aa082354ee33a8df60cae2b"
        )).getAddress()));

        lockWhitelistChangeAuthorizer = AddressBasedAuthorizerFactory.buildOneOfManyAuthorizer(authorizerAddress);
    }

    public static WhitelistTestNetConstants getInstance() {
        return instance;
    }
}
