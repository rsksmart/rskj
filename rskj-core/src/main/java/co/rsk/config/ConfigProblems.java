package co.rsk.config;

import com.typesafe.config.ConfigValue;

final class ConfigProblems {

    private ConfigProblems() { /* hidden */ }

    public static String unexpectedKeyProblem(String keyPath, ConfigValue actualValue) {
        return "Unexpected config value " + actualValue + " for key path `" + keyPath + "`. See expected.conf for expected setting";
    }

    public static String expectedScalarValueProblem(String keyPath, ConfigValue actualValue) {
        return "Expected scalar config value for key path `"+ keyPath + "`. Actual value is " + actualValue + ". See expected.conf for expected setting";
    }

    public static String typeMismatchProblem(String keyPath, ConfigValue expectedValue, ConfigValue actualValue) {
        return "Config value type mismatch. `" + keyPath + "` has type " + actualValue.valueType()
                + ", but should have " + expectedValue.valueType() + ". See expected.conf for expected setting";
    }
}
