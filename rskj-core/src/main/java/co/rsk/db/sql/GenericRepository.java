package co.rsk.db.sql;

import java.io.Closeable;
import java.sql.SQLException;

public interface GenericRepository<E> extends Closeable {
    void insert(E e) throws SQLException;
}
