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

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.core.RskAddress;
import co.rsk.peg.*;
import co.rsk.peg.federation.FederationMember;
import co.rsk.peg.federation.PendingFederation;
import org.ethereum.core.Repository;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Disabled
class FederationChangeTest extends BridgePerformanceTestCase {
    // regtest constants
    private static final List<ECKey> federationChangeAuthorizedKeys = Arrays.stream(new String[]{
            "auth-a",
            "auth-b",
            "auth-c",
            "auth-d",
            "auth-e",
    }).map(generator -> ECKey.fromPrivate(HashUtil.keccak256(generator.getBytes(StandardCharsets.UTF_8)))).collect(Collectors.toList());

    private ECKey winnerKeyToTry;
    private PendingFederation pendingFederation;
    private BtcECKey votedFederatorPublicKey;

    @Test
    void createFederation() throws VMException {
        ExecutionStats stats = new ExecutionStats("createFederation");
        createFederation_noWinner(200, stats);
        createFederation_winner(200, stats);
        Assertions.assertTrue(BridgePerformanceTest.addStats(stats));
    }

    private void createFederation_noWinner(int times, ExecutionStats stats) throws VMException {
        executeAndAverage(
                "createFederation-noWinner",
                times,
                (int executionIndex) -> Bridge.CREATE_FEDERATION.encode(),
                Helper.buildNoopInitializer(),
                (int executionIndex) -> Helper.buildSendValueTx(getRandomFederationChangeKey(), BigInteger.ZERO),
                Helper.getRandomHeightProvider(10),
                stats
        );
    }

    private void createFederation_winner(int times, ExecutionStats stats) throws VMException {
        executeAndAverage(
                "createFederation-winner",
                times,
                (int executionIndex) -> Bridge.CREATE_FEDERATION.encode(),
                buildInitializer(false, () -> new ABICallSpec("create", new byte[][]{})),
                (int executionIndex) -> Helper.buildSendValueTx(winnerKeyToTry, BigInteger.ZERO),
                Helper.getRandomHeightProvider(10),
                stats
        );
    }

    @Test
    void addFederatorPublicKey() throws VMException {
        ExecutionStats stats = new ExecutionStats("addFederatorPublicKey");
        addFederatorPublicKey_noWinner(200, stats);
        addFederatorPublicKey_winner(200, stats);
        Assertions.assertTrue(BridgePerformanceTest.addStats(stats));
    }

    private void addFederatorPublicKey_noWinner(int times, ExecutionStats stats) throws VMException {
        executeAndAverage(
                "addFederatorPublicKey-noWinner",
                times,
                (int executionIndex) -> Bridge.ADD_FEDERATOR_PUBLIC_KEY.encode(new Object[]{ new BtcECKey().getPubKey() }),
                buildInitializer(true, null),
                (int executionIndex) -> Helper.buildSendValueTx(getRandomFederationChangeKey(), BigInteger.ZERO),
                Helper.getRandomHeightProvider(10),
                stats
        );
    }

    private void addFederatorPublicKey_winner(int times, ExecutionStats stats) throws VMException {
        executeAndAverage(
                "addFederatorPublicKey-winner",
                times,
                (int executionIndex) -> Bridge.ADD_FEDERATOR_PUBLIC_KEY.encode(new Object[]{ votedFederatorPublicKey.getPubKey() }),
                buildInitializer(true, () -> {
                    votedFederatorPublicKey = new BtcECKey();
                    return new ABICallSpec("add", new byte[][]{ votedFederatorPublicKey.getPubKey() });
                }),
                (int executionIndex) -> Helper.buildSendValueTx(winnerKeyToTry, BigInteger.ZERO),
                Helper.getRandomHeightProvider(10),
                stats
        );
    }

    @Test
    void commitFederation() throws VMException {
        ExecutionStats stats = new ExecutionStats("commitFederation");
        commitFederation_noWinner(200, stats);
        commitFederation_winner(200, stats);
        Assertions.assertTrue(BridgePerformanceTest.addStats(stats));
    }

    private void commitFederation_noWinner(int times, ExecutionStats stats) throws VMException {
        executeAndAverage(
                "commitFederation-noWinner",
                times,
                (int executionIndex) -> Bridge.COMMIT_FEDERATION.encode(new Object[]{ pendingFederation.getHash().getBytes() }),
                buildInitializer(true, null),
                (int executionIndex) -> Helper.buildSendValueTx(getRandomFederationChangeKey(), BigInteger.ZERO),
                Helper.getRandomHeightProvider(10),
                stats
        );
    }

    private void commitFederation_winner(int times, ExecutionStats stats) throws VMException {
        executeAndAverage(
                "commitFederation-winner",
                times,
                (int executionIndex) -> Bridge.COMMIT_FEDERATION.encode(new Object[]{ pendingFederation.getHash().getBytes() }),
                buildInitializer(true, () -> new ABICallSpec("commit", new byte[][]{ pendingFederation.getHash().getBytes() })),
                (int executionIndex) -> Helper.buildSendValueTx(winnerKeyToTry, BigInteger.ZERO),
                Helper.getRandomHeightProvider(10),
                stats
        );
    }

    @Test
    void rollbackFederation() throws VMException {
        ExecutionStats stats = new ExecutionStats("rollbackFederation");
        rollbackFederation_noWinner(200, stats);
        rollbackFederation_winner(200, stats);
        Assertions.assertTrue(BridgePerformanceTest.addStats(stats));
    }

    private void rollbackFederation_noWinner(int times, ExecutionStats stats) throws VMException {
        executeAndAverage(
                "rollbackFederation-noWinner",
                times,
                (int executionIndex) -> Bridge.ROLLBACK_FEDERATION.encode(new Object[]{}),
                buildInitializer(true, null),
                (int executionIndex) -> Helper.buildSendValueTx(getRandomFederationChangeKey(), BigInteger.ZERO),
                Helper.getRandomHeightProvider(10),
                stats
        );
    }

    private void rollbackFederation_winner(int times, ExecutionStats stats) throws VMException {
        executeAndAverage(
                "rollbackFederation-winner",
                times,
                (int executionIndex) -> Bridge.ROLLBACK_FEDERATION.encode(new Object[]{}),
                buildInitializer(true, () -> new ABICallSpec("rollback", new byte[][]{})),
                (int executionIndex) -> Helper.buildSendValueTx(winnerKeyToTry, BigInteger.ZERO),
                Helper.getRandomHeightProvider(10),
                stats
        );
    }

    private interface ABICallSpecGenerator {
        ABICallSpec generate();
    }

    private BridgeStorageProviderInitializer buildInitializer(boolean generatePendingFederation, ABICallSpecGenerator specToVoteGenerator) {
        return (BridgeStorageProvider provider, Repository repository, int executionIndex, BtcBlockStore blockStore) -> {
            if (generatePendingFederation) {
                final int minKeys = 10;
                final int maxKeys = 16;
                int numKeys = Helper.randomInRange(minKeys, maxKeys);

                List<FederationMember> pendingFederationMembers = new ArrayList<>();
                for (int i = 0; i < numKeys; i++) {
                    pendingFederationMembers.add(new FederationMember(new BtcECKey(), new ECKey(), new ECKey()));
                }
                pendingFederation = new PendingFederation(pendingFederationMembers);
                provider.setPendingFederation(pendingFederation);
            }

            ABICallElection election = provider.getFederationElection(bridgeConstants.getFederationChangeAuthorizer());

            List<ECKey> shuffledKeys = federationChangeAuthorizedKeys.stream().limit(3).collect(Collectors.toList());
            Collections.shuffle(shuffledKeys);

            int votes = 2;

            if (specToVoteGenerator != null) {
                ABICallSpec specToVote = specToVoteGenerator.generate();
                for (int i = 0; i < votes; i++) {
                    election.vote(specToVote, new RskAddress(shuffledKeys.get(i).getAddress()));
                }
            }

            winnerKeyToTry = shuffledKeys.get(votes);
        };
    }

    private ECKey getRandomFederationChangeKey() {
        return federationChangeAuthorizedKeys.get(Helper.randomInRange(0, federationChangeAuthorizedKeys.size()-1));
    }
}
