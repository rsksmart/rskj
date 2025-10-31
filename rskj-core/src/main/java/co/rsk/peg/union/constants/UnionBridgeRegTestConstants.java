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

        // seed: changeLockingCapAuthorizer
        changeLockingCapAuthorizer = AddressBasedAuthorizerFactory.buildSingleAuthorizer(
            new RskAddress("8f8185858643e08b07df4701d8546406b7bf22e4")
        );

        // seed: changeTransferPermissionsAuthorizer
        changeTransferPermissionsAuthorizer = AddressBasedAuthorizerFactory.buildSingleAuthorizer(
            new RskAddress("af0fd16c15d0286fd78db5fe89412c00d3de3cf4")
        );
    }

    public static UnionBridgeConstants getInstance() {
        return instance;
    }
}
