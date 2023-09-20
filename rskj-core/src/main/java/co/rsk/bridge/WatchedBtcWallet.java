package co.rsk.bridge;

import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.wallet.RedeemData;
import co.rsk.bitcoinj.wallet.Wallet;

import javax.annotation.Nullable;

/**
 * Watched Btc wallet class implementation use to watch addresses
 */
public class WatchedBtcWallet extends Wallet {
    public WatchedBtcWallet(Context context) {
        super(context);
    }

    /**
     * @param payToScriptHash
     * @return null because base implementation method throws UnsupportedOperationException
     * and this cause an error when getting the amount sent to an address.
     */
    @Nullable
    @Override
    public RedeemData findRedeemDataFromScriptHash(byte[] payToScriptHash) {
        return null;
    }
}
