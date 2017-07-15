package co.rsk.scoring;

/**
 * Created by ajlopez on 15/07/2017.
 */
public class InetAddressUtils {
    public static boolean hasMask(String text) {
        if (text == null)
            return false;

        String[] parts = text.split("/");

        if (parts.length != 2 || parts[0].length() == 0 || parts[1].length() == 0)
            return false;

        return true;
    }

    private InetAddressUtils() {}
}
