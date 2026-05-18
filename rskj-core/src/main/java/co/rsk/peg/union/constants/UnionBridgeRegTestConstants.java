package co.rsk.peg.union.constants;

import static co.rsk.core.RskAddress.ZERO_ADDRESS;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.vote.AddressBasedAuthorizerFactory;
import java.math.BigInteger;

public class UnionBridgeRegTestConstants extends UnionBridgeConstants {

    private static final UnionBridgeConstants instance = new UnionBridgeRegTestConstants();

    // Private constructor to prevent instantiation
    private UnionBridgeRegTestConstants() {
        btcParams = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);

        unionBridgeAddress = ZERO_ADDRESS;

        BigInteger oneRbtc = BigInteger.TEN.pow(18); // 1 RBTC = 1000000000000000000 wei
        initialLockingCap = new Coin(oneRbtc);
        lockingCapIncrementsMultiplier = 2;

        // seed: changeUnionBridgeContractAddressAuthorizer
        changeUnionBridgeContractAddressAuthorizer = AddressBasedAuthorizerFactory.buildSingleAuthorizer(
            new RskAddress("6c9dfd950bf748bb26f893f7e5f693c7f60a8f85")
        );

        // contract deployer seed: UnionBridgeAuthorizerDeployer
        changeLockingCapAuthorizer = AddressBasedAuthorizerFactory.buildSingleAuthorizer(
            new RskAddress("377e67e16c13994A4d44791Daf0a4d4Cac445783")
        );

        // contract deployer seed: UnionBridgeAuthorizerDeployer
        changeTransferPermissionsAuthorizer = AddressBasedAuthorizerFactory.buildSingleAuthorizer(
            new RskAddress("377e67e16c13994A4d44791Daf0a4d4Cac445783")
        );
    }

    public static UnionBridgeConstants getInstance() {
        return instance;
    }
}
