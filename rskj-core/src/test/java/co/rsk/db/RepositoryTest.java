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

import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.core.Repository;
import org.ethereum.crypto.HashUtil;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.ContractDetails;
import org.ethereum.vm.DataWord;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.spongycastle.util.encoders.Hex;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * This tests are based on the org.ethereum.db.RepositoryTest.java
 * It's main goal is to add more coverage.
 * TODO: Revisit test names to make them more declarative
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class RepositoryTest {
    @Test
    public void test4() {

        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        byte[] cow = Hex.decode("CD2A3D9F938E13CD947EC05ABC7FE734DF8DD826");
        byte[] horse = Hex.decode("13978AEE95F38490E9769C39B2773ED763D9CD5F");

        byte[] cowKey = Hex.decode("A1A2A3");
        byte[] cowValue = Hex.decode("A4A5A6");

        byte[] horseKey = Hex.decode("B1B2B3");
        byte[] horseValue = Hex.decode("B4B5B6");

        track.addStorageBytes(cow, new DataWord(cowKey), cowValue);
        track.addStorageBytes(horse, new DataWord(horseKey), horseValue);
        track.commit();

        assertArrayEquals(cowValue, repository.getStorageBytes(cow, new DataWord(cowKey)));
        assertArrayEquals(horseValue, repository.getStorageBytes(horse, new DataWord(horseKey)));

        repository.close();
    }

    @Test
    public void test9() {

        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        byte[] cow = Hex.decode("CD2A3D9F938E13CD947EC05ABC7FE734DF8DD826");
        byte[] horse = Hex.decode("13978AEE95F38490E9769C39B2773ED763D9CD5F");

        DataWord cowKey = new DataWord(Hex.decode("A1A2A3"));
        byte[] cowValue = Hex.decode("A4A5A6");

        DataWord horseKey = new DataWord(Hex.decode("B1B2B3"));
        byte[] horseValue = Hex.decode("B4B5B6");

        track.addStorageBytes(cow, cowKey, cowValue);
        track.addStorageBytes(horse, horseKey, horseValue);

        assertArrayEquals(cowValue, track.getStorageBytes(cow, cowKey));
        assertArrayEquals(horseValue, track.getStorageBytes(horse, horseKey));

        track.commit();

        assertArrayEquals(cowValue, repository.getStorageBytes(cow, cowKey));
        assertArrayEquals(horseValue, repository.getStorageBytes(horse, horseKey));

        repository.close();
    }

    @Test
    public void test10() {

        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        byte[] cow = Hex.decode("CD2A3D9F938E13CD947EC05ABC7FE734DF8DD826");
        byte[] horse = Hex.decode("13978AEE95F38490E9769C39B2773ED763D9CD5F");

        DataWord cowKey = new DataWord(Hex.decode("A1A2A3"));
        byte[] cowValue = Hex.decode("A4A5A6");

        DataWord horseKey = new DataWord(Hex.decode("B1B2B3"));
        byte[] horseValue = Hex.decode("B4B5B6");

        track.addStorageBytes(cow, cowKey, cowValue);
        track.addStorageBytes(horse, horseKey, horseValue);

        assertArrayEquals(cowValue, track.getStorageBytes(cow, cowKey));
        assertArrayEquals(horseValue, track.getStorageBytes(horse, horseKey));

        track.rollback();

        assertEquals(null, repository.getStorageBytes(cow, cowKey));
        assertEquals(null, repository.getStorageBytes(horse, horseKey));

        repository.close();
    }

    @Test
    public void test16() {
        Repository repository = new RepositoryImpl(new TrieStoreImpl(new HashMapDB()));

        byte[] cow = Hex.decode("CD2A3D9F938E13CD947EC05ABC7FE734DF8DD826");
        byte[] horse = Hex.decode("13978AEE95F38490E9769C39B2773ED763D9CD5F");

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
        track1.addStorageBytes(cow, new DataWord(cowKey1), cowValue1);
        track1.addStorageBytes(horse, new DataWord(horseKey1), horseValue1);

        assertArrayEquals(cowValue1, track1.getStorageBytes(cow, new DataWord(cowKey1)));
        assertArrayEquals(horseValue1, track1.getStorageBytes(horse, new DataWord(horseKey1)));

        // changes level_2
        Repository track2 = track1.startTracking();
        track2.addStorageBytes(cow, new DataWord(cowKey2), cowValue2);
        track2.addStorageBytes(horse, new DataWord(horseKey2), horseValue2);

        assertArrayEquals(cowValue1, track2.getStorageBytes(cow, new DataWord(cowKey1)));
        assertArrayEquals(horseValue1, track2.getStorageBytes(horse, new DataWord(horseKey1)));

        assertArrayEquals(cowValue2, track2.getStorageBytes(cow, new DataWord(cowKey2)));
        assertArrayEquals(horseValue2, track2.getStorageBytes(horse, new DataWord(horseKey2)));

        track2.commit();
        // leaving level_2

        assertArrayEquals(cowValue1, track1.getStorageBytes(cow, new DataWord(cowKey1)));
        assertArrayEquals(horseValue1, track1.getStorageBytes(horse, new DataWord(horseKey1)));

        assertArrayEquals(cowValue2, track1.getStorageBytes(cow, new DataWord(cowKey2)));
        assertArrayEquals(horseValue2, track1.getStorageBytes(horse, new DataWord(horseKey2)));

        track1.commit();
        // leaving level_1

        assertArrayEquals(cowValue1, repository.getStorageBytes(cow, new DataWord(cowKey1)));
        assertArrayEquals(horseValue1, repository.getStorageBytes(horse, new DataWord(horseKey1)));

        assertArrayEquals(cowValue2, repository.getStorageBytes(cow, new DataWord(cowKey2)));
        assertArrayEquals(horseValue2, repository.getStorageBytes(horse, new DataWord(horseKey2)));

        repository.close();
    }

    @Test
    public void test16_2() {
        Repository repository = new RepositoryImpl();

        byte[] cow = Hex.decode("CD2A3D9F938E13CD947EC05ABC7FE734DF8DD826");
        byte[] horse = Hex.decode("13978AEE95F38490E9769C39B2773ED763D9CD5F");

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
        track2.addStorageBytes(cow, new DataWord(cowKey2), cowValue2);
        track2.addStorageBytes(horse, new DataWord(horseKey2), horseValue2);

        assertNull(track2.getStorageBytes(cow, new DataWord(cowKey1)));
        assertNull(track2.getStorageBytes(horse, new DataWord(horseKey1)));

        assertArrayEquals(cowValue2, track2.getStorageBytes(cow, new DataWord(cowKey2)));
        assertArrayEquals(horseValue2, track2.getStorageBytes(horse, new DataWord(horseKey2)));

        assertEquals(null, repository.getStorageBytes(cow, new DataWord(cowKey1)));
        assertEquals(null, repository.getStorageBytes(horse, new DataWord(horseKey1)));

        track2.commit();

        assertEquals(null, repository.getStorageBytes(cow, new DataWord(cowKey1)));
        assertEquals(null, repository.getStorageBytes(horse, new DataWord(horseKey1)));

        // leaving level_2

        assertNull(track1.getStorageValue(cow, new DataWord(cowKey1)));
        assertNull(track1.getStorageValue(horse, new DataWord(horseKey1)));

        assertArrayEquals(cowValue2, track1.getStorageBytes(cow, new DataWord(cowKey2)));
        assertArrayEquals(horseValue2, track1.getStorageBytes(horse, new DataWord(horseKey2)));

        assertEquals(null, repository.getStorageBytes(cow, new DataWord(cowKey1)));
        assertEquals(null, repository.getStorageBytes(horse, new DataWord(horseKey1)));

        track1.commit();

        // leaving level_1

        assertEquals(null, repository.getStorageBytes(cow, new DataWord(cowKey1)));
        assertEquals(null, repository.getStorageBytes(horse, new DataWord(horseKey1)));

        assertArrayEquals(cowValue2, repository.getStorageBytes(cow, new DataWord(cowKey2)));
        assertArrayEquals(horseValue2, repository.getStorageBytes(horse, new DataWord(horseKey2)));

        repository.close();
    }

    @Test
    public void test16_3() {
        Repository repository = new RepositoryImpl();

        byte[] cow = Hex.decode("CD2A3D9F938E13CD947EC05ABC7FE734DF8DD826");
        byte[] horse = Hex.decode("13978AEE95F38490E9769C39B2773ED763D9CD5F");

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
        track2.addStorageBytes(cow, new DataWord(cowKey2), cowValue2);
        track2.addStorageBytes(horse, new DataWord(horseKey2), horseValue2);

        assertNull(track2.getStorageBytes(cow, new DataWord(cowKey1)));
        assertNull(track2.getStorageBytes(horse, new DataWord(horseKey1)));

        assertArrayEquals(cowValue2, track2.getStorageBytes(cow, new DataWord(cowKey2)));
        assertArrayEquals(horseValue2, track2.getStorageBytes(horse, new DataWord(horseKey2)));

        track2.commit();
        // leaving level_2

        assertNull(track1.getStorageBytes(cow, new DataWord(cowKey1)));
        assertNull(track1.getStorageBytes(horse, new DataWord(horseKey1)));

        assertArrayEquals(cowValue2, track1.getStorageBytes(cow, new DataWord(cowKey2)));
        assertArrayEquals(horseValue2, track1.getStorageBytes(horse, new DataWord(horseKey2)));

        track1.rollback();
        // leaving level_1

        assertNull(track1.getStorageBytes(cow, new DataWord(cowKey1)));
        assertNull(track1.getStorageBytes(horse, new DataWord(horseKey1)));

        assertNull(track1.getStorageBytes(cow, new DataWord(cowKey2)));
        assertNull(track1.getStorageBytes(horse, new DataWord(horseKey2)));

        repository.close();
    }

    @Test
    public void test16_4() {
        Repository repository = new RepositoryImpl();

        byte[] cow = Hex.decode("CD2A3D9F938E13CD947EC05ABC7FE734DF8DD826");
        byte[] horse = Hex.decode("13978AEE95F38490E9769C39B2773ED763D9CD5F");

        byte[] cowKey1 = "key-c-1".getBytes();
        byte[] cowValue1 = "val-c-1".getBytes();

        byte[] horseKey1 = "key-h-1".getBytes();
        byte[] horseValue1 = "val-h-1".getBytes();

        byte[] cowKey2 = "key-c-2".getBytes();
        byte[] cowValue2 = "val-c-2".getBytes();

        byte[] horseKey2 = "key-h-2".getBytes();
        byte[] horseValue2 = "val-h-2".getBytes();

        Repository track = repository.startTracking();
        track.addStorageBytes(cow, new DataWord(cowKey1), cowValue1);
        track.commit();

        // changes level_1
        Repository track1 = repository.startTracking();

        // changes level_2
        Repository track2 = track1.startTracking();
        track2.addStorageBytes(cow, new DataWord(cowKey2), cowValue2);

        track2.commit();
        // leaving level_2

        track1.commit();
        // leaving level_1

        assertArrayEquals(cowValue1, track1.getStorageBytes(cow, new DataWord(cowKey1)));
        assertArrayEquals(cowValue2, track1.getStorageBytes(cow, new DataWord(cowKey2)));

        repository.close();
    }

    @Test
    public void test16_5() {
        Repository repository = new RepositoryImpl();

        byte[] cow = Hex.decode("CD2A3D9F938E13CD947EC05ABC7FE734DF8DD826");
        byte[] horse = Hex.decode("13978AEE95F38490E9769C39B2773ED763D9CD5F");

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
        track1.addStorageBytes(cow, new DataWord(cowKey2), cowValue2);

        // changes level_2
        Repository track2 = track1.startTracking();
        assertArrayEquals(cowValue2, track1.getStorageBytes(cow, new DataWord(cowKey2)));
        assertNull(track1.getStorageBytes(cow, new DataWord(cowKey1)));

        track2.commit();
        // leaving level_2

        track1.commit();
        // leaving level_1

        assertArrayEquals(cowValue2, track1.getStorageBytes(cow, new DataWord(cowKey2)));
        assertNull(track1.getStorageBytes(cow, new DataWord(cowKey1)));

        repository.close();
    }

    @Test
    public void test17() {
        Repository repository = new RepositoryImpl();

        byte[] cow = Hex.decode("CD2A3D9F938E13CD947EC05ABC7FE734DF8DD826");

        byte[] cowKey1 = "key-c-1".getBytes();
        byte[] cowValue1 = "val-c-1".getBytes();

        // changes level_1
        Repository track1 = repository.startTracking();

        // changes level_2
        Repository track2 = track1.startTracking();
        track2.addStorageBytes(cow, new DataWord(cowKey1), cowValue1);
        assertArrayEquals(cowValue1, track2.getStorageBytes(cow, new DataWord(cowKey1)));
        track2.rollback();
        // leaving level_2

        track1.commit();
        // leaving level_1

        Assert.assertEquals(Hex.toHexString(HashUtil.EMPTY_TRIE_HASH), Hex.toHexString(repository.getRoot()));
        repository.close();
    }

    @Test
    public void test19() {
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        byte[] cow = Hex.decode("CD2A3D9F938E13CD947EC05ABC7FE734DF8DD826");
        byte[] horse = Hex.decode("13978AEE95F38490E9769C39B2773ED763D9CD5F");

        DataWord cowKey1 = new DataWord("c1");
        byte[] cowVal1 = Hex.decode("c0a1");
        byte[] cowVal0 = Hex.decode("c0a0");

        DataWord horseKey1 = new DataWord("e1");
        byte[] horseVal1 = Hex.decode("c0a1");
        byte[] horseVal0 = Hex.decode("c0a0");

        track.addStorageBytes(cow, cowKey1, cowVal0);
        track.addStorageBytes(horse, horseKey1, horseVal0);
        track.commit();

        Repository track2 = repository.startTracking(); //track

        track2.addStorageBytes(horse, horseKey1, horseVal0);
        Repository track3 = track2.startTracking();

        ContractDetails cowDetails = track3.getContractDetails(cow);
        cowDetails.putBytes(cowKey1, cowVal1);

        ContractDetails horseDetails = track3.getContractDetails(horse);
        horseDetails.putBytes(horseKey1, horseVal1);

        track3.commit();
        track2.rollback();

        ContractDetails cowDetailsOrigin = repository.getContractDetails(cow);
        byte[] cowValOrin = cowDetailsOrigin.getBytes(cowKey1);

        ContractDetails horseDetailsOrigin = repository.getContractDetails(horse);
        byte[] horseValOrin = horseDetailsOrigin.getBytes(horseKey1);

        assertArrayEquals(cowVal0, cowValOrin);
        assertArrayEquals(horseVal0, horseValOrin);
    }

    @Test
    public void test19b() {
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        byte[] cow = Hex.decode("CD2A3D9F938E13CD947EC05ABC7FE734DF8DD826");
        byte[] horse = Hex.decode("13978AEE95F38490E9769C39B2773ED763D9CD5F");

        DataWord cowKey1 = new DataWord("c1");
        byte[] cowVal1 = Hex.decode("c0a1");
        byte[] cowVal0 = Hex.decode("c0a0");

        DataWord horseKey1 = new DataWord("e1");
        byte[] horseVal1 = Hex.decode("c0a1");
        byte[] horseVal0 = Hex.decode("c0a0");

        track.addStorageBytes(cow, cowKey1, cowVal0);
        track.addStorageBytes(horse, horseKey1, horseVal0);
        track.commit();

        Repository track2 = repository.startTracking(); //track

        track2.addStorageBytes(horse, horseKey1, horseVal0);
        Repository track3 = track2.startTracking();

        ContractDetails cowDetails = track3.getContractDetails(cow);
        cowDetails.putBytes(cowKey1, cowVal1);

        ContractDetails horseDetails = track3.getContractDetails(horse);
        horseDetails.putBytes(horseKey1, horseVal1);

        track3.commit();
        track2.rollback();

        ContractDetails cowDetailsOrigin = repository.getContractDetails(cow);
        byte[] cowValOrin = cowDetailsOrigin.getBytes(cowKey1);

        ContractDetails horseDetailsOrigin = repository.getContractDetails(horse);
        byte[] horseValOrin = horseDetailsOrigin.getBytes(horseKey1);

        assertArrayEquals(cowVal0, cowValOrin);
        assertArrayEquals(horseVal0, horseValOrin);
    }

    @Test // testing for snapshot
    public void testMultiThread() throws InterruptedException {
        TrieStore store = new TrieStoreImpl(new HashMapDB());
        final Repository repository = new RepositoryImpl(store);

        final byte[] cow = Hex.decode("CD2A3D9F938E13CD947EC05ABC7FE734DF8DD826");

        final DataWord cowKey1 = new DataWord("c1");
        final DataWord cowKey2 = new DataWord("c2");
        final byte[] cowVal0 = Hex.decode("c0a0");

        Repository track2 = repository.startTracking(); //track
        track2.addStorageBytes(cow, cowKey2, cowVal0);
        track2.commit();
        repository.flush();

        ContractDetails cowDetails = repository.getContractDetails(cow);
        assertArrayEquals(cowVal0, cowDetails.getBytes(cowKey2));

        final CountDownLatch failSema = new CountDownLatch(1);

        new Thread(() -> {
            try {
                int cnt = 1;
                while(true) {
                    // To Review, not needed?
                    repository.flush();

                    Repository snap = repository.getSnapshotTo(repository.getRoot()).startTracking();
                    byte[] vcnr = new byte[1];
                    vcnr[0] = (byte)(cnt % 128);
                    snap.addStorageBytes(cow, cowKey1, vcnr);
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
                    track21.addStorageBytes(cow, cowKey1, cVal);
                    track21.commit();

                    repository.flush();

                    assertArrayEquals(cVal, repository.getStorageBytes(cow, cowKey1));
                    assertArrayEquals(cowVal0, repository.getStorageBytes(cow, cowKey2));
                    cnt++;
                }
            } catch (Throwable e) {
                e.printStackTrace();
                failSema.countDown();
            }
        }).start();

        failSema.await(10, TimeUnit.SECONDS);

        if (failSema.getCount() == 0) {
            throw new RuntimeException("Test failed.");
        }
    }
}
