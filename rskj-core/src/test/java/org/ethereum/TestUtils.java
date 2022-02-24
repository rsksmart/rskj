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

package org.ethereum;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

import java.io.File;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.apache.commons.lang3.StringUtils;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockHeader;
import org.ethereum.crypto.HashUtil;
import org.ethereum.vm.DataWord;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;

public final class TestUtils {

    private TestUtils() {
    }

    // Fix the Random object to make tests more deterministic. Each new Random object
    // created gets a seed xores with system nanoTime.
    // Alse it reduces the time to get the random in performance tests
    static Random aRandom;

    static public Random getRandom() {
        if (aRandom==null)
            aRandom = new Random();
        return aRandom;
    }

    public static byte[] randomBytes(int length) {
        byte[] result = new byte[length];
        getRandom().nextBytes(result);
        return result;
    }

    public static short[] randomShortArray(int length) {
        short[] result = new short[length];
        byte[] bytes = new byte[length * 2];
        getRandom().nextBytes(bytes);
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(result);
        return result;
    }

    public static BigInteger randomBigInteger(int maxSizeBytes) {
        return new BigInteger(maxSizeBytes*8,getRandom());
    }

    public static Coin randomCoin(int decimalZeros,int maxValue) {
        return new Coin(BigInteger.TEN.pow(decimalZeros).multiply(
                BigInteger.valueOf(getRandom().nextInt(maxValue))));
    }

    public static DataWord randomDataWord() {
        return DataWord.valueOf(randomBytes(32));
    }

    public static RskAddress randomAddress() {
        return new RskAddress(randomBytes(20));
    }

    public static Keccak256 randomHash() {
        return new Keccak256(randomBytes(32));
    }

    public static DB createMapDB(String testDBDir){

        String blocksIndexFile = testDBDir + "/blocks/index";
        File dbFile = new File(blocksIndexFile);
        if (!dbFile.getParentFile().exists()) dbFile.getParentFile().mkdirs();

        DB db = DBMaker.fileDB(dbFile)
                .transactionDisable()
                .closeOnJvmShutdown()
                .make();


        return db;
    }

    public static List<Block> getRandomChain(BlockFactory blockFactory, byte[] startParentHash, long startNumber, long length){

        List<Block> result = new ArrayList<>();

        byte[] lastHash = startParentHash;
        long lastIndex = startNumber;


        for (int i = 0; i < length; ++i){

            byte[] difficutly = new BigInteger(8, new Random()).toByteArray();
            byte[] newHash = HashUtil.randomHash();

            BlockHeader newHeader = blockFactory.getBlockHeaderBuilder()
                    .setParentHash(lastHash)
                    .setUnclesHash(newHash)
                    .setCoinbase(RskAddress.nullAddress())
                    .setStateRoot(HashUtil.randomHash())
                    .setDifficultyFromBytes(difficutly)
                    .setNumber(lastIndex)
                    .build();

            Block block = blockFactory.newBlock(
                    newHeader,
                    Collections.emptyList(),
                    Collections.emptyList()
            );

            ++lastIndex;
            lastHash = block.getHash().getBytes();
            result.add(block);
        }

        return result;
    }

    public static String padRight(String s, int n) {
        return String.format("%1$-" + n + "s", s);
    }

    public static String padLeft(String s, int n) {
        return String.format("%1$" + n + "s", s);
    }

    public static String padZeroesLeft(String s, int n) {
        return StringUtils.leftPad(s, n, '0');
    }

    public static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    public static<T extends Exception>  T assertThrows(Class<T> c, Runnable f) {
        Exception thrownException = null;
        try {
            f.run();
        } catch (Exception e) {
            thrownException = e;
        }

        assertNotNull(thrownException);
        assertEquals(thrownException.getClass(), c);
        return c.cast(thrownException);
    }
}
