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

import co.rsk.bitcoinj.core.BtcECKey;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Representation of a given state of the election
 * of an ABI function call by a series of known
 * and authorized electors.
 *
 * @author Ariel Mendelzon
 */
public class ABICallElection {
    private ABICallAuthorizer authorizer;
    private Map<ABICallSpec, List<ECKey>> votes;

    public ABICallElection(ABICallAuthorizer authorizer, Map<ABICallSpec, List<ECKey>> votes) {
        this.authorizer = authorizer;
        this.votes = votes;
        validate();
    }

    public ABICallElection(ABICallAuthorizer authorizer) {
        this.authorizer = authorizer;
        this.votes = new HashMap<>();
    }

    public Map<ABICallSpec, List<ECKey>> getVotes() {
        return votes;
    }

    public void clear() {
        this.votes = new HashMap<>();
    }

    /**
     * Register voter's vote for callSpec
     * @param callSpec the call spec the voter is voting for
     * @param voter the voter's key
     * @return whether the voting succeeded
     */
    public boolean vote(ABICallSpec callSpec, ECKey voter) {
        if (!authorizer.isAuthorized(voter))
            return false;

        if (!votes.containsKey(callSpec)) {
            votes.put(callSpec, new ArrayList<>());
        }

        List<ECKey> callVotes = votes.get(callSpec);

        if (callVotes.contains(voter))
            return false;

        callVotes.add(voter);
        return true;
    }

    /**
     * Returns the election winner abi call spec, or null if there's none
     * The vote authorizer determines the number of participants,
     * whereas this class determines the number of votes that
     * conforms a win
     * @return the winner abi call spec
     */
    public ABICallSpec getWinner() {
        for (Map.Entry<ABICallSpec, List<ECKey>> specVotes : votes.entrySet()) {
            if (specVotes.getValue().size() >= authorizer.getRequiredAuthorizedKeys()) {
                return specVotes.getKey();
            }
        }

        return null;
    }

    /**
     * Removes the entry votes for the current winner of the election
     */
    public void clearWinners() {
        ABICallSpec winner = getWinner();
        if (winner != null) {
            votes.remove(winner);
        }
    }

    private void validate() {
        // Make sure all the votes are authorized
        for (Map.Entry<ABICallSpec, List<ECKey>> specVotes : votes.entrySet())
            for (ECKey vote : specVotes.getValue())
                if (!authorizer.isAuthorized(vote))
                    throw new RuntimeException("Unauthorized voter");
    }
}
