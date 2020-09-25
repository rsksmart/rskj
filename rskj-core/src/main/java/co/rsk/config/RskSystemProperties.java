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
import co.rsk.rpc.ModuleDescription;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import org.ethereum.config.Constants;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.Account;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by ajlopez on 3/3/2016.
 */
public class RskSystemProperties extends SystemProperties {
    /** while timeout period is lower than clean period it doesn't affect much since
    requests will be checked after a clean period.
     **/
    private static final int PD_DEFAULT_CLEAN_PERIOD = 15000; //miliseconds
    private static final int PD_DEFAULT_TIMEOUT_MESSAGE = PD_DEFAULT_CLEAN_PERIOD - 1; //miliseconds
    private static final int PD_DEFAULT_REFRESH_PERIOD = 60000; //miliseconds

    private static final String REGTEST_BLOCKCHAIN_CONFIG = "regtest";

    private static final String MINER_REWARD_ADDRESS_CONFIG = "miner.reward.address";
    private static final String MINER_COINBASE_SECRET_CONFIG = "miner.coinbase.secret";
    private static final int CHUNK_SIZE = 192;

    //TODO: REMOVE THIS WHEN THE LocalBLockTests starts working with REMASC
    private boolean remascEnabled = true;

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
        if(configFromFiles.getString(PROPERTY_BC_CONFIG_NAME).equals(REGTEST_BLOCKCHAIN_CONFIG) &&
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

    public long minerMinGasPrice() {
        return configFromFiles.getLong("miner.minGasPrice");
    }

    public double minerGasUnitInDollars() {
        return getDouble("miner.gasUnitInDollars", 0);
    }

    public double minerMinFeesNotifyInDollars() {
        return getDouble("miner.minFeesNotifyInDollars", 0);
    }

    public boolean waitForSync() {
        return getBoolean("sync.waitForSync", false);
    }

    public boolean isWalletEnabled() {
        return getBoolean("wallet.enabled", false);
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

    //TODO: REMOVE THIS WHEN THE LocalBLockTests starts working with REMASC
    public void setRemascEnabled(boolean remascEnabled) {
        this.remascEnabled = remascEnabled;
    }

    public long peerDiscoveryMessageTimeOut() {
        return getLong("peer.discovery.msg.timeout", PD_DEFAULT_TIMEOUT_MESSAGE);
    }

    public long peerDiscoveryRefreshPeriod() {
        long period = getLong("peer.discovery.refresh.period", PD_DEFAULT_REFRESH_PERIOD);

        return (period < PD_DEFAULT_REFRESH_PERIOD)? PD_DEFAULT_REFRESH_PERIOD : period;
    }

    public List<ModuleDescription> getRpcModules() {
        if (this.moduleDescriptions != null) {
            return this.moduleDescriptions;
        }

        List<ModuleDescription> modules = new ArrayList<>();

        if (!configFromFiles.hasPath("rpc.modules")) {
            return modules;
        }

        List<? extends ConfigObject> list = configFromFiles.getObjectList("rpc.modules");

        for (ConfigObject configObject : list) {
            Config configElement = configObject.toConfig();
            String name = configElement.getString("name");
            String version = configElement.getString("version");
            boolean enabled = configElement.getBoolean("enabled");
            List<String> enabledMethods = null;
            List<String> disabledMethods = null;

            if (configElement.hasPath("methods.enabled")) {
                enabledMethods = configElement.getStringList("methods.enabled");
            }

            if (configElement.hasPath("methods.disabled")) {
                disabledMethods = configElement.getStringList("methods.disabled");
            }

            modules.add(new ModuleDescription(name, version, enabled, enabledMethods, disabledMethods));
        }

        this.moduleDescriptions = modules;

        return modules;
    }

    public boolean hasMessageRecorderEnabled() {
        return getBoolean("messages.recorder.enabled",false);
    }

    public List<String> getMessageRecorderCommands() {
        if (!configFromFiles.hasPath("messages.recorder.commands")) {
            return new ArrayList<>();
        }

        return configFromFiles.getStringList("messages.recorder.commands");
    }

    public long getTargetGasLimit() {
        return getLong("targetgaslimit",6_800_000L);
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

    public int getPeerP2PPingInterval(){
        return configFromFiles.getInt("peer.p2p.pingInterval");
    }

    public Integer getGasPriceBump() {
        return configFromFiles.getInt("transaction.gasPriceBump");
    }

    public Integer getNumOfAccountSlots() {
        return configFromFiles.getInt("transaction.accountSlots");
    }

    public int getStatesCacheSize() {
        return configFromFiles.getInt("cache.states.max-elements");
    }

    public int getReceiptsCacheSize() {
        return configFromFiles.getInt("cache.receipts.max-elements");
    }

    public long getVmExecutionStackSize() {
        return configFromFiles.getBytes("vm.executionStackSize");
    }

    public String cryptoLibrary() { return configFromFiles.getString("crypto.library");}
}
