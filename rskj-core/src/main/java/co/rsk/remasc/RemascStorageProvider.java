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

package co.rsk.remasc;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.core.Repository;
import org.ethereum.util.RLP;
import org.ethereum.vm.DataWord;

/**
 * Responsible for persisting the remasc state into the contract state
 * @see co.rsk.bridge.BridgeStorageProvider
 * @author Oscar Guindzberg
 */
class RemascStorageProvider {
    // Contract state keys used to store values
    private static final String REWARD_BALANCE_KEY = "rewardBalance";
    private static final String BURNED_BALANCE_KEY = "burnedBalance";
    private static final String SIBLINGS_KEY = "siblings";
    private static final String BROKEN_SELECTION_RULE_KEY = "brokenSelectionRule";
    private static final String FEDERATION_BALANCE_KEY = "federationBalance";

    private Repository repository;
    private RskAddress contractAddress;

    // Values retrieved / to be stored on the contract state
    private Coin rewardBalance;
    private Coin burnedBalance;
    private Coin federationBalance;
    private Boolean brokenSelectionRule;

    public RemascStorageProvider(Repository repository, RskAddress contractAddress) {
        this.repository = repository;
        this.contractAddress = contractAddress;
    }

    public Coin getFederationBalance() {
        if (federationBalance != null) {
            return federationBalance ;
        }

        DataWord address = DataWord.fromString(FEDERATION_BALANCE_KEY);

        DataWord value = this.repository.getStorageValue(this.contractAddress, address);

        if (value == null) {
            return Coin.ZERO;
        }

        return new Coin(value.getData());
    }


    public Coin getRewardBalance() {
        if (rewardBalance != null) {
            return rewardBalance;
        }

        DataWord address = DataWord.fromString(REWARD_BALANCE_KEY);

        DataWord value = this.repository.getStorageValue(this.contractAddress, address);

        if (value == null) {
            return Coin.ZERO;
        }

        return new Coin(value.getData());
    }


    public void setFederationBalance(Coin federationBalance) {
        this.federationBalance = federationBalance;
    }

    private void saveFederationBalance() {
        if (federationBalance == null) {
            return;
        }

        DataWord address = DataWord.fromString(FEDERATION_BALANCE_KEY);

        this.repository.addStorageRow(this.contractAddress, address, DataWord.valueOf(this.federationBalance.getBytes()));
    }

    public void setRewardBalance(Coin rewardBalance) {
        this.rewardBalance = rewardBalance;
    }

    private void saveRewardBalance() {
        if (rewardBalance == null) {
            return;
        }

        DataWord address = DataWord.fromString(REWARD_BALANCE_KEY);

        this.repository.addStorageRow(this.contractAddress, address, DataWord.valueOf(this.rewardBalance.getBytes()));
    }

    public Coin getBurnedBalance() {
        if (burnedBalance != null) {
            return burnedBalance;
        }

        DataWord address = DataWord.fromString(BURNED_BALANCE_KEY);

        DataWord value = this.repository.getStorageValue(this.contractAddress, address);

        if (value == null) {
            return Coin.ZERO;
        }

        return new Coin(value.getData());
    }

    public void setBurnedBalance(Coin burnedBalance) {
        this.burnedBalance = burnedBalance;
    }

    public void addToBurnBalance(Coin amountToBurn) {
        this.burnedBalance = this.getBurnedBalance().add(amountToBurn);
    }

    private void saveBurnedBalance() {
        if (burnedBalance == null) {
            return;
        }

        DataWord address = DataWord.fromString(BURNED_BALANCE_KEY);

        this.repository.addStorageRow(this.contractAddress, address, DataWord.valueOf(this.burnedBalance.getBytes()));
    }

    private void saveSiblings() {
        DataWord address = DataWord.fromString(SIBLINGS_KEY);

        // we add an empty list because Remasc state expects to have an empty siblings list after 0.5.0 activation
        this.repository.addStorageBytes(this.contractAddress, address, RLP.encodedEmptyList());
    }

    public Boolean getBrokenSelectionRule() {
        if (brokenSelectionRule!= null) {
            return brokenSelectionRule;
        }

        DataWord address = DataWord.fromString(BROKEN_SELECTION_RULE_KEY);

        byte[] bytes = this.repository.getStorageBytes(this.contractAddress, address);

        if (bytes == null || bytes.length == 0) {
            return Boolean.FALSE;
        }

        if (bytes[0] == 0) {
            return Boolean.FALSE;
        } else {
            return Boolean.TRUE;
        }
    }

    public void setBrokenSelectionRule(Boolean brokenSelectionRule) {
        this.brokenSelectionRule = brokenSelectionRule;
    }

    private void saveBrokenSelectionRule() {
        if (brokenSelectionRule == null) {
            return;
        }

        DataWord address = DataWord.fromString(BROKEN_SELECTION_RULE_KEY);

        byte[] bytes = new byte[1];

        bytes[0] = (byte)(this.brokenSelectionRule ? 1 : 0);

        this.repository.addStorageBytes(this.contractAddress, address, bytes);
    }

    /*
     * Persist all the contract data into the contract state
     */
    public void save() {
        saveRewardBalance();
        saveBurnedBalance();
        // This could be done only once because it will never change
        saveSiblings();
        saveBrokenSelectionRule();
        saveFederationBalance();
    }
}
