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

package co.rsk.bridge;

import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.wallet.RedeemData;
import co.rsk.bitcoinj.wallet.Wallet;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * @author ajlopez
 * @author Oscar Guindzberg
 */
public class BridgeBtcWallet extends Wallet {
    private final List<Federation> federations;
    private final Context btcContext;

    public BridgeBtcWallet(Context btcContext, List<Federation> federations) {
        super(btcContext);
        this.federations = federations;
        this.btcContext = btcContext;
    }

    protected Optional<Federation> getDestinationFederation(byte[] payToScriptHash) {
        Context.propagate(this.btcContext);
        return federations.stream().filter(federation ->
            Arrays.equals(federation.getP2SHScript().getPubKeyHash(), payToScriptHash)).findFirst();
    }

    /*
     Method is overridden because implementation in parent is kind of buggy: does not check watched scripts
     */
    @Nullable
    @Override
    public RedeemData findRedeemDataFromScriptHash(byte[] payToScriptHash) {
        Optional<Federation> destinationFederation = getDestinationFederation(payToScriptHash);

        return destinationFederation.map(federation -> RedeemData
            .of(federation.getBtcPublicKeys(), federation.getRedeemScript())).orElse(null);
    }
}
