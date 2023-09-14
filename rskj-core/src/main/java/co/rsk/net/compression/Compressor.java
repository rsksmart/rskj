package co.rsk.net.compression;

import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4SafeDecompressor;
import org.ethereum.util.ByteUtil;

import java.util.Arrays;

public class Compressor {


    public static byte[] decompressLz4(byte[] src, int expandedSize) {
        LZ4SafeDecompressor decompressor = LZ4Factory.safeInstance().safeDecompressor();
        byte[] dst = new byte[expandedSize];
        decompressor.decompress(src, dst);
        return  dst;
    }

    public static byte[] compressLz4(byte[] src) {
        LZ4Factory lz4Factory = LZ4Factory.safeInstance();
        LZ4Compressor fastCompressor = lz4Factory.fastCompressor();
        int maxCompressedLength = fastCompressor.maxCompressedLength(src.length);
        byte[] dst = new byte[maxCompressedLength];
        int compressedLength = fastCompressor.compress(src, 0, src.length, dst, 0, maxCompressedLength);
        return Arrays.copyOf(dst, compressedLength);
    }

    public static boolean validateCompression(byte[] value, byte[] compressedValue) {
        return Arrays.equals(Compressor.decompressLz4(compressedValue, value.length), value);
    }
}
