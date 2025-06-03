package co.rsk.db.sql.h2.impl;

import co.rsk.db.sql.GenericRepository;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

public abstract class GenericRepositoryImpl<E> implements GenericRepository<E> {
    protected Connection connection;

    public GenericRepositoryImpl(Connection connection) throws SQLException {
        this.connection = connection;
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
