package co.rsk.peg.feeperkb.constants;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.vote.AddressBasedAuthorizerFactory;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;

public class FeePerKbRegTestConstants extends FeePerKbConstants {

    private static final FeePerKbRegTestConstants instance = new FeePerKbRegTestConstants();

    private FeePerKbRegTestConstants() {
        // Key generated with GenNodeKey using generator 'auth-fee-per-kb'
        RskAddress feePerKbAuthorizedAddress = new RskAddress(ECKey.fromPublicOnly(Hex.decode(
            "0430c7d0146029db553d60cf11e8d39df1c63979ee2e4cd1e4d4289a5d88cfcbf3a09b06b5cbc88b5bfeb4b87a94cefab81c8d44655e7e813fc3e18f51cfe7e8a0"
        )).getAddress());
        feePerKbChangeAuthorizer = AddressBasedAuthorizerFactory.buildSingleAuthorizer(feePerKbAuthorizedAddress);

        genesisFeePerKb = Coin.MILLICOIN;

        maxFeePerKb = Coin.valueOf(5_000_000L);
    }

    public static FeePerKbRegTestConstants getInstance() {
        return instance;
    }
}
