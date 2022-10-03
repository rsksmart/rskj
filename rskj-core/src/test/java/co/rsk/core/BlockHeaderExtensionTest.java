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

package co.rsk.core;

import org.ethereum.core.BlockHeaderExtension;
import org.ethereum.core.Bloom;
import org.junit.Test;
import static org.junit.Assert.*;

public class BlockHeaderExtensionTest {
    @Test
    public void hasHeaderVersion() {
        BlockHeaderExtension extension = new BlockHeaderExtension(1, new Bloom().getData());
        assertEquals(1, extension.getHeaderVersion());
    }

    @Test
    public void hasLogsBloom() {
        byte[] logsBloom = new byte[]{ 1, 2, 3, 4 };
        BlockHeaderExtension extension = new BlockHeaderExtension(1, logsBloom);

        assertArrayEquals(logsBloom, extension.getLogsBloom());
    }
}
