package org.ethereum.rpc;

import org.ethereum.core.Block;

import static org.ethereum.rpc.TypeConverter.toJsonHex;

/**
 * Created by ajlopez on 17/01/2018.
 */

public class NewBlockFilter extends Filter {
    class NewBlockFilterEvent extends FilterEvent {
        public final Block b;

        NewBlockFilterEvent(Block b) {
            this.b = b;
        }

        @Override
        public String getJsonEventObject() {
            return toJsonHex(b.getHash());
        }
    }

    @Override
    public void newBlockReceived(Block b) {
        add(new NewBlockFilterEvent(b));
    }
}

