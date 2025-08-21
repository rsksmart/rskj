package co.rsk.peg.union.constants;

import static co.rsk.core.RskAddress.ZERO_ADDRESS;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.vote.AddressBasedAuthorizerFactory;
import java.math.BigInteger;
import java.util.Set;

public class UnionBridgeTestNetConstants extends UnionBridgeConstants {

    private static final UnionBridgeConstants instance = new UnionBridgeTestNetConstants();

    // Private constructor to prevent instantiation
    private UnionBridgeTestNetConstants() {
        btcParams = NetworkParameters.fromID(NetworkParameters.ID_TESTNET);

        unionBridgeAddress = ZERO_ADDRESS;

        BigInteger oneRbtc = BigInteger.TEN.pow(18); // 1 RBTC = 1000000000000000000 wei
        initialLockingCap = new Coin(oneRbtc).multiply(BigInteger.valueOf(400)); // 400 rbtc
        lockingCapIncrementsMultiplier = 3;

        changeUnionBridgeContractAddressAuthorizer = AddressBasedAuthorizerFactory.buildSingleAuthorizer(
            new RskAddress("e8b8a9214ca868ab10e0d9fd8af1136313965154")
        );

        changeLockingCapAuthorizer = AddressBasedAuthorizerFactory.buildMajorityAuthorizer(Set.of(
            new RskAddress("e8b8a9214ca868ab10e0d9fd8af1136313965154"),
            new RskAddress("624e1844183096932c013db4995923fc9fe580f9"),
            new RskAddress("39bbfc0ffd207dbe53e51506f4b8fceac9629bdf")
        ));

        changeTransferPermissionsAuthorizer = AddressBasedAuthorizerFactory.buildMajorityAuthorizer(Set.of(
            new RskAddress("e8b8a9214ca868ab10e0d9fd8af1136313965154"),
            new RskAddress("624e1844183096932c013db4995923fc9fe580f9"),
            new RskAddress("39bbfc0ffd207dbe53e51506f4b8fceac9629bdf")
        ));
    }

    public static UnionBridgeConstants getInstance() {
        return instance;
    }
}
