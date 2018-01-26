package org.ethereum.rpc;

public class HttpUtils {

    public static String getMimeType(String contentTypeValue) {
        if (contentTypeValue == null) {
            throw new NullPointerException("contentTypeValue");
        }

        int indexOfSemicolon = contentTypeValue.indexOf(';');
        if (indexOfSemicolon != -1) {
            return contentTypeValue.substring(0, indexOfSemicolon);
        } else {
            return contentTypeValue.length() > 0 ? contentTypeValue : null;
        }

    }
}
