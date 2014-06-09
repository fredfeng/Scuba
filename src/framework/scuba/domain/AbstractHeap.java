package framework.scuba.domain;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import joeq.Class.jq_Class;
import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.util.tuple.object.Pair;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.enumerations.Z3_lbool;

import framework.scuba.helper.ArgDerivedHelper;
import framework.scuba.helper.ConstraintManager;
import framework.scuba.helper.G;
import framework.scuba.helper.P2SetHelper;

public class AbstractHeap {

	// the method whose heap is represented by this AbstractHeap
	final protected jq_Method method;

	// heap is a translation from heap, which is used to dump the
	// topology of the abstract heap
	// THIS IS the main data structure to represent the abstract heap
	// every time we refer to a heap, it means this heap topology
	// MAYBE we will not use this? we can use memLocFactory
	// this heap might be used for instantiating the memory locations
	protected final Set<AbstractMemLoc> heap;

	// heap is a mapping described in Figure 7 of the paper
	// mapping: (\pi, f) --> \theta
	// THIS IS just a helper field used to get the P2Set but still very critical
	protected final Map<Pair<AbstractMemLoc, FieldElem>, P2Set> heapObjectsToP2Set;

	// all the abstract memory locations that have been CREATED as instances in
	// the heap, and this is a map mapping key to value which is the key itself
	// this should include the keySet of heap but include more than that (maybe
	// some locations are not used in the program
	private final Map<AbstractMemLoc, AbstractMemLoc> memLocFactory;

	private boolean isChanged = false;

	// this map records the sequence that the edges are added into the heap
	// it contains a LOT of information such as:
	// (1) the sequence the edges are added (edges with the same number are
	// added at the same time and we do not care about the sequence to
	// instantiate them, i.e. we can instantiate them in any order)
	// (2) the edges associated with larger number depends on the heap modified
	// by (all) the edges that are marked with smaller number, so they should be
	// instantiated after all edges with smaller numbers are finished
	// (3) the edges that are in the same SCC will be associated with the same
	// number and set the inSCC to be true (which means the above two are
	// false), and they should be instantiated until a fixed point
	protected Map<Numbering, Set<HeapEdge>> edgeSeq = new TreeMap<Numbering, Set<HeapEdge>>(
			new Comparator<Numbering>() {
				public int compare(Numbering first, Numbering second) {
					if (first.getNumber() < second.getNumber()) {
						return -1;
					} else if (first.getNumber() == second.getNumber()) {
						return 0;
					} else {
						return 1;
					}
				}
			});

	// factory of edges in the heap
	protected final Map<HeapEdge, HeapEdge> edgesFactory = new HashMap<HeapEdge, HeapEdge>();

	// a reverse mapping used for dumping the numbering with the heap
	private Map<HeapEdge, Set<Numbering>> reverseEdgeSeq = new HashMap<HeapEdge, Set<Numbering>>();
	// a numbering factory
	private Map<Numbering, Numbering> numberingFactory = new HashMap<Numbering, Numbering>();

	protected int maxNumber = 0;

	public static enum VariableType {
		PARAMEMTER, LOCAL_VARIABLE, ARRAY_BASE, NULL_POINTER, CONSTANT;
		// currently we are only use the first three
	}

	public AbstractHeap(jq_Method method) {
		// this.method = method;
		heap = new HashSet<AbstractMemLoc>();
		heapObjectsToP2Set = new HashMap<Pair<AbstractMemLoc, FieldElem>, P2Set>();
		memLocFactory = new HashMap<AbstractMemLoc, AbstractMemLoc>();
		this.method = method;
	}

	public jq_Method getMethod() {
		return this.method;
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
			} else if (loc instanceof RetElem) {
				AbstractMemLoc root = loc.findRoot();
				assert (loc.equals(root)) : root + " of " + loc
						+ " should be the same as " + loc;
				assert (loc.isNotArgDerived()) : "RetElem should NOT be arg-derived";
			} else {
				assert false : "wried things happen!";
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
			// because of instantiating, the locations in the caller's heap
			// might be produced by the callee's mem loc factory
			// assert memLocFactory.containsKey(loc) : loc
			// + " should be contained in the memory location factory!";
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

	public void dumpHeapNumberingMap(String count) {
		StringBuilder b = new StringBuilder("");
		for (Numbering n : edgeSeq.keySet()) {
			b.append("-------------------------------------------------\n");
			b.append(n + "\n");
			b.append(edgeSeq.get(n) + "\n");
			b.append("-------------------------------------------------\n");
		}
		try {
			BufferedWriter bufw = new BufferedWriter(new FileWriter(
					G.dotOutputPath + "NumberingMapping" + count));
			bufw.write(b.toString());
			bufw.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	public void dumpHeapNumberingToFile(String count) {
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
				b.append("  ").append("\"" + loc.dump() + "\"");
				b.append(" [shape=circle,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof AllocElem) {
				b.append("  ").append("\"" + loc.dump() + "\"");
				b.append(" [shape=rectangle,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof StaticElem) {
				b.append("  ").append("\"" + loc.dump() + "\"");
				b.append(" [shape=oval,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof LocalVarElem) {
				b.append("  ").append("\"" + loc.dump() + "\"");
				b.append(" [shape=triangle,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof ParamElem) {
				b.append("  ").append("\"" + loc.dump() + "\"");
				b.append(" [shape=oval,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof RetElem) {
				b.append("  ").append("\"" + loc.dump() + "\"");
				b.append(" [shape=diamond,label=\"");
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
				BoolExpr cst = p2Set.getConstraint(hObj);
				Set<Numbering> nums = reverseEdgeSeq.get(getHeapEdge(loc, hObj,
						f));
				b.append("  ").append("\"" + loc.dump() + "\"");
				b.append(" -> ").append("\"" + hObj.dump() + "\"")
						.append(" [label=\"");
				b.append("(" + f + "," + cst + ")");
				b.append(" <");
				for (Numbering n : nums) {
					b.append("(" + n.getNumber() + "," + n.isInSCC() + ")");
				}
				b.append(">");
				b.append("\"]\n");
			}
		}

		b.append("}\n");

		try {
			BufferedWriter bufw = new BufferedWriter(new FileWriter(
					G.dotOutputPath + "Numbering" + count + ".dot"));
			bufw.write(b.toString());
			bufw.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	// print the heapObjectsToP2Set mapping in file
	public void dumpHeapMappingToFile(String count) {
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
					G.dotOutputPath + count + "createdLocations.dot"));
			bufw.write(b.toString());
			bufw.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	// draw the heap (without default edges) in the dot file
	public void dumpHeapToFile(String count) {
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
				b.append("  ").append("\"" + loc.dump() + "\"");
				b.append(" [shape=circle,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof AllocElem) {
				b.append("  ").append("\"" + loc.dump() + "\"");
				b.append(" [shape=rectangle,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof StaticElem) {
				b.append("  ").append("\"" + loc.dump() + "\"");
				b.append(" [shape=oval,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof LocalVarElem) {
				b.append("  ").append("\"" + loc.dump() + "\"");
				b.append(" [shape=triangle,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof ParamElem) {
				b.append("  ").append("\"" + loc.dump() + "\"");
				b.append(" [shape=oval,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof RetElem) {
				b.append("  ").append("\"" + loc.dump() + "\"");
				b.append(" [shape=diamond,label=\"");
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
				BoolExpr cst = p2Set.getConstraint(hObj);
				b.append("  ").append("\"" + loc.dump() + "\"");
				b.append(" -> ").append("\"" + hObj.dump() + "\"")
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
	public void dumpAllMemLocsHeapToFile(String count) {
		StringBuilder b = new StringBuilder("Digraph allMemLocs {\n");
		b.append("  rankdir = LR;\n");

		for (AbstractMemLoc loc : memLocFactory.keySet()) {
			if (loc instanceof AccessPath) {
				b.append("  ").append("\"" + loc.dump() + "\"");
				b.append(" [shape=circle,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof AllocElem) {
				b.append("  ").append("\"" + loc.dump() + "\"");
				b.append(" [shape=rectangle,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof StaticElem) {
				b.append("  ").append("\"" + loc.dump() + "\"");
				b.append(" [shape=oval,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof LocalVarElem) {
				b.append("  ").append("\"" + loc.dump() + "\"");
				b.append(" [shape=triangle,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof ParamElem) {
				b.append("  ").append("\"" + loc.dump() + "\"");
				b.append(" [shape=oval,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof RetElem) {
				b.append("  ").append("\"" + loc.dump() + "\"");
				b.append(" [shape=diamond,label=\"");
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
					BoolExpr cst = p2Set.getConstraint(obj);
					b.append("  ").append("\"" + loc.dump() + "\"");
					b.append(" -> ").append("\"" + obj.dump() + "\"")
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
		// for that case, we just return the empty p2 set if we want to
		// dereference a null pointer
		P2Set ret = new P2Set();

		for (HeapObject obj : p2Set.getHeapObjects()) {
			BoolExpr cst = p2Set.getConstraint(obj);

			P2Set tgt = lookup(obj, field);
			assert (tgt != null) : "get a null p2 set!";
			P2Set projP2Set = P2SetHelper.project(tgt, cst);
			ret.join(projP2Set);
		}

		return ret;
	}

	// this lookup is used for instantiating memory locations
	public InstantiatedLocSet instnLookup(InstantiatedLocSet instnLocSet,
			FieldElem field) {
		InstantiatedLocSet ret = new InstantiatedLocSet();

		for (AbstractMemLoc loc : instnLocSet.getAbstractMemLocs()) {
			BoolExpr cst = instnLocSet.getConstraint(loc);

			P2Set tgt = lookup(loc, field);
			assert (tgt != null) : "get a null p2 set!";
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
			VariableType rightVType, int numberCounter, boolean isInSCC) {
		if (G.debug) {
			System.out.println("we are really hanlding!");
		}

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
			// assert (memLocFactory.containsKey(new LocalVarElem(clazz, method,
			// right))) :
			// "LocalVarElem should be created first before used as RHS";
			v2 = getLocalVarElem(clazz, method, right);
		} else {
			assert false : "for assign stmt, RHS must be LocalElem or ParamElem!";
		}
		assert (v2 != null) : "v2 is null!";

		if (G.debug) {
			System.out.println("v1: " + v1);
			System.out.println("v2: " + v2);
		}

		assert v1.knownArgDerived() : "we should set arg-derived marker for v1!";
		assert v2.knownArgDerived() : "we should set arg-derived marker for v2!";

		P2Set p2Setv2 = lookup(v2, EpsilonFieldElem.getEpsilonFieldElem());
		assert (p2Setv2 != null) : "get a null p2 set!";

		Pair<AbstractMemLoc, FieldElem> pair = new Pair<AbstractMemLoc, FieldElem>(
				v1, EpsilonFieldElem.getEpsilonFieldElem());

		Pair<Boolean, Boolean> ret1 = weakUpdate(pair, p2Setv2, numberCounter,
				isInSCC);
		boolean ret = ret1.val0;
		boolean ret2 = ret1.val1;

		if (G.debug) {
			System.out.println("previous max number: " + maxNumber);
		}
		if (G.debug) {
			System.out.println(numberCounter);
			System.out.println(maxNumber);
		}
		// think about Phi node, we should do assignment more than once
		assert (numberCounter >= maxNumber) : "we should increment the counter every time!";
		maxNumber = ret2 ? Math.max(maxNumber, numberCounter) : maxNumber;
		if (G.debug) {
			System.out.println("new max number: " + maxNumber);
		}
		return ret;
	}

	// this method is just a helper method for handling array allocations
	private boolean handleArrayLoad(ArrayAllocElem left, IndexFieldElem index,
			AllocElem right, int numberCounter, boolean isInSCC) {
		if (G.debug) {
			System.out.println("we are really hanlding!");
		}

		// assert (memLocFactory.containsKey(right)) :
		// "AllocElem (or ArrayAllocElem)"
		// + " should be created before used as RHS!";
		Pair<AbstractMemLoc, FieldElem> pair = new Pair<AbstractMemLoc, FieldElem>(
				left, index);
		P2Set p2Set = new P2Set(right, ConstraintManager.genTrue());

		assert p2Set != null : "p2 set can not be null!";
		// assert !heapObjectsToP2Set.containsKey(pair) :
		// "we cannot re-put ArrayAllocElem into the map!";

		Pair<Boolean, Boolean> ret1 = weakUpdate(pair, p2Set, numberCounter,
				isInSCC);
		boolean ret = ret1.val0;
		boolean ret2 = ret1.val1;

		if (G.debug) {
			System.out.println("previous max number: " + maxNumber);
		}
		assert (isInSCC || numberCounter > maxNumber) : "we should increment the counter every time!";
		maxNumber = ret2 ? Math.max(maxNumber, numberCounter) : maxNumber;
		if (G.debug) {
			System.out.println("new max number: " + maxNumber);
		}
		return ret;
	}

	// handleLoadStmt implements rule (2) in Figure 8 of the paper
	// v1 = v2.f
	// v1: parameter / local (for SSA, only local)
	// v2: parameter / local
	// f: non-static field
	protected boolean handleLoadStmt(jq_Class clazz, jq_Method method,
			Register left, VariableType leftVType, Register rightBase,
			jq_Field rightField, VariableType rightBaseVType,
			int numberCounter, boolean isInSCC) {

		assert (leftVType == VariableType.LOCAL_VARIABLE) : "for non-static load stmt, LHS must be LocalElem";
		assert (rightBaseVType == VariableType.LOCAL_VARIABLE)
				|| (rightBaseVType == VariableType.PARAMEMTER) : ""
				+ "for non-static stmt, RHS BASE must be LocalElem or ParamElem!";

		// generates StackObject (either ParamElem or LocalVarElem)
		LocalVarElem v1 = null;
		StackObject v2 = null;

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
			// assert (memLocFactory.containsKey(new LocalVarElem(clazz, method,
			// rightBase))) :
			// "LocalVarElem should be created first before used as RHS";
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
		Pair<Boolean, Boolean> ret1 = weakUpdate(pair, p2Setv2f, numberCounter,
				isInSCC);
		boolean ret = ret1.val0;
		boolean ret2 = ret1.val1;

		if (G.debug) {
			System.out.println("previous max number: " + maxNumber);
		}
		assert (isInSCC || numberCounter > maxNumber) : "we should increment the counter every time!";
		maxNumber = ret2 ? Math.max(maxNumber, numberCounter) : maxNumber;
		if (G.debug) {
			System.out.println("new max number: " + maxNumber);
		}
		return ret;
	}

	// v1 = v2[0] where v2 is an array, e.g. v2 = new X[10][10]
	// treat it just like a load stmt: v1 = v2.\i where \i is the index field
	protected boolean handleALoadStmt(jq_Class clazz, jq_Method method,
			Register left, VariableType leftVType, Register rightBase,
			VariableType rightBaseVType, int numberCounter, boolean isInSCC) {
		if (G.debug) {
			System.out.println("we are really hanlding!");
		}

		assert (leftVType == VariableType.LOCAL_VARIABLE) : "for array load stmt, LHS must be LocalElem";
		assert (rightBaseVType == VariableType.LOCAL_VARIABLE)
				|| (rightBaseVType == VariableType.PARAMEMTER) : ""
				+ "for array stmt, RHS BASE must be either LocalVarElem or ParamElem!";

		LocalVarElem v1 = getLocalVarElem(clazz, method, left);
		// assert (memLocFactory.containsKey(new LocalVarElem(clazz, method,
		// rightBase))) :
		// "LocalVarElem should be created first before used as RHS";
		LocalVarElem v2 = getLocalVarElem(clazz, method, rightBase);

		assert (v1 != null) : "v1 is null!";
		assert (v2 != null) : "v2 is null!";

		assert (v1.knownArgDerived()) : "we should mark arg-derived marker before using v1!";
		assert (v2.knownArgDerived()) : "we should mark arg-derived marker before using v2!";

		P2Set p2Setv2 = lookup(v2, EpsilonFieldElem.getEpsilonFieldElem());
		assert (p2Setv2 != null) : "get a null p2 set!";

		IndexFieldElem index = IndexFieldElem.getIndexFieldElem();
		P2Set p2Setv2i = lookup(p2Setv2, index);
		assert (p2Setv2i != null) : "get a null p2 set!";

		Pair<AbstractMemLoc, FieldElem> pair = new Pair<AbstractMemLoc, FieldElem>(
				v1, EpsilonFieldElem.getEpsilonFieldElem());

		Pair<Boolean, Boolean> ret1 = weakUpdate(pair, p2Setv2i, numberCounter,
				isInSCC);
		boolean ret = ret1.val0;
		boolean ret2 = ret1.val1;

		if (G.debug) {
			System.out.println("previous max number: " + maxNumber);
		}
		assert (isInSCC || numberCounter > maxNumber) : "we should increment the counter every time!";
		maxNumber = ret2 ? Math.max(maxNumber, numberCounter) : maxNumber;
		if (G.debug) {
			System.out.println("new max number: " + maxNumber);
		}
		return ret;
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
			jq_Field rightField, int numberCounter, boolean isInSCC) {

		if (G.debug) {
			System.out.println("we are really hanlding!");
		}
		boolean ret = false;
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

		// generate the mem loc for RHS base using the global env generator
		StaticElem v2 = Env.getStaticElem(rightBase, rightField);
		// we also add this global into the mem loc factory for this method
		memLocFactory.put(v2, v2);

		assert (v2 != null) : "v2 is null!";

		assert v1.knownArgDerived() : "we should set the arg-derived marker when creating v1";
		assert v2.knownArgDerived() : "we should set the arg-derived marker when creating v2";

		P2Set p2Setv2 = lookup(v2, EpsilonFieldElem.getEpsilonFieldElem());
		assert (p2Setv2 != null) : "get a null p2 set!";

		Pair<AbstractMemLoc, FieldElem> pair = new Pair<AbstractMemLoc, FieldElem>(
				v1, EpsilonFieldElem.getEpsilonFieldElem());

		Pair<Boolean, Boolean> ret1 = weakUpdate(pair, p2Setv2, numberCounter,
				isInSCC);
		ret = ret1.val0;
		boolean ret2 = ret1.val1;
		if (G.debug) {
			System.out.println("previous max number: " + maxNumber);
		}
		if (G.debug) {
			System.out.println(numberCounter);
			System.out.println(maxNumber);
		}
		assert (isInSCC || numberCounter > maxNumber) : "we should increment the counter every time!";
		maxNumber = ret2 ? Math.max(maxNumber, numberCounter) : maxNumber;
		if (G.debug) {
			System.out.println("new max number: " + maxNumber);
		}
		return ret;
	}

	// v1[0] = v2 where v1 = new V[10][10]
	// treat it just as a store stmt like: v1.\i = v2 where \i is the index
	// field (all array base shares the same \i)
	protected boolean handleAStoreStmt(jq_Class clazz, jq_Method method,
			Register leftBase, VariableType leftBaseVType, Register right,
			VariableType rightVType, int numberCounter, boolean isInSCC) {

		if (G.debug) {
			System.out.println("we are really hanlding!");
		}
		assert (rightVType == VariableType.PARAMEMTER)
				|| (rightVType == VariableType.LOCAL_VARIABLE) : "we are only considering local"
				+ " variables and parameters as RHS";
		assert (leftBaseVType == VariableType.PARAMEMTER)
				|| (leftBaseVType == VariableType.LOCAL_VARIABLE) : "we are only considering local"
				+ " variables and parameters as LHS Base";

		// generates StackObject (either ParamElem or LocalVarElem)
		StackObject v1 = null, v2 = null;

		// generate the mem loc for LHS
		if (leftBaseVType == VariableType.PARAMEMTER) {
			v1 = getParamElem(clazz, method, leftBase);
		} else if (leftBaseVType == VariableType.LOCAL_VARIABLE) {
			v1 = getLocalVarElem(clazz, method, leftBase);
		} else {
			assert false : "wried thing! For array store stmt,"
					+ " LHS Base must be LocalElem or ParamElem!";
		}
		assert (v1 != null) : "v1 is null!";

		// generate the mem loc for RHS base
		if (rightVType == VariableType.PARAMEMTER) {
			v2 = getParamElem(clazz, method, right);
		} else if (rightVType == VariableType.LOCAL_VARIABLE) {
			// assert (memLocFactory.containsKey(new LocalVarElem(clazz, method,
			// right))) :
			// "LocalVarElem should be created first before used as RHS";
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

		boolean ret2 = false;
		IndexFieldElem index = IndexFieldElem.getIndexFieldElem();
		for (HeapObject obj : p2Setv1.getHeapObjects()) {
			BoolExpr cst = p2Setv1.getConstraint(obj);
			// projP2Set is a new P2Set with copies of the constraints (same
			// content but different constraint instances)
			P2Set projP2Set = P2SetHelper.project(p2Setv2, cst);

			Pair<AbstractMemLoc, FieldElem> pair = new Pair<AbstractMemLoc, FieldElem>(
					obj, index);
			Pair<Boolean, Boolean> ret1 = weakUpdate(pair, projP2Set,
					numberCounter, isInSCC);
			ret = ret | ret1.val0;
			ret2 = ret2 | ret1.val1;
		}

		if (G.debug) {
			System.out.println("previous max number: " + maxNumber);
		}
		assert (isInSCC || numberCounter > maxNumber) : "we should increment the counter every time!";
		maxNumber = ret2 ? Math.max(maxNumber, numberCounter) : maxNumber;
		if (G.debug) {
			System.out.println("new max number: " + maxNumber);
		}
		return ret;
	}

	// handleStoreStmt implements rule (3) in Figure 8 of the paper
	// v1.f = v2
	protected boolean handleStoreStmt(jq_Class clazz, jq_Method method,
			Register leftBase, VariableType leftBaseVType, jq_Field leftField,
			Register right, VariableType rightVType, int numberCounter,
			boolean isInSCC) {

		if (G.debug) {
			System.out.println("we are really hanlding!");
		}

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
			// assert (memLocFactory.containsKey(new LocalVarElem(clazz, method,
			// right))) :
			// "LocalVarElem should be created first before used as RHS";
			v2 = getLocalVarElem(clazz, method, right);
		} else {
			assert false : "for non-static store stmt, RHS must be LocalElem or ParamElem!";
		}
		assert (v2 != null) : "v2 is null!";

		assert v1.knownArgDerived() : "we should set the arg-derived marker when creating v1";
		assert v2.knownArgDerived() : "we should set the arg-derived marker when creating v2";

		boolean ret = false;
		boolean ret2 = false;

		P2Set p2Setv1 = lookup(v1, EpsilonFieldElem.getEpsilonFieldElem());
		P2Set p2Setv2 = lookup(v2, EpsilonFieldElem.getEpsilonFieldElem());
		assert (p2Setv1 != null) : "get a null p2 set!";
		assert (p2Setv2 != null) : "get a null p2 set!";

		NormalFieldElem f = new NormalFieldElem(leftField);
		for (HeapObject obj : p2Setv1.getHeapObjects()) {
			BoolExpr cst = p2Setv1.getConstraint(obj);
			// projP2Set is a new P2Set with copies of the constraints (same
			// content but different constraint instances)
			P2Set projP2Set = P2SetHelper.project(p2Setv2, cst);

			Pair<AbstractMemLoc, FieldElem> pair = new Pair<AbstractMemLoc, FieldElem>(
					obj, f);

			Pair<Boolean, Boolean> ret1 = weakUpdate(pair, projP2Set,
					numberCounter, isInSCC);
			ret = ret | ret1.val0;
			ret2 = ret2 | ret1.val1;
		}
		if (G.debug) {
			System.out.println("previous max number: " + maxNumber);
		}
		assert (isInSCC || numberCounter > maxNumber) : "we should increment the counter every time!";
		maxNumber = ret2 ? Math.max(maxNumber, numberCounter) : maxNumber;
		if (G.debug) {
			System.out.println("new max number: " + maxNumber);
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
			VariableType rightVType, int numberCounter, boolean isInSCC) {
		if (G.debug) {
			System.out.println("we are really hanlding!");
		}
		boolean ret = false;
		assert (rightVType == VariableType.PARAMEMTER)
				|| (rightVType == VariableType.LOCAL_VARIABLE) : "we are only considering local"
				+ " variables and parameters as RHS in static store stmt";

		// generate the mem loc for LHS Base using the global env
		StaticElem v1 = Env.getStaticElem(leftBase, leftField);
		// we also add this global into the mem loc factory for this method
		memLocFactory.put(v1, v1);

		assert (v1 != null) : "v1 is null!";

		StackObject v2 = null;
		// generate the mem loc for RHS
		if (rightVType == VariableType.PARAMEMTER) {
			v2 = getParamElem(clazz, method, right);
		} else if (rightVType == VariableType.LOCAL_VARIABLE) {
			// assert (memLocFactory.containsKey(new LocalVarElem(clazz, method,
			// right))) :
			// "LocalVarElem should be created first before used as RHS";
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

		Pair<Boolean, Boolean> ret1 = weakUpdate(pair, p2Setv2, numberCounter,
				isInSCC);
		ret = ret1.val0;
		boolean ret2 = ret1.val1;
		if (G.debug) {
			System.out.println("previous max number: " + maxNumber);
		}
		assert (isInSCC || numberCounter > maxNumber) : "we should increment the counter every time!";
		maxNumber = ret2 ? Math.max(maxNumber, numberCounter) : maxNumber;
		if (G.debug) {
			System.out.println("new max number: " + maxNumber);
		}
		return ret;
	}

	// handleNewStmt implements rule (4) in Figure 8 of the paper
	// v = new T
	protected boolean handleNewStmt(jq_Class clazz, jq_Method method,
			Register left, VariableType leftVType, jq_Type right, int line,
			int numberCounter, boolean isInSCC) {
		if (G.debug) {
			System.out.println("we are really hanlding!");
		}
		boolean ret = false;
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

		Pair<Boolean, Boolean> ret1 = weakUpdate(pair, new P2Set(allocT,
				ConstraintManager.genTrue()), numberCounter, isInSCC);
		boolean ret2 = ret1.val1;
		ret = ret1.val0;
		if (G.debug) {
			System.out.println("previous max number: " + maxNumber);
		}
		assert (isInSCC || numberCounter > maxNumber) : "we should increment the counter every time!";
		maxNumber = ret2 ? Math.max(maxNumber, numberCounter) : maxNumber;
		if (G.debug) {
			System.out.println("new max number: " + maxNumber);
		}
		return ret;
	}

	// X x1 = new X[10] by just calling the handleMultiNewArrayStmt method with
	// dim = 1
	protected boolean handleNewArrayStmt(jq_Class clazz, jq_Method method,
			Register left, VariableType leftVType, jq_Type right, int line,
			int numberCounter, boolean isInSCC) {
		return handleMultiNewArrayStmt(clazz, method, left, leftVType, right,
				1, line, numberCounter, isInSCC);
	}

	// handle multi-new stmt, e.g. X x1 = new X[1][2][3]
	// dim is the dimension of this array, dim >= 2
	protected boolean handleMultiNewArrayStmt(jq_Class clazz, jq_Method method,
			Register left, VariableType leftVType, jq_Type right, int dim,
			int line, int numberCounter, boolean isInSCC) {
		if (G.debug) {
			System.out.println("we are really hanlding!");
		}
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
		Pair<Boolean, Boolean> ret1 = weakUpdate(pair, new P2Set(allocT,
				ConstraintManager.genTrue()), numberCounter, isInSCC);
		boolean ret2 = ret1.val1;
		ret = ret1.val0;

		// handling fields of the ArrayAllocElem for multi-array with dim > 1
		for (int i = dim; i >= 2; i--) {
			ArrayAllocElem leftAllocT = getArrayAllocElem(clazz, method, right,
					i, line);
			ArrayAllocElem rightAllocT = getArrayAllocElem(clazz, method,
					right, i - 1, line);
			ret = handleArrayLoad(leftAllocT,
					IndexFieldElem.getIndexFieldElem(), rightAllocT,
					numberCounter, isInSCC) | ret;
		}
		if (G.debug) {
			System.out.println("previous max number: " + maxNumber);
		}
		assert (isInSCC || numberCounter > maxNumber) : "we should increment the counter every time!";
		maxNumber = ret2 ? Math.max(maxNumber, numberCounter) : maxNumber;
		if (G.debug) {
			System.out.println("new max number: " + maxNumber);
		}
		return ret;
	}

	// return v;
	protected boolean handleRetStmt(jq_Class clazz, jq_Method method,
			Register retValue, VariableType type, int numberCounter,
			boolean isInSCC) {
		if (G.debug) {
			System.out.println("we are really hanlding!");
		}
		boolean ret = false;
		// first try to find the corresponding local or param that has been
		// declared before returning
		StackObject v = null;
		if (type == VariableType.LOCAL_VARIABLE) {
			v = getLocalVarElem(clazz, method, retValue);
		} else if (type == VariableType.PARAMEMTER) {
			v = getParamElem(clazz, method, retValue);
		} else {
			assert false : "we are only considering return value to be local or parameter!";
		}

		// create the return value elem
		RetElem retElem = getRetElem(clazz, method);
		// update the p2set of the return value by the p2set of the local/param
		Pair<AbstractMemLoc, FieldElem> pair = new Pair<AbstractMemLoc, FieldElem>(
				retElem, EpsilonFieldElem.getEpsilonFieldElem());
		P2Set p2Set = lookup(v, EpsilonFieldElem.getEpsilonFieldElem());
		assert (p2Set != null) : "get a null p2set!";
		Pair<Boolean, Boolean> ret1 = weakUpdate(pair, p2Set, numberCounter,
				isInSCC);
		ret = ret1.val0;
		boolean ret2 = ret1.val1;
		if (G.debug) {
			System.out.println("previous max number: " + maxNumber);
		}
		assert (isInSCC || numberCounter > maxNumber) : "we should increment the counter every time!";
		maxNumber = ret2 ? Math.max(maxNumber, numberCounter) : maxNumber;
		if (G.debug) {
			System.out.println("new max number: " + maxNumber);
		}
		return ret;
	}

	/* Constraint instantiation. */
	protected BoolExpr instCst(BoolExpr cst, AbstractHeap callerHeap,
			ProgramPoint point, MemLocInstantiation memLocInstn) {
		assert cst != null : "Invalid Constrait before instantiation.";
		// return directly.
		if (ConstraintManager.isScala(cst))
			return cst;
		
		System.out.println("Inst CST:" + cst);
		BoolExpr instC = ConstraintManager.instConstaint(cst, this);
		assert instC != null : "Invalid instantiated Constrait.";
		return instC;
	}

	protected Pair<Boolean, Boolean> instantiateEdgesForRecursiveCall(
			Set<HeapEdge> edges, MemLocInstantiation memLocInstn,
			AbstractHeap calleeHeap, ProgramPoint point, BoolExpr typeCst,
			int numberCounter, boolean isInSCC,
			Map<Numbering, Set<HeapEdge>> toAdd) {

		if (G.debug) {
			System.out.println("I am in a self-recursive call!");
		}

		boolean ret = false;
		boolean ret2 = false;

		for (HeapEdge edge : edges) {

			AbstractMemLoc src = edge.getSrc();
			HeapObject dst = edge.getDst();
			FieldElem field = edge.getField();

			assert (src != null && dst != null && field != null) : "nulls!";
			assert (calleeHeap.contains(src)) : "callee's heap should contain the source of the edge!";
			assert (calleeHeap.lookup(src, field).containsHeapObject(dst)) : ""
					+ "the p2 set should contain the destination of the edge!";

			BoolExpr calleeCst = calleeHeap.lookup(src, field).getConstraint(
					dst);
			assert (calleeCst != null) : "constraint is null!";

			// instantiate the calleeCst
			BoolExpr instnCst = instCst(calleeCst, this, point, memLocInstn);

			if (G.debug) {
				System.out.println(calleeHeap.getMethod());
			}
			InstantiatedLocSet instnSrc = memLocInstn.instantiate(src, this,
					point);
			InstantiatedLocSet instnDst = memLocInstn.instantiate(dst, this,
					point);
			if (G.debug) {
				System.out.println("instantiate the edge: " + edge);
				System.out.println("the source of the edge is: " + src);
				System.out.println("the field is : " + field);
				System.out.println("the destination is: " + dst);
				System.out.println("the source is instantiated to: "
						+ instnSrc.getInstnLocSet());
				System.out.println("the destination is instantiated to: "
						+ instnDst.getInstnLocSet());
			}
			assert (instnDst != null) : "instantiation of dst cannot be null!";
			if (instnSrc == null) {
				assert (src instanceof RetElem) : "only return value in the callee"
						+ " is allowed not having an instantiated location in the callee";
			}
			// if meeting a return value in the callee mapped to nothing in the
			// caller, e.g. v.foo(a,b) with a LHS
			// just jump to the next edge and do not instantiate this edge
			if (instnSrc == null || instnDst == null) {
				continue;
			}

			// it is possible to be empty
			// assert (!instnSrc.isEmpty()) : "instnSrc cannot be empty!";
			// it is possible the dst has no location in the caller to
			// instantiate to, just think about the native call in the caller
			// assert (!instnDst.isEmpty()) : "instnDst cannot be empty!";

			for (AbstractMemLoc newSrc : instnSrc.getAbstractMemLocs()) {
				for (AbstractMemLoc newDst : instnDst.getAbstractMemLocs()) {
					assert (newDst instanceof HeapObject) : ""
							+ "dst should be instantiated as a heap object!";
					HeapObject newDst1 = (HeapObject) newDst;

					assert (newDst1 != null) : "null!";

					BoolExpr cst1 = instnSrc.getConstraint(newSrc);
					BoolExpr cst2 = instnDst.getConstraint(newDst);
					BoolExpr cst = ConstraintManager.intersect(
							ConstraintManager.intersect(cst1, cst2),
							ConstraintManager.intersect(instnCst, typeCst));

					assert (cst1 != null && cst2 != null && cst != null) : "get null constraints!";

					Pair<AbstractMemLoc, FieldElem> pair = new Pair<AbstractMemLoc, FieldElem>(
							newSrc, field);
					Pair<Boolean, Boolean> ret1 = weakUpdateForRecursiveCall(
							pair, new P2Set(newDst1, cst), numberCounter,
							isInSCC, toAdd);
					ret = ret | ret1.val0;
					ret2 = ret2 | ret1.val1;
				}
			}
		}

		return new Pair<Boolean, Boolean>(ret, ret2);
	}

	protected Pair<Boolean, Boolean> instantiateEdges(Set<HeapEdge> edges,
			MemLocInstantiation memLocInstn, AbstractHeap calleeHeap,
			ProgramPoint point, BoolExpr typeCst, int numberCounter,
			boolean isInSCC) {
		boolean ret = false;
		boolean ret2 = false;

		if (G.debug) {
			System.out.println("instantiate edgeS: " + edges);
		}
		for (HeapEdge edge : edges) {

			AbstractMemLoc src = edge.getSrc();
			HeapObject dst = edge.getDst();
			FieldElem field = edge.getField();

			assert (src != null && dst != null && field != null) : "nulls!";
			assert (calleeHeap.contains(src)) : "callee's heap should contain the source of the edge!";
			assert (calleeHeap.lookup(src, field).containsHeapObject(dst)) : ""
					+ "the p2 set should contain the destination of the edge!";

			BoolExpr calleeCst = calleeHeap.lookup(src, field).getConstraint(
					dst);
			assert (calleeCst != null) : "constraint is null!";

			// instantiate the calleeCst
			BoolExpr instnCst = instCst(calleeCst, this, point, memLocInstn);

			if (G.debug) {
				System.out.println(calleeHeap.getMethod());
			}
			InstantiatedLocSet instnSrc = memLocInstn.instantiate(src, this,
					point);
			InstantiatedLocSet instnDst = memLocInstn.instantiate(dst, this,
					point);
			if (G.debug) {
				System.out.println("instantiate the edge: " + edge);
				System.out.println("the source of the edge is: " + src);
				System.out.println("the field is : " + field);
				System.out.println("the destination is: " + dst);
				System.out.println("the source is instantiated to: "
						+ instnSrc.getInstnLocSet());
				System.out.println("the destination is instantiated to: "
						+ instnDst.getInstnLocSet());
			}
			assert (instnDst != null) : "instantiation of dst cannot be null!";
			if (instnSrc == null) {
				assert (src instanceof RetElem) : "only return value in the callee"
						+ " is allowed not having an instantiated location in the callee";
			}
			// if meeting a return value in the callee mapped to nothing in the
			// caller, e.g. v.foo(a,b) with a LHS
			// just jump to the next edge and do not instantiate this edge
			if (instnSrc == null || instnDst == null) {
				continue;
			}

			if (G.debug) {
				calleeHeap.dumpHeapNumberingToFile("$callee");
				this.dumpHeapNumberingToFile("$caller");
			}
			// it is possible that the src has no location in the caller's heap
			// to instantiate
			// assert (!instnSrc.isEmpty()) : "instnSrc cannot be empty!";
			// it is possible the dst has no location in the caller to
			// instantiate to, just think about the native call in the caller
			// assert (!instnDst.isEmpty()) : "instnDst cannot be empty!";

			for (AbstractMemLoc newSrc : instnSrc.getAbstractMemLocs()) {
				// following is just for creating the node in the heapMapping
				// because it is possible that the dst is instantiated to
				// nothing, which leads to the src not created in the heap
				// Pair<AbstractMemLoc, FieldElem> pair = new
				// Pair<AbstractMemLoc, FieldElem>(
				// newSrc, field);
				// weakUpdate(pair, new P2Set(), numberCounter, isInSCC);

				for (AbstractMemLoc newDst : instnDst.getAbstractMemLocs()) {
					assert (newDst instanceof HeapObject) : ""
							+ "dst should be instantiated as a heap object!";
					HeapObject newDst1 = (HeapObject) newDst;

					assert (newDst1 != null) : "null!";

					BoolExpr cst1 = instnSrc.getConstraint(newSrc);
					BoolExpr cst2 = instnDst.getConstraint(newDst);
					BoolExpr cst = ConstraintManager.intersect(
							ConstraintManager.intersect(cst1, cst2),
							ConstraintManager.intersect(instnCst, typeCst));

					assert (cst1 != null && cst2 != null && cst != null) : "get null constraints!";
					Pair<AbstractMemLoc, FieldElem> pair = new Pair<AbstractMemLoc, FieldElem>(
							newSrc, field);
					if (G.debug) {
						this.dumpHeapNumberingToFile("$caller");
						calleeHeap.dumpHeapNumberingToFile("$callee");
						System.out.println("****** " + newDst1);
					}
					Pair<Boolean, Boolean> ret1 = weakUpdate(pair, new P2Set(
							newDst1, cst), numberCounter, isInSCC);

					ret = ret | ret1.val0;
					ret2 = ret2 | ret1.val1;
				}
			}
		}

		if (G.debug) {
			System.out.println("the return boolean is: " + ret2);
		}
		return new Pair<Boolean, Boolean>(ret, ret2);
	}

	// TODO
	protected boolean handleInvokeStmt(jq_Class clazz, jq_Method method,
			int line, AbstractHeap calleeHeap, MemLocInstantiation memLocInstn,
			BoolExpr typeCst, int numberCounter, boolean isInSCC) {
		boolean ret = false;
		// record the program point in the caller so that we can use this for
		// allocation site naming (heap naming)
		ProgramPoint point = Env.getProgramPoint(clazz, method, line);
		Map<Numbering, Set<HeapEdge>> calleeEdgeSeq = calleeHeap.getEdgeSeq();

		// this is used for recursive call
		Map<Numbering, Set<HeapEdge>> toAdd = null;
		boolean isRecursive = false;
		if (this.equals(calleeHeap)) {
			toAdd = new TreeMap<Numbering, Set<HeapEdge>>(
					new Comparator<Numbering>() {
						public int compare(Numbering first, Numbering second) {
							if (first.getNumber() < second.getNumber()) {
								return -1;
							} else if (first.getNumber() == second.getNumber()) {
								return 0;
							} else {
								return 1;
							}
						}
					});
			isRecursive = true;
		}
		// before instantiating all the edges in the heap
		// because it is possible there are some isolated nodes in the callee's
		// heap, we should first make sure that all the nodes will be
		// instantiated as a location in the caller's heap
		for (Pair<AbstractMemLoc, FieldElem> pair : calleeHeap.getHeap()
				.keySet()) {
			AbstractMemLoc loc = pair.val0;
			FieldElem field = pair.val1;
			InstantiatedLocSet instnMemLocSet = memLocInstn.instantiate(loc,
					this, point);
			if (G.debug) {
				System.out.println("I am pre-instantiate the location: " + loc);

			}
			assert (instnMemLocSet != null);
			for (AbstractMemLoc loc1 : instnMemLocSet.getAbstractMemLocs()) {
				weakUpdate(new Pair<AbstractMemLoc, FieldElem>(loc1, field),
						new P2Set(), -1, false);
			}
		}

		// begin to add the edges
		for (Numbering n : calleeEdgeSeq.keySet()) {
			boolean ret2 = false;

			// fetch the edges with the same number (added in the same patch)
			Set<HeapEdge> edges = calleeEdgeSeq.get(n);
			// whether they are added in an SCC in the CFG
			boolean edgesAreInSCC = n.isInSCC();
			// the number used to number the edges if the callee's edges are not
			// in SCC in the CFG of the callee
			assert (n.getNumber() > 0) : "we cannot assign non-positive numbers!";
			assert (numberCounter > 0) : "we cannot assign non-positive numbers!";
			int number = numberCounter - 1 + n.getNumber();
			// assign assgnNumber to the edges
			int assgnNumber = isInSCC ? numberCounter : number;
			// mark whether the edges are in SCC
			boolean assgnFlag = isInSCC || edgesAreInSCC;

			if (G.debug) {
				System.out.println("Instantiating edges: ");
				System.out.println("numbering: " + n);
				System.out.println("edges: " + edges);
			}

			if (!isRecursive) {
				if (edgesAreInSCC) {
					// do a fix-point
					boolean go = true;
					while (go) {
						Pair<Boolean, Boolean> ret1 = instantiateEdges(edges,
								memLocInstn, calleeHeap, point, typeCst,
								assgnNumber, assgnFlag);
						go = ret1.val0;
						ret = ret | go;
						ret2 = ret2 | ret1.val1;
					}
				} else {
					Pair<Boolean, Boolean> ret1 = instantiateEdges(edges,
							memLocInstn, calleeHeap, point, typeCst,
							assgnNumber, assgnFlag);
					ret = ret1.val0;
					ret2 = ret1.val1;
				}

				if (G.debug) {
					System.out.println("return boolean is : " + ret2);
					System.out.println("edges are in SCC: " + edgesAreInSCC);
					System.out.println("previous max number: " + maxNumber);
					System.out.println("assigning the number: " + assgnNumber);
					System.out.println("assigning the flag: " + assgnFlag);
				}
				// consider multiple callees, we cannot do the following
				// assertion
				// assert (isInSCC || assgnNumber > maxNumber) :
				// "we should increment the counter every time!";
				assert (!edges.isEmpty()) : "a number should not be assigned to no-edge!";
				if (G.debug) {
					System.out.println("ret2 : " + ret2);
				}
				maxNumber = ret2 ? Math.max(maxNumber, assgnNumber) : maxNumber;
				if (G.debug) {
					System.out.println("new max number: " + maxNumber);
					System.out.println(edgeSeq);
				}
				assert (maxNumber == 0 || edgeSeq.containsKey(new Numbering(
						maxNumber, assgnFlag))) : "error in adding edges and assigning number and flag!";
				// consider multiple callees, we cannot do the following
				// assertion
				// assert (!edgeSeq
				// .containsKey(new Numbering(maxNumber + 1, true))) : ""
				// + "error in adding edges and assigning number and flag!";
				// assert (!edgeSeq
				// .containsKey(new Numbering(maxNumber + 1, false))) : ""
				// + "error in adding edges and assigning number and flag!";

			} else {
				assert (toAdd != null);
				if (edgesAreInSCC) {
					// do a fix-point
					boolean go = true;
					while (go) {
						Pair<Boolean, Boolean> ret1 = instantiateEdgesForRecursiveCall(
								edges, memLocInstn, calleeHeap, point, typeCst,
								assgnNumber, assgnFlag, toAdd);
						go = ret1.val0;
						ret = ret | go;
						ret2 = ret2 | ret1.val1;
					}

				} else {
					Pair<Boolean, Boolean> ret1 = instantiateEdgesForRecursiveCall(
							edges, memLocInstn, calleeHeap, point, typeCst,
							assgnNumber, assgnFlag, toAdd);
					ret = ret1.val0;
					ret2 = ret1.val1;
				}
				assert (isInSCC || assgnNumber > maxNumber) : "we should increment the counter every time!";
				assert (!edges.isEmpty()) : "a number should not be assigned to no-edge!";
				maxNumber = ret2 ? Math.max(maxNumber, assgnNumber) : maxNumber;
				if (G.debug) {
					System.out.println("new max number: " + maxNumber);
					System.out.println(edgeSeq);
				}
				// considering multiple potential callees, we cannot do this
				// assertion
				// assert (maxNumber == 0
				// || toAdd.containsKey(new Numbering(maxNumber, assgnFlag)) ||
				// edgeSeq
				// .containsKey(new Numbering(maxNumber, assgnFlag))) : ""
				// + "error in adding edges and assigning number and flag!";
				// assert (!edgeSeq
				// .containsKey(new Numbering(maxNumber + 1, true))) : ""
				// + "error in adding edges and assigning number and flag!";
				// assert (!edgeSeq
				// .containsKey(new Numbering(maxNumber + 1, false))) : ""
				// + "error in adding edges and assigning number and flag!";
				// assert (!toAdd.containsKey(new Numbering(maxNumber + 1,
				// true))) : ""
				// + "error in adding edges and assigning number and flag!";
				// assert (!toAdd.containsKey(new Numbering(maxNumber + 1,
				// false))) : ""
				// + "error in adding edges and assigning number and flag!";
			}
		}

		if (isRecursive) {
			for (Numbering n : toAdd.keySet()) {
				Set<HeapEdge> edges = edgeSeq.get(n);
				if (edges == null) {
					edges = new HashSet<HeapEdge>();
					edgeSeq.put(n, edges);
				}
				edges.addAll(toAdd.get(n));
			}
		}

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

		AccessPath ret = new AccessPath(base, field, Env.countAccessPath++);
		if (memLocFactory.containsKey(ret)) {
			return (AccessPath) memLocFactory.get(ret);
		}

		ArgDerivedHelper.markArgDerived(ret);
		memLocFactory.put(ret, ret);

		return ret;
	}

	// get the AccessPath whose base is StaticElem
	private AccessPath getAccessPath(StaticElem base, FieldElem field) {

		AccessPath ret = new AccessPath(base, field, Env.countAccessPath++);
		if (memLocFactory.containsKey(ret)) {
			return (AccessPath) memLocFactory.get(ret);
		}

		ArgDerivedHelper.markArgDerived(ret);
		memLocFactory.put(ret, ret);

		return ret;
	}

	// get the AccessPath whose base is HeapObject
	private AccessPath getAccessPath(HeapObject base, FieldElem field) {

		AccessPath ret = new AccessPath(base, field, Env.countAccessPath++);
		if (memLocFactory.containsKey(ret)) {
			return (AccessPath) memLocFactory.get(ret);
		}

		ArgDerivedHelper.markArgDerived(ret);
		memLocFactory.put(ret, ret);

		return ret;
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
		Context context = new Context(Env.getProgramPoint(clazz, method, line));
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

	// get the AllocElem in the mem loc factory by an AllocElem with the same
	// content (we want to use exactly the same instance)
	protected AllocElem getAllocElem(AllocElem other) {

		if (memLocFactory.containsKey(other)) {
			return (AllocElem) memLocFactory.get(other);
		}

		AllocElem ret = new AllocElem(other.alloc, other.context.clone());

		ArgDerivedHelper.markArgDerived(ret);
		memLocFactory.put(ret, ret);

		return ret;
	}

	protected AllocElem getAllocElem(AllocElem other, ProgramPoint point) {

		AllocElem ret = other.clone();
		ret.appendContextFront(point);

		if (memLocFactory.containsKey(ret)) {
			return (AllocElem) memLocFactory.get(ret);
		}

		ArgDerivedHelper.markArgDerived(ret);
		memLocFactory.put(ret, ret);

		return ret;
	}

	protected ArrayAllocElem getArrayAllocElem(jq_Class clazz,
			jq_Method method, jq_Type type, int dim, int line) {
		Context context = new Context(Env.getProgramPoint(clazz, method, line));
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

	// get the RetElem in the factory
	// if not existed, create one and put into the factory
	// this is only used for instantiation and there might be a LocalVarElem or
	// ParamElem with the same content
	protected RetElem getRetElem(jq_Class clazz, jq_Method method) {
		// create a wrapper
		RetElem ret = new RetElem(clazz, method);
		// try to look up
		if (memLocFactory.containsKey(ret)) {
			return (RetElem) memLocFactory.get(ret);
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
				|| (loc instanceof ParamElem) : ""
				+ "we can only get default targets for arg-derived elements";
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

	protected Pair<Boolean, Boolean> weakUpdateForRecursiveCall(
			Pair<AbstractMemLoc, FieldElem> pair, P2Set p2Set,
			int numberCounter, boolean isInSCC,
			Map<Numbering, Set<HeapEdge>> toAdd) {
		if (G.debug) {
			System.out.println("I am in the weak update for recursive method!");
			System.out.println("weak updating!");
			System.out.println("source is: " + pair.val0);
			System.out.println("Field elem is: " + pair.val1);
			System.out.println("current p2 set is: "
					+ heapObjectsToP2Set.get(pair));
			System.out.println("target p2 set is: " + p2Set);
			System.out.println("use numbering: " + numberCounter);
			System.out.println("is in scc: " + isInSCC);
		}
		boolean ret = false;
		AbstractMemLoc src = pair.val0;
		FieldElem f = pair.val1;
		Set<HeapObject> tgts = p2Set.getHeapObjects();

		// first clean up the default targets in the p2set given the pair
		cleanup(p2Set, pair);

		src.addField(f);
		if (G.debug) {
			System.out.println("Adding the field " + f + " in the p2 set of "
					+ src);
		}

		// then get the current heap given the memory location and the field
		P2Set currentP2Set = heapObjectsToP2Set.get(pair);

		if (currentP2Set == null) {
			currentP2Set = new P2Set();
			heapObjectsToP2Set.put(pair, currentP2Set);
		}

		// update the locations in the real heap graph
		// currently we are not using this feature
		// maybe we will use this for instantiating the memory locations
		heap.add(src);
		heap.addAll(tgts);

		boolean ret1 = false;
		// do the numbering
		// we should add the edges even though there might be a lot of edges
		// that are added many times, because this reflects the dependence
		// relation of the edges
		// for recursive call, we should use another map to temporarily store
		// the information to avoid concurrent modification exception
		if (!tgts.isEmpty()) {
			Numbering wrapper = getNumbering(numberCounter, isInSCC);
			Set<HeapEdge> edges = toAdd.get(wrapper);
			// we should do this check considering the invoke and also regular
			// operations
			if (edges == null) {
				edges = new HashSet<HeapEdge>();
				toAdd.put(wrapper, edges);
			}
			for (HeapObject tgt : tgts) {
				HeapEdge added = getHeapEdge(src, tgt, f);
				edges.add(added);
				ret1 = true;
			}
			// filling the reverse mapping, just for dbg and dumping
			for (HeapEdge edge : edges) {
				Set<Numbering> nums = reverseEdgeSeq.get(edge);
				if (nums == null) {
					nums = new HashSet<Numbering>();
					reverseEdgeSeq.put(edge, nums);
				}
				nums.add(getNumbering(numberCounter, isInSCC));
			}
		}

		// the KEY for weak update
		ret = currentP2Set.join(p2Set);

		return new Pair<Boolean, Boolean>(ret, ret1);
	}

	// TODO
	// still need to check whether this returned boolean value is correct
	protected Pair<Boolean, Boolean> weakUpdate(
			Pair<AbstractMemLoc, FieldElem> pair, P2Set p2Set,
			int numberCounter, boolean isInSCC) {
		if (G.debug) {
			System.out.println("weak updating!");
			System.out.println("source is: " + pair.val0);
			System.out.println("Field elem is: " + pair.val1);
			System.out.println("current p2 set is: "
					+ heapObjectsToP2Set.get(pair));
			System.out.println("target p2 set is: " + p2Set);
			System.out.println("use numbering: " + numberCounter);
			System.out.println("is in scc: " + isInSCC);
		}
		boolean ret = false;
		AbstractMemLoc src = pair.val0;
		FieldElem f = pair.val1;
		Set<HeapObject> tgts = p2Set.getHeapObjects();

		// first clean up the default targets in the p2set given the pair
		cleanup(p2Set, pair);

		src.addField(f);
		if (G.debug) {
			System.out.println("Adding the field " + f + " in the p2 set of "
					+ src);
		}

		// then get the current heap given the memory location and the field
		P2Set currentP2Set = heapObjectsToP2Set.get(pair);

		if (currentP2Set == null) {
			currentP2Set = new P2Set();
			if (G.debug) {
				System.out.println("I am creating the p2 set!");
				System.out.println("src: " + src);
				System.out.println("Field: " + f);
			}
			heapObjectsToP2Set.put(pair, currentP2Set);
		}

		// update the locations in the real heap graph
		// currently we are not using this feature
		// maybe we will use this for instantiating the memory locations
		heap.add(src);
		heap.addAll(tgts);

		boolean ret1 = false;
		// do the numbering
		// we should add the edges even though there might be a lot of edges
		// that are added many times, because this reflects the dependence
		// relation of the edges
		if (!tgts.isEmpty()) {
			Numbering wrapper = getNumbering(numberCounter, isInSCC);
			Set<HeapEdge> edges = edgeSeq.get(wrapper);
			// we should do this check considering the invoke and also regular
			// operations
			if (edges == null) {
				edges = new HashSet<HeapEdge>();
				edgeSeq.put(wrapper, edges);
			}
			for (HeapObject tgt : tgts) {
				HeapEdge added = getHeapEdge(src, tgt, f);
				edges.add(added);
				ret1 = true;
			}
			// filling the reverse mapping, just for dbg and dumping
			for (HeapEdge edge : edges) {
				Set<Numbering> nums = reverseEdgeSeq.get(edge);
				if (nums == null) {
					nums = new HashSet<Numbering>();
					reverseEdgeSeq.put(edge, nums);
				}
				nums.add(getNumbering(numberCounter, isInSCC));
			}
		}

		// the KEY for weak update
		ret = currentP2Set.join(p2Set);

		return new Pair<Boolean, Boolean>(ret, ret1);
	}

	protected HeapEdge getHeapEdge(AbstractMemLoc src, HeapObject tgt,
			FieldElem f) {
		HeapEdge ret = new HeapEdge(src, tgt, f);
		if (edgesFactory.containsKey(ret)) {
			return edgesFactory.get(ret);
		}
		edgesFactory.put(ret, ret);
		return ret;
	}

	protected Numbering getNumbering(int num, boolean isInSCC) {
		Numbering ret = new Numbering(num, isInSCC);
		if (numberingFactory.containsKey(ret)) {
			return numberingFactory.get(ret);
		}
		numberingFactory.put(ret, ret);
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
	public boolean contains(AbstractMemLoc loc) {
		return heap.contains(loc);
	}

	// mark whether the heap has changed.
	public void markChanged(boolean flag) {
		this.isChanged = flag;
	}

	public boolean isChanged() {
		return isChanged;
	}

	public Map<Numbering, Set<HeapEdge>> getEdgeSeq() {
		return edgeSeq;
	}

	public int getMaxNumber() {
		return maxNumber;
	}
}