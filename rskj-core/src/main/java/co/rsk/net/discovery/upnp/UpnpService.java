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

import co.rsk.config.InternalService;
import com.google.common.annotations.VisibleForTesting;
import org.bitlet.weupnp.GatewayDevice;
import org.bitlet.weupnp.GatewayDiscover;
import org.ethereum.config.SystemProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Can query the local network interfaces for UPnP-enabled gateways, and provides an
 * {@link UpnpGatewayManager abstraction} for interacting with those gateways.
 */
public class UpnpService implements InternalService {

    private static final Logger logger = LoggerFactory.getLogger(UpnpService.class);
    private static final String DEVICE_SEARCH_WANIPCONNECTION = "urn:schemas-upnp-org:service:WANIPConnection:1";
    private static final String UPNP_DISABLED_NOTIFICATION = "Ensure that your router has UPnP enabled, " +
            "or disable UPnP in the settings of this node (" + SystemProperties.PROPERTY_PEER_DISCOVERY_UPNP_ENABLED +
            ") and forward ports manually.";
    private static final int DEFAULT_UPNP_TIMEOUT_MILLIS = 5000;
    private static final int DELETE_PORT_MAPPINGS_TIMEOUT_MILLIS = 5000;
    private static final int CACHE_INITIAL_CAPACITY = 4;
    private static final InetAddress localWildcardAddress = new InetSocketAddress(0).getAddress();

    private final GatewayDiscover query;
    private final Map<InetAddress, UpnpGatewayManager> cachedGatewayManagers =
            new HashMap<>(CACHE_INITIAL_CAPACITY);

    /**
     * Default constructor.<br/>
     * Will only find gateways which have {@link #DEVICE_SEARCH_WANIPCONNECTION a valid WAN connection},
     * and uses a UPnP timeout of {@value #DEFAULT_UPNP_TIMEOUT_MILLIS} milliseconds.
     */
    public UpnpService() {
        this(new GatewayDiscover(new String[]{DEVICE_SEARCH_WANIPCONNECTION}), DEFAULT_UPNP_TIMEOUT_MILLIS);
    }

    /**
     * Constructor.
     *
     * @param query   The gateway discoverer to use.
     * @param timeout UPnP messaging timeout, in milliseconds.
     */
    public UpnpService(GatewayDiscover query, int timeout) {
        this.query = query;
        // only available as a static call, but UpnpService is a singleton so it should be fine
        GatewayDevice.setHttpReadTimeout(timeout);
    }

    @Override
    public void start() {
        // empty; device discovery is lazy
    }

    @Override
    public void stop() {
        logger.info("Deleting port mappings...");
        Collection<UpnpGatewayManager> gateways = cachedGatewayManagers.values();
        if (!gateways.isEmpty()) {
            // release port mappings of each gateway concurrently
            ExecutorService executor = Executors.newFixedThreadPool(gateways.size());
            try {
                executor.invokeAll(
                        gateways.stream()
                                .map(gateway -> Executors.callable(gateway::deleteAllPortMappings))
                                .collect(Collectors.toList()),
                        DELETE_PORT_MAPPINGS_TIMEOUT_MILLIS,
                        TimeUnit.MILLISECONDS
                );
            } catch (Exception e) {
                // best effort was made; continue with service shutdown
                logger.warn("Exception caught while waiting for port mappings to be released; ignoring.");
            } finally {
                executor.shutdown();
            }
        }
        cachedGatewayManagers.clear();
    }

    /**
     * Searches for a gateway on the network(s) that this machine is connected to,
     * and returns the first valid one found.
     * <br>
     * This is equivalent to calling {@link #findGateway(InetAddress)} with a wildcard address.
     *
     * @return a UPnP interface for the gateway, or empty if none found.
     */
    public synchronized Optional<UpnpGatewayManager> findGateway() {
        return findGateway(localWildcardAddress);
    }

    /**
     * Searches for a gateway connected to the given local address.
     *
     * @param localAddress the local address used to find the gateway.  Use a wildcard address to
     *                     search all local addresses.
     * @return a UPnP interface for the gateway, or empty if none found.
     */
    public synchronized Optional<UpnpGatewayManager> findGateway(String localAddress) {
        try {
            return findGateway(InetAddress.getByName(localAddress));
        } catch (UnknownHostException e) {
            logger.error("Unable to resolve local address.", e);
            return Optional.empty();
        }
    }

    /**
     * Searches for a gateway connected to the given local address.
     *
     * @param localAddress the local address used to find the gateway.  Use a wildcard address to
     *                     search all local addresses.
     * @return a UPnP interface for the gateway, or empty if none found.
     */
    public synchronized Optional<UpnpGatewayManager> findGateway(InetAddress localAddress) {
        // check cached results to avoid redundant queries
        UpnpGatewayManager gatewayManager = cachedGatewayManagers.get(localAddress);
        if (gatewayManager != null) {
            return Optional.of(gatewayManager);
        }

        GatewayDevice gateway;
        if (localAddress.isAnyLocalAddress()) {
            logger.info("Searching all local interfaces for a valid gateway...");
            searchForAllGateways();
            // get the first valid gateway found from the search results
            gateway = query.getValidGateway();
            if (gateway != null) {
                logger.info(
                        "Found gateway \"{}\", connected at local address {}.",
                        gateway.getFriendlyName(),
                        gateway.getLocalAddress().getHostAddress()
                );
            }
        } else {
            logger.info("Searching {} for a valid gateway...", localAddress);
            gateway = searchForAllGateways().get(localAddress);
            if (gateway != null) {
                if (isValidGateway(gateway)) {
                    logger.info(
                            "Found gateway \"{}\", connected at local address {}.",
                            gateway.getFriendlyName(),
                            localAddress.getHostAddress()
                    );
                } else {
                    gateway = null;
                }
            }
        }

        if (gateway == null) {
            logger.error("No valid gateway found. " + UPNP_DISABLED_NOTIFICATION);
        } else {
            gatewayManager = new UpnpGatewayManager(gateway);
            cachedGatewayManagers.put(localAddress, gatewayManager);
        }
        return Optional.ofNullable(gatewayManager);
    }

    private synchronized Map<InetAddress, GatewayDevice> searchForAllGateways() {
        try {
            return query.discover();
        } catch (Exception e) {
            logger.error("Gateway discovery failed.", e);
            return Collections.emptyMap();
        }
    }

    private synchronized boolean isValidGateway(GatewayDevice gateway) {
        try {
            return gateway.isConnected();
        } catch (Exception e) {
            logger.error(String.format(
                    "Failed to read status of gateway \"%s\" connected at local address %s.",
                    gateway.getFriendlyName(),
                    gateway.getLocalAddress().getHostAddress()
            ), e);
            return false;
        }
    }

    @VisibleForTesting
    Map<InetAddress, UpnpGatewayManager> getCachedGatewayManagers() {
        return cachedGatewayManagers;
    }
}
