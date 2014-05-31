package framework.scuba.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Class;
import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.Operator.ALength;
import joeq.Compiler.Quad.Operator.ALoad;
import joeq.Compiler.Quad.Operator.AStore;
import joeq.Compiler.Quad.Operator.Binary;
import joeq.Compiler.Quad.Operator.BoundsCheck;
import joeq.Compiler.Quad.Operator.Branch;
import joeq.Compiler.Quad.Operator.CheckCast;
import joeq.Compiler.Quad.Operator.Getfield;
import joeq.Compiler.Quad.Operator.Getstatic;
import joeq.Compiler.Quad.Operator.InstanceOf;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operator.MemLoad;
import joeq.Compiler.Quad.Operator.MemStore;
import joeq.Compiler.Quad.Operator.Monitor;
import joeq.Compiler.Quad.Operator.Move;
import joeq.Compiler.Quad.Operator.MultiNewArray;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Compiler.Quad.Operator.NullCheck;
import joeq.Compiler.Quad.Operator.Phi;
import joeq.Compiler.Quad.Operator.Putfield;
import joeq.Compiler.Quad.Operator.Putstatic;
import joeq.Compiler.Quad.Operator.Return;
import joeq.Compiler.Quad.Operator.Special;
import joeq.Compiler.Quad.Operator.StoreCheck;
import joeq.Compiler.Quad.Operator.Unary;
import joeq.Compiler.Quad.Operator.ZeroCheck;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.QuadVisitor;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.util.tuple.object.Pair;
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
	protected Map<Pair<AbstractMemLoc, FieldElem>, P2Set> heapObjectsToP2Set;

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
		heapObjectsToP2Set = new HashMap<Pair<AbstractMemLoc, FieldElem>, P2Set>();
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
		// create a pair wrapper for lookup
		Pair<AbstractMemLoc, FieldElem> pair = new Pair<AbstractMemLoc, FieldElem>(
				loc, field);
		if (loc.isArgDerived()) {
			HeapObject hObj = getAbstractMemLoc(loc, field);
			P2Set defaultP2Set = new P2Set(hObj);
			if (heapObjectsToP2Set.containsKey(pair)) {
				return P2SetHelper.join(heapObjectsToP2Set.get(pair),
						defaultP2Set);
			} else {
				return defaultP2Set;
			}
		} else if (loc.isNotArgDerived()) {
			// TODO maybe we need a clone()?
			return heapObjectsToP2Set.get(pair);
		} else {
			assert false : "you have not mark the argument derived marker before lookup!";
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

	QuadVisitor qv = new QuadVisitor.EmptyVisitor() {

		public void visitALenth(Quad stmt) {
		}

		public void visitALoad(Quad stmt) {
			handleALoadStmt(stmt);
			// TODO
		}

		public void visitAStore(Quad stmt) {
			// TODO
		}

		public void visitBinary(Quad stmt) {
		}

		public void visitBoundsCheck(Quad stmt) {
		}

		public void visitBranch(Quad stmt) {
		}

		public void visitCheckCast(Quad stmt) {
		}

		public void visitGetfield(Quad stmt) {
			// TODO
		}

		public void visitGetstatic(Quad stmt) {
			// TODO
		}

		public void visitInstanceOf(Quad stmt) {
		}

		public void visitInvoke(Quad stmt) {
			// TODO
		}

		public void visitMemLoad(Quad stmt) {
		}

		public void visitMemStore(Quad stmt) {
		}

		public void visitMonitor(Quad stmt) {
		}

		public void visitMove(Quad stmt) {
			// TODO
		}

		public void visitMultiNewArray(Quad stmt) {
			// TODO
		}

		public void visitNew(Quad stmt) {
			// TODO
		}

		public void visitNewArray(Quad stmt) {
			// TODO
		}

		public void visitNulCheck(Quad stmt) {
		}

		public void visitPhi(Quad stmt) {
		}

		public void visitPutfield(Quad stmt) {
			// TODO
		}

		public void visitPutstatic(Quad stmt) {
			// TODO
		}

		public void visitReturn(Quad stmt) {
			// TODO
		}

		public void visitSpecial(Quad stmt) {
		}

		public void visitStoreCheck(Quad stmt) {
		}

		public void visitUnary(Quad stmt) {
		}

		public void visitZeroCheck(Quad stmt) {
		}

	};

	protected void handleALoadStmt(Quad stmt) {

	}

	protected void handleAStoreStmt(Quad stmt) {

	}

	protected void handleGetfieldStmt(Quad stmt) {

	}

	protected void handleGetstaticStmt(Quad stmt) {

	}

	protected void handleInvokeStmt(Quad stmt) {

	}

	protected void handleMemLoadStmt(Quad stmt) {

	}

	protected void handleMemStoreStmt(Quad stmt) {

	}

	protected void handleMoveStmt(Quad stmt) {

	}

	protected void handleMultiNewArrayStmt(Quad stmt) {

	}

	protected void handleNewStmt(Quad stmt) {

	}

	protected void handleNewArrayStmt(Quad stmt) {

	}

	protected void handlePutfieldStmt(Quad stmt) {

	}

	protected void handlePutstaticStmt(Quad stmt) {

	}

	protected void handleReturnStmt(Quad stmt) {

	}

	// handleAssgnStmt implements rule (1) in Figure 8 of the paper
	// v1 = v2
	protected void handleAssgnStmt(jq_Class clazz, jq_Method method,
			Register left, VariableType leftVType, Register right,
			VariableType rightVType) {
		StackObject v1 = getAbstractMemLoc(clazz, method, left, leftVType);
		StackObject v2 = getAbstractMemLoc(clazz, method, right, rightVType);
		P2Set p2Setv2 = lookup(v2, new EpsilonFieldElem());
		Pair<AbstractMemLoc, FieldElem> pair = new Pair<AbstractMemLoc, FieldElem>(
				v1, new EpsilonFieldElem());
		weakUpdate(pair, p2Setv2);
	}

	// handleLoadStmt implements rule (2) in Figure 8 of the paper
	// v1 = v2.f
	protected void handleLoadStmt(jq_Class clazz, jq_Method method, Register left,
			VariableType leftVType, Register rightBase, jq_Field rightField,
			VariableType rightBaseVType) {
		StackObject v1 = getAbstractMemLoc(clazz, method, left, leftVType);
		StackObject v2 = getAbstractMemLoc(clazz, method, rightBase,
				rightBaseVType);
		P2Set p2Setv2 = lookup(v2, new EpsilonFieldElem());
		NormalFieldElem f = new NormalFieldElem(rightField);
		P2Set p2Setv2Epsilon = lookup(p2Setv2, f);
		Pair<AbstractMemLoc, FieldElem> pair = new Pair<AbstractMemLoc, FieldElem>(
				v1, new EpsilonFieldElem());
		weakUpdate(pair, p2Setv2Epsilon);
	}

	// handleLoadStmt implements rule (2) in Figure 8 of the paper
	// v1 = A.f, where A is a class and f is a static field
	protected void handleLoadStmt(jq_Class clazz, jq_Method method, Register left,
			VariableType leftVType, jq_Class rightBase, jq_Field rightField) {
		StackObject v1 = getAbstractMemLoc(clazz, method, left, leftVType);
		StaticElem A = getAbstractMemLoc(rightBase);
		P2Set p2SetA = lookup(A, new EpsilonFieldElem());
		NormalFieldElem f = new NormalFieldElem(rightField);
		P2Set p2SetAf = lookup(p2SetA, f);
		Pair<AbstractMemLoc, FieldElem> pair = new Pair<AbstractMemLoc, FieldElem>(
				v1, new EpsilonFieldElem());
		weakUpdate(pair, p2SetAf);
	}

	// handleStoreStmt implements rule (3) in Figure 8 of the paper
	// v1.f = v2
	protected void handleStoreStmt(jq_Class clazz, jq_Method method,
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
			// projP2Set is a new P2Set with copies of the constraints (same
			// content but different constraint instances)
			P2Set projP2Set = P2SetHelper.project(p2Setv2, cst);

			Pair<AbstractMemLoc, FieldElem> pair = new Pair<AbstractMemLoc, FieldElem>(
					obj, f);
			weakUpdate(pair, projP2Set);
		}
	}

	// handleNewStmt implements rule (4) in Figure 8 of the paper
	// v = new T
	protected void handleNewStmt(jq_Class clazz, jq_Method method, Register left,
			VariableType leftVType, jq_Type right, int line) {
		AllocElem allocT = getAbstractMemLoc(clazz, method, right, line);
		StackObject v = getAbstractMemLoc(clazz, method, left, leftVType);
		Pair<AbstractMemLoc, FieldElem> pair = new Pair<AbstractMemLoc, FieldElem>(
				v, new EpsilonFieldElem());
		weakUpdate(pair, new P2Set(allocT, ConstraintManager.genTrue()));
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

	// get the AccessPath object using memLocFactory which generates
	// that if it
	// is not in the factory
	protected AccessPath getAbstractMemLoc(HeapObject base, FieldElem field) {
		AccessPath ret = new AccessPath(base, field);
		if (memLocFactory.containsKey(ret)) {
			return (AccessPath) memLocFactory.get(ret);
		}

		memLocFactory.put(ret, ret);

		return ret;
	}

	protected StackObject getAbstractMemLoc(jq_Class clazz, jq_Method method,
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
	protected AllocElem getAbstractMemLoc(jq_Class clazz, jq_Method method,
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
	protected StaticElem getAbstractMemLoc(jq_Class clazz) {
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

	protected boolean strongUpdate(Pair<AbstractMemLoc, FieldElem> pair,
			P2Set p2Set) {
		heapObjectsToP2Set.put(pair, p2Set);
		return false;
	}

	protected P2Set weakUpdate(Pair<AbstractMemLoc, FieldElem> pair, P2Set p2Set) {
		P2Set ret = null;
		if (heapObjectsToP2Set.containsKey(pair)) {
			ret = heapObjectsToP2Set.get(pair);
		} else {
			ret = new P2Set();
			heapObjectsToP2Set.put(pair, ret);
			// fill the fields of the abstract memory location so that we can
			// conveniently dump the topology of the heap
			pair.val0.addField(pair.val1);
		}

		ret.join(p2Set);

		return ret;
	}
}
