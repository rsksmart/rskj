package co.rsk.pcc.bto;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.pcc.NativeContractIllegalArgumentException;

import java.math.BigInteger;

public class BTOUtilsHelper {
    public NetworkParameters validateAndExtractNetworkFromExtendedPublicKey(String xpub) {
        // Network determination is done by starting character
        // Extended public key must start with a "xpub" (mainnet) or "tpub" (testnet)
        if (xpub.startsWith("xpub")) {
            return NetworkParameters.fromID(NetworkParameters.ID_MAINNET);
        } else if (xpub.startsWith("tpub")) {
            return NetworkParameters.fromID(NetworkParameters.ID_TESTNET);
        } else {
            throw new NativeContractIllegalArgumentException(String.format("Invalid extended public key '%s'", xpub));
        }
    }

    public byte validateAndGetByteFromBigInteger(BigInteger value) {
        byte result;
        try {
            result = value.byteValueExact();
        } catch (ArithmeticException e) {
            throw new NativeContractIllegalArgumentException(String.format("Invalid unsigned byte %d", value), e);
        }
        return result;
    }
}
