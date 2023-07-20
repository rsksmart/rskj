package org.ethereum.rpc.validation;

import co.rsk.util.HexUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BnTagOrNumberValidator {
    private static final String HEX_BN_OR_ID_REGEX = "^(?:0x[0-9a-fA-F]+$|earliest$|finalized$|safe$|latest$|pending$)";
    private static final Pattern HEX_BN_OR_ID_PATTERN = Pattern.compile(HEX_BN_OR_ID_REGEX);
    private BnTagOrNumberValidator() { }

    public static boolean isValid(String parameter) {
        Matcher matcher = HEX_BN_OR_ID_PATTERN.matcher(parameter);
        HexUtils.isHex(parameter);
        return matcher.matches();
    }

}
