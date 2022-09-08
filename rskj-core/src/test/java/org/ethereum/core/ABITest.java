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

package org.ethereum.core;

import co.rsk.core.RskAddress;
import org.ethereum.config.Constants;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Anton Nashatyrev
 */
public class ABITest {

    private static final Logger logger = LoggerFactory.getLogger("test");

    @Test
    public void testTransactionCreate() {
        // demo only
        CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson1);
        Transaction ctx = CallTransaction.createCallTransaction(1, 1_000_000_000,
                1_000_000_000, new RskAddress("86e0497e32a8e1d79fe38ab87dc80140df5470d9"), 0, function, Constants.REGTEST_CHAIN_ID, "1234567890abcdef1234567890abcdef12345678");
        ctx.sign(Keccak256Helper.keccak256("974f963ee4571e86e5f9bc3b493e453db9c15e5bd19829a4ef9a790de0da0015".getBytes()));
    }

    static String funcJson1 = "{ \n" +
                            "  'constant': false, \n" +
                            "  'inputs': [{'name':'to', 'type':'address'}], \n" +
                            "  'name': 'delegate', \n" +
                            "  'outputs': [], \n" +
                            "  'type': 'function' \n" +
                            "} \n";
    static {funcJson1 = funcJson1.replaceAll("'", "\"");}

    @Test
    public void testSimple1() {

        logger.info("\n{}", funcJson1);

        CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson1);

        Assertions.assertEquals("5c19a95c0000000000000000000000001234567890abcdef1234567890abcdef12345678",
                ByteUtil.toHexString(function.encode("1234567890abcdef1234567890abcdef12345678")));
        Assertions.assertEquals("5c19a95c0000000000000000000000001234567890abcdef1234567890abcdef12345678",
                ByteUtil.toHexString(function.encode("0x1234567890abcdef1234567890abcdef12345678")));
        try {
            ByteUtil.toHexString(function.encode("0xa1234567890abcdef1234567890abcdef12345678"));
            Assertions.assertTrue(false);
        } catch (Exception e) {}

        try {
            ByteUtil.toHexString(function.encode("blabla"));
            Assertions.assertTrue(false);
        } catch (Exception e) {}
    }

    static String funcJson2 = "{\n" +
            " 'constant':false, \n" +
            " 'inputs':[], \n" +
            " 'name':'tst', \n" +
            " 'outputs':[], \n" +
            " 'type':'function' \n" +
            "}";
    static {funcJson2 = funcJson2.replaceAll("'", "\"");}

    @Test
    public void testSimple2() {

        logger.info("\n{}", funcJson2);

        CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson2);
        Transaction ctx = CallTransaction.createCallTransaction(1, 1_000_000_000, 1_000_000_000,
                new RskAddress("86e0497e32a8e1d79fe38ab87dc80140df5470d9"), 0, function, Constants.REGTEST_CHAIN_ID);
        ctx.sign(Keccak256Helper.keccak256("974f963ee4571e86e5f9bc3b493e453db9c15e5bd19829a4ef9a790de0da0015".getBytes()));

        Assertions.assertEquals("91888f2e", ByteUtil.toHexString(ctx.getData()));
    }

    static String funcJson3 = "{\n" +
            " 'constant':false, \n" +
            " 'inputs':[ \n" +
            "   {'name':'i','type':'int'}, \n" +
            "   {'name':'u','type':'uint'}, \n" +
            "   {'name':'i8','type':'int8'}, \n" +
            "   {'name':'b2','type':'bytes2'}, \n" +
            "   {'name':'b32','type':'bytes32'} \n" +
            "  ], \n" +
            "  'name':'f1', \n" +
            "  'outputs':[], \n" +
            "  'type':'function' \n" +
            "}\n";
    static {funcJson3 = funcJson3.replaceAll("'", "\"");}

    @Test
    public void test3() {

        logger.info("\n{}", funcJson3);

        CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson3);

        Assertions.assertEquals("a4f72f5a" +
                "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffb2e" +
                "00000000000000000000000000000000000000000000000000000000000004d2" +
                "000000000000000000000000000000000000000000000000000000000000007b61" +
                "000000000000000000000000000000000000000000000000000000000000007468" +
                "6520737472696e6700000000000000000000000000000000000000000000",
                ByteUtil.toHexString(function.encode(-1234, 1234, 123, "a", "the string")));
    }

    static String funcJson4 = "{\n" +
            " 'constant':false, \n" +
            " 'inputs':[{'name':'i','type':'int[3]'}, {'name':'j','type':'int[]'}], \n" +
            " 'name':'f2', \n" +
            " 'outputs':[], \n" +
            " 'type':'function' \n" +
            "}\n";
    static {funcJson4 = funcJson4.replaceAll("'", "\"");};


    @Test
    public void test4() {

        logger.info("\n{}", funcJson4);

        CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson4);
        Assertions.assertEquals("d383b9f6" +
                        "0000000000000000000000000000000000000000000000000000000000000001" +
                        "0000000000000000000000000000000000000000000000000000000000000002" +
                        "0000000000000000000000000000000000000000000000000000000000000003",
                ByteUtil.toHexString(function.encode(new int[] {1,2,3})));

        Assertions.assertEquals(
                "d383b9f60000000000000000000000000000000000000000000000000000000000000001" +
                        "0000000000000000000000000000000000000000000000000000000000000002" +
                        "0000000000000000000000000000000000000000000000000000000000000003" +
                        "0000000000000000000000000000000000000000000000000000000000000080" +
                        "0000000000000000000000000000000000000000000000000000000000000002" +
                        "0000000000000000000000000000000000000000000000000000000000000004" +
                        "0000000000000000000000000000000000000000000000000000000000000005",
                ByteUtil.toHexString(function.encode(new int[]{1, 2, 3}, new int[]{4, 5})));

    }

    static String funcJson5 = "{\n" +
            "   'constant':false, \n" +
            "   'inputs':[{'name':'i','type':'int'}, \n" +
            "               {'name':'s','type':'bytes'}, \n" +
            "               {'name':'j','type':'int'}], \n" +
            "    'name':'f4', \n" +
            "    'outputs':[], \n" +
            "    'type':'function' \n" +
            "}\n";
    static {funcJson5 = funcJson5.replaceAll("'", "\"");};

    @Test
    public void test5() {

        logger.info("\n{}", funcJson5);

        CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson5);

        Assertions.assertEquals(
                "3ed2792b000000000000000000000000000000000000000000000000000000000000006f" +
                        "0000000000000000000000000000000000000000000000000000000000000060" +
                        "00000000000000000000000000000000000000000000000000000000000000de" +
                        "0000000000000000000000000000000000000000000000000000000000000003" +
                        "abcdef0000000000000000000000000000000000000000000000000000000000",
            ByteUtil.toHexString(function.encode(111, new byte[] {(byte) 0xab, (byte) 0xcd, (byte) 0xef}, 222)));

    }

    @Test
    public void decodeDynamicTest1() {
        String funcJson = "{\n" +
                "   'constant':false, \n" +
                "   'inputs':[{'name':'i','type':'int'}, \n" +
                "               {'name':'s','type':'bytes'}, \n" +
                "               {'name':'j','type':'int'}], \n" +
                "    'name':'f4', \n" +
                "   'outputs':[{'name':'i','type':'int'}, \n" +
                "               {'name':'s','type':'bytes'}, \n" +
                "               {'name':'j','type':'int'}], \n" +
                "    'type':'function' \n" +
                "}\n";
        funcJson = funcJson.replaceAll("'", "\"");

        CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
        byte[] bytes = new byte[]{(byte) 0xab, (byte) 0xcd, (byte) 0xef};
        byte[] encoded = function.encodeArguments(111, bytes, 222);

        Object[] objects = function.decodeResult(encoded);
//        System.out.println(Arrays.toString(objects));
        Assertions.assertEquals(((Number) objects[0]).intValue(), 111);
        Assertions.assertArrayEquals((byte[]) objects[1], bytes);
        Assertions.assertEquals(((Number) objects[2]).intValue(), 222);
    }
    @Test
    public void decodeDynamicTest2() {
        String funcJson = "{\n" +
                "   'constant':false, \n" +
                "   'inputs':[{'name':'i','type':'int'}, \n" +
                "               {'name':'s','type':'string[]'}, \n" +
                "               {'name':'j','type':'int'}], \n" +
                "    'name':'f4', \n" +
                "   'outputs':[{'name':'i','type':'int'}, \n" +
                "               {'name':'s','type':'string[]'}, \n" +
                "               {'name':'j','type':'int'}], \n" +
                "    'type':'function' \n" +
                "}\n";
        funcJson = funcJson.replaceAll("'", "\"");

        CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
        String[] strings = new String[] {"aaa", "long string: 123456789012345678901234567890", "ccc"};
        byte[] encoded = function.encodeArguments(111, strings, 222);
        Object[] objects = function.decodeResult(encoded);
//        System.out.println(Arrays.toString(objects));
        Assertions.assertEquals(((Number) objects[0]).intValue(), 111);
        Assertions.assertArrayEquals((Object[]) objects[1], strings);
        Assertions.assertEquals(((Number) objects[2]).intValue(), 222);
    }

    @Test
    public void encodeArrayWithInvalidInputShouldFail() {
        String funcJson = "{\n" +
                "   'constant':false, \n" +
                "   'inputs':[" +
                "{'name':'a','type':'string'}," +
                "{'name':'s','type':'string[4]'}], \n" + // I define the input as a string array with 4 positions
                "    'name':'f4', \n" +
                "   'outputs':[], \n" +
                "    'type':'function' \n" +
                "}\n";
        funcJson = funcJson.replaceAll("'", "\"");

        CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
        try {
            String[] strings = new String[] {"aaa", "long string: 123456789012345678901234567890", "ccc"}; // but my input is a smaller array
            function.encodeArguments("a", strings);
            Assertions.fail("should have thrown an exception");
        }
        catch (Exception e) {
            Assertions.assertTrue(e.getMessage().contains("List size"));
        }

        try {
            String[] strings = new String[] {"aaa", "long string: 123456789012345678901234567890", "ccc", "ddd", "eee"}; // but my input is a bigger array
            function.encodeArguments("a", strings);
            Assertions.fail("should have thrown an exception");
        }
        catch (Exception e) {
            Assertions.assertTrue(e.getMessage().contains("List size"));
        }
    }

}
