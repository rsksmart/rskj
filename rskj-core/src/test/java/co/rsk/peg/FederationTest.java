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

@RunWith(PowerMockRunner.class)
public class FederationTest {
    private Federation federation;
    private List<BtcECKey> sortedPublicKeys;
    private List<byte[]> rskAddresses;

    @Before
    public void createFederation() {
        federation = new Federation(
                Arrays.asList(new BtcECKey[]{
                        BtcECKey.fromPrivate(BigInteger.valueOf(100)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(200)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(300)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(400)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(500)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(600)),
                }),
                ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        sortedPublicKeys = Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPrivate(BigInteger.valueOf(100)),
                BtcECKey.fromPrivate(BigInteger.valueOf(200)),
                BtcECKey.fromPrivate(BigInteger.valueOf(300)),
                BtcECKey.fromPrivate(BigInteger.valueOf(400)),
                BtcECKey.fromPrivate(BigInteger.valueOf(500)),
                BtcECKey.fromPrivate(BigInteger.valueOf(600)),
        }).stream().sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());
        rskAddresses = sortedPublicKeys.stream()
                .map(FederationTest::getRskAddressFromBtcKey)
                .collect(Collectors.toList());
    }

    @Test
    public void publicKeysImmutable() {
        boolean exception = false;
        try {
            federation.getBtcPublicKeys().add(BtcECKey.fromPrivate(BigInteger.valueOf(1000)));
        } catch (Exception e) {
            exception = true;
        }
        Assert.assertTrue(exception);

        exception = false;
        try {
            federation.getBtcPublicKeys().remove(0);
        } catch (Exception e) {
            exception = true;
        }
        Assert.assertTrue(exception);
    }

    @PrepareForTest({ ScriptBuilder.class })
    @Test
    public void redeemScript() {
        final List<Integer> calls = new ArrayList<>();
        PowerMockito.mockStatic(ScriptBuilder.class);
        PowerMockito.when(ScriptBuilder.createRedeemScript(any(int.class), any(List.class))).thenAnswer((invocationOnMock) -> {
            calls.add(1);
            int numberOfSignaturesRequired = invocationOnMock.getArgumentAt(0, int.class);
            List<BtcECKey> publicKeys = invocationOnMock.getArgumentAt(1, List.class);
            Assert.assertEquals(4, numberOfSignaturesRequired);
            Assert.assertEquals(6, publicKeys.size());
            for (int i = 0; i < sortedPublicKeys.size(); i++) {
                Assert.assertTrue(Arrays.equals(sortedPublicKeys.get(i).getPubKey(), publicKeys.get(i).getPubKey()));
            }
            return new Script(new byte[]{(byte)0xaa});
        });
        Assert.assertTrue(Arrays.equals(federation.getRedeemScript().getProgram(), new byte[]{(byte) 0xaa}));
        Assert.assertTrue(Arrays.equals(federation.getRedeemScript().getProgram(), new byte[]{(byte) 0xaa}));
        // Make sure the script creation happens only once
        Assert.assertEquals(1, calls.size());
    }

    @PrepareForTest({ ScriptBuilder.class })
    @Test
    public void P2SHScript() {
        final List<Integer> calls = new ArrayList<>();
        PowerMockito.mockStatic(ScriptBuilder.class);
        PowerMockito.when(ScriptBuilder.createP2SHOutputScript(any(int.class), any(List.class))).thenAnswer((invocationOnMock) -> {
            calls.add(0);
            int numberOfSignaturesRequired = invocationOnMock.getArgumentAt(0, int.class);
            List<BtcECKey> publicKeys = invocationOnMock.getArgumentAt(1, List.class);
            Assert.assertEquals(4, numberOfSignaturesRequired);
            Assert.assertEquals(6, publicKeys.size());
            for (int i = 0; i < sortedPublicKeys.size();i ++) {
                Assert.assertTrue(Arrays.equals(sortedPublicKeys.get(i).getPubKey(), publicKeys.get(i).getPubKey()));
            }
            return new Script(new byte[]{(byte)0xaa});
        });
        Assert.assertTrue(Arrays.equals(federation.getP2SHScript().getProgram(), new byte[]{(byte) 0xaa}));
        Assert.assertTrue(Arrays.equals(federation.getP2SHScript().getProgram(), new byte[]{(byte) 0xaa}));
        // Make sure the script creation happens only once
        Assert.assertEquals(1, calls.size());
    }

    @PrepareForTest({ ScriptBuilder.class })
    @Test
    public void Address() {
        // Since we can't mock both Address and ScriptBuilder at the same time (due to PowerMockito limitations)
        // we use a well known P2SH and its corresponding address
        // and just mock the ScriptBuilder
        // a914896ed9f3446d51b5510f7f0b6ef81b2bde55140e87 => 2N5muMepJizJE1gR7FbHJU6CD18V3BpNF9p
        final List<Integer> calls = new ArrayList<>();
        PowerMockito.mockStatic(ScriptBuilder.class);
        PowerMockito.when(ScriptBuilder.createP2SHOutputScript(any(int.class), any(List.class))).thenAnswer((invocationOnMock) -> {
            calls.add(0);
            int numberOfSignaturesRequired = invocationOnMock.getArgumentAt(0, int.class);
            List<BtcECKey> publicKeys = invocationOnMock.getArgumentAt(1, List.class);
            Assert.assertEquals(4, numberOfSignaturesRequired);
            Assert.assertEquals(6, publicKeys.size());
            for (int i = 0; i < sortedPublicKeys.size();i ++) {
                Assert.assertTrue(Arrays.equals(sortedPublicKeys.get(i).getPubKey(), publicKeys.get(i).getPubKey()));
            }
            return new Script(Hex.decode("a914896ed9f3446d51b5510f7f0b6ef81b2bde55140e87"));
        });

        Assert.assertEquals("2N5muMepJizJE1gR7FbHJU6CD18V3BpNF9p", federation.getAddress().toBase58());
        Assert.assertEquals("2N5muMepJizJE1gR7FbHJU6CD18V3BpNF9p", federation.getAddress().toBase58());
        // Make sure the address creation happens only once
        Assert.assertEquals(1, calls.size());
    }

    @Test
    public void testEquals_a() {
        Assert.assertTrue(federation.equals(federation));

        Assert.assertFalse(federation.equals(null));
        Assert.assertFalse(federation.equals(new Object()));
        Assert.assertFalse(federation.equals("something else"));
    }

    @Test
    public void testEquals_differentNumberOfPublicKeys() {
        Federation otherFederation = new Federation(
                Arrays.asList(new BtcECKey[]{
                        BtcECKey.fromPrivate(BigInteger.valueOf(100)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(200)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(300)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(400)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(500)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(600)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(700)),
                }),
                ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        Assert.assertFalse(federation.equals(otherFederation));
    }

    @Test
    public void testEquals_differentCreationTime() {
        Federation otherFederation = new Federation(
                Arrays.asList(new BtcECKey[]{
                        BtcECKey.fromPrivate(BigInteger.valueOf(100)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(200)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(300)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(400)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(500)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(600)),
                }),
                ZonedDateTime.parse("2017-06-10T02:30:01Z").toInstant(),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        Assert.assertFalse(federation.equals(otherFederation));
    }

    @Test
    public void testEquals_differentCreationBlockNumber() {
        Federation otherFederation = new Federation(
                Arrays.asList(
                    BtcECKey.fromPrivate(BigInteger.valueOf(100)),
                    BtcECKey.fromPrivate(BigInteger.valueOf(200)),
                    BtcECKey.fromPrivate(BigInteger.valueOf(300)),
                    BtcECKey.fromPrivate(BigInteger.valueOf(400)),
                    BtcECKey.fromPrivate(BigInteger.valueOf(500)),
                    BtcECKey.fromPrivate(BigInteger.valueOf(600))
                ),
                ZonedDateTime.parse("2017-06-10T02:30:01Z").toInstant(),
                1L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        Assert.assertThat(federation, is(not(otherFederation)));
    }

    @Test
    public void testEquals_differentNetworkParameters() {
        Federation otherFederation = new Federation(
                Arrays.asList(new BtcECKey[]{
                        BtcECKey.fromPrivate(BigInteger.valueOf(100)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(200)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(300)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(400)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(500)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(600)),
                }),
                ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_TESTNET)
        );
        Assert.assertFalse(federation.equals(otherFederation));
    }

    @Test
    public void testEquals_differentPublicKeys() {
        Federation otherFederation = new Federation(
                Arrays.asList(new BtcECKey[]{
                        BtcECKey.fromPrivate(BigInteger.valueOf(100)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(200)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(300)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(400)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(500)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(610)),
                }),
                ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        Assert.assertFalse(federation.equals(otherFederation));
    }

    @Test
    public void testEquals_same() {
        Federation otherFederation = new Federation(
                Arrays.asList(new BtcECKey[]{
                        BtcECKey.fromPrivate(BigInteger.valueOf(100)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(200)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(300)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(400)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(500)),
                        BtcECKey.fromPrivate(BigInteger.valueOf(600)),
                }),
                ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        Assert.assertTrue(federation.equals(otherFederation));
    }

    @Test
    public void getPublicKeyIndex() {
        for (int i = 0; i < federation.getBtcPublicKeys().size(); i++) {
            Assert.assertEquals(i, federation.getPublicKeyIndex(sortedPublicKeys.get(i)).intValue());
        }
        Assert.assertNull(federation.getPublicKeyIndex(BtcECKey.fromPrivate(BigInteger.valueOf(1234))));
    }

    @Test
    public void hasPublicKey() {
        for (int i = 0; i < federation.getBtcPublicKeys().size(); i++) {
            Assert.assertTrue(federation.hasPublicKey(sortedPublicKeys.get(i)));
        }
        Assert.assertFalse(federation.hasPublicKey(BtcECKey.fromPrivate(BigInteger.valueOf(1234))));
    }

    @Test
    public void hasMemberWithRskAddress() {
        for (int i = 0; i < federation.getBtcPublicKeys().size(); i++) {
            Assert.assertTrue(federation.hasMemberWithRskAddress(rskAddresses.get(i)));
        }

        BtcECKey nonFederateKey = BtcECKey.fromPrivate(BigInteger.valueOf(1234));
        byte[] nonFederateRskAddress = getRskAddressFromBtcKey(nonFederateKey);
        Assert.assertFalse(federation.hasMemberWithRskAddress(nonFederateRskAddress));
    }

    @Test
    public void testToString() {
        Assert.assertEquals("4 of 6 signatures federation", federation.toString());
    }

    private static byte[] getRskAddressFromBtcKey(BtcECKey btcECKey) {
        return ECKey.fromPublicOnly(btcECKey.getPubKey()).getAddress();
    }
}
