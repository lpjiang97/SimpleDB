package simpledb;

import java.io.*;
import java.util.*;

/**
 * HeapFile is an implementation of a DbFile that stores a collection of tuples
 * in no particular order. Tuples are stored on pages, each of which is a fixed
 * size, and the file is simply a collection of those pages. HeapFile works
 * closely with HeapPage. The format of HeapPages is described in the HeapPage
 * constructor.
 * 
 * @see simpledb.HeapPage#HeapPage
 * @author Sam Madden
 */
public class HeapFile implements DbFile {

    private File f;
    private TupleDesc td;

    /**
     * Constructs a heap file backed by the specified file.
     * 
     * @param f
     *            the file that stores the on-disk backing store for this heap
     *            file.
     */
    public HeapFile(File f, TupleDesc td) {
        this.f = f;
        this.td = td;
    }

    /**
     * Returns the File backing this HeapFile on disk.
     * 
     * @return the File backing this HeapFile on disk.
     */
    public File getFile() {
        return this.f;
    }

    /**
     * Returns an ID uniquely identifying this HeapFile. Implementation note:
     * you will need to generate this tableid somewhere to ensure that each
     * HeapFile has a "unique id," and that you always return the same value for
     * a particular HeapFile. We suggest hashing the absolute file name of the
     * file underlying the heapfile, i.e. f.getAbsoluteFile().hashCode().
     * 
     * @return an ID uniquely identifying this HeapFile.
     */
    public int getId() {
        return this.f.getAbsoluteFile().hashCode();
    }

    /**
     * Returns the TupleDesc of the table stored in this DbFile.
     * 
     * @return TupleDesc of this DbFile.
     */
    public TupleDesc getTupleDesc() {
        return this.td;
    }

    // see DbFile.java for javadocs
    public Page readPage(PageId pid) {
        int pgNo = pid.getPageNumber();
        // check if page exists
        if (pgNo >= this.numPages())
            throw new IllegalArgumentException();
        int pageSize = BufferPool.getPageSize();
        // read
        try {
            RandomAccessFile raf = new RandomAccessFile(this.f, "r");
            // set offset
            raf.seek(pgNo * pageSize);
            // read
            byte[] data = new byte[pageSize];
            raf.read(data);
            raf.close();
            return new HeapPage((HeapPageId)pid, data);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    // see DbFile.java for javadocs
    public void writePage(Page page) throws IOException {
        // some code goes here
        // not necessary for lab1
    }

    /**
     * Returns the number of pages in this HeapFile.
     */
    public int numPages() {
        return (int) (this.f.length() / BufferPool.getPageSize());
    }

    // see DbFile.java for javadocs
    public ArrayList<Page> insertTuple(TransactionId tid, Tuple t)
            throws DbException, IOException, TransactionAbortedException {
        // some code goes here
        return null;
        // not necessary for lab1
    }

    // see DbFile.java for javadocs
    public ArrayList<Page> deleteTuple(TransactionId tid, Tuple t) throws DbException,
            TransactionAbortedException {
        // some code goes here
        return null;
        // not necessary for lab1
    }

    // see DbFile.java for javadocs
    public DbFileIterator iterator(TransactionId tid) {
        return new DbFileIt(tid);
    }

    private class DbFileIt implements DbFileIterator {

        private int pageNo;
        private HeapPage p;
        private TransactionId tid;
        private Iterator<Tuple> tupleIt;

        private DbFileIt(TransactionId tid) {
            this.tid = tid;
            this.p = null;
            this.tupleIt = null;
        }

        @Override
        public void open() throws DbException, TransactionAbortedException {
            reset();
        }

        @Override
        public boolean hasNext() throws DbException, TransactionAbortedException {
            // check nulls
            if (closed())
                return false;
            // check if current page has next
            if (this.tupleIt.hasNext())
                return true;
            // if there is no next page
            if (!this.tupleIt.hasNext() && this.pageNo + 1 >= numPages())
                return false;
            // switch to next page
            this.p = (HeapPage) Database.getBufferPool().getPage(this.tid, new HeapPageId(getId(), ++this.pageNo), Permissions.READ_ONLY);
            this.tupleIt = this.p.iterator();
            return true;
        }

        @Override
        public Tuple next() throws DbException, TransactionAbortedException, NoSuchElementException {
            if (closed())
                throw new NoSuchElementException();
            return this.tupleIt.next();
        }

        @Override
        public void rewind() throws DbException, TransactionAbortedException {
            reset();
        }

        @Override
        public void close() {
            this.p = null;
            this.tupleIt = null;
        }

        private void reset() throws TransactionAbortedException, DbException {
            this.pageNo = 0;
            // read only for now, might need to change
            this.p = (HeapPage) Database.getBufferPool().getPage(this.tid, new HeapPageId(getId(), this.pageNo), Permissions.READ_ONLY);
            this.tupleIt = this.p.iterator();
        }

        private boolean closed() {
            return p == null || tupleIt == null;
        }
    }
}

