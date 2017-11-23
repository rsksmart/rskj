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

package co.rsk.peg;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
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

import static org.mockito.Matchers.any;

@RunWith(PowerMockRunner.class)
public class PendingFederationTest {
    private PendingFederation pendingFederation;
    private List<BtcECKey> sortedPublicKeys;

    @Before
    public void createPendingFederation() {
        pendingFederation = new PendingFederation(
                12,
                3,
                Arrays.asList(new BtcECKey[]{
                        BtcECKey.fromPrivate(BigInteger.valueOf(100)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(200)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(300)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(400)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(500)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(600)),
                })
        );
        sortedPublicKeys = Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPrivate(BigInteger.valueOf(100)),
                BtcECKey.fromPrivate(BigInteger.valueOf(200)),
                BtcECKey.fromPrivate(BigInteger.valueOf(300)),
                BtcECKey.fromPrivate(BigInteger.valueOf(400)),
                BtcECKey.fromPrivate(BigInteger.valueOf(500)),
                BtcECKey.fromPrivate(BigInteger.valueOf(600)),
        }).stream().sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());
    }

    @Test
    public void publicKeysImmutable() {
        boolean exception = false;
        try {
            pendingFederation.getPublicKeys().add(BtcECKey.fromPrivate(BigInteger.valueOf(1000)));
        } catch (Exception e) {
            exception = true;
        }
        Assert.assertTrue(exception);

        exception = false;
        try {
            pendingFederation.getPublicKeys().remove(0);
        } catch (Exception e) {
            exception = true;
        }
        Assert.assertTrue(exception);
    }
    
    @Test
    public void isComplete() {
        Assert.assertTrue(pendingFederation.isComplete());
    }

    @Test
    public void isComplete_not() {
        PendingFederation otherPendingFederation = new PendingFederation(
                12,
                3,
                Arrays.asList(new BtcECKey[]{
                        BtcECKey.fromPrivate(BigInteger.valueOf(100)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(200)),
                })
        );
        Assert.assertFalse(otherPendingFederation.isComplete());
    }

    @Test
    public void testEquals_a() {
        Assert.assertTrue(pendingFederation.equals(pendingFederation));

        Assert.assertFalse(pendingFederation.equals(null));
        Assert.assertFalse(pendingFederation.equals(new Object()));
        Assert.assertFalse(pendingFederation.equals("something else"));
    }

    @Test
    public void testEquals_differentId() {
        PendingFederation otherPendingFederation = new PendingFederation(
                11,
                3,
                Arrays.asList(new BtcECKey[]{
                        BtcECKey.fromPrivate(BigInteger.valueOf(100)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(200)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(300)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(400)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(500)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(600)),
                })
        );
        Assert.assertFalse(pendingFederation.equals(otherPendingFederation));
    }

    @Test
    public void testEquals_differentThreshold() {
        PendingFederation otherPendingFederation = new PendingFederation(
                12,
                2,
                Arrays.asList(new BtcECKey[]{
                        BtcECKey.fromPrivate(BigInteger.valueOf(100)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(200)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(300)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(400)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(500)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(600)),
                })
        );
        Assert.assertFalse(pendingFederation.equals(otherPendingFederation));
    }

    @Test
    public void testEquals_differentNumberOfPublicKeys() {
        PendingFederation otherPendingFederation = new PendingFederation(
                12,
                3,
                Arrays.asList(new BtcECKey[]{
                        BtcECKey.fromPrivate(BigInteger.valueOf(100)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(200)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(300)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(400)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(500)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(600)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(700)),
                })
        );
        Assert.assertFalse(pendingFederation.equals(otherPendingFederation));
    }

    @Test
    public void testEquals_differentPublicKeys() {
        PendingFederation otherPendingFederation = new PendingFederation(
                12,
                3,
                Arrays.asList(new BtcECKey[]{
                        BtcECKey.fromPrivate(BigInteger.valueOf(100)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(200)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(300)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(400)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(500)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(610)),
                })
        );
        Assert.assertFalse(pendingFederation.equals(otherPendingFederation));
    }

    @Test
    public void testEquals_same() {
        PendingFederation otherPendingFederation = new PendingFederation(
                12,
                3,
                Arrays.asList(new BtcECKey[]{
                        BtcECKey.fromPrivate(BigInteger.valueOf(100)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(200)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(300)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(400)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(500)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(600)),
                })
        );
        Assert.assertTrue(pendingFederation.equals(otherPendingFederation));
    }

    @Test
    public void testToString() {
        Assert.assertEquals("3 of 6 signatures pending federation (complete)", pendingFederation.toString());
        PendingFederation otherPendingFederation = new PendingFederation(
                12,
                3,
                Arrays.asList(new BtcECKey[]{
                        BtcECKey.fromPrivate(BigInteger.valueOf(100)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(200)),
                })
        );
        Assert.assertEquals("3 of 2 signatures pending federation (incomplete)", otherPendingFederation.toString());
    }
}
