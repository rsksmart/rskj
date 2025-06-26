package co.rsk.db.sql.h2.impl;

import co.rsk.db.sql.Pagination;
import co.rsk.db.sql.SnapStateChunkResponseMessageRepository;
import co.rsk.net.messages.SnapStateChunkResponseMessage;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class SnapStateChunkResponseMessageRepositoryImpl extends GenericRepositoryImpl<SnapStateChunkResponseMessage> implements SnapStateChunkResponseMessageRepository {
    private static final String FIND_ALL_PAGINATED_SELECT_CLAUSE = """
            SELECT sscrm.id, sscrm.to, sscrm.from, sscrm.complete, sscrm.block_number, sscrm.chunk_of_trie_key_value
            """;
    private static final String FIND_ALL_PAGINATED_SELECT_COUNT = """
            SELECT count(*) AS total_count
            """;
    private static final String FIND_ALL_PAGINATED_STATEMENT = """
              FROM snap_state_chunk_response_messages AS sscrm
             WHERE sscrm.from = ? AND sscrm.processed = 0
             ORDER BY sscrm.from ASC
             LIMIT ?
            OFFSET ?
            """;
    private static final String DATA_FIND_ALL_PAGINATED_STATEMENT = FIND_ALL_PAGINATED_SELECT_CLAUSE + FIND_ALL_PAGINATED_STATEMENT;
    private static final String COUNT_FIND_ALL_PAGINATED_STATEMENT = FIND_ALL_PAGINATED_SELECT_COUNT + FIND_ALL_PAGINATED_STATEMENT;
    private static final String INSERT_STATEMENT = """
            INSERT INTO snap_state_chunk_response_messages
                (id, to, from, complete, block_number, chunk_of_trie_key_value)
            VALUES
                (?, ?, ?, ?, ?)
            """;
    private static final String UPDATE_TO_PROCESSED_STATEMENT = """
            UPDATE snap_state_chunk_response_messages
               SET processed = 1
             WHERE id IN (?)
            """;

    public SnapStateChunkResponseMessageRepositoryImpl(Connection connection) throws SQLException {
        super(connection);
    }

    @Override
    public Pagination<List<SnapStateChunkResponseMessage>> findAllPaginatedAndUnprocessedByFrom(int page, int pageSize, long from) throws SQLException {
        final var dataStmt = connection.prepareStatement(DATA_FIND_ALL_PAGINATED_STATEMENT);
        final var offset = pageSize * (page - 1)  ;

        dataStmt.setLong(0, from);
        dataStmt.setInt(1, pageSize);
        dataStmt.setInt(2, offset);

        final var msgs = new ArrayList<SnapStateChunkResponseMessage>();

        try (final var rs = dataStmt.executeQuery()) {
            while (rs.next()) {
                final var msg = new SnapStateChunkResponseMessage(
                        rs.getLong("id"),
                        rs.getBytes("chunk_of_trie_key_value"),
                        rs.getLong("block_number"),
                        rs.getLong("from"),
                        rs.getLong("to"),
                        rs.getBoolean("complete")
                );

                msgs.add(msg);
            }
        }

        final var totalStmt = connection.prepareStatement(COUNT_FIND_ALL_PAGINATED_STATEMENT);
        var totalCount = 0L;

        try (final var rs = totalStmt.executeQuery()) {
            while (rs.next()) {
                totalCount = rs.getLong("total_count");
            }
        }

        return new Pagination<>(msgs, totalCount, pageSize, page);
    }

    @Override
    public void insert(SnapStateChunkResponseMessage snapStateChunkResponseMessage) throws SQLException {
        final var stmt = connection.prepareStatement(INSERT_STATEMENT);

        stmt.setLong(0, snapStateChunkResponseMessage.getId());
        stmt.setLong(1, snapStateChunkResponseMessage.getTo());
        stmt.setLong(2, snapStateChunkResponseMessage.getFrom());
        stmt.setBoolean(3, snapStateChunkResponseMessage.isComplete());
        stmt.setLong(4, snapStateChunkResponseMessage.getBlockNumber());
        stmt.setBytes(5, snapStateChunkResponseMessage.getChunkOfTrieKeyValue());

        stmt.executeUpdate();
    }

    @Override
    public void updateToProcessed(List<Long> ids) throws SQLException {
        final var stmt = connection.prepareStatement(UPDATE_TO_PROCESSED_STATEMENT);

        stmt.setArray(0, connection.createArrayOf("BIGINT", ids.toArray()));

        stmt.executeUpdate();
    }
}
