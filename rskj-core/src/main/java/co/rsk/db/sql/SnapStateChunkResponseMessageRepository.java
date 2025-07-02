package co.rsk.db.sql;

import co.rsk.net.messages.SnapStateChunkResponseMessage;

import java.sql.SQLException;
import java.util.List;

public interface SnapStateChunkResponseMessageRepository extends GenericRepository<SnapStateChunkResponseMessage> {
    Pagination<List<SnapStateChunkResponseMessage>> findAllPaginatedAndUnprocessedByFrom(int page, int pageSize, long from) throws SQLException;
    void updateToProcessed(List<Long> ids) throws SQLException;
}
