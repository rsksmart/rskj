package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.Coin;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class UtxoTestUtils {
    public static List<Coin> coinListOf(long ... values) {
        return Arrays.stream(values)
            .mapToObj(Coin::valueOf)
            .collect(Collectors.toList());
    }
}
