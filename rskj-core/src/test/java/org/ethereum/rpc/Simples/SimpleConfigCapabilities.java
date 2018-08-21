/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package org.ethereum.rpc.Simples;

import org.ethereum.net.client.Capability;
import org.ethereum.net.client.ConfigCapabilities;
import org.ethereum.net.p2p.HelloMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Ruben Altman on 09/06/2016.
 */
public class SimpleConfigCapabilities implements ConfigCapabilities {

    @Override
    public List<Capability> getConfigCapabilities() {
        List<Capability> capabilities = new ArrayList<>();

        capabilities.add(new Capability("rsk", (byte)1));

        return capabilities;
    }

    @Override
    public List<Capability> getSupportedCapabilities(HelloMessage hello) {
        return getConfigCapabilities();
    }
}
