/*
 * This file is part of RskJ
 * Copyright (C) 2020 RSK Labs Ltd.
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

package co.rsk.net.light.state;

import co.rsk.net.light.LightPeer;
import co.rsk.net.light.LightSyncProcessor;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.BlockHeader;

import java.util.ArrayList;
import java.util.List;

public class StartRoundSyncState implements LightSyncState {
    private final List<BlockHeader> sparseHeaders;
    private final int pivots;
    private final int skip;
    private final LightSyncProcessor lightSyncProcessor;
    private final LightPeer lightPeer;
    private final BlockHeader start;
    private int maxAmountOfHeaders;
    private long startBlockNumber;


    public StartRoundSyncState(LightSyncProcessor lightSyncProcessor, LightPeer lightPeer, BlockHeader start, long targetNumber) {
        this.lightSyncProcessor = lightSyncProcessor;
        this.lightPeer = lightPeer;
        this.start = start;
        this.sparseHeaders = new ArrayList<>();
        final RoundSyncHelper roundSyncHelper = new RoundSyncHelper(targetNumber - start.getNumber());
        this.pivots = roundSyncHelper.getPivots();
        this.skip = roundSyncHelper.getSkip();
    }

    @Override
    public void sync() {
        this.startBlockNumber = this.start.getNumber() + 1 + Math.multiplyExact(this.sparseHeaders.size(), this.skip);
        this.maxAmountOfHeaders = this.pivots - this.sparseHeaders.size();
        lightSyncProcessor.sendBlockHeadersByNumberMessage(lightPeer, this.startBlockNumber, maxAmountOfHeaders, skip, false);
    }

    @Override
    public void newBlockHeaders(LightPeer lightPeer, List<BlockHeader> blockHeaders) {
        if (blockHeaders.size() > maxAmountOfHeaders) {
            lightSyncProcessor.moreBlocksThanAllowed();
            //TODO: Abort process
            return;
        }

        if (blockHeaders.get(0).getNumber() != this.startBlockNumber) {
            lightSyncProcessor.differentFirstBlocks();
            //TODO: Abort process
            return;
        }

        if (!isCorrectSkipped(blockHeaders)){
            lightSyncProcessor.incorrectSkipped();
            //TODO: Abort process
            return;
        }

        if (sparseHeaders.isEmpty() && !blockHeaders.get(0).getParentHash().equals(start.getHash())) {
            lightSyncProcessor.incorrectParentHash();
            //TODO: Abort process
            return;
        }

        sparseHeaders.addAll(blockHeaders);

        if (sparseHeaders.size() == pivots) {
            if (skip == 0) {
                lightSyncProcessor.endStartRound();
            } else {
                lightSyncProcessor.startFetchRound();
            }
        } else {
            lightSyncProcessor.failedAttempt();
        }
    }

    private boolean isCorrectSkipped(List<BlockHeader> blockHeaders) {
        for (int i = 0; i < blockHeaders.size() - 1; i++) {
            final long low = blockHeaders.get(i).getNumber();
            final long high = blockHeaders.get(i+1).getNumber();
            if (low >= high) {
                return false;
            } else {
                if (high - low - 1 != skip) {
                    return false;
                }
            }
        }
        return true;
    }

    @VisibleForTesting
    private static class RoundSyncHelper {
        private final int skip;
        private final int pivots;

        //Default parameters
        private static final int ROUND_SKIP = 192; //Distance between pivots without counting pivots
        private static final int ROUND_PIVOTS = 20; //Number of pivots

        public RoundSyncHelper(long diff) {
            int remain = Math.toIntExact(diff % (ROUND_SKIP + 1));
            if (diff <= ROUND_SKIP) {
                skip = 0;
                pivots = remain;
            } else {
                long pivotsToTarget = Math.floorDiv(diff, (long) ROUND_SKIP + 1); //How many pivots peer needs to get the target
                pivotsToTarget += remain == 0? 0 : 1; //If there is a remain, one pivot else is needed.
                skip = ROUND_SKIP;
                pivots = (int) Math.min(pivotsToTarget, ROUND_PIVOTS);
            }
        }

        int getSkip() {
            return skip;
        }

        int getPivots() {
            return pivots;
        }
    }
}
