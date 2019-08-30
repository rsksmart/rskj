package co.rsk.net.syncrefactor;

public class LongSync {

    private final SyncStateFactory factory;
    private boolean stopped;

    public LongSync(SyncStateFactory factory) {

        this.stopped = true;
        this.factory = factory;
    }

    public void start() {
        this.stopped = false;
        Thread run = new Thread(this::run);
        run.start();
    }

    public void stop() {
        this.stopped = true;
    }

    public void run() {
        SyncState syncState = factory.newDecidingSyncState();
        while (!stopped) {
            syncState = syncState.execute();
        }
    }
}
