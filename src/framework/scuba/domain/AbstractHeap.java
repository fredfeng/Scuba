package framework.scuba.domain;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Class;
import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.util.tuple.object.Pair;
import framework.scuba.helper.ArgDerivedHelper;
import framework.scuba.helper.ConstraintManager;
import framework.scuba.helper.G;
import framework.scuba.helper.P2SetHelper;

public class AbstractHeap {

	// the method whose heap is represented by this AbstractHeap
	// final protected jq_Method method;

	// heap is a translation from heap, which is used to dump the
	// topology of the abstract heap
	// THIS IS the main data structure to represent the abstract heap
	// every time we refer to a heap, it means this heap topology
	// MAYBE we will not use this? we can use memLocFactory
	// this heap might be used for instantiating the memory locations
	final protected Set<AbstractMemLoc> heap;

	// heap is a mapping described in Figure 7 of the paper
	// mapping: (\pi, f) --> \theta
	// THIS IS just a helper field used to get the P2Set but still very critical
	final protected Map<Pair<AbstractMemLoc, FieldElem>, P2Set> heapObjectsToP2Set;

	// all the abstract memory locations that have been CREATED as instances in
	// the heap, and this is a map mapping key to value which is the key itself
	// this should include the keySet of heap but include more than that (maybe
	// some locations are not used in the program
	final private Map<AbstractMemLoc, AbstractMemLoc> memLocFactory;

	private boolean isChanged = false;

	public static enum VariableType {
		PARAMEMTER, LOCAL_VARIABLE, NULL_POINTER, CONSTANT;
		// currently we are only use the first two
	}

	public AbstractHeap() {
		// this.method = method;
		heap = new HashSet<AbstractMemLoc>();
		heapObjectsToP2Set = new HashMap<Pair<AbstractMemLoc, FieldElem>, P2Set>();
		memLocFactory = new HashMap<AbstractMemLoc, AbstractMemLoc>();
	}

	public void validate() {

		for (AbstractMemLoc loc : memLocFactory.keySet()) {
			// validate there is no illegal heap objects like y.\epsilon where y
			// is just a stack local variable
			if (loc instanceof AccessPath) {
				AbstractMemLoc root = loc.findRoot();
				assert (root instanceof ParamElem)
						|| (root instanceof StaticElem) : "Root of " + loc
						+ " : " + root + " should be a ParamElem or StaticElem";
				assert (loc.isArgDerived()) : "all AccessPath should be arg-derived";
			} else if (loc instanceof StaticElem) {
				AbstractMemLoc root = loc.findRoot();
				assert (loc.equals(root)) : "Root of " + loc + " : " + root
						+ " should be the same as " + loc;
				assert (loc.isArgDerived()) : "StaticElem should be arg-derived";
			} else if (loc instanceof AllocElem) {
				AbstractMemLoc root = loc.findRoot();
				assert (loc.equals(root)) : root + " of " + loc
						+ " should be the same as " + loc;
				assert (loc.isNotArgDerived()) : "AllocElem should NOT be arg-derived";
			} else if (loc instanceof LocalVarElem) {
				AbstractMemLoc root = loc.findRoot();
				assert (loc.equals(root)) : root + " of " + loc
						+ " should be the same as " + loc;
				assert (loc.isNotArgDerived()) : "LocalVarElem should NOT be arg-derived";
			} else if (loc instanceof ParamElem) {
				AbstractMemLoc root = loc.findRoot();
				assert (loc.equals(root)) : root + " of " + loc
						+ " should be the same as " + loc;
				assert (loc.isArgDerived()) : "ParamElem should be arg-derive";
			}
			// all arg-derived instances must be:
			// AccessPath, ParamElem, or StaticElem
			if (loc.isArgDerived()) {
				assert (loc instanceof AccessPath)
						|| (loc instanceof ParamElem)
						|| (loc instanceof StaticElem);
			}

			// validate all the argument derived marker are properly set
			assert (loc.knownArgDerived()) : "we should set the argument derived marker of "
					+ loc
					+ " before putting it into the abstract memory location facltory";
		}
		for (Pair<AbstractMemLoc, FieldElem> pair : heapObjectsToP2Set.keySet()) {
			AbstractMemLoc loc = pair.val0;
			FieldElem f = pair.val1;
			P2Set p2Set = heapObjectsToP2Set.get(pair);
			// validate all abstract memory locations appeared in the key set of
			// heapObjectsToP2Set are either heap objects or stack objects
			assert (loc instanceof HeapObject || loc instanceof StackObject) : loc
					+ " is NOT a proper object";
			// validate the field element is an element of the fields of the
			// corresponding abstract memory location
			assert (loc.getFields().contains(f)) : f
					+ " is NOT in the fields of the abstract memory location "
					+ loc;
			// validate the elements in the key set of the P2Set are all heap
			// objects
			for (HeapObject hobj : p2Set.getHeapObjects()) {
				assert (hobj instanceof HeapObject) : hobj
						+ " should be appeared as a heap object!";
			}
			// validate that all the elements in the key set of the
			// heapObjectsToP2Set are appeared in the key set of memLocFacotry
			assert memLocFactory.containsKey(loc) : loc
					+ " should be contained in the memory location factory!";
			// validate that all the elements in the key set of the p2Set should
			// appear in the key set of memLocFactory
			for (HeapObject hobj : p2Set.getHeapObjects()) {
				assert (memLocFactory.containsKey(hobj)) : hobj
						+ " should be contained in the memory location factory!";
			}
			// validate there is no default edges in the abstract heap
			if (loc.isArgDerived()) {
				HeapObject hObj = getAccessPath(loc, f);
				assert (!p2Set.containsHeapObject(hObj)) : "A default edge exists from "
						+ loc + " with field " + f + " to heap object " + hObj;
			}
		}
	}

	// print the heapObjectsToP2Set mapping in file
	public void dumpHeapMappingToFile(int count) {
		StringBuilder b = new StringBuilder("");
		for (Pair<AbstractMemLoc, FieldElem> pair : heapObjectsToP2Set.keySet()) {
			AbstractMemLoc loc = pair.val0;
			FieldElem f = pair.val1;
			P2Set p2set = heapObjectsToP2Set.get(pair);
			b.append("(" + loc + "," + f + ")\n");
			b.append(p2set + "\n");
		}

		try {
			BufferedWriter bufw = new BufferedWriter(new FileWriter(
					G.dotOutputPath + count + "heapMapping.dot"));
			bufw.write(b.toString());
			bufw.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	public void dumpAllMemLocsToFile(int count) {
		StringBuilder b = new StringBuilder("");
		for (AbstractMemLoc loc : memLocFactory.keySet()) {
			b.append(loc + "\n");
		}

		try {
			BufferedWriter bufw = new BufferedWriter(new FileWriter(
					G.dotOutputPath + count + "/createdLocations.dot"));
			bufw.write(b.toString());
			bufw.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	// draw the heap (without default edges) in the dot file
	public void dumpHeapToFile(int count) {
		StringBuilder b = new StringBuilder("digraph AbstractHeap {\n");
		b.append("  rankdir = LR;\n");

		Set<AbstractMemLoc> allLocs = new HashSet<AbstractMemLoc>();

		for (Pair<AbstractMemLoc, FieldElem> pair : heapObjectsToP2Set.keySet()) {
			allLocs.add(pair.val0);
			for (HeapObject hObj : heapObjectsToP2Set.get(pair)
					.getHeapObjects()) {
				allLocs.add(hObj);
			}
		}

		for (AbstractMemLoc loc : allLocs) {
			if (loc instanceof AccessPath) {
				b.append("  ").append("\"" + loc + "\"");
				b.append(" [shape=circle,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof AllocElem) {
				b.append("  ").append("\"" + loc + "\"");
				b.append(" [shape=rectangle,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof StaticElem) {
				b.append("  ").append("\"" + loc + "\"");
				b.append(" [shape=oval,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof LocalVarElem) {
				b.append("  ").append("\"" + loc + "\"");
				b.append(" [shape=triangle,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof ParamElem) {
				b.append("  ").append("\"" + loc + "\"");
				b.append(" [shape=oval,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else {
				assert false : "wried things! Unknow memory location";
			}
		}

		for (Pair<AbstractMemLoc, FieldElem> pair : heapObjectsToP2Set.keySet()) {
			AbstractMemLoc loc = pair.val0;
			FieldElem f = pair.val1;
			P2Set p2Set = heapObjectsToP2Set.get(pair);
			for (HeapObject hObj : p2Set.getHeapObjects()) {
				Constraint cst = p2Set.getConstraint(hObj);
				b.append("  ").append("\"" + loc + "\"");
				b.append(" -> ").append("\"" + hObj + "\"")
						.append(" [label=\"");
				b.append("(" + f + "," + cst + ")");
				b.append("\"]\n");
			}
		}

		b.append("}\n");

		try {
			BufferedWriter bufw = new BufferedWriter(new FileWriter(
					G.dotOutputPath + "abstractHeap" + count + ".dot"));
			bufw.write(b.toString());
			bufw.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	// draw the heap (with all memory locations created and all default edges
	// used) in the dot file
	public void dumpAllMemLocsHeapToFile(int count) {
		StringBuilder b = new StringBuilder("Digraph allMemLocs {\n");
		b.append("  rankdir = LR;\n");

		for (AbstractMemLoc loc : memLocFactory.keySet()) {
			if (loc instanceof AccessPath) {
				b.append("  ").append("\"" + loc + "\"");
				b.append(" [shape=circle,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof AllocElem) {
				b.append("  ").append("\"" + loc + "\"");
				b.append(" [shape=rectangle,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof StaticElem) {
				b.append("  ").append("\"" + loc + "\"");
				b.append(" [shape=oval,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof LocalVarElem) {
				b.append("  ").append("\"" + loc + "\"");
				b.append(" [shape=triangle,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof ParamElem) {
				b.append("  ").append("\"" + loc + "\"");
				b.append(" [shape=oval,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else {
				assert false : "wried things! Unknown memory location.";
			}
		}

		for (AbstractMemLoc loc : memLocFactory.keySet()) {
			Set<FieldElem> fields = loc.getFields();
			for (FieldElem f : fields) {
				// we should not use the following commented to print the p2set
				// P2Set p2Set = heapObjectsToP2Set.get(getAbstractMemLoc(loc,
				// f));
				// instead we should use the following
				P2Set p2Set = lookup(loc, f);
				assert (p2Set != null) : "get a null p2 set!";

				for (HeapObject obj : p2Set.getHeapObjects()) {
					Constraint cst = p2Set.getConstraint(obj);
					b.append("  ").append("\"" + loc + "\"");
					b.append(" -> ").append("\"" + obj + "\"")
							.append(" [label=\"");
					b.append("(" + f + "," + cst + ")");
					b.append("\"]\n");
				}
			}
		}

		b.append("}\n");

		try {
			BufferedWriter bufw = new BufferedWriter(new FileWriter(
					G.dotOutputPath + "allMemLocs" + count + ".dot"));
			bufw.write(b.toString());
			bufw.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	// field look-up for location which is described in definition 7 of the
	// paper
	public P2Set lookup(AbstractMemLoc loc, FieldElem field) {
		// create a pair wrapper for lookup
		Pair<AbstractMemLoc, FieldElem> pair = new Pair<AbstractMemLoc, FieldElem>(
				loc, field);
		if (loc.isArgDerived()) {
			// get the default target given the memory location and the field
			AccessPath defaultTarget = getDefaultTarget(loc, field);
			// always find the default p2set of (loc, field)
			P2Set defaultP2Set = new P2Set(defaultTarget);
			// return the p2set always including the default p2set
			if (heapObjectsToP2Set.containsKey(pair)) {
				return P2SetHelper.join(heapObjectsToP2Set.get(pair),
						defaultP2Set);
			} else {
				return defaultP2Set;
			}
		} else if (loc.isNotArgDerived()) {
			// TODO maybe we need a clone()?
			// it is possible to have null pointers
			P2Set ret = heapObjectsToP2Set.get(pair);
			if (ret != null) {
				// if the field of this memory does point to something, return
				// that memory location
				return ret;
			} else {
				// if the field of this memory does NOT point to anything, just
				// return an empty P2Set
				return new P2Set();
			}
		} else {
			assert false : "you have not mark the argument derived marker before lookup!";
		}
		assert false : "you have not mark the argument derived marker before lookup!";
		return null;
	}

	// generalized field look-up for location which is described in definition
	// 10 of the paper
	public P2Set lookup(P2Set p2Set, FieldElem field) {
		// it is possible to have null pointers that are dereferenced if we
		// think about reflection or some native methods which we cannot
		// retrieve the active bodies

		// we just return the empty p2 set if we want to dereference a null
		// pointer
		P2Set ret = new P2Set();

		for (HeapObject obj : p2Set.getHeapObjects()) {
			Constraint cst = p2Set.getConstraint(obj);

			P2Set tgt = lookup(obj, field);
			assert (p2Set != null) : "get a null p2 set!";
			P2Set projP2Set = P2SetHelper.project(tgt, cst);
			ret.join(projP2Set);
		}

		return ret;
	}

	// handleAssignStmt implements rule (1) in Figure 8 of the paper
	// v1 = v2
	// v1: parameter / local
	// v2: parameter / local (for SSA, only local is possible)
	// TODO we loose the constraint to allow LHS to be ParamElem (Not SSA)
	protected boolean handleAssignStmt(jq_Class clazz, jq_Method method,
			Register left, VariableType leftVType, Register right,
			VariableType rightVType) {

		assert (leftVType == VariableType.LOCAL_VARIABLE || leftVType == VariableType.PARAMEMTER) : ""
				+ "for Assign stmt, LHS must be LocalElem (or ParamElem, we HAVE NOT fully fixed SSA";
		assert (rightVType == VariableType.LOCAL_VARIABLE || rightVType == VariableType.PARAMEMTER) : ""
				+ "for Assign stmt, RHS must be LocalElem or ParamElem!";

		// generates StackObject (either ParamElem or LocalVarElem)
		StackObject v1 = null, v2 = null;

		// generate the mem loc for LHS
		if (leftVType == VariableType.PARAMEMTER) {
			// assert false : "for assign stmt, LHS must be LocalElem";
			System.err.println("ParamElem appears as LHS in the Assign stmt");
			v1 = getParamElem(clazz, method, left);
		} else if (leftVType == VariableType.LOCAL_VARIABLE) {
			v1 = getLocalVarElem(clazz, method, left);
		} else {
			assert false : "wried thing! For assign stmt, LHS must be LocalElem!";
		}
		assert (v1 != null) : "v1 is null!";

		// generate the mem loc for RHS
		if (rightVType == VariableType.PARAMEMTER) {
			v2 = getParamElem(clazz, method, right);
		} else if (rightVType == VariableType.LOCAL_VARIABLE) {
			v2 = getLocalVarElem(clazz, method, right);
		} else {
			assert false : "for assign stmt, RHS must be LocalElem or ParamElem!";
		}
		assert (v2 != null) : "v2 is null!";

		assert v1.knownArgDerived() : "we should set arg-derived marker for v1!";
		assert v2.knownArgDerived() : "we should set arg-derived marker for v2!";

		P2Set p2Setv2 = lookup(v2, EpsilonFieldElem.getEpsilonFieldElem());
		assert (p2Setv2 != null) : "get a null p2 set!";

		Pair<AbstractMemLoc, FieldElem> pair = new Pair<AbstractMemLoc, FieldElem>(
				v1, EpsilonFieldElem.getEpsilonFieldElem());

		return weakUpdate(pair, p2Setv2);
	}

	// this method is just a helper method for handling array allocations
	protected boolean handleLoadStmt(ArrayAllocElem left, IndexFieldElem index,
			AllocElem right) {

		Pair<AbstractMemLoc, FieldElem> pair = new Pair<AbstractMemLoc, FieldElem>(
				left, index);
		P2Set p2Set = new P2Set(right, ConstraintManager.genTrue());

		assert !heapObjectsToP2Set.containsKey(pair) : "we cannot re-put ArrayAllocElem into the map!";

		left.fields.add(index);
		heap.add(left);
		heap.add(right);
		heapObjectsToP2Set.put(pair, p2Set);

		return true;
	}

	// handleLoadStmt implements rule (2) in Figure 8 of the paper
	// v1 = v2.f
	// v1: parameter / local (for SSA, only local)
	// v2: parameter / local
	// f: non-static field
	protected boolean handleLoadStmt(jq_Class clazz, jq_Method method,
			Register left, VariableType leftVType, Register rightBase,
			jq_Field rightField, VariableType rightBaseVType) {

		assert (leftVType == VariableType.LOCAL_VARIABLE) : "for non-static load stmt, LHS must be LocalElem";
		assert (rightBaseVType == VariableType.LOCAL_VARIABLE)
				|| (rightBaseVType == VariableType.PARAMEMTER) : ""
				+ "for non-static stmt, RHS BASE must be LocalElem or ParamElem!";

		// generates StackObject (either ParamElem or LocalVarElem)
		StackObject v1 = null, v2 = null;

		// generate the mem loc for LHS
		if (leftVType == VariableType.PARAMEMTER) {
			assert false : "for non-static load stmt, LHS must be LocalElem";
		} else if (leftVType == VariableType.LOCAL_VARIABLE) {
			v1 = getLocalVarElem(clazz, method, left);
		} else {
			assert false : "wried thing! For non-static load stmt, LHS must be LocalElem!";
		}
		assert (v1 != null) : "v1 is null!";

		// generate the mem loc for RHS base
		if (rightBaseVType == VariableType.PARAMEMTER) {
			v2 = getParamElem(clazz, method, rightBase);
		} else if (leftVType == VariableType.LOCAL_VARIABLE) {
			v2 = getLocalVarElem(clazz, method, rightBase);
		} else {
			assert false : "for non-static load stmt, RHS BASE must be LocalElem or ParamElem!";
		}
		assert (v2 != null) : "v2 is null!";

		assert v1.knownArgDerived() : "we should set the arg-derived marker when creating v1";
		assert v2.knownArgDerived() : "we should set the arg-derived marker when creating v2";

		P2Set p2Setv2 = lookup(v2, EpsilonFieldElem.getEpsilonFieldElem());
		assert (p2Setv2 != null) : "get a null p2 set!";

		NormalFieldElem f = new NormalFieldElem(rightField);
		P2Set p2Setv2f = lookup(p2Setv2, f);
		assert (p2Setv2f != null) : "get a null p2 set!";

		Pair<AbstractMemLoc, FieldElem> pair = new Pair<AbstractMemLoc, FieldElem>(
				v1, EpsilonFieldElem.getEpsilonFieldElem());
		return weakUpdate(pair, p2Setv2f);
	}

	// handleLoadStmt implements rule (2) in Figure 8 of the paper
	// v1 = A.f, where A is a class and f is a static field
	// v1: parameter / local (for SSA, only local_
	// A: jq_Class
	// f: a static field declared in class A
	// although this is a load stmt, we regard it as an Assign stmt by following
	// v1 = (A.f) where A.f is just a stack object
	protected boolean handleStatLoadStmt(jq_Class clazz, jq_Method method,
			Register left, VariableType leftVType, jq_Class rightBase,
			jq_Field rightField) {

		assert (leftVType == VariableType.LOCAL_VARIABLE) : "for static load stmt, LHS must be a local!";

		StackObject v1 = null;
		// generate the mem loc for LHS
		if (leftVType == VariableType.PARAMEMTER) {
			assert false : "for static load stmt, LHS must be LocalElem";
		} else if (leftVType == VariableType.LOCAL_VARIABLE) {
			v1 = getLocalVarElem(clazz, method, left);
		} else {
			assert false : "wried thing! For static load stmt, LHS must be LocalElem!";
		}
		assert (v1 != null) : "v1 is null!";

		// generate the mem loc for RHS base
		StaticElem v2 = getStaticElem(rightBase, rightField);
		assert (v2 != null) : "v2 is null!";

		assert v1.knownArgDerived() : "we should set the arg-derived marker when creating v1";
		assert v2.knownArgDerived() : "we should set the arg-derived marker when creating v2";

		P2Set p2Setv2 = lookup(v2, EpsilonFieldElem.getEpsilonFieldElem());
		assert (p2Setv2 != null) : "get a null p2 set!";

		Pair<AbstractMemLoc, FieldElem> pair = new Pair<AbstractMemLoc, FieldElem>(
				v1, EpsilonFieldElem.getEpsilonFieldElem());

		return weakUpdate(pair, p2Setv2);
	}

	// handleStoreStmt implements rule (3) in Figure 8 of the paper
	// v1.f = v2
	protected boolean handleStoreStmt(jq_Class clazz, jq_Method method,
			Register leftBase, VariableType leftBaseVType, jq_Field leftField,
			Register right, VariableType rightVType) {

		assert (rightVType == VariableType.PARAMEMTER)
				|| (rightVType == VariableType.LOCAL_VARIABLE) : "we are only considering local"
				+ " variables and parameters as RHS Base";
		assert (leftBaseVType == VariableType.PARAMEMTER)
				|| (leftBaseVType == VariableType.LOCAL_VARIABLE) : "we are only considering local"
				+ " variables and parameters as LHS";

		// generates StackObject (either ParamElem or LocalVarElem)
		StackObject v1 = null, v2 = null;

		// generate the mem loc for LHS
		if (leftBaseVType == VariableType.PARAMEMTER) {
			v1 = getParamElem(clazz, method, leftBase);
		} else if (leftBaseVType == VariableType.LOCAL_VARIABLE) {
			v1 = getLocalVarElem(clazz, method, leftBase);
		} else {
			assert false : "wried thing! For non-static store load stmt,"
					+ " LHS Base must be LocalElem or ParamElem!";
		}
		assert (v1 != null) : "v1 is null!";

		// generate the mem loc for RHS base
		if (rightVType == VariableType.PARAMEMTER) {
			v2 = getParamElem(clazz, method, right);
		} else if (rightVType == VariableType.LOCAL_VARIABLE) {
			v2 = getLocalVarElem(clazz, method, right);
		} else {
			assert false : "for non-static store stmt, RHS must be LocalElem or ParamElem!";
		}
		assert (v2 != null) : "v2 is null!";

		assert v1.knownArgDerived() : "we should set the arg-derived marker when creating v1";
		assert v2.knownArgDerived() : "we should set the arg-derived marker when creating v2";

		boolean ret = false;

		P2Set p2Setv1 = lookup(v1, EpsilonFieldElem.getEpsilonFieldElem());
		P2Set p2Setv2 = lookup(v2, EpsilonFieldElem.getEpsilonFieldElem());
		assert (p2Setv1 != null) : "get a null p2 set!";
		assert (p2Setv2 != null) : "get a null p2 set!";

		NormalFieldElem f = new NormalFieldElem(leftField);
		for (HeapObject obj : p2Setv1.getHeapObjects()) {
			Constraint cst = p2Setv1.getConstraint(obj);
			// projP2Set is a new P2Set with copies of the constraints (same
			// content but different constraint instances)
			P2Set projP2Set = P2SetHelper.project(p2Setv2, cst);

			Pair<AbstractMemLoc, FieldElem> pair = new Pair<AbstractMemLoc, FieldElem>(
					obj, f);

			ret = weakUpdate(pair, projP2Set) | ret;
		}

		return ret;
	}

	// handleStoreStmt implements rule (3) in Figure 8 of the paper
	// A.f = v2
	// A: jq_Class
	// f: a static field declared in class A
	// v2: local / parameter
	// although this is a store stmt, we regard it as an Assign stmt by
	// (A.f) = v2 where (A.f) is just a stack object (StaticElem)
	protected boolean handleStaticStoreStmt(jq_Class clazz, jq_Method method,
			jq_Class leftBase, jq_Field leftField, Register right,
			VariableType rightVType) {

		assert (rightVType == VariableType.PARAMEMTER)
				|| (rightVType == VariableType.LOCAL_VARIABLE) : "we are only considering local"
				+ " variables and parameters as RHS in static store stmt";

		// generate the mem loc for LHS Base
		StaticElem v1 = getStaticElem(leftBase, leftField);
		assert (v1 != null) : "v1 is null!";

		StackObject v2 = null;
		// generate the mem loc for RHS
		if (rightVType == VariableType.PARAMEMTER) {
			v2 = getParamElem(clazz, method, right);
		} else if (rightVType == VariableType.LOCAL_VARIABLE) {
			v2 = getLocalVarElem(clazz, method, right);
		} else {
			assert false : "for static store stmt, RHS must be LocalElem or ParamElem!";
		}
		assert (v2 != null) : "v2 is null!";

		assert v1.knownArgDerived() : "we should set the arg-derived marker when creating v1";
		assert v2.knownArgDerived() : "we should set the arg-derived marker when creating v2";

		P2Set p2Setv2 = lookup(v2, EpsilonFieldElem.getEpsilonFieldElem());
		assert (p2Setv2 != null) : "get a null p2 set!";

		Pair<AbstractMemLoc, FieldElem> pair = new Pair<AbstractMemLoc, FieldElem>(
				v1, EpsilonFieldElem.getEpsilonFieldElem());

		return weakUpdate(pair, p2Setv2);
	}

	// handleNewStmt implements rule (4) in Figure 8 of the paper
	// v = new T
	protected boolean handleNewStmt(jq_Class clazz, jq_Method method,
			Register left, VariableType leftVType, jq_Type right, int line) {

		assert (leftVType == VariableType.LOCAL_VARIABLE) : "LHS of a new stmt must be a local variable!";

		// generate the allocElem for RHS
		AllocElem allocT = getAllocElem(clazz, method, right, line);
		assert (allocT != null) : "allocT is null!";

		LocalVarElem v = null;
		// generate the localVarElem for LHS
		if (leftVType == VariableType.LOCAL_VARIABLE) {
			v = getLocalVarElem(clazz, method, left);
		} else {
			assert false : "LHS of a new stmt must be a local variable!";
		}
		assert (v != null) : "v is null!";

		assert allocT.knownArgDerived() : "we should set the arg-derived marker when creating allocT";
		assert v.knownArgDerived() : "we should set the arg-derived marker when creating v";

		Pair<AbstractMemLoc, FieldElem> pair = new Pair<AbstractMemLoc, FieldElem>(
				v, EpsilonFieldElem.getEpsilonFieldElem());

		return weakUpdate(pair, new P2Set(allocT, ConstraintManager.genTrue()));
	}

	protected boolean handleNewArrayStmt(jq_Class clazz, jq_Method method,
			Register left, VariableType leftVType, jq_Type right, int line) {
		return handleMultiNewArrayStmt(clazz, method, left, leftVType, right,
				1, line);
	}

	// handle multi-new stmt, e.g. X x1 = new X[1][2][3]
	// dim is the dimension of this array, dim >= 1, where dim = 1 is the
	// easiest case like X x1 = new X[10]
	protected boolean handleMultiNewArrayStmt(jq_Class clazz, jq_Method method,
			Register left, VariableType leftVType, jq_Type right, int dim,
			int line) {
		boolean ret = false;

		assert (leftVType == VariableType.LOCAL_VARIABLE) : "LHS of a new stmt must be a local variable!";

		LocalVarElem v = null;
		// generate the localVarElem for LHS
		if (leftVType == VariableType.LOCAL_VARIABLE) {
			v = getLocalVarElem(clazz, method, left);
		} else {
			assert false : "LHS of a new stmt must be a local variable!";
		}
		assert (v != null) : "v is null!";
		// generate the ArrayAllocElem for RHS
		ArrayAllocElem allocT = getArrayAllocElem(clazz, method, right, dim,
				line);

		assert allocT.knownArgDerived() : "we should set the arg-derived marker when creating allocT";
		assert v.knownArgDerived() : "we should set the arg-derived marker when creating v";

		Pair<AbstractMemLoc, FieldElem> pair = new Pair<AbstractMemLoc, FieldElem>(
				v, EpsilonFieldElem.getEpsilonFieldElem());
		// update the LHS's P2Set weakly
		ret = weakUpdate(pair, new P2Set(allocT, ConstraintManager.genTrue()))
				| ret;

		// handling fields of the ArrayAllocElem for multi-array with dim > 1
		for (int i = dim; i >= 2; i--) {
			ArrayAllocElem leftAllocT = getArrayAllocElem(clazz, method, right,
					i, line);
			ArrayAllocElem rightAllocT = getArrayAllocElem(clazz, method,
					right, i - 1, line);
			handleLoadStmt(leftAllocT, IndexFieldElem.getIndexFieldElem(),
					rightAllocT);
		}

		// handling fields of the ArrayAllocElem for array with dim = 1
		ArrayAllocElem leftAllocT = getArrayAllocElem(clazz, method, right, 1,
				line);
		AllocElem rightAllocT = getAllocElem(clazz, method, right, line);
		ret = handleLoadStmt(leftAllocT, IndexFieldElem.getIndexFieldElem(),
				rightAllocT) | ret;

		return ret;
	}

	// given a base and a field, get the corresponding AccessPath
	// if it is not in the factory, create, put into factory and return
	// otherwise, return the one in the factory
	protected AccessPath getAccessPath(AbstractMemLoc base, FieldElem field) {

		AccessPath ret = null;

		if (base instanceof HeapObject) {
			assert (base instanceof AccessPath || base instanceof AllocElem) : "a heap object can "
					+ "only be either an AccessPath or AllocElem!";
			ret = getAccessPath(((HeapObject) base), field);
		} else if (base instanceof ParamElem) {
			ret = getAccessPath(((ParamElem) base), field);
		} else if (base instanceof StaticElem) {
			ret = getAccessPath(((StaticElem) base), field);
		} else if (base instanceof LocalVarElem) {
			assert false : "you can NOT dereference a LocalVarElem " + base;
		} else {
			assert false : "wried things! Unkown memory location.";
		}

		return ret;
	}

	// helper methods for getAccessPath
	// get the AccessPath whose base is ParamElem
	private AccessPath getAccessPath(ParamElem base, FieldElem field) {

		AccessPath ret = new AccessPath(base, field);
		if (memLocFactory.containsKey(ret)) {
			return (AccessPath) memLocFactory.get(ret);
		}

		ArgDerivedHelper.markArgDerived(ret);
		memLocFactory.put(ret, ret);

		return ret;
	}

	// get the AccessPath whose base is StaticElem
	private AccessPath getAccessPath(StaticElem base, FieldElem field) {

		AccessPath ret = new AccessPath(base, field);
		if (memLocFactory.containsKey(ret)) {
			return (AccessPath) memLocFactory.get(ret);
		}

		ArgDerivedHelper.markArgDerived(ret);
		memLocFactory.put(ret, ret);

		return ret;
	}

	// get the AccessPath whose base is HeapObject
	private AccessPath getAccessPath(HeapObject base, FieldElem field) {

		AccessPath ret = new AccessPath(base, field);
		if (memLocFactory.containsKey(ret)) {
			return (AccessPath) memLocFactory.get(ret);
		}

		ArgDerivedHelper.markArgDerived(ret);
		memLocFactory.put(ret, ret);

		return ret;
	}

	// get the StaticElem in the mem loc factory by an StaticElem with the
	// same content (we want to use exactly the same instance)
	protected StaticElem getStaticElem(StaticElem other) {

		if (memLocFactory.containsKey(other)) {
			return (StaticElem) memLocFactory.get(other);
		}

		ArgDerivedHelper.markArgDerived(other);
		memLocFactory.put(other, other);

		return other;
	}

	// get the ParamElem in the mem loc factory by an ParamElem with the
	// same content (we want to use exactly the same instance)
	protected ParamElem getParamElem(ParamElem other) {

		if (memLocFactory.containsKey(other)) {
			return (ParamElem) memLocFactory.get(other);
		}

		ArgDerivedHelper.markArgDerived(other);
		memLocFactory.put(other, other);

		return other;
	}

	// get the LocalVarElem in the mem loc factory by an LocalVarElem with the
	// same content (we want to use exactly the same instance)
	protected LocalVarElem getLocalElem(LocalVarElem other) {

		if (memLocFactory.containsKey(other)) {
			return (LocalVarElem) memLocFactory.get(other);
		}

		ArgDerivedHelper.markArgDerived(other);
		memLocFactory.put(other, other);

		return other;
	}

	// get the AllocElem in the mem loc factory by an AllocElem with the same
	// content (we want to use exactly the same instance)
	protected AllocElem getAllocElem(AllocElem other) {

		if (memLocFactory.containsKey(other)) {
			return (AllocElem) memLocFactory.get(other);
		}

		ArgDerivedHelper.markArgDerived(other);
		memLocFactory.put(other, other);

		return other;
	}

	// get the AccessPath in the mem loc factory by an AccessPath with the same
	// content (we want to use exactly the same instance)
	protected AccessPath getAccessPath(AccessPath other) {

		if (memLocFactory.containsKey(other)) {
			return (AccessPath) memLocFactory.get(other);
		}

		ArgDerivedHelper.markArgDerived(other);
		memLocFactory.put(other, other);

		return other;
	}

	// get the AllocElem given the declaring class, declaring method and the
	// corresponding type and the line number
	protected AllocElem getAllocElem(jq_Class clazz, jq_Method method,
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

	protected ArrayAllocElem getArrayAllocElem(jq_Class clazz,
			jq_Method method, jq_Type type, int dim, int line) {
		Context context = new Context(new ProgramPoint(clazz, method, line));
		// create an AllocElem wrapper
		ArrayAllocElem ret = new ArrayAllocElem(new Alloc(type), context, dim);
		// try to look up this wrapper in the memory location factory
		if (memLocFactory.containsKey(ret)) {
			return (ArrayAllocElem) memLocFactory.get(ret);
		}
		// not found in the factory
		// every time generating a memory location, do this marking
		ArgDerivedHelper.markArgDerived(ret);
		memLocFactory.put(ret, ret);

		return ret;
	}

	// get the LocalVarElem given the declaring class, declaring method, and the
	// corresponding register in the IR
	protected LocalVarElem getLocalVarElem(jq_Class clazz, jq_Method method,
			Register variable) {
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
	}

	// get the ParamElem given the declaring class, declaring method and the
	// corresponding register in the IR
	protected ParamElem getParamElem(jq_Class clazz, jq_Method method,
			Register parameter) {
		// create a wrapper
		ParamElem ret = new ParamElem(clazz, method, parameter);
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

	// get the StaticElem given the declaring class and the corresponding field
	// in the IR
	protected StaticElem getStaticElem(jq_Class clazz, jq_Field field) {
		// create a wrapper
		StaticElem ret = new StaticElem(clazz, field);
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

	// actually we do not use this method for Scuba project
	// because we use Chord in SSA form so that it is not necessary to do strong
	// update for local variables (they will have different names if assigned
	// more than once), also we do weak update for heap objects and the local
	// variables in SCC (e.g loops)
	protected boolean strongUpdate(Pair<AbstractMemLoc, FieldElem> pair,
			P2Set p2Set) {
		assert (pair.val0 instanceof StackObject) : "Only stack objects can do strong update!";
		assert (pair.val1 instanceof EpsilonFieldElem) : "Only stack objects with epsilon field"
				+ " can do strong update!";
		pair.val0.addField(pair.val1);
		heapObjectsToP2Set.put(pair, p2Set);
		return false;
	}

	// get the default target of memory location loc and the field
	// we can ONLY call this method when ensuring loc is arg-derived
	protected AccessPath getDefaultTarget(AbstractMemLoc loc, FieldElem field) {
		assert (loc instanceof AccessPath) || (loc instanceof StaticElem)
				|| (loc instanceof ParamElem);
		assert loc.knownArgDerived() : "we must first set the argument derived marker "
				+ "before using the mem loc!";
		assert loc.isArgDerived() : "you can ONLY get the default target for an arg derived mem loc!";

		AccessPath ret = null;
		if (loc.isArgDerived()) {
			if (loc.hasFieldSelector(field)) {
				assert (loc instanceof AccessPath) : "only AccessPath has field selectors!";
				// only AccessPath has field selectors
				AccessPath path = ((AccessPath) loc).getPrefix(field);
				ret = getAccessPath(path);
			} else {
				ret = getAccessPath(loc, field);
			}
		} else {
			assert false : "you can NOT get the default target for a non-arg derived mem loc!";
		}
		assert (ret != null) : "you can NOT get the default target for a non-arg derived mem loc!";
		return ret;
	}

	// TODO
	// still need to check whether this returned boolean value is correct
	protected boolean weakUpdate(Pair<AbstractMemLoc, FieldElem> pair,
			P2Set p2Set) {
		boolean ret = false;
		// first clean up the default targets in the p2set given the pair
		cleanup(p2Set, pair);

		// if the new p2Set is empty then return immediately
		if (!p2Set.isEmpty())
			// fill the fields of the abstract memory location so that we can
			// conveniently dump the topology of the heap
			pair.val0.addField(pair.val1);
		else
			return ret;

		// then get the current heap given the memory location and the field
		P2Set currentP2Set = heapObjectsToP2Set.get(pair);
		if (currentP2Set == null) {
			currentP2Set = new P2Set();
			heapObjectsToP2Set.put(pair, currentP2Set);
		}

		// update the locations in the real heap graph
		// currently we are not using this feature
		// maybe we will use this for instantiating the memory locations
		heap.add(pair.val0);
		heap.addAll(p2Set.getHeapObjects());

		// the KEY for weak update
		ret = currentP2Set.join(p2Set);

		return ret;
	}

	protected void cleanup(P2Set p2Set, Pair<AbstractMemLoc, FieldElem> pair) {
		// if (p2Set == null)
		// return;
		assert (p2Set != null) : "p2 set is null when doing the cleanup.";

		AbstractMemLoc loc = pair.val0;
		FieldElem f = pair.val1;

		if (!loc.isArgDerived())
			return;

		AccessPath defaultTarget = getDefaultTarget(loc, f);

		if (p2Set.containsHeapObject(defaultTarget)) {
			p2Set.remove(defaultTarget);
		}
		// do we need to check whether after removing the p2set is empty so that
		// we can directly remove that whole entry?
		// TODO
	}

	public Map<Pair<AbstractMemLoc, FieldElem>, P2Set> getHeap() {
		return this.heapObjectsToP2Set;
	}

	public Set<AbstractMemLoc> getAllMemLocs() {
		return this.memLocFactory.keySet();
	}

	// check whether some abstract memory location is contained in the factory
	public boolean hasCreated(AbstractMemLoc loc) {
		return memLocFactory.containsKey(loc);
	}

	// check whether some abstract memory location is in the heap
	public boolean isInHeap(AbstractMemLoc loc) {
		return heap.contains(loc);
	}

	// mark whether the heap has changed.
	public void markChanged(boolean flag) {
		this.isChanged = flag;
	}

	public boolean isChanged() {
		return isChanged;
	}
}
