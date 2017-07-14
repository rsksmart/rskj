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

package co.rsk.core;

import co.rsk.blocks.FileBlockPlayer;
import co.rsk.blocks.FileBlockRecorder;
import co.rsk.config.RskSystemProperties;
import co.rsk.net.BlockProcessResult;
import org.ethereum.config.DefaultConfig;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.ImportResult;
import org.ethereum.net.eth.EthVersion;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.util.BuildInfo;
import org.ethereum.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Created by ajlopez on 3/3/2016.
 */
@Component
public class RskFactory {

    private static final Logger logger = LoggerFactory.getLogger("general");
    private static ApplicationContext context;

    private RskFactory(){
    }

    public static Rsk createRsk() {
        return createRsk((Class) null);
    }

    public static ApplicationContext getContext(){
        return context;
    }

    public static Rsk createRsk(Class userSpringConfig) {
        return createRsk(SystemProperties.CONFIG, userSpringConfig);
    }

    public static Rsk createRsk(SystemProperties config, Class userSpringConfig) {
        logger.info("Running {},  core version: {}-{}", config.genesisInfo(), config.projectVersion(), config.projectVersionModifier());
        BuildInfo.printInfo();

        if (config.databaseReset()){
            FileUtil.recursiveDelete(config.databaseDir());
            logger.info("Database reset done");
        }

        return userSpringConfig == null ? createRsk(new Class[] {DefaultConfig.class}) :
                createRsk(DefaultConfig.class, userSpringConfig);
    }

    public static Rsk createRsk(Class ... springConfigs) {

        if (logger.isInfoEnabled()) {
            StringBuilder versions = new StringBuilder();
            for (EthVersion v : EthVersion.supported()) {
                versions.append(v.getCode()).append(", ");
            }
            versions.delete(versions.length() - 2, versions.length());
            logger.info("capability eth version: [{}]", versions);
        }

        context = new AnnotationConfigApplicationContext(springConfigs);
        final Rsk rs = context.getBean(Rsk.class);

        rs.getBlockchain().setRsk(true);

        if (RskSystemProperties.RSKCONFIG.isBlocksEnabled()) {
            String recorder = RskSystemProperties.RSKCONFIG.blocksRecorder();

            if (recorder != null) {
                String filename = recorder;

                rs.getBlockchain().setBlockRecorder(new FileBlockRecorder(filename));
            }

            final String player = RskSystemProperties.RSKCONFIG.blocksPlayer();

            if (player != null) {
                new Thread() {
                    @Override
                    public void run() {
                        try (FileBlockPlayer bplayer = new FileBlockPlayer(player)) {
                            ((RskImpl)rs).setIsPlayingBlocks(true);

                            Blockchain bc = rs.getWorldManager().getBlockchain();
                            ChannelManager cm = rs.getChannelManager();

                            for (Block block = bplayer.readBlock(); block != null; block = bplayer.readBlock()) {
                                ImportResult tryToConnectResult = bc.tryToConnect(block);
                                if (BlockProcessResult.importOk(tryToConnectResult)) {
                                    cm.broadcastBlock(block, null);
                                }
                            }
                        } catch (Exception e) {
                            logger.error("Error", e);
                        } finally {
                            ((RskImpl)rs).setIsPlayingBlocks(false);
                        }
                    }
                }.start();
            }
        }

        return rs;
    }
}
