/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

package co.rsk.core;

import org.ethereum.core.Denomination;

import javax.annotation.Nonnull;
import java.math.BigInteger;

/**
 * RSK's native coin.
 * One coin is convertible to (10^10)^(-1) satoshis.
 * It is comparable to 1 wei in the Ethereum network.
 */
public class Coin implements Comparable<Coin> {
    public static final Coin ZERO = new Coin(BigInteger.ZERO);

    private final BigInteger value;

    public Coin(byte[] value) {
        this(new BigInteger(1, value));
    }

    public Coin(BigInteger value) {
        this.value = value;
    }

    public byte[] getBytes() {
        return value.toByteArray();
    }

    public BigInteger asBigInteger() {
        return value;
    }

    public Coin negate() {
        return new Coin(value.negate());
    }

    public Coin add(Coin val) {
        return new Coin(value.add(val.value));
    }

    public Coin subtract(Coin val) {
        return new Coin(value.subtract(val.value));
    }

    public Coin multiply(BigInteger val) {
        return new Coin(value.multiply(val));
    }

    public Coin divide(BigInteger val) {
        return new Coin(value.divide(val));
    }

    public Coin[] divideAndRemainder(BigInteger val) {
        BigInteger[] raw = value.divideAndRemainder(val);
        return new Coin[]{new Coin(raw[0]), new Coin(raw[1])};
    }

    /**
     * @return the value denominated in Bitcoin, according to the 2-way peg convertibility rules.
     */
    public co.rsk.bitcoinj.core.Coin toBitcoin() {
        return co.rsk.bitcoinj.core.Coin.valueOf(Denomination.weisToSatoshis(value).longValue());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || this.getClass() != other.getClass()) {
            return false;
        }

        Coin otherCoin = (Coin) other;
        return value.equals(otherCoin.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public int compareTo(@Nonnull Coin other) {
        return value.compareTo(other.value);
    }

    /**
     * @return a DEBUG representation of the value, mainly used for logging.
     */
    @Override
    public String toString() {
        return value.toString();
    }

    public static Coin valueOf(long val) {
        return new Coin(BigInteger.valueOf(val));
    }

    public static Coin fromBitcoin(co.rsk.bitcoinj.core.Coin val) {
        return new Coin(Denomination.satoshisToWeis(val.getValue()));
    }
}
