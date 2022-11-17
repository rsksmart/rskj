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

public class MessageVersionValidator {

    private final IntSupplier versionSupplier;
    private final Supplier<BlockDifficulty> difficultySupplier;

    public MessageVersionValidator(ActivationConfig activationConfig, StatusResolver statusResolver) {
        this.versionSupplier = () -> {
            long bestBlock = statusResolver.currentStatusLenient().getBestBlockNumber();
            return activationConfig.getMessageVersionForHeight(bestBlock);
        };

        this.difficultySupplier = () -> statusResolver.currentStatusLenient().getTotalDifficulty();
    }

    boolean versionDifferentFromLocal(Integer peerVersion) {
        int localVersion = versionSupplier.getAsInt();
        return isVersionCheckEnabled(localVersion) && peerVersion != localVersion;
    }

    boolean versionLowerThanLocal(Integer peerVersion) {
        int localVersion = versionSupplier.getAsInt();
        return isVersionCheckEnabled(localVersion) && peerVersion < localVersion;
    }

    boolean versionHigherThanLocal(Integer peerVersion) {
        int localVersion = versionSupplier.getAsInt();
        return isVersionCheckEnabled(localVersion) && peerVersion > localVersion;
    }

    boolean localVersionIsLowerButDifficultyIsNot(Integer peerVersion, BlockDifficulty peerDifficulty) {
        BlockDifficulty localDifficulty = difficultySupplier.get();
        return versionHigherThanLocal(peerVersion) && peerDifficulty.compareTo(localDifficulty) <= 0;
    }

    boolean localVersionIsHigherButDifficultyIsNot(Integer peerVersion, BlockDifficulty peerDifficulty) {
        BlockDifficulty localDifficulty = difficultySupplier.get();
        return versionLowerThanLocal(peerVersion) && peerDifficulty.compareTo(localDifficulty) >= 0;
    }

    private static boolean isVersionCheckEnabled(int localVersion) {
        return localVersion != -1; // TODO(iago) constant
    }
}
