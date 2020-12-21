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

package org.ethereum.config;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.config.BridgeDevNetConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.config.ConfigLoader;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigRenderOptions;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.net.p2p.P2pHandler;
import org.ethereum.net.rlpx.MessageCodec;
import org.ethereum.net.rlpx.Node;
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Utility class to retrieve property values from the rskj.conf files
 *
 * The properties are taken from different sources and merged in the following order
 * (the config option from the next source overrides option from previous):
 * - resource rskj.conf : normally used as a reference config with default values
 *          and shouldn't be changed
 * - system property : each config entry might be altered via -D VM option
 * - [user dir]/config/rskj.conf
 * - config specified with the -Drsk.conf.file=[file.conf] VM option
 * - CLI options
 *
 * @author Roman Mandeleil
 * @since 22.05.2014
 */
public abstract class SystemProperties {
    private static Logger logger = LoggerFactory.getLogger("general");

    public static final String PROPERTY_BLOCKCHAIN_CONFIG = "blockchain.config";
    public static final String PROPERTY_BC_CONFIG_NAME = PROPERTY_BLOCKCHAIN_CONFIG + ".name";
    public static final String PROPERTY_BC_VERIFY = PROPERTY_BLOCKCHAIN_CONFIG + ".verify";
    public static final String PROPERTY_GENESIS_CONSTANTS_FEDERATION_PUBLICKEYS = "genesis_constants.federationPublicKeys";
    public static final String PROPERTY_PEER_PORT = "peer.port";
    public static final String PROPERTY_BASE_PATH = "database.dir";
    public static final String PROPERTY_DB_RESET = "database.reset";
    public static final String PROPERTY_DB_IMPORT = "database.import.enabled";
    // TODO review rpc properties
    public static final String PROPERTY_RPC_CORS = "rpc.providers.web.cors";
    public static final String PROPERTY_RPC_HTTP_ENABLED = "rpc.providers.web.http.enabled";
    public static final String PROPERTY_RPC_HTTP_ADDRESS = "rpc.providers.web.http.bind_address";
    public static final String PROPERTY_RPC_HTTP_HOSTS = "rpc.providers.web.http.hosts";
    public static final String PROPERTY_RPC_HTTP_PORT = "rpc.providers.web.http.port";
    private static final String PROPERTY_RPC_WEBSOCKET_ENABLED = "rpc.providers.web.ws.enabled";
    private static final String PROPERTY_RPC_WEBSOCKET_ADDRESS = "rpc.providers.web.ws.bind_address";
    private static final String PROPERTY_RPC_WEBSOCKET_PORT = "rpc.providers.web.ws.port";

    public static final String PROPERTY_PUBLIC_IP = "public.ip";
    public static final String PROPERTY_BIND_ADDRESS = "bind_address";

    public static final String PROPERTY_PRINT_SYSTEM_INFO = "system.printInfo";

    /* Testing */
    private static final Boolean DEFAULT_VMTEST_LOAD_LOCAL = false;

    protected final Config configFromFiles;

    // mutable options for tests
    private String databaseDir = null;
    private String projectVersion = null;
    private String projectVersionModifier = null;

    private String genesisInfo = null;

    private String publicIp = null;

    private ActivationConfig activationConfig;
    private Constants constants;

    protected SystemProperties(ConfigLoader loader) {
        try {
            this.configFromFiles = loader.getConfig();
            logger.trace(
                    "Config trace: {}",
                    configFromFiles.root().render(ConfigRenderOptions.defaults().setComments(false).setJson(false))
            );

            Properties props = new Properties();
            try (InputStream is = getClass().getResourceAsStream("/version.properties")) {
                props.load(is);
            }
            this.projectVersion = getProjectVersion(props);
            this.projectVersionModifier = getProjectVersionModifier(props);

        } catch (Exception e) {
            logger.error("Can't read config.", e);
            throw new RuntimeException(e);
        }
    }

    private static String getProjectVersion(Properties props) {
        String versionNumber = props.getProperty("versionNumber");

        if (versionNumber == null) {
            return "-.-.-";
        }

        return versionNumber.replaceAll("'", "");
    }

    private static String getProjectVersionModifier(Properties props) {
        return props.getProperty("modifier").replaceAll("\"", "");
    }

    public Config getConfig() {
        return configFromFiles;
    }

    public ActivationConfig getActivationConfig() {
        if (activationConfig == null) {
            activationConfig = ActivationConfig.read(configFromFiles.getConfig(PROPERTY_BLOCKCHAIN_CONFIG));
        }

        return activationConfig;
    }

    public Constants getNetworkConstants() {
        if (constants == null) {
            switch (netName()) {
                case "main":
                    constants = Constants.mainnet();
                    break;
                case "testnet":
                    constants = Constants.testnet();
                    break;
                case "devnet":
                    constants = Constants.devnetWithFederation(
                            getGenesisFederationPublicKeys().orElse(BridgeDevNetConstants.DEVNET_FEDERATION_PUBLIC_KEYS)
                    );
                    break;
                case "regtest":
                    constants = Constants.regtestWithFederation(
                            getGenesisFederationPublicKeys().orElse(BridgeRegTestConstants.REGTEST_FEDERATION_PUBLIC_KEYS)
                    );
                    break;
                default:
                    throw new RuntimeException(String.format("Unknown network name '%s'", netName()));
            }
        }

        return constants;
    }

    public boolean isPeerDiscoveryEnabled() {
        return configFromFiles.getBoolean("peer.discovery.enabled");
    }

    public int peerConnectionTimeout() {
        return configFromFiles.getInt("peer.connection.timeout") * 1000;
    }

    public int defaultP2PVersion() {
        return configFromFiles.hasPath("peer.p2p.version") ? configFromFiles.getInt("peer.p2p.version") : P2pHandler.VERSION;
    }

    public int rlpxMaxFrameSize() {
        return configFromFiles.hasPath("peer.p2p.framing.maxSize") ? configFromFiles.getInt("peer.p2p.framing.maxSize") : MessageCodec.NO_FRAMING;
    }

    public List<String> peerDiscoveryIPList() {
        return configFromFiles.hasPath("peer.discovery.ip.list") ? configFromFiles.getStringList("peer.discovery.ip.list") : new ArrayList<>();
    }

    public boolean databaseReset() {
        return configFromFiles.getBoolean("database.reset");
    }

    public boolean importEnabled() {
        return configFromFiles.getBoolean(PROPERTY_DB_IMPORT);
    }

    public String importUrl() {
        return configFromFiles.getString("database.import.url");
    }

    public List<String> importTrustedKeys() {
        return configFromFiles.getStringList("database.import.trusted-keys");
    }

    public List<Node> peerActive() {
        if (!configFromFiles.hasPath("peer.active")) {
            return Collections.emptyList();
        }
        List<? extends ConfigObject> list = configFromFiles.getObjectList("peer.active");
        return list.stream().map(this::parsePeer).collect(Collectors.toList());
    }

    private Node parsePeer(ConfigObject configObject) {
        if (configObject.get("url") != null) {
            String url = configObject.toConfig().getString("url");
            return new Node(url.startsWith("enode://") ? url : "enode://" + url);
        }

        if (configObject.get("ip") != null) {
            String ip = configObject.toConfig().getString("ip");
            int port = configObject.toConfig().getInt("port");
            if (configObject.toConfig().hasPath("nodeId")) {
                byte[] nodeId = Hex.decode(configObject.toConfig().getString("nodeId").trim());
                if (nodeId.length == 64) {
                    return new Node(nodeId, ip, port);
                }

                throw new RuntimeException("Invalid config nodeId '" + nodeId + "' at " + configObject);
            }

            if (configObject.toConfig().hasPath("nodeName")) {
                String nodeName = configObject.toConfig().getString("nodeName").trim();
                // FIXME should be sha3-512 here ?
                byte[] nodeId = ECKey.fromPrivate(Keccak256Helper.keccak256(nodeName.getBytes(StandardCharsets.UTF_8))).getNodeId();
                return new Node(nodeId, ip, port);
            }

            throw new RuntimeException("Either nodeId or nodeName should be specified: " + configObject);
        }

        throw new RuntimeException("Unexpected element within 'peer.active' config list: " + configObject);
    }

    public NodeFilter trustedPeers() {
        List<? extends ConfigObject> list = configFromFiles.getObjectList("peer.trusted");
        NodeFilter ret = new NodeFilter();
        list.stream().map(ConfigObject::toConfig).forEach(config -> {
            String nodeIdData = config.getString("nodeId");
            String ipData = config.getString("ip");
            byte[] nodeId = nodeIdData != null ? Hex.decode(nodeIdData.trim()) : null;
            String ipMask = ipData != null ? ipData.trim() : null;
            ret.add(nodeId, ipMask);
        });

        return ret;
    }

    public Integer peerChannelReadTimeout() {
        return configFromFiles.getInt("peer.channel.read.timeout");
    }

    public String dumpStyle() {
        return configFromFiles.getString("dump.style");
    }

    public int dumpBlock() {
        return configFromFiles.getInt("dump.block");
    }

    public String databaseDir() {
        return databaseDir == null ? configFromFiles.getString(PROPERTY_BASE_PATH) : databaseDir;
    }

    public void setDataBaseDir(String dataBaseDir) {
        this.databaseDir = dataBaseDir;
    }

    public boolean playVM() {
        return configFromFiles.getBoolean("play.vm");
    }

    public int maxHashesAsk() {
        return configFromFiles.getInt("sync.max.hashes.ask");
    }

    public int syncPeerCount() {
        return configFromFiles.getInt("sync.peer.count");
    }

    public Integer syncVersion() {
        if (!configFromFiles.hasPath("sync.version")) {
            return null;
        }
        return configFromFiles.getInt("sync.version");
    }


    public String projectVersion() {
        return projectVersion;
    }

    public String projectVersionModifier() {
        return projectVersionModifier;
    }

    public String helloPhrase() {
        return configFromFiles.getString("hello.phrase");
    }

    public List<String> peerCapabilities() {
        return configFromFiles.hasPath("peer.capabilities") ?  configFromFiles.getStringList("peer.capabilities") : new ArrayList<>(Arrays.asList("rsk"));
    }

    public boolean vmTrace() {
        return configFromFiles.getBoolean("vm.structured.trace");
    }

    public int vmTraceOptions() {
        return configFromFiles.getInt("vm.structured.traceOptions");
    }

    public boolean vmTraceCompressed() {
        return configFromFiles.getBoolean("vm.structured.compressed");
    }

    public int vmTraceInitStorageLimit() {
        return configFromFiles.getInt("vm.structured.initStorageLimit");
    }

    public String vmTraceDir() {
        return configFromFiles.getString("vm.structured.dir");
    }

    public String privateKey() {
        if (configFromFiles.hasPath("peer.privateKey")) {
            String key = configFromFiles.getString("peer.privateKey");
            if (key.length() != 64) {
                throw new RuntimeException("The peer.privateKey needs to be Hex encoded and 32 byte length");
            }
            return key;
        } else {
            return getGeneratedNodePrivateKey();
        }
    }

    private String getGeneratedNodePrivateKey() {
        try {
            File file = new File(databaseDir(), "nodeId.properties");
            Properties props = new Properties();
            if (file.canRead()) {
                props.load(new FileReader(file));
            } else {
                ECKey key = new ECKey();
                props.setProperty("nodeIdPrivateKey", ByteUtil.toHexString(key.getPrivKeyBytes()));
                props.setProperty("nodeId", ByteUtil.toHexString(key.getNodeId()));
                file.getParentFile().mkdirs();
                props.store(new FileWriter(file), "Generated NodeID. To use your own nodeId please refer to 'peer.privateKey' config option.");
                logger.info("New nodeID generated: {}", props.getProperty("nodeId"));
                logger.info("Generated nodeID and its private key stored in {}", file);
            }
            return props.getProperty("nodeIdPrivateKey");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public ECKey getMyKey() {
        return ECKey.fromPrivate(Hex.decode(privateKey())).decompress();
    }

    /**
     *  Home NodeID calculated from 'peer.privateKey' property
     */
    public byte[] nodeId() {
        return getMyKey().getNodeId();
    }

    public int networkId() {
        return configFromFiles.getInt("peer.networkId");
    }

    public int maxActivePeers() {
        return configFromFiles.getInt("peer.maxActivePeers");
    }

    public int maxConnectionsAllowed() {
        return configFromFiles.getInt("peer.filter.maxConnections");
    }

    public int networkCIDR() {
        return configFromFiles.getInt("peer.filter.networkCidr");
    }

    public boolean eip8() {
        return configFromFiles.getBoolean("peer.p2p.eip8");
    }

    public int getPeerPort() {
        return configFromFiles.getInt(PROPERTY_PEER_PORT);
    }

    public InetAddress getBindAddress() {
        String host = configFromFiles.getString(PROPERTY_BIND_ADDRESS);
        try {
            return InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(String.format("%s is not a valid %s property", host, PROPERTY_BIND_ADDRESS), e);
        }
    }

    /**
     * This can be a blocking call with long timeout (thus no ValidateMe)
     */
    public synchronized String getPublicIp() {
        if (publicIp != null) {
            return publicIp;
        }

        if (configFromFiles.hasPath(PROPERTY_PUBLIC_IP)) {
            String externalIpFromConfig = configFromFiles.getString(PROPERTY_PUBLIC_IP).trim();
            if (!externalIpFromConfig.isEmpty()){
                try {
                    InetAddress address = tryParseIpOrThrow(externalIpFromConfig);
                    publicIp = address.getHostAddress();
                    logger.info("Public IP identified {}", publicIp);
                    return publicIp;
                } catch (IllegalArgumentException e) {
                    logger.warn("Can't resolve public IP", e);
                }
                publicIp = null;
            }
        }

        publicIp = getMyPublicIpFromRemoteService().getHostAddress();
        return publicIp;
    }

    private InetAddress getMyPublicIpFromRemoteService(){
        try {
            URL ipCheckService = publicIpCheckService();
            logger.info("Public IP wasn't set or resolved, using {} to identify it...", ipCheckService);

            String ipFromService;
            try (BufferedReader in = new BufferedReader(new InputStreamReader(ipCheckService.openStream()))) {
                ipFromService = in.readLine();
            }

            if (ipFromService == null || ipFromService.trim().isEmpty()) {
                logger.warn("Unable to retrieve public IP from {} {}.", ipCheckService, ipFromService);
                throw new IOException("Invalid address: '" + ipFromService + "'");
            }

            InetAddress resolvedIp = tryParseIpOrThrow(ipFromService);
            logger.info("Identified public IP: {}", resolvedIp);
            return resolvedIp;
        } catch (IOException e) {
            logger.error("Can't get public IP", e);
        } catch (IllegalArgumentException e) {
            logger.error("Can't get public IP", e);
        }

        InetAddress bindAddress = getBindAddress();
        if (bindAddress.isAnyLocalAddress()){
            throw new RuntimeException("Wildcard on bind address it's not allowed as fallback for public IP " + bindAddress);
        }

        return bindAddress;
    }

    private URL publicIpCheckService() throws MalformedURLException {
        return new URL(configFromFiles.getString("public.ipCheckService"));
    }

    public boolean isSyncEnabled() {
        return configFromFiles.getBoolean("sync.enabled");
    }

    public String genesisInfo() {

        if (genesisInfo == null) {
            return configFromFiles.getString("genesis");
        } else {
            return genesisInfo;
        }
    }

    public int txOutdatedThreshold() {
        return configFromFiles.getInt("transaction.outdated.threshold");
    }

    public int txOutdatedTimeout() {
        return configFromFiles.getInt("transaction.outdated.timeout");
    }

    public void setGenesisInfo(String genesisInfo){
        this.genesisInfo = genesisInfo;
    }

    public boolean scoringPunishmentEnabled() {
        return configFromFiles.hasPath("scoring.punishmentEnabled") ?
                configFromFiles.getBoolean("scoring.punishmentEnabled") : false;
    }

    public int scoringNumberOfNodes() {
        return getInt("scoring.nodes.number", 100);
    }

    public long scoringNodesPunishmentDuration() {
        return getLong("scoring.nodes.duration", 10) * 60000L;
    }

    public int scoringNodesPunishmentIncrement() {
        return getInt("scoring.nodes.increment", 10);
    }

    public long scoringNodesPunishmentMaximumDuration() {
        // default value: no maximum duration
        return getLong("scoring.nodes.maximum", 0) * 60000L;
    }

    public long scoringAddressesPunishmentDuration() {
        return getLong("scoring.addresses.duration", 10) * 60000L;
    }

    public int scoringAddressesPunishmentIncrement() {
        return getInt("scoring.addresses.increment", 10);
    }

    public long scoringAddressesPunishmentMaximumDuration() {
        // default value: 1 week
        return TimeUnit.MINUTES.toMillis(getLong("scoring.addresses.maximum", TimeUnit.DAYS.toMinutes(7)));
    }

    public boolean shouldPrintSystemInfo() {
        return getBoolean(PROPERTY_PRINT_SYSTEM_INFO, false);
    }

    protected int getInt(String path, int val) {
        return configFromFiles.hasPath(path) ? configFromFiles.getInt(path) : val;
    }

    protected long getLong(String path, long val) {
        return configFromFiles.hasPath(path) ? configFromFiles.getLong(path) : val;
    }

    protected double getDouble(String path, double val) {
        return configFromFiles.hasPath(path) ? configFromFiles.getDouble(path) : val;
    }

    protected boolean getBoolean(String path, boolean val) {
        return configFromFiles.hasPath(path) ? configFromFiles.getBoolean(path) : val;
    }

    protected String getString(String path, String val) {
        return configFromFiles.hasPath(path) ? configFromFiles.getString(path) : val;
    }

    /*
     *
     * Testing
     *
     */
    public boolean vmTestLoadLocal() {
        return configFromFiles.hasPath("GitHubTests.VMTest.loadLocal") ?
                configFromFiles.getBoolean("GitHubTests.VMTest.loadLocal") : DEFAULT_VMTEST_LOAD_LOCAL;
    }

    public String customSolcPath() {
        return configFromFiles.hasPath("solc.path") ? configFromFiles.getString("solc.path"): null;
    }

    public String netName() {
        return configFromFiles.getString(PROPERTY_BC_CONFIG_NAME);
    }

    public boolean isRpcHttpEnabled() {
        return configFromFiles.getBoolean(PROPERTY_RPC_HTTP_ENABLED);
    }

    public boolean isRpcWebSocketEnabled() {
        return configFromFiles.getBoolean(PROPERTY_RPC_WEBSOCKET_ENABLED);
    }

    public int rpcHttpPort() {
        return configFromFiles.getInt(PROPERTY_RPC_HTTP_PORT);
    }

    public int rpcWebSocketPort() {
        return configFromFiles.getInt(PROPERTY_RPC_WEBSOCKET_PORT);
    }

    public InetAddress rpcHttpBindAddress() {
        return getWebBindAddress(PROPERTY_RPC_HTTP_ADDRESS);
    }

    public InetAddress rpcWebSocketBindAddress() {
        return getWebBindAddress(PROPERTY_RPC_WEBSOCKET_ADDRESS);
    }

    public List<String> rpcHttpHost() {
        return configFromFiles.getStringList(PROPERTY_RPC_HTTP_HOSTS);
    }

    private InetAddress getWebBindAddress(String bindAddressConfigKey) {
        String bindAddress = configFromFiles.getString(bindAddressConfigKey);
        try {
            return InetAddress.getByName(bindAddress);
        } catch (UnknownHostException e) {
            logger.warn("Unable to bind to {}. Using loopback instead", e);
            return InetAddress.getLoopbackAddress();
        }
    }

    public String corsDomains() {
        return configFromFiles.getString(PROPERTY_RPC_CORS);
    }

    /**
     * Parses a list of IPs separated by commas. E.g. "171.99.160.48, 171.99.160.48".
     */
    private InetAddress tryParseIpOrThrow(String ipsToParse) {
        try {
            String[] ips = ipsToParse.split(", ");
            String ipToParse = ips[ips.length - 1];
            return InetAddress.getByName(ipToParse);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid address(es): '" + ipsToParse + "'", e);
        }
    }

    private Optional<List<BtcECKey>> getGenesisFederationPublicKeys() {
        if (!configFromFiles.hasPath(PROPERTY_GENESIS_CONSTANTS_FEDERATION_PUBLICKEYS)) {
            return Optional.empty();
        }

        List<String> configFederationPublicKeys = configFromFiles.getStringList(PROPERTY_GENESIS_CONSTANTS_FEDERATION_PUBLICKEYS);
        return Optional.of(
                configFederationPublicKeys.stream()
                        .map(key -> BtcECKey.fromPublicOnly(Hex.decode(key))).collect(Collectors.toList())
        );
    }
}
