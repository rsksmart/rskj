/*
 * This file is part of RskJ
 * Copyright (C) 2020 RSK Labs Ltd.
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

package co.rsk.net.eth;

import co.rsk.net.light.*;
import co.rsk.net.light.message.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This is the equivalent to the RSKWireProtocol, but both classes are
 * really Handlers for the communication channel
 */

public class LightClientHandler extends SimpleChannelInboundHandler<LightClientMessage> {
    private static final Logger logger = LoggerFactory.getLogger("lightnet");
    private final LightPeer lightPeer;
    private final LightSyncProcessor lightSyncProcessor;
    private final LightProcessor lightProcessor;

    public LightClientHandler(LightPeer lightPeer, LightProcessor lightProcessor, LightSyncProcessor lightSyncProcessor) {
        this.lightProcessor = lightProcessor;
        this.lightSyncProcessor = lightSyncProcessor;
        this.lightPeer = lightPeer;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, LightClientMessage msg) throws UnsupportedOperationException {
        if (LightClientMessageCodes.inRange(msg.getCommand().asByte())) {
            throw new UnsupportedOperationException("Message not in supported Range");
        }

        MessageVisitor visitor = new MessageVisitor(lightPeer, lightProcessor, lightSyncProcessor, ctx,this);
        msg.accept(visitor);
    }

    public void activate() {
        lightSyncProcessor.sendStatusMessage(lightPeer);
    }

    public interface Factory {
        LightClientHandler newInstance(LightPeer lightPeer);
    }
}
