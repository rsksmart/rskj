/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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
package co.rsk.rpc.modules.eth.subscribe;

public class SyncStatusNotification {
    private final long startingBlock;
    private final long currentBlock;
    private final long highestBlock;

    public SyncStatusNotification(long startingBlock, long currentBlock, long highestBlock) {
        this.startingBlock = startingBlock;
        this.currentBlock = currentBlock;
        this.highestBlock = highestBlock;
    }

    public long getStartingBlock() {
        return startingBlock;
    }

    public long getCurrentBlock() {
        return currentBlock;
    }

    public long getHighestBlock() {
        return highestBlock;
    }
}
