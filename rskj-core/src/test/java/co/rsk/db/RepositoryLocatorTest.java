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

import co.rsk.crypto.Keccak256;
import org.ethereum.TestUtils;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Repository;
import org.junit.Test;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RepositoryLocatorTest {
    @Test
    public void getsSnapshotFromTranslatedStateRoot() {
        Repository repository = mock(Repository.class);
        StateRootHandler stateRootHandler = mock(StateRootHandler.class);

        BlockHeader header = mock(BlockHeader.class);
        Keccak256 stateRoot = TestUtils.randomHash();
        when(stateRootHandler.translate(header)).thenReturn(stateRoot);
        Repository expectedRepository = mock(Repository.class);
        when(repository.getSnapshotTo(stateRoot.getBytes())).thenReturn(expectedRepository);

        RepositoryLocator repositoryLocator = new RepositoryLocator(repository, stateRootHandler);
        assertSame(expectedRepository, repositoryLocator.snapshotAt(header));
    }
}
