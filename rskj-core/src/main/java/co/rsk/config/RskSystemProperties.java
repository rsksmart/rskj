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

import co.rsk.net.eth.MessageFilter;
import co.rsk.net.eth.MessageRecorder;
import co.rsk.net.eth.WriterMessageRecorder;
import co.rsk.rpc.ModuleDescription;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.Account;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import javax.annotation.Nullable;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by ajlopez on 3/3/2016.
 */
public class RskSystemProperties extends SystemProperties {
    private static final Logger logger = LoggerFactory.getLogger("config");

    public static final RskSystemProperties CONFIG = new RskSystemProperties();

    public static final int PD_DEFAULT_REFRESH_PERIOD = 60000;
    public static final int BLOCKS_FOR_PEERS_DEFAULT = 100;
    private static final String MINER_REWARD_ADDRESS_CONFIG = "miner.reward.address";
    private static final String MINER_COINBASE_SECRET_CONFIG = "miner.coinbase.secret";

    //TODO: REMOVE THIS WHEN THE LocalBLockTests starts working with REMASC
    private boolean remascEnabled = true;

    private MessageRecorder messageRecorder;

    private List<ModuleDescription> moduleDescriptions;

    @Nullable
    public byte[] coinbaseAddress() {
        if (!minerServerEnabled()) {
            return null;
        }

        // validity checks are performed by localCoinbaseAccount
        Account account = localCoinbaseAccount();
        if (account != null) {
            return account.getAddress();
        }

        String coinbaseAddress = configFromFiles.getString(MINER_REWARD_ADDRESS_CONFIG);
        if (coinbaseAddress.length() != 64) {
            throw new RskConfigurationException(MINER_REWARD_ADDRESS_CONFIG + " needs to be Hex encoded and 32 byte length");
        }

        return Hex.decode(coinbaseAddress);
    }

    @Nullable
    public Account localCoinbaseAccount() {
        if (!minerServerEnabled()) {
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
        return new Account(ECKey.fromPrivate(HashUtil.sha3(coinbaseSecret.getBytes(StandardCharsets.UTF_8))));
    }

    public boolean minerClientEnabled() {
        return configFromFiles.hasPath("miner.client.enabled") ?
                configFromFiles.getBoolean("miner.client.enabled") : false;
    }

    public boolean minerServerEnabled() {
        return configFromFiles.hasPath("miner.server.enabled") ?
                configFromFiles.getBoolean("miner.server.enabled") : false;
    }

    public long minerMinGasPrice() {
        return configFromFiles.hasPath("miner.minGasPrice") ?
                configFromFiles.getLong("miner.minGasPrice") : 0;
    }

    public double minerGasUnitInDollars() {
        return configFromFiles.hasPath("miner.gasUnitInDollars") ?
                configFromFiles.getDouble("miner.gasUnitInDollars") : 0;
    }

    public double minerMinFeesNotifyInDollars() {
        return configFromFiles.hasPath("miner.minFeesNotifyInDollars") ?
                configFromFiles.getDouble("miner.minFeesNotifyInDollars") : 0;
    }

    public boolean simulateTxs() {
        return configFromFiles.hasPath("simulateTxs.enabled") ?
                configFromFiles.getBoolean("simulateTxs.enabled") : false;
    }

    public boolean simulateTxsEx() {
        return configFromFiles.hasPath("simulateTxsEx.enabled") ?
                configFromFiles.getBoolean("simulateTxsEx.enabled") : false;
    }

    public Long simulateTxsExFounding() {
        return configFromFiles.hasPath("simulateTxsEx.foundingAmount") ?
                configFromFiles.getLong("simulateTxsEx.foundingAmount") : 10000000000L;
    }

    public String simulateTxsExAccountSeed() {
        return configFromFiles.hasPath("simulateTxsEx.accountSeed") ?
                configFromFiles.getString("simulateTxsEx.accountSeed") : "this is a seed";
    }

    public boolean waitForSync() {
        return configFromFiles.hasPath("sync.waitForSync") && configFromFiles.getBoolean("sync.waitForSync");
    }

    // TODO review added method
    public boolean isRpcEnabled() {
        return configFromFiles.hasPath("rpc.enabled") ?
                configFromFiles.getBoolean("rpc.enabled") : false;
    }

    // TODO review added method
    public int rpcPort() {
        return configFromFiles.hasPath("rpc.port") ?
                configFromFiles.getInt("rpc.port") : 4444;
    }

    public boolean isWalletEnabled() {
        return configFromFiles.hasPath("wallet.enabled") &&
                configFromFiles.getBoolean("wallet.enabled");
    }

    public List<WalletAccount> walletAccounts() {
        if (!configFromFiles.hasPath("wallet.accounts"))
            return Collections.emptyList();

        List<WalletAccount> ret = new ArrayList<>();
        List<? extends ConfigObject> list = configFromFiles.getObjectList("wallet.accounts");
        for (ConfigObject configObject : list) {
            WalletAccount acc = null;
            if (configObject.get("privateKey") != null)
                acc = new WalletAccount(configObject.toConfig().getString("privateKey"));

            if (acc != null)
                ret.add(acc);
        }

        return ret;
    }

    public boolean isPanicExitEnabled() {
        return configFromFiles.hasPath("panic.enabled") ?
                configFromFiles.getBoolean("panic.enabled") : false;
    }

    public boolean isBlocksEnabled() {
        return configFromFiles.hasPath("blocks.enabled") ?
                configFromFiles.getBoolean("blocks.enabled") : false;
    }

    public String blocksRecorder() {
        return configFromFiles.hasPath("blocks.recorder") ?
                configFromFiles.getString("blocks.recorder") : null;
    }

    public String blocksPlayer() {
        return configFromFiles.hasPath("blocks.player") ?
                configFromFiles.getString("blocks.player") : null;
    }

    public boolean isFlushEnabled() {
        return configFromFiles.hasPath("blockchain.flush") ?
                configFromFiles.getBoolean("blockchain.flush") : true;
    }

    public int flushNumberOfBlocks() {
        return configFromFiles.hasPath("blockchain.flushNumberOfBlocks") && configFromFiles.getInt("blockchain.flushNumberOfBlocks") > 0 ?
                configFromFiles.getInt("blockchain.flushNumberOfBlocks") : 20;
    }

    public int soLingerTime() {
        return configFromFiles.hasPath("rpc.linger.time") ?
                configFromFiles.getInt("rpc.linger.time") : -1;

    }

    public int acceptorsNumber() {
        return configFromFiles.hasPath("rpc.acceptors.number") ?
                configFromFiles.getInt("rpc.acceptors.number") : -1;

    }

    public int acceptQueueSize() {
        return configFromFiles.hasPath("rpc.accept.queue.size") ?
                configFromFiles.getInt("rpc.accept.queue.size") : 0;
    }

    public String multipleUsersAccountsFile()  {
        return configFromFiles.hasPath("multipleUser.file.path") ? configFromFiles.getString("multipleUser.file.path") : "";
    }

    //TODO: REMOVE THIS WHEN THE LocalBLockTests starts working with REMASC
    public boolean isRemascEnabled() {
        return remascEnabled;
    }

    //TODO: REMOVE THIS WHEN THE LocalBLockTests starts working with REMASC
    public void disableRemasc() {
        this.remascEnabled = false;
    }

    //TODO: REMOVE THIS WHEN THE LocalBLockTests starts working with REMASC
    public void enableRemasc() {
        this.remascEnabled = true;
    }

    public long peerDiscoveryMessageTimeOut() {
        return configFromFiles.hasPath("peer.discovery.msg.timeout") ?
                configFromFiles.getLong("peer.discovery.msg.timeout") : 30000;
    }

    public long peerDiscoveryRefreshPeriod() {
        long period = configFromFiles.hasPath("peer.discovery.refresh.period") ?
                configFromFiles.getLong("peer.discovery.refresh.period") : PD_DEFAULT_REFRESH_PERIOD;

        return (period < PD_DEFAULT_REFRESH_PERIOD)? PD_DEFAULT_REFRESH_PERIOD : period;
    }

    public List<ModuleDescription> getRpcModules() {
        if (this.moduleDescriptions != null)
            return this.moduleDescriptions;

        List<ModuleDescription> modules = new ArrayList<>();

        if (!configFromFiles.hasPath("rpc.modules"))
            return modules;

        List<? extends ConfigObject> list = configFromFiles.getObjectList("rpc.modules");

        for (ConfigObject configObject : list) {
            Config configElement = configObject.toConfig();
            String name = configElement.getString("name");
            String version = configElement.getString("version");
            boolean enabled = configElement.getBoolean("enabled");
            List<String> enabledMethods = null;
            List<String> disabledMethods = null;

            if (configElement.hasPath("methods.enabled"))
                enabledMethods = configElement.getStringList ("methods.enabled");
            if (configElement.hasPath("methods.disabled"))
                disabledMethods = configElement.getStringList ("methods.disabled");

            modules.add(new ModuleDescription(name, version, enabled, enabledMethods, disabledMethods));
        }

        this.moduleDescriptions = modules;

        return modules;
    }

    public boolean hasMessageRecorderEnabled() {
        return configFromFiles.hasPath("messages.recorder.enabled") ?
                configFromFiles.getBoolean("messages.recorder.enabled") : false;
    }

    public List<String> getMessageRecorderCommands() {
        if (!configFromFiles.hasPath("messages.recorder.commands"))
            return new ArrayList<>();

        return configFromFiles.getStringList("messages.recorder.commands");
    }

    public MessageRecorder getMessageRecorder() {
        if (messageRecorder != null)
            return messageRecorder;

        if (!hasMessageRecorderEnabled())
            return null;

        String database = this.databaseDir();
        String filename = "messages";
        Path filePath;

        if (Paths.get(database).isAbsolute())
            filePath = Paths.get(database, filename);
        else
            filePath = Paths.get(System.getProperty("user.dir"), database, filename);

        String fullFilename = filePath.toString();

        MessageFilter filter = new MessageFilter(this.getMessageRecorderCommands());

        try {
            messageRecorder = new WriterMessageRecorder(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fullFilename), StandardCharsets.UTF_8)), filter);
        }
        catch (IOException ex) {
            logger.error("Exception creating message recorder: ", ex);
        }

        return messageRecorder;
    }

    public long getBlocksForPeers() {
        return getLongProperty("blocksforpeers", BLOCKS_FOR_PEERS_DEFAULT);
    }

    public long getTargetGasLimit() {
        return getLongProperty("targetgaslimit",
                getBlockchainConfig().getCommonConstants().getTargetGasLimit());
    }

    public boolean getForceTargetGasLimit() {
        return getBooleanProperty("forcegaslimit", true);
    }

    /**
     * SYNC CONFIG PROPERTIES
     * **/
    public int getExpectedPeers() {
        return getInt("sync.expectedPeers", 5);
    }

    public int getTimeoutWaitingPeers() {
        return getInt("sync.timeoutWaitingPeers", 1);
    }

    public int getTimeoutWaitingRequest() {
        return getInt("sync.timeoutWaitingRequest", 30);
    }

    public int getExpirationTimePeerStatus() {
        return getInt("sync.expirationTimePeerStatus", 10);
    }

    public int getMaxSkeletonChunks() {
        return getInt("sync.maxSkeletonChunks", 20);
    }

    public int getChunkSize() {
        return getInt("sync.chunkSize", 192);
    }
}
