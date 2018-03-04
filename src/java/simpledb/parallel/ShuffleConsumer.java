package simpledb.parallel;

import java.util.Iterator;
import simpledb.DbException;
import simpledb.OpIterator;
import simpledb.TransactionAbortedException;
import simpledb.Tuple;
import simpledb.TupleDesc;

/**
 * The consumer part of the Shuffle Exchange operator.
 * 
 * A ShuffleProducer operator sends tuples to all the workers according to some
 * PartitionFunction, while the ShuffleConsumer (this class) encapsulates the
 * methods to collect the tuples received at the worker from multiple source
 * workers' ShuffleProducer.
 * 
 * */
public class ShuffleConsumer extends Consumer {

    private static final long serialVersionUID = 1L;

    public String getName() {
        return "shuffle_c";
    }

    public ShuffleConsumer(ParallelOperatorID operatorID, SocketInfo[] workers) {
        this(null, operatorID, workers);
    }

    private OpIterator child;
    private SocketInfo[] workers;

    public ShuffleConsumer(ShuffleProducer child,
            ParallelOperatorID operatorID, SocketInfo[] workers) {
        super(operatorID);
        this.child = child;
        this.workers = workers;
    }

    @Override
    public void open() throws DbException, TransactionAbortedException {
        // some code goes here
    }

    @Override
    public void rewind() throws DbException, TransactionAbortedException {
        // some code goes here
    }

    @Override
    public void close() {
        // some code goes here
    }

    @Override
    public TupleDesc getTupleDesc() {
        // some code goes here
        return null;

    }

    /**
     * 
     * Retrieve a batch of tuples from the buffer of ExchangeMessages. Wait if
     * the buffer is empty.
     * 
     * @return Iterator over the new tuples received from the source workers.
     *         Return <code>null</code> if all source workers have sent an end
     *         of file message.
     */
    Iterator<Tuple> getTuples() throws InterruptedException {
        // some code goes here
        return null;
    }

    @Override
    protected Tuple fetchNext() throws DbException, TransactionAbortedException {
        // some code goes here
        return null;
    }

    @Override
    public OpIterator[] getChildren() {
        return new OpIterator[]{this.child};
    }

    @Override
    public void setChildren(OpIterator[] children) {
        this.child = children[0];
    }

}
