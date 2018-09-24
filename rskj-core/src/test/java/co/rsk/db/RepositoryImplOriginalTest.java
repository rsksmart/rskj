/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package co.rsk.db;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.trie.TrieImpl;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.core.Genesis;
import org.ethereum.core.Repository;
import org.ethereum.crypto.HashUtil;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.util.RskTestFactory;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.DataWord;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.bouncycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.*;

/**
 * @author Roman Mandeleil
 * @since 17.11.2014
 * Modified by ajlopez on 03/04/2017, to use RepositoryImpl
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class RepositoryImplOriginalTest {

    public static final RskAddress COW = new RskAddress("CD2A3D9F938E13CD947EC05ABC7FE734DF8DD826");
    public static final RskAddress HORSE = new RskAddress("13978AEE95F38490E9769C39B2773ED763D9CD5F");
    private final TestSystemProperties config = new TestSystemProperties();

    @Test
    public void test1() {
        Repository repository = createRepository();

        repository.increaseNonce(COW);
        repository.increaseNonce(HORSE);

        assertEquals(BigInteger.ONE, repository.getNonce(COW));

        repository.increaseNonce(COW);
    }

    @Test
    public void test2() {
        Repository repository = createRepository();

        repository.addBalance(COW, Coin.valueOf(10L));
        repository.addBalance(HORSE, Coin.valueOf(1L));

        assertEquals(BigInteger.TEN, repository.getBalance(COW).asBigInteger());
        assertEquals(BigInteger.ONE, repository.getBalance(HORSE).asBigInteger());
    }

    @Test
    public void test3() {
        Repository repository = createRepository();

        byte[] cowCode = Hex.decode("A1A2A3");
        byte[] horseCode = Hex.decode("B1B2B3");

        repository.saveCode(COW, cowCode);
        repository.saveCode(HORSE, horseCode);

        assertArrayEquals(cowCode, repository.getCode(COW));
        assertArrayEquals(horseCode, repository.getCode(HORSE));
    }

    @Test
    public void test4() {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        byte[] cowKey = Hex.decode("A1A2A3");
        byte[] cowValue = Hex.decode("A4A5A6");

        byte[] horseKey = Hex.decode("B1B2B3");
        byte[] horseValue = Hex.decode("B4B5B6");

        track.addStorageRow(COW, new DataWord(cowKey), new DataWord(cowValue));
        track.addStorageRow(HORSE, new DataWord(horseKey), new DataWord(horseValue));
        track.commit();

        assertEquals(new DataWord(cowValue), repository.getStorageValue(COW, new DataWord(cowKey)));
        assertEquals(new DataWord(horseValue), repository.getStorageValue(HORSE, new DataWord(horseKey)));
    }

    @Test
    public void test5() {
        Repository repository = createRepository();

        Repository track = repository.startTracking();

        track.increaseNonce(COW);
        track.increaseNonce(COW);
        track.increaseNonce(COW);
        track.increaseNonce(COW);
        track.increaseNonce(COW);
        track.increaseNonce(COW);
        track.increaseNonce(COW);
        track.increaseNonce(COW);
        track.increaseNonce(COW);
        track.increaseNonce(COW);

        track.increaseNonce(HORSE);

        track.commit();

        assertEquals(BigInteger.TEN, repository.getNonce(COW));
        assertEquals(BigInteger.ONE, repository.getNonce(HORSE));
    }

    @Test
    public void test6() {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        track.increaseNonce(COW);
        track.increaseNonce(COW);
        track.increaseNonce(COW);
        track.increaseNonce(COW);
        track.increaseNonce(COW);
        track.increaseNonce(COW);
        track.increaseNonce(COW);
        track.increaseNonce(COW);
        track.increaseNonce(COW);
        track.increaseNonce(COW);

        track.increaseNonce(HORSE);

        assertEquals(BigInteger.TEN, track.getNonce(COW));
        assertEquals(BigInteger.ONE, track.getNonce(HORSE));

        track.rollback();

        assertEquals(BigInteger.ZERO, repository.getNonce(COW));
        assertEquals(BigInteger.ZERO, repository.getNonce(HORSE));
    }

    @Test
    public void test7() {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        track.addBalance(COW, Coin.valueOf(10L));
        track.addBalance(HORSE, Coin.valueOf(1L));

        assertEquals(BigInteger.TEN, track.getBalance(COW).asBigInteger());
        assertEquals(BigInteger.ONE, track.getBalance(HORSE).asBigInteger());

        track.commit();

        assertEquals(BigInteger.TEN, repository.getBalance(COW).asBigInteger());
        assertEquals(BigInteger.ONE, repository.getBalance(HORSE).asBigInteger());
    }

    @Test
    public void test8() {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        track.addBalance(COW, Coin.valueOf(10L));
        track.addBalance(HORSE, Coin.valueOf(1L));

        assertEquals(BigInteger.TEN, track.getBalance(COW).asBigInteger());
        assertEquals(BigInteger.ONE, track.getBalance(HORSE).asBigInteger());

        track.rollback();

        assertEquals(BigInteger.ZERO, repository.getBalance(COW).asBigInteger());
        assertEquals(BigInteger.ZERO, repository.getBalance(HORSE).asBigInteger());
    }

    @Test
    public void test7_1() {
        Repository repository = createRepository();
        Repository track1 = repository.startTracking();

        track1.addBalance(COW, Coin.valueOf(10L));
        track1.addBalance(HORSE, Coin.valueOf(1L));

        assertEquals(BigInteger.TEN, track1.getBalance(COW).asBigInteger());
        assertEquals(BigInteger.ONE, track1.getBalance(HORSE).asBigInteger());

        Repository track2 = track1.startTracking();

        assertEquals(BigInteger.TEN, track2.getBalance(COW).asBigInteger());
        assertEquals(BigInteger.ONE, track2.getBalance(HORSE).asBigInteger());

        track2.addBalance(COW, Coin.valueOf(10L));
        track2.addBalance(COW, Coin.valueOf(10L));
        track2.addBalance(COW, Coin.valueOf(10L));

        track2.commit();

        track1.commit();

        assertEquals(new BigInteger("40"), repository.getBalance(COW).asBigInteger());
        assertEquals(BigInteger.ONE, repository.getBalance(HORSE).asBigInteger());
    }

    @Test
    public void test7_2() {
        Repository repository = createRepository();
        Repository track1 = repository.startTracking();

        track1.addBalance(COW, Coin.valueOf(10L));
        track1.addBalance(HORSE, Coin.valueOf(1L));

        assertEquals(BigInteger.TEN, track1.getBalance(COW).asBigInteger());
        assertEquals(BigInteger.ONE, track1.getBalance(HORSE).asBigInteger());

        Repository track2 = track1.startTracking();

        assertEquals(BigInteger.TEN, track2.getBalance(COW).asBigInteger());
        assertEquals(BigInteger.ONE, track2.getBalance(HORSE).asBigInteger());

        track2.addBalance(COW, Coin.valueOf(10L));
        track2.addBalance(COW, Coin.valueOf(10L));
        track2.addBalance(COW, Coin.valueOf(10L));

        track2.commit();

        track1.rollback();

        assertEquals(BigInteger.ZERO, repository.getBalance(COW).asBigInteger());
        assertEquals(BigInteger.ZERO, repository.getBalance(HORSE).asBigInteger());
    }

    @Test
    public void test9() {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        DataWord cowKey = new DataWord(Hex.decode("A1A2A3"));
        DataWord cowValue = new DataWord(Hex.decode("A4A5A6"));

        DataWord horseKey = new DataWord(Hex.decode("B1B2B3"));
        DataWord horseValue = new DataWord(Hex.decode("B4B5B6"));

        track.addStorageRow(COW, cowKey, cowValue);
        track.addStorageRow(HORSE, horseKey, horseValue);

        assertEquals(cowValue, track.getStorageValue(COW, cowKey));
        assertEquals(horseValue, track.getStorageValue(HORSE, horseKey));

        track.commit();

        assertEquals(cowValue, repository.getStorageValue(COW, cowKey));
        assertEquals(horseValue, repository.getStorageValue(HORSE, horseKey));
    }

    @Test
    public void test10() {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        DataWord cowKey = new DataWord(Hex.decode("A1A2A3"));
        DataWord cowValue = new DataWord(Hex.decode("A4A5A6"));

        DataWord horseKey = new DataWord(Hex.decode("B1B2B3"));
        DataWord horseValue = new DataWord(Hex.decode("B4B5B6"));

        track.addStorageRow(COW, cowKey, cowValue);
        track.addStorageRow(HORSE, horseKey, horseValue);

        assertEquals(cowValue, track.getStorageValue(COW, cowKey));
        assertEquals(horseValue, track.getStorageValue(HORSE, horseKey));

        track.rollback();
        // getStorageValue() returns always a DataWord, not null anymore
        assertEquals(null, repository.getStorageValue(COW, cowKey));
        assertEquals(null, repository.getStorageValue(HORSE, horseKey));
    }


    @Test
    public void test11() {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        byte[] cowCode = Hex.decode("A1A2A3");
        byte[] horseCode = Hex.decode("B1B2B3");

        track.saveCode(COW, cowCode);
        track.saveCode(HORSE, horseCode);

        assertArrayEquals(cowCode, track.getCode(COW));
        assertArrayEquals(horseCode, track.getCode(HORSE));

        track.commit();

        assertArrayEquals(cowCode, repository.getCode(COW));
        assertArrayEquals(horseCode, repository.getCode(HORSE));
    }

    @Test
    public void test12() {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        byte[] cowCode = Hex.decode("A1A2A3");
        byte[] horseCode = Hex.decode("B1B2B3");

        track.saveCode(COW, cowCode);
        track.saveCode(HORSE, horseCode);

        assertArrayEquals(cowCode, track.getCode(COW));
        assertArrayEquals(horseCode, track.getCode(HORSE));

        track.rollback();

        assertArrayEquals(EMPTY_BYTE_ARRAY, repository.getCode(COW));
        assertArrayEquals(EMPTY_BYTE_ARRAY, repository.getCode(HORSE));
    }

    @Test  // Let's upload genesis pre-mine just like in the real world
    public void test13() {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Genesis genesis = RskTestFactory.getGenesisInstance(config);
        for (RskAddress addr : genesis.getPremine().keySet()) {
            repository.createAccount(addr);
            repository.addBalance(addr, genesis.getPremine().get(addr).getAccountState().getBalance());
        }

        track.commit();

        // To Review: config Genesis should have an State Root according to the new trie algorithm
        // assertArrayEquals(Genesis.getGenesisInstance(SystemProperties.CONFIG).getStateRoot(), repository.getRoot());
    }

    @Test
    public void test14() {
        Repository repository = createRepository();

        final BigInteger ELEVEN = BigInteger.TEN.add(BigInteger.ONE);


        // changes level_1
        Repository track1 = repository.startTracking();
        track1.addBalance(COW, Coin.valueOf(10L));
        track1.addBalance(HORSE, Coin.valueOf(1L));

        assertEquals(BigInteger.TEN, track1.getBalance(COW).asBigInteger());
        assertEquals(BigInteger.ONE, track1.getBalance(HORSE).asBigInteger());


        // changes level_2
        Repository track2 = track1.startTracking();
        track2.addBalance(COW, Coin.valueOf(1L));
        track2.addBalance(HORSE, Coin.valueOf(10L));

        assertEquals(ELEVEN, track2.getBalance(COW).asBigInteger());
        assertEquals(ELEVEN, track2.getBalance(HORSE).asBigInteger());

        track2.commit();
        track1.commit();

        assertEquals(ELEVEN, repository.getBalance(COW).asBigInteger());
        assertEquals(ELEVEN, repository.getBalance(HORSE).asBigInteger());
    }

    @Test
    public void test15() {
        Repository repository = createRepository();

        final BigInteger ELEVEN = BigInteger.TEN.add(BigInteger.ONE);


        // changes level_1
        Repository track1 = repository.startTracking();
        track1.addBalance(COW, Coin.valueOf(10L));
        track1.addBalance(HORSE, Coin.valueOf(1L));

        assertEquals(BigInteger.TEN, track1.getBalance(COW).asBigInteger());
        assertEquals(BigInteger.ONE, track1.getBalance(HORSE).asBigInteger());

        // changes level_2
        Repository track2 = track1.startTracking();
        track2.addBalance(COW, Coin.valueOf(1L));
        track2.addBalance(HORSE, Coin.valueOf(10L));

        assertEquals(ELEVEN, track2.getBalance(COW).asBigInteger());
        assertEquals(ELEVEN, track2.getBalance(HORSE).asBigInteger());

        track2.rollback();
        track1.commit();

        assertEquals(BigInteger.TEN, repository.getBalance(COW).asBigInteger());
        assertEquals(BigInteger.ONE, repository.getBalance(HORSE).asBigInteger());
    }

    @Test
    public void test16() {
        Repository repository = createRepository();

        byte[] cowKey1 = "key-c-1".getBytes();
        byte[] cowValue1 = "val-c-1".getBytes();

        byte[] horseKey1 = "key-h-1".getBytes();
        byte[] horseValue1 = "val-h-1".getBytes();

        byte[] cowKey2 = "key-c-2".getBytes();
        byte[] cowValue2 = "val-c-2".getBytes();

        byte[] horseKey2 = "key-h-2".getBytes();
        byte[] horseValue2 = "val-h-2".getBytes();

        // changes level_1
        Repository track1 = repository.startTracking();
        track1.addStorageRow(COW, new DataWord(cowKey1), new DataWord(cowValue1));
        track1.addStorageRow(HORSE, new DataWord(horseKey1), new DataWord(horseValue1));

        assertEquals(new DataWord(cowValue1), track1.getStorageValue(COW, new DataWord(cowKey1)));
        assertEquals(new DataWord(horseValue1), track1.getStorageValue(HORSE, new DataWord(horseKey1)));

        // changes level_2
        Repository track2 = track1.startTracking();
        track2.addStorageRow(COW, new DataWord(cowKey2), new DataWord(cowValue2));
        track2.addStorageRow(HORSE, new DataWord(horseKey2), new DataWord(horseValue2));

        assertEquals(new DataWord(cowValue1), track2.getStorageValue(COW, new DataWord(cowKey1)));
        assertEquals(new DataWord(horseValue1), track2.getStorageValue(HORSE, new DataWord(horseKey1)));

        assertEquals(new DataWord(cowValue2), track2.getStorageValue(COW, new DataWord(cowKey2)));
        assertEquals(new DataWord(horseValue2), track2.getStorageValue(HORSE, new DataWord(horseKey2)));

        track2.commit();
        // leaving level_2

        assertEquals(new DataWord(cowValue1), track1.getStorageValue(COW, new DataWord(cowKey1)));
        assertEquals(new DataWord(horseValue1), track1.getStorageValue(HORSE, new DataWord(horseKey1)));

        assertEquals(new DataWord(cowValue2), track1.getStorageValue(COW, new DataWord(cowKey2)));
        assertEquals(new DataWord(horseValue2), track1.getStorageValue(HORSE, new DataWord(horseKey2)));

        track1.commit();
        // leaving level_1

        assertEquals(new DataWord(cowValue1), repository.getStorageValue(COW, new DataWord(cowKey1)));
        assertEquals(new DataWord(horseValue1), repository.getStorageValue(HORSE, new DataWord(horseKey1)));

        assertEquals(new DataWord(cowValue2), repository.getStorageValue(COW, new DataWord(cowKey2)));
        assertEquals(new DataWord(horseValue2), repository.getStorageValue(HORSE, new DataWord(horseKey2)));
    }

    @Test
    public void test16_2() {
        Repository repository = createRepository();

        byte[] cowKey1 = "key-c-1".getBytes();
        byte[] cowValue1 = "val-c-1".getBytes();

        byte[] horseKey1 = "key-h-1".getBytes();
        byte[] horseValue1 = "val-h-1".getBytes();

        byte[] cowKey2 = "key-c-2".getBytes();
        byte[] cowValue2 = "val-c-2".getBytes();

        byte[] horseKey2 = "key-h-2".getBytes();
        byte[] horseValue2 = "val-h-2".getBytes();

        // changes level_1
        Repository track1 = repository.startTracking();

        // changes level_2
        Repository track2 = track1.startTracking();
        track2.addStorageRow(COW, new DataWord(cowKey2), new DataWord(cowValue2));
        track2.addStorageRow(HORSE, new DataWord(horseKey2), new DataWord(horseValue2));

        assertNull(track2.getStorageValue(COW, new DataWord(cowKey1)));
        assertNull(track2.getStorageValue(HORSE, new DataWord(horseKey1)));

        assertEquals(new DataWord(cowValue2), track2.getStorageValue(COW, new DataWord(cowKey2)));
        assertEquals(new DataWord(horseValue2), track2.getStorageValue(HORSE, new DataWord(horseKey2)));

        track2.commit();
        // leaving level_2

        assertNull(track1.getStorageValue(COW, new DataWord(cowKey1)));
        assertNull(track1.getStorageValue(HORSE, new DataWord(horseKey1)));

        assertEquals(new DataWord(cowValue2), track1.getStorageValue(COW, new DataWord(cowKey2)));
        assertEquals(new DataWord(horseValue2), track1.getStorageValue(HORSE, new DataWord(horseKey2)));

        track1.commit();
        // leaving level_1

        assertEquals(null, repository.getStorageValue(COW, new DataWord(cowKey1)));
        assertEquals(null, repository.getStorageValue(HORSE, new DataWord(horseKey1)));

        assertEquals(new DataWord(cowValue2), repository.getStorageValue(COW, new DataWord(cowKey2)));
        assertEquals(new DataWord(horseValue2), repository.getStorageValue(HORSE, new DataWord(horseKey2)));
    }

    @Test
    public void test16_3() {
        Repository repository = createRepository();

        byte[] cowKey1 = "key-c-1".getBytes();
        byte[] cowValue1 = "val-c-1".getBytes();

        byte[] horseKey1 = "key-h-1".getBytes();
        byte[] horseValue1 = "val-h-1".getBytes();

        byte[] cowKey2 = "key-c-2".getBytes();
        byte[] cowValue2 = "val-c-2".getBytes();

        byte[] horseKey2 = "key-h-2".getBytes();
        byte[] horseValue2 = "val-h-2".getBytes();

        // changes level_1
        Repository track1 = repository.startTracking();

        // changes level_2
        Repository track2 = track1.startTracking();
        track2.addStorageRow(COW, new DataWord(cowKey2), new DataWord(cowValue2));
        track2.addStorageRow(HORSE, new DataWord(horseKey2), new DataWord(horseValue2));

        assertNull(track2.getStorageValue(COW, new DataWord(cowKey1)));
        assertNull(track2.getStorageValue(HORSE, new DataWord(horseKey1)));

        assertEquals(new DataWord(cowValue2), track2.getStorageValue(COW, new DataWord(cowKey2)));
        assertEquals(new DataWord(horseValue2), track2.getStorageValue(HORSE, new DataWord(horseKey2)));

        track2.commit();
        // leaving level_2

        assertNull(track1.getStorageValue(COW, new DataWord(cowKey1)));
        assertNull(track1.getStorageValue(HORSE, new DataWord(horseKey1)));

        assertEquals(new DataWord(cowValue2), track1.getStorageValue(COW, new DataWord(cowKey2)));
        assertEquals(new DataWord(horseValue2), track1.getStorageValue(HORSE, new DataWord(horseKey2)));

        track1.rollback();
        // leaving level_1

        assertNull(track1.getStorageValue(COW, new DataWord(cowKey1)));
        assertNull(track1.getStorageValue(HORSE, new DataWord(horseKey1)));

        assertNull(track1.getStorageValue(COW, new DataWord(cowKey2)));
        assertNull(track1.getStorageValue(HORSE, new DataWord(horseKey2)));
    }

    @Test
    public void test16_4() {
        Repository repository = createRepository();

        byte[] cowKey1 = "key-c-1".getBytes();
        byte[] cowValue1 = "val-c-1".getBytes();

        byte[] horseKey1 = "key-h-1".getBytes();
        byte[] horseValue1 = "val-h-1".getBytes();

        byte[] cowKey2 = "key-c-2".getBytes();
        byte[] cowValue2 = "val-c-2".getBytes();

        byte[] horseKey2 = "key-h-2".getBytes();
        byte[] horseValue2 = "val-h-2".getBytes();

        Repository track = repository.startTracking();
        track.addStorageRow(COW, new DataWord(cowKey1), new DataWord(cowValue1));
        track.commit();

        // changes level_1
        Repository track1 = repository.startTracking();

        // changes level_2
        Repository track2 = track1.startTracking();
        track2.addStorageRow(COW, new DataWord(cowKey2), new DataWord(cowValue2));

        track2.commit();
        // leaving level_2

        track1.commit();
        // leaving level_1

        assertEquals(new DataWord(cowValue1), track1.getStorageValue(COW, new DataWord(cowKey1)));
        assertEquals(new DataWord(cowValue2), track1.getStorageValue(COW, new DataWord(cowKey2)));
    }


    @Test
    public void test16_5() {
        Repository repository = createRepository();

        byte[] cowKey1 = "key-c-1".getBytes();
        byte[] cowValue1 = "val-c-1".getBytes();

        byte[] horseKey1 = "key-h-1".getBytes();
        byte[] horseValue1 = "val-h-1".getBytes();

        byte[] cowKey2 = "key-c-2".getBytes();
        byte[] cowValue2 = "val-c-2".getBytes();

        byte[] horseKey2 = "key-h-2".getBytes();
        byte[] horseValue2 = "val-h-2".getBytes();

        // changes level_1
        Repository track1 = repository.startTracking();
        track1.addStorageRow(COW, new DataWord(cowKey2), new DataWord(cowValue2));

        // changes level_2
        Repository track2 = track1.startTracking();
        assertEquals(new DataWord(cowValue2), track1.getStorageValue(COW, new DataWord(cowKey2)));
        assertNull(track1.getStorageValue(COW, new DataWord(cowKey1)));

        track2.commit();
        // leaving level_2

        track1.commit();
        // leaving level_1

        assertEquals(new DataWord(cowValue2), track1.getStorageValue(COW, new DataWord(cowKey2)));
        assertNull(track1.getStorageValue(COW, new DataWord(cowKey1)));
    }

    @Test
    public void test17() {
        Repository repository = createRepository();

        byte[] cowKey1 = "key-c-1".getBytes();
        byte[] cowValue1 = "val-c-1".getBytes();

        // changes level_1
        Repository track1 = repository.startTracking();

        // changes level_2
        Repository track2 = track1.startTracking();
        track2.addStorageRow(COW, new DataWord(cowKey1), new DataWord(cowValue1));
        assertEquals(new DataWord(cowValue1), track2.getStorageValue(COW, new DataWord(cowKey1)));
        track2.rollback();
        // leaving level_2

        track1.commit();
        // leaving level_1

        Assert.assertEquals(Hex.toHexString(HashUtil.EMPTY_TRIE_HASH), Hex.toHexString(repository.getRoot()));
    }

    @Test
    public void test18() {
        Repository repository = createRepository();
        Repository repoTrack2 = repository.startTracking(); //track

        RskAddress pig = new RskAddress("F0B8C9D84DD2B877E0B952130B73E218106FEC04");
        RskAddress precompiled = new RskAddress("0000000000000000000000000000000000000002");

        byte[] cowCode = Hex.decode("A1A2A3");
        byte[] horseCode = Hex.decode("B1B2B3");

        repository.saveCode(COW, cowCode);
        repository.saveCode(HORSE, horseCode);

        repository.delete(HORSE);

        assertEquals(true, repoTrack2.isExist(COW));
        assertEquals(false, repoTrack2.isExist(HORSE));
        assertEquals(false, repoTrack2.isExist(pig));
        assertEquals(false, repoTrack2.isExist(precompiled));
    }
    @Test
    public void test19() {
        testTrie19(false);
        testTrie19(true);
    }

    public void testTrie19(boolean isSecure) {
        // Creates a repository without store
        Repository repository = createRepository(isSecure);

        // Problem: the store is probably not copied into the track, which is good
        // BUT how takes care of saving items ?
        Repository track = repository.startTracking();

        DataWord cowKey1 = new DataWord("c1");
        DataWord cowVal1 = new DataWord("c0a1");
        DataWord cowVal0 = new DataWord("c0a0");

        DataWord horseKey1 = new DataWord("e1");
        DataWord horseVal1 = new DataWord("c0a1");
        DataWord horseVal0 = new DataWord("c0a0");

        track.addStorageRow(COW, cowKey1, cowVal0);
        track.addStorageRow(HORSE, horseKey1, horseVal0);
        track.commit();


        DataWord horseValAfter = repository.getStorageValue(HORSE,horseKey1);
        assertEquals(horseVal0, horseValAfter);

        // The repository is modified at this time.
        // To actually make it change
        // we don't have to re-sync root
        assertArrayEquals(repository.getRoot(),track.getRoot());
        //repository.setSnapshotTo(track.getRoot());

        // No we create another track, that will be later discarded
        Repository track2 = repository.startTracking(); //track

        track2.addStorageRow(HORSE, horseKey1, horseVal0);

        // Track3 will commit to track2, but track2 will be discarded.
        Repository track3 = track2.startTracking();
        track3.addStorageRow(COW, cowKey1, cowVal1);
        track3.addStorageRow(HORSE, horseKey1, horseVal1);
        track3.commit();

        // Since track2 is rolled back, nothing changes in repo
        track2.rollback();

        assertThat(repository.getStorageValue(COW, cowKey1), is(cowVal0));
        assertThat(repository.getStorageValue(HORSE, horseKey1), is(horseVal0));
    }

    @Test // testing for snapshot
    public void test20() {
        TrieStore store = new TrieStoreImpl(new HashMapDB());
        Repository repository = new MutableRepository(new MutableTrieImpl(new TrieImpl(store, true)));
        byte[] root = repository.getRoot();

        DataWord cowKey1 = new DataWord("c1");
        DataWord cowKey2 = new DataWord("c2");
        DataWord cowVal1 = new DataWord("c0a1");
        DataWord cowVal0 = new DataWord("c0a0");

        DataWord horseKey1 = new DataWord("e1");
        DataWord horseKey2 = new DataWord("e2");
        DataWord horseVal1 = new DataWord("c0a1");
        DataWord horseVal0 = new DataWord("c0a0");

        Repository track2 = repository.startTracking(); //track
        track2.addStorageRow(COW, cowKey1, cowVal1);
        track2.addStorageRow(HORSE, horseKey1, horseVal1);
        track2.commit();

        byte[] root2 = repository.getRoot();

        track2 = repository.startTracking(); //track
        track2.addStorageRow(COW, cowKey2, cowVal0);
        track2.addStorageRow(HORSE, horseKey2, horseVal0);
        track2.commit();

        byte[] root3 = repository.getRoot();

        Repository snapshot = repository.getSnapshotTo(root);
        assertThat(snapshot.getStorageValue(COW, cowKey1), is(nullValue()));
        assertThat(snapshot.getStorageValue(COW, cowKey2), is(nullValue()));
        assertThat(snapshot.getStorageValue(HORSE, horseKey1), is(nullValue()));
        assertThat(snapshot.getStorageValue(HORSE, horseKey2), is(nullValue()));

        snapshot = repository.getSnapshotTo(root2);
        assertThat(snapshot.getStorageValue(COW, cowKey1), is(cowVal1));
        assertThat(snapshot.getStorageValue(COW, cowKey2), is(nullValue()));
        assertThat(snapshot.getStorageValue(HORSE, horseKey1), is(horseVal1));
        assertThat(snapshot.getStorageValue(HORSE, horseKey2), is(nullValue()));

        snapshot = repository.getSnapshotTo(root3);
        assertThat(snapshot.getStorageValue(COW, cowKey1), is(cowVal1));
        assertThat(snapshot.getStorageValue(COW, cowKey2), is(cowVal0));
        assertThat(snapshot.getStorageValue(HORSE, horseKey1), is(horseVal1));
        assertThat(snapshot.getStorageValue(HORSE, horseKey2), is(horseVal0));
    }

    private boolean running = true;

    @Test // testing for snapshot
    public void testMultiThread() throws InterruptedException {
        TrieStore store = new TrieStoreImpl(new HashMapDB());
        final Repository repository = new MutableRepository(new MutableTrieImpl(new TrieImpl(store, true)));

        final DataWord cowKey1 = new DataWord("c1");
        final DataWord cowKey2 = new DataWord("c2");
        final DataWord cowVal0 = new DataWord("c0a0");

        Repository track2 = repository.startTracking();
        track2.addStorageRow(COW, cowKey2, cowVal0);
        track2.commit();
        // Changes commited to repository

        assertThat(repository.getStorageValue(COW, cowKey2), is(cowVal0));

        final CountDownLatch failSema = new CountDownLatch(1);
        // First create the 10 snapshots. The snapshots should not be created while the
        // repository is being changed.
        Repository[] snaps = new Repository[10];

        for (int i = 0; i < 10; ++i) {
            snaps[i] = repository.getSnapshotTo(repository.getRoot());
        }
        for (int i = 0; i < 10; ++i) {
            int finalI = i;
            new Thread(() -> {
                try {
                    int cnt = 1;
                    while (running) {
                        Repository snap = snaps[finalI].startTracking();
                        snap.addBalance(COW, Coin.valueOf(10L));
                        snap.addStorageRow(COW, cowKey1, new DataWord(cnt));
                        snap.rollback();
                        cnt++;
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                    failSema.countDown();
                }
            }).start();
        }

        new Thread(() -> {
            int cnt = 1;
            try {
                while(running) {
                    Repository track21 = repository.startTracking();
                    DataWord cVal = new DataWord(cnt);
                    track21.addStorageRow(COW, cowKey1, cVal);
                    track21.addBalance(COW, Coin.valueOf(1L));
                    track21.commit();

                    assertEquals(BigInteger.valueOf(cnt), repository.getBalance(COW).asBigInteger());
                    assertEquals(cVal, repository.getStorageValue(COW, cowKey1));
                    assertEquals(cowVal0, repository.getStorageValue(COW, cowKey2));
                    cnt++;
                }
            } catch (Throwable e) {
                e.printStackTrace();
                try {
                    repository.addStorageRow(COW, cowKey1, new DataWord(123));
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
                failSema.countDown();
            }
        }).start();

        failSema.await(10, TimeUnit.SECONDS);
        running = false;

        if (failSema.getCount() == 0) {
            throw new RuntimeException("Test failed.");
        }
    }

    private static Repository createRepository(boolean isSecure) {
        return new MutableRepository(new MutableTrieCache(new MutableTrieImpl(new TrieImpl(isSecure))));
    }

    private static Repository createRepository() {
        return new MutableRepository(new MutableTrieCache(new MutableTrieImpl(new TrieImpl())));
    }
}
