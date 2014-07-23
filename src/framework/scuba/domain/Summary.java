package framework.scuba.domain;

import java.util.ArrayList;
import java.util.List;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.util.tuple.object.Pair;

/**
 * Representing the summary for a method. Now it only contains abstractHeap.
 * 
 * @author yufeng
 * 
 */
public class Summary {

	private jq_Method method;

	private AbsHeap absHeap;

	// (call site, callee method) --> memory location instantiation
	// invoke stmt includes: InvokeVirtual, InvokeStatic, and InvokeInterface
	final protected MemLocInstn4Method memLocInstnResult;

	// finish current summary.
	private boolean terminated;

	// parameter list used for instantiating
	// once initialized, never changed
	protected List<ParamElem> formals;

	// return value list
	protected RetElem retValue;

	// heap for the whole summary has changed?
	protected Pair<Boolean, Boolean> changed = new Pair<Boolean, Boolean>(
			false, false);

	// used for dealing with recursive call
	protected boolean hasAnalyzed = false;

	// whether current method is in a bad scc.
	protected boolean inBadScc = false;

	public boolean isInBadScc() {
		return inBadScc;
	}

	public void setInBadScc(boolean inBadScc) {
		this.inBadScc = inBadScc;
	}

	public boolean heapIsChanged() {
		return changed.val0;
	}

	public boolean sumIsChanged() {
		return changed.val1;
	}

	public Pair<Boolean, Boolean> isChanged() {
		return changed;
	}

	public void setChanged(Pair<Boolean, Boolean> isChanged) {
		this.changed.val0 = isChanged.val0;
		this.changed.val1 = isChanged.val1;
	}

	public Summary(jq_Method meth) {
		this.method = meth;
		this.absHeap = new AbsHeap(this);
		this.memLocInstnResult = new MemLocInstn4Method(this);
	}

	// initialize the paramList
	// this will be done ONLY once!
	public void initFormals() {
		formals = new ArrayList<ParamElem>();
	}

	// fill the paramList from left to right, one by one
	// MUST keep the proper sequence!
	// every parameter in the list will only be translated once!
	public void fillFormals(jq_Class clazz, jq_Method meth, Register r,
			jq_Type type) {
		formals.add(Env.getParamElem(r, meth, clazz, type));
	}

	// get the paramList, which will used in the instantiation
	public List<ParamElem> getFormals() {
		return formals;
	}

	public void setRetValue(RetElem retValue) {
		this.retValue = retValue;
	}

	public RetElem getRetValue() {
		return retValue;
	}

	public boolean isTerminated() {
		return terminated;
	}

	public void setTerminated(boolean terminated) {
		this.terminated = terminated;
	}

	public jq_Method getMethod() {
		return method;
	}

	public AbsHeap getAbsHeap() {
		return absHeap;
	}

	public boolean hasAnalyzed() {
		return hasAnalyzed;
	}

	public void setHasAnalyzed() {
		hasAnalyzed = true;
	}

	public int getHeapSize() {
		return absHeap.size();
	}

	public MemLocInstn4Method getMemLocInstnResult() {
		return memLocInstnResult;
	}

}