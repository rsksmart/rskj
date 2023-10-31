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
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

/**
 * DTO to send the contract state.
 * Not production code, just used for debugging.
 * @author Oscar Guindzberg
 */
public class RemascState {
    private final Coin rewardBalance;
    private final Coin burnedBalance;

    private final Boolean brokenSelectionRule;

    public RemascState(Coin rewardBalance, Coin burnedBalance, Boolean brokenSelectionRule) {
        this.rewardBalance = rewardBalance;
        this.burnedBalance = burnedBalance;
        this.brokenSelectionRule = brokenSelectionRule;
    }

    public Coin getRewardBalance() {
        return rewardBalance;
    }

    public Coin getBurnedBalance() {
        return burnedBalance;
    }

    public Boolean getBrokenSelectionRule() {
        return brokenSelectionRule;
    }

    public byte[] getEncoded() {
        byte[] rlpRewardBalance = RLP.encodeCoin(this.rewardBalance);
        byte[] rlpBurnedBalance = RLP.encodeCoin(this.burnedBalance);
        byte[] rlpBrokenSelectionRule = new byte[1];

        if (brokenSelectionRule) {
            rlpBrokenSelectionRule[0] = 1;
        } else {
            rlpBrokenSelectionRule[0] = 0;
        }

        // we add an empty list because Remasc state expects to have an empty siblings list after 0.5.0 activation
        return RLP.encodeList(rlpRewardBalance, rlpBurnedBalance, RLP.encodeList(), rlpBrokenSelectionRule);
    }

    public static RemascState create(byte[] data) {
        RLPList rlpList = (RLPList)RLP.decode2(data).get(0);

        Coin rlpRewardBalance = RLP.parseCoin(rlpList.get(0).getRLPData());
        Coin rlpBurnedBalance = RLP.parseCoin(rlpList.get(1).getRLPData());
        // index 2 is ignored because it's a leftover from when we stored the Remasc siblings
        byte[] rlpBrokenSelectionRuleBytes = rlpList.get(3).getRLPData();

        Boolean rlpBrokenSelectionRule;

        if (rlpBrokenSelectionRuleBytes != null && rlpBrokenSelectionRuleBytes.length != 0 && rlpBrokenSelectionRuleBytes[0] != 0) {
            rlpBrokenSelectionRule = Boolean.TRUE;
        } else {
            rlpBrokenSelectionRule = Boolean.FALSE;
        }

        return new RemascState(rlpRewardBalance, rlpBurnedBalance, rlpBrokenSelectionRule);
    }

    @Override
    public String toString() {
        return "RemascState{" +
                "rewardBalance=" + rewardBalance +
                ", burnedBalance=" + burnedBalance +
                ", brokenSelectionRule=" + brokenSelectionRule +
                '}';
    }
}
