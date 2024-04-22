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

package co.rsk.peg.vote;

import co.rsk.core.RskAddress;

import java.util.*;

import co.rsk.peg.UnauthorizedVoterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Representation of a given state of the election
 * of an ABI function call by a series of known
 * and authorized electors.
 *
 * @author Ariel Mendelzon
 */
public class ABICallElection {
    private static final Logger logger = LoggerFactory.getLogger(ABICallElection.class);
    private final AddressBasedAuthorizer authorizer;
    private Map<ABICallSpec, List<RskAddress>> votes;

    public ABICallElection(AddressBasedAuthorizer authorizer, Map<ABICallSpec, List<RskAddress>> votes) {
        this.authorizer = authorizer;
        this.votes = votes;
        validate();
    }

    public ABICallElection(AddressBasedAuthorizer authorizer) {
        this.authorizer = authorizer;
        this.votes = new HashMap<>();
    }

    public Map<ABICallSpec, List<RskAddress>> getVotes() {
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
    public boolean vote(ABICallSpec callSpec, RskAddress voter) {
        if (!authorizer.isAuthorized(voter)) {
            logger.info("[vote] Voter is not authorized.");
            return false;
        }

        votes.computeIfAbsent(callSpec, k -> new ArrayList<>());
        List<RskAddress> callVoters = votes.get(callSpec);

        if (callVoters.contains(voter)) {
            logger.info("[vote] Vote has already been registered.");
            return false;
        }

        callVoters.add(voter);
        logger.info("[vote] Vote registered successfully.");
        return true;
    }

    /**
     * Returns the election winner abi call spec, or empty if there's none
     * The vote authorizer determines the number of participants,
     * whereas this class determines the number of votes that
     * conforms a win
     * @return the (optional) winner abi call spec
     */
    public Optional<ABICallSpec> getWinner() {
        for (Map.Entry<ABICallSpec, List<RskAddress>> specVotes : votes.entrySet()) {
            int votesSize = specVotes.getValue().size();
            if (areEnoughVotes(votesSize)) {
                ABICallSpec winner = specVotes.getKey();
                logger.info("[getWinner] Winner is {} ", winner);
                return Optional.of(winner);
            }
        }

        return Optional.empty();
    }

    private boolean areEnoughVotes(int votesSize) {
        return votesSize >= authorizer.getRequiredAuthorizedKeys();
    }

    /**
     * Removes the entry votes for the current winner of the election
     */
    public void clearWinners() {
        Optional<ABICallSpec> winnerOptional = getWinner();
        if (winnerOptional.isPresent()) {
            ABICallSpec winner = winnerOptional.get();
            votes.remove(winner);
        }
    }

    private void validate() {
        // Make sure all the votes are authorized
        for (Map.Entry<ABICallSpec, List<RskAddress>> specVotes : votes.entrySet()) {
            for (RskAddress vote : specVotes.getValue()) {
                if (!authorizer.isAuthorized(vote)) {
                    throw new UnauthorizedVoterException("Unauthorized voter");
                }
            }
        }
    }
}
