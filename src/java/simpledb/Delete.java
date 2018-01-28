package simpledb;

import java.io.IOException;

/**
 * The delete operator. Delete reads tuples from its child operator and removes
 * them from the table they belong to.
 */
public class Delete extends Operator {

    private static final long serialVersionUID = 1L;

    private TransactionId tid;
    private OpIterator child;
    private final TupleDesc td;
    private int counter;
    private boolean called;

    /**
     * Constructor specifying the transaction that this delete belongs to as
     * well as the child to read from.
     * 
     * @param t
     *            The transaction this delete runs in
     * @param child
     *            The child operator from which to read tuples for deletion
     */
    public Delete(TransactionId t, OpIterator child) {
        this.tid = t;
        this.child = child;
        this.td = new TupleDesc(new Type[] {Type.INT_TYPE}, new String[] {"number of deleted tuples"});
        this.counter = -1;
        this.called = false;
    }

    public TupleDesc getTupleDesc() {
        return this.td;
    }

    public void open() throws DbException, TransactionAbortedException {
        this.child.open();
        super.open();
        this.counter = 0;
    }

    public void close() {
        super.close();
        this.child.close();
        this.counter = -1;
    }

    public void rewind() throws DbException, TransactionAbortedException {
        this.child.rewind();
        this.counter = 0;
    }

    /**
     * Deletes tuples as they are read from the child operator. Deletes are
     * processed via the buffer pool (which can be accessed via the
     * Database.getBufferPool() method.
     * 
     * @return A 1-field tuple containing the number of deleted records.
     * @see Database#getBufferPool
     * @see BufferPool#deleteTuple
     */
    protected Tuple fetchNext() throws TransactionAbortedException, DbException {
        if (this.called)
            return null;

        this.called = true;
        while (this.child.hasNext()) {
            Tuple t = this.child.next();
            try {
                Database.getBufferPool().deleteTuple(this.tid, t);
                this.counter++;
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
        }
        Tuple tu = new Tuple(this.td);
        tu.setField(0, new IntField(this.counter));
        return tu;
    }

    @Override
    public OpIterator[] getChildren() {
        return new OpIterator[] {this.child};
    }

    @Override
    public void setChildren(OpIterator[] children) {
        this.child = children[0];
    }

}
