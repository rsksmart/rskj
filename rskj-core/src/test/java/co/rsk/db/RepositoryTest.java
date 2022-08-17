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

package co.rsk.db;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.trie.TmpTrieStoreFactory;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Repository;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.MutableRepository;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * This tests are based on the org.ethereum.db.RepositoryTest.java
 * It's main goal is to add more coverage.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class RepositoryTest {

    public static final RskAddress COW = new RskAddress("CD2A3D9F938E13CD947EC05ABC7FE734DF8DD826");
    public static final RskAddress HORSE = new RskAddress("13978AEE95F38490E9769C39B2773ED763D9CD5F");

    private MutableRepository repository;
    private MutableTrieImpl mutableTrie;
    private TrieStore trieStore;

    @Before
    public void setUp() {
        trieStore = TmpTrieStoreFactory.newInstance();
        mutableTrie = new MutableTrieImpl(trieStore, new Trie(trieStore));
        repository = new MutableRepository(mutableTrie);
    }

    @Test
    public void testStorageRoot() {
        repository.createAccount(COW);
        repository.setupContract(COW);
        byte[] stateRoot1 = repository.getStorageStateRoot(COW);

        byte[] cow1Key = Hex.decode("A1A2A3");
        byte[] cow1Value = Hex.decode("A4A5A6");
        byte[] cow2Key = Hex.decode("B1B2B3");
        byte[] cow2Value = Hex.decode("B4B5B6");

        repository.addStorageBytes(COW, DataWord.valueOf(cow1Key), cow1Value);

        byte[] stateRoot2 = repository.getStorageStateRoot(COW);
        assertFalse(Arrays.equals(stateRoot1,stateRoot2));

        repository.addStorageBytes(COW, DataWord.valueOf(cow2Key), cow2Value);

        byte[] stateRoot3 = repository.getStorageStateRoot(COW);
        assertFalse(Arrays.equals(stateRoot1,stateRoot3));
        assertFalse(Arrays.equals(stateRoot2,stateRoot3));

        // Now delete the last item
        repository.addStorageBytes(COW, DataWord.valueOf(cow2Key), ByteUtil.EMPTY_BYTE_ARRAY);

        byte[] stateRoot4 = repository.getStorageStateRoot(COW);
        assertArrayEquals(stateRoot2, stateRoot4);

        // Now delete the last item
        repository.addStorageBytes(COW, DataWord.valueOf(cow1Key), ByteUtil.EMPTY_BYTE_ARRAY);

        byte[] stateRoot5 = repository.getStorageStateRoot(COW);
        assertArrayEquals(stateRoot1, stateRoot5);
    }

    @Test
    public void test4() {
        Repository track = repository.startTracking();

        byte[] cowKey = Hex.decode("A1A2A3");
        byte[] cowValue = Hex.decode("A4A5A6");

        byte[] horseKey = Hex.decode("B1B2B3");
        byte[] horseValue = Hex.decode("B4B5B6");

        track.addStorageBytes(COW, DataWord.valueOf(cowKey), cowValue);
        track.addStorageBytes(HORSE, DataWord.valueOf(horseKey), horseValue);
        track.commit();

        assertArrayEquals(cowValue, repository.getStorageBytes(COW, DataWord.valueOf(cowKey)));
        assertArrayEquals(horseValue, repository.getStorageBytes(HORSE, DataWord.valueOf(horseKey)));
    }

    @Test
    public void test9() {
        Repository track = repository.startTracking();

        DataWord cowKey = DataWord.valueOf(Hex.decode("A1A2A3"));
        byte[] cowValue = Hex.decode("A4A5A6");

        DataWord horseKey = DataWord.valueOf(Hex.decode("B1B2B3"));
        byte[] horseValue = Hex.decode("B4B5B6");

        track.addStorageBytes(COW, cowKey, cowValue);
        track.addStorageBytes(HORSE, horseKey, horseValue);

        assertArrayEquals(cowValue, track.getStorageBytes(COW, cowKey));
        assertArrayEquals(horseValue, track.getStorageBytes(HORSE, horseKey));

        track.commit();

        assertArrayEquals(cowValue, repository.getStorageBytes(COW, cowKey));
        assertArrayEquals(horseValue, repository.getStorageBytes(HORSE, horseKey));
    }

    @Test
    public void test10() {
        Repository track = repository.startTracking();

        DataWord cowKey = DataWord.valueOf(Hex.decode("A1A2A3"));
        byte[] cowValue = Hex.decode("A4A5A6");

        DataWord horseKey = DataWord.valueOf(Hex.decode("B1B2B3"));
        byte[] horseValue = Hex.decode("B4B5B6");

        track.addStorageBytes(COW, cowKey, cowValue);
        track.addStorageBytes(HORSE, horseKey, horseValue);

        assertArrayEquals(cowValue, track.getStorageBytes(COW, cowKey));
        assertArrayEquals(horseValue, track.getStorageBytes(HORSE, horseKey));

        track.rollback();

        assertNull(repository.getStorageBytes(COW, cowKey));
        assertNull(repository.getStorageBytes(HORSE, horseKey));
    }

    @Test
    public void test16() {
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
        track1.addStorageBytes(COW, DataWord.valueOf(cowKey1), cowValue1);
        track1.addStorageBytes(HORSE, DataWord.valueOf(horseKey1), horseValue1);

        assertArrayEquals(cowValue1, track1.getStorageBytes(COW, DataWord.valueOf(cowKey1)));
        assertArrayEquals(horseValue1, track1.getStorageBytes(HORSE, DataWord.valueOf(horseKey1)));

        // changes level_2
        Repository track2 = track1.startTracking();
        track2.addStorageBytes(COW, DataWord.valueOf(cowKey2), cowValue2);
        track2.addStorageBytes(HORSE, DataWord.valueOf(horseKey2), horseValue2);

        assertArrayEquals(cowValue1, track2.getStorageBytes(COW, DataWord.valueOf(cowKey1)));
        assertArrayEquals(horseValue1, track2.getStorageBytes(HORSE, DataWord.valueOf(horseKey1)));

        assertArrayEquals(cowValue2, track2.getStorageBytes(COW, DataWord.valueOf(cowKey2)));
        assertArrayEquals(horseValue2, track2.getStorageBytes(HORSE, DataWord.valueOf(horseKey2)));

        track2.commit();
        // leaving level_2

        assertArrayEquals(cowValue1, track1.getStorageBytes(COW, DataWord.valueOf(cowKey1)));
        assertArrayEquals(horseValue1, track1.getStorageBytes(HORSE, DataWord.valueOf(horseKey1)));

        assertArrayEquals(cowValue2, track1.getStorageBytes(COW, DataWord.valueOf(cowKey2)));
        assertArrayEquals(horseValue2, track1.getStorageBytes(HORSE, DataWord.valueOf(horseKey2)));

        track1.commit();
        // leaving level_1

        assertArrayEquals(cowValue1, repository.getStorageBytes(COW, DataWord.valueOf(cowKey1)));
        assertArrayEquals(horseValue1, repository.getStorageBytes(HORSE, DataWord.valueOf(horseKey1)));

        assertArrayEquals(cowValue2, repository.getStorageBytes(COW, DataWord.valueOf(cowKey2)));
        assertArrayEquals(horseValue2, repository.getStorageBytes(HORSE, DataWord.valueOf(horseKey2)));
    }

    @Test
    public void test16_2() {
        byte[] cowKey1 = "key-c-1".getBytes();
        byte[] cowKey2 = "key-c-2".getBytes();
        byte[] cowValue2 = "val-c-2".getBytes();

        byte[] horseKey1 = "key-h-1".getBytes();
        byte[] horseKey2 = "key-h-2".getBytes();
        byte[] horseValue2 = "val-h-2".getBytes();

        // changes level_1
        Repository track1 = repository.startTracking();

        // changes level_2
        Repository track2 = track1.startTracking();
        track2.addStorageBytes(COW, DataWord.valueOf(cowKey2), cowValue2);
        track2.addStorageBytes(HORSE, DataWord.valueOf(horseKey2), horseValue2);

        assertNull(track2.getStorageBytes(COW, DataWord.valueOf(cowKey1)));
        assertNull(track2.getStorageBytes(HORSE, DataWord.valueOf(horseKey1)));

        assertArrayEquals(cowValue2, track2.getStorageBytes(COW, DataWord.valueOf(cowKey2)));
        assertArrayEquals(horseValue2, track2.getStorageBytes(HORSE, DataWord.valueOf(horseKey2)));

        assertNull(repository.getStorageBytes(COW, DataWord.valueOf(cowKey1)));
        assertNull(repository.getStorageBytes(HORSE, DataWord.valueOf(horseKey1)));

        track2.commit();

        assertNull(repository.getStorageBytes(COW, DataWord.valueOf(cowKey1)));
        assertNull(repository.getStorageBytes(HORSE, DataWord.valueOf(horseKey1)));

        // leaving level_2

        assertNull(track1.getStorageValue(COW, DataWord.valueOf(cowKey1)));
        assertNull(track1.getStorageValue(HORSE, DataWord.valueOf(horseKey1)));

        assertArrayEquals(cowValue2, track1.getStorageBytes(COW, DataWord.valueOf(cowKey2)));
        assertArrayEquals(horseValue2, track1.getStorageBytes(HORSE, DataWord.valueOf(horseKey2)));

        assertNull(repository.getStorageBytes(COW, DataWord.valueOf(cowKey1)));
        assertNull(repository.getStorageBytes(HORSE, DataWord.valueOf(horseKey1)));

        track1.commit();

        // leaving level_1

        assertNull(repository.getStorageBytes(COW, DataWord.valueOf(cowKey1)));
        assertNull(repository.getStorageBytes(HORSE, DataWord.valueOf(horseKey1)));

        assertArrayEquals(cowValue2, repository.getStorageBytes(COW, DataWord.valueOf(cowKey2)));
        assertArrayEquals(horseValue2, repository.getStorageBytes(HORSE, DataWord.valueOf(horseKey2)));
    }

    @Test
    public void test16_3() {
        byte[] cowKey1 = "key-c-1".getBytes();
        byte[] cowKey2 = "key-c-2".getBytes();
        byte[] cowValue2 = "val-c-2".getBytes();

        byte[] horseKey1 = "key-h-1".getBytes();
        byte[] horseKey2 = "key-h-2".getBytes();
        byte[] horseValue2 = "val-h-2".getBytes();

        // changes level_1
        Repository track1 = repository.startTracking();

        // changes level_2
        Repository track2 = track1.startTracking();
        track2.addStorageBytes(COW, DataWord.valueOf(cowKey2), cowValue2);
        track2.addStorageBytes(HORSE, DataWord.valueOf(horseKey2), horseValue2);

        assertNull(track2.getStorageBytes(COW, DataWord.valueOf(cowKey1)));
        assertNull(track2.getStorageBytes(HORSE, DataWord.valueOf(horseKey1)));

        assertArrayEquals(cowValue2, track2.getStorageBytes(COW, DataWord.valueOf(cowKey2)));
        assertArrayEquals(horseValue2, track2.getStorageBytes(HORSE, DataWord.valueOf(horseKey2)));

        track2.commit();
        // leaving level_2

        assertNull(track1.getStorageBytes(COW, DataWord.valueOf(cowKey1)));
        assertNull(track1.getStorageBytes(HORSE, DataWord.valueOf(horseKey1)));

        assertArrayEquals(cowValue2, track1.getStorageBytes(COW, DataWord.valueOf(cowKey2)));
        assertArrayEquals(horseValue2, track1.getStorageBytes(HORSE, DataWord.valueOf(horseKey2)));

        track1.rollback();
        // leaving level_1

        assertNull(track1.getStorageBytes(COW, DataWord.valueOf(cowKey1)));
        assertNull(track1.getStorageBytes(HORSE, DataWord.valueOf(horseKey1)));

        assertNull(track1.getStorageBytes(COW, DataWord.valueOf(cowKey2)));
        assertNull(track1.getStorageBytes(HORSE, DataWord.valueOf(horseKey2)));
    }

    @Test
    public void test16_4() {
        byte[] cowKey1 = "key-c-1".getBytes();
        byte[] cowValue1 = "val-c-1".getBytes();
        byte[] cowKey2 = "key-c-2".getBytes();
        byte[] cowValue2 = "val-c-2".getBytes();

        Repository track = repository.startTracking();
        track.addStorageBytes(COW, DataWord.valueOf(cowKey1), cowValue1);
        track.commit();

        // changes level_1
        Repository track1 = repository.startTracking();

        // changes level_2
        Repository track2 = track1.startTracking();
        track2.addStorageBytes(COW, DataWord.valueOf(cowKey2), cowValue2);

        track2.commit();
        // leaving level_2

        track1.commit();
        // leaving level_1

        assertArrayEquals(cowValue1, track1.getStorageBytes(COW, DataWord.valueOf(cowKey1)));
        assertArrayEquals(cowValue2, track1.getStorageBytes(COW, DataWord.valueOf(cowKey2)));
    }

    @Test
    public void test16_5() {
        byte[] cowKey1 = "key-c-1".getBytes();
        byte[] cowKey2 = "key-c-2".getBytes();
        byte[] cowValue2 = "val-c-2".getBytes();

        // changes level_1
        Repository track1 = repository.startTracking();
        track1.addStorageBytes(COW, DataWord.valueOf(cowKey2), cowValue2);

        // changes level_2
        Repository track2 = track1.startTracking();
        assertArrayEquals(cowValue2, track1.getStorageBytes(COW, DataWord.valueOf(cowKey2)));
        assertNull(track1.getStorageBytes(COW, DataWord.valueOf(cowKey1)));

        track2.commit();
        // leaving level_2

        track1.commit();
        // leaving level_1

        assertArrayEquals(cowValue2, track1.getStorageBytes(COW, DataWord.valueOf(cowKey2)));
        assertNull(track1.getStorageBytes(COW, DataWord.valueOf(cowKey1)));
    }

    @Test
    public void test17() {
        byte[] cowKey1 = "key-c-1".getBytes();
        byte[] cowValue1 = "val-c-1".getBytes();

        // changes level_1
        Repository track1 = repository.startTracking();

        // changes level_2
        Repository track2 = track1.startTracking();
        track2.addStorageBytes(COW, DataWord.valueOf(cowKey1), cowValue1);
        assertArrayEquals(cowValue1, track2.getStorageBytes(COW, DataWord.valueOf(cowKey1)));
        track2.rollback();
        // leaving level_2

        track1.commit();
        // leaving level_1

        Assert.assertEquals(ByteUtil.toHexString(HashUtil.EMPTY_TRIE_HASH), ByteUtil.toHexString(repository.getRoot()));
    }

    @Test
    public void test19() {
        Repository track = repository.startTracking();

        DataWord cowKey1 = DataWord.valueFromHex("c1");
        byte[] cowVal1 = Hex.decode("c0a1");
        byte[] cowVal0 = Hex.decode("c0a0");

        DataWord horseKey1 = DataWord.valueFromHex("e1");
        byte[] horseVal1 = Hex.decode("c0a1");
        byte[] horseVal0 = Hex.decode("c0a0");

        track.addStorageBytes(COW, cowKey1, cowVal0);
        track.addStorageBytes(HORSE, horseKey1, horseVal0);
        track.commit();

        Repository track2 = repository.startTracking(); //track

        track2.addStorageBytes(HORSE, horseKey1, horseVal0);
        Repository track3 = track2.startTracking();

        track3.addStorageBytes(COW, cowKey1, cowVal1);
        track3.addStorageBytes(HORSE, horseKey1, horseVal1);

        track3.commit();
        track2.rollback();

        assertArrayEquals(cowVal0, repository.getStorageBytes(COW, cowKey1));
        assertArrayEquals(horseVal0, repository.getStorageBytes(HORSE, horseKey1));
    }

    @Test // testing for snapshot
    public void testMultiThread() throws InterruptedException {
        final DataWord cowKey1 = DataWord.valueFromHex("c1");
        final DataWord cowKey2 = DataWord.valueFromHex("c2");
        final byte[] cowVal0 = Hex.decode("c0a0");

        Repository track2 = repository.startTracking(); //track
        track2.addStorageBytes(COW, cowKey2, cowVal0);
        track2.commit();
        trieStore.flush();

        assertArrayEquals(cowVal0, repository.getStorageBytes(COW, cowKey2));

        final CountDownLatch failSema = new CountDownLatch(2);

        Repository snap = new MutableRepository(trieStore, trieStore.retrieve(repository.getRoot()).get());
        new Thread(() -> {
            try {
                int cnt = 1;
                while(true) {

                    Repository snapTrack = snap.startTracking();
                    byte[] vcnr = new byte[1];
                    vcnr[0] = (byte)(cnt % 128);
                    snapTrack.addStorageBytes(COW, cowKey1, vcnr);
                    cnt++;

                }
            } catch (Throwable e) {
                e.printStackTrace();
                failSema.countDown();
            }
        }).start();

        new Thread(() -> {
            int cnt = 1;
            try {
                while(true) {
                    Repository track21 = repository.startTracking(); //track
                    byte[] cVal = new byte[1];
                    cVal[0] = (byte)(cnt % 128);
                    track21.addStorageBytes(COW, cowKey1, cVal);
                    track21.commit();

                    trieStore.flush();

                    assertArrayEquals(cVal, repository.getStorageBytes(COW, cowKey1));
                    assertArrayEquals(cowVal0, repository.getStorageBytes(COW, cowKey2));
                    cnt++;
                }
            } catch (Throwable e) {
                e.printStackTrace();
                failSema.countDown();
            }
        }).start();

        failSema.await(10, TimeUnit.SECONDS);

        if (failSema.getCount() < 2) {
            throw new RuntimeException("Test failed.");
        }
    }

    @Test
    public void testCode() {
        Repository track = repository.startTracking();
        byte[] codeLongerThan32bytes = "this-is-code-because-I-say-it-man".getBytes();
        assertTrue(codeLongerThan32bytes.length>32);

        track.saveCode(COW, codeLongerThan32bytes);
        byte[] returnedCode = track.getCode(COW);
        assertArrayEquals(codeLongerThan32bytes, returnedCode);
        track.commit();

        // Now try to get the size
        int codeSize = repository.getCodeLength(COW);
        assertEquals(codeLongerThan32bytes.length, codeSize);

        byte[] returnedCode2 = repository.getCode(COW);
        assertArrayEquals(codeLongerThan32bytes, returnedCode2);

        repository.save();
        byte[] prevRoot = repository.getRoot();

        // Use the same store
        // Now we'll create a new repository based on the same store and force
        // this new repository to read all nodes from the store. The results must
        // be the same: lazy evaluation of the value must work.

        Repository repository2 = new MutableRepository(trieStore, trieStore.retrieve(prevRoot).get());
        // Now try to get the size
        codeSize = repository2.getCodeLength(COW);
        assertEquals(codeLongerThan32bytes.length, codeSize);

        returnedCode2 = repository2.getCode(COW);
        assertArrayEquals(codeLongerThan32bytes, returnedCode2);
    }

    @Test
    public void testGetCodeHash() {
        RskAddress addressNotAdded = new RskAddress("13978AEE95F38490E9769C39B2773ED763D9CD5A");
        Repository track = repository.startTracking();

        byte[] code = "a-great-code".getBytes();
        byte[] emptyCode = new byte[0];

        Keccak256 codeKeccak = getKeccak256Hash(code);
        Keccak256 emptyKeccak = getKeccak256Hash(emptyCode);

        track.createAccount(HORSE);

        //Make COW address a contract
        track.setupContract(COW);
        track.saveCode(COW, code);

        assertArrayEquals(code, track.getCode(COW));
        assertNull(track.getCode(HORSE));

        track.commit();

        //Non-empty code hash
        Keccak256 codeHash = repository.getCodeHashNonStandard(COW);
        assertEquals(codeKeccak, codeHash);

        //Empty code hash
        Keccak256 emptyCodeHash = repository.getCodeHashNonStandard(HORSE);
        assertEquals(emptyKeccak, emptyCodeHash);

        //Code hash of non-existing account
        Keccak256 hashOfNonExistingAccount = repository.getCodeHashNonStandard(addressNotAdded);
        assertTrue(hashOfNonExistingAccount.equals(Keccak256.ZERO_HASH));
    }

    @Test
    public void testCreateAccount_IgnoreExistingBalance() {
        Repository track = repository.startTracking();
        Coin tenCoins = Coin.valueOf(10L);

        track.addBalance(COW, tenCoins);

        track.createAccount(COW);
        Coin newBalance = track.getBalance(COW);
        assertEquals(Coin.ZERO, newBalance);

        track.addBalance(HORSE, tenCoins);

        track.createAccount(HORSE, false);
        newBalance = track.getBalance(HORSE);
        assertEquals(Coin.ZERO, newBalance);
    }

    @Test
    public void testCreateAccount_PreserveExistingBalance() {
        Repository track = repository.startTracking();
        Coin tenCoins = Coin.valueOf(10L);

        track.addBalance(COW, tenCoins);

        track.createAccount(COW, true);
        Coin newBalance = track.getBalance(COW);
        assertEquals(tenCoins, newBalance);
    }

    private static Keccak256 getKeccak256Hash(byte[] emptyCode) {
        return new Keccak256(Keccak256Helper.keccak256(emptyCode));
    }

}
