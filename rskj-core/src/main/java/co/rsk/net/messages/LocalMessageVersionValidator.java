/*
 * This file is part of RskJ
 * Copyright (C) 2022 RSK Labs Ltd.
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

package co.rsk.net.messages;

import co.rsk.core.BlockDifficulty;
import co.rsk.net.StatusResolver;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class LocalMessageVersionValidator { // TODO(iago:2) rename, it is not only validator

    public static final int DISABLED_VERSION = -1;

    private final IntSupplier versionSupplier;
    private final Supplier<BlockDifficulty> difficultySupplier;

    public LocalMessageVersionValidator(ActivationConfig activationConfig, StatusResolver statusResolver) {
        this.versionSupplier = () -> {
            // TODO(iago:3) is it a good idea to use this status for the check? how up to date is it?
            long bestBlock = statusResolver.currentStatusLenient().getBestBlockNumber();
            return activationConfig.getMessageVersionForHeight(bestBlock);
        };
        // TODO(iago) is it better to use difficulty or blockNumber?
        this.difficultySupplier = () -> statusResolver.currentStatusLenient().getTotalDifficulty();
    }

    // TODO(iago:1) rethink this name, what about backward sync?
    public boolean notValidForLongSync(int peerVersion, BlockDifficulty peerDifficulty) {
        // peer version is higher but difficulty lower, not valid even for us to long sync
        if (localVersionIsLowerButDifficultyIsNot(peerVersion, peerDifficulty)) {
            return true;
        }

        // peer version is lower but difficulty higher, not valid even for peer to long sync with us
        if (localVersionIsHigherButDifficultyIsNot(peerVersion, peerDifficulty)) {
            return true;
        }

        // accept only if:
        // a) same message version
        // b) peer lower version and lower difficulty to allow it long sync with us
        // c) peer greater version and greater difficulty to allow us full sync with it
        return false;
    }

    boolean versionDifferentFromLocal(Integer peerVersion) {
        int localVersion = versionSupplier.getAsInt();
        return isVersioningEnabledFor(localVersion) && peerVersion != localVersion;
    }

    boolean versionLowerThanLocal(Integer peerVersion) {
        int localVersion = versionSupplier.getAsInt();
        return isVersioningEnabledFor(localVersion) && peerVersion < localVersion;
    }

    boolean versionHigherThanLocal(Integer peerVersion) {
        int localVersion = versionSupplier.getAsInt();
        return isVersioningEnabledFor(localVersion) && peerVersion > localVersion;
    }

    private boolean localVersionIsLowerButDifficultyIsNot(Integer peerVersion, BlockDifficulty peerDifficulty) {
        BlockDifficulty localDifficulty = difficultySupplier.get();
        return versionHigherThanLocal(peerVersion) && peerDifficulty.compareTo(localDifficulty) <= 0;
    }

    private boolean localVersionIsHigherButDifficultyIsNot(Integer peerVersion, BlockDifficulty peerDifficulty) {
        BlockDifficulty localDifficulty = difficultySupplier.get();
        return versionLowerThanLocal(peerVersion) && peerDifficulty.compareTo(localDifficulty) >= 0;
    }

    public static boolean isVersioningEnabledFor(int versionToCheck) {
        return versionToCheck != DISABLED_VERSION;
    }

    public int getLocalVersion() {
        return this.versionSupplier.getAsInt();
    }

}
