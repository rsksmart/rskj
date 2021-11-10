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
package co.rsk.net.sync;

import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.BlockIdentifier;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Optional;

public class ChunksDownloadHelper {
    private SyncConfiguration syncConfiguration;

    // Block identifiers retrieved in skeleton
    private List<BlockIdentifier> skeleton;
    private long connectionPoint;
    private int lastRequestedLinkIndex;

    public ChunksDownloadHelper(@Nonnull SyncConfiguration syncConfiguration, List<BlockIdentifier> skeleton, long connectionPoint) {
        this.syncConfiguration = syncConfiguration;
        this.connectionPoint = connectionPoint;
        this.lastRequestedLinkIndex = 0;
        this.skeleton = skeleton;
    }

    public boolean hasNextChunk() {
        int linkIndex = this.lastRequestedLinkIndex + 1;
        return linkIndex < skeleton.size() && linkIndex <= syncConfiguration.getMaxSkeletonChunks();
    }

    public Optional<ChunkDescriptor> getCurrentChunk() {
        return Optional.of(getChunk(this.lastRequestedLinkIndex));
    }

    public ChunkDescriptor getNextChunk() {
        // We use 0 so we start iterarting from the second element,
        // because we always have the first element in our blockchain
        return getChunk(this.lastRequestedLinkIndex + 1);
    }

    private ChunkDescriptor getChunk(int linkIndex) {
        byte[] hash = skeleton.get(linkIndex).getHash();
        long height = skeleton.get(linkIndex).getNumber();

        long lastHeight = skeleton.get(linkIndex - 1).getNumber();
        long previousKnownHeight = Math.max(lastHeight, connectionPoint);
        int count = (int)(height - previousKnownHeight);
        this.lastRequestedLinkIndex = linkIndex;

        return new ChunkDescriptor(hash, count);
    }

    @VisibleForTesting
    public List<BlockIdentifier> getSkeleton() {
        return this.skeleton;
    }
}
