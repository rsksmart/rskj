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

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.peg.utils.FederationUtils;
import java.time.Instant;
import java.util.List;

/**
 * Immutable representation of an RSK Federation in the context of
 * a specific BTC network.
 *
 */

public class StandardMultisigFederation extends Federation {

    public StandardMultisigFederation(
        List<FederationMember> members,
        Instant creationTime,
        long creationBlockNumber,
        NetworkParameters btcParams) {

        super(members, creationTime, creationBlockNumber, btcParams);

        validateRedeemScriptSize();
    }

    @Override
    public Script getRedeemScript() {
        if (redeemScript == null) {
            redeemScript = ScriptBuilder.createRedeemScript(getNumberOfSignaturesRequired(), getBtcPublicKeys());
        }

        return redeemScript;
    }

    private void validateRedeemScriptSize() {
        Script redeemScript = this.getRedeemScript();
        if (!FederationUtils.isRedeemScriptSizeValid(redeemScript)) {
            String message = String.format(
                "Unable to create StandardMultisigFederation. The redeem script size is %d, that is above the maximum allowed.",
                redeemScript.getProgram().length
            );
            throw new FederationCreationException(message);
        }
    }
}
