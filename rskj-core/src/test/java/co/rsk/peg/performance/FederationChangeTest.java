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

package co.rsk.peg.performance;

import co.rsk.peg.*;
import org.ethereum.core.Repository;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.junit.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class FederationChangeTest extends BridgePerformanceTestCase {
    private PendingFederation pendingFederation;

    // regtest constants
    private static final List<ECKey> federationChangeAuthorizedKeys = Arrays.stream(new String[]{
            "auth-a",
            "auth-b",
            "auth-c",
            "auth-d",
            "auth-e",
    }).map(generator -> ECKey.fromPrivate(HashUtil.sha3(generator.getBytes(StandardCharsets.UTF_8)))).collect(Collectors.toList());

    @Test
    public void createFederation() {
        ExecutionStats stats = new ExecutionStats("createFederation");
        createFederation_noWinner_noVotes(200, stats);
        createFederation_noWinner_votes(200, stats);
        createFederation_winner(200, stats);
        BridgePerformanceTest.addStats(stats);
    }

    private void createFederation_noWinner_noVotes(int times, ExecutionStats stats) {
        executeAndAverage(
                "createFederation-noWinner-noVotes",
                times,
                (int executionIndex) -> Bridge.CREATE_FEDERATION.encode(),
                Helper.buildNoopInitializer(),
                (int executionIndex) -> {
                    ECKey key = federationChangeAuthorizedKeys.get(Helper.randomInRange(0, federationChangeAuthorizedKeys.size()-1));
                    return Helper.buildSendValueTx(key, BigInteger.ZERO);
                },
                Helper.getRandomHeightProvider(10),
                stats
        );
    }

    private void createFederation_noWinner_votes(int times, ExecutionStats stats) {
        executeAndAverage(
                "createFederation-noWinner-votes",
                times,
                (int executionIndex) -> Bridge.CREATE_FEDERATION.encode(),
                buildCreateFederationInitializer(1),
                (int executionIndex) -> {
                    ECKey key = federationChangeAuthorizedKeys.get(Helper.randomInRange(3, 4));
                    return Helper.buildSendValueTx(key, BigInteger.ZERO);
                },
                Helper.getRandomHeightProvider(10),
                stats
        );
    }

    private void createFederation_winner(int times, ExecutionStats stats) {
        executeAndAverage(
                "createFederation-winner",
                times,
                (int executionIndex) -> Bridge.CREATE_FEDERATION.encode(),
                buildCreateFederationInitializer(2),
                (int executionIndex) -> {
                    ECKey key = federationChangeAuthorizedKeys.get(Helper.randomInRange(3, 4));
                    return Helper.buildSendValueTx(key, BigInteger.ZERO);
                },
                Helper.getRandomHeightProvider(10),
                stats
        );
    }

    private BridgeStorageProviderInitializer buildCreateFederationInitializer(int numVotes) {
        return (BridgeStorageProvider provider, Repository repository, int executionIndex) -> {
            ABICallElection election = provider.getFederationElection(bridgeConstants.getFederationChangeAuthorizer());

            List<ECKey> shuffledKeys = federationChangeAuthorizedKeys.stream().limit(3).collect(Collectors.toList());
            Collections.shuffle(shuffledKeys);

            for (int i = 0; i < numVotes; i++) {
                ABICallSpec callSpec = new ABICallSpec("create", new byte[][]{});
                election.vote(callSpec, new TxSender(shuffledKeys.get(i).getAddress()));
            }
        };
    }


}
