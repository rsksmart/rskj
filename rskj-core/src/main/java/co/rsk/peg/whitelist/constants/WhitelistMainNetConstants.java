package co.rsk.peg.whitelist.constants;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.core.RskAddress;
import co.rsk.peg.vote.AddressBasedAuthorizerFactory;
import java.util.Set;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;

public class WhitelistMainNetConstants extends WhitelistConstants {

    private static final WhitelistMainNetConstants instance = new WhitelistMainNetConstants();

    private WhitelistMainNetConstants() {
        btcParams = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);

        Set<RskAddress> authorizerAddress = Set.of(new RskAddress(ECKey.fromPublicOnly(Hex.decode(
            "03fcf11ef18d377b345571cb71d533aee40354020d3aa082354ee33a8df60cae2b"
        )).getAddress()));

        lockWhitelistChangeAuthorizer = AddressBasedAuthorizerFactory.buildOneOfManyAuthorizer(authorizerAddress);
    }

    public static WhitelistMainNetConstants getInstance() {
        return instance;
    }
}
