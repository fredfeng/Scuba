package framework.scuba.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Class;
import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.RegisterFactory.Register;
import framework.scuba.helper.ArgDerivedHelper;
import framework.scuba.helper.ConstraintManager;
import framework.scuba.helper.P2SetHelper;

public class AbstractHeap {

	// the method whose heap is represented by this AbstractHeap
	protected jq_Method method;

	// heap is a translation from heap, which is used to dump the
	// topology of the abstract heap
	// THIS IS the main data structure to represent the abstract heap
	// every time we refer to a heap, it means this heap topology
	// MAYBE we will not use this? we can use memLocFactory
	protected Map<AbstractMemLoc, Set<HeapObject>> heap;

	// heap is a mapping described in Figure 7 of the paper
	// mapping: (\pi, f) --> \theta
	// THIS IS just a helper field used to get the P2Set but still very critical
	protected Map<HeapObject, P2Set> heapObjectsToP2Set;

	// all the abstract memory locations that have been CREATED as instances in
	// the heap, and this is a map mapping key to value which is the key itself
	// this should include the keySet of heap but include more than that (maybe
	// some locations are not used in the program
	protected Map<AbstractMemLoc, AbstractMemLoc> memLocFactory;

	// protected ArgDerivedHelper argDerivedHelper = new ArgDerivedHelper();

	public static enum VariableType {
		PARAMEMTER, LOCAL_VARIABLE;
	}

	public AbstractHeap() {
		heap = new HashMap<AbstractMemLoc, Set<HeapObject>>();
		heapObjectsToP2Set = new HashMap<HeapObject, P2Set>();
		memLocFactory = new HashMap<AbstractMemLoc, AbstractMemLoc>();
	}

	public void dump() {
		StringBuilder b = new StringBuilder("Abstract Heap {\n");
		b.append("  rankdir = LR;\n");

		for (AbstractMemLoc loc : memLocFactory.keySet()) {
			if (loc instanceof AccessPath) {
				b.append("  ").append("\"" + loc + "\"");
				b.append(" [shape=circle,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof AllocElem) {
				b.append("  ").append("\"" + loc + "\"");
				b.append(" [shape=doublecircle,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof StaticElem) {
				b.append("  ").append("\"" + loc + "\"");
				b.append(" [shape=circle,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof LocalVarElem) {
				b.append("  ").append("\"" + loc + "\"");
				b.append(" [shape=rectangle,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof ParamElem) {
				b.append("  ").append("\"" + loc + "\"");
				b.append(" [shape=oval,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else {
				assert false : "wried things!";
			}
		}

		for (AbstractMemLoc loc : memLocFactory.keySet()) {
			Set<FieldElem> fields = loc.getFields();
			for (FieldElem f : fields) {
				P2Set p2Set = heapObjectsToP2Set.get(getAbstractMemLoc(loc, f));
				for (HeapObject obj : p2Set.getHeapObjects()) {
					b.append("  ").append("\"" + loc + "\"");
					b.append(" -> ").append("\"" + obj + "\"")
							.append(" [label=\"");
					b.append("\"" + f + "\"");
					b.append("\"]\n");
				}
			}
		}
	}

	// field look-up for location which is described in definition 7 of the
	// paper
	public P2Set lookup(AbstractMemLoc loc, FieldElem field) {
		HeapObject loc1 = getAbstractMemLoc(loc, field);
		if (loc.isArgDerived()) {
			P2Set defaultP2Set = new P2Set(loc1);
			if (heapObjectsToP2Set.containsKey(loc1)) {
				return P2SetHelper.join(heapObjectsToP2Set.get(loc1),
						defaultP2Set);
			} else {
				return defaultP2Set;
			}
		} else if (loc.isNotArgDerived()) {
			return heapObjectsToP2Set.get(loc1);
		} else {
			assert false : "we have not mark the argument derived marker before lookup!";
		}

		return null;
	}

	// generalized field look-up for location which is described in definition
	// 10 of the paper
	public P2Set lookup(P2Set p2Set, FieldElem field) {
		P2Set ret = new P2Set();
		for (HeapObject obj : p2Set.getHeapObjects()) {
			Constraint cst = p2Set.getConstraint(obj);
			P2Set tgt = lookup(obj, field);
			P2Set projP2Set = P2SetHelper.project(tgt, cst);
			ret.join(projP2Set);
		}

		return ret;
	}

	// handleAssgnStmt implements rule (1) in Figure 8 of the paper
	// v1 = v2
	public void handleAssgnStmt(jq_Class clazz, jq_Method method,
			Register left, VariableType leftVType, Register right,
			VariableType rightVType) {
		StackObject v1 = getAbstractMemLoc(clazz, method, left, leftVType);
		StackObject v2 = getAbstractMemLoc(clazz, method, right, rightVType);
		P2Set p2Setv2 = lookup(v2, new EpsilonFieldElem());
		HeapObject h1 = getAbstractMemLoc(v1, new EpsilonFieldElem());
		weakUpdate(h1, p2Setv2);
	}

	// handleLoadStmt implements rule (2) in Figure 8 of the paper
	// v1 = v2.f
	public void handleLoadStmt(jq_Class clazz, jq_Method method, Register left,
			VariableType leftVType, Register rightBase, jq_Field rightField,
			VariableType rightBaseVType) {
		StackObject v1 = getAbstractMemLoc(clazz, method, left, leftVType);
		StackObject v2 = getAbstractMemLoc(clazz, method, rightBase,
				rightBaseVType);
		P2Set p2Setv2 = lookup(v2, new EpsilonFieldElem());
		NormalFieldElem f = new NormalFieldElem(rightField);
		P2Set p2Setv2Epsilon = lookup(p2Setv2, f);
		HeapObject h1 = getAbstractMemLoc(v1, new EpsilonFieldElem());
		weakUpdate(h1, p2Setv2Epsilon);
	}

	// handleLoadStmt implements rule (2) in Figure 8 of the paper
	// v1 = A.f, where A is a class and f is a static field
	public void handleLoadStmt(jq_Class clazz, jq_Method method, Register left,
			VariableType leftVType, jq_Class rightBase, jq_Field rightField) {
		StackObject v1 = getAbstractMemLoc(clazz, method, left, leftVType);
		StaticElem v2 = getAbstractMemLoc(rightBase);
		P2Set p2Setv2 = lookup(v2, new EpsilonFieldElem());
		NormalFieldElem f = new NormalFieldElem(rightField);
		P2Set p2Setv2Epsilon = lookup(p2Setv2, f);
		HeapObject h1 = getAbstractMemLoc(v1, new EpsilonFieldElem());
		weakUpdate(h1, p2Setv2Epsilon);
	}

	// handleStoreStmt implements rule (3) in Figure 8 of the paper
	// v1.f = v2
	public void handleStoreStmt(jq_Class clazz, jq_Method method,
			Register leftBase, VariableType leftBaseVType, jq_Field leftField,
			Register right, VariableType rightVType) {
		StackObject v1 = getAbstractMemLoc(clazz, method, leftBase,
				leftBaseVType);
		StackObject v2 = getAbstractMemLoc(clazz, method, right, rightVType);
		P2Set p2Setv1 = lookup(v1, new EpsilonFieldElem());
		P2Set p2Setv2 = lookup(v2, new EpsilonFieldElem());
		NormalFieldElem f = new NormalFieldElem(leftField);
		for (HeapObject obj : p2Setv1.getHeapObjects()) {
			Constraint cst = p2Setv1.getConstraint(obj);
			P2Set projP2Set = P2SetHelper.project(p2Setv2, cst);
			HeapObject tgtObj = getAbstractMemLoc(obj, f);
			weakUpdate(tgtObj, projP2Set);
		}
	}

	// handleNewStmt implements rule (4) in Figure 8 of the paper
	// v = new T
	public void handleNewStmt(jq_Class clazz, jq_Method method, Register left,
			VariableType leftVType, jq_Type right, int line) {
		AllocElem allocT = getAbstractMemLoc(clazz, method, right, line);
		StackObject v = getAbstractMemLoc(clazz, method, left, leftVType);
		HeapObject h1 = getAbstractMemLoc(v, new EpsilonFieldElem());
		weakUpdate(h1, new P2Set(allocT, ConstraintManager.genTrue()));
	}

	// check whether some abstract memory location is contained in the heap
	public boolean hasCreated(AbstractMemLoc loc) {
		return memLocFactory.containsKey(loc);
	}

	// check whether some abstract memory location is in the heap
	public boolean isInHeap(AbstractMemLoc loc) {
		return heap.containsKey(loc);
	}

	protected HeapObject getAbstractMemLoc(AbstractMemLoc base, FieldElem field) {
		HeapObject ret = null;
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

	protected AccessPath getAbstractMemLoc(ParamElem base, FieldElem field) {
		AccessPath ret = new AccessPath(base, field);
		if (memLocFactory.containsKey(ret)) {
			return (AccessPath) memLocFactory.get(ret);
		}

		memLocFactory.put(ret, ret);

		return ret;
	}

	// get the AccessPath object using memLocFactory which generates that if it
	// is not in the factory
	protected AccessPath getAbstractMemLoc(HeapObject base, FieldElem field) {
		AccessPath ret = new AccessPath(base, field);
		if (memLocFactory.containsKey(ret)) {
			return (AccessPath) memLocFactory.get(ret);
		}

		memLocFactory.put(ret, ret);

		return ret;
	}

	public StackObject getAbstractMemLoc(jq_Class clazz, jq_Method method,
			Register variable, VariableType vType) {
		if (vType == VariableType.LOCAL_VARIABLE) {
			// create a wrapper
			LocalVarElem ret = new LocalVarElem(clazz, method, variable);
			// try to look up this wrapper in the memory location factory
			if (memLocFactory.containsKey(ret)) {
				return (LocalVarElem) memLocFactory.get(ret);
			}

			// not found in the factory
			// every time generating a memory location, do this marking
			ArgDerivedHelper.markArgDerived(ret);

			memLocFactory.put(ret, ret);

			return ret;
		} else {
			// create a wrapper
			ParamElem ret = new ParamElem(clazz, method, variable);
			// try to look up this wrapper in the memory location factory
			if (memLocFactory.containsKey(ret)) {
				return (ParamElem) memLocFactory.get(ret);
			}

			// not found in the factory
			// every time generating a memory location, do this marking
			ArgDerivedHelper.markArgDerived(ret);

			memLocFactory.put(ret, ret);

			return ret;
		}
	}

	// given a new instruction in the bytecode, create the corresponding
	// AllocElem
	public AllocElem getAbstractMemLoc(jq_Class clazz, jq_Method method,
			jq_Type type, int line) {
		Context context = new Context(new ProgramPoint(clazz, method, line));
		// create an AllocElem wrapper
		AllocElem ret = new AllocElem(new Alloc(type), context);
		// try to look up this wrapper in the memory location factory
		if (memLocFactory.containsKey(ret)) {
			return (AllocElem) memLocFactory.get(ret);
		}

		// not found in the factory
		// every time generating a memory location, do this marking
		ArgDerivedHelper.markArgDerived(ret);

		memLocFactory.put(ret, ret);

		return ret;
	}

	// given a class (thinking about static field like A.f), create the
	// corresponding ClassElem
	public StaticElem getAbstractMemLoc(jq_Class clazz) {
		// create a wrapper
		StaticElem ret = new StaticElem(clazz);
		// try to look up this wrapper in the memory location factory
		if (memLocFactory.containsKey(ret)) {
			return (StaticElem) memLocFactory.get(ret);
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

	public P2Set weakUpdate(HeapObject obj, P2Set p2Set) {
		P2Set ret = null;
		if (heapObjectsToP2Set.containsKey(obj)) {
			ret = heapObjectsToP2Set.get(obj);
		} else {
			ret = new P2Set();
			heapObjectsToP2Set.put(obj, ret);
		}

		ret.join(p2Set);

		return ret;
	}
}
