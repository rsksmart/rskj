package co.rsk.db;

import java.math.BigInteger;

/**
 * TODO get rid of this duplicate
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
    public int compareTo(Coin other) {
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
}
