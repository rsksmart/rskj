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

package co.rsk.trie;

import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.EthereumListener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.mockito.Mockito.*;

public class GarbageCollectorTest {
    private CompositeEthereumListener emitter;
    private GarbageCollector collector;

    private EthereumListener listener;

    @Before
    public void setUp() {
        emitter = mock(CompositeEthereumListener.class);
        collector = new GarbageCollector(emitter);
        collector.start();

        ArgumentCaptor<EthereumListener> argument = ArgumentCaptor.forClass(EthereumListener.class);
        verify(emitter, times(1)).addListener(argument.capture());
        listener = argument.getValue();
    }

    @After
    public void tearDown() {
        verify(emitter, times(0)).removeListener(listener);
        collector.stop();
        verify(emitter, times(1)).removeListener(listener);
    }

    @Test
    public void onBlock() {
        collector.hey();
    }
}