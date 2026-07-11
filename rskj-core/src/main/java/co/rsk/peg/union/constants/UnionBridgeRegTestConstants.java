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

        // EOA authorizer (seed: changeUnionBridgeContractAddressAuthorizer) — calls the bridge directly.
        changeUnionBridgeContractAddressAuthorizer = AddressBasedAuthorizerFactory.buildSingleAuthorizer(
            new RskAddress("6c9dfd950bf748bb26f893f7e5f693c7f60a8f85")
        );

        // Contract authorizer (deployer seed: UnionBridgeAuthorizerDeployer).
        // Deployer is a deterministic EOA from that seed; authorizer is its first deployment (nonce 0).
        // Address = last 20 bytes of keccak256(RLP([deployer, 0])) — standard CREATE addressing.
        // Resulting regtest authorizer contract: 0xff6f4ff09c54561e1476203e56fcfc834911aa64
        changeLockingCapAuthorizer = AddressBasedAuthorizerFactory.buildSingleAuthorizer(
            new RskAddress("ff6f4ff09c54561e1476203e56fcfc834911aa64")
        );

        // Same authorizer as the locking cap authorizer above
        changeTransferPermissionsAuthorizer = AddressBasedAuthorizerFactory.buildSingleAuthorizer(
            new RskAddress("ff6f4ff09c54561e1476203e56fcfc834911aa64")
        );
    }

    public static UnionBridgeConstants getInstance() {
        return instance;
    }
}
