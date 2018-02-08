package simpledb;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LockManager manages the locking/unlocking of simpleDB at page granularity. It is implemented as Singleton and should
 * only be used in BufferPool </p>
 *
 * @see simpledb.BufferPool
 */
public class LockManager {

    private Map<TransactionId, PageId> pageMap;
    private Map<PageId, Lock> lockMap;

    private static LockManager instance = new LockManager();

    private LockManager() {
        this.pageMap = new ConcurrentHashMap<>();
        this.lockMap = new ConcurrentHashMap<>();
    }

    public static LockManager getInstance() {
        return instance;
    }

    public synchronized void acquire() {

    }

}

class Lock {

    static final short SHARED = 0;
    static final short EXCLUSIVE = 1;

    short type;
    boolean held;

    Lock (short type) {
        if (type != 0 && type != 1)
            throw new IllegalArgumentException("Type can only be 0 or 1");
        this.type = type;
        this.held = false;
    }

    void lock() {
        this.held = true;
    }

    void unlock() {
        this.held = false;
    }

    short getType() {
        return this.type;
    }
}