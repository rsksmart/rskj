package co.rsk.signing;

import co.rsk.config.SignerConfig;

public class ECDSASignerFactory {
    public ECDSASigner buildFromConfig(SignerConfig config) {
        String type = config.getType();
        switch (type) {
            case "keyFile":
                return new ECDSASignerFromDiskKey(
                        new KeyId(config.getId()),
                        config.getConfig().getString("path")
                );
            default:
                throw new RuntimeException(String.format("Unsupported signer type: %s", type));
        }
    }
}
