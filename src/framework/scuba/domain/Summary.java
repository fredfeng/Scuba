package framework.scuba.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.util.tuple.object.Pair;
import chord.util.tuple.object.Trio;

/**
 * Representing the summary for a method. Now it only contains abstractHeap.
 * 
 * @author yufeng
 * 
 */
public class Summary {

	private jq_Method method;

	private AbstractHeap absHeap;

	// (call site, callee method) --> memory location instantiation
	// invoke stmt includes: InvokeVirtual, InvokeStatic, and InvokeInterface
	final protected MemLocInstn4Method memLocInstnResult;

	// used for efficient caching
	final protected MemLocInstnCacheDepMap locDepMap;

	// smart skip for instantiating the callees
	protected Set<MemLocInstnItem> smartSkip = new HashSet<MemLocInstnItem>();
	//
	protected Map<MemLocInstnItem, Set<Trio<AbsMemLoc, HeapObject, FieldElem>>> instnedEdges = new HashMap<MemLocInstnItem, Set<Trio<AbsMemLoc, HeapObject, FieldElem>>>();
	protected Map<MemLocInstnItem, Map<AbsMemLoc, Set<Trio<AbsMemLoc, HeapObject, FieldElem>>>> edgeDepMap = new HashMap<MemLocInstnItem, Map<AbsMemLoc, Set<Trio<AbsMemLoc, HeapObject, FieldElem>>>>();

	// the methods this summary has effect on
	protected Set<Summary> jumpEffectSet = new HashSet<Summary>();
	// the methods that this summary depends on
	protected Set<Summary> jumpInstnSet = new HashSet<Summary>();

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

	// alias query in this method or instantiated in this method
	protected AliasQueries aliasQueries;

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
		this.absHeap = new AbstractHeap(this);
		this.memLocInstnResult = new MemLocInstn4Method(this);
		this.locDepMap = new MemLocInstnCacheDepMap(this);
		this.aliasQueries = new AliasQueries(meth, this);

	}

	// initialize the paramList
	// this will be done ONLY once!
	public void initFormals() {
		formals = new ArrayList<ParamElem>();
	}

	// fill the paramList from left to right, one by one
	// MUST keep the proper sequence!
	// every parameter in the list will only be translated once!
	public void fillFormals(jq_Class clazz, jq_Method method, Register param,
			jq_Type type) {
		formals.add(absHeap.getParamElem(clazz, method, param, type));
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

	public void validate() {
		absHeap.validate();
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

	public AbstractHeap getAbsHeap() {
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
	
	public boolean inSmartSkip(MemLocInstnItem item) {
		return smartSkip.contains(item);
	}
	
	public void addSmartSkip(MemLocInstnItem item) {
		smartSkip.add(item);
	}
	
	public boolean inJumpInstnSet(Summary sum) {
		return jumpInstnSet.contains(sum);
	}
	
	public void addJumpInstnSet(Summary sum) {
		jumpInstnSet.add(sum);
	}
	
	public MemLocInstn4Method getMemLocInstnResult() {
		return memLocInstnResult;
	}
	
	public MemLocInstnCacheDepMap getLocDepMap() {
		return locDepMap;
	}
	
	public Map<MemLocInstnItem, Set<Trio<AbsMemLoc, HeapObject, FieldElem>>> getInstnedEdges() {
		return instnedEdges;
	}
	
	public Map<MemLocInstnItem, Map<AbsMemLoc, Set<Trio<AbsMemLoc, HeapObject, FieldElem>>>> getEdgeDepMap() {
		return edgeDepMap;
	}
}
