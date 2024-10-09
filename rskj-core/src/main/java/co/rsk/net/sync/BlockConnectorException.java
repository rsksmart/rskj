/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
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

public class BlockConnectorException extends RuntimeException {
    private final long blockNumber;
    private final long childBlockNumber;

    public BlockConnectorException(final long blockNumber, final long childBlockNumber) {
        super(String.format("Block with number %s is not child's (%s) parent.", blockNumber, childBlockNumber));
        this.blockNumber = blockNumber;
        this.childBlockNumber = childBlockNumber;
    }

    public long getBlockNumber() {
        return blockNumber;
    }

    public long getChildBlockNumber() {
        return childBlockNumber;
    }
}
