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
import joeq.Class.jq_Reference.jq_NullType;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operand;
import joeq.Compiler.Quad.Operand.AConstOperand;
import joeq.Compiler.Quad.Operand.FieldOperand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operand.TypeOperand;
import joeq.Compiler.Quad.Operator.Getfield;
import joeq.Compiler.Quad.Operator.Getstatic;
import joeq.Compiler.Quad.Operator.Move;
import joeq.Compiler.Quad.Operator.MultiNewArray;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Compiler.Quad.Operator.Putfield;
import joeq.Compiler.Quad.Operator.Putstatic;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.util.tuple.object.Pair;
import framework.scuba.helper.ArgDerivedHelper;
import framework.scuba.helper.ConstraintManager;
import framework.scuba.helper.P2SetHelper;

public class AbstractHeap {

	// the method whose heap is represented by this AbstractHeap
	// final protected jq_Method method;

	// heap is a translation from heap, which is used to dump the
	// topology of the abstract heap
	// THIS IS the main data structure to represent the abstract heap
	// every time we refer to a heap, it means this heap topology
	// MAYBE we will not use this? we can use memLocFactory
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

	public boolean isChanged = false;

	public static enum VariableType {
		PARAMEMTER, LOCAL_VARIABLE, NULL_POINTER, CONSTANT;
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
				assert (root instanceof ParamElem || root instanceof StaticElem) : "Root of "
						+ loc
						+ " : "
						+ root
						+ " should be either ParamElem or StaticElem!";
			} else if (loc instanceof StaticElem) {
				AbstractMemLoc root = loc.findRoot();
				assert (loc.equals(root)) : "Root of " + loc + " : " + root
						+ " should be the same as " + loc;
			} else if (loc instanceof AllocElem) {
				AbstractMemLoc root = loc.findRoot();
				assert (loc.equals(root)) : root + " of " + loc
						+ " should be the same as " + loc;
			} else if (loc instanceof LocalVarElem) {
				AbstractMemLoc root = loc.findRoot();
				assert (loc.equals(root)) : root + " of " + loc
						+ " should be the same as " + loc;
			} else if (loc instanceof ParamElem) {
				AbstractMemLoc root = loc.findRoot();
				assert (loc.equals(root)) : root + " of " + loc
						+ " should be the same as " + loc;
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
				HeapObject hObj = getAbstractMemLoc(loc, f);
				assert (!p2Set.containsHeapObject(hObj)) : "A default edge exists from "
						+ loc + " with field " + f + " to heap object " + hObj;
			}
		}
	}

	// print the heapObjectsToP2Set mapping in file
	public void dumpHeapMappingToFile() {
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
					"output/heapMapping.dot"));
			bufw.write(b.toString());
			bufw.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	public void dumpAllMemLocsToFile() {
		StringBuilder b = new StringBuilder("");
		for (AbstractMemLoc loc : memLocFactory.keySet()) {
			b.append(loc + "\n");
		}

		try {
			BufferedWriter bufw = new BufferedWriter(new FileWriter(
					"output/createdLocations.dot"));
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
				b.append(" [shape=circle,label=\"");
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
			} else if (loc instanceof NullElem) {
				b.append("  ").append("\"" + loc + "\"");
				b.append(" [shape=point,label=\"");
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
				b.append("  ").append("\"" + loc + "\"");
				b.append(" -> ").append("\"" + hObj + "\"")
						.append(" [label=\"");
				b.append(f);
				b.append("\"]\n");
			}
		}

		b.append("}\n");

		try {
			BufferedWriter bufw = new BufferedWriter(new FileWriter(
					"/Users/xwang/xwang/Research/Projects/scuba/Scuba/output/abstractHeap"
							+ count + ".dot"));
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
				b.append(" [shape=circle,label=\"");
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
			} else if (loc instanceof NullElem) {
				b.append("  ").append("\"" + loc + "\"");
				b.append(" [shape=point,label=\"");
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

				for (HeapObject obj : p2Set.getHeapObjects()) {
					b.append("  ").append("\"" + loc + "\"");
					b.append(" -> ").append("\"" + obj + "\"")
							.append(" [label=\"");
					b.append(f);
					b.append("\"]\n");
				}
			}
		}

		b.append("}\n");

		try {
			BufferedWriter bufw = new BufferedWriter(new FileWriter(
					"/Users/xwang/xwang/Research/Projects/scuba/Scuba/output/allMemLocs"
							+ count + ".dot"));
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

		assert (!(loc instanceof NullElem)) : "we cannot get some filed of a null pointer!";
		Pair<AbstractMemLoc, FieldElem> pair = new Pair<AbstractMemLoc, FieldElem>(
				loc, field);
		if (loc.isArgDerived()) {

			// get the default target given the memory location and the field
			HeapObject defaultTarget = getDefaultTarget(loc, field);

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
			// assert (heapObjectsToP2Set.containsKey(pair)) : loc
			// + " with field " + field + " should have a p2 set?";
			P2Set ret = heapObjectsToP2Set.get(pair);
			if (ret != null) {
				return ret;
			} else {
				return new P2Set(getAbstractMemLoc());
			}
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

			// TODO
			// if we want to lookup some field of a null pointer
			// just ignore and jump to the next one
			if (obj instanceof NullElem)
				continue;

			P2Set tgt = lookup(obj, field);
			P2Set projP2Set = P2SetHelper.project(tgt, cst);
			ret.join(projP2Set);
		}

		return ret;
	}

	public void handleALoadStmt(Quad stmt) {

	}

	public void handleAStoreStmt(Quad stmt) {

	}

	// v1 = v2.f
	public void handleGetfieldStmt(Quad stmt) {
		assert (stmt.getOperator() instanceof Getfield);
		RegisterOperand lhs = Getfield.getDest(stmt);
		RegisterOperand rhsBase = (RegisterOperand) Getfield.getBase(stmt);
		FieldOperand rhsField = Getfield.getField(stmt);
		jq_Method meth = stmt.getMethod();
		VariableType lvt = getVarType(stmt.getMethod(), lhs.getRegister());
		VariableType rvt = getVarType(stmt.getMethod(), rhsBase.getRegister());

		boolean flag = this.handleLoadStmt(meth.getDeclaringClass(), meth,
				lhs.getRegister(), lvt, rhsBase.getRegister(),
				rhsField.getField(), rvt);
		isChanged = (flag || isChanged);

	}

	//v = A.f.
	public void handleGetstaticStmt(Quad stmt) {
		jq_Method meth = stmt.getMethod();
		RegisterOperand lhs = Getstatic.getDest(stmt);
		FieldOperand field = Getstatic.getField(stmt);
		jq_Class encloseClass = field.getField().getDeclaringClass();
		VariableType lvt = getVarType(stmt.getMethod(), lhs.getRegister());

		boolean flag = handleStatLoadStmt(meth.getDeclaringClass(), meth, lhs.getRegister(),
				lvt, encloseClass, field.getField());
		isChanged = (flag || isChanged);
	}

	public void handleInvokeStmt(Quad stmt) {

	}

	public void handleMemLoadStmt(Quad stmt) {

	}

	public void handleMemStoreStmt(Quad stmt) {

	}

	// v1 = v2.
	public void handleMoveStmt(Quad stmt) {
		jq_Method meth = stmt.getMethod();
		Operand rhso = Putfield.getSrc(stmt);
		if (rhso instanceof RegisterOperand) {
			RegisterOperand rhs = (RegisterOperand) Move.getSrc(stmt);
			RegisterOperand lhs = (RegisterOperand) Move.getDest(stmt);
			VariableType lvt = getVarType(stmt.getMethod(), lhs.getRegister());
			VariableType rvt = getVarType(stmt.getMethod(), rhs.getRegister());

			boolean flag = handleAssgnStmt(meth.getDeclaringClass(), meth,
					lhs.getRegister(), lvt, rhs.getRegister(), rvt);
			isChanged = (flag || isChanged);
		} else if (rhso instanceof AConstOperand) {
			//need xinyu's support for v1=null
		}

	}

	public void handleMultiNewArrayStmt(Quad stmt) {
		assert (stmt.getOperator() instanceof MultiNewArray);
		jq_Method meth = stmt.getMethod();
		TypeOperand to = MultiNewArray.getType(stmt);
		RegisterOperand rop = MultiNewArray.getDest(stmt);
		VariableType vt = getVarType(meth, rop.getRegister());

		boolean flag = handleNewStmt(stmt.getMethod().getDeclaringClass(),
				meth, rop.getRegister(), vt, to.getType(), stmt.getLineNumber());
		isChanged = (flag || isChanged);
	}

	// v1 = new A();
	public void handleNewStmt(Quad stmt) {
		assert (stmt.getOperator() instanceof New);
		jq_Method meth = stmt.getMethod();
		TypeOperand to = New.getType(stmt);
		RegisterOperand rop = New.getDest(stmt);
		VariableType vt = getVarType(meth, rop.getRegister());

		boolean flag = handleNewStmt(stmt.getMethod().getDeclaringClass(),
				meth, rop.getRegister(), vt, to.getType(), stmt.getLineNumber());
		isChanged = (flag || isChanged);
	}

	//v = new Array(); is it ok if we use the same handler as handlerNew for array?
	public void handleNewArrayStmt(Quad stmt) {
		assert (stmt.getOperator() instanceof NewArray);
		jq_Method meth = stmt.getMethod();
		TypeOperand to = NewArray.getType(stmt);
		RegisterOperand rop = NewArray.getDest(stmt);
		VariableType vt = getVarType(meth, rop.getRegister());

		boolean flag = handleNewStmt(stmt.getMethod().getDeclaringClass(),
				meth, rop.getRegister(), vt, to.getType(), stmt.getLineNumber());
		isChanged = (flag || isChanged);
	}

	// v1.f = v2
	public void handlePutfieldStmt(Quad stmt) {
		assert (stmt.getOperator() instanceof Putfield);
		jq_Method meth = stmt.getMethod();
		boolean flag;
		Operand rhso = Putfield.getSrc(stmt);
		if (rhso instanceof RegisterOperand) {
			RegisterOperand rhs = (RegisterOperand) rhso;
			RegisterOperand lhs = (RegisterOperand) Putfield.getBase(stmt);
			FieldOperand field = Putfield.getField(stmt);
			VariableType lvt = getVarType(stmt.getMethod(), lhs.getRegister());
			VariableType rvt = getVarType(stmt.getMethod(), rhs.getRegister());

			flag = this.handleStoreStmt(meth.getDeclaringClass(), meth,
					lhs.getRegister(), lvt, field.getField(),
					rhs.getRegister(), rvt);
			isChanged = (flag || isChanged);
		} else if (rhso instanceof AConstOperand) {
			AConstOperand rhs = (AConstOperand) rhso;
			// handle v.f = null;
			if (rhs.getType() instanceof jq_NullType) {
				RegisterOperand lhs = (RegisterOperand) Putfield.getBase(stmt);
				FieldOperand field = Putfield.getField(stmt);
				VariableType lvt = getVarType(stmt.getMethod(),
						lhs.getRegister());
				VariableType rvt = VariableType.NULL_POINTER;

				flag = this.handleStoreStmt(meth.getDeclaringClass(), meth,
						lhs.getRegister(), lvt, field.getField(), rhs, rvt);
				isChanged = (flag || isChanged);
			} else {
				// for now, ignore string case.
			}

		} else {
			assert false : "strange case that we haven't consider.";
		}
	}

	//A.f = b;
	public void handlePutstaticStmt(Quad stmt) {
		jq_Method meth = stmt.getMethod();
		Operand rhso = Putstatic.getSrc(stmt);
		FieldOperand field = Putstatic.getField(stmt);
		jq_Class encloseClass = field.getField().getDeclaringClass();
		boolean flag;
		
		if (rhso instanceof RegisterOperand) {
			RegisterOperand rhs = (RegisterOperand) rhso;
			VariableType rvt = getVarType(stmt.getMethod(), rhs.getRegister());
			
			flag = handleStaticStoreStmt(meth.getDeclaringClass(), meth, encloseClass,
					field.getField(), rhs.getRegister(), rvt);
			isChanged = (flag || isChanged);

		} else if (rhso instanceof AConstOperand) {
			AConstOperand rhs = (AConstOperand) rhso;
			// handle v.f = null;
			if (rhs.getType() instanceof jq_NullType) {
				flag = handleStaticStoreStmt(meth.getDeclaringClass(), meth,
						encloseClass, field.getField(), rhs,
						VariableType.NULL_POINTER);
				isChanged = (flag || isChanged);
			} else {
				// for now, ignore string case. like A.f = "";
			}
		} else {
			assert false : "strange case that we haven't consider.";
		}

	}

	public void handleReturnStmt(Quad stmt) {

	}

	// is this a param or local. helper function.
	public VariableType getVarType(jq_Method meth, Register r) {
		VariableType vt = VariableType.LOCAL_VARIABLE;

		ControlFlowGraph cfg = meth.getCFG();
		RegisterFactory rf = cfg.getRegisterFactory();
		int numArgs = meth.getParamTypes().length;
		for (int zIdx = 0; zIdx < numArgs; zIdx++) {
			Register v = rf.get(zIdx);
			if (v.equals(r)) {
				vt = VariableType.PARAMEMTER;
				break;
			}
		}
		return vt;
	}

	// handleAssgnStmt implements rule (1) in Figure 8 of the paper
	// v1 = v2
	protected boolean handleAssgnStmt(jq_Class clazz, jq_Method method,
			Register left, VariableType leftVType, Register right,
			VariableType rightVType) {
		StackObject v1 = getAbstractMemLoc(clazz, method, left, leftVType);
		assert v1.knownArgDerived();
		StackObject v2 = getAbstractMemLoc(clazz, method, right, rightVType);
		assert v2.knownArgDerived();

		P2Set p2Setv2 = lookup(v2, new EpsilonFieldElem());
		Pair<AbstractMemLoc, FieldElem> pair = new Pair<AbstractMemLoc, FieldElem>(
				v1, new EpsilonFieldElem());
		return weakUpdate(pair, p2Setv2);
	}

	// handleLoadStmt implements rule (2) in Figure 8 of the paper
	// v1 = v2.f
	protected boolean handleLoadStmt(jq_Class clazz, jq_Method method,
			Register left, VariableType leftVType, Register rightBase,
			jq_Field rightField, VariableType rightBaseVType) {
		StackObject v1 = getAbstractMemLoc(clazz, method, left, leftVType);
		StackObject v2 = getAbstractMemLoc(clazz, method, rightBase,
				rightBaseVType);
		P2Set p2Setv2 = lookup(v2, new EpsilonFieldElem());
		NormalFieldElem f = new NormalFieldElem(rightField);
		P2Set p2Setv2Epsilon = lookup(p2Setv2, f);
		Pair<AbstractMemLoc, FieldElem> pair = new Pair<AbstractMemLoc, FieldElem>(
				v1, new EpsilonFieldElem());
		return weakUpdate(pair, p2Setv2Epsilon);
	}

	// handleLoadStmt implements rule (2) in Figure 8 of the paper
	// v1 = A.f, where A is a class and f is a static field
	protected boolean handleStatLoadStmt(jq_Class clazz, jq_Method method,
			Register left, VariableType leftVType, jq_Class rightBase,
			jq_Field rightField) {
		StackObject v1 = getAbstractMemLoc(clazz, method, left, leftVType);
		StaticElem A = getAbstractMemLoc(rightBase);
		P2Set p2SetA = lookup(A, new EpsilonFieldElem());
		NormalFieldElem f = new NormalFieldElem(rightField);
		P2Set p2SetAf = lookup(p2SetA, f);
		Pair<AbstractMemLoc, FieldElem> pair = new Pair<AbstractMemLoc, FieldElem>(
				v1, new EpsilonFieldElem());
		return weakUpdate(pair, p2SetAf);
	}

	// handleStoreStmt implements rule (3) in Figure 8 of the paper
	// v1.f = v2
	protected boolean handleStoreStmt(jq_Class clazz, jq_Method method,
			Register leftBase, VariableType leftBaseVType, jq_Field leftField,
			Register right, VariableType rightVType) {

		assert (rightVType == VariableType.PARAMEMTER)
				|| (rightVType == VariableType.LOCAL_VARIABLE) : "we are only considering local"
				+ " variables and parameters as RHS";
		assert (leftBaseVType == VariableType.PARAMEMTER)
				|| (leftBaseVType == VariableType.LOCAL_VARIABLE) : "we are only considering local"
				+ " variables and parameters as LHS";

		boolean ret = false;
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
			ret = weakUpdate(pair, projP2Set) | ret;
		}
		return ret;
	}

	// handleStoreStmt implements rule (3) in Figure 8 of the paper
	// A.f = v2
	protected boolean handleStaticStoreStmt(jq_Class clazz, jq_Method method,
			jq_Class leftBase, jq_Field leftField, Register right,
			VariableType rightVType) {

		assert (rightVType == VariableType.PARAMEMTER)
				|| (rightVType == VariableType.LOCAL_VARIABLE) : "we are only considering local"
				+ " variables and parameters as RHS";

		boolean ret = false;
		StaticElem v1 = getAbstractMemLoc(leftBase);
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
			ret = weakUpdate(pair, projP2Set) | ret;
		}
		return ret;
	}

	// v1.f = constant operand
	// (1). v1.f = null
	protected boolean handleStoreStmt(jq_Class clazz, jq_Method method,
			Register leftBase, VariableType leftBaseVType, jq_Field leftField,
			AConstOperand right, VariableType rightVType) {

		assert (rightVType == VariableType.NULL_POINTER) : "we are only considering null pointer!";
		assert (leftBaseVType == VariableType.PARAMEMTER)
				|| (leftBaseVType == VariableType.LOCAL_VARIABLE) : "we are only considering local"
				+ " variables and parameters as LHS";

		boolean ret = false;
		StackObject v1 = getAbstractMemLoc(clazz, method, leftBase,
				leftBaseVType);
		HeapObject v2 = getAbstractMemLoc(clazz, method, right, rightVType);
		P2Set p2Setv1 = lookup(v1, new EpsilonFieldElem());
		P2Set p2Setv2 = new P2Set(v2);
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

	// v1.f = constant operand
	// (1). A.f = null
	protected boolean handleStaticStoreStmt(jq_Class clazz, jq_Method method,
			jq_Class leftBase, jq_Field leftField, AConstOperand right,
			VariableType rightVType) {

		assert (rightVType == VariableType.NULL_POINTER) : "we are only considering null pointer!";

		boolean ret = false;
		StaticElem v1 = getAbstractMemLoc(leftBase);
		HeapObject v2 = getAbstractMemLoc(clazz, method, right, rightVType);
		P2Set p2Setv1 = lookup(v1, new EpsilonFieldElem());
		P2Set p2Setv2 = new P2Set(v2);
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

	// handleNewStmt implements rule (4) in Figure 8 of the paper
	// v = new T
	protected boolean handleNewStmt(jq_Class clazz, jq_Method method,
			Register left, VariableType leftVType, jq_Type right, int line) {
		AllocElem allocT = getAbstractMemLoc(clazz, method, right, line);
		StackObject v = getAbstractMemLoc(clazz, method, left, leftVType);
		Pair<AbstractMemLoc, FieldElem> pair = new Pair<AbstractMemLoc, FieldElem>(
				v, new EpsilonFieldElem());
		return weakUpdate(pair, new P2Set(allocT, ConstraintManager.genTrue()));
	}

	// check whether some abstract memory location is contained in the heap
	public boolean hasCreated(AbstractMemLoc loc) {
		return memLocFactory.containsKey(loc);
	}

	// check whether some abstract memory location is in the heap
	public boolean isInHeap(AbstractMemLoc loc) {
		return heap.contains(loc);
	}

	protected AccessPath getAbstractMemLoc(AbstractMemLoc base, FieldElem field) {
		AccessPath ret = null;
		if (base instanceof HeapObject) {
			ret = getAbstractMemLoc(((HeapObject) base), field);
		} else if (base instanceof ParamElem) {
			ret = getAbstractMemLoc(((ParamElem) base), field);
		} else if (base instanceof LocalVarElem) {
			assert false : base + " can NOT be a LocalVarElem";
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

		ArgDerivedHelper.markArgDerived(ret);
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

		ArgDerivedHelper.markArgDerived(ret);
		memLocFactory.put(ret, ret);

		return ret;
	}

	protected AccessPath getAbstractMemLoc(AccessPath path) {
		if (memLocFactory.containsKey(path)) {
			return (AccessPath) memLocFactory.get(path);
		}

		ArgDerivedHelper.markArgDerived(path);
		memLocFactory.put(path, path);

		return path;
	}

	// given a variable (maybe parameter or local variable) and the type
	// (parameter/local), generate (if not yet existed in the memory location
	// factory) the corresponding memory location (and at the same time put it
	// into the factory), OR get the corresponding memory location (if it has
	// been created and put into the factory)
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
		} else if (vType == VariableType.PARAMEMTER) {
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
		} else {
			assert false : "wried things!";
			return null;
		}
	}

	// TODO maybe we need to handle more AConstOperand cases
	// if we only handle the null pointer case, we can return NullElem
	protected HeapObject getAbstractMemLoc(jq_Class clazz, jq_Method method,
			AConstOperand variable, VariableType vType) {
		if (vType == VariableType.NULL_POINTER) {
			return getAbstractMemLoc();
		} else {
			assert false : "wried things! Unknown Type";
			return null;
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

	protected NullElem getAbstractMemLoc() {
		NullElem ret = new NullElem();
		if (memLocFactory.containsKey(ret)) {
			return (NullElem) memLocFactory.get(ret);
		}

		ArgDerivedHelper.markArgDerived(ret);
		memLocFactory.put(ret, ret);

		return ret;
	}

	protected boolean strongUpdate(Pair<AbstractMemLoc, FieldElem> pair,
			P2Set p2Set) {
		assert (pair.val0 instanceof StackObject) : "Only stack objects can do strong update!";
		assert (pair.val1 instanceof EpsilonFieldElem) : "Only stack objects with epsilon field"
				+ " can do strong update!";
		pair.val0.addField(pair.val1);
		heapObjectsToP2Set.put(pair, p2Set);
		return false;
	}

	protected HeapObject getDefaultTarget(AbstractMemLoc loc, FieldElem field) {
		AccessPath ret = null;
		if (loc.isArgDerived()) {
			if (loc.hasFieldSelector(field)) {
				// only AccessPath has field selectors
				AccessPath path = ((AccessPath) loc).getPrefix(field);
				ret = getAbstractMemLoc(path);
			} else {
				ret = getAbstractMemLoc(loc, field);
			}
		}

		return ret;
	}

	protected boolean weakUpdate(Pair<AbstractMemLoc, FieldElem> pair,
			P2Set p2Set) {
		boolean ret = false;
		P2Set currentHeap = null;
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
		if (heapObjectsToP2Set.containsKey(pair)) {
			currentHeap = heapObjectsToP2Set.get(pair);
		} else {
			currentHeap = new P2Set();
			heapObjectsToP2Set.put(pair, currentHeap);
		}

		// update the locations in the real heap graph
		heap.add(pair.val0);
		heap.addAll(p2Set.getHeapObjects());

		ret = currentHeap.join(p2Set);

		return ret;
	}

	protected void cleanup(P2Set p2Set, Pair<AbstractMemLoc, FieldElem> pair) {
		if (p2Set == null)
			return;

		HeapObject defaultTarget = getDefaultTarget(pair.val0, pair.val1);
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
}
