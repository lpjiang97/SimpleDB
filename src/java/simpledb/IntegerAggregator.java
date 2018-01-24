package simpledb;

import sun.text.normalizer.IntTrie;

import java.util.*;

/**
 * Knows how to compute some aggregate over a set of IntFields.
 */
public class IntegerAggregator implements Aggregator {

    private static final long serialVersionUID = 1L;

    private int gbfield;
    private Type gbfieldtype;
    private int afield;
    private Op what;

    private Map<Field, Integer> groupMap;
    // only used for AVG aggregate
    private Map<Field, List<Integer>> avgMap;

    /**
     * Aggregate constructor
     * 
     * @param gbfield
     *            the 0-based index of the group-by field in the tuple, or
     *            NO_GROUPING if there is no grouping
     * @param gbfieldtype
     *            the type of the group by field (e.g., Type.INT_TYPE), or null
     *            if there is no grouping
     * @param afield
     *            the 0-based index of the aggregate field in the tuple
     * @param what
     *            the aggregation operator
     */

    public IntegerAggregator(int gbfield, Type gbfieldtype, int afield, Op what) {
        this.gbfield = gbfield;
        this.gbfieldtype = gbfieldtype;
        this.afield = afield;
        this.what = what;
        this.groupMap = new HashMap<>();
        // can't do avg on the go -- integer division might result in bad value
        this.avgMap = new HashMap<>();
    }

    /**
     * Merge a new tuple into the aggregate, grouping as indicated in the
     * constructor
     * 
     * @param tup
     *            the Tuple containing an aggregate field and a group-by field
     */
    public void mergeTupleIntoGroup(Tuple tup) {
        // get fields
        IntField afield = (IntField)tup.getField(this.afield);
        Field gbfield = tup.getField(this.gbfield);
        int newValue = afield.getValue();

        if (gbfield.getType() != this.gbfieldtype) {
            throw new IllegalArgumentException("Given tuple has wrong type");
        }
        // get number
        switch (this.what) {
            case MIN:
                if (!this.groupMap.containsKey(gbfield))
                    this.groupMap.put(gbfield, newValue);
                else
                    this.groupMap.put(gbfield, Math.min(this.groupMap.get(gbfield), newValue));
                break;
            case MAX:
                if (!this.groupMap.containsKey(gbfield))
                    this.groupMap.put(gbfield, newValue);
                else
                    this.groupMap.put(gbfield, Math.max(this.groupMap.get(gbfield), newValue));
                break;
            case SUM:
                if (!this.groupMap.containsKey(gbfield))
                    this.groupMap.put(gbfield, newValue);
                else
                    this.groupMap.put(gbfield, this.groupMap.get(gbfield) + newValue);
                break;
            case COUNT:
                if (!this.groupMap.containsKey(gbfield))
                    this.groupMap.put(gbfield, 1);
                else
                    this.groupMap.put(gbfield, this.groupMap.get(gbfield) + 1);
                break;
            case AVG:
                if (!this.avgMap.containsKey(gbfield)) {
                    List<Integer> l = new ArrayList<>();
                    l.add(newValue);
                    this.avgMap.put(gbfield, l);
                } else {
                    // reference
                    List<Integer> l = this.avgMap.get(gbfield);
                    l.add(newValue);
                }
                break;
            default:
                throw new IllegalArgumentException("Aggregate not supported!");
        }
    }


    /**
     * Create a OpIterator over group aggregate results.
     * 
     * @return a OpIterator whose tuples are the pair (groupVal, aggregateVal)
     *         if using group, or a single (aggregateVal) if no grouping. The
     *         aggregateVal is determined by the type of aggregate specified in
     *         the constructor.
     */
    public OpIterator iterator() {
        return new IntAggIterator();
    }

    private class IntAggIterator implements OpIterator {

        private Iterator<Map.Entry<Field, Integer>> it;
        private Iterator<Map.Entry<Field, List<Integer>>> avgIt;
        private boolean isAvg;
        private TupleDesc td;


        @Override
        public void open() throws DbException, TransactionAbortedException {
            this.it = groupMap.entrySet().iterator();
            this.isAvg = (what.equals(Op.AVG));
            if (this.isAvg)
                this.avgIt = avgMap.entrySet().iterator();
            else
                this.avgIt = null;
            // no grouping
            if (groupMap.containsKey(null))
                this.td = new TupleDesc(new Type[] {Type.INT_TYPE}, new String[] {"aggregateVal"});
            else
                this.td = new TupleDesc(new Type[] {gbfieldtype, Type.INT_TYPE}, new String[] {"groupVal", "aggregateVal"});
        }

        @Override
        public boolean hasNext() throws DbException, TransactionAbortedException {
            if (this.isAvg)
                return avgIt.hasNext();
            return it.hasNext();
        }

        @Override
        public Tuple next() throws DbException, TransactionAbortedException, NoSuchElementException {
            int value = 0;
            boolean noGroup = false;
            Field f = null;
            if (this.isAvg) {
                Map.Entry<Field, List<Integer>> entry = this.avgIt.next();
                f = entry.getKey();
                noGroup = f == null;
                List<Integer> l = entry.getValue();
                value = this.sumList(l) / l.size();
            } else {
                Map.Entry<Field, Integer> entry = this.it.next();
                f = entry.getKey();
                noGroup = f == null;
                value = entry.getValue();
            }
            Tuple rtn = new Tuple(this.td);
            // no grouping
            if (noGroup) {
                rtn.setField(0, new IntField(value));
            } else {
                rtn.setField(0, f);
                rtn.setField(1, new IntField(value));
            }
            return rtn;
        }

        @Override
        public void rewind() throws DbException, TransactionAbortedException {
            this.it = groupMap.entrySet().iterator();
            if (this.isAvg)
                this.avgIt = avgMap.entrySet().iterator();
        }

        @Override
        public TupleDesc getTupleDesc() {
            return this.td;
        }

        @Override
        public void close() {
            this.it = null;
            this.avgIt = null;
            this.td = null;
        }

        private int sumList(List<Integer> l) {
            int sum = 0;
            for (int i : l)
                sum += i;
            return sum;
        }
    }
}
