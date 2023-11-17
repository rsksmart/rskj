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

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.config.BridgeConstants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Block;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class FederationSupport {

    private enum StorageFederationReference { NONE, NEW, OLD, GENESIS }

    private final BridgeStorageProvider provider;
    private final BridgeConstants bridgeConstants;
    private final Block executionBlock;
    private final ActivationConfig.ForBlock activations;

    public FederationSupport(BridgeConstants bridgeConstants, BridgeStorageProvider provider, Block executionBlock, ActivationConfig.ForBlock activations) {
        this.provider = provider;
        this.bridgeConstants = bridgeConstants;
        this.executionBlock = executionBlock;
        this.activations = activations;
    }

    /**
     * Returns the federation's size
     * @return the federation size
     */
    public int getFederationSize() {
        return getActiveFederation().getMembersPublicKeys().size();
    }

    /**
     * Returns the BTC public key of the federation's federator at the given index
     * @param index the federator's index (zero-based)
     * @return the federator's public key
     */
    public byte[] getFederatorBtcPublicKey(int index) {
        List<BtcECKey> publicKeys = getActiveFederation().getMembersPublicKeys();

        if (index < 0 || index >= publicKeys.size()) {
            throw new IndexOutOfBoundsException(String.format("Federator index must be between 0 and %d", publicKeys.size() - 1));
        }

        return publicKeys.get(index).getPubKey();
    }

    /**
     * Returns the public key of given type of the federation's federator at the given index
     * @param index the federator's index (zero-based)
     * @param keyType the key type
     * @return the federator's public key
     */
    public byte[] getFederatorPublicKeyOfType(int index, FederationMember.KeyType keyType) {
        return getMemberPublicKeyOfType(getActiveFederation().getMembers(), index, keyType, "Federator");
    }

    /**
     * Returns the compressed public key of given type of the member list at the given index
     * Throws a custom index out of bounds exception when appropiate
     * @param members the list of federation members
     * @param index the federator's index (zero-based)
     * @param keyType the key type
     * @param errorPrefix the index out of bounds error prefix
     * @return the federation member's public key
     */
    public byte[] getMemberPublicKeyOfType(List<FederationMember> members, int index, FederationMember.KeyType keyType, String errorPrefix) {
        if (index < 0 || index >= members.size()) {
            throw new IndexOutOfBoundsException(String.format("%s index must be between 0 and %d", errorPrefix, members.size() - 1));
        }

        return members.get(index).getPublicKey(keyType).getPubKey(true);
    }

    /**
     * Returns the currently active federation.
     * See getActiveFederationReference() for details.
     *
     * @return the currently active federation.
     */
    public Federation getActiveFederation() {
        switch (getActiveFederationReference()) {
            case NEW:
                return provider.getNewFederation();
            case OLD:
                return provider.getOldFederation();
            case GENESIS:
            default:
                return bridgeConstants.getGenesisFederation();
        }
    }

    /**
     * Returns the currently retiring federation.
     * See getRetiringFederationReference() for details.
     *
     * @return the retiring federation.
     */
    @Nullable
    public Federation getRetiringFederation() {
        switch (getRetiringFederationReference()) {
            case OLD:
                return provider.getOldFederation();
            case NONE:
            default:
                return null;
        }
    }

    public List<UTXO> getActiveFederationBtcUTXOs() throws IOException {
        switch (getActiveFederationReference()) {
            case OLD:
                return provider.getOldFederationBtcUTXOs();
            case NEW:
            case GENESIS:
            default:
                return provider.getNewFederationBtcUTXOs();
        }
    }

    public List<UTXO> getRetiringFederationBtcUTXOs() throws IOException {
        switch (getRetiringFederationReference()) {
            case OLD:
                return provider.getOldFederationBtcUTXOs();
            case NONE:
            default:
                return Collections.emptyList();
        }
    }

    public boolean amAwaitingFederationActivation() {
        Federation newFederation = provider.getNewFederation();
        Federation oldFederation = provider.getOldFederation();

        return newFederation != null && oldFederation != null && !shouldFederationBeActive(newFederation);
    }

    /**
     * Returns the currently active federation reference.
     * Logic is as follows:
     * When no "new" federation is recorded in the blockchain, then return GENESIS
     * When a "new" federation is present and no "old" federation is present, then return NEW
     * When both "new" and "old" federations are present, then
     * 1) If the "new" federation is at least bridgeConstants::getFederationActivationAge() blocks old,
     * return the NEW
     * 2) Otherwise, return OLD
     *
     * @return a reference to where the currently active federation is stored.
     */
    private StorageFederationReference getActiveFederationReference() {
        Federation newFederation = provider.getNewFederation();

        // No new federation in place, then the active federation
        // is the genesis federation
        if (newFederation == null) {
            return StorageFederationReference.GENESIS;
        }

        Federation oldFederation = provider.getOldFederation();

        // No old federation in place, then the active federation
        // is the new federation
        if (oldFederation == null) {
            return StorageFederationReference.NEW;
        }

        // Both new and old federations in place
        // If the minimum age has gone by for the new federation's
        // activation, then that federation is the currently active.
        // Otherwise, the old federation is still the currently active.
        if (shouldFederationBeActive(newFederation)) {
            return StorageFederationReference.NEW;
        }

        return StorageFederationReference.OLD;
    }

    /**
     * Returns the currently retiring federation reference.
     * Logic is as follows:
     * When no "new" or "old" federation is recorded in the blockchain, then return empty.
     * When both "new" and "old" federations are present, then
     * 1) If the "new" federation is at least bridgeConstants::getFederationActivationAge() blocks old,
     * return OLD
     * 2) Otherwise, return empty
     *
     * @return the retiring federation.
     */
    private StorageFederationReference getRetiringFederationReference() {
        Federation newFederation = provider.getNewFederation();
        Federation oldFederation = provider.getOldFederation();

        if (oldFederation == null || newFederation == null) {
            return StorageFederationReference.NONE;
        }

        // Both new and old federations in place
        // If the minimum age has gone by for the new federation's
        // activation, then the old federation is the currently retiring.
        // Otherwise, there is no retiring federation.
        if (shouldFederationBeActive(newFederation)) {
            return StorageFederationReference.OLD;
        }

        return StorageFederationReference.NONE;
    }

    private boolean shouldFederationBeActive(Federation federation) {
        long federationAge = executionBlock.getNumber() - federation.getCreationBlockNumber();
        return federationAge >= bridgeConstants.getFederationActivationAge(activations);
    }
}
