package co.rsk.peg.federation;

import com.google.common.primitives.UnsignedBytes;

import java.util.Comparator;

public class FederationMemberPubKeysComparator implements Comparator<FederationMember> {
    /**
     * Compares federation members based on their underlying keys.
     *
     * The total ordering is defined such that, for any two members M1, M2,
     * 1) M1 < M2 iff BTC_PUB_KEY(M1) <lex BTC_PUB_KEY(M2) OR
     *              (BTC_PUB_KEY(M1) ==lex BTC_PUB_KEY(M2) AND
     *               RSK_PUB_KEY(M1) <lex RSK_PUB_KEY(M2)) OR
     *              (BTC_PUB_KEY(M1) ==lex BTC_PUB_KEY(M2) AND
     *               RSK_PUB_KEY(M1) ==lex RSK_PUB_KEY(M2) AND
     *               MST_PUB_KEY(M1) <lex MST_PUB_KEY(M2))
     * 2) M1 == M2 iff BTC_PUB_KEY(M1) ==lex BTC_PUB_KEY(M2) AND
     *                 RSK_PUB_KEY(M1) ==lex RSK_PUB_KEY(M2) AND
     *                 MST_PUB_KEY(M1) ==lex MST_PUB_KEY(M2) AND
     * 3) M1 > M2 otherwise
     *
     * where <lex and ==lex is given by negative and zero values (resp.) of the
     * UnsignedBytes.lexicographicalComparator() comparator.
     */
    private final Comparator<byte[]> comparator = UnsignedBytes.lexicographicalComparator();

    @Override
    public int compare(FederationMember fed1, FederationMember fed2) {
        int btcKeysComparison = comparator.compare(fed1.getBtcPublicKey().getPubKey(), fed2.getBtcPublicKey().getPubKey());
        if (btcKeysComparison == 0) {
            int rskKeysComparison = comparator.compare(fed1.getRskPublicKey().getPubKey(), fed2.getRskPublicKey().getPubKey());
            if (rskKeysComparison == 0) {
                return comparator.compare(fed1.getMstPublicKey().getPubKey(), fed2.getMstPublicKey().getPubKey());
            }
            return rskKeysComparison;
        }
        return btcKeysComparison;
    }
}
