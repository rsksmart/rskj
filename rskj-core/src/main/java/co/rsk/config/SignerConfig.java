package co.rsk.config;

import com.typesafe.config.Config;

public class SignerConfig {
    private String id;
    private String type;
    private Config config;

    public SignerConfig(String id, String type, Config config) {
        this.id = id;
        this.type = type;
        this.config = config.withoutPath("type");
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public Config getConfig() {
        return config;
    }
}
