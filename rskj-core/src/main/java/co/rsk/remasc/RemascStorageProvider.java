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
import org.ethereum.util.RLPList;
import org.ethereum.vm.DataWord;
import org.spongycastle.util.BigIntegers;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Responsible for persisting the remasc state into the contract state
 * @see co.rsk.peg.BridgeStorageProvider
 * @author Oscar Guindzberg
 */
class RemascStorageProvider {
    // Contract state keys used to store values
    private static final String REWARD_BALANCE_KEY = "rewardBalance";
    private static final String BURNED_BALANCE_KEY = "burnedBalance";
    private static final String SIBLINGS_KEY = "siblings";
    private static final String BROKEN_SELECTION_RULE_KEY = "brokenSelectionRule";

    private Repository repository;
    private RskAddress contractAddress;

    // Values retrieved / to be stored on the contract state
    private Coin rewardBalance;
    private Coin burnedBalance;
    private SortedMap<Long, List<Sibling>> siblings;
    private Boolean brokenSelectionRule;

    public RemascStorageProvider(Repository repository, RskAddress contractAddress) {
        this.repository = repository;
        this.contractAddress = contractAddress;
    }

    public Coin getRewardBalance() {
        if (rewardBalance != null) {
            return rewardBalance;
        }

        DataWord address = new DataWord(REWARD_BALANCE_KEY.getBytes(StandardCharsets.UTF_8));

        DataWord value = this.repository.getStorageValue(this.contractAddress, address);

        if (value == null) {
            return Coin.ZERO;
        }

        return new Coin(value.getData());
    }

    public void setRewardBalance(Coin rewardBalance) {
        this.rewardBalance = rewardBalance;
    }

    private void saveRewardBalance() {
        if (rewardBalance == null) {
            return;
        }

        DataWord address = new DataWord(REWARD_BALANCE_KEY.getBytes(StandardCharsets.UTF_8));

        this.repository.addStorageRow(this.contractAddress, address, new DataWord(this.rewardBalance.getBytes()));
    }

    public Coin getBurnedBalance() {
        if (burnedBalance != null) {
            return burnedBalance;
        }

        DataWord address = new DataWord(BURNED_BALANCE_KEY.getBytes(StandardCharsets.UTF_8));

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

        DataWord address = new DataWord(BURNED_BALANCE_KEY.getBytes(StandardCharsets.UTF_8));

        this.repository.addStorageRow(this.contractAddress, address, new DataWord(this.burnedBalance.getBytes()));
    }

    public SortedMap<Long, List<Sibling>> getSiblings() {
        if (siblings != null) {
            return siblings;
        }

        DataWord address = new DataWord(SIBLINGS_KEY.getBytes(StandardCharsets.UTF_8));

        byte[] bytes = this.repository.getStorageBytes(this.contractAddress, address);

        siblings = getSiblingsFromBytes(bytes);

        return siblings;
    }

    public static SortedMap<Long, List<Sibling>> getSiblingsFromBytes(byte[] bytes) {
        SortedMap<Long, List<Sibling>> siblings = new TreeMap<>();

        if (bytes == null || bytes.length == 0) {
            return siblings;
        }

        RLPList rlpList = (RLPList) RLP.decode2(bytes).get(0);

        int nentries = rlpList.size() / 2;

        for (int k = 0; k < nentries; k++) {
            byte[] bytesKey = rlpList.get(k * 2).getRLPData();
            byte[] bytesValue = rlpList.get(k * 2 + 1).getRLPData();

            long longKey = bytesKey == null ? 0 : BigIntegers.fromUnsignedByteArray(bytesKey).longValue();

            Long key = Long.valueOf(longKey);

            RLPList rlpSiblingList = (RLPList) RLP.decode2(bytesValue).get(0);

            int nsiblings = rlpSiblingList.size();

            List<Sibling> list = new ArrayList<>();

            for (int j = 0; j < nsiblings; j++) {
                byte[] bytesSibling = rlpSiblingList.get(j).getRLPData();
                Sibling sibling = Sibling.create(bytesSibling);
                list.add(sibling);
            }

            siblings.put(key, list);
        }

        return siblings;
    }

    private void saveSiblings() {
        if (this.siblings == null) {
            return;
        }

        byte[] bytes = getSiblingsBytes(this.siblings);

        DataWord address = new DataWord(SIBLINGS_KEY.getBytes(StandardCharsets.UTF_8));

        this.repository.addStorageBytes(this.contractAddress, address, bytes);
    }

    public static byte[] getSiblingsBytes(SortedMap<Long, List<Sibling>> siblings) {
        int nentries = siblings.size();

        byte[][] entriesBytes = new byte[nentries * 2][];

        int n = 0;

        for (Map.Entry<Long, List<Sibling>> entry : siblings.entrySet()) {
            entriesBytes[n++] = RLP.encodeBigInteger(BigInteger.valueOf(entry.getKey()));

            List<Sibling> list = entry.getValue();

            int nsiblings = list.size();

            byte[][] siblingsBytes = new byte[nsiblings][];

            int j = 0;

            for (Sibling element : list) {
                siblingsBytes[j++] = element.getEncoded();
            }

            entriesBytes[n++] = RLP.encodeList(siblingsBytes);
        }

        return RLP.encodeList(entriesBytes);
    }

    public Boolean getBrokenSelectionRule() {
        if (brokenSelectionRule!= null) {
            return brokenSelectionRule;
        }

        DataWord address = new DataWord(BROKEN_SELECTION_RULE_KEY.getBytes(StandardCharsets.UTF_8));

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

        DataWord address = new DataWord(BROKEN_SELECTION_RULE_KEY.getBytes(StandardCharsets.UTF_8));

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
        saveSiblings();
        saveBrokenSelectionRule();
    }
}
