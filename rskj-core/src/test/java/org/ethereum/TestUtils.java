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

import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockHeader;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.junit.jupiter.api.Assertions;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import javax.annotation.Nonnull;
import java.io.File;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.*;


public final class TestUtils {

    private TestUtils() {
    }

    public static DataWord generateDataWord() {
        return DataWord.valueOf(generateBytes(DataWord.class.hashCode(), 32));
    }

    public static RskAddress generateAddress(String discriminator) {
        return new RskAddress(generateBytes(TestUtils.class, discriminator, 20));
    }

    public static Keccak256 generateHash(String discriminator) {
        return new Keccak256(generateBytes(TestUtils.class, discriminator, 32));
    }

    public static Long generateLong(@Nonnull String discriminator) {
        return new Random(discriminator.hashCode()).nextLong();
    }

    public static Integer generateInt(@Nonnull String discriminator){
        return new Random(discriminator.hashCode()).nextInt();
    }

    public static Integer generateInt(@Nonnull String discriminator, int bound){
        return new Random(discriminator.hashCode()).nextInt(bound);
    }

    public static ECKey generateECKey(@Nonnull String discriminator) {
        return new ECKey(new SecureRandom(discriminator.getBytes()));
    }

    public static DB createMapDB(String testDBDir) {

        String blocksIndexFile = testDBDir + "/blocks/index";
        File dbFile = new File(blocksIndexFile);
        if (!dbFile.getParentFile().exists()) dbFile.getParentFile().mkdirs();

        DB db = DBMaker.fileDB(dbFile)
                .transactionDisable()
                .closeOnJvmShutdown()
                .make();


        return db;
    }

    public static List<Block> getRandomChain(BlockFactory blockFactory, byte[] startParentHash, long startNumber, long length) {

        List<Block> result = new ArrayList<>();

        byte[] lastHash = startParentHash;
        long lastIndex = startNumber;

        Random random = new Random(blockFactory.hashCode());
        for (int i = 0; i < length; ++i) {

            byte[] difficutly = new BigInteger(8, random).toByteArray();
            byte[] newHash = generateBytes("newHash", 32);

            BlockHeader newHeader = blockFactory.getBlockHeaderBuilder()
                    .setParentHash(lastHash)
                    .setUnclesHash(newHash)
                    .setCoinbase(RskAddress.nullAddress())
                    .setStateRoot(generateBytes("newHeader", 32))
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

    public static InetAddress generateIpAddressV4(@Nonnull String discriminator) throws UnknownHostException {
        byte[] bytes = TestUtils.generateBytes(discriminator, 4);
        return InetAddress.getByAddress(bytes);
    }

    public static InetAddress generateIpAddressV6(@Nonnull String discriminator) throws UnknownHostException {
        byte[] bytes = TestUtils.generateBytes(discriminator, 16);
        return InetAddress.getByAddress(bytes);
    }

    public static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    public static <T extends Exception> T assertThrows(Class<T> c, Runnable f) {
        Exception thrownException = null;
        try {
            f.run();
        } catch (Exception e) {
            thrownException = e;
        }

        Assertions.assertNotNull(thrownException);
        Assertions.assertEquals(thrownException.getClass(), c);
        return c.cast(thrownException);
    }

    public static <T, V> void setInternalState(T instance, String fieldName, V value) {
        Field field = getPrivateField(instance, fieldName);
        field.setAccessible(true);

        try {
            field.set(instance, value);
        } catch (IllegalAccessException re) {
            throw new WhiteboxException("Could not set private field", re);
        }
    }

    public static <T> T getInternalState(Object instance, String fieldName) {
        Field field = getPrivateField(instance, fieldName);
        field.setAccessible(true);

        try {
            return (T) field.get(instance);
        } catch (IllegalAccessException re) {
            throw new WhiteboxException("Could not get private field", re);
        }
    }

    private static Field getPrivateField(Object instance, String fieldName) {
        Field field = FieldUtils.getAllFieldsList(instance.getClass())
                .stream()
                .filter(f -> f.getName().equals(fieldName))
                .findFirst()
                .orElse(null);

        if (field == null) {
            throw new WhiteboxException("Field not found in class");
        }

        return field;
    }

    private static class WhiteboxException extends RuntimeException {
        public WhiteboxException(String message) {
            super(message);
        }

        public WhiteboxException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Generates a deterministic byte[], of requested length, for the provided seed
     *
     * @param seed   Seed to generate result from
     * @param length Expected result length
     * @return the deterministic generated byte[]
     */
    public static byte[] generateBytes(long seed, int length) {
        byte[] result = new byte[length];
        new Random(seed).nextBytes(result);
        return result;
    }

    /**
     * Generates a deterministic byte[], of requested length, for the given class and discriminator
     *
     * @param clazz         Class to generate a value for. hashCode() is applied to this parameter to obtain a seed.
     * @param discriminator String to differentiate this usage from others within the same class, method, etc. hashCode() is applied to this parameter to obtain a salt.
     * @param length        Expected result length
     * @return the deterministic generated byte[]
     */
    public static byte[] generateBytes(Class<?> clazz, String discriminator, int length) {
        long seedWithSalt = 31L * clazz.getName().hashCode() + discriminator.hashCode();
        return generateBytes(seedWithSalt, length);
    }

    public static byte[] generateBytes(@Nonnull String discriminator, @Nonnull int length) {
        return generateBytes(TestUtils.class, discriminator, length);
    }

    public static byte[] generateBytesFromRandom(@Nonnull Random random, @Nonnull int size) {
        byte[] byteArray = new byte[size];
        random.nextBytes(byteArray);
        return byteArray;
    }

    /**
     * @return generates random peer id for the HelloMessage
     */
    public static byte[] generatePeerId(@Nonnull String discriminator) {
        Random random = new SecureRandom(discriminator.getBytes());
        byte[] peerIdBytes = new BigInteger(512, random).toByteArray();

        final String peerId;
        if (peerIdBytes.length > 64) {
            peerId = ByteUtil.toHexString(peerIdBytes, 1, 64);
        } else {
            peerId = ByteUtil.toHexString(peerIdBytes);
        }

        return Hex.decode(peerId);
    }

}
