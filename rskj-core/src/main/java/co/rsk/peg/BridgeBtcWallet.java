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
 * @author ajlopez
 * @author Oscar Guindzberg
 */
public class BridgeBtcWallet extends Wallet {
    private Federation federation;
    private Context btcContext;

    public BridgeBtcWallet(Context btcContext, Federation federation) {
        super(btcContext);
        this.federation = federation;
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
            if (!Arrays.equals(federation.getScript().getPubKeyHash(), payToScriptHash)) {
                return null;
            }
            Script redeemScript = ScriptBuilder.createRedeemScript(federation.getNumberOfSignaturesRequired(), federation.getPublicKeys());
            return RedeemData.of(federation.getPublicKeys(), redeemScript);
        } finally {
//          Oscar: Comment out this line since we now have our own bitcoinj wallet and we disabled this feature
//          I leave the code here just in case we decide to rollback to use the full original bitcoinj Wallet
//          "keyChainGroupLock.unlock();"
        }
    }
}
