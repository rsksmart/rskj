/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.net.eth.handler;

import co.rsk.net.eth.RskWireProtocol;
import org.ethereum.net.eth.EthVersion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Default factory implementation
 *
 * @author Mikhail Kalinin
 * @since 20.08.2015
 */
@Component
public class EthHandlerFactoryImpl implements EthHandlerFactory {

    private final RskWireProtocolFactory rskWireProtocolFactory;

    @Autowired
    public EthHandlerFactoryImpl(RskWireProtocolFactory rskWireProtocolFactory) {
        this.rskWireProtocolFactory = rskWireProtocolFactory;
    }

    @Override
    public EthHandler create(EthVersion version) {
        switch (version) {
            case V62:
                return rskWireProtocolFactory.newInstance();

            default:    throw new IllegalArgumentException("Eth " + version + " is not supported");
        }
    }

    public interface RskWireProtocolFactory {
        RskWireProtocol newInstance();
    }
}
