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
import co.rsk.rules.Standardness;

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
        // TODO: uncomment this and fix related tests
        // validateScriptSigSize();
    }

    @Override
    public Script getRedeemScript() {
        if (redeemScript == null) {
            redeemScript = ScriptBuilder.createRedeemScript(getNumberOfSignaturesRequired(), getBtcPublicKeys());
        }

        return redeemScript;
    }

    private void validateScriptSigSize() {
        // we have to check if the size of every script inside the scriptSig is not above the maximum
        // this scriptSig contains the signatures, the redeem script and some other bytes
        // so it is ok to just check the redeem script size

        int bytesFromRedeemScript = getRedeemScript().getProgram().length;

        if (bytesFromRedeemScript > Standardness.MAX_SCRIPT_ELEMENT_SIZE
        ) {
            String message = "Unable to create Federation. The scriptSig size is above the maximum allowed.";
            throw new FederationCreationException(message);
        }
    }
}
