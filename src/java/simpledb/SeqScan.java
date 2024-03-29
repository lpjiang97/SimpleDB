package simpledb;

import com.sun.corba.se.impl.orb.DataCollectorBase;

import javax.xml.crypto.Data;
import java.util.*;

/**
 * SeqScan is an implementation of a sequential scan access method that reads
 * each tuple of a table in no particular order (e.g., as they are laid out on
 * disk).
 */
public class SeqScan implements OpIterator {

    private static final long serialVersionUID = 1L;

    private TransactionId tid;
    private int tableid;
    private String tableAlias;
    private String tableName;
    private TupleDesc td;
    private transient DbFileIterator it;

    /**
     * Creates a sequential scan over the specified table as a part of the
     * specified transaction.
     *
     * @param tid
     *            The transaction this scan is running as a part of.
     * @param tableid
     *            the table to scan.
     * @param tableAlias
     *            the alias of this table (needed by the parser); the returned
     *            tupleDesc should have fields with name tableAlias.fieldName
     *            (note: this class is not responsible for handling a case where
     *            tableAlias or fieldName are null. It shouldn't crash if they
     *            are, but the resulting name can be null.fieldName,
     *            tableAlias.null, or null.null).
     */
    public SeqScan(TransactionId tid, int tableid, String tableAlias) {
        // some code goes here
        this.tid = tid;
        this.reset(tableid, tableAlias);

        this.it = null;
    }

    /**
     * @return
     *       return the table name of the table the operator scans. This should
     *       be the actual name of the table in the catalog of the database
     * */
    public String getTableName() {
        return this.tableName;
    }

    /**
     * @return Return the alias of the table this operator scans.
     * */
    public String getAlias()
    {
        return this.tableAlias;
    }

    /**
     * Reset the tableid, and tableAlias of this operator.
     * @param tableid
     *            the table to scan.
     * @param tableAlias
     *            the alias of this table (needed by the parser); the returned
     *            tupleDesc should have fields with name tableAlias.fieldName
     *            (note: this class is not responsible for handling a case where
     *            tableAlias or fieldName are null. It shouldn't crash if they
     *            are, but the resulting name can be null.fieldName,
     *            tableAlias.null, or null.null).
     */
    public void reset(int tableid, String tableAlias) {
        this.tableid = tableid;
        this.tableAlias = tableAlias;
        this.tableName = Database.getCatalog().getTableName(this.tableid);
        this.td = Database.getCatalog().getTupleDesc(this.tableid);
    }

    public SeqScan(TransactionId tid, int tableId) {
        this(tid, tableId, Database.getCatalog().getTableName(tableId));
    }

    public void open() throws DbException, TransactionAbortedException {
        DbFile f = Database.getCatalog().getDatabaseFile(this.tableid);
        this.it = f.iterator(this.tid);
        this.it.open();
    }

    /**
     * Returns the TupleDesc with field names from the underlying HeapFile,
     * prefixed with the tableAlias string from the constructor. This prefix
     * becomes useful when joining tables containing a field(s) with the same
     * name.  The alias and name should be separated with a "." character
     * (e.g., "alias.fieldName").
     *
     * @return the TupleDesc with field names from the underlying HeapFile,
     *         prefixed with the tableAlias string from the constructor.
     */
    public TupleDesc getTupleDesc() {
        TupleDesc noPrefix = this.td;
        int length = noPrefix.numFields();
        // new TupleDesc
        Type[] types = new Type[length];
        String[] fieldNames = new String[length];
        for (int i = 0; i < length; i++) {
            types[i] = noPrefix.getFieldType(i);
            fieldNames[i] = this.tableAlias + "." + noPrefix.getFieldName(i);
        }
        return new TupleDesc(types, fieldNames);
    }

    public boolean hasNext() throws TransactionAbortedException, DbException {
        return this.it.hasNext();
    }

    public Tuple next() throws NoSuchElementException,
            TransactionAbortedException, DbException {
        return this.it.next();
    }

    public void close() {
        this.it.close();
    }

    public void rewind() throws DbException, NoSuchElementException,
            TransactionAbortedException {
        this.it.rewind();
    }
}
