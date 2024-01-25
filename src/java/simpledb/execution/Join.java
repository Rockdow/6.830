package simpledb.execution;

import simpledb.storage.Field;
import simpledb.transaction.TransactionAbortedException;
import simpledb.common.DbException;
import simpledb.storage.Tuple;
import simpledb.storage.TupleDesc;

import java.util.*;

/**
 * The Join operator implements the relational join operation.
 */
public class Join extends Operator {

    private static final long serialVersionUID = 1L;
    private OpIterator[] opIterators = new OpIterator[2];
    private final JoinPredicate joinPredicate;
    private volatile boolean initial = false;
    private ThreadLocal<Tuple> leftThreadLocal = new ThreadLocal<>();
    /**
     * Constructor. Accepts two children to join and the predicate to join them
     * on
     * 
     * @param p
     *            The predicate to use to join the children
     * @param child1
     *            Iterator for the left(outer) relation to join
     * @param child2
     *            Iterator for the right(inner) relation to join
     */
    public Join(JoinPredicate p, OpIterator child1, OpIterator child2) {
        this.joinPredicate = p;
        this.opIterators[0] = child1;
        this.opIterators[1] = child2;
    }

    public JoinPredicate getJoinPredicate() {
        return this.joinPredicate;
    }

    /**
     * @return
     *       the field name of join field1. Should be quantified by
     *       alias or table name.
     * */
    public String getJoinField1Name() {
        return opIterators[0].getTupleDesc().getFieldName(joinPredicate.getField1());
    }

    /**
     * @return
     *       the field name of join field2. Should be quantified by
     *       alias or table name.
     * */
    public String getJoinField2Name() {
        return opIterators[1].getTupleDesc().getFieldName(joinPredicate.getField2());
    }

    /**
     * @see TupleDesc#merge(TupleDesc, TupleDesc) for possible
     *      implementation logic.
     */
    public TupleDesc getTupleDesc() {
        return TupleDesc.merge(opIterators[0].getTupleDesc(),opIterators[1].getTupleDesc());
    }

    public void open() throws DbException, NoSuchElementException,
            TransactionAbortedException {
        super.open();
        opIterators[0].open();
        opIterators[1].open();
    }

    public void close() {
        super.close();
        opIterators[0].close();
        opIterators[1].close();
    }

    public void rewind() throws DbException, TransactionAbortedException {
        super.open();
        opIterators[0].rewind();
        opIterators[1].rewind();
    }

    /**
     * Returns the next tuple generated by the join, or null if there are no
     * more tuples. Logically, this is the next tuple in r1 cross r2 that
     * satisfies the join predicate. There are many possible implementations;
     * the simplest is a nested loops join.
     * <p>
     * Note that the tuples returned from this particular implementation of Join
     * are simply the concatenation of joining tuples from the left and right
     * relation. Therefore, if an equality predicate is used there will be two
     * copies of the join attribute in the results. (Removing such duplicate
     * columns can be done with an additional projection operator if needed.)
     * <p>
     * For example, if one tuple is {1,2,3} and the other tuple is {1,5,6},
     * joined on equality of the first column, then this returns {1,2,3,1,5,6}.
     * 
     * @return The next matching tuple.
     * @see JoinPredicate#filter
     */
    protected Tuple fetchNext() throws TransactionAbortedException, DbException {
        Tuple left = null;
        Tuple right = null;
        // 判断 opIterators[0].hasNext() || opIterators[1].hasNext() 是让 opIterators[0]拿出最后一个元素后可以再遍历一遍 opIterators[1]中的元素
        outer:while(opIterators[0].hasNext() || opIterators[1].hasNext()){
            if(initial==false || !opIterators[1].hasNext()){
                initial = true;
                if(!opIterators[1].hasNext())
                    opIterators[1].rewind();
                leftThreadLocal.set(opIterators[0].next());
            }
            left = leftThreadLocal.get();
            right = null;
            if(left != null){
                while (opIterators[1].hasNext()){
                    right = opIterators[1].next();
                    if(right != null){
                        if(joinPredicate.filter(left,right)){
                            break outer;
                        }else {
                            right = null;
                        }
                    }
                }
            }
        }
        if(left == null || right == null){
            return null;
        }else{
            Tuple tuple = new Tuple(this.getTupleDesc());
            Iterator<Field> lIter = left.fields();
            Iterator<Field> rIter = right.fields();
            int idx = 0;
            while (lIter.hasNext()){
                tuple.setField(idx++,lIter.next());
            }
            while (rIter.hasNext()){
                tuple.setField(idx++,rIter.next());
            }
            return tuple;
        }
    }

    @Override
    public OpIterator[] getChildren() {
        return this.opIterators;
    }

    @Override
    public void setChildren(OpIterator[] children) {
        this.opIterators = children;
    }

}
