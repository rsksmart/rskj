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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public static final RskSystemProperties RSKCONFIG = new RskSystemProperties();
    public static final int PD_DEFAULT_REFRESH_PERIOD = 60000;
    public static final int BLOCKS_FOR_PEERS_DEFAULT = 100;

    //TODO: REMOVE THIS WHEN THE LocalBLockTests starts working with REMASC
    private boolean remascEnabled = true;

    private MessageRecorder messageRecorder;

    private List<ModuleDescription> moduleDescriptions;

    public boolean minerClientEnabled() {
        return config.hasPath("miner.client.enabled") ?
                config.getBoolean("miner.client.enabled") : false;
    }

    public boolean minerServerEnabled() {
        return config.hasPath("miner.server.enabled") ?
                config.getBoolean("miner.server.enabled") : false;
    }

    public long minerMinGasPrice() {
        return config.hasPath("miner.minGasPrice") ?
                config.getLong("miner.minGasPrice") : 0;
    }

    public double minerGasUnitInDollars() {
        return config.hasPath("miner.gasUnitInDollars") ?
                config.getDouble("miner.gasUnitInDollars") : 0;
    }

    public double minerMinFeesNotifyInDollars() {
        return config.hasPath("miner.minFeesNotifyInDollars") ?
                config.getDouble("miner.minFeesNotifyInDollars") : 0;
    }

    public boolean simulateTxs() {
        return config.hasPath("simulateTxs.enabled") ?
                config.getBoolean("simulateTxs.enabled") : false;
    }

    public boolean simulateTxsEx() {
        return config.hasPath("simulateTxsEx.enabled") ?
                config.getBoolean("simulateTxsEx.enabled") : false;
    }

    public Long simulateTxsExFounding() {
        return config.hasPath("simulateTxsEx.foundingAmount") ?
                config.getLong("simulateTxsEx.foundingAmount") : 10000000000L;
    }

    public String simulateTxsExAccountSeed() {
        return config.hasPath("simulateTxsEx.accountSeed") ?
                config.getString("simulateTxsEx.accountSeed") : "this is a seed";
    }

    public boolean waitForSync() {
        return config.hasPath("sync.waitForSync") && config.getBoolean("sync.waitForSync");
    }

    // TODO review added method
    public boolean isRpcEnabled() {
        return config.hasPath("rpc.enabled") ?
                config.getBoolean("rpc.enabled") : false;
    }

    // TODO review added method
    public int RpcPort() {
        return config.hasPath("rpc.port") ?
                config.getInt("rpc.port") : 4444;
    }

    public List<WalletAccount> walletAccounts() {
        if (!config.hasPath("wallet.accounts"))
            return Collections.EMPTY_LIST;

        List<WalletAccount> ret = new ArrayList<>();
        List<? extends ConfigObject> list = config.getObjectList("wallet.accounts");
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
        return config.hasPath("panic.enabled") ?
                config.getBoolean("panic.enabled") : false;
    }

    public boolean isBlocksEnabled() {
        return config.hasPath("blocks.enabled") ?
                config.getBoolean("blocks.enabled") : false;
    }

    public String blocksRecorder() {
        return config.hasPath("blocks.recorder") ?
                config.getString("blocks.recorder") : null;
    }

    public String blocksPlayer() {
        return config.hasPath("blocks.player") ?
                config.getString("blocks.player") : null;
    }

    public boolean isFlushEnabled() {
        return config.hasPath("blockchain.flush") ?
                config.getBoolean("blockchain.flush") : true;
    }

    public int flushNumberOfBlocks() {
        return config.hasPath("blockchain.flushNumberOfBlocks") && config.getInt("blockchain.flushNumberOfBlocks") > 0 ?
                config.getInt("blockchain.flushNumberOfBlocks") : 20;
    }

    public int soLingerTime() {
        return config.hasPath("rpc.linger.time") ?
                config.getInt("rpc.linger.time") : -1;

    }

    public int acceptorsNumber() {
        return config.hasPath("rpc.acceptors.number") ?
                config.getInt("rpc.acceptors.number") : -1;

    }

    public int acceptQueueSize() {
        return config.hasPath("rpc.accept.queue.size") ?
                config.getInt("rpc.accept.queue.size") : 0;
    }

    public String multipleUsersAccountsFile()  {
        return config.hasPath("multipleUser.file.path") ? config.getString("multipleUser.file.path") : "";
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
        return config.hasPath("peer.discovery.msg.timeout") ?
                config.getLong("peer.discovery.msg.timeout") : 30000;
    }

    public long peerDiscoveryRefreshPeriod() {
        long period = config.hasPath("peer.discovery.refresh.period") ?
                config.getLong("peer.discovery.refresh.period") : PD_DEFAULT_REFRESH_PERIOD;

        return (period < PD_DEFAULT_REFRESH_PERIOD)? PD_DEFAULT_REFRESH_PERIOD : period;
    }

    public List<ModuleDescription> getRpcModules() {
        if (this.moduleDescriptions != null)
            return this.moduleDescriptions;

        List<ModuleDescription> modules = new ArrayList<>();

        if (!config.hasPath("rpc.modules"))
            return modules;

        List<? extends ConfigObject> list = config.getObjectList("rpc.modules");

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
        return config.hasPath("messages.recorder.enabled") ?
                config.getBoolean("messages.recorder.enabled") : false;
    }

    public List<String> getMessageRecorderCommands() {
        if (!config.hasPath("messages.recorder.commands"))
            return new ArrayList<>();

        return config.getStringList("messages.recorder.commands");
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
        long ret = config.hasPath("blocksforpeers") ? config.getLong("blocksforpeers") : 0;

        return (ret > 0) ? ret : BLOCKS_FOR_PEERS_DEFAULT;

    }
}
