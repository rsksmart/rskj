package co.rsk.signing;

import org.apache.commons.lang3.StringUtils;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ajlopez on 29/12/2016.
 */
public class KeyFileChecker {
    public static final int KEY_LENGTH = 32;

    private String filePath;

    public KeyFileChecker(String filePath) {
        this.filePath = filePath;
    }

    public List<String> check() {
        List<String> messages = new ArrayList<>();

        String messageFileName = this.checkKeyFile();
        if (StringUtils.isNotEmpty(messageFileName)) {
            messages.add(messageFileName);
        }

        return messages;
    }

    public String checkKeyFile() {
        if (StringUtils.isBlank(this.filePath)) {
            return "Invalid Federate Key File Name";
        }

        if (!Paths.get(this.filePath).toFile().exists()) {
            return "Federate Key File '" + this.filePath + "' does not exist";
        }
        try {
            byte[] var;
            KeyFileHandler keyHandler = new KeyFileHandler(this.filePath);
            var = keyHandler.privateKey();
            boolean sizeOk = this.validateKeyLength(var);
            var = null;
            if (!sizeOk) {
                return "Invalid Key Size";
            }
        } catch (Exception ex) {
            return "Error Reading Federate Key File '" + this.filePath + "'";
        }
        return "";
    }

    private boolean validateKeyLength(byte[] var) {
        return !(var == null || var.length != KEY_LENGTH);
    }
}
