package co.rsk.scoring;

/**
 * Created by ajlopez on 14/07/2017.
 */
public class InetAddressBlockParser {
    public boolean hasMask(String text) {
        if (text == null)
            return false;

        String[] parts = text.split("/");

        if (parts.length != 2 || parts[0].length() == 0 || parts[1].length() == 0)
            return false;

        return true;
    }

    public InetAddressBlock parse(String text) {
        String[] parts = text.split("/");

        return null;
    }
}
