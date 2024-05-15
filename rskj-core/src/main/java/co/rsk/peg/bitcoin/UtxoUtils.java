package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.VarInt;
import com.google.common.primitives.Bytes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UtxoUtils {
    private UtxoUtils() {}

    public static List<Coin> decodeOutpointValues(byte[] encodedOutpointValues){
        if (encodedOutpointValues == null || encodedOutpointValues.length == 0) {
            return Collections.EMPTY_LIST;
        }

        int offset = 0;
        List<Coin> outpointValues = new ArrayList<>();
        while (encodedOutpointValues.length > offset){
            VarInt entryEncoded = new VarInt(encodedOutpointValues, offset);
            offset += entryEncoded.getSizeInBytes();
            outpointValues.add(Coin.valueOf(entryEncoded.value));
        }
        return outpointValues;
    }

    public static byte[] encodeOutpointValues(List<Coin> outpointValues) {
        if (outpointValues == null || outpointValues.isEmpty()) {
            return new byte[]{};
        }

        List<byte[]> encodedOutpointValues = new ArrayList<>();
        for (Coin outpointValue : outpointValues) {
            VarInt varIntOutpointValue = new VarInt(outpointValue.getValue());
            encodedOutpointValues.add(varIntOutpointValue.encode());
        }
        return Bytes.concat(encodedOutpointValues.toArray(new byte[][]{{}}));
    }
}
