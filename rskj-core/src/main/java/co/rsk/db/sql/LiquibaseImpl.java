package co.rsk.db.sql;

import liquibase.Scope;
import liquibase.command.CommandScope;
import liquibase.command.core.UpdateCommandStep;
import liquibase.database.core.H2Database;
import liquibase.database.jvm.JdbcConnection;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

public class LiquibaseImpl implements Closeable {
    protected Connection connection;

    public LiquibaseImpl(Connection connection) throws SQLException {
        this.connection = connection;
    }

    public void runMigrations(String context) throws Exception {
        final var database = new H2Database();
        database.setConnection(new JdbcConnection(connection));

        Scope.child(Scope.Attr.database.name(), database, () -> {
            new CommandScope(UpdateCommandStep.COMMAND_NAME)
                    .addArgumentValue("changeLogFile", "db/changelog/master-changelog.xml")
                    .addArgumentValue("database", database)
                    .addArgumentValue("contexts", context) // optional
                    .execute();
        });
    }

    @Override
    public void close() throws IOException {
        try {
            this.connection.close();
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }
}
