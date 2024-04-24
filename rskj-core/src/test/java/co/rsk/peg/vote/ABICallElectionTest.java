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
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.crypto.ECKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ABICallElectionTest {
    private ABICallSpec spec_fna, spec_fnb;
    private ABICallElection election;
    private AddressBasedAuthorizer authorizer;
    private Map<ABICallSpec, List<RskAddress>> votes;

    @BeforeEach
    void createVotesAuthorizerAndElection() {
        authorizer = new AddressBasedAuthorizer(Arrays.asList(
                createMockKeyForAddress("aa"),
                createMockKeyForAddress("bb"),
                createMockKeyForAddress("cc"),
                createMockKeyForAddress("dd"),
                createMockKeyForAddress("ee")
        ), AddressBasedAuthorizer.MinimumRequiredCalculation.MAJORITY);

        spec_fna = new ABICallSpec("fn-a", new byte[][]{});
        spec_fnb = new ABICallSpec("fn-b", new byte[][]{ Hex.decode("11"), Hex.decode("2233") });

        votes = new HashMap<>();
        votes.put(
                spec_fna,
                new ArrayList<>(Collections.emptyList())
        );
        votes.put(
                spec_fnb,
                new ArrayList<>(Arrays.asList(
                        createVoter("aa"),
                        createVoter("bb")
                ))
        );

        election = new ABICallElection(authorizer, votes);
    }

    @Test
    void emptyVotesConstructor() {
        ABICallElection electionBis = new ABICallElection(authorizer);
        Assertions.assertEquals(0, electionBis.getVotes().size());
    }

    @Test
    void getVotes() {
        Assertions.assertSame(votes, election.getVotes());
    }

    @Test
    void clear() {
        election.clear();
        Assertions.assertEquals(0, election.getVotes().size());
    }

    @Test
    void vote_unauthorized() {
        ABICallSpec spec_fnc = new ABICallSpec("fn-c", new byte[][]{});
        Assertions.assertFalse(election.vote(spec_fnc, createVoter("112233")));
        Assertions.assertEquals(2, election.getVotes().size());
        Assertions.assertEquals(0, election.getVotes().get(spec_fna).size());
        Assertions.assertEquals(2, election.getVotes().get(spec_fnb).size());
        Assertions.assertNull(election.getVotes().get(spec_fnc));
    }

    @Test
    void vote_alreadyVoted() {
        Assertions.assertFalse(election.vote(spec_fnb, createVoter("aa")));
        Assertions.assertFalse(election.vote(spec_fnb, createVoter("bb")));
        Assertions.assertEquals(2, election.getVotes().size());
        Assertions.assertEquals(0, election.getVotes().get(spec_fna).size());
        Assertions.assertEquals(2, election.getVotes().get(spec_fnb).size());
    }

    @Test
    void vote_newFn() {
        ABICallSpec spec_fnc = new ABICallSpec("fn-c", new byte[][]{ Hex.decode("44") });
        Assertions.assertTrue(election.vote(spec_fnc, createVoter("dd")));
        Assertions.assertTrue(election.vote(spec_fnc, createVoter("ee")));
        Assertions.assertEquals(3, election.getVotes().size());
        Assertions.assertEquals(0, election.getVotes().get(spec_fna).size());
        Assertions.assertEquals(2, election.getVotes().get(spec_fnb).size());
        Assertions.assertEquals(Arrays.asList(createVoter("dd"), createVoter("ee")), election.getVotes().get(spec_fnc));
    }

    @Test
    void vote_existingFn() {
        Assertions.assertTrue(election.vote(spec_fna, createVoter("cc")));
        Assertions.assertTrue(election.vote(spec_fna, createVoter("dd")));
        Assertions.assertEquals(2, election.getVotes().size());
        Assertions.assertEquals(2, election.getVotes().get(spec_fna).size());
        Assertions.assertEquals(2, election.getVotes().get(spec_fnb).size());
        Assertions.assertEquals(Arrays.asList(createVoter("cc"), createVoter("dd")), election.getVotes().get(spec_fna));
        Assertions.assertEquals(Arrays.asList(createVoter("aa"), createVoter("bb")), election.getVotes().get(spec_fnb));
    }

    @Test
    void getWinnerAndClearWinners_existingFn() {
        Assertions.assertNull(election.getWinner());
        Assertions.assertTrue(election.vote(spec_fnb, createVoter("ee")));
        Assertions.assertEquals(spec_fnb, election.getWinner());

        election.clearWinners();

        Assertions.assertNull(election.getWinner());
        Assertions.assertEquals(1, election.getVotes().size());
        Assertions.assertEquals(Collections.emptyList(), election.getVotes().get(spec_fna));
    }

    @Test
    void getWinnerAndClearWinners_newFn() {
        ABICallSpec spec_fnc = new ABICallSpec("fn-c", new byte[][]{ Hex.decode("44") });
        Assertions.assertNull(election.getWinner());
        Assertions.assertTrue(election.vote(spec_fnc, createVoter("ee")));
        Assertions.assertNull(election.getWinner());
        Assertions.assertTrue(election.vote(spec_fnc, createVoter("cc")));
        Assertions.assertNull(election.getWinner());
        Assertions.assertTrue(election.vote(spec_fnc, createVoter("aa")));
        Assertions.assertEquals(spec_fnc, election.getWinner());

        election.clearWinners();

        Assertions.assertNull(election.getWinner());
        Assertions.assertEquals(2, election.getVotes().size());
        Assertions.assertEquals(Collections.emptyList(), election.getVotes().get(spec_fna));
        Assertions.assertEquals(Arrays.asList(createVoter("aa"), createVoter("bb")), election.getVotes().get(spec_fnb));
    }

    private RskAddress createVoter(String hex) {
        return new RskAddress(TestUtils.padZeroesLeft(hex, 40));
    }

    private ECKey createMockKeyForAddress(String hex) {
        ECKey mockedKey = mock(ECKey.class);
        when(mockedKey.getAddress()).thenReturn(Hex.decode(TestUtils.padZeroesLeft(hex, 40)));
        return mockedKey;
    }
}
