package framework.scuba.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.RegisterFactory.Register;
import framework.scuba.domain.AbstractMemLoc.ArgDerivedType;
import framework.scuba.helper.ArgDerivedHelper;

public class AbstractHeap {

	// the method whose heap is represented by this AbstractHeap
	protected jq_Method method;

	// heap is a translation from heap, which is used to dump the
	// topology of the abstract heap
	// THIS IS the main data structure to represent the abstract heap
	// every time we refer to a heap, it means this heap topology
	protected Map<AbstractMemLoc, Set<HeapObject>> heap;

	// heap is a mapping described in Figure 7 of the paper
	// mapping: (\pi, f) --> \theta
	// THIS IS just a helper field used to get the P2Set but still very critical
	protected Map<HeapObject, P2Set> heapObjectsToP2Set;

	// all the abstract memory locations that have been CREATED as instances in
	// the heap, and this is a map mapping key to value which is the key itself
	protected Map<AbstractMemLoc, AbstractMemLoc> memLocFactory;

	// protected ArgDerivedHelper argDerivedHelper = new ArgDerivedHelper();

	public AbstractHeap() {
		heap = new HashMap<AbstractMemLoc, Set<HeapObject>>();
		heapObjectsToP2Set = new HashMap<HeapObject, P2Set>();
		memLocFactory = new HashMap<AbstractMemLoc, AbstractMemLoc>();
	}

	// field look-up for location which is decribed in definition 7 of the paper
	public P2Set lookup(AbstractMemLoc loc, FieldElem field) {
		if (loc.isArgDerived()) {
			AbstractMemLoc loc1 = getAbstractMemLoc(loc, field);
			
		} else if (loc.isNotArgDerived()) {

		} else {
			assert false : "we have not mark the argument derived marker before lookup!";
		}

		return null;
	}

	// check whether some abstract memory location is contained in the heap
	public boolean hasCreated(AbstractMemLoc loc) {
		return memLocFactory.containsKey(loc);
	}

	// check whether some abstract memory location is in the heap
	public boolean isInHeap(AbstractMemLoc loc) {
		return heap.containsKey(loc);
	}

	protected AbstractMemLoc getAbstractMemLoc(AbstractMemLoc base,
			FieldElem field) {
		AbstractMemLoc ret = null;
		if (base instanceof HeapObject) {
			ret = getAbstractMemLoc(((HeapObject) base), field);
		} else if (base instanceof ParamElem) {
			ret = getAbstractMemLoc(((ParamElem) base), field);
		} else if (base instanceof LocalVarElem) {
			assert false : base + " is a LocalVarElem";
		} else {
			assert false : "wried things!";
		}

		return ret;
	}

	protected AbstractMemLoc getAbstractMemLoc(ParamElem base, FieldElem field) {
		AccessPath ret = new AccessPath(base, field);
		if (memLocFactory.containsKey(ret)) {
			return memLocFactory.get(ret);
		}

		memLocFactory.put(ret, ret);

		return ret;
	}

	// get the AccessPath object using memLocFactory which generates that if it
	// is not in the factory
	protected AbstractMemLoc getAbstractMemLoc(HeapObject base, FieldElem field) {
		AccessPath ret = new AccessPath(base, field);
		if (memLocFactory.containsKey(ret)) {
			return memLocFactory.get(ret);
		}

		memLocFactory.put(ret, ret);

		return ret;
	}

	// given a local variable in the bytecode, get the corresponding
	// LocalVarElem
	public AbstractMemLoc getAbstractMemLoc(jq_Class clazz, jq_Method method,
			Register local) {
		// create a wrapper
		LocalVarElem ret = new LocalVarElem(clazz, method, local);
		// try to look up this wrapper in the memory location factory
		if (memLocFactory.containsKey(ret)) {
			return memLocFactory.get(ret);
		}

		// not found in the factory
		// every time generating a memory location, do this marking
		ArgDerivedHelper.markArgDerived(ret);

		memLocFactory.put(ret, ret);

		return ret;
	}

	// given a new instruction in the bytecode, create the corresponding
	public AbstractMemLoc getAbstractMemLoc(jq_Class clazz, jq_Method method,
			jq_Type type, int line) {
		Context context = new Context(new ProgramPoint(clazz, method, line));
		// create an AllocElem wrapper
		AllocElem ret = new AllocElem(new Alloc(type), context);
		// try to look up this wrapper in the memory location factory
		if (memLocFactory.containsKey(ret)) {
			return memLocFactory.get(ret);
		}

		// not found in the factory
		// every time generating a memory location, do this marking
		ArgDerivedHelper.markArgDerived(ret);

		memLocFactory.put(ret, ret);

		return ret;
	}

	public boolean strongUpdate(HeapObject obj, P2Set p2Set) {
		heapObjectsToP2Set.put(obj, p2Set);
		return false;
	}

	public boolean weakUpdate(HeapObject obj, P2Set p2Set) {
		P2Set ret = null;
		if (heapObjectsToP2Set.containsKey(obj)) {
			ret = heapObjectsToP2Set.get(obj);
		} else {
			ret = new P2Set();
			heapObjectsToP2Set.put(obj, ret);
		}

		ret.join(p2Set);

		return false;
	}
}
