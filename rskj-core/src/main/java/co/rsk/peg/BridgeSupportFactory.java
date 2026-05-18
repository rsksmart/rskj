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
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.BtcBlockStoreWithCache.Factory;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.federation.*;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.feeperkb.*;
import co.rsk.peg.feeperkb.constants.FeePerKbConstants;
import co.rsk.peg.lockingcap.*;
import co.rsk.peg.lockingcap.constants.LockingCapConstants;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import co.rsk.peg.storage.BridgeStorageAccessorImpl;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.union.UnionBridgeStorageProvider;
import co.rsk.peg.union.UnionBridgeStorageProviderImpl;
import co.rsk.peg.union.UnionBridgeSupport;
import co.rsk.peg.union.UnionBridgeSupportImpl;
import co.rsk.peg.utils.*;
import co.rsk.peg.whitelist.*;
import co.rsk.peg.whitelist.constants.WhitelistConstants;
import java.util.List;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.vm.LogInfo;

/**
 * BridgeSupportFactory allows BridgeSupport instantiation.
 */
public class BridgeSupportFactory {

    private final Factory btcBlockStoreFactory;
    private final BridgeConstants bridgeConstants;
    private final ActivationConfig activationConfig;
    private final SignatureCache signatureCache;

    public BridgeSupportFactory(
        Factory btcBlockStoreFactory,
        BridgeConstants bridgeConstants,
        ActivationConfig activationConfig,
        SignatureCache signatureCache) {

        this.btcBlockStoreFactory = btcBlockStoreFactory;
        this.bridgeConstants = bridgeConstants;
        this.activationConfig = activationConfig;
        this.signatureCache = signatureCache;
    }

    public BridgeSupport newInstance(
        Repository repository,
        Block executionBlock,
        List<LogInfo> logs) {

        ActivationConfig.ForBlock activations = activationConfig.forBlock(executionBlock.getNumber());
        Context btcContext = new Context(bridgeConstants.getBtcParams());
        NetworkParameters networkParameters = bridgeConstants.getBtcParams();

        StorageAccessor bridgeStorageAccessor = new BridgeStorageAccessorImpl(repository);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            networkParameters,
            activations
        );

        FeePerKbSupport feePerKbSupport = getFeePerKbSupportInstance(bridgeStorageAccessor);
        WhitelistSupport whitelistSupport = getWhitelistSupportInstance(bridgeStorageAccessor, activations);
        FederationSupport federationSupport = getFederationSupportInstance(
            bridgeStorageAccessor,
            executionBlock,
            activations
        );
        LockingCapSupport lockingCapSupport = getLockingCapSupportInstance(bridgeStorageAccessor, activations);

        BridgeEventLogger eventLogger = null;
        if (logs != null) {
            if (activations.isActive(ConsensusRule.RSKIP146)) {
                eventLogger = new BridgeEventLoggerImpl(bridgeConstants, activations, logs);
            } else {
                eventLogger = new BrigeEventLoggerLegacyImpl(bridgeConstants, activations, logs);
            }
        }

        UnionBridgeSupport unionBridgeSupport = getUnionBridgeSupportInstance(bridgeStorageAccessor, eventLogger);

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
            feePerKbSupport,
            whitelistSupport,
            federationSupport,
            lockingCapSupport,
            unionBridgeSupport,
            btcBlockStoreFactory,
            activations,
            signatureCache
        );
    }

    private UnionBridgeSupport getUnionBridgeSupportInstance(StorageAccessor bridgeStorageAccessor, BridgeEventLogger eventLogger) {
        UnionBridgeStorageProvider unionBridgeStorageProvider = new UnionBridgeStorageProviderImpl(bridgeStorageAccessor);

        return new UnionBridgeSupportImpl(
            bridgeConstants.getUnionBridgeConstants(),
            unionBridgeStorageProvider,
            signatureCache,
            eventLogger
        );
    }

    private FeePerKbSupport getFeePerKbSupportInstance(StorageAccessor bridgeStorageAccessor) {
        FeePerKbConstants feePerKbConstants = bridgeConstants.getFeePerKbConstants();
        FeePerKbStorageProvider feePerKbStorageProvider = new FeePerKbStorageProviderImpl(bridgeStorageAccessor);

        return new FeePerKbSupportImpl(feePerKbConstants, feePerKbStorageProvider);
    }

    private WhitelistSupport getWhitelistSupportInstance(StorageAccessor bridgeStorageAccessor, ActivationConfig.ForBlock activations) {
        WhitelistConstants whitelistConstants = bridgeConstants.getWhitelistConstants();
        WhitelistStorageProvider whitelistStorageProvider = new WhitelistStorageProviderImpl(bridgeStorageAccessor);

        return new WhitelistSupportImpl(
            whitelistConstants,
            whitelistStorageProvider,
            activations,
            signatureCache
        );
    }

    private FederationSupport getFederationSupportInstance(StorageAccessor bridgeStorageAccessor, Block rskExecutionBlock, ActivationConfig.ForBlock activations) {
        FederationConstants federationConstants = bridgeConstants.getFederationConstants();
        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);
        return new FederationSupportImpl(federationConstants, federationStorageProvider, rskExecutionBlock, activations);
    }

    private LockingCapSupport getLockingCapSupportInstance(StorageAccessor bridgeStorageAccessor, ActivationConfig.ForBlock activations) {
        LockingCapConstants lockingCapConstants = bridgeConstants.getLockingCapConstants();
        LockingCapStorageProvider lockingCapStorageProvider = new LockingCapStorageProviderImpl(bridgeStorageAccessor);

        return new LockingCapSupportImpl(lockingCapStorageProvider, activations, lockingCapConstants, signatureCache);
    }
}
