/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

package co.rsk.db;

/**
 * Created by ajlopez on 23/04/2018.
 */
public class PruneConfiguration {
    private final long noBlocksToCopy;
    private final long noBlocksToAvoidForks;
    private final long noBlocksToWait;

    public PruneConfiguration(long noBlocksToCopy, long noBlocksToAvoidForks, long noBlocksToWait) {
        this.noBlocksToCopy = noBlocksToCopy;
        this.noBlocksToAvoidForks = noBlocksToAvoidForks;
        this.noBlocksToWait = noBlocksToWait;
    }

    public long getNoBlocksToCopy() {
        return this.noBlocksToCopy;
    }

    public long getNoBlocksToAvoidForks() {
        return this.noBlocksToAvoidForks;
    }

    public long getNoBlocksToWait() {
        return this.noBlocksToWait;
    }
}
