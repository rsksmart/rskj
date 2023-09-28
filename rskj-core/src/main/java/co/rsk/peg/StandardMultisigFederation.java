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
import co.rsk.bitcoinj.script.ScriptChunk;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

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
    }

    @Override
    public Script getRedeemScript() {
        if (redeemScript == null) {
            redeemScript = ScriptBuilder.createRedeemScript(getNumberOfSignaturesRequired(), getBtcPublicKeys());
        }

        return redeemScript;
    }

    @Override
    public int getNumberOfSignaturesRequired() {
        List<ScriptChunk> standardRedeemScriptChunks = getRedeemScript().getChunks();

        // the threshold of a multisig is the first chunk of the redeemScript
        // and the standardRedeemScript represents a multisig
        ScriptChunk thresholdChunk = standardRedeemScriptChunks.get(0);
        String thresholdString = thresholdChunk.toString();
        return (int) thresholdString.charAt(thresholdString.length() - 1);
    }

    // TODO: define what it means that two federations are "equal"
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || this.getClass() != other.getClass()) {
            return false;
        }

        StandardMultisigFederation otherFederation = (StandardMultisigFederation) other;

        return this.getNumberOfSignaturesRequired() == otherFederation.getNumberOfSignaturesRequired() &&
            this.getSize() == otherFederation.getSize() &&
            this.getCreationTime().equals(otherFederation.getCreationTime()) &&
            this.creationBlockNumber == otherFederation.creationBlockNumber &&
            this.btcParams.equals(otherFederation.btcParams) &&
            this.members.equals(otherFederation.members) &&
            this.getRedeemScript().equals(otherFederation.getRedeemScript());
    }

    @Override
    public int hashCode() {
        // Can use java.util.Objects.hash since all of Instant, int and List<BtcECKey> have
        // well-defined hashCode()s
        return Objects.hash(
            getCreationTime(),
            this.creationBlockNumber,
            getNumberOfSignaturesRequired(),
            getBtcPublicKeys()
        );
    }
}
