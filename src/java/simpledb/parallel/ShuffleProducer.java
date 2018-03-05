package simpledb.parallel;

import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.core.future.WriteFuture;
import org.apache.mina.core.session.IoSession;
import simpledb.*;
import simpledb.OpIterator;
import java.util.ArrayList;
import java.util.List;

/**
 * The producer part of the Shuffle Exchange operator.
 * 
 * ShuffleProducer distributes tuples to the workers according to some
 * partition function (provided as a PartitionFunction object during the
 * ShuffleProducer's instantiation).
 * 
 * */
public class ShuffleProducer extends Producer {

    private static final long serialVersionUID = 1L;

    public String getName() {
        return "shuffle_p";
    }

    private OpIterator child;
    private SocketInfo[] workers;
    private PartitionFunction<?, ?> pf;

    private transient WorkingThread runningThread;

    public ShuffleProducer(OpIterator child, ParallelOperatorID operatorID,
                           SocketInfo[] workers, PartitionFunction<?, ?> pf) {
        super(operatorID);
        // some code goes here
        this.child = child;
        this.workers = workers;
        this.pf = pf;
    }

    public void setPartitionFunction(PartitionFunction<?, ?> pf) {
        this.pf = pf;
    }

    public SocketInfo[] getWorkers() {
        return this.workers;
    }

    public PartitionFunction<?, ?> getPartitionFunction() {
        return this.pf;
    }

    // some code goes here
    class WorkingThread extends Thread {
        public void run() {
            // create sessions, buffers, and timestamps for each worker
            IoSession[] sessions = new IoSession[workers.length];
            List<List<Tuple>> buffers = new ArrayList<>(workers.length);
            long[] lastTimes = new long[workers.length];
            for (int i = 0 ; i < workers.length; i++) {
                sessions[i] = ParallelUtility.createSession(workers[i].getAddress(), getThisWorker().minaHandler, -1);
                buffers.add(new ArrayList<>());
            }
            try {
                while (child.hasNext()) {
                    Tuple tup = child.next();
                    int workerNo = pf.partition(tup, getTupleDesc());
                    // get the right worker
                    List<Tuple> buffer = buffers.get(workerNo);
                    IoSession session = sessions[workerNo];
                    // sending
                    buffer.add(tup);
                    int cnt = buffer.size();
                    if (cnt >= TupleBag.MAX_SIZE) {
                        session.write(new TupleBag(
                                operatorID, getThisWorker().workerID, buffer.toArray(new Tuple[] {}), getTupleDesc()));
                        buffer.clear();
                        lastTimes[workerNo] = System.currentTimeMillis();
                    }
                    if (cnt >= TupleBag.MIN_SIZE) {
                        long thisTime = System.currentTimeMillis();
                        if (thisTime - lastTimes[workerNo]> TupleBag.MAX_MS) {
                            session.write(new TupleBag(
                                    operatorID, getThisWorker().workerID, buffer.toArray(new Tuple[] {}), getTupleDesc()));
                            buffer.clear();
                            lastTimes[workerNo] = thisTime;
                        }
                    }
                }
                // now check all tuples in the buffers residule
                for (int i = 0; i < buffers.size(); i++) {
                    if (buffers.get(i).size() > 0)
                        sessions[i].write(new TupleBag(
                                operatorID, getThisWorker().workerID, buffers.get(i).toArray(new Tuple[] {}), getTupleDesc()));
                    sessions[i].write(new TupleBag(operatorID, getThisWorker().workerID)).addListener((future) ->
                            ParallelUtility.closeSession(future.getSession())
                    );
                }
            } catch (DbException e) {
                e.printStackTrace();
            } catch (TransactionAbortedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void open() throws DbException, TransactionAbortedException {
        // some code goes here
        child.open();
        runningThread = new WorkingThread();
        runningThread.start();
        super.open();
    }

    public void close() {
        super.close();
        child.close();
    }

    @Override
    public void rewind() throws DbException, TransactionAbortedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public TupleDesc getTupleDesc() {
        return child.getTupleDesc();
    }

    @Override
    protected Tuple fetchNext() throws DbException, TransactionAbortedException {
        try {
            // wait until the working thread terminate and return an empty tuple set
            runningThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
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
