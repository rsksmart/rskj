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
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import org.ethereum.crypto.ECKey;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.mockito.Matchers.any;

public class FederationMemberTest {
    private BtcECKey btcKey;
    private ECKey rskKey;
    private FederationMember member;

    @Before
    public void createFederationMember() {
        btcKey = new BtcECKey();
        rskKey = new ECKey();
        member = new FederationMember(btcKey, rskKey);
    }

    @Test
    public void immutable() {
        Assert.assertNotSame(btcKey, member.getBtcPublicKey());
        Assert.assertTrue(Arrays.equals(btcKey.getPubKey(), member.getBtcPublicKey().getPubKey()));
        Assert.assertNotSame(rskKey, member.getRskPublicKey());
        Assert.assertTrue(Arrays.equals(rskKey.getPubKey(), member.getRskPublicKey().getPubKey()));
    }

    @Test
    public void testEquals_basic() {
        Assert.assertTrue(member.equals(member));

        Assert.assertFalse(member.equals(null));
        Assert.assertFalse(member.equals(new Object()));
        Assert.assertFalse(member.equals("something else"));
    }

    @Test
    public void testEquals_sameKeys() {
        FederationMember otherMember = new FederationMember(btcKey, rskKey);

        Assert.assertTrue(member.equals(otherMember));
    }

    @Test
    public void testEquals_differentBtcKey() {
        FederationMember otherMember = new FederationMember(new BtcECKey(), rskKey);

        Assert.assertFalse(member.equals(otherMember));
    }

    @Test
    public void testEquals_differentRskKey() {
        FederationMember otherMember = new FederationMember(btcKey, new ECKey());

        Assert.assertFalse(member.equals(otherMember));
    }
}
