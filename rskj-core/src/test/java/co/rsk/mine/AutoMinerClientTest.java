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

package co.rsk.mine;

import co.rsk.bitcoinj.core.BtcBlock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;

public class AutoMinerClientTest {

    private AutoMinerClient autoMinerClient;
    private MinerServer minerServer;

    @BeforeEach
    public void setUp() {
        minerServer = mock(MinerServer.class);
        autoMinerClient = new AutoMinerClient(minerServer);
    }

    @Test
    public void byDefaultIsDisabled() {
        assertThat(autoMinerClient.isMining(), is(false));
    }

    @Test
    public void minesBlock() {
        MinerWork work = mock(MinerWork.class);
        when(work.getBlockHashForMergedMining()).thenReturn("0x404142");
        when(work.getTarget()).thenReturn("0x10000000000000000000000000000000000000000000000000000000000000");
        when(minerServer.getWork()).thenReturn(work);
        autoMinerClient.start();

        assertThat(autoMinerClient.isMining(), is(true));
        assertThat(autoMinerClient.mineBlock(), is(true));
        verify(minerServer, times(1)).submitBitcoinBlock(eq(work.getBlockHashForMergedMining()), any(BtcBlock.class));
    }

    @Test
    public void disablesMining() {
        autoMinerClient.start();
        autoMinerClient.stop();

        assertThat(autoMinerClient.isMining(), is(false));
        assertThat(autoMinerClient.mineBlock(), is(false));
    }
}
