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
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.AccountState;
import org.ethereum.core.Genesis;
import org.ethereum.core.Repository;
import org.ethereum.crypto.HashUtil;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.MutableRepository;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RskTestFactory;
import org.ethereum.vm.DataWord;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.math.BigInteger;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.hamcrest.Matchers.is;
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

        assertEquals(BigInteger.ONE, getNonce(repository,COW));

        repository.increaseNonce(COW);
    }

    @Test
    public void test2() {
        Repository repository = createRepository();

        repository.addBalance(COW, Coin.valueOf(10L));
        repository.addBalance(HORSE, Coin.valueOf(1L));

        assertEquals(BigInteger.TEN, balanceAsBI(repository,COW));
        assertEquals(BigInteger.ONE, balanceAsBI(repository,HORSE));
    }

    @Test
    public void test3() {
        Repository repository = createRepository();

        byte[] cowCode = Hex.decode("A1A2A3");
        byte[] horseCode = Hex.decode("B1B2B3");

        repository.saveCode(COW, cowCode);
        repository.saveCode(HORSE, horseCode);

        assertArrayEquals(cowCode, getCode(repository,COW));
        assertArrayEquals(horseCode, getCode(repository,HORSE));
    }
    private byte[] getCode(Repository repository, RskAddress addr) {
        return repository.getCode(addr,false);
    } 
    @Test
    public void test4() {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        byte[] cowKey = Hex.decode("A1A2A3");
        byte[] cowValue = Hex.decode("A4A5A6");

        byte[] horseKey = Hex.decode("B1B2B3");
        byte[] horseValue = Hex.decode("B4B5B6");

        track.addStorageRow(COW, DataWord.valueOf(cowKey), DataWord.valueOf(cowValue));
        track.addStorageRow(HORSE, DataWord.valueOf(horseKey), DataWord.valueOf(horseValue));
        track.commit();

        assertEquals(DataWord.valueOf(cowValue), getStorageValue(repository,COW, DataWord.valueOf(cowKey)));
        assertEquals(DataWord.valueOf(horseValue), getStorageValue(repository,HORSE, DataWord.valueOf(horseKey)));
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

        assertEquals(BigInteger.TEN, getNonce(repository,COW));
        assertEquals(BigInteger.ONE, getNonce(repository,HORSE));
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

        assertEquals(BigInteger.TEN, getNonce(track,COW));
        assertEquals(BigInteger.ONE, getNonce(track,HORSE));

        track.rollback();

        assertEquals(BigInteger.ZERO, getNonce(repository,COW));
        assertEquals(BigInteger.ZERO, getNonce(repository,HORSE));
    }

    @Test
    public void test7() {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        track.addBalance(COW, Coin.valueOf(10L));
        track.addBalance(HORSE, Coin.valueOf(1L));

        assertEquals(BigInteger.TEN, balanceAsBI(track,COW));
        assertEquals(BigInteger.ONE, balanceAsBI(track,HORSE));

        track.commit();

        assertEquals(BigInteger.TEN, balanceAsBI(repository,COW));
        assertEquals(BigInteger.ONE, balanceAsBI(repository,HORSE));
    }

    @Test
    public void test8() {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        track.addBalance(COW, Coin.valueOf(10L));
        track.addBalance(HORSE, Coin.valueOf(1L));

        assertEquals(BigInteger.TEN, balanceAsBI(track,COW));
        assertEquals(BigInteger.ONE, balanceAsBI(track,HORSE));

        track.rollback();

        assertEquals(BigInteger.ZERO, balanceAsBI(repository,COW));
        assertEquals(BigInteger.ZERO, balanceAsBI(repository,HORSE));
    }

    private BigInteger getNonce(Repository repository, RskAddress addr) {
        return repository.getNonce(addr,false);
    }
    
    private BigInteger balanceAsBI(Repository repository, RskAddress addr) {
        return repository.getBalance(addr,false).asBigInteger();
    }
    
    @Test
    public void test7_1() {
        Repository repository = createRepository();
        Repository track1 = repository.startTracking();

        track1.addBalance(COW, Coin.valueOf(10L));
        track1.addBalance(HORSE, Coin.valueOf(1L));

        assertEquals(BigInteger.TEN, balanceAsBI(track1,COW));
        assertEquals(BigInteger.ONE, balanceAsBI(track1,HORSE));

        Repository track2 = track1.startTracking();

        assertEquals(BigInteger.TEN, balanceAsBI(track2,COW));
        assertEquals(BigInteger.ONE, balanceAsBI(track2,HORSE));

        track2.addBalance(COW, Coin.valueOf(10L));
        track2.addBalance(COW, Coin.valueOf(10L));
        track2.addBalance(COW, Coin.valueOf(10L));

        track2.commit();

        track1.commit();

        assertEquals(new BigInteger("40"), balanceAsBI(repository,COW));
        assertEquals(BigInteger.ONE, balanceAsBI(repository,HORSE));
    }

    @Test
    public void test7_2() {
        Repository repository = createRepository();
        Repository track1 = repository.startTracking();

        track1.addBalance(COW, Coin.valueOf(10L));
        track1.addBalance(HORSE, Coin.valueOf(1L));

        assertEquals(BigInteger.TEN, balanceAsBI(track1,COW));
        assertEquals(BigInteger.ONE, balanceAsBI(track1,HORSE));

        Repository track2 = track1.startTracking();

        assertEquals(BigInteger.TEN, balanceAsBI(track2,COW));
        assertEquals(BigInteger.ONE, balanceAsBI(track2,HORSE));

        track2.addBalance(COW, Coin.valueOf(10L));
        track2.addBalance(COW, Coin.valueOf(10L));
        track2.addBalance(COW, Coin.valueOf(10L));

        track2.commit();

        track1.rollback();

        assertEquals(BigInteger.ZERO, balanceAsBI(repository,COW));
        assertEquals(BigInteger.ZERO, balanceAsBI(repository,HORSE));
    }

    @Test
    public void test9() {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        DataWord cowKey = DataWord.valueOf(Hex.decode("A1A2A3"));
        DataWord cowValue = DataWord.valueOf(Hex.decode("A4A5A6"));

        DataWord horseKey = DataWord.valueOf(Hex.decode("B1B2B3"));
        DataWord horseValue = DataWord.valueOf(Hex.decode("B4B5B6"));

        track.addStorageRow(COW, cowKey, cowValue);
        track.addStorageRow(HORSE, horseKey, horseValue);

        assertEquals(cowValue, getStorageValue(track,COW, cowKey));
        assertEquals(horseValue, getStorageValue(track,HORSE, horseKey));

        track.commit();

        assertEquals(cowValue, getStorageValue(repository,COW, cowKey));
        assertEquals(horseValue, getStorageValue(repository,HORSE, horseKey));
    }

    @Test
    public void test10() {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        DataWord cowKey = DataWord.valueOf(Hex.decode("A1A2A3"));
        DataWord cowValue = DataWord.valueOf(Hex.decode("A4A5A6"));

        DataWord horseKey = DataWord.valueOf(Hex.decode("B1B2B3"));
        DataWord horseValue = DataWord.valueOf(Hex.decode("B4B5B6"));

        track.addStorageRow(COW, cowKey, cowValue);
        track.addStorageRow(HORSE, horseKey, horseValue);

        assertEquals(cowValue, getStorageValue(track,COW, cowKey));
        assertEquals(horseValue, getStorageValue(track,HORSE, horseKey));

        track.rollback();
        // getStorageValue() returns always a DataWord, not null anymore
        assertEquals(null, getStorageValue(repository,COW, cowKey));
        assertEquals(null, getStorageValue(repository,HORSE, horseKey));
    }


    @Test
    public void test11() {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        byte[] cowCode = Hex.decode("A1A2A3");
        byte[] horseCode = Hex.decode("B1B2B3");

        track.saveCode(COW, cowCode);
        track.saveCode(HORSE, horseCode);

        assertArrayEquals(cowCode, getCode(track,COW));
        assertArrayEquals(horseCode, getCode(track,HORSE));

        track.commit();

        assertArrayEquals(cowCode, getCode(repository,COW));
        assertArrayEquals(horseCode, getCode(repository,HORSE));
    }

    @Test
    public void test12() {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        byte[] cowCode = Hex.decode("A1A2A3");
        byte[] horseCode = Hex.decode("B1B2B3");

        track.saveCode(COW, cowCode);
        track.saveCode(HORSE, horseCode);

        assertArrayEquals(cowCode, getCode(track,COW));
        assertArrayEquals(horseCode, getCode(track,HORSE));

        track.rollback();

        assertArrayEquals(EMPTY_BYTE_ARRAY, getCode(repository,COW));
        assertArrayEquals(EMPTY_BYTE_ARRAY, getCode(repository,HORSE));
    }

    @Test  // Let's upload genesis pre-mine just like in the real world
    public void test13() {
        Repository repository = createRepository();
        Repository track = repository.startTracking();

        Genesis genesis = RskTestFactory.getGenesisInstance(config);
        for (Map.Entry<RskAddress, AccountState> accountsEntry : genesis.getAccounts().entrySet()) {
            RskAddress accountAddress = accountsEntry.getKey();
            repository.createAccount(accountAddress);
            repository.addBalance(accountAddress, accountsEntry.getValue().getBalance());
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

        assertEquals(BigInteger.TEN, balanceAsBI(track1,COW));
        assertEquals(BigInteger.ONE, balanceAsBI(track1,HORSE));


        // changes level_2
        Repository track2 = track1.startTracking();
        track2.addBalance(COW, Coin.valueOf(1L));
        track2.addBalance(HORSE, Coin.valueOf(10L));

        assertEquals(ELEVEN, balanceAsBI(track2,COW));
        assertEquals(ELEVEN, balanceAsBI(track2,HORSE));

        track2.commit();
        track1.commit();

        assertEquals(ELEVEN, balanceAsBI(repository,COW));
        assertEquals(ELEVEN, balanceAsBI(repository,HORSE));
    }

    @Test
    public void test15() {
        Repository repository = createRepository();

        final BigInteger ELEVEN = BigInteger.TEN.add(BigInteger.ONE);


        // changes level_1
        Repository track1 = repository.startTracking();
        track1.addBalance(COW, Coin.valueOf(10L));
        track1.addBalance(HORSE, Coin.valueOf(1L));

        assertEquals(BigInteger.TEN, balanceAsBI(track1,COW));
        assertEquals(BigInteger.ONE, balanceAsBI(track1,HORSE));

        // changes level_2
        Repository track2 = track1.startTracking();
        track2.addBalance(COW, Coin.valueOf(1L));
        track2.addBalance(HORSE, Coin.valueOf(10L));

        assertEquals(ELEVEN, balanceAsBI(track2,COW));
        assertEquals(ELEVEN, balanceAsBI(track2,HORSE));

        track2.rollback();
        track1.commit();

        assertEquals(BigInteger.TEN, balanceAsBI(repository,COW));
        assertEquals(BigInteger.ONE, balanceAsBI(repository,HORSE));
    }
    private DataWord getStorageValue(Repository repository, RskAddress addr, DataWord key) {
        return repository.getStorageValue(addr,key,false);
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
        track1.addStorageRow(COW, DataWord.valueOf(cowKey1), DataWord.valueOf(cowValue1));
        track1.addStorageRow(HORSE, DataWord.valueOf(horseKey1), DataWord.valueOf(horseValue1));

        assertEquals(DataWord.valueOf(cowValue1), getStorageValue(track1,COW, DataWord.valueOf(cowKey1)));
        assertEquals(DataWord.valueOf(horseValue1), getStorageValue(track1,HORSE, DataWord.valueOf(horseKey1)));

        // changes level_2
        Repository track2 = track1.startTracking();
        track2.addStorageRow(COW, DataWord.valueOf(cowKey2), DataWord.valueOf(cowValue2));
        track2.addStorageRow(HORSE, DataWord.valueOf(horseKey2), DataWord.valueOf(horseValue2));

        assertEquals(DataWord.valueOf(cowValue1), getStorageValue(track2,COW, DataWord.valueOf(cowKey1)));
        assertEquals(DataWord.valueOf(horseValue1), getStorageValue(track2,HORSE, DataWord.valueOf(horseKey1)));

        assertEquals(DataWord.valueOf(cowValue2), getStorageValue(track2,COW, DataWord.valueOf(cowKey2)));
        assertEquals(DataWord.valueOf(horseValue2), getStorageValue(track2,HORSE, DataWord.valueOf(horseKey2)));

        track2.commit();
        // leaving level_2

        assertEquals(DataWord.valueOf(cowValue1), getStorageValue(track1,COW, DataWord.valueOf(cowKey1)));
        assertEquals(DataWord.valueOf(horseValue1), getStorageValue(track1,HORSE, DataWord.valueOf(horseKey1)));

        assertEquals(DataWord.valueOf(cowValue2), getStorageValue(track1,COW, DataWord.valueOf(cowKey2)));
        assertEquals(DataWord.valueOf(horseValue2), getStorageValue(track1,HORSE, DataWord.valueOf(horseKey2)));

        track1.commit();
        // leaving level_1

        assertEquals(DataWord.valueOf(cowValue1), getStorageValue(repository,COW, DataWord.valueOf(cowKey1)));
        assertEquals(DataWord.valueOf(horseValue1), getStorageValue(repository,HORSE, DataWord.valueOf(horseKey1)));

        assertEquals(DataWord.valueOf(cowValue2), getStorageValue(repository,COW, DataWord.valueOf(cowKey2)));
        assertEquals(DataWord.valueOf(horseValue2), getStorageValue(repository,HORSE, DataWord.valueOf(horseKey2)));
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
        track2.addStorageRow(COW, DataWord.valueOf(cowKey2), DataWord.valueOf(cowValue2));
        track2.addStorageRow(HORSE, DataWord.valueOf(horseKey2), DataWord.valueOf(horseValue2));

        assertNull(getStorageValue(track2,COW, DataWord.valueOf(cowKey1)));
        assertNull(getStorageValue(track2,HORSE, DataWord.valueOf(horseKey1)));

        assertEquals(DataWord.valueOf(cowValue2), getStorageValue(track2,COW, DataWord.valueOf(cowKey2)));
        assertEquals(DataWord.valueOf(horseValue2), getStorageValue(track2,HORSE, DataWord.valueOf(horseKey2)));

        track2.commit();
        // leaving level_2

        assertNull(getStorageValue(track1,COW, DataWord.valueOf(cowKey1)));
        assertNull(getStorageValue(track1,HORSE, DataWord.valueOf(horseKey1)));

        assertEquals(DataWord.valueOf(cowValue2), getStorageValue(track1,COW, DataWord.valueOf(cowKey2)));
        assertEquals(DataWord.valueOf(horseValue2), getStorageValue(track1,HORSE, DataWord.valueOf(horseKey2)));

        track1.commit();
        // leaving level_1

        assertEquals(null, getStorageValue(repository,COW, DataWord.valueOf(cowKey1)));
        assertEquals(null, getStorageValue(repository,HORSE, DataWord.valueOf(horseKey1)));

        assertEquals(DataWord.valueOf(cowValue2), getStorageValue(repository,COW, DataWord.valueOf(cowKey2)));
        assertEquals(DataWord.valueOf(horseValue2), getStorageValue(repository,HORSE, DataWord.valueOf(horseKey2)));
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
        track2.addStorageRow(COW, DataWord.valueOf(cowKey2), DataWord.valueOf(cowValue2));
        track2.addStorageRow(HORSE, DataWord.valueOf(horseKey2), DataWord.valueOf(horseValue2));

        assertNull(getStorageValue(track2,COW, DataWord.valueOf(cowKey1)));
        assertNull(getStorageValue(track2,HORSE, DataWord.valueOf(horseKey1)));

        assertEquals(DataWord.valueOf(cowValue2), getStorageValue(track2,COW, DataWord.valueOf(cowKey2)));
        assertEquals(DataWord.valueOf(horseValue2), getStorageValue(track2,HORSE, DataWord.valueOf(horseKey2)));

        track2.commit();
        // leaving level_2

        assertNull(getStorageValue(track1,COW, DataWord.valueOf(cowKey1)));
        assertNull(getStorageValue(track1,HORSE, DataWord.valueOf(horseKey1)));

        assertEquals(DataWord.valueOf(cowValue2), getStorageValue(track1,COW, DataWord.valueOf(cowKey2)));
        assertEquals(DataWord.valueOf(horseValue2), getStorageValue(track1,HORSE, DataWord.valueOf(horseKey2)));

        track1.rollback();
        // leaving level_1

        assertNull(getStorageValue(track1,COW, DataWord.valueOf(cowKey1)));
        assertNull(getStorageValue(track1,HORSE, DataWord.valueOf(horseKey1)));

        assertNull(getStorageValue(track1,COW, DataWord.valueOf(cowKey2)));
        assertNull(getStorageValue(track1,HORSE, DataWord.valueOf(horseKey2)));
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
        track.addStorageRow(COW, DataWord.valueOf(cowKey1), DataWord.valueOf(cowValue1));
        track.commit();

        // changes level_1
        Repository track1 = repository.startTracking();

        // changes level_2
        Repository track2 = track1.startTracking();
        track2.addStorageRow(COW, DataWord.valueOf(cowKey2), DataWord.valueOf(cowValue2));

        track2.commit();
        // leaving level_2

        track1.commit();
        // leaving level_1

        assertEquals(DataWord.valueOf(cowValue1), getStorageValue(track1,COW, DataWord.valueOf(cowKey1)));
        assertEquals(DataWord.valueOf(cowValue2), getStorageValue(track1,COW, DataWord.valueOf(cowKey2)));
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
        track1.addStorageRow(COW, DataWord.valueOf(cowKey2), DataWord.valueOf(cowValue2));

        // changes level_2
        Repository track2 = track1.startTracking();
        assertEquals(DataWord.valueOf(cowValue2), getStorageValue(track1,COW, DataWord.valueOf(cowKey2)));
        assertNull(getStorageValue(track1,COW, DataWord.valueOf(cowKey1)));

        track2.commit();
        // leaving level_2

        track1.commit();
        // leaving level_1

        assertEquals(DataWord.valueOf(cowValue2), getStorageValue(track1,COW, DataWord.valueOf(cowKey2)));
        assertNull(getStorageValue(track1,COW, DataWord.valueOf(cowKey1)));
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
        track2.addStorageRow(COW, DataWord.valueOf(cowKey1), DataWord.valueOf(cowValue1));
        assertEquals(DataWord.valueOf(cowValue1), getStorageValue(track2,COW, DataWord.valueOf(cowKey1)));
        track2.rollback();
        // leaving level_2

        track1.commit();
        // leaving level_1

        Assert.assertEquals(ByteUtil.toHexString(HashUtil.EMPTY_TRIE_HASH), ByteUtil.toHexString(repository.getRoot()));
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

        assertEquals(true, isExist(repoTrack2,COW));
        assertEquals(false, isExist(repoTrack2,HORSE));
        assertEquals(false, isExist(repoTrack2,pig));
        assertEquals(false, isExist(repoTrack2,precompiled));
    }
    private boolean isExist(Repository repository, RskAddress addr) {
        return repository.isExist(addr,false);
    }
    @Test
    public void test19() {
        // Creates a repository without store
        Repository repository = createRepository();

        // Problem: the store is probably not copied into the track, which is good
        // BUT how takes care of saving items ?
        Repository track = repository.startTracking();

        DataWord cowKey1 = DataWord.valueFromHex("c1");
        DataWord cowVal1 = DataWord.valueFromHex("c0a1");
        DataWord cowVal0 = DataWord.valueFromHex("c0a0");

        DataWord horseKey1 = DataWord.valueFromHex("e1");
        DataWord horseVal1 = DataWord.valueFromHex("c0a1");
        DataWord horseVal0 = DataWord.valueFromHex("c0a0");

        track.addStorageRow(COW, cowKey1, cowVal0);
        track.addStorageRow(HORSE, horseKey1, horseVal0);
        track.commit();


        DataWord horseValAfter = getStorageValue(repository,HORSE,horseKey1);
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

        assertThat(getStorageValue(repository,COW, cowKey1), is(cowVal0));
        assertThat(getStorageValue(repository,HORSE, horseKey1), is(horseVal0));
    }

    private boolean running = true;

    @Test // testing for snapshot
    public void testMultiThread() throws InterruptedException {
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        MutableTrieImpl mutableTrie = new MutableTrieImpl(trieStore, new Trie(trieStore));
        final Repository repository = new MutableRepository(mutableTrie);

        final DataWord cowKey1 = DataWord.valueFromHex("c1");
        final DataWord cowKey2 = DataWord.valueFromHex("c2");
        final DataWord cowVal0 = DataWord.valueFromHex("c0a0");

        Repository track2 = repository.startTracking();
        track2.addStorageRow(COW, cowKey2, cowVal0);
        track2.commit();
        // Changes commited to repository

        assertThat(getStorageValue(repository,COW, cowKey2), is(cowVal0));

        final CountDownLatch failSema = new CountDownLatch(1);
        // First create the 10 snapshots. The snapshots should not be created while the
        // repository is being changed.
        Repository[] snaps = new Repository[10];

        for (int i = 0; i < 10; ++i) {
            snaps[i] = new MutableRepository(trieStore, trieStore.retrieve(repository.getRoot()).get());
        }
        for (int i = 0; i < 10; ++i) {
            int finalI = i;
            new Thread(() -> {
                try {
                    int cnt = 1;
                    while (running) {
                        Repository snap = snaps[finalI].startTracking();
                        snap.addBalance(COW, Coin.valueOf(10L));
                        snap.addStorageRow(COW, cowKey1, DataWord.valueOf(cnt));
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
                    DataWord cVal = DataWord.valueOf(cnt);
                    track21.addStorageRow(COW, cowKey1, cVal);
                    track21.addBalance(COW, Coin.valueOf(1L));
                    track21.commit();

                    assertEquals(BigInteger.valueOf(cnt), balanceAsBI(repository,COW));
                    assertEquals(cVal, getStorageValue(repository,COW, cowKey1));
                    assertEquals(cowVal0, getStorageValue(repository,COW, cowKey2));
                    cnt++;
                }
            } catch (Throwable e) {
                e.printStackTrace();
                try {
                    repository.addStorageRow(COW, cowKey1, DataWord.valueOf(123));
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

    private static Repository createRepository() {
        return new MutableRepository(new MutableTrieImpl(null, new Trie()));
    }
}
