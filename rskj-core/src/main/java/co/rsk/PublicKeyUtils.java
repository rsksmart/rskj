package co.rsk;

import co.rsk.bitcoinj.core.Base58;
import org.bouncycastle.util.encoders.Hex;

import java.util.Arrays;

/**
 * This class contains methods for converting an Extended Public Key To The Compressed Format
 *
 * @author Kelvin Isievwore
 */
public class PublicKeyUtils {

    public static String convertXpubToCompressedPublicKey(String xpub) {

        // Decode from Base58Check
        byte[] decodedXpub = decodeFromBase58Check(xpub);

        // Grab last 33 bytes
        byte[] last33Bytes = Arrays.copyOfRange(decodedXpub, decodedXpub.length - 33, decodedXpub.length);

        return toHexString(last33Bytes);
    }

    private static byte[] decodeFromBase58Check(String input) {
        return Base58.decodeChecked(input);
    }

    private static String toHexString(byte[] input) {
        return Hex.toHexString(input);
    }

    public static void main(String[] args) {
        String xPub = "xpub661MyMwAqRbcG8Zah6TcX3QpP5yJApaXcyLK8CJcZkuYjczivsHxVL5qm9cw8BYLYehgFeddK5WrxhntpcvqJKTVg96dUVL9P7hZ7Kcvqvd";
        System.out.println("ExtendedFormat: " + xPub);
        System.out.println("CompressedFormat: " + convertXpubToCompressedPublicKey(xPub));
    }

}
