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
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.utils.BridgeEventLoggerImpl;
import co.rsk.peg.utils.PegUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.vm.LogInfo;

import java.util.List;

/**
 * BridgeSupportFactory allows BridgeSupport instantiation.
 */
public class BridgeSupportFactory {

    private final Factory btcBlockStoreFactory;
    private final BridgeConstants bridgeConstants;
    private final ActivationConfig activationConfig;
    private final PegUtils pegUtils;

    public BridgeSupportFactory(Factory btcBlockStoreFactory,
            BridgeConstants bridgeConstants, ActivationConfig activationConfig, PegUtils pegUtils) {

        this.btcBlockStoreFactory = btcBlockStoreFactory;
        this.bridgeConstants = bridgeConstants;
        this.activationConfig = activationConfig;
        this.pegUtils = pegUtils;
    }

    public BridgeSupport newInstance(Repository repository, Block executionBlock,
            RskAddress contractAddress, List<LogInfo> logs) {
        ActivationConfig.ForBlock activations = activationConfig.forBlock(executionBlock.getNumber());
        Context btcContext = new Context(bridgeConstants.getBtcParams());

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            contractAddress,
            bridgeConstants,
            activations,
            pegUtils.getBridgeSerializationUtils()
        );

        FederationSupport federationSupport = new FederationSupport(bridgeConstants, provider, executionBlock);

        BridgeEventLogger eventLogger;
        if (logs == null) {
            eventLogger = null;
        } else {
            eventLogger = new BridgeEventLoggerImpl(bridgeConstants, activations, logs);
        }

        BtcLockSenderProvider btcLockSenderProvider = new BtcLockSenderProvider();
        PeginInstructionsProvider peginInstructionsProvider = new PeginInstructionsProvider();

        return new BridgeSupport(
                bridgeConstants,
                provider,
                eventLogger,
                btcLockSenderProvider,
                peginInstructionsProvider,
                repository,
                executionBlock,
                btcContext,
                federationSupport,
                btcBlockStoreFactory,
                activations,
                pegUtils
        );
    }
}
