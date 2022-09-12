package co.rsk.cli.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.io.StringReader;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;

class MigratorTest {

    @Test
    void migrateConfiguration() {
        Reader initialConfiguration = new StringReader("inline.config.name=\"value\"\n" +
                "nested {\n" +
                "  nested = {\n" +
                "    #test comment\n" +
                "    config = 13\n" +
                "  }\n" +
                "}\n" +
                "flat.config = \"0.0.0.0\"\n" +
                "another.config=\"don't change\"");
        Properties migrationProperties = new Properties();
        migrationProperties.put("inline.config.name", "inline.config.new.name");
        migrationProperties.put("flat.config", "flat_config");
        migrationProperties.put("nested.nested.config", "nested.nested.new.config");
        migrationProperties.put("unknown.config", "none");
        migrationProperties.put("[new]new.key", "new value");
        migrationProperties.put("[new] other.new.key", "12");

        String migratedConfiguration = Migrator.migrateConfiguration(initialConfiguration, migrationProperties);
        Config config = ConfigFactory.parseString(migratedConfiguration);
        MatcherAssert.assertThat(config.hasPath("inline.config.name"), is(false));
        MatcherAssert.assertThat(config.getString("inline.config.new.name"), is("value"));
        MatcherAssert.assertThat(config.hasPath("flat.config"), is(false));
        MatcherAssert.assertThat(config.hasPath("flat_config"), is(true));
        MatcherAssert.assertThat(config.getString("flat_config"), is("0.0.0.0"));
        MatcherAssert.assertThat(config.hasPath("nested.nested.config"), is(false));
        MatcherAssert.assertThat(config.getInt("nested.nested.new.config"), is(13));
        MatcherAssert.assertThat(config.hasPath("unknown.config"), is(false));
        MatcherAssert.assertThat(config.getString("another.config"), is("don't change"));
        MatcherAssert.assertThat(config.getString("new.key"), is("new value"));
        MatcherAssert.assertThat(config.getInt("other.new.key"), is(12));
    }
}
