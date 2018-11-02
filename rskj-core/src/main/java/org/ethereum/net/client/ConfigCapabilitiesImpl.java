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

package org.ethereum.net.client;

import org.ethereum.config.SystemProperties;
import org.ethereum.net.eth.EthVersion;
import org.ethereum.net.p2p.HelloMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.ethereum.net.client.Capability.*;
import static org.ethereum.net.eth.EthVersion.fromCode;

/**
 * Created by Anton Nashatyrev on 13.10.2015.
 */
@Component("configCapabilities")
public class ConfigCapabilitiesImpl implements ConfigCapabilities{

    private final SystemProperties config;

    private SortedSet<Capability> allCaps = new TreeSet<>();

    @Autowired
    public ConfigCapabilitiesImpl(SystemProperties config) {
        if (config.syncVersion() != null) {
            EthVersion eth = fromCode(config.syncVersion());
            if (eth != null) {
                allCaps.add(new Capability(RSK, eth.getCode()));
            }
        } else {
            for (EthVersion v : EthVersion.supported()) {
                allCaps.add(new Capability(RSK, v.getCode()));
            }
        }
        this.config = config;
    }

    /**
     * Gets the capabilities listed in 'peer.capabilities' config property
     * sorted by their names.
     */
    public List<Capability> getConfigCapabilities() {
        List<Capability> ret = new ArrayList<>();
        List<String> caps = config.peerCapabilities();
        for (Capability capability : allCaps) {
            if (caps.contains(capability.getName())) {
                ret.add(capability);
            }
        }
        return ret;
    }

    /**
     * Returns the node's supported capabilities for this hello message
     */
    @Override
    public List<Capability> getSupportedCapabilities(HelloMessage hello) {
        List<Capability> configCaps = getConfigCapabilities();
        List<Capability> supported = new ArrayList<>();

        List<Capability> eths = new ArrayList<>();

        for (Capability cap : hello.getCapabilities()) {
            if (configCaps.contains(cap)) {
                if (cap.isRSK()) {
                    eths.add(cap);
                } else {
                    supported.add(cap);
                }
            }
        }

        if (eths.isEmpty()) {
            return supported;
        }

        // we need to pick up
        // the most recent Eth version
        Capability highest = null;
        for (Capability eth : eths) {
            if (highest == null || highest.getVersion() < eth.getVersion()) {
                highest = eth;
            }
        }

        supported.add(highest);
        return supported;
    }

}
