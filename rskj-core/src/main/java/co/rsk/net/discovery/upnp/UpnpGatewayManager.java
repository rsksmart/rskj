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

package co.rsk.net.discovery.upnp;

import org.bitlet.weupnp.GatewayDevice;
import org.bitlet.weupnp.PortMappingEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;

/**
 * Provides a UPnP interface for a particular Internet Gateway Device:
 * <ul>
 *     <li>Add/remove port mappings.</li>
 *     <li>Find the gateway's external IP address.</li>
 * </ul>
 */
public class UpnpGatewayManager {

    private static final Logger logger = LoggerFactory.getLogger(UpnpGatewayManager.class);
    private static final int PORT_MAPPINGS_INITIAL_CAPACITY = 3;

    private final GatewayDevice gateway;
    private final List<PortMappingEntry> portMappings = new ArrayList<>(PORT_MAPPINGS_INITIAL_CAPACITY);

    /**
     * Package-private constructor.  Called by {@link UpnpService#findGateway()}.
     *
     * @param gateway a valid gateway with a WAN connection.
     */
    UpnpGatewayManager(GatewayDevice gateway) {
        this.gateway = gateway;
    }

    /**
     * Gets the external IP address of the gateway.
     *
     * @return the external IP address of the gateway, or empty if failure.
     */
    public Optional<String> getExternalIPAddress() {
        try {
            return Optional.ofNullable(gateway.getExternalIPAddress());
        } catch (Exception e) {
            logger.error("Failed to get external IP address.", e);
            return Optional.empty();
        }
    }

    /**
     * Forwards a port on the gateway to this node.  The port mapping will be deleted
     * on {@link UpnpService#stop()}.
     *
     * @param externalPort the external port to be forwarded.
     * @param internalPort the destination port being forwarded to.
     * @param protocol     the protocol to use.
     * @param description  describes the purpose of this port mapping.
     * @return true if successful.
     */
    public boolean addPortMapping(int externalPort, int internalPort, UpnpProtocol protocol, String description) {
        String strProtocol = protocol.toString();
        String strLocalAddress = gateway.getLocalAddress().getHostAddress();

        if (addPortMapping(externalPort, internalPort, strLocalAddress, strProtocol, description)) {
            logger.info(
                    "Added port mapping of {} port {} to {}:{} for \"{}\".",
                    strProtocol,
                    externalPort,
                    strLocalAddress,
                    internalPort,
                    description
            );
            // saved here for release on service shutdown
            portMappings.add(createPortMappingEntryObject(
                    strProtocol,
                    externalPort,
                    strLocalAddress,
                    internalPort,
                    description
            ));
            return true;
        } else {
            logger.error(getPortForwardingExceptionMessage(
                    strProtocol,
                    externalPort,
                    strLocalAddress,
                    internalPort,
                    description
            ));
            return false;
        }
    }

    private boolean addPortMapping(
            int externalPort,
            int internalPort,
            String strLocalAddress,
            String strProtocol,
            String description) {

        try {
            return gateway.addPortMapping(
                    externalPort,
                    internalPort,
                    strLocalAddress,
                    strProtocol,
                    description
            );
        } catch (Exception e) {
            logger.error(getPortForwardingExceptionMessage(
                    strProtocol,
                    externalPort,
                    strLocalAddress,
                    internalPort,
                    description
            ), e);
            return false;
        }
    }

    private static PortMappingEntry createPortMappingEntryObject(
            String protocol,
            int externalPort,
            String localAddress,
            int internalPort,
            String description) {

        PortMappingEntry pm = new PortMappingEntry();
        pm.setProtocol(protocol);
        pm.setExternalPort(externalPort);
        pm.setInternalClient(localAddress);
        pm.setInternalPort(internalPort);
        pm.setPortMappingDescription(description);
        return pm;
    }

    private static String getPortForwardingExceptionMessage(
            String strProtocol,
            int externalPort,
            String strLocalAddress,
            int internalPort,
            String description) {
        return String.format(
                "Failed to forward %s port %s to %s:%s for %s.",
                strProtocol,
                externalPort,
                strLocalAddress,
                internalPort,
                description
        );
    }

    /**
     * Deletes all port mappings which were created by calls to
     * {@link #addPortMapping(int, int, UpnpProtocol, String)}.
     */
    public void deleteAllPortMappings() {
        ListIterator<PortMappingEntry> iter = portMappings.listIterator();
        while (iter.hasNext()) {
            PortMappingEntry entry = iter.next();
            int externalPort = entry.getExternalPort();
            String protocol = entry.getProtocol();

            if (deletePortMapping(externalPort, protocol)) {
                logger.info(
                        "Deleted port mapping of {} port {}.",
                        protocol,
                        externalPort
                );
                iter.remove();
            }
        }
    }

    private boolean deletePortMapping(int externalPort, String protocol) {
        try {
            return gateway.deletePortMapping(externalPort, protocol);
        } catch (Exception e) {
            logger.error(String.format(
                    "Failed to delete port mapping of %s port %s",
                    protocol,
                    externalPort
            ), e);
            return false;
        }
    }
}
