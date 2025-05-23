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

import co.rsk.config.RskSystemProperties;
import org.ethereum.net.eth.EthVersion;
import org.ethereum.net.p2p.HelloMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.ethereum.net.client.Capability.RSK;
import static org.ethereum.net.client.Capability.SNAP;
import static org.ethereum.net.client.Capability.SNAP_VERSION;
import static org.ethereum.net.eth.EthVersion.fromCode;

/**
 * Created by Anton Nashatyrev on 13.10.2015.
 */
public class ConfigCapabilitiesImpl implements ConfigCapabilities{

    private final RskSystemProperties config;

    private final SortedSet<Capability> allCapabilities = new TreeSet<>();

    public ConfigCapabilitiesImpl(RskSystemProperties config) {
        if (config.syncVersion() != null) {
            EthVersion eth = fromCode(config.syncVersion());
            if (eth != null) {
                allCapabilities.add(new Capability(RSK, eth.getCode()));
            }
        } else {
            for (EthVersion v : EthVersion.supported()) {
                allCapabilities.add(new Capability(RSK, v.getCode()));
            }
        }

        if (allCapabilities.stream().anyMatch(Capability::isRSK)) {
            allCapabilities.add(new Capability(SNAP, SNAP_VERSION));
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
        for (Capability capability : allCapabilities) {
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
        if (config.isClientSnapshotSyncEnabled()) {
            configCaps.add(new Capability(Capability.SNAP, Capability.SNAP_VERSION));
        }

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
