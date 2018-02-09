package org.ethereum.config.blockchain;

import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.mainnet.MainNetAfterBridgeSyncConfig;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;

/**
 * Created by SerAdmin on 1/4/2018.
 */
public class FallbackMainNetConfig extends GenesisConfig  {

    public static class FallbackMainNetConstants extends MainNetAfterBridgeSyncConfig.MainNetConstants {
        private static final BigInteger DIFFICULTY_BOUND_DIVISOR = BigInteger.valueOf(50);
        private static final byte CHAIN_ID = 30;

        private byte[] newfallbackMiningPubKey0 = Hex.decode("04a0434d9e47f3c86235477c7b1ae6ae5d3442d49b1943c2b752a68e2a47e247c7893aba425419bc27a3b6c7e693a24c696f794c2ed877a1593cbee53b037368d7");
        private byte[] newfallbackMiningPubKey1 = Hex.decode("04774ae7f858a9411e5ef4246b70c65aac5649980be5c17891bbec17895da008cbd984a032eb6b5e190243dd56d7b7b365372db1e2dff9d6a8301d74c9c953c61b");



        @Override
        public byte[] getFallbackMiningPubKey0() {
            return newfallbackMiningPubKey0;
        }

        @Override
        public byte[] getFallbackMiningPubKey1() {
            return newfallbackMiningPubKey1;
        }

    }

    public FallbackMainNetConfig() {
        super(new FallbackMainNetConfig.FallbackMainNetConstants());
    }

    protected FallbackMainNetConfig(Constants constants) {
        super(constants);
    }

}
