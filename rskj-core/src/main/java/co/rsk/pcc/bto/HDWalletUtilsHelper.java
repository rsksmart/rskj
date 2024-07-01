package co.rsk.pcc.bto;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.pcc.exception.NativeContractIllegalArgumentException;
import co.rsk.util.StringUtils;

public class HDWalletUtilsHelper {
    public NetworkParameters validateAndExtractNetworkFromExtendedPublicKey(String xpub) throws NativeContractIllegalArgumentException {
        // Network determination is done by starting character
        // Extended public key must start with a "xpub" (mainnet) or "tpub" (testnet)
        if (xpub == null) {
            throw new NativeContractIllegalArgumentException(String.format("Invalid extended public key '%s'", xpub));
        }
        if (xpub.startsWith("xpub")) {
            return NetworkParameters.fromID(NetworkParameters.ID_MAINNET);
        } else if (xpub.startsWith("tpub")) {
            return NetworkParameters.fromID(NetworkParameters.ID_TESTNET);
        } else {
            throw new NativeContractIllegalArgumentException(String.format("Invalid extended public key '%s'", StringUtils.trim(xpub)));
        }
    }
}
