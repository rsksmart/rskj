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

package co.rsk.util;

import co.rsk.core.Coin;
import co.rsk.util.HexUtils;

import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * Created by martin.medina on 3/7/17.
 */
public class HexUtilsTest {

    @Test
    public void stringToByteArray() {
        Assert.assertArrayEquals(new byte[] { 116, 105, 110, 99, 104, 111 }, HexUtils.stringToByteArray("tincho"));
    }

    @Test
    public void stringHexToByteArrayStartsWithZeroX() {
        Assert.assertArrayEquals(new byte[] { 32 }, HexUtils.stringHexToByteArray("0x20"));
    }

    @Test
    public void stringHexToByteArrayLengthNotModTwo() {
        Assert.assertArrayEquals(new byte[] { 2 }, HexUtils.stringHexToByteArray("0x2"));
    }

    @Test
    public void stringHexToByteArray() {
        Assert.assertArrayEquals(new byte[] { 32 }, HexUtils.stringHexToByteArray("20"));
    }

    @Test
    public void toJsonHex() {
        Assert.assertEquals("0x20", HexUtils.toJsonHex(new byte[] { 32 }));
    }

    @Test
    public void toJsonHexNullInput() {
        Assert.assertEquals("0x00", HexUtils.toJsonHex((byte[])null));
    }

    @Test
    public void toUnformattedJsonHex() {
        Assert.assertEquals("0x20", HexUtils.toUnformattedJsonHex(new byte[] { 0x20 }));
    }

    @Test
    public void toUnformattedJsonHex_nullArray() {
        Assert.assertEquals("0x", HexUtils.toUnformattedJsonHex(null));
    }

    @Test
    public void toUnformattedJsonHex_empty() {
        Assert.assertEquals("0x", HexUtils.toUnformattedJsonHex(new byte[0]));
    }

    @Test
    public void toUnformattedJsonHex_twoHex() {
        Assert.assertEquals("0x02", HexUtils.toUnformattedJsonHex(new byte[] {0x2}));
    }

    @Test
    public void toQuantityJsonHex() {
        byte[] toEncode = new byte[]{0x0A};
        Assert.assertEquals("0xa", HexUtils.toQuantityJsonHex(toEncode));
    }

    @Test
    public void toQuantityJsonHex_Zero() {
        byte[] toEncode = new byte[]{0x00, 0x00};
        Assert.assertEquals("0x0", HexUtils.toQuantityJsonHex(toEncode));
    }

    @Test
    public void toQuantityJsonHex_EmptyByteArray() {
        byte[] toEncode = new byte[0];
        Assert.assertEquals("0x0", HexUtils.toQuantityJsonHex(toEncode));
    }

    @Test
    public void toJsonHexCoin() {
        Assert.assertEquals("1234", HexUtils.toJsonHex(new Coin(new BigInteger("1234"))));
    }

    @Test
    public void toJsonHexNullCoin() {
        Assert.assertEquals("", HexUtils.toJsonHex((Coin) null));
    }


    @Test
    public void stringHexToBigIntegerDefaultCase() {
        Assert.assertEquals(new BigInteger("1"), HexUtils.stringHexToBigInteger("0x1"));
    }

    @Test
    public void stringHexToBigIntegerDefaultCase2() {
        Assert.assertEquals(new BigInteger("255"), HexUtils.stringHexToBigInteger("0xff"));
    }

    @Test(expected = NumberFormatException.class)
    public void stringHexToBigIntegerWhenThereIsNoNumber() {
        HexUtils.stringHexToBigInteger("0x");
    }

    @Test(expected = NumberFormatException.class)
    public void stringHexToBigIntegerWhenItIsNotHexa() {
        HexUtils.stringHexToBigInteger("0xg");
    }

    @Test(expected = NumberFormatException.class)
    public void stringHexToBigIntegerWhenItHasLessThanTwoCharacters() {
        HexUtils.stringHexToBigInteger("0");
    }

    @Test(expected = NumberFormatException.class)
    public void stringHexToBigIntegerWhenItIsEmpty() {
        HexUtils.stringHexToBigInteger("");
    }

    @Test(expected = NumberFormatException.class)
    public void stringHexToBigIntegerWhenItDoesNotStartWith0x() {
        HexUtils.stringHexToBigInteger("0d99");
    }
    
    @Test
    public void test_hasHexPrefix_valid_case() {
    	Assert.assertTrue(HexUtils.hasHexPrefix("0x746573746530"));
    }
    
    @Test
    public void test_isHexWithPrefix_valid_invalid_case() {
    	
    	boolean trueCase = HexUtils.isHexWithPrefix("0x746573746530");
    	boolean falseCase = HexUtils.isHexWithPrefix("internet");
    	
    	Assert.assertTrue(trueCase);
    	Assert.assertFalse(falseCase);
    }
   
    @Test
    public void test_encodeToHexByteArray_compare_preencoded() {
    	
    	byte[] strBytes = "internet".getBytes();
    	
    	String encoded = "0x" + Hex.toHexString(strBytes);

		byte[] strEncoded = HexUtils.encodeToHexByteArray(strBytes);
		
		Assert.assertTrue(Arrays.equals(encoded.getBytes(), strEncoded));
    }
 
    @Test
    public void test_hasHexPrefix() {
    	byte[] data = "0x746573746530".getBytes();
    	boolean hasHexPrefix = HexUtils.hasHexPrefix(data);
    	Assert.assertTrue(hasHexPrefix);
    }
    
    @Test
    public void test_removeHexPrefix() {
    	
    	byte[] data = "0x746573746530".getBytes();
    	
    	byte[] clean = HexUtils.removeHexPrefix(data);

    	Assert.assertEquals("746573746530", new String(clean));
    }
    
    
}
