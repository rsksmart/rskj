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
package co.rsk.rpc.modules.debug;

import co.rsk.net.MessageHandler;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.rpc.Web3Mocks;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.mockito.Mockito.when;


public class DebugModuleImplTest {

    private DebugModuleImpl debugModule;
    private MessageHandler messageHandler;

    @Before
    public void setup(){
        messageHandler = Web3Mocks.getMockMessageHandler();
        debugModule = new DebugModuleImpl(messageHandler);
    }

    @Test
    public void debug_wireProtocolQueueSize_basic() throws IOException {
        String result = debugModule.wireProtocolQueueSize();
        try {
            TypeConverter.JSonHexToLong(result);
        } catch (NumberFormatException e) {
            Assert.fail("This method is not returning a  0x Long");
        }
    }

    @Test
    public void debug_wireProtocolQueueSize_value() throws IOException {
        when(messageHandler.getMessageQueueSize()).thenReturn(5L);
        String result = debugModule.wireProtocolQueueSize();
        try {
            long value = TypeConverter.JSonHexToLong(result);
            Assert.assertEquals(5L, value);
        } catch (NumberFormatException e) {
            Assert.fail("This method is not returning a  0x Long");
        }
    }


}
