package co.rsk.peg.utils;

import co.rsk.bitcoinj.core.Sha256Hash;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MerkleTreeUtilsTest {
    @Test
    void combineLeftRight() {
        // Examples generated using bitcoind on regtest, with two-transaction blocks
        Assertions.assertEquals(
                Sha256Hash.wrap("ceea4835dd23fae1978a3f6f3f0aa0171e018360272dd5b98d37550fbc978d01"),
                MerkleTreeUtils.combineLeftRight(
                        Sha256Hash.wrap("b945b008fbc3f357db745909958b570773fc14575a36af8bbc195b484e21f366"),
                        Sha256Hash.wrap("9880f57b6735152a8c6d4c7e1b3bc6434ee75e459511a642bbb8cb71d3a6b6d8")
                )
        );

        Assertions.assertEquals(
                Sha256Hash.wrap("107857d7233c41d4c37ecaa9ad9d9ab15371f643074866cd23d657e6e99676be"),
                MerkleTreeUtils.combineLeftRight(
                        Sha256Hash.wrap("083eafdf670bb1bbc83b63262887e3cf519c3e252fac29adfb92c1e857b37f91"),
                        Sha256Hash.wrap("5740915e973a211c71655d10e4c672301c27c287843dcfa97b7aafc04992ec5e")
                )
        );

        Assertions.assertEquals(
                Sha256Hash.wrap("71a12c9bd54735864dd6e12640e6d00d60a42a2e92e4cd0bde3f9f268b7d4345"),
                MerkleTreeUtils.combineLeftRight(
                        Sha256Hash.wrap("c960ed36a67318cd562d384bfbf41499db1312835e2bfe86805d9465afe9736f"),
                        Sha256Hash.wrap("120196be3b0ca6ba07d3cfee53f8dc883781e82afdfba11181184f41a67b9898")
                )
        );
    }
}
