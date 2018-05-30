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

import co.rsk.config.ConfigLoader;
import com.google.common.annotations.VisibleForTesting;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigRenderOptions;
import org.ethereum.config.blockchain.DevNetConfig;
import org.ethereum.config.blockchain.FallbackMainNetConfig;
import org.ethereum.config.blockchain.RegTestConfig;
import org.ethereum.config.net.MainNetConfig;
import org.ethereum.config.net.TestNetConfig;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.net.p2p.P2pHandler;
import org.ethereum.net.rlpx.MessageCodec;
import org.ethereum.net.rlpx.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import javax.naming.ConfigurationException;
import java.io.*;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Utility class to retrieve property values from the rskj.conf files
 * <p>
 * The properties are taken from different sources and merged in the following order
 * (the config option from the next source overrides option from previous):
 * - resource rskj.conf : normally used as a reference config with default values
 * and shouldn't be changed
 * - system property : each config entry might be altered via -D VM option
 * - [user dir]/config/rskj.conf
 * - config specified with the -Drsk.conf.file=[file.conf] VM option
 * - CLI options
 *
 * @author Roman Mandeleil
 * @since 22.05.2014
 */
public abstract class SystemProperties {
    public static final String PROPERTY_BC_CONFIG_NAME = "blockchain.config.name";
    public static final String PROPERTY_DB_DIR = "database.dir";
    public static final String PROPERTY_PEER_PORT = "peer.port";
    public static final String PROPERTY_PEER_ACTIVE = "peer.active";
    public static final String PROPERTY_DB_RESET = "database.reset";
    // TODO review rpc properties
    public static final String PROPERTY_RPC_CORS = "rpc.providers.web.cors";
    public static final String PROPERTY_RPC_HTTP_ENABLED = "rpc.providers.web.http.enabled";
    public static final String PROPERTY_RPC_HTTP_ADDRESS = "rpc.providers.web.http.bind_address";
    public static final String PROPERTY_RPC_HTTP_HOSTS = "rpc.providers.web.http.hosts";
    public static final String PROPERTY_RPC_HTTP_PORT = "rpc.providers.web.http.port";
    public static final String PROPERTY_PUBLIC_IP = "public.ip";
    public static final String PROPERTY_BIND_ADDRESS = "bind_address";
    private static final String PROPERTY_RPC_WEBSOCKET_ENABLED = "rpc.providers.web.ws.enabled";
    private static final String PROPERTY_RPC_WEBSOCKET_ADDRESS = "rpc.providers.web.ws.bind_address";
    private static final String PROPERTY_RPC_WEBSOCKET_PORT = "rpc.providers.web.ws.port";
    /* Testing */
    private static final Boolean DEFAULT_VMTEST_LOAD_LOCAL = false;
    private static final String DEFAULT_BLOCKS_LOADER = "";
    private static Logger logger = LoggerFactory.getLogger("general");
    protected final Config configFromFiles;
    // mutable options for tests
    private String databaseDir = null;
    private String fallbackMiningKeysDir = null;
    private String projectVersion = null;
    private String projectVersionModifier = null;
    private String genesisInfo = null;
    private String publicIp = null;
    private Boolean syncEnabled = null;
    private Boolean discoveryEnabled = null;
    private BlockchainNetConfig blockchainConfig;

    protected SystemProperties(ConfigLoader loader) {
        try {
            this.configFromFiles = loader.getConfig();
            logger.trace(
                    "Config trace: {}",
                    configFromFiles.root().render(ConfigRenderOptions.defaults().setComments(false).setJson(false))
            );
            validateConfig();

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

    private void validateConfig() {
        for (Method method : getClass().getMethods()) {
            try {
                if (method.isAnnotationPresent(ValidateMe.class)) {
                    method.invoke(this);
                }
            } catch (Exception e) {
                throw new RuntimeException("Error validating config method: " + method, e);
            }
        }
    }

    public <T> T getProperty(String propName, T defaultValue) {
        if (!configFromFiles.hasPath(propName)) {
            return defaultValue;
        }

        String string = configFromFiles.getString(propName);
        if (string.trim().isEmpty()) {
            return defaultValue;
        }

        return (T) configFromFiles.getAnyRef(propName);
    }

    @ValidateMe
    public BlockchainNetConfig getBlockchainConfig() {
        if (blockchainConfig == null) {
            String netName = netName();
            if (netName != null && configFromFiles.hasPath("blockchain.config.class")) {
                throw new RuntimeException(String.format(
                        "Only one of two options should be defined: '%s' and 'blockchain.config.class'",
                        PROPERTY_BC_CONFIG_NAME)
                );
            }
            if (netName != null) {
                switch (netName) {
                    case "main":
                        blockchainConfig = new MainNetConfig();
                        break;
                    case "fallbackmain":
                        blockchainConfig = new FallbackMainNetConfig();
                        break;
                    case "testnet":
                        blockchainConfig = new TestNetConfig();
                        break;
                    case "devnet":
                        blockchainConfig = new DevNetConfig();
                        break;
                    case "regtest":
                        blockchainConfig = new RegTestConfig();
                        break;
                    default:
                        throw new RuntimeException(String.format(
                                "Unknown value for '%s': '%s'",
                                PROPERTY_BC_CONFIG_NAME,
                                netName)
                        );
                }
            } else {
                String className = configFromFiles.getString("blockchain.config.class");
                try {
                    Class<? extends BlockchainNetConfig> aClass = (Class<? extends BlockchainNetConfig>) Class.forName(className);
                    blockchainConfig = aClass.newInstance();
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException("The class specified via blockchain.config.class '" + className + "' not found", e);
                } catch (ClassCastException e) {
                    throw new RuntimeException("The class specified via blockchain.config.class '" + className + "' is not instance of org.ethereum.config.BlockchainForkConfig", e);
                } catch (InstantiationException | IllegalAccessException e) {
                    throw new RuntimeException("The class specified via blockchain.config.class '" + className + "' couldn't be instantiated (check for default constructor and its accessibility)", e);
                }
            }
        }
        return blockchainConfig;
    }

    @VisibleForTesting
    public void setBlockchainConfig(BlockchainNetConfig config) {
        blockchainConfig = config;
    }

    @ValidateMe
    public boolean isPeerDiscoveryEnabled() {
        return discoveryEnabled == null ? configFromFiles.getBoolean("peer.discovery.enabled") : discoveryEnabled;
    }

    public void setDiscoveryEnabled(Boolean discoveryEnabled) {
        this.discoveryEnabled = discoveryEnabled;
    }

    @ValidateMe
    public int peerConnectionTimeout() {
        return configFromFiles.getInt("peer.connection.timeout") * 1000;
    }

    @ValidateMe
    public int defaultP2PVersion() {
        return configFromFiles.hasPath("peer.p2p.version") ? configFromFiles.getInt("peer.p2p.version") : P2pHandler.VERSION;
    }

    @ValidateMe
    public int rlpxMaxFrameSize() {
        return configFromFiles.hasPath("peer.p2p.framing.maxSize") ? configFromFiles.getInt("peer.p2p.framing.maxSize") : MessageCodec.NO_FRAMING;
    }

    @ValidateMe
    public List<String> peerDiscoveryIPList() {
        return configFromFiles.hasPath("peer.discovery.ip.list") ? configFromFiles.getStringList("peer.discovery.ip.list") : new ArrayList<>();
    }

    @ValidateMe
    public boolean databaseReset() {
        return configFromFiles.getBoolean("database.reset");
    }

    @ValidateMe
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

    @ValidateMe
    public NodeFilter peerTrusted() {
        List<? extends ConfigObject> list = configFromFiles.getObjectList("peer.trusted");
        NodeFilter ret = new NodeFilter();

        for (ConfigObject configObject : list) {
            byte[] nodeId = null;
            String ipMask = null;
            if (configObject.get("nodeId") != null) {
                nodeId = Hex.decode(configObject.toConfig().getString("nodeId").trim());
            }
            if (configObject.get("ip") != null) {
                ipMask = configObject.toConfig().getString("ip").trim();
            }
            ret.add(nodeId, ipMask);
        }
        return ret;
    }

    @ValidateMe
    public Integer peerChannelReadTimeout() {
        return configFromFiles.getInt("peer.channel.read.timeout");
    }

    @ValidateMe
    public String dumpStyle() {
        return configFromFiles.getString("dump.style");
    }

    @ValidateMe
    public int dumpBlock() {
        return configFromFiles.getInt("dump.block");
    }

    @ValidateMe
    public String databaseDir() {
        return databaseDir == null ? configFromFiles.getString("database.dir") : databaseDir;
    }

    // can be missing
    public String fallbackMiningKeysDir() {
        try {
            return fallbackMiningKeysDir == null ? configFromFiles.getString("fallbackMining.keysDir") : fallbackMiningKeysDir;
        } catch (ConfigException.Missing e) {
            fallbackMiningKeysDir = "";
            return fallbackMiningKeysDir;
        }
    }

    public void setDataBaseDir(String dataBaseDir) {
        this.databaseDir = dataBaseDir;
    }

    @ValidateMe
    public boolean playVM() {
        return configFromFiles.getBoolean("play.vm");
    }

    @ValidateMe
    public int maxHashesAsk() {
        return configFromFiles.getInt("sync.max.hashes.ask");
    }

    @ValidateMe
    public int syncPeerCount() {
        return configFromFiles.getInt("sync.peer.count");
    }

    public Integer syncVersion() {
        if (!configFromFiles.hasPath("sync.version")) {
            return null;
        }
        return configFromFiles.getInt("sync.version");
    }

    @ValidateMe
    public String projectVersion() {
        return projectVersion;
    }

    @ValidateMe
    public String projectVersionModifier() {
        return projectVersionModifier;
    }

    @ValidateMe
    public String helloPhrase() {
        return configFromFiles.getString("hello.phrase");
    }

    @ValidateMe
    public String rootHashStart() {
        return configFromFiles.hasPath("root.hash.start") ? configFromFiles.getString("root.hash.start") : null;
    }

    @ValidateMe
    public List<String> peerCapabilities() {
        return configFromFiles.hasPath("peer.capabilities") ? configFromFiles.getStringList("peer.capabilities") : new ArrayList<>(Arrays.asList("rsk"));
    }

    @ValidateMe
    public boolean vmTrace() {
        return configFromFiles.getBoolean("vm.structured.trace");
    }

    @ValidateMe
    public boolean vmTraceCompressed() {
        return configFromFiles.getBoolean("vm.structured.compressed");
    }

    @ValidateMe
    public int vmTraceInitStorageLimit() {
        return configFromFiles.getInt("vm.structured.initStorageLimit");
    }

    @ValidateMe
    public int detailsInMemoryStorageLimit() {
        return configFromFiles.getInt("details.inmemory.storage.limit");
    }

    @ValidateMe
    public String vmTraceDir() {
        return configFromFiles.getString("vm.structured.dir");
    }

    @ValidateMe
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
                props.setProperty("nodeIdPrivateKey", Hex.toHexString(key.getPrivKeyBytes()));
                props.setProperty("nodeId", Hex.toHexString(key.getNodeId()));
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

    @ValidateMe
    public ECKey getMyKey() {
        return ECKey.fromPrivate(Hex.decode(privateKey())).decompress();
    }

    /**
     * Home NodeID calculated from 'peer.privateKey' property
     */
    @ValidateMe
    public byte[] nodeId() {
        return getMyKey().getNodeId();
    }

    @ValidateMe
    public int networkId() {
        return configFromFiles.getInt("peer.networkId");
    }

    @ValidateMe
    public int maxActivePeers() {
        return configFromFiles.getInt("peer.maxActivePeers");
    }

    @ValidateMe
    public boolean eip8() {
        return configFromFiles.getBoolean("peer.p2p.eip8");
    }

    @ValidateMe
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
            if (!externalIpFromConfig.isEmpty()) {
                try {
                    InetAddress address = tryParseIpOrThrow(externalIpFromConfig);
                    publicIp = address.getHostAddress();
                    logger.info("Public IP identified {}", publicIp);
                    return publicIp;
                } catch (IOException e) {
                    logger.warn("Can't resolve public IP", e);
                } catch (IllegalArgumentException e) {
                    logger.warn("Can't resolve public IP", e);
                }
                publicIp = null;
            }
        }

        publicIp = getMyPublicIpFromRemoteService();
        return publicIp;
    }

    private String getMyPublicIpFromRemoteService() {
        try {
            logger.info("Public IP wasn't set or resolved, using checkip.amazonaws.com to identify it...");

            try (BufferedReader in = new BufferedReader(new InputStreamReader(new URL("http://checkip.amazonaws.com").openStream()))) {
                publicIp = in.readLine();
            }

            if (publicIp == null || publicIp.trim().isEmpty()) {
                logger.warn("Unable to retrieve public IP from checkip.amazonaws.com {}.", publicIp);
                throw new IOException("Invalid address: '" + publicIp + "'");
            }

            tryParseIpOrThrow(publicIp);
            logger.info("Identified public IP: {}", publicIp);
            return publicIp;
        } catch (IOException e) {
            logger.error("Can't get public IP", e);
        } catch (IllegalArgumentException e) {
            logger.error("Can't get public IP", e);
        }

        String bindAddress = getBindAddress().toString();
        if (getBindAddress().isAnyLocalAddress()) {
            throw new RuntimeException("Wildcard on bind address it's not allowed as fallback for public IP " + bindAddress);
        }
        publicIp = bindAddress;

        return publicIp;
    }

    @ValidateMe
    public String getKeyValueDataSource() {
        return configFromFiles.getString("keyvalue.datasource");
    }

    @ValidateMe
    public boolean isSyncEnabled() {
        return this.syncEnabled == null ? configFromFiles.getBoolean("sync.enabled") : syncEnabled;
    }

    public void setSyncEnabled(Boolean syncEnabled) {
        this.syncEnabled = syncEnabled;
    }

    @ValidateMe
    public String genesisInfo() {

        if (genesisInfo == null) {
            return configFromFiles.getString("genesis");
        } else {
            return genesisInfo;
        }
    }

    @ValidateMe
    public int txOutdatedThreshold() {
        return configFromFiles.getInt("transaction.outdated.threshold");
    }

    @ValidateMe
    public int txOutdatedTimeout() {
        return configFromFiles.getInt("transaction.outdated.timeout");
    }

    public void setGenesisInfo(String genesisInfo) {
        this.genesisInfo = genesisInfo;
    }

    public String dump() {
        return configFromFiles.root().render(ConfigRenderOptions.defaults().setComments(false));
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

    protected int getInt(String path, int val) {
        return configFromFiles.hasPath(path) ? configFromFiles.getInt(path) : val;
    }

    protected long getLong(String path, long val) {
        return configFromFiles.hasPath(path) ? configFromFiles.getLong(path) : val;
    }

    protected int getUnsignedInt(String path, int val) throws ConfigurationException {
        if (!configFromFiles.hasPath(path)) {
            return val;
        }

        int configuredValue = configFromFiles.getInt(path);
        if (configuredValue < 0) {
            throw new ConfigurationException("Expected unsigned int for property " + path + " . Configured value is " + configuredValue);
        }

        return configuredValue;
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

    public String blocksLoader() {
        return configFromFiles.hasPath("blocks.loader") ?
                configFromFiles.getString("blocks.loader") : DEFAULT_BLOCKS_LOADER;
    }

    public String customSolcPath() {
        return configFromFiles.hasPath("solc.path") ? configFromFiles.getString("solc.path") : null;
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

    protected long getLongProperty(String propertyName, long defaultValue) {
        return configFromFiles.hasPath(propertyName) ? configFromFiles.getLong(propertyName) : defaultValue;
    }

    protected boolean getBooleanProperty(String propertyName, boolean defaultValue) {
        return configFromFiles.hasPath(propertyName) ? configFromFiles.getBoolean(propertyName) : defaultValue;
    }

    private InetAddress tryParseIpOrThrow(String ipToParse) throws IOException {
        try {
            return InetAddress.getByName(ipToParse);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid address: '" + ipToParse + "'", e);
        }
    }

    /**
     * Marks config accessor methods which need to be called (for value validation)
     * upon config creation or modification
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    private @interface ValidateMe {
    }
}
