package co.rsk.db;

import co.rsk.crypto.Keccak256;
import co.rsk.remasc.Sibling;

import java.util.List;
import java.util.Map;

public interface RemascCache {

    Map<Long, List<Sibling>> getSiblingsFromBlockByHash(Keccak256 hash);
}
