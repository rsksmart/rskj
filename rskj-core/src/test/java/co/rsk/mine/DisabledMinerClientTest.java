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

import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class DisabledMinerClientTest {

    private DisabledMinerClient disabledMinerClient;

    @Before
    public void setUp() {
        disabledMinerClient = new DisabledMinerClient();
    }

    @Test
    public void byDefaultIsDisabled() {
        assertThat(disabledMinerClient.isMining(), is(false));
        assertThat(disabledMinerClient.mineBlock(), is(false));
    }

    @Test
    public void startDoesntActuallyEnableIt() {
        disabledMinerClient.start();

        assertThat(disabledMinerClient.isMining(), is(false));
        assertThat(disabledMinerClient.mineBlock(), is(false));
    }

    @Test
    public void stillDisabledAfterStop() {
        disabledMinerClient.start();
        disabledMinerClient.stop();

        assertThat(disabledMinerClient.isMining(), is(false));
        assertThat(disabledMinerClient.mineBlock(), is(false));
    }
}