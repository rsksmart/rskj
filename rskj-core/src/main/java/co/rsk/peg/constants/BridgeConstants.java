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

package co.rsk.peg.constants;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import java.util.List;

import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;

import java.time.Instant;

public abstract class BridgeConstants {
    protected String btcParamsString;
    protected List<BtcECKey> genesisFederationPublicKeys;
    protected Instant genesisFederationAddressCreatedAt;
    protected int btc2RskMinimumAcceptableConfirmations;
    protected int btc2RskMinimumAcceptableConfirmationsOnRsk;
    protected int rsk2BtcMinimumAcceptableConfirmations;

    protected int updateBridgeExecutionPeriod;

    protected int maxBtcHeadersPerRskBlock;

    protected Coin legacyMinimumPeginTxValue;
    protected Coin minimumPeginTxValue;
    protected Coin legacyMinimumPegoutTxValueInSatoshis;
    protected Coin minimumPegoutTxValueInSatoshis;

    protected long federationActivationAge;
    protected long federationActivationAgeLegacy;

    protected long fundsMigrationAgeSinceActivationBegin;
    protected long fundsMigrationAgeSinceActivationEnd;
    protected long specialCaseFundsMigrationAgeSinceActivationEnd;

    protected AddressBasedAuthorizer federationChangeAuthorizer;

    protected AddressBasedAuthorizer lockWhitelistChangeAuthorizer;

    protected AddressBasedAuthorizer feePerKbChangeAuthorizer;

    protected Coin genesisFeePerKb;

    protected Coin maxFeePerKb;

    protected AddressBasedAuthorizer increaseLockingCapAuthorizer;

    protected Coin initialLockingCap;

    protected int lockingCapIncrementsMultiplier;

    protected int btcHeightWhenBlockIndexActivates;
    protected int maxDepthToSearchBlocksBelowIndexActivation;
    protected long minSecondsBetweenCallsReceiveHeader;

    protected int maxDepthBlockchainAccepted;

    protected long erpFedActivationDelay;

    protected List<BtcECKey> erpFedPubKeysList;

    protected String oldFederationAddress;

    protected int minimumPegoutValuePercentageToReceiveAfterFee;

    protected int maxInputsPerPegoutTransaction;

    protected int numberOfBlocksBetweenPegouts;

    protected int btcHeightWhenPegoutTxIndexActivates;
    protected int pegoutTxIndexGracePeriodInBtcBlocks;

    public NetworkParameters getBtcParams() {
        return NetworkParameters.fromID(btcParamsString);
    }

    public String getBtcParamsString() {
        return btcParamsString;
    }

    public List<BtcECKey> getGenesisFederationPublicKeys() {
        return genesisFederationPublicKeys;
    }

    public Instant getGenesisFederationAddressCreatedAt() {
        return genesisFederationAddressCreatedAt;
    }

    public int getBtc2RskMinimumAcceptableConfirmations() {
        return btc2RskMinimumAcceptableConfirmations;
    }

    public int getBtc2RskMinimumAcceptableConfirmationsOnRsk() {
        return btc2RskMinimumAcceptableConfirmationsOnRsk;
    }

    public int getRsk2BtcMinimumAcceptableConfirmations() {
        return rsk2BtcMinimumAcceptableConfirmations;
    }

    public int getUpdateBridgeExecutionPeriod() { return updateBridgeExecutionPeriod; }

    public int getMaxBtcHeadersPerRskBlock() { return maxBtcHeadersPerRskBlock; }

    public Coin getMinimumPeginTxValue(ActivationConfig.ForBlock activations) {
        return activations.isActive(ConsensusRule.RSKIP219) ? minimumPeginTxValue : legacyMinimumPeginTxValue;
    }

    public Coin getLegacyMinimumPegoutTxValueInSatoshis() { return legacyMinimumPegoutTxValueInSatoshis; }

    public Coin getMinimumPegoutTxValueInSatoshis() { return minimumPegoutTxValueInSatoshis; }

    public long getFederationActivationAge(ActivationConfig.ForBlock activations) {
        return activations.isActive(ConsensusRule.RSKIP383)? federationActivationAge: federationActivationAgeLegacy;
    }

    public long getFundsMigrationAgeSinceActivationBegin() {
        return fundsMigrationAgeSinceActivationBegin;
    }

    public long getFundsMigrationAgeSinceActivationEnd(ActivationConfig.ForBlock activations) {
        if (activations.isActive(ConsensusRule.RSKIP357) && !activations.isActive(ConsensusRule.RSKIP374)){
            return specialCaseFundsMigrationAgeSinceActivationEnd;
        }

        return fundsMigrationAgeSinceActivationEnd;
    }

    public AddressBasedAuthorizer getFederationChangeAuthorizer() { return federationChangeAuthorizer; }

    public AddressBasedAuthorizer getLockWhitelistChangeAuthorizer() { return lockWhitelistChangeAuthorizer; }

    public AddressBasedAuthorizer getFeePerKbChangeAuthorizer() { return feePerKbChangeAuthorizer; }

    public AddressBasedAuthorizer getIncreaseLockingCapAuthorizer() { return increaseLockingCapAuthorizer; }

    public int getLockingCapIncrementsMultiplier() { return lockingCapIncrementsMultiplier; }

    public Coin getInitialLockingCap() { return initialLockingCap; }

    public Coin getGenesisFeePerKb() { return genesisFeePerKb; }

    public Coin getMaxFeePerKb() { return maxFeePerKb; }

    public Coin getMaxRbtc() { return Coin.valueOf(21_000_000, 0); }

    public int getBtcHeightWhenBlockIndexActivates() { return btcHeightWhenBlockIndexActivates; }

    public int getMaxDepthToSearchBlocksBelowIndexActivation() { return maxDepthToSearchBlocksBelowIndexActivation; }

    public long getErpFedActivationDelay() {
        return erpFedActivationDelay;
    }

    public List<BtcECKey> getErpFedPubKeysList() {
        return erpFedPubKeysList;
    }

    public String getOldFederationAddress() {
        return oldFederationAddress;
    }

    public long getMinSecondsBetweenCallsToReceiveHeader() { return minSecondsBetweenCallsReceiveHeader; }

    public int getMaxDepthBlockchainAccepted() { return maxDepthBlockchainAccepted; }

    public int getMinimumPegoutValuePercentageToReceiveAfterFee() {
        return minimumPegoutValuePercentageToReceiveAfterFee;
    }

    public int getMaxInputsPerPegoutTransaction() {
        return maxInputsPerPegoutTransaction;
    }

    public int getNumberOfBlocksBetweenPegouts() {
        return numberOfBlocksBetweenPegouts;
    }

    public int getBtcHeightWhenPegoutTxIndexActivates() {
        return btcHeightWhenPegoutTxIndexActivates;
    }

    public int getPegoutTxIndexGracePeriodInBtcBlocks() {
        return pegoutTxIndexGracePeriodInBtcBlocks;
    }
}
