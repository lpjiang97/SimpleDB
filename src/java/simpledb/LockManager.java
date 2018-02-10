package simpledb;

import java.util.*;
import java.util.concurrent.*;

/**
 * LockManager manages the locking/unlocking of simpleDB at page granularity. It is implemented as Singleton and should
 * only be used in BufferPool.
 *
 * @see simpledb.BufferPool
 */
public class LockManager {

    private Map<TransactionId, Set<PageId>> pageMap;
    private Map<PageId, Lock> lockMap;

    private static LockManager instance = new LockManager();

    private LockManager() {
        this.pageMap = new ConcurrentHashMap<>();
        this.lockMap = new ConcurrentHashMap<>();
    }

    public static LockManager getInstance() {
        return instance;
    }

    /**
     * One transacatiion tries to acquire a lock on a page defined by pid. The type of the lock is determined by
     * Permission. If READ_ONLY, it will try to acquire a SHARED lock, otherwise (READ_WRITE), it will try to acquire
     * a EXCLUSIVE lock.
     * <p>
     *
     * @param tid
     * @param pid
     * @param perm
     * @throws TransactionAbortedException if it has been block for over
     */
    public synchronized void acquire(TransactionId tid, PageId pid, Permissions perm)
            throws TransactionAbortedException {
        // check if this transaction has a set of page ids yet
        this.pageMap.putIfAbsent(tid, new HashSet<>());
        // check if there is a lock for this page id yet
        this.lockMap.putIfAbsent(pid, new Lock((short)perm.permLevel));
        // get lock
        Lock l = this.lockMap.get(pid);
        while (l.isLocked()) {
            // if this lock is obtained by tid already
            if(l.hasTid(tid)) {
                // can I upgrade?
                if (l.getRefCount() == 1 && perm.equals(Permissions.READ_WRITE) && l.getType() == Lock.SHARED) {
                    l.upgrade();
                    return;
                }
                // we want shared OR we want exclusive and we have it
                if ((perm.permLevel == Lock.SHARED) || (perm.equals(Permissions.READ_WRITE) && l.getType() == Lock.EXCLUSIVE))
                    return;
            } else { // new tid is trying to get lock which is already obtained
                // if it's shared lock, it's okay
                if (perm.equals(Permissions.READ_ONLY) && l.getType() == Lock.SHARED) {
                    l.lock(tid);
                    this.pageMap.get(tid).add(pid);
                    return;
                }
            }
            // block here
            try {
                this.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        // get the lock
        l.lock(tid);
        this.pageMap.get(tid).add(pid);
    }

    public synchronized void release(TransactionId tid, PageId pid) {
        Lock l = this.lockMap.get(pid);
        l.unlock(tid);
        // remove this page from page set of tid
        this.pageMap.get(tid).remove(pid);
        this.notifyAll();
    }

    public boolean holdsLock(TransactionId tid, PageId pid) {
        Lock l = null;
        if (this.pageMap.get(tid) == null || !this.pageMap.get(tid).contains(pid) || (l = this.lockMap.get(pid)) == null)
            return false;
        return l.hasTid(tid);
    }
}

class Lock {

    static final short SHARED = 0;
    static final short EXCLUSIVE = 1;

    private short type;
    private Set<TransactionId> tids;

    Lock (short type) {
        if (type != 0 && type != 1)
            throw new IllegalArgumentException("Type can only be 0 or 1");
        this.type = type;
        this.tids = new HashSet<>();
    }

    void lock(TransactionId tid) {
        this.tids.add(tid);
    }

    void unlock(TransactionId tid) {
        this.tids.remove(tid);
    }

    boolean isLocked() {
        return this.tids.size() > 0;
    }

    short getType() {
        return this.type;
    }

    void upgrade() {
        this.type = 1;
    }

    boolean hasTid(TransactionId tid) {
        return this.tids.contains(tid);
    }

    int getRefCount() {
        return this.tids.size();
    }
}