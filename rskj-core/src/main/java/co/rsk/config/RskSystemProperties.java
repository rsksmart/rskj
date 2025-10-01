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

package co.rsk.config;

import co.rsk.core.RskAddress;
import co.rsk.net.discovery.table.KademliaOptions;
import co.rsk.rpc.ModuleDescription;
import com.google.common.annotations.VisibleForTesting;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;
import org.ethereum.config.Constants;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.Account;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.listener.GasPriceCalculator;
import org.ethereum.vm.PrecompiledContracts;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Created by ajlopez on 3/3/2016.
 */
public class RskSystemProperties extends SystemProperties {
    /**
     * while timeout period is lower than clean period it doesn't affect much since
     * requests will be checked after a clean period.
     **/
    private static final int PD_DEFAULT_CLEAN_PERIOD = 15000; //miliseconds
    private static final int PD_DEFAULT_TIMEOUT_MESSAGE = PD_DEFAULT_CLEAN_PERIOD - 1; //miliseconds
    private static final int PD_DEFAULT_REFRESH_PERIOD = 60000; //miliseconds
    private static final int PD_DEFAULT_MAX_BOOTSTRAP_RETRIES = -1;

    private static final String PD_MAX_BOOTSTRAP_RETRIES_CONFIG = "peer.discovery.maxBootRetries";

    private static final String REGTEST_BLOCKCHAIN_CONFIG = "regtest";

    private static final String MINER_REWARD_ADDRESS_CONFIG = "miner.reward.address";
    private static final String MINER_COINBASE_SECRET_CONFIG = "miner.coinbase.secret";
    private static final String RPC_MODULES_PATH = "rpc.modules";
    private static final String RPC_ETH_GET_LOGS_MAX_BLOCKS_TO_QUERY = "rpc.logs.maxBlocksToQuery";
    private static final String RPC_ETH_GET_LOGS_MAX_LOGS_TO_RETURN = "rpc.logs.maxLogsToReturn";
    public static final String TX_GAS_PRICE_CALCULATOR_TYPE = "transaction.gasPriceCalculatorType";

    private static final String RPC_GAS_PRICE_MULTIPLIER_CONFIG = "rpc.gasPriceMultiplier";
    private static final String DISCOVERY_BUCKET_SIZE = "peer.discovery.bucketSize";

    private static final int CHUNK_SIZE = 192;

    public static final String PROPERTY_SYNC_TOP_BEST = "sync.topBest";
    public static final String USE_PEERS_FROM_LAST_SESSION = "peer.discovery.usePeersFromLastSession";

    public static final String PROPERTY_SNAP_CLIENT_ENABLED = "sync.snapshot.client.enabled";
    public static final String PROPERTY_SNAP_CLIENT_CHECK_HISTORICAL_HEADERS = "sync.snapshot.client.checkHistoricalHeaders";
    public static final String PROPERTY_SNAP_NODES = "sync.snapshot.client.snapBootNodes";

    //TODO: REMOVE THIS WHEN THE LocalBLockTests starts working with REMASC
    private boolean remascEnabled = true;

    private Set<RskAddress> concurrentContractsDisallowed = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            PrecompiledContracts.REMASC_ADDR, PrecompiledContracts.BRIDGE_ADDR
    )));

    private List<ModuleDescription> moduleDescriptions;

    public RskSystemProperties(ConfigLoader loader) {
        super(loader);
    }

    @Nullable
    public RskAddress coinbaseAddress() {
        if (!isMinerServerEnabled()) {
            //todo(diegoll): we should carefully handle the case when you don't have a coinbase and want to execute pending blocks
            return new RskAddress(new byte[20]);
        }

        // validity checks are performed by localCoinbaseAccount
        Account account = localCoinbaseAccount();
        if (account != null) {
            return account.getAddress();
        }

        String coinbaseAddress = configFromFiles.getString(MINER_REWARD_ADDRESS_CONFIG);
        if (coinbaseAddress.length() != Constants.getMaxAddressByteLength() * 2) {
            throw new RskConfigurationException(MINER_REWARD_ADDRESS_CONFIG + " needs to be Hex encoded and 20 byte length");
        }

        return new RskAddress(coinbaseAddress);
    }

    @Nullable
    public Account localCoinbaseAccount() {
        if (!isMinerServerEnabled()) {
            return null;
        }

        // Regtest always has MINER_COINBASE_SECRET_CONFIG set in regtest.conf file. When MINER_REWARD_ADDRESS_CONFIG is set both values exist
        // and that does not pass the checks below. If MINER_REWARD_ADDRESS_CONFIG exists, that value must be used so consider that
        // special regtest case by adding this guard.
        if (configFromFiles.getString(PROPERTY_BC_CONFIG_NAME).equals(REGTEST_BLOCKCHAIN_CONFIG) &&
                configFromFiles.hasPath(MINER_REWARD_ADDRESS_CONFIG)) {
            return null;
        }

        if (configFromFiles.hasPath(MINER_COINBASE_SECRET_CONFIG) &&
                configFromFiles.hasPath(MINER_REWARD_ADDRESS_CONFIG)) {
            throw new RskConfigurationException("It is required to have only one of " + MINER_REWARD_ADDRESS_CONFIG + " or " + MINER_COINBASE_SECRET_CONFIG);
        }

        if (!configFromFiles.hasPath(MINER_COINBASE_SECRET_CONFIG) &&
                !configFromFiles.hasPath(MINER_REWARD_ADDRESS_CONFIG)) {
            throw new RskConfigurationException("It is required to either have " + MINER_REWARD_ADDRESS_CONFIG + " or " + MINER_COINBASE_SECRET_CONFIG + " to use the miner server");
        }

        if (!configFromFiles.hasPath(MINER_COINBASE_SECRET_CONFIG)) {
            return null;
        }

        String coinbaseSecret = configFromFiles.getString(MINER_COINBASE_SECRET_CONFIG);
        return new Account(ECKey.fromPrivate(HashUtil.keccak256(coinbaseSecret.getBytes(StandardCharsets.UTF_8))));
    }

    public boolean isMinerClientEnabled() {
        return configFromFiles.getBoolean("miner.client.enabled");
    }

    public Duration minerClientDelayBetweenBlocks() {
        return configFromFiles.getDuration("miner.client.delayBetweenBlocks");
    }

    public Duration minerClientDelayBetweenRefreshes() {
        return configFromFiles.getDuration("miner.client.delayBetweenRefreshes");
    }

    public boolean minerClientAutoMine() {
        return configFromFiles.getBoolean("miner.client.autoMine");
    }

    public boolean isMinerServerEnabled() {
        return configFromFiles.getBoolean("miner.server.enabled");
    }

    public boolean isMinerServerFixedClock() {
        return configFromFiles.getBoolean("miner.server.isFixedClock");
    }

    public long workSubmissionRateLimitInMills() {
        return configFromFiles.getLong("miner.server.workSubmissionRateLimitInMills");
    }

    public boolean updateWorkOnNewTransaction() {
        return getBoolean("miner.server.updateWorkOnNewTransaction", false);
    }

    public long minerMinGasPrice() {
        return configFromFiles.getLong("miner.minGasPrice");
    }

    public double minerGasUnitInDollars() {
        return getDouble("miner.gasUnitInDollars", 0);
    }

    public double minerMinFeesNotifyInDollars() {
        return getDouble("miner.minFeesNotifyInDollars", 0);
    }

    public boolean bloomServiceEnabled() {
        return getBoolean("blooms.service", false);
    }

    public int bloomNumberOfBlocks() {
        return getInt("blooms.blocks", 64);
    }

    public int bloomNumberOfConfirmations() {
        return getInt("blooms.confirmations", 400);
    }

    public boolean waitForSync() {
        return getBoolean("sync.waitForSync", false);
    }

    public boolean isWalletEnabled() {
        return getBoolean("wallet.enabled", false);
    }

    public double gasPriceMultiplier() {
        double gasPriceMultiplier = getDouble(RPC_GAS_PRICE_MULTIPLIER_CONFIG, 1.1);

        if(gasPriceMultiplier >= 0) {
            return gasPriceMultiplier;
        } else {
            throw new RskConfigurationException(RPC_GAS_PRICE_MULTIPLIER_CONFIG + " cannot be a negative number");
        }
    }

    public List<WalletAccount> walletAccounts() {
        if (!configFromFiles.hasPath("wallet.accounts")) {
            return Collections.emptyList();
        }

        List<WalletAccount> ret = new ArrayList<>();
        List<? extends ConfigObject> list = configFromFiles.getObjectList("wallet.accounts");

        for (ConfigObject configObject : list) {
            WalletAccount acc = null;
            if (configObject.get("privateKey") != null) {
                acc = new WalletAccount(configObject.toConfig().getString("privateKey"));
            }

            if (acc != null) {
                ret.add(acc);
            }
        }

        return ret;
    }

    public GarbageCollectorConfig garbageCollectorConfig() {
        return GarbageCollectorConfig.fromConfig(configFromFiles.getConfig("blockchain.gc"));
    }

    public int flushNumberOfBlocks() {
        return configFromFiles.hasPath("blockchain.flushNumberOfBlocks") && configFromFiles.getInt("blockchain.flushNumberOfBlocks") > 0 ?
                configFromFiles.getInt("blockchain.flushNumberOfBlocks") : 20;
    }

    public int soLingerTime() {
        return configFromFiles.getInt("rpc.providers.web.http.linger_time");

    }

    //TODO: REMOVE THIS WHEN THE LocalBLockTests starts working with REMASC
    public boolean isRemascEnabled() {
        return remascEnabled;
    }

    public Set<RskAddress> concurrentContractsDisallowed() {
        return concurrentContractsDisallowed;
    }

    @VisibleForTesting
    public void setConcurrentContractsDisallowed(@Nonnull Set<RskAddress> disallowed) {
        this.concurrentContractsDisallowed = Collections.unmodifiableSet(new HashSet<>(disallowed));
    }

    //TODO: REMOVE THIS WHEN THE LocalBLockTests starts working with REMASC
    public void setRemascEnabled(boolean remascEnabled) {
        this.remascEnabled = remascEnabled;
    }

    public boolean skipRemasc() {
        return getBoolean("rpc.skipRemasc", false);
    }

    public boolean usePeersFromLastSession() {
        return getBoolean(USE_PEERS_FROM_LAST_SESSION, false);
    }

    public long peerDiscoveryMessageTimeOut() {
        return getLong("peer.discovery.msg.timeout", PD_DEFAULT_TIMEOUT_MESSAGE);
    }

    public long peerDiscoveryRefreshPeriod() {
        long period = getLong("peer.discovery.refresh.period", PD_DEFAULT_REFRESH_PERIOD);

        return (period < PD_DEFAULT_REFRESH_PERIOD) ? PD_DEFAULT_REFRESH_PERIOD : period;
    }

    public boolean allowMultipleConnectionsPerHostPort() {
        return getBoolean("peer.discovery.allowMultipleConnectionsPerHostPort", true);
    }

    public long peerDiscoveryMaxBootRetries() {
        return getLong(PD_MAX_BOOTSTRAP_RETRIES_CONFIG, PD_DEFAULT_MAX_BOOTSTRAP_RETRIES);
    }

    public int discoveryBucketSize() {
        return getInt(DISCOVERY_BUCKET_SIZE, KademliaOptions.BUCKET_SIZE);
    }

    public List<ModuleDescription> getRpcModules() {
        if (this.moduleDescriptions != null) {
            return this.moduleDescriptions;
        }

        List<ModuleDescription> modules = new ArrayList<>();

        if (!configFromFiles.hasPath(RPC_MODULES_PATH)) {
            return modules;
        }

        ConfigValue modulesConfig = configFromFiles.getValue(RPC_MODULES_PATH);
        if (modulesConfig.valueType() == ConfigValueType.LIST) {
            List<? extends ConfigObject> list = configFromFiles.getObjectList(RPC_MODULES_PATH);
            modules = getModulesFromListFormat(list);
        } else {
            ConfigObject configObject = configFromFiles.getObject(RPC_MODULES_PATH);
            modules = getModulesFromObjectFormat(configObject);
        }
        this.moduleDescriptions = modules;
        return modules;
    }

    private List<ModuleDescription> getModulesFromObjectFormat(ConfigObject modulesConfigObject) {
        List<ModuleDescription> modules = new ArrayList<>();

        for (String configKey : modulesConfigObject.keySet()) {
            String name = configKey;
            Config configElement = ((ConfigObject) modulesConfigObject.get(configKey)).toConfig();
            modules.add(getModule(configElement, name));
        }
        return modules;
    }

    private List<ModuleDescription> getModulesFromListFormat(List<? extends ConfigObject> list) {
        List<ModuleDescription> modules = new ArrayList<>();
        for (ConfigObject configObject : list) {
            Config configElement = configObject.toConfig();
            String name = configElement.getString("name");
            modules.add(getModule(configElement, name));
        }
        return modules;
    }

    private ModuleDescription getModule(Config configElement, String name) {

        String version = configElement.getString("version");
        boolean enabled = configElement.getBoolean("enabled");
        List<String> enabledMethods = null;
        List<String> disabledMethods = null;
        int timeout = 0;
        Map<String, Long> methodTimeoutMap = new HashMap<>();


        if (configElement.hasPath("timeout")) {
            timeout = configElement.getInt("timeout");
        }

        if (configElement.hasPath("methods.timeout")) {
            fetchMethodTimeout(configElement, methodTimeoutMap);
        }

        if (configElement.hasPath("methods.enabled")) {
            enabledMethods = configElement.getStringList("methods.enabled");
        }

        if (configElement.hasPath("methods.disabled")) {
            disabledMethods = configElement.getStringList("methods.disabled");
        }
        return new ModuleDescription(name, version, enabled, enabledMethods, disabledMethods, timeout, methodTimeoutMap);
    }

    public boolean hasMessageRecorderEnabled() {
        return getBoolean("messages.recorder.enabled", false);
    }

    public List<String> getMessageRecorderCommands() {
        if (!configFromFiles.hasPath("messages.recorder.commands")) {
            return new ArrayList<>();
        }

        return configFromFiles.getStringList("messages.recorder.commands");
    }

    public long getTargetGasLimit() {
        return getLong("targetgaslimit", 6_800_000L);
    }

    public boolean getForceTargetGasLimit() {
        return getBoolean("forcegaslimit", true);
    }

    // Sync config properties

    public boolean getIsHeartBeatEnabled() {
        return getBoolean("sync.heartBeat.enabled", false);
    }

    public int getExpectedPeers() {
        return configFromFiles.getInt("sync.expectedPeers");
    }

    public int getTimeoutWaitingPeers() {
        return configFromFiles.getInt("sync.timeoutWaitingPeers");
    }

    public int getTimeoutWaitingRequest() {
        return configFromFiles.getInt("sync.timeoutWaitingRequest");
    }

    public int getExpirationTimePeerStatus() {
        return configFromFiles.getInt("sync.expirationTimePeerStatus");
    }

    public int getMaxSkeletonChunks() {
        return configFromFiles.getInt("sync.maxSkeletonChunks");
    }

    public int getMaxRequestedBodies() {
        return configFromFiles.getInt("sync.maxRequestedBodies");
    }

    public int getLongSyncLimit() {
        return configFromFiles.getInt("sync.longSyncLimit");
    }

    public boolean isServerSnapshotSyncEnabled() { return configFromFiles.getBoolean("sync.snapshot.server.enabled");}
    public boolean isClientSnapshotSyncEnabled() { return configFromFiles.getBoolean(PROPERTY_SNAP_CLIENT_ENABLED);}

    public boolean checkHistoricalHeaders() { return configFromFiles.getBoolean(PROPERTY_SNAP_CLIENT_CHECK_HISTORICAL_HEADERS);}

    public boolean isSnapshotParallelEnabled() { return configFromFiles.getBoolean("sync.snapshot.client.parallel");}

    public int getSnapshotChunkSize() { return 50; }

    public int getSnapshotMaxSenderRequests() { return configFromFiles.getInt("sync.snapshot.server.maxSenderRequests");}

    public int getSnapshotSyncLimit() { return configFromFiles.getInt("sync.snapshot.client.limit");}

    // its fixed, cannot be set by config file
    public int getChunkSize() {
        return CHUNK_SIZE;
    }

    public VmConfig getVmConfig() {
        return new VmConfig(vmTrace(), vmTraceOptions(), vmTraceInitStorageLimit(), dumpBlock(), dumpStyle(), getNetworkConstants().getChainId());
    }

    public long peerDiscoveryCleanPeriod() {
        return PD_DEFAULT_CLEAN_PERIOD;
    }

    public int getPeerP2PPingInterval() {
        return configFromFiles.getInt("peer.p2p.pingInterval");
    }

    public Integer getGasPriceBump() {
        return configFromFiles.getInt("transaction.gasPriceBump");
    }

    public Integer getNumOfAccountSlots() {
        return configFromFiles.getInt("transaction.accountSlots");
    }

    public boolean isAccountTxRateLimitEnabled() {
        return configFromFiles.getBoolean("transaction.accountTxRateLimit.enabled");
    }

    public Integer accountTxRateLimitCleanerPeriod() {
        return configFromFiles.getInt("transaction.accountTxRateLimit.cleanerPeriod");
    }

    public int getStatesCacheSize() {
        return configFromFiles.getInt("cache.states.max-elements");
    }

    public int getBloomsCacheSize() {
        return configFromFiles.getInt("cache.blooms.max-elements");
    }

    public int getStateRootsCacheSize() {
        return configFromFiles.getInt("cache.stateRoots.max-elements");
    }

    public int getReceiptsCacheSize() {
        return configFromFiles.getInt("cache.receipts.max-elements");
    }

    public int getBtcBlockStoreCacheSize() {
        return configFromFiles.getInt("cache.btcBlockStore.size");
    }

    public int getBtcBlockStoreCacheDepth() {
        return configFromFiles.getInt("cache.btcBlockStore.depth");
    }

    public long getVmExecutionStackSize() {
        return configFromFiles.getBytes("vm.executionStackSize");
    }

    public String cryptoLibrary() {
        return configFromFiles.getString("crypto.library");
    }

    public boolean isPeerScoringStatsReportEnabled() {
        return configFromFiles.getBoolean("scoring.summary.enabled");
    }

    public long getPeerScoringSummaryTime() {
        return configFromFiles.getLong("scoring.summary.time");
    }

    public boolean fastBlockPropagation() {
        return configFromFiles.getBoolean("peer.fastBlockPropagation");
    }

    public int getMessageQueueMaxSize() {
        return configFromFiles.getInt("peer.messageQueue.maxSizePerPeer");
    }

    public int getMessageQueuePerMinuteThreshold() {
        return configFromFiles.getInt("peer.messageQueue.thresholdPerMinutePerPeer");
    }

    public boolean rpcZeroSignatureIfRemasc() {
        return configFromFiles.getBoolean("rpc.zeroSignatureIfRemasc");
    }

    public long getRpcEthGetLogsMaxBlockToQuery() {
        return configFromFiles.getLong(RPC_ETH_GET_LOGS_MAX_BLOCKS_TO_QUERY);
    }

    public long getRpcEthGetLogsMaxLogsToReturn() {
        return configFromFiles.getLong(RPC_ETH_GET_LOGS_MAX_LOGS_TO_RETURN);
    }

    public double getTopBest() {
        if (!configFromFiles.hasPath(PROPERTY_SYNC_TOP_BEST)) {
            return 0.0D;
        }

        double value = configFromFiles.getDouble(PROPERTY_SYNC_TOP_BEST);

        if (value < 0.0D || value > 100.0D) {
            throw new RskConfigurationException(PROPERTY_SYNC_TOP_BEST + " must be between 0 and 100");
        }

        return value;
    }

    public GasPriceCalculator.GasCalculatorType getGasCalculatorType() {
        String value = configFromFiles.getString(TX_GAS_PRICE_CALCULATOR_TYPE);
        if (value == null || value.isEmpty()) {
            return GasPriceCalculator.GasCalculatorType.PLAIN_PERCENTILE;
        }
        GasPriceCalculator.GasCalculatorType gasCalculatorType = GasPriceCalculator.GasCalculatorType.fromString(value);
        if(gasCalculatorType == null) {
            throw new RskConfigurationException("Invalid gasPriceCalculatorType: " + value);
        }
        return gasCalculatorType;
    }

    private void fetchMethodTimeout(Config configElement, Map<String, Long> methodTimeoutMap) {
        configElement.getObject("methods.timeout")
                .unwrapped()
                .entrySet()
                .stream()
                .map(entry -> new AbstractMap.SimpleEntry<>(entry.getKey(), Long.parseLong(entry.getValue().toString())))
                .forEach(entry -> methodTimeoutMap.put(entry.getKey(), entry.getValue()));
    }
}
