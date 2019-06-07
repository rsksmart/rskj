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

package co.rsk.db;

import org.ethereum.core.BlockHeader;
import org.ethereum.core.Repository;

public class RepositoryLocator {
    private final Repository repository;
    private final StateRootHandler stateRootHandler;

    public RepositoryLocator(
            Repository repository,
            StateRootHandler stateRootHandler) {
        this.repository = repository;
        this.stateRootHandler = stateRootHandler;
    }

    public Repository snapshotAt(BlockHeader header) {
        byte[] stateRoot = stateRootHandler.translate(header).getBytes();
        return repository.getSnapshotTo(stateRoot);
    }
}
