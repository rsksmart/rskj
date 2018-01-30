package org.ethereum.rpc;

public class HttpUtils {

    /** This function is strongly based on the function from netty 4.1, since
     *  we have an older version from it and do not need the overhead of checking
     *  the rest of the implementation for security reasons.
     *
     * @param contentTypeValue the value obtained from the header Content-Type
     * @return only the mime type, ignoring charset or other values after ;
     */
    public static String getMimeType(String contentTypeValue) {
        if (contentTypeValue == null) {
            return null;
        }

        int indexOfSemicolon = contentTypeValue.indexOf(';');
        if (indexOfSemicolon != -1) {
            return contentTypeValue.substring(0, indexOfSemicolon);
        } else {
            return contentTypeValue.length() > 0 ? contentTypeValue : null;
        }

    }
}
