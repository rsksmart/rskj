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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DebugModuleImpl implements DebugModule {

    private final MessageHandler messageHandler;

    @Autowired
    public DebugModuleImpl(MessageHandler messageHandler) {
        this.messageHandler = messageHandler;
    }

    @Override
    public String wireProtocolQueueSize() {
        long n = messageHandler.getMessageQueueSize();
        return TypeConverter.toJsonHex(n);
    }
}
