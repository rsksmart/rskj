/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.db;

import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockWrapper;

import java.util.Collection;
import java.util.List;

/**
 * @author Mikhail Kalinin
 * @since 09.07.2015
 */
public interface BlockQueue extends DiskStore {

    void addOrReplaceAll(Collection<BlockWrapper> blockList);

    void add(BlockWrapper block);

    void returnBlock(BlockWrapper block);

    void addOrReplace(BlockWrapper block);

    BlockWrapper poll();

    BlockWrapper peek();

    BlockWrapper take();

    int size();

    boolean isEmpty();

    void clear();

    List<byte[]> filterExisting(Collection<byte[]> hashes);

    List<BlockHeader> filterExistingHeaders(Collection<BlockHeader> headers);

    boolean isBlockExist(byte[] hash);

    void drop(byte[] nodeId, int scanLimit);

    long getLastNumber();

    BlockWrapper peekLast();

    void remove(BlockWrapper block);
}
