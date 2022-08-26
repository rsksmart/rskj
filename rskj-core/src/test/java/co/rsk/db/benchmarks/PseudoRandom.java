package co.rsk.db.benchmarks;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import org.ethereum.vm.DataWord;

import java.math.BigInteger;

public class PseudoRandom {

    private long seed;
    private static final long multiplier = 25214903917L;
    private static final long addend = 11L;
    private static final long mask = 281474976710655L;

    public PseudoRandom() {
        this(0);
    }


    public PseudoRandom(long seed) {
            this.setSeed(seed);
    }

    private static long initialScramble(long seed) {
        return (seed ^ 25214903917L) & 281474976710655L;
    }

    public synchronized void setSeed(long aseed) {
        this.seed = initialScramble(aseed);
        scrambleSeed(); // scramble once more
    }
    protected void scrambleSeed() {
        long oldseed;
        long nextseed;

        oldseed = seed;
        nextseed = (oldseed * 25214903917L + 11L) & 281474976710655L;
        seed = nextseed;
    }

    protected int next(int bits) {
        scrambleSeed();
        return (int)(seed >>> 48 - bits);
    }

    public void nextBytes(byte[] bytes) {
        int i = 0;
        int len = bytes.length;

        while(i < len) {
            int rnd = this.nextInt();

            for(int var5 = Math.min(len - i, 4); var5-- > 0; rnd >>= 8) {
                bytes[i++] = (byte)rnd;
            }
        }

    }
    public double nextDouble() {
        return (double)(((long)this.next(26) << 27) + (long)this.next(27)) * 1.1102230246251565E-16D;
    }

    final long internalNextLong(long origin, long bound) {
        long r = this.nextLong();
        if (origin < bound) {
            long n = bound - origin;
            long m = n - 1L;
            if ((n & m) == 0L) {
                r = (r & m) + origin;
            } else if (n > 0L) {
                for(long u = r >>> 1; u + m - (r = u % n) < 0L; u = this.nextLong() >>> 1) {
                }

                r += origin;
            } else {
                while(r < origin || r >= bound) {
                    r = this.nextLong();
                }
            }
        }

        return r;
    }

    final int internalNextInt(int origin, int bound) {
        if (origin >= bound) {
            return this.nextInt();
        } else {
            int n = bound - origin;
            if (n > 0) {
                return this.nextInt(n) + origin;
            } else {
                int r;
                do {
                    do {
                        r = this.nextInt();
                    } while(r < origin);
                } while(r >= bound);

                return r;
            }
        }
    }


    public int nextInt() {
        return this.next(32);
    }

    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        } else {
            int r = this.next(31);
            int m = bound - 1;
            if ((bound & m) == 0) {
                r = (int)((long)bound * (long)r >> 31);
            } else {
                for(int u = r; u - (r = u % bound) + m < 0; u = this.next(31)) {
                }
            }

            return r;
        }
    }

    public long nextLong() {
        return ((long)this.next(32) << 32) + (long)this.next(32);
    }
    // note: nextLong(bound) has bias towards zero
    public long nextLong(long bound) {
        long positiveLong = ((long)this.next(31) << 32) + (long)this.next(32);
        return positiveLong % bound;
    }

    public boolean nextBoolean() {
        return this.next(1) != 0;
    }


    public  void  fillRandomBytes(byte[] buf, int ofs,int length) {
        byte[] result = new byte[length];
        nextBytes(result);
        System.arraycopy(result,0,buf,ofs,length);
    }

    // Must follows CompactTrieKeySlice convention
    // First bit is most significant
    static public int getCompactBitMask(int bitofs) {
        return (0x80>>((bitofs) & 0x7));
    }

    static public int getCompactByte(int bitofs) {
        return bitofs>>3;
    }

    static public boolean getCompactBit(byte[] buf, int bitofs) {
        int destofs = getCompactByte(bitofs);
        int destmask = getCompactBitMask(bitofs);
        return (buf[destofs] & destmask)!=0;
    }

    static public void setCompactBit(byte[] result, int bitofs,boolean set) {
         int destofs = getCompactByte(bitofs);
         int destmask = getCompactBitMask(bitofs);
         if (set) {
             result[destofs] |= destmask;
         } else {
             result[destofs] &=~destmask;
         }
    }
    private byte[] randomBits(int numBits,boolean leadingZeros,boolean fromHigherBits) {
        if (numBits < 0) {
            throw new IllegalArgumentException("numBits must be non-negative");
        } else {
            int numBytes = (int)(((long)numBits + 7L) / 8L);
            byte[] randomBits = new byte[numBytes];
            if (numBytes > 0) {
                nextBytes(randomBits);
                int excessBits = 8 * numBytes - numBits;
                if (excessBits>0) {
                    int zeroByteIndex;
                    if (leadingZeros)
                        zeroByteIndex = 0;
                    else
                        zeroByteIndex = numBytes - 1;

                    int mask;
                    // Remove Most significant bits
                    if (!fromHigherBits)
                        mask = (1 << excessBits) - 1;
                    else
                        mask = (1 << 8 - excessBits) - 1;
                    randomBits[zeroByteIndex] = (byte) (randomBits[0] & mask);
                }
            }

            return randomBits;
        }
    }

    public  static void  moveBits(byte srcByte, int baseSrcOfs, boolean fromTop,byte[] result, int baseDstOfs,int bitlength) {
        for (int i = 0; i < bitlength; i++) {
            int srcmask ;
            if (fromTop)
                srcmask = (0x80 >> ((baseSrcOfs + i) & 0x7));
            else
                srcmask = (1 << ((baseSrcOfs + i) & 0x7));
            int destofs = (baseDstOfs + i) >> 3;
            int destmask = (0x80 >> ((baseDstOfs + i) & 0x7));
            if ((srcByte & srcmask) != 0) {
                result[destofs] |= destmask;
            } else {
                result[destofs] &= ~destmask;
            }
        }
    }
    // first bit is always MSB
    public  static void  moveBits(byte[] buf, int baseSrcOfs, byte[] result, int baseDstOfs,int bitlength) {
        for (int i = 0; i < bitlength; i++) {
            int srcofs = (baseSrcOfs + i) >> 3;
            int srcmask = (0x80 >> ((baseSrcOfs + i) & 0x7));
            int destofs = (baseDstOfs + i) >> 3;
            int destmask = (0x80 >> ((baseDstOfs + i) & 0x7));
            if ((buf[srcofs] & srcmask) != 0) {
                result[destofs] |= destmask;
            } else {
                result[destofs] &= ~destmask;
            }
        }
    }

    public  void  fillRandomBits(byte[] result, int bitofs,int bitlength) {
        int length = (bitlength+7)/8;
        byte[] tmpBuf = new byte[length];
        nextBytes(tmpBuf);
        moveBits(tmpBuf,0,result,bitofs,bitlength);
    }

    public  byte[] randomBytes(int length) {
        byte[] result = new byte[length];
        nextBytes(result);
        return result;
    }

    // Check this method!!!!
    public  BigInteger randomBigInteger(int maxSizeBytes) {
        return new BigInteger(1,randomBits(maxSizeBytes*8,true,true));
    }

    public Coin randomCoin(int decimalZeros, int maxValue) {
        return new Coin(BigInteger.TEN.pow(decimalZeros).multiply(
                BigInteger.valueOf(nextInt(maxValue))));
    }

    public  DataWord randomDataWord() {
        return DataWord.valueOf(randomBytes(32));
    }

    public  RskAddress randomAddress() {
        return new RskAddress(randomBytes(20));
    }

    public  Keccak256 randomHash() {
        return new Keccak256(randomBytes(32));
    }
}
