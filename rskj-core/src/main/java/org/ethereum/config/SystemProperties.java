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

import com.google.common.annotations.VisibleForTesting;
import com.typesafe.config.*;
import org.ethereum.config.blockchain.DevNetConfig;
import org.ethereum.config.blockchain.FallbackMainNetConfig;
import org.ethereum.config.blockchain.RegTestConfig;
import org.ethereum.config.net.TestNetConfig;
import org.ethereum.config.net.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.net.p2p.P2pHandler;
import org.ethereum.net.rlpx.MessageCodec;
import org.ethereum.net.rlpx.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.io.*;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.ethereum.crypto.SHA3Helper.sha3;

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
    public static final String DEFAULT_BIND_IP = "::";
    private static Logger logger = LoggerFactory.getLogger("general");

    public static final String PROPERTY_DB_DIR = "database.dir";
    public static final String PROPERTY_LISTEN_PORT = "peer.listen.port";
    public static final String PROPERTY_PEER_ACTIVE = "peer.active";
    public static final String PROPERTY_DB_RESET = "database.reset";
    // TODO review rpc properties
    public static final String PROPERTY_RPC_ENABLED = "rpc.enabled";
    public static final String PROPERTY_RPC_PORT = "rpc.port";
    public static final String PROPERTY_RPC_CORS = "rpc.cors";

    /* Testing */
    private static final Boolean DEFAULT_VMTEST_LOAD_LOCAL = false;
    private static final String DEFAULT_BLOCKS_LOADER = "";

    private static final String YES = "yes";
    private static final String NO = "no";

    /**
     * Marks config accessor methods which need to be called (for value validation)
     * upon config creation or modification
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    private @interface ValidateMe {}

    protected Config configFromFiles;

    // mutable options for tests
    private String databaseDir = null;
    private String fallbackMiningKeysDir = null;
    private Boolean databaseReset = null;
    private String projectVersion = null;
    private String projectVersionModifier = null;

    private String genesisInfo = null;

    private String bindIp = null;
    private String externalIp = null;

    private Boolean syncEnabled = null;
    private Boolean discoveryEnabled = null;

    private BlockchainNetConfig blockchainConfig;
    
    protected SystemProperties() {
        try {
            configFromFiles = getConfigFromFiles();
            validateConfig();

            logger.debug("Config trace: " + configFromFiles.root().render(ConfigRenderOptions.defaults().
                    setComments(false).setJson(false)));

            Properties props = new Properties();
            InputStream is = getClass().getResourceAsStream("/version.properties");
            props.load(is);
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

    private static Config getConfigFromFiles() {
        Config javaSystemProperties = ConfigFactory.load("no-such-resource-only-system-props");
        Config referenceConfig = ConfigFactory.parseResources("rskj.conf");
        logger.info("Config ( {} ): default properties from resource 'rskj.conf'", referenceConfig.entrySet().isEmpty() ? NO : YES);
        File installerFile = new File("/etc/rsk/node.conf");
        Config installerConfig = installerFile.exists() ? ConfigFactory.parseFile(installerFile) : ConfigFactory.empty();
        logger.info("Config ( {} ): default properties from installer '/etc/rsk/node.conf'", installerConfig.entrySet().isEmpty() ? NO : YES);
        Config testConfig = ConfigFactory.parseResources("test-rskj.conf");
        logger.info("Config ( {} ): test properties from resource 'test-rskj.conf'", testConfig.entrySet().isEmpty() ? NO : YES);
        String file = System.getProperty("rsk.conf.file");
        Config cmdLineConfigFile = file != null ? ConfigFactory.parseFile(new File(file)) : ConfigFactory.empty();
        logger.info("Config ( {} ): user properties from -Drsk.conf.file file '{}'", cmdLineConfigFile.entrySet().isEmpty() ? NO : YES, file);
        return javaSystemProperties
                .withFallback(cmdLineConfigFile)
                .withFallback(testConfig)
                .withFallback(referenceConfig)
                .withFallback(installerConfig);
    }

    public Config getConfig() {
        return configFromFiles;
    }

    /**
     * Puts a new config atop of existing stack making the options
     * in the supplied config overriding existing options
     * Once put this config can't be removed
     *
     * @param overrideOptions - atop config
     */
    public void overrideParams(Config overrideOptions) {
        configFromFiles = overrideOptions.withFallback(configFromFiles);
        validateConfig();
    }

    /**
     * Puts a new config atop of existing stack making the options
     * in the supplied config overriding existing options
     * Once put this config can't be removed
     *
     * @param keyValuePairs [name] [value] [name] [value] ...
     */
    public void overrideParams(String ... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new RuntimeException("Odd argument number");
        }

        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            map.put(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        overrideParams(map);
    }

    /**
     * Puts a new config atop of existing stack making the options
     * in the supplied config overriding existing options
     * Once put this config can't be removed
     *
     * @param cliOptions -  command line options to take presidency
     */
    public void overrideParams(Map<String, String> cliOptions) {
        Config cliConf = ConfigFactory.parseMap(cliOptions);
        overrideParams(cliConf);
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
                throw new RuntimeException("Only one of two options should be defined: 'blockchain.config.name' and 'blockchain.config.class'");
            }
            if (netName != null) {
                switch(netName) {
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
                        throw new RuntimeException("Unknown value for 'blockchain.config.name': '" + configFromFiles.getString("blockchain.config.name") + "'");
                }
            } else {
                String className = configFromFiles.getString("blockchain.config.class");
                try {
                    Class<? extends BlockchainNetConfig> aClass = (Class<? extends BlockchainNetConfig>) Class.forName(className);
                    blockchainConfig = aClass.newInstance();
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException("The class specified via blockchain.config.class '" + className + "' not found" , e);
                } catch (ClassCastException e) {
                    throw new RuntimeException("The class specified via blockchain.config.class '" + className + "' is not instance of org.ethereum.config.BlockchainForkConfig" , e);
                } catch (InstantiationException|IllegalAccessException e) {
                    throw new RuntimeException("The class specified via blockchain.config.class '" + className + "' couldn't be instantiated (check for default constructor and its accessibility)" , e);
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
    public boolean peerDiscovery() {
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
        return databaseReset == null ? configFromFiles.getBoolean("database.reset") : databaseReset;
    }

    public void setDatabaseReset(Boolean reset) {
        databaseReset = reset;
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
                byte[] nodeId = ECKey.fromPrivate(sha3(nodeName.getBytes(StandardCharsets.UTF_8))).getNodeId();
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
        } catch(ConfigException.Missing e) {
            fallbackMiningKeysDir ="";
            return fallbackMiningKeysDir;
        }
    }

    public void setDataBaseDir(String dataBaseDir) {
        this.databaseDir = dataBaseDir;
    }

    @ValidateMe
    public boolean dumpCleanOnRestart() {
        return configFromFiles.getBoolean("dump.clean.on.restart");
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
        return configFromFiles.hasPath("peer.capabilities") ?  configFromFiles.getStringList("peer.capabilities") : new ArrayList<>(Arrays.asList("rsk"));
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
                logger.info("New nodeID generated: " + props.getProperty("nodeId"));
                logger.info("Generated nodeID and its private key stored in " + file);
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
     *  Home NodeID calculated from 'peer.privateKey' property
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
    public int listenPort() {
        return configFromFiles.getInt("peer.listen.port");
    }

    public String getPeerDiscoveryBindAddress() {
        if (bindIp != null) {
            return bindIp;
        }

        bindIp = DEFAULT_BIND_IP;
        if (configFromFiles.hasPath("peer.discovery.bind.ip")) {
            String bindIpFromConfig = configFromFiles.getString("peer.discovery.bind.ip").trim();
            if (!bindIpFromConfig.isEmpty()) {
                bindIp = bindIpFromConfig;
            }
        }

        logger.info("Binding peer discovery on {}", bindIp);
        return bindIp;
    }

    /**
     * This can be a blocking call with long timeout (thus no ValidateMe)
     */
    public synchronized String getExternalIp() {
        if (externalIp != null) {
            return externalIp;
        }

        if (configFromFiles.hasPath("peer.discovery.external.ip")) {
            String externalIpFromConfig = configFromFiles.getString("peer.discovery.external.ip").trim();
            if (!externalIpFromConfig.isEmpty()){
                try {
                    InetAddress addr = InetAddress.getByName(externalIpFromConfig);
                    externalIp = addr.getHostAddress();
                    logger.info("External address identified {}", externalIp);
                    return externalIp;
                } catch (IOException e) {
                    externalIp = null;
                    logger.warn("Can't resolve external address");
                }
            }
        }

        externalIp = getMyPublicIpFromRemoteService();
        return externalIp;
    }

    public String getMyPublicIpFromRemoteService(){
        logger.info("External IP wasn't set or resolved, using checkip.amazonaws.com to identify it...");
        try {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(new URL("http://checkip.amazonaws.com").openStream()))) {
                externalIp = in.readLine();
            }

            if (externalIp == null || externalIp.trim().isEmpty()) {
                throw new IOException("Invalid address: '" + externalIp + "'");
            }

            tryParseIpOrThrow();
            logger.info("External address identified: {}", externalIp);
        } catch (IOException e) {
            logger.error("Can't get external IP. " + e);
            externalIp = getPeerDiscoveryBindAddress();
        }
        return externalIp;
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

    public void setGenesisInfo(String genesisInfo){
        this.genesisInfo = genesisInfo;
    }

    public String dump() {
        return configFromFiles.root().render(ConfigRenderOptions.defaults().setComments(false));
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
        return configFromFiles.hasPath("solc.path") ? configFromFiles.getString("solc.path"): null;
    }

    public String netName() {
        return configFromFiles.hasPath("blockchain.config.name") ? configFromFiles.getString("blockchain.config.name") : null;
    }

    public String corsDomains() {
        return configFromFiles.hasPath(PROPERTY_RPC_CORS) ?
                configFromFiles.getString(PROPERTY_RPC_CORS) : null;
    }

    protected long getLongProperty(String propertyName, long defaultValue) {
        return configFromFiles.hasPath(propertyName) ? configFromFiles.getLong(propertyName) : defaultValue;
    }
    protected boolean getBooleanProperty(String propertyName, boolean defaultValue) {
        return configFromFiles.hasPath(propertyName) ? configFromFiles.getBoolean(propertyName) : defaultValue;
    }

    private void tryParseIpOrThrow() throws IOException {
        try {
            InetAddress.getByName(externalIp);
        } catch (Exception e) {
            throw new IOException("Invalid address: '" + externalIp + "'");
        }
    }
}
