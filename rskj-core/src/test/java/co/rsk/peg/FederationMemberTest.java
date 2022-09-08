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
import org.ethereum.crypto.ECKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

public class FederationMemberTest {
    private BtcECKey btcKey;
    private ECKey rskKey;
    private ECKey mstKey;
    private FederationMember member;

    @BeforeEach
    public void createFederationMember() {
        btcKey = new BtcECKey();
        rskKey = new ECKey();
        mstKey = new ECKey();
        member = new FederationMember(btcKey, rskKey, mstKey);
    }

    @Test
    public void immutable() {
        Assertions.assertNotSame(btcKey, member.getBtcPublicKey());
        Assertions.assertTrue(Arrays.equals(btcKey.getPubKey(), member.getBtcPublicKey().getPubKey()));
        Assertions.assertNotSame(rskKey, member.getRskPublicKey());
        Assertions.assertTrue(Arrays.equals(rskKey.getPubKey(), member.getRskPublicKey().getPubKey()));
    }

    @Test
    public void testEquals_basic() {
        Assertions.assertTrue(member.equals(member));

        Assertions.assertFalse(member.equals(null));
        Assertions.assertFalse(member.equals(new Object()));
        Assertions.assertFalse(member.equals("something else"));
    }

    @Test
    public void testEquals_sameKeys() {
        FederationMember otherMember = new FederationMember(btcKey, rskKey, mstKey);

        Assertions.assertTrue(member.equals(otherMember));
    }

    @Test
    public void testEquals_sameKeysDifferentCompression() {
        FederationMember uncompressedMember = new FederationMember(
                BtcECKey.fromPublicOnly(btcKey.getPubKeyPoint().getEncoded(false)),
                ECKey.fromPublicOnly(rskKey.getPubKey(false)),
                ECKey.fromPublicOnly(mstKey.getPubKey(false))
        );

        FederationMember compressedMember = new FederationMember(
                BtcECKey.fromPublicOnly(btcKey.getPubKeyPoint().getEncoded(true)),
                ECKey.fromPublicOnly(rskKey.getPubKey(true)),
                ECKey.fromPublicOnly(mstKey.getPubKey(true))
        );

        Assertions.assertTrue(compressedMember.equals(uncompressedMember));
        Assertions.assertTrue(uncompressedMember.equals(compressedMember));
    }

    @Test
    public void testEquals_differentBtcKey() {
        FederationMember otherMember = new FederationMember(new BtcECKey(), rskKey, mstKey);

        Assertions.assertFalse(member.equals(otherMember));
    }

    @Test
    public void testEquals_differentRskKey() {
        FederationMember otherMember = new FederationMember(btcKey, new ECKey(), mstKey);

        Assertions.assertFalse(member.equals(otherMember));
    }

    @Test
    public void testEquals_differentMstKey() {
        FederationMember otherMember = new FederationMember(btcKey, rskKey, new ECKey());

        Assertions.assertFalse(member.equals(otherMember));
    }

    @Test
    public void keyType_byValue() {
        Assertions.assertEquals(FederationMember.KeyType.BTC, FederationMember.KeyType.byValue("btc"));
        Assertions.assertEquals(FederationMember.KeyType.RSK, FederationMember.KeyType.byValue("rsk"));
        Assertions.assertEquals(FederationMember.KeyType.MST, FederationMember.KeyType.byValue("mst"));
    }

    @Test
    public void keyType_byValueInvalid() {
        try {
            FederationMember.KeyType.byValue("whatever");
            Assertions.fail();
        } catch (IllegalArgumentException e) {}
    }
}
