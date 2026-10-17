/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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
package co.rsk.cli.tools;

import co.rsk.crypto.Keccak256;
import co.rsk.db.BlocksIndex;
import org.ethereum.db.IndexedBlockStore;

import java.util.List;

/**
 * A {@link BlocksIndex} that can be read but not changed.
 *
 * <p>The block store flushes its index when it is closed. On an index opened read-only that flush
 * fails, which would turn an ordinary shutdown into an error after a replay had already succeeded.
 * There is nothing buffered to write, so the flush is simply dropped; anything that would actually
 * modify the index is refused.
 */
public class ReadOnlyBlocksIndex implements BlocksIndex {

    private final BlocksIndex delegate;

    public ReadOnlyBlocksIndex(BlocksIndex delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public long getMaxNumber() {
        return delegate.getMaxNumber();
    }

    @Override
    public long getMinNumber() {
        return delegate.getMinNumber();
    }

    @Override
    public boolean contains(long blockNumber) {
        return delegate.contains(blockNumber);
    }

    @Override
    public List<IndexedBlockStore.BlockInfo> getBlocksByNumber(long blockNumber) {
        return delegate.getBlocksByNumber(blockNumber);
    }

    @Override
    public void putBlocks(long blockNumber, List<IndexedBlockStore.BlockInfo> blocks) {
        throw new UnsupportedOperationException("The block index was opened read-only");
    }

    @Override
    public void removeBlock(long blockNumber, Keccak256 blockHash) {
        throw new UnsupportedOperationException("The block index was opened read-only");
    }

    @Override
    public List<IndexedBlockStore.BlockInfo> removeLast() {
        throw new UnsupportedOperationException("The block index was opened read-only");
    }

    @Override
    public void flush() {
        // Nothing was buffered for writing.
    }

    @Override
    public void close() {
        delegate.close();
    }
}
