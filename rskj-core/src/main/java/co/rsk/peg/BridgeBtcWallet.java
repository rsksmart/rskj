/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.peg;

import co.rsk.config.BridgeConstants;
import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.wallet.RedeemData;
import co.rsk.bitcoinj.wallet.Wallet;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * Created by ajlopez on 7/19/2016.
 */
public class BridgeBtcWallet extends Wallet {
    private BridgeConstants bridgeConstants;
    private Context btcContext;

    public BridgeBtcWallet(Context btcContext, BridgeConstants bridgeConstants) {
        super(btcContext);
        this.bridgeConstants = bridgeConstants;
        this.btcContext = btcContext;
    }

    /*
     Method is overridden because implementation in parent is kind of buggy: does not check watched scripts
     */
    @Nullable
    @Override
    public RedeemData findRedeemDataFromScriptHash(byte[] payToScriptHash) {
        Context.propagate(this.btcContext);
//      Oscar: Comment out this line since we now have our own bitcoinj wallet and we disabled this feature
//      I leave the code here just in case we decide to rollback to use the full original bitcoinj Wallet
//      "keyChainGroupLock.lock();"
        try {
            if (!Arrays.equals(bridgeConstants.getFederationPubScript().getPubKeyHash(), payToScriptHash)) {
                return null;
            }
            Script redeemScript = ScriptBuilder.createRedeemScript(bridgeConstants.getFederatorsRequiredToSign(), bridgeConstants.getFederatorPublicKeys());
            return RedeemData.of(bridgeConstants.getFederatorPublicKeys(), redeemScript);
        } finally {
//          Oscar: Comment out this line since we now have our own bitcoinj wallet and we disabled this feature
//          I leave the code here just in case we decide to rollback to use the full original bitcoinj Wallet
//          "keyChainGroupLock.unlock();"
        }
    }
}
