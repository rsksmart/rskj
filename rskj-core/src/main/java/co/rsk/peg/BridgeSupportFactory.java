/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

package co.rsk.peg;

import co.rsk.bitcoinj.core.Context;
import co.rsk.config.BridgeConstants;
import co.rsk.core.RskAddress;
import co.rsk.peg.BtcBlockStoreWithCache.Factory;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.utils.BridgeEventLoggerImpl;
import java.util.List;

import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.vm.LogInfo;

/**
 * BridgeSupportFactory allows BridgeSupport instantiation.
 */
public class BridgeSupportFactory {

    private final Factory btcBlockStoreFactory;
    private final BridgeConstants bridgeConstants;
    private final ActivationConfig activationConfig;

    public BridgeSupportFactory(Factory btcBlockStoreFactory,
            BridgeConstants bridgeConstants, ActivationConfig activationConfig) {

        this.btcBlockStoreFactory = btcBlockStoreFactory;
        this.bridgeConstants = bridgeConstants;
        this.activationConfig = activationConfig;
    }


    public BridgeSupport newInstance(Repository repository, Block executionBlock,
            RskAddress contractAddress, List<LogInfo> logs) {
        ActivationConfig.ForBlock activations = activationConfig.forBlock(executionBlock.getNumber());
        Context btcContext = new Context(bridgeConstants.getBtcParams());

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            contractAddress,
            bridgeConstants,
            activations
        );

        FederationSupport federationSupport = new FederationSupport(bridgeConstants, provider, executionBlock);

        BridgeEventLogger eventLogger;
        if (logs == null) {
            eventLogger = null;
        } else {
            eventLogger = new BridgeEventLoggerImpl(bridgeConstants, activations, logs);
        }

        BtcLockSenderProvider btcLockSenderProvider = new BtcLockSenderProvider();

        return new BridgeSupport(
                bridgeConstants,
                provider,
                eventLogger,
                btcLockSenderProvider,
                repository,
                executionBlock,
                btcContext,
                federationSupport,
                btcBlockStoreFactory,
                activations
        );
    }
}