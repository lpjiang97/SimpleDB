package simpledb;

import javax.xml.soap.SAAJMetaFactory;
import java.util.*;
import java.util.concurrent.*;

/**
 * LockManager manages the locking/unlocking of simpleDB at page granularity. It is implemented as Singleton and should
 * only be used in BufferPool.
 *
 * @see BufferPool
 */
public class LockManager {

    private Map<TransactionId, Set<PageId>> pageMap;
    private Map<PageId, Lock> lockMap;

    // SINGLETON
    private static LockManager instance = new LockManager();

    /**
     * Construct a LockManager
     */
    private LockManager() {
        reset();
    }

    /**
     * Return a LockManager. Any class who needs a LockManager (though really should only be BufferPool) should get an
     * instance with this method <p>
     *
     * @return a LockManager
     */
    public static LockManager getInstance() {
        return instance;
    }

    /**
     * One transacatiion tries to acquire a lock on a page defined by pid. The type of the lock is determined by
     * Permission. If READ_ONLY, it will try to acquire a SHARED lock, otherwise (READ_WRITE), it will try to acquire
     * a EXCLUSIVE lock.
     * <p>
     *
     * There are a few conditions to make acquire a non-blocking call:
     *   - When the lock on that page is not locked
     *   - When the lock is locked by this transaction itself (will upgrade a share lock to exclusive if this
     *      transaction is the only one which holds the lock)
     *   - When the lock is locked, but it is a shared lock, and the transaction wants a shared lock.
     * <p>
     *
     * This method will block and sleep for 500 milliseconds on the thread which calls acquire if it cannot grab the
     * lock, after waking up, it will try to grab the lock again. If it still cannot grab the lock, it will throw a
     * TransactionAbortedException
     *
     * @param tid the TransactionId to acquire this lock
     * @param pid the PageId to acquire the lock
     * @param perm the permission on this lock
     * @throws TransactionAbortedException if the second try of grabbing the lock still fails
     */
    public synchronized void acquire(TransactionId tid, PageId pid, Permissions perm) throws TransactionAbortedException{
        // check if this transaction has a set of page ids yet
        this.pageMap.putIfAbsent(tid, new HashSet<>());
        // check if there is a lock for this page id yet
        this.lockMap.putIfAbsent(pid, new Lock((short)perm.permLevel));
        // get lock
        Lock l = this.lockMap.get(pid);
        boolean hasSlept = false;
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
            if (hasSlept) {
                this.releaseAll(tid);
                throw new TransactionAbortedException();
            }
            // Sleep for a while
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            hasSlept = true;
        }
        // get the lock
        l.setType((short)perm.permLevel);
        l.lock(tid);
        this.pageMap.get(tid).add(pid);
    }

    /**
     * Release the lock acquired by TransactionId tid and PageId pid.
     *
     * @param tid the TransactionId to release the lock
     * @param pid the PageId to release the lock
     * @throws IllegalArgumentException if tid or pid does not have a lock to release
     */
    public synchronized void release(TransactionId tid, PageId pid) {
        if (!holdsLock(tid, pid))
            throw new IllegalArgumentException("This tid or pid does not have a lock!");
        Lock l = this.lockMap.get(pid);
        l.unlock(tid);
        // remove this page from page set of tid
        this.pageMap.get(tid).remove(pid);
    }

    /**
     * Release all the lock acquired by TransactionId tid
     *
     * @param tid the TransactionId to release the lock
     */
    public synchronized void releaseAll(TransactionId tid) {
        Set<PageId> pids = this.pageMap.get(tid);
        if (pids != null) {
            pids = new HashSet<>(this.pageMap.get(tid));
            for (PageId pid : pids)
                this.release(tid, pid);
        }
    }

    /**
     * Checks if a TransactionId and PageId holds a lock.
     *
     * @param tid the TransactionId to check
     * @param pid the PageId to check
     * @return true if the tid and pid has a lock
     */
    public synchronized boolean holdsLock(TransactionId tid, PageId pid) {
        Lock l = null;
        if (this.pageMap.get(tid) == null || !this.pageMap.get(tid).contains(pid) || (l = this.lockMap.get(pid)) == null)
            return false;
        return l.hasTid(tid);
    }

    /**
     * Reset the LockManager to initial state
     */
    public synchronized void reset() {
        this.pageMap = new ConcurrentHashMap<>();
        this.lockMap = new ConcurrentHashMap<>();
    }
}


/**
 * Lock represents the SHARED/EXCLUSIVE locks needed by simpleDB. simpleDB should only interacts with Lock through
 * LockManager. <p>
 *
 * @see LockManager
 */
class Lock {

    // TYPES of Lock
    static final short SHARED = 0;
    static final short EXCLUSIVE = 1;

    private short type;
    // tids which hold this lock. Should be no more than one for EXCLUSIVE locks.
    private Set<TransactionId> tids;

    Lock (short type) {
        if (type != 0 && type != 1)
            throw new IllegalArgumentException("Type can only be 0 or 1");
        this.type = type;
        this.tids = new HashSet<>();
    }

    void lock(TransactionId tid) {
        this.tids.add(tid);
        assert (this.getType() == SHARED || this.tids.size() == 1);
    }

    void unlock(TransactionId tid) {
        this.tids.remove(tid);
        assert (this.getType() == SHARED || this.tids.size() == 0);
    }

    boolean isLocked() {
        return this.tids.size() > 0;
    }

    short getType() {
        return this.type;
    }

    void setType(short newtype) {
        assert (newtype == 0 || newtype == 1);
        this.type = newtype;
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