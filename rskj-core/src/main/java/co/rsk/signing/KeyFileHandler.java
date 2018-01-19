package co.rsk.signing;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

/**
 * Created by mario on 30/08/2016.
 */
public class KeyFileHandler {
    private static final Logger logger = LoggerFactory.getLogger("KeyFileHandler");

    private final String filePath;

    public KeyFileHandler(String filePath) {
        this.filePath = filePath;
    }

    public byte[] privateKey() throws FileNotFoundException {
        if (StringUtils.isNotBlank(filePath) && Paths.get(filePath).toFile().exists()) {
            try (FileReader fr = new FileReader(filePath); BufferedReader br = new BufferedReader(fr)) {
                return Hex.decode(StringUtils.trim(br.readLine()).getBytes(StandardCharsets.UTF_8));
            } catch (Exception ex) {
                logger.error("Error while reading key file", ex);
                throw new RuntimeException("Error while reading key file", ex);
            }
        } else {
            logger.error("Key file not found: ", filePath);
            throw new FileNotFoundException(String.format("Error accessing key file %s", filePath));
        }
    }
}
