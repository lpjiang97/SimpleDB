package simpledb.parallel;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
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

    private final BitSet workerEOS;
    private final HashMap<String, Integer> workerIdToIndex;

    private transient Iterator<Tuple> tuples;
    private transient int innerBufferIndex;
    private transient ArrayList<TupleBag> innerBuffer;

    public ShuffleConsumer(ShuffleProducer child,
            ParallelOperatorID operatorID, SocketInfo[] workers) {
        super(operatorID);
        this.child = child;
        this.workers = workers;
        this.workerIdToIndex = new HashMap<>();
        int idx = 0;
        for (SocketInfo w : workers)
            this.workerIdToIndex.put(w.getId(), idx++);
        this.workerEOS = new BitSet(workers.length);
    }

    @Override
    public void open() throws DbException, TransactionAbortedException {
        this.tuples = null;
        this.innerBuffer = new ArrayList<>();
        this.innerBufferIndex = 0;
        if (child != null)
            this.child.open();
        super.open();
    }

    @Override
    public void rewind() throws DbException, TransactionAbortedException {
        // some code goes here
        this.tuples = null;
        this.innerBufferIndex = 0;
    }

    @Override
    public void close() {
        // some code goes here
        super.close();
        this.setBuffer(null);
        this.tuples = null;
        this.innerBufferIndex = -1;
        this.innerBuffer = null;
        this.workerEOS.clear();
    }

    @Override
    public TupleDesc getTupleDesc() {
        if (child == null)
            return null;
        return child.getTupleDesc();
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
        TupleBag tb = null;
        if (this.innerBufferIndex < this.innerBuffer.size())
            return this.innerBuffer.get(this.innerBufferIndex++).iterator();

        while (this.workerEOS.nextClearBit(0) < this.workers.length) {
            tb = (TupleBag)this.take(-1);
            if (tb.isEos()) {
                this.workerEOS.set(this.workerIdToIndex.get(tb.getWorkerID()));
            } else {
                innerBuffer.add(tb);
                this.innerBufferIndex++;
                return tb.iterator();
            }
        }
        // have received all the eos message from all the workers
        return null;
    }

    @Override
    protected Tuple fetchNext() throws DbException, TransactionAbortedException {
        while (tuples == null || !tuples.hasNext()) {
	    try {
            this.tuples = getTuples();
	    } catch (InterruptedException e) {
	        e.printStackTrace();
            throw new DbException(e.getLocalizedMessage());
	    }
	    if (tuples == null) // finish
            return null;
        }
        return tuples.next();
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
