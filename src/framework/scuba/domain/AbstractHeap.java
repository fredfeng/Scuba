package framework.scuba.domain;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import joeq.Class.jq_Array;
import joeq.Class.jq_Class;
import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.BytecodeToQuad.jq_ReturnAddressType;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.program.Program;
import chord.util.tuple.object.Pair;
import chord.util.tuple.object.Trio;

import com.microsoft.z3.BoolExpr;

import framework.scuba.helper.ArgDerivedHelper;
import framework.scuba.helper.ConstraintManager;
import framework.scuba.helper.G;
import framework.scuba.helper.P2SetHelper;
import framework.scuba.utils.StringUtil;

public class AbstractHeap extends Heap {
	// the summary this heap belongs to
	protected final Summary summary;

	// all locations in the heap including both keys and values
	public final Set<AbsMemLoc> heap;

	// a mapping described in Figure 7 of the paper
	public final Map<Pair<AbsMemLoc, FieldElem>, P2Set> locToP2Set;

	// the locations that are originally produced by the method this abstract
	// heap belongs to, particularly the locations includes:
	// 1. <LocalVarElem> which are declared in this method
	// 2. <ParamElem> which are formals of this method
	// 3. <RetElem> if this method has a return value
	// 4. <AllocElem> that are allocated in this method and propagated from the
	// methods that can be transitively called by this method (we record the
	// context of the AllocElem so that we should regard as them being
	// "produced" by this method, although they are originally allocated by
	// other methods potentially)
	// 5. <AccessPath> that
	private final Map<AbsMemLoc, AbsMemLoc> memLocFactory;

	// whether the heap and summary has been changed: (heap, summary)
	private Pair<Boolean, Boolean> isChanged = new Pair<Boolean, Boolean>(
			false, false);

	// the locations in this set will be propagated to the caller
	// 1. <AccessPath> 2. <ParamElem> 3. <StaticElem> 4. <RetElem>
	// 5. <AllocElem> that are connected by 1-4 will be propagated
	// 6. others will not be propagated
	public Set<AbsMemLoc> toProp = new HashSet<AbsMemLoc>();

	public static enum VariableType {
		PARAMEMTER, LOCAL_VARIABLE;
	}

	public AbstractHeap(Summary summary) {
		this.summary = summary;
		this.heap = new HashSet<AbsMemLoc>();
		this.locToP2Set = new HashMap<Pair<AbsMemLoc, FieldElem>, P2Set>();
		this.memLocFactory = new HashMap<AbsMemLoc, AbsMemLoc>();
	}

	public jq_Method getMethod() {
		return this.summary.getMethod();
	}

	public Set<Pair<AbsMemLoc, FieldElem>> keySet() {
		return locToP2Set.keySet();
	}

	public P2Set get(Pair<AbsMemLoc, FieldElem> pair) {
		return locToP2Set.get(pair);
	}

	// lookup for p2set which is described in definition 7 of the paper
	public P2Set lookup(AbsMemLoc loc, FieldElem field) {
		// create a pair wrapper for lookup
		Pair<AbsMemLoc, FieldElem> pair = new Pair<AbsMemLoc, FieldElem>(loc,
				field);
		if (loc.isArgDerived()) {
			// get the default target given the memory location and the field
			AccessPath defaultTarget = getDefaultTarget(loc, field);

			// always find the default p2set of (loc, field)
			P2Set defaultP2Set = new P2Set(defaultTarget);
			// return the p2set always including the default p2set
			if (locToP2Set.containsKey(pair)) {
				return P2SetHelper.join(locToP2Set.get(pair), defaultP2Set);
			} else {
				return defaultP2Set;
			}
		} else if (loc.isNotArgDerived()) {
			// it is possible to have null pointers
			P2Set ret = locToP2Set.get(pair);
			if (ret != null) {
				// if the field of this memory does point to something, return
				// that memory location (cloned to avoid wired bugs)
				return ret.clone();
			} else {
				// if the field of this memory does NOT point to anything, just
				// return an empty P2Set (think about lacking models)
				return new P2Set();
			}
		} else {
			assert false : "you have not mark the argument derived marker before lookup!";
		}
		assert false : "you have not mark the argument derived marker before lookup!";
		return null;
	}

	// generalized lookup for p2set described in definition 10 of the paper
	public P2Set lookup(P2Set p2Set, FieldElem field) {
		// it is possible to have null pointers that are dereferenced if we
		// think about lacking models, just return an empty p2set
		P2Set ret = new P2Set();

		for (HeapObject obj : p2Set.keySet()) {
			BoolExpr cst = p2Set.get(obj);

			P2Set tgt = lookup(obj, field);
			assert (tgt != null) : "get a null p2 set!";
			P2Set projP2Set = P2SetHelper.project(tgt, cst);

			ret.join(projP2Set, this);
		}

		return ret;
	}

	// this lookup is used for instantiating memory locations
	public MemLocInstnSet instnLookup(MemLocInstnSet instnLocSet,
			FieldElem field) {
		MemLocInstnSet ret = new MemLocInstnSet();

		for (AbsMemLoc loc : instnLocSet.keySet()) {
			BoolExpr cst = instnLocSet.get(loc);

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
	public Pair<Boolean, Boolean> handleAssignStmt(jq_Class clazz,
			jq_Method method, Register left, VariableType leftVType,
			jq_Type leftType, Register right, VariableType rightVType,
			jq_Type rightType) {

		Pair<Boolean, Boolean> ret = new Pair<Boolean, Boolean>(false, false);

		assert (leftVType == VariableType.LOCAL_VARIABLE || leftVType == VariableType.PARAMEMTER) : ""
				+ "for Assign stmt, LHS must be LocalElem (or ParamElem, we HAVE NOT fully fixed SSA";
		assert (rightVType == VariableType.LOCAL_VARIABLE || rightVType == VariableType.PARAMEMTER) : ""
				+ "for Assign stmt, rhs must be LocalElem or ParamElem!";

		// generates StackObject (either ParamElem or LocalVarElem)
		StackObject v1 = null, v2 = null;

		// generate memory location for lhs
		if (leftVType == VariableType.PARAMEMTER) {
			v1 = getParamElem(clazz, method, left, leftType);
		} else if (leftVType == VariableType.LOCAL_VARIABLE) {
			v1 = getLocalVarElem(clazz, method, left, leftType);
		} else {
			assert false : "wired thing! For assign stmt, LHS must be LocalElem!";
		}
		assert (v1 != null) : "v1 is null!";

		// generate the memory location for rhs
		if (rightVType == VariableType.PARAMEMTER) {
			v2 = getParamElem(clazz, method, right, rightType);
		} else if (rightVType == VariableType.LOCAL_VARIABLE) {
			v2 = getLocalVarElem(clazz, method, right, rightType);
		} else {
			assert false : "for assign stmt, rhs must be LocalElem or ParamElem!";
		}
		assert (v2 != null) : "v2 is null!";

		assert v1.knownArgDerived() : "we should set arg-derived marker for v1!";
		assert v2.knownArgDerived() : "we should set arg-derived marker for v2!";

		P2Set p2Setv2 = lookup(v2, EpsilonFieldElem.getEpsilonFieldElem());
		assert (p2Setv2 != null) : "get a null p2 set!";

		Pair<AbsMemLoc, FieldElem> pair = new Pair<AbsMemLoc, FieldElem>(v1,
				EpsilonFieldElem.getEpsilonFieldElem());

		Pair<Boolean, Boolean> res = weakUpdate(pair, p2Setv2);
		ret.val0 = res.val0;
		ret.val1 = res.val1;

		return ret;
	}

	public Pair<Boolean, Boolean> handleCheckCastStmt(jq_Class clazz,
			jq_Method method, Register left, VariableType leftVType,
			jq_Type leftType, Register right, VariableType rightVType,
			jq_Type rightType) {
		Pair<Boolean, Boolean> ret = new Pair<Boolean, Boolean>(false, false);
		assert (leftVType == VariableType.LOCAL_VARIABLE || leftVType == VariableType.PARAMEMTER) : ""
				+ "for CheckCast stmt, LHS must be LocalElem (or ParamElem, we HAVE NOT fully fixed SSA";
		assert (rightVType == VariableType.LOCAL_VARIABLE || rightVType == VariableType.PARAMEMTER) : ""
				+ "for CheckCast stmt, rhs must be LocalElem or ParamElem!";

		// generates StackObject (either ParamElem or LocalVarElem)
		StackObject v2 = null;
		// generate the memory location for rhs
		if (rightVType == VariableType.PARAMEMTER) {
			v2 = getParamElem(clazz, method, right, rightType);
		} else if (rightVType == VariableType.LOCAL_VARIABLE) {
			v2 = getLocalVarElem(clazz, method, right, rightType);
		} else {
			assert false : "for CheckCast stmt, rhs must be LocalElem or ParamElem!";
		}
		assert (v2 != null) : "v2 is null!";
		assert v2.knownArgDerived() : "we should set arg-derived marker for v2!";

		// change v2's static type
		// v2.setType(leftType);

		// do an extra assignment
		Pair<Boolean, Boolean> res = handleAssignStmt(clazz, method, left,
				leftVType, leftType, right, rightVType, rightType);
		ret.val0 = res.val0;
		ret.val1 = res.val1;

		return ret;
	}

	// this method is just a helper method for handling array allocations
	private Pair<Boolean, Boolean> handleArrayLoad(AllocElem left,
			IndexFieldElem index, AllocElem right) {
		Pair<Boolean, Boolean> ret = new Pair<Boolean, Boolean>(false, false);
		Pair<AbsMemLoc, FieldElem> pair = new Pair<AbsMemLoc, FieldElem>(left,
				index);
		P2Set p2Set = new P2Set(right, ConstraintManager.genTrue());
		assert p2Set != null : "p2 set can not be null!";
		Pair<Boolean, Boolean> res = weakUpdate(pair, p2Set);
		ret.val0 = res.val0;
		ret.val1 = res.val1;
		return ret;
	}

	// handleLoadStmt implements rule (2) in Figure 8 of the paper
	// v1 = v2.f
	// v1: parameter / local (for SSA, only local)
	// v2: parameter / local
	// f: non-static field
	public Pair<Boolean, Boolean> handleLoadStmt(jq_Class clazz,
			jq_Method method, Register left, VariableType leftVType,
			jq_Type leftType, Register rightBase, jq_Field rightField,
			VariableType rightBaseVType, jq_Type rightBaseType) {
		Pair<Boolean, Boolean> ret = new Pair<Boolean, Boolean>(false, false);

		assert (leftVType == VariableType.LOCAL_VARIABLE) : "for non-static load stmt, LHS must be LocalElem";
		assert (rightBaseVType == VariableType.LOCAL_VARIABLE)
				|| (rightBaseVType == VariableType.PARAMEMTER) : ""
				+ "for non-static stmt, rhs BASE must be LocalElem or ParamElem!";

		// generates StackObject (either ParamElem or LocalVarElem)
		LocalVarElem v1 = null;
		StackObject v2 = null;

		// generate the memory location for lhs
		if (leftVType == VariableType.PARAMEMTER) {
			assert false : "for non-static load stmt, LHS must be LocalElem";
		} else if (leftVType == VariableType.LOCAL_VARIABLE) {
			v1 = getLocalVarElem(clazz, method, left, leftType);
		} else {
			assert false : "wired thing! For non-static load stmt, LHS must be LocalElem!";
		}
		assert (v1 != null) : "v1 is null!";

		// generate the memory location for rhs's base
		if (rightBaseVType == VariableType.PARAMEMTER) {
			v2 = getParamElem(clazz, method, rightBase, rightBaseType);
		} else if (leftVType == VariableType.LOCAL_VARIABLE) {
			v2 = getLocalVarElem(clazz, method, rightBase, rightBaseType);
		} else {
			assert false : "for non-static load stmt, rhs BASE must be LocalElem or ParamElem!";
		}
		assert (v2 != null) : "v2 is null!";

		assert v1.knownArgDerived() : "we should set the arg-derived marker when creating v1";
		assert v2.knownArgDerived() : "we should set the arg-derived marker when creating v2";

		P2Set p2Setv2 = lookup(v2, EpsilonFieldElem.getEpsilonFieldElem());
		assert (p2Setv2 != null) : "get a null p2 set!";

		NormalFieldElem f = Env.getNormalFieldElem(rightField);
		P2Set p2Setv2f = lookup(p2Setv2, f);
		assert (p2Setv2f != null) : "get a null p2 set!";

		Pair<AbsMemLoc, FieldElem> pair = new Pair<AbsMemLoc, FieldElem>(v1,
				EpsilonFieldElem.getEpsilonFieldElem());
		Pair<Boolean, Boolean> res = weakUpdate(pair, p2Setv2f);
		ret.val0 = res.val0;
		ret.val1 = res.val1;
		return ret;
	}

	// v1 = v2[0] where v2 is an array, e.g. v2 = new X[10][10]
	// treat it just like a load stmt: v1 = v2.\i where \i is the index field
	public Pair<Boolean, Boolean> handleALoadStmt(jq_Class clazz,
			jq_Method method, Register left, VariableType leftVType,
			jq_Type leftType, Register rightBase, VariableType rightBaseVType,
			jq_Type rightBaseType) {

		Pair<Boolean, Boolean> ret = new Pair<Boolean, Boolean>(false, false);
		assert (leftVType == VariableType.LOCAL_VARIABLE) : "for array load stmt, LHS must be LocalElem";
		assert (rightBaseVType == VariableType.LOCAL_VARIABLE)
				|| (rightBaseVType == VariableType.PARAMEMTER) : ""
				+ "for array stmt, rhs BASE must be either LocalVarElem or ParamElem!";

		LocalVarElem v1 = getLocalVarElem(clazz, method, left, leftType);
		LocalVarElem v2 = getLocalVarElem(clazz, method, rightBase,
				rightBaseType);

		assert (v1 != null) : "v1 is null!";
		assert (v2 != null) : "v2 is null!";

		assert (v1.knownArgDerived()) : "we should mark arg-derived marker before using v1!";
		assert (v2.knownArgDerived()) : "we should mark arg-derived marker before using v2!";

		P2Set p2Setv2 = lookup(v2, EpsilonFieldElem.getEpsilonFieldElem());
		assert (p2Setv2 != null) : "get a null p2 set!";

		// TODO need to verifying this getArrayType method for Chord
		assert (v2.getType() instanceof jq_Array) : "rhs of ALoad must be jq_Array!";
		IndexFieldElem index = IndexFieldElem.getIndexFieldElem();
		P2Set p2Setv2i = lookup(p2Setv2, index);
		assert (p2Setv2i != null) : "get a null p2 set!";

		Pair<AbsMemLoc, FieldElem> pair = new Pair<AbsMemLoc, FieldElem>(v1,
				EpsilonFieldElem.getEpsilonFieldElem());

		Pair<Boolean, Boolean> res = weakUpdate(pair, p2Setv2i);
		ret.val0 = res.val0;
		ret.val1 = res.val1;
		return ret;
	}

	// handleLoadStmt implements rule (2) in Figure 8 of the paper
	// v1 = A.f, where A is a class and f is a static field
	// v1: parameter / local (for SSA, only local_
	// A: jq_Class
	// f: a static field declared in class A
	// although this is a load stmt, we regard it as an Assign stmt by following
	// v1 = (A.f) where A.f is just a stack object
	public Pair<Boolean, Boolean> handleStatLoadStmt(jq_Class clazz,
			jq_Method method, Register left, VariableType leftVType,
			jq_Type leftType, jq_Class rightBase, jq_Field rightField) {

		Pair<Boolean, Boolean> ret = new Pair<Boolean, Boolean>(false, false);
		assert (leftVType == VariableType.LOCAL_VARIABLE) : "for static load stmt, LHS must be a local!";

		StackObject v1 = null;
		// generate the memory location for lhs
		if (leftVType == VariableType.PARAMEMTER) {
			assert false : "for static load stmt, LHS must be LocalElem";
		} else if (leftVType == VariableType.LOCAL_VARIABLE) {
			v1 = getLocalVarElem(clazz, method, left, leftType);
		} else {
			assert false : "wired thing! For static load stmt, LHS must be LocalElem!";
		}
		assert (v1 != null) : "v1 is null!";

		// generate the memory location for rhs's base
		StaticElem v2 = Env.getStaticElem(rightBase, rightField);
		memLocFactory.put(v2, v2);

		assert (v2 != null) : "v2 is null!";

		assert v1.knownArgDerived() : "we should set the arg-derived marker when creating v1";
		assert v2.knownArgDerived() : "we should set the arg-derived marker when creating v2";

		P2Set p2Setv2 = lookup(v2, EpsilonFieldElem.getEpsilonFieldElem());
		assert (p2Setv2 != null) : "get a null p2 set!";

		Pair<AbsMemLoc, FieldElem> pair = new Pair<AbsMemLoc, FieldElem>(v1,
				EpsilonFieldElem.getEpsilonFieldElem());

		Pair<Boolean, Boolean> res = weakUpdate(pair, p2Setv2);
		ret.val0 = res.val0;
		ret.val1 = res.val1;
		return ret;
	}

	// v1[0] = v2 where v1 = new V[10][10]
	// treat it just as a store stmt like: v1.\i = v2 where \i is the index
	// field (all array base shares the same \i)
	public Pair<Boolean, Boolean> handleAStoreStmt(jq_Class clazz,
			jq_Method method, Register leftBase, VariableType leftBaseVType,
			jq_Type leftBaseType, Register right, VariableType rightVType,
			jq_Type rightType) {

		Pair<Boolean, Boolean> ret = new Pair<Boolean, Boolean>(false, false);

		// just temporily use this
		if (!(leftBaseType instanceof jq_Array)) {
			return ret;
		}

		assert (rightVType == VariableType.PARAMEMTER)
				|| (rightVType == VariableType.LOCAL_VARIABLE) : "we are only considering local"
				+ " variables and parameters as rhs";
		assert (leftBaseVType == VariableType.PARAMEMTER)
				|| (leftBaseVType == VariableType.LOCAL_VARIABLE) : "we are only considering local"
				+ " variables and parameters as LHS Base";

		StackObject v1 = null, v2 = null;

		// generate the memory location for rhs
		if (leftBaseVType == VariableType.PARAMEMTER) {
			v1 = getParamElem(clazz, method, leftBase, leftBaseType);
		} else if (leftBaseVType == VariableType.LOCAL_VARIABLE) {
			v1 = getLocalVarElem(clazz, method, leftBase, leftBaseType);
		} else {
			assert false : "wired thing! For array store stmt,"
					+ " LHS Base must be LocalElem or ParamElem!";
		}
		assert (v1 != null) : "v1 is null!";

		// generate the memory location for rhs's base
		if (rightVType == VariableType.PARAMEMTER) {
			v2 = getParamElem(clazz, method, right, rightType);
		} else if (rightVType == VariableType.LOCAL_VARIABLE) {
			v2 = getLocalVarElem(clazz, method, right, rightType);
		} else {
			assert false : "for non-static store stmt, rhs must be LocalElem or ParamElem!";
		}
		assert (v2 != null) : "v2 is null!";

		assert v1.knownArgDerived() : "we should set the arg-derived marker when creating v1";
		assert v2.knownArgDerived() : "we should set the arg-derived marker when creating v2";

		P2Set p2Setv1 = lookup(v1, EpsilonFieldElem.getEpsilonFieldElem());
		P2Set p2Setv2 = lookup(v2, EpsilonFieldElem.getEpsilonFieldElem());
		assert (p2Setv1 != null) : "get a null p2 set!";
		assert (p2Setv2 != null) : "get a null p2 set!";

		// TODO need verifying this
		assert (v1.getType() instanceof jq_Array) : "lhs of AStore must be jq_Array!";
		IndexFieldElem index = IndexFieldElem.getIndexFieldElem();
		for (HeapObject hObj : p2Setv1.keySet()) {
			BoolExpr cst = p2Setv1.get(hObj);
			P2Set projP2Set = P2SetHelper.project(p2Setv2, cst);
			Pair<AbsMemLoc, FieldElem> pair = new Pair<AbsMemLoc, FieldElem>(
					hObj, index);

			Pair<Boolean, Boolean> res = weakUpdate(pair, projP2Set);
			ret.val0 = res.val0 | ret.val0;
			ret.val1 = res.val1 | ret.val1;
		}

		return ret;
	}

	// handleStoreStmt implements rule (3) in Figure 8 of the paper
	// v1.f = v2
	public Pair<Boolean, Boolean> handleStoreStmt(jq_Class clazz,
			jq_Method method, Register leftBase, VariableType leftBaseVType,
			jq_Type leftBaseType, jq_Field leftField, Register right,
			VariableType rightVType, jq_Type rightType) {

		Pair<Boolean, Boolean> ret = new Pair<Boolean, Boolean>(false, false);
		assert (rightVType == VariableType.PARAMEMTER)
				|| (rightVType == VariableType.LOCAL_VARIABLE) : "we are only considering local"
				+ " variables and parameters as rhs Base";
		assert (leftBaseVType == VariableType.PARAMEMTER)
				|| (leftBaseVType == VariableType.LOCAL_VARIABLE) : "we are only considering local"
				+ " variables and parameters as LHS";

		StackObject v1 = null, v2 = null;

		// generate the memory location for lhs
		if (leftBaseVType == VariableType.PARAMEMTER) {
			v1 = getParamElem(clazz, method, leftBase, leftBaseType);
		} else if (leftBaseVType == VariableType.LOCAL_VARIABLE) {
			v1 = getLocalVarElem(clazz, method, leftBase, leftBaseType);
		} else {
			assert false : "wired thing! For non-static store load stmt,"
					+ " LHS Base must be LocalElem or ParamElem!";
		}
		assert (v1 != null) : "v1 is null!";

		// generate the memory location for rhs's base
		if (rightVType == VariableType.PARAMEMTER) {
			v2 = getParamElem(clazz, method, right, rightType);
		} else if (rightVType == VariableType.LOCAL_VARIABLE) {
			v2 = getLocalVarElem(clazz, method, right, rightType);
		} else {
			assert false : "for non-static store stmt, rhs must be LocalElem or ParamElem!";
		}
		assert (v2 != null) : "v2 is null!";

		assert v1.knownArgDerived() : "we should set the arg-derived marker when creating v1";
		assert v2.knownArgDerived() : "we should set the arg-derived marker when creating v2";

		P2Set p2Setv1 = lookup(v1, EpsilonFieldElem.getEpsilonFieldElem());
		P2Set p2Setv2 = lookup(v2, EpsilonFieldElem.getEpsilonFieldElem());
		assert (p2Setv1 != null) : "get a null p2 set!";
		assert (p2Setv2 != null) : "get a null p2 set!";

		NormalFieldElem f = Env.getNormalFieldElem(leftField);
		for (HeapObject obj : p2Setv1.keySet()) {
			BoolExpr cst = p2Setv1.get(obj);
			P2Set projP2Set = P2SetHelper.project(p2Setv2, cst);
			Pair<AbsMemLoc, FieldElem> pair = new Pair<AbsMemLoc, FieldElem>(
					obj, f);

			Pair<Boolean, Boolean> res = weakUpdate(pair, projP2Set);
			ret.val0 = res.val0 | ret.val0;
			ret.val1 = res.val1 | ret.val1;
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
	public Pair<Boolean, Boolean> handleStaticStoreStmt(jq_Class clazz,
			jq_Method method, jq_Class leftBase, jq_Field leftField,
			Register right, VariableType rightVType, jq_Type rightType) {

		Pair<Boolean, Boolean> ret = new Pair<Boolean, Boolean>(false, false);
		assert (rightVType == VariableType.PARAMEMTER)
				|| (rightVType == VariableType.LOCAL_VARIABLE) : "we are only considering local"
				+ " variables and parameters as rhs in static store stmt";

		// generate the memory location for lhs's base
		StaticElem v1 = Env.getStaticElem(leftBase, leftField);
		memLocFactory.put(v1, v1);

		assert (v1 != null) : "v1 is null!";

		StackObject v2 = null;
		// generate the memory location for rhs
		if (rightVType == VariableType.PARAMEMTER) {
			v2 = getParamElem(clazz, method, right, rightType);
		} else if (rightVType == VariableType.LOCAL_VARIABLE) {
			v2 = getLocalVarElem(clazz, method, right, rightType);
		} else {
			assert false : "for static store stmt, rhs must be LocalElem or ParamElem!";
		}
		assert (v2 != null) : "v2 is null!";

		assert v1.knownArgDerived() : "we should set the arg-derived marker when creating v1";
		assert v2.knownArgDerived() : "we should set the arg-derived marker when creating v2";

		P2Set p2Setv2 = lookup(v2, EpsilonFieldElem.getEpsilonFieldElem());
		assert (p2Setv2 != null) : "get a null p2 set!";

		Pair<AbsMemLoc, FieldElem> pair = new Pair<AbsMemLoc, FieldElem>(v1,
				EpsilonFieldElem.getEpsilonFieldElem());

		Pair<Boolean, Boolean> res = weakUpdate(pair, p2Setv2);
		ret.val0 = res.val0;
		ret.val1 = res.val1;
		return ret;
	}

	// handleNewStmt implements rule (4) in Figure 8 of the paper
	// v = new T
	public Pair<Boolean, Boolean> handleNewStmt(jq_Class clazz,
			jq_Method method, Register left, VariableType leftVType,
			jq_Type leftType, jq_Type right, Quad allocSite, int line) {

		Pair<Boolean, Boolean> ret = new Pair<Boolean, Boolean>(false, false);
		assert (leftVType == VariableType.LOCAL_VARIABLE) : "LHS of a new stmt must be a local variable!";

		// generate the allocElem for rhs
		AllocElem allocT = getAllocElem(clazz, method, allocSite, right, line);
		assert (allocT != null) : "allocT is null!";

		LocalVarElem v = null;
		// generate the localVarElem for lhs
		if (leftVType == VariableType.LOCAL_VARIABLE) {
			v = getLocalVarElem(clazz, method, left, leftType);
		} else {
			assert false : "LHS of a new stmt must be a local variable!";
		}
		assert (v != null) : "v is null!";

		assert allocT.knownArgDerived() : "we should set the arg-derived marker when creating allocT";
		assert v.knownArgDerived() : "we should set the arg-derived marker when creating v";

		Pair<AbsMemLoc, FieldElem> pair = new Pair<AbsMemLoc, FieldElem>(v,
				EpsilonFieldElem.getEpsilonFieldElem());

		Pair<Boolean, Boolean> res = weakUpdate(pair, new P2Set(allocT,
				ConstraintManager.genTrue()));
		ret.val0 = res.val0;
		ret.val1 = res.val1;
		return ret;
	}

	// X x1 = new X[10] by just calling the handleMultiNewArrayStmt method with
	// dim = 1
	public Pair<Boolean, Boolean> handleNewArrayStmt(jq_Class clazz,
			jq_Method method, Register left, VariableType leftVType,
			jq_Type leftType, jq_Type right, Quad allocSite, int line) {
		return handleMultiNewArrayStmt(clazz, method, left, leftVType,
				leftType, right, 1, allocSite, line);
	}

	// handle multi-new stmt, e.g. X x1 = new X[1][2][3]
	// dim is the dimension of this array, dim >= 2
	public Pair<Boolean, Boolean> handleMultiNewArrayStmt(jq_Class clazz,
			jq_Method method, Register left, VariableType leftVType,
			jq_Type leftType, jq_Type right, int dim, Quad allocSite, int line) {

		Pair<Boolean, Boolean> ret = new Pair<Boolean, Boolean>(false, false);

		assert (leftVType == VariableType.LOCAL_VARIABLE) : "LHS of a new stmt must be a local variable!";

		LocalVarElem v = null;
		// generate the localVarElem for lhs
		if (leftVType == VariableType.LOCAL_VARIABLE) {
			v = getLocalVarElem(clazz, method, left, leftType);
		} else {
			assert false : "LHS of a new stmt must be a local variable!";
		}
		assert (v != null) : "v is null!";
		// generate the ArrayAllocElem for rhs
		AllocElem allocT = getAllocElem(clazz, method, allocSite, right, line);

		assert allocT.knownArgDerived() : "we should set the arg-derived marker when creating allocT";
		assert v.knownArgDerived() : "we should set the arg-derived marker when creating v";

		Pair<AbsMemLoc, FieldElem> pair = new Pair<AbsMemLoc, FieldElem>(v,
				EpsilonFieldElem.getEpsilonFieldElem());
		// update the LHS's P2Set weakly
		ret = weakUpdate(pair, new P2Set(allocT, ConstraintManager.genTrue()));

		int i = dim;
		jq_Type leftType1 = right;
		jq_Type rightType1 = null;
		while (i >= 2) {
			assert (leftType1 instanceof jq_Array) : "lhs of multi-new must be jq_Array!";
			rightType1 = ((jq_Array) leftType1).getElementType();
			AllocElem leftAllocT = getAllocElem(clazz, method, allocSite,
					leftType1, line);
			AllocElem rightAllocT = getAllocElem(clazz, method, allocSite,
					rightType1, line);
			Pair<Boolean, Boolean> res = handleArrayLoad(leftAllocT,
					IndexFieldElem.getIndexFieldElem(), rightAllocT);
			ret.val0 = res.val0 | ret.val0;
			ret.val1 = res.val1 | ret.val1;
			assert (leftType1 instanceof jq_Array) : "lhs of multi-new must be jq_Array!";
			leftType1 = ((jq_Array) leftType1).getElementType();
			i--;
		}

		return ret;
	}

	// return v;
	public Pair<Boolean, Boolean> handleRetStmt(jq_Class clazz,
			jq_Method method, Register retValue, VariableType retVType,
			jq_Type retType) {

		Pair<Boolean, Boolean> ret = new Pair<Boolean, Boolean>(false, false);
		// first try to find the corresponding local or parameter that has been
		// declared before returning
		StackObject v = null;
		if (retVType == VariableType.LOCAL_VARIABLE) {
			v = getLocalVarElem(clazz, method, retValue, retType);
		} else if (retVType == VariableType.PARAMEMTER) {
			v = getParamElem(clazz, method, retValue, retType);
		} else {
			assert false : "we are only considering return value to be local or parameter!";
		}

		// create return value element (only one return value for one method)
		RetElem retElem = getRetElem(clazz, method, method.getReturnType());
		// update the p2set of the return value
		Pair<AbsMemLoc, FieldElem> pair = new Pair<AbsMemLoc, FieldElem>(
				retElem, EpsilonFieldElem.getEpsilonFieldElem());
		P2Set p2Set = lookup(v, EpsilonFieldElem.getEpsilonFieldElem());
		assert (p2Set != null) : "get a null p2set!";

		Pair<Boolean, Boolean> res = weakUpdate(pair, p2Set);
		ret.val0 = res.val0;
		ret.val1 = res.val1;
		return ret;
	}

	public Pair<Boolean, Boolean> handleInvokeStmt(jq_Class clazz,
			jq_Method method, int line, AbstractHeap calleeHeap,
			MemLocInstnItem memLocInstn, BoolExpr typeCst) {
		Pair<Boolean, Boolean> ret = new Pair<Boolean, Boolean>(false, false);
		ProgramPoint point = Env.getProgramPoint(clazz, method, line);
		// this is used for recursive call
		boolean isRecursive = false;
		if (this.equals(calleeHeap)) {
			isRecursive = true;
		}
		int iteration = 0;
		// this is the real updating
		if (!isRecursive) {
			while (true) {
				iteration++;
				if (G.dbgAntlr) {
					StringUtil
							.reportInfo("[dbgAntlr] "
									+ "****************************************************");
					StringUtil.reportInfo("[dbgAntlr] " + "[" + iteration
							+ "]-th iteration");
				}
				boolean go = false;
				Iterator<Map.Entry<Pair<AbsMemLoc, FieldElem>, P2Set>> it = calleeHeap.locToP2Set
						.entrySet().iterator();
				while (it.hasNext()) {
					Map.Entry<Pair<AbsMemLoc, FieldElem>, P2Set> entry = it
							.next();
					Pair<AbsMemLoc, FieldElem> key = entry.getKey();
					P2Set tgts = entry.getValue();
					AbsMemLoc src = key.val0;
					FieldElem f = key.val1;
					Iterator<Map.Entry<HeapObject, BoolExpr>> it1 = tgts
							.iterator();
					while (it1.hasNext()) {
						Map.Entry<HeapObject, BoolExpr> entry1 = it1.next();
						HeapObject tgt = entry1.getKey();

						Pair<Boolean, Boolean> res = instnEdge(src, tgt, f,
								memLocInstn, calleeHeap, point, typeCst);
						addDepEdges(memLocInstn, src, tgt, f);
						ret.val0 = res.val0 | ret.val0;
						ret.val1 = res.val1 | ret.val1;
						// changing the heap means we need go (conservative)
						go = res.val0 | go;
					}
				}

				if (G.dbgAntlr) {
					StringUtil.reportInfo("[dbgAntlr] " + "[Iteration Result] "
							+ "[" + iteration + "]-th iteration: " + "[" + go
							+ "]");
					StringUtil
							.reportInfo("[dbgAntlr] "
									+ "****************************************************");
				}

				if (!go) {
					break;
				}
				if (!SummariesEnv.v().useFixPoint) {
					break;
				}
			}
		} else {
			while (true) {
				iteration++;
				if (G.dbgAntlr) {
					StringUtil
							.reportInfo("[dbgAntlr] "
									+ "****************************************************");
					StringUtil.reportInfo("[dbgAntlr] " + "[" + iteration
							+ "]-th iteration");
				}
				boolean go = false;
				// this is used for updating for recursive calls
				Map<Pair<AbsMemLoc, FieldElem>, Set<Pair<AbsMemLoc, P2Set>>> result = new HashMap<Pair<AbsMemLoc, FieldElem>, Set<Pair<AbsMemLoc, P2Set>>>();
				Iterator<Map.Entry<Pair<AbsMemLoc, FieldElem>, P2Set>> it = calleeHeap.locToP2Set
						.entrySet().iterator();
				while (it.hasNext()) {
					Map.Entry<Pair<AbsMemLoc, FieldElem>, P2Set> entry = it
							.next();
					Pair<AbsMemLoc, FieldElem> key = entry.getKey();
					P2Set tgts = entry.getValue();
					AbsMemLoc src = key.val0;
					FieldElem f = key.val1;

					Set<Pair<AbsMemLoc, P2Set>> toAdd = new HashSet<Pair<AbsMemLoc, P2Set>>();
					Iterator<Map.Entry<HeapObject, BoolExpr>> it1 = tgts
							.iterator();
					while (it1.hasNext()) {
						Map.Entry<HeapObject, BoolExpr> entry1 = it1.next();
						HeapObject tgt = entry1.getKey();
						instnEdge4RecurCall(src, tgt, f, memLocInstn,
								calleeHeap, point, typeCst, toAdd);
						addDepEdges(memLocInstn, src, tgt, f);
						result.put(key, toAdd);
					}
				}

				// weak update outside to avoid ConcurrentModification
				Iterator<Map.Entry<Pair<AbsMemLoc, FieldElem>, Set<Pair<AbsMemLoc, P2Set>>>> it2 = result
						.entrySet().iterator();
				while (it2.hasNext()) {
					Map.Entry<Pair<AbsMemLoc, FieldElem>, Set<Pair<AbsMemLoc, P2Set>>> entry = it2
							.next();
					Pair<AbsMemLoc, FieldElem> key = entry.getKey();
					FieldElem f = key.val1;
					Set<Pair<AbsMemLoc, P2Set>> toAdd = entry.getValue();
					Iterator<Pair<AbsMemLoc, P2Set>> it3 = toAdd.iterator();
					while (it3.hasNext()) {
						Pair<AbsMemLoc, P2Set> pair = it3.next();
						AbsMemLoc newSrc = pair.val0;
						P2Set newP2Set = pair.val1;
						Pair<AbsMemLoc, FieldElem> pair1 = new Pair<AbsMemLoc, FieldElem>(
								newSrc, f);
						Pair<Boolean, Boolean> res = weakUpdate(pair1, newP2Set);
						ret.val0 = res.val0 | ret.val0;
						ret.val1 = res.val1 | ret.val1;
						// changing the heap means we should go (conservative)
						go = res.val0 | go;
					}
				}

				if (G.dbgAntlr) {
					StringUtil.reportInfo("[dbgAntlr] " + "[Iteration Result] "
							+ "[" + iteration + "]-th iteration: " + "[" + go
							+ "]");
					StringUtil
							.reportInfo("[dbgAntlr] "
									+ "****************************************************");
				}

				if (!go) {
					break;
				}
				if (!SummariesEnv.v().useFixPoint) {
					break;
				}
			}
		}

		if (ret.val1) {
			if (SummariesEnv.v().jump) {
				// this is for conclusion
				if (summary == null) {
					return ret;
				}
				// we need to reanalyze this method (conservative)
				for (Summary s : summary.jumpEffectSet) {
					s.jumpInstnSet.remove(summary);
				}
			}
		}

		return ret;
	}

	public static int count = 0;

	private Pair<Boolean, Boolean> instnEdge(AbsMemLoc src, HeapObject dst,
			FieldElem field, MemLocInstnItem memLocInstn,
			AbstractHeap calleeHeap, ProgramPoint point, BoolExpr typeCst) {

		if (G.dbgAntlr && G.dbgInvoke) {
			StringUtil.reportInfo("[dbgAntlr] "
					+ "[instantiating callee edge]: " + "method Id: "
					+ G.IdMapping.get(calleeHeap.summary));
			StringUtil.reportInfo("[dbgAntlr] "
					+ "-------------------------------------------");
			StringUtil.reportInfo("[dbgAntlr] " + "SRC: " + src);
			StringUtil.reportInfo("[dbgAntlr] " + "FIELD: " + field);
			StringUtil.reportInfo("[dbgAntlr] " + "DST: " + dst);
			StringUtil.reportInfo("[dbgAntlr] "
					+ "-------------------------------------------");
		}

		Pair<Boolean, Boolean> ret = new Pair<Boolean, Boolean>(false, false);

		assert (src != null && dst != null && field != null) : "nulls!";
		assert (calleeHeap.contains(src)) : "callee's heap should contain the source of the edge!";
		assert (calleeHeap.lookup(src, field).contains(dst)) : ""
				+ "the p2 set should contain the destination of the edge!";

		if (SummariesEnv.v().localType == SummariesEnv.PropType.NOLOCAL) {
			if (src instanceof LocalVarElem) {
				return ret;
			} else if (src instanceof AllocElem) {
				if (!calleeHeap.toProp(src)) {
					return ret;
				}
			}
		} else if (SummariesEnv.v().localType == SummariesEnv.PropType.ALL) {

		} else if (SummariesEnv.v().localType == SummariesEnv.PropType.NOTHING) {
			if (src.isNotArgDerived()) {
				return ret;
			}
		} else if (SummariesEnv.v().localType == SummariesEnv.PropType.NOALLOC) {
			if (src instanceof AllocElem) {
				return ret;
			}
		} else {
			if (src instanceof LocalVarElem || src instanceof AllocElem) {
				if (!calleeHeap.toProp(src)) {
					return ret;
				}
			}
		}

		BoolExpr calleeCst = calleeHeap.lookup(src, field).get(dst);
		assert (calleeCst != null) : "constraint is null!";

		// more smart skip for instantiating edges
		if (SummariesEnv.v().moreSmartSkip) {
			if (calleeHeap.summary.containsInstnedEdge(memLocInstn, src, dst,
					field)) {
				return ret;
			}
		}

		// instantiate the calleeCst
		BoolExpr instnCst = instnCst(calleeCst, this, point, memLocInstn);

		MemLocInstnSet instnSrc = memLocInstn.instnMemLoc(src, this, point);
		MemLocInstnSet instnDst = memLocInstn.instnMemLoc(dst, this, point);

		assert (instnDst != null) : "instantiation of dst cannot be null!";
		if (instnSrc == null) {
			assert (src instanceof RetElem) : "only return value in the callee"
					+ " is allowed not having an instantiated location in the callee";
		}

		if (instnSrc == null) {
			return ret;
		}

		if (G.dbgAntlr && G.dbgInvoke) {
			StringUtil.reportInfo("[dbgAntlr] "
					+ "-------------------------------------------");
			StringUtil.reportInfo("[dbgAntlr] "
					+ "[the callee edge is instantiated into]: "
					+ instnSrc.size() + "src locations " + "and "
					+ instnDst.size() + " dst locations");
			StringUtil.reportInfo("[dbgAntlr] "
					+ "-------------------------------------------");
		}

		for (AbsMemLoc newSrc : instnSrc.keySet()) {
			// only for bad methods
			if (SummariesEnv.v().badMethodSkip) {
				P2Set p2set = lookup(newSrc, field);
				if (p2set.containsAll(instnDst.keySet())) {
					continue;
				}
			}

			for (AbsMemLoc newDst : instnDst.keySet()) {
				assert (newDst instanceof HeapObject) : ""
						+ "dst should be instantiated as a heap object!";
				HeapObject newDst1 = (HeapObject) newDst;

				if (SummariesEnv.v().badMethodSkip) {
					P2Set p2set = lookup(newSrc, field);
					if (p2set.contains(newDst1)) {
						continue;
					}
				}

				if (G.dbgAntlr && G.dbgInstn) {
					StringUtil.reportInfo("[dbgAntlr] "
							+ "[instantiated caller edge]: ");
					StringUtil.reportInfo("[dbgAntlr] "
							+ "-------------------------------------------");
					StringUtil.reportInfo("[dbgAntlr] " + "SRC: " + newSrc);
					StringUtil.reportInfo("[dbgAntlr] " + "FIELD: " + field);
					StringUtil.reportInfo("[dbgAntlr] " + "DST: " + newDst1);
					StringUtil.reportInfo("[dbgAntlr] "
							+ "-------------------------------------------");
				}
				assert (newDst1 != null) : "null!";

				BoolExpr cst1 = instnSrc.get(newSrc);
				BoolExpr cst2 = instnDst.get(newDst);
				BoolExpr cst = ConstraintManager.intersect(
						ConstraintManager.intersect(cst1, cst2),
						ConstraintManager.intersect(instnCst, typeCst));

				assert (cst != null) : "null cst!";
				assert (cst1 != null && cst2 != null && cst != null) : "get null constraints!";
				Pair<AbsMemLoc, FieldElem> pair = new Pair<AbsMemLoc, FieldElem>(
						newSrc, field);

				if (G.dbgFilter) {
					System.out
							.println("dbgFilter: " + "updating pair: " + pair);
					System.out.println("dbgFilter: " + "updating p2set: "
							+ "[dst]: " + newDst1);
				}

				Pair<Boolean, Boolean> res = weakUpdate(pair, new P2Set(
						newDst1, cst));
				ret.val0 = res.val0 | ret.val0;
				ret.val1 = res.val1 | ret.val1;

			}
		}

		if (G.dbgAntlr) {
			StringUtil.reportInfo("[dbgAntlr] " + "[Instn Result] "
					+ "change heap: " + "[" + ret.val0 + "] "
					+ "change summary: " + "[" + ret.val1 + "]");
		}

		return ret;
	}

	private Pair<Boolean, Boolean> instnEdge4RecurCall(AbsMemLoc src,
			HeapObject dst, FieldElem field, MemLocInstnItem memLocInstn,
			AbstractHeap calleeHeap, ProgramPoint point, BoolExpr typeCst,
			Set<Pair<AbsMemLoc, P2Set>> toAdd) {

		Pair<Boolean, Boolean> ret = new Pair<Boolean, Boolean>(false, false);

		if (G.dbgAntlr && G.dbgInvoke) {
			StringUtil.reportInfo("[dbgAntlr] "
					+ "[instantiating (recursive) callee edge]: "
					+ "method Id: " + G.IdMapping.get(calleeHeap.summary));
			StringUtil.reportInfo("[dbgAntlr] "
					+ "-------------------------------------------");
			StringUtil.reportInfo("[dbgAntlr] " + "SRC: " + src);
			StringUtil.reportInfo("[dbgAntlr] " + "FIELD: " + field);
			StringUtil.reportInfo("[dbgAntlr] " + "DST: " + dst);
			StringUtil.reportInfo("[dbgAntlr] "
					+ "-------------------------------------------");
		}

		// more smart skip for instantiating edges
		if (SummariesEnv.v().moreSmartSkip) {
			if (calleeHeap.summary.containsInstnedEdge(memLocInstn, src, dst,
					field)) {
				return ret;
			}
		}

		assert (src != null && dst != null && field != null) : "nulls!";
		assert (calleeHeap.contains(src)) : "callee's heap should contain the source of the edge!";
		assert (calleeHeap.lookup(src, field).contains(dst)) : ""
				+ "the p2 set should contain the destination of the edge!";

		if (SummariesEnv.v().localType == SummariesEnv.PropType.NOLOCAL) {
			if (src instanceof LocalVarElem) {
				return ret;
			} else if (src instanceof AllocElem) {
				if (!calleeHeap.toProp(src)) {
					return ret;
				}
			}
		} else if (SummariesEnv.v().localType == SummariesEnv.PropType.ALL) {

		} else if (SummariesEnv.v().localType == SummariesEnv.PropType.NOTHING) {
			if (src.isNotArgDerived()) {
				return ret;
			}
		} else {
			if (src instanceof LocalVarElem || src instanceof AllocElem) {
				if (!calleeHeap.toProp(src)) {
					return ret;
				}
			}
		}

		BoolExpr calleeCst = calleeHeap.lookup(src, field).get(dst);
		assert (calleeCst != null) : "constraint is null!";

		// instantiate the calleeCst
		BoolExpr instnCst = instnCst(calleeCst, this, point, memLocInstn);

		MemLocInstnSet instnSrc = memLocInstn.instnMemLoc(src, this, point);
		MemLocInstnSet instnDst = memLocInstn.instnMemLoc(dst, this, point);

		assert (instnDst != null) : "instantiation of dst cannot be null!";
		if (instnSrc == null) {
			assert (src instanceof RetElem) : "only return value in the callee"
					+ " is allowed not having an instantiated location in the callee";
		}

		if (instnSrc == null) {
			return ret;
		}

		if (G.dbgAntlr && G.dbgInvoke) {
			StringUtil.reportInfo("[dbgAntlr] "
					+ "-------------------------------------------");
			StringUtil.reportInfo("[dbgAntlr] "
					+ "[the callee edge is instantiated into]: "
					+ instnSrc.size() + "src locations " + "and "
					+ instnDst.size() + " dst locations");
			StringUtil.reportInfo("[dbgAntlr] "
					+ "-------------------------------------------");
		}

		for (AbsMemLoc newSrc : instnSrc.keySet()) {
			for (AbsMemLoc newDst : instnDst.keySet()) {

				assert (newDst instanceof HeapObject) : ""
						+ "dst should be instantiated as a heap object!";
				HeapObject newDst1 = (HeapObject) newDst;

				assert (newDst1 != null) : "null!";

				if (G.dbgAntlr && G.dbgInstn) {
					StringUtil.reportInfo("[dbgAntlr] "
							+ "[instantiated (recurisve) caller edge]: ");
					StringUtil.reportInfo("[dbgAntlr] "
							+ "-------------------------------------------");
					StringUtil.reportInfo("[dbgAntlr] " + "SRC: " + newSrc);
					StringUtil.reportInfo("[dbgAntlr] " + "FIELD: " + field);
					StringUtil.reportInfo("[dbgAntlr] " + "DST: " + newDst1);
					StringUtil.reportInfo("[dbgAntlr] "
							+ "-------------------------------------------");
				}

				BoolExpr cst1 = instnSrc.get(newSrc);
				BoolExpr cst2 = instnDst.get(newDst);

				BoolExpr cst = ConstraintManager.intersect(
						ConstraintManager.intersect(cst1, cst2),
						ConstraintManager.intersect(instnCst, typeCst));
				assert (cst != null) : "null cst1";
				assert (cst1 != null && cst2 != null && cst != null) : "get null constraints!";
				toAdd.add(new Pair<AbsMemLoc, P2Set>(newSrc, new P2Set(newDst1,
						cst)));
			}
		}

		if (G.dbgAntlr) {
			StringUtil.reportInfo("[dbgAntlr] " + "[Instn Result] "
					+ "change heap: " + "[" + ret.val0 + "] "
					+ "change summary: " + "[" + ret.val1 + "]");
		}

		return ret;
	}

	/* Constraint instantiation. */
	private BoolExpr instnCst(BoolExpr cst, AbstractHeap callerHeap,
			ProgramPoint point, MemLocInstnItem memLocInstn) {
		long startInstCst = System.nanoTime();
		if (SummariesEnv.v().disableCst() || summary.isInBadScc())
			return ConstraintManager.genTrue();
		assert cst != null : "Invalid Constrait before instantiation.";
		// return directly.
		if (ConstraintManager.isScala(cst))
			return cst;

		BoolExpr instC = ConstraintManager.instnConstaint(cst, callerHeap,
				point, memLocInstn);

		assert instC != null : "Invalid instantiated Constrait.";

		long endInstCst = System.nanoTime();
		G.instCstTime += (endInstCst - startInstCst);
		if (instC.toString().length() > 2500) {
			int length = instC.toString().length();
			StringUtil
					.reportInfo("We are in trouble..." + length + ":" + instC);
			StringUtil.reportSec("Inst Cst time: ", startInstCst, endInstCst);
		}
		return instC;
	}

	// get the LocalVarElem given the declaring class, declaring method, and the
	// corresponding register in the IR
	public LocalVarElem getLocalVarElem(jq_Class clazz, jq_Method method,
			Register variable, jq_Type type) {
		if (!type.isPrepared() && !(type instanceof jq_ReturnAddressType)) {
			type.prepare();
		}
		// create a wrapper
		LocalVarElem ret = new LocalVarElem(clazz, method, variable, type);

		// try to look up this wrapper in the memory location factory
		if (memLocFactory.containsKey(ret)) {
			return (LocalVarElem) memLocFactory.get(ret);
		}
		// not found in the factory
		// every time generating a memory location, do this marking
		ArgDerivedHelper.markArgDerived(ret);
		memLocFactory.put(ret, ret);
		// initialize the P2Set of this local
		Pair<AbsMemLoc, FieldElem> pair = new Pair<AbsMemLoc, FieldElem>(ret,
				EpsilonFieldElem.getEpsilonFieldElem());
		ret.addField(EpsilonFieldElem.getEpsilonFieldElem());
		heap.add(ret);
		P2Set p2set = locToP2Set.get(pair);
		if (p2set == null) {
			locToP2Set.put(pair, new P2Set());
		}
		// weakUpdate(pair, new P2Set());

		return ret;
	}

	public LocalVarElem getLocalVarElem4Query(jq_Class clazz, jq_Method method,
			Register variable) {
		// create a wrapper
		LocalVarElem ret = new LocalVarElem(clazz, method, variable);
		// try to look up this wrapper in the memory location factory
		if (memLocFactory.containsKey(ret)) {
			return (LocalVarElem) memLocFactory.get(ret);
		}
		ret = null;
		return ret;
	}

	// get the ParamElem given the declaring class, declaring method and the
	// corresponding register in the IR
	public ParamElem getParamElem(jq_Class clazz, jq_Method method,
			Register parameter, jq_Type type) {
		if (!type.isPrepared() && !(type instanceof jq_ReturnAddressType)) {
			type.prepare();
		}
		// create a wrapper
		ParamElem ret = new ParamElem(clazz, method, parameter, type);

		// try to look up this wrapper in the memory location factory
		if (memLocFactory.containsKey(ret)) {
			return (ParamElem) memLocFactory.get(ret);
		}
		// not found in the factory
		// every time generating a memory location, do this marking
		ArgDerivedHelper.markArgDerived(ret);
		memLocFactory.put(ret, ret);
		// initialize the P2Set of this local
		Pair<AbsMemLoc, FieldElem> pair = new Pair<AbsMemLoc, FieldElem>(ret,
				EpsilonFieldElem.getEpsilonFieldElem());
		ret.addField(EpsilonFieldElem.getEpsilonFieldElem());
		heap.add(ret);
		P2Set p2set = locToP2Set.get(pair);
		if (p2set == null) {
			locToP2Set.put(pair, new P2Set());
		}
		// weakUpdate(pair, new P2Set());
		return ret;
	}

	// only one RetElem for one specific method
	public RetElem getRetElem(jq_Class clazz, jq_Method method, jq_Type retType) {
		if (!retType.isPrepared() && !(retType instanceof jq_ReturnAddressType)) {
			retType.prepare();
		}
		// create a wrapper
		RetElem ret = new RetElem(clazz, method, retType);

		// try to look up
		if (memLocFactory.containsKey(ret)) {
			return (RetElem) memLocFactory.get(ret);
		}
		// not found in the factory
		// every time generating a memory location, do this marking
		ArgDerivedHelper.markArgDerived(ret);
		memLocFactory.put(ret, ret);
		// initialize the P2Set of this local
		Pair<AbsMemLoc, FieldElem> pair = new Pair<AbsMemLoc, FieldElem>(ret,
				EpsilonFieldElem.getEpsilonFieldElem());
		ret.addField(EpsilonFieldElem.getEpsilonFieldElem());
		heap.add(ret);
		P2Set p2set = locToP2Set.get(pair);
		if (p2set == null) {
			locToP2Set.put(pair, new P2Set());
		}
		// weakUpdate(pair, new P2Set());

		return ret;
	}

	// get the LocalAccessPath whose base is ParamElem
	private LocalAccessPath getLocalAccessPath(ParamElem base, FieldElem field) {
		LocalAccessPath ret = new LocalAccessPath(base, field,
				Env.countAccessPath++);
		if (memLocFactory.containsKey(ret)) {
			return (LocalAccessPath) memLocFactory.get(ret);
		}
		ArgDerivedHelper.markArgDerived(ret);
		memLocFactory.put(ret, ret);
		return ret;
	}

	// get the AccessPath whose base is LocalAccessPath
	private LocalAccessPath getLocalAccessPath(LocalAccessPath base,
			FieldElem field) {
		LocalAccessPath ret = new LocalAccessPath(base, field,
				Env.countAccessPath++);
		if (memLocFactory.containsKey(ret)) {
			return (LocalAccessPath) memLocFactory.get(ret);
		}
		ArgDerivedHelper.markArgDerived(ret);
		memLocFactory.put(ret, ret);
		return ret;
	}

	// get the LocalAccessPath in the factory by an LocalAccessPath with the
	// same content (we want to use exactly the same instance)
	private LocalAccessPath getLocalAccessPath(LocalAccessPath other) {
		if (memLocFactory.containsKey(other)) {
			return (LocalAccessPath) memLocFactory.get(other);
		}
		// assert false : "I think other should have been created!";
		ArgDerivedHelper.markArgDerived(other);
		memLocFactory.put(other, other);
		return other;
	}

	// get the AllocElem given the declaring class, declaring method and the
	// corresponding type and the line number
	public AllocElem getAllocElem(jq_Class clazz, jq_Method method,
			Quad allocSite, jq_Type type, int line) {
		if (!type.isPrepared())
			type.prepare();
		// create a wrapper
		Context context = new Context(Env.getProgramPoint(clazz, method, line));
		// create an AllocElem wrapper
		AllocElem ret = new AllocElem(new Alloc(type, allocSite), context, type);
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

	// get the AllocElem in the factory by an AllocElem with the same
	// content (we want to use exactly the same instance)
	public AllocElem getAllocElem(AllocElem other) {
		if (memLocFactory.containsKey(other)) {
			return (AllocElem) memLocFactory.get(other);
		}

		AllocElem ret = new AllocElem(other.alloc, other.context.clone(),
				other.type);
		ArgDerivedHelper.markArgDerived(ret);
		memLocFactory.put(ret, ret);

		return ret;
	}

	// append the program point to a given AllocElem and generate a new
	// AllocElem
	public AllocElem getAllocElem(AllocElem other, ProgramPoint point) {
		AllocElem ret = other.clone();

		ret.appendContextFront(point);

		if (memLocFactory.containsKey(ret)) {
			return (AllocElem) memLocFactory.get(ret);
		}
		ArgDerivedHelper.markArgDerived(ret);
		memLocFactory.put(ret, ret);

		return ret;
	}

	// get the default target of memory location loc and the field
	// we can ONLY call this method when ensuring loc is arg-derived
	protected AccessPath getDefaultTarget(AbsMemLoc loc, FieldElem field) {
		assert (loc instanceof AccessPath) || (loc instanceof StaticElem)
				|| (loc instanceof ParamElem) : ""
				+ "we can only get default targets for arg-derived elements";
		assert loc.knownArgDerived() : "we must first set the argument derived marker "
				+ "before using the mem loc!";
		assert loc.isArgDerived() : "you can ONLY get the default target for an arg derived mem loc!";

		AccessPath ret = null;

		if (SummariesEnv.v().level == SummariesEnv.FieldSmashLevel.REG) {
			if (SummariesEnv.v().markSmashedFields) {
				if (loc.isArgDerived()) {
					if (field instanceof IndexFieldElem) {
						// for index field we do this smashing for smashLength
						if (loc.countFieldSelector(field) >= SummariesEnv.v().smashLength) {
							assert (loc instanceof AccessPath) : "only AccessPath has field selectors!";
							// only AccessPath has field selectors
							AccessPath path = ((AccessPath) loc)
									.getPrefix(field);
							Set<FieldElem> smashedFields = ((AccessPath) loc)
									.getPreSmashedFields(field);

							if (path instanceof LocalAccessPath) {
								ret = getLocalAccessPath((LocalAccessPath) path);
							} else if (path instanceof StaticAccessPath) {
								ret = Env
										.getStaticAccessPath((StaticAccessPath) path);
							} else {
								assert false : "only access path is allowed!";
							}
							// TODO changing the location potentially dangerous!
							ret.addSmashedFields(smashedFields);
						} else {
							if (loc instanceof StaticElem) {
								ret = Env.getStaticAccessPath((StaticElem) loc,
										field);
							} else if (loc instanceof ParamElem) {
								ret = getLocalAccessPath((ParamElem) loc, field);
							} else if (loc instanceof AccessPath) {
								if (loc instanceof StaticAccessPath) {
									ret = Env.getStaticAccessPath(
											(StaticAccessPath) loc, field);
								} else if (loc instanceof LocalAccessPath) {
									ret = getLocalAccessPath(
											(LocalAccessPath) loc, field);
								} else {
									assert false : "only two kinds of access path!";
								}
							} else {
								assert false : "only three kinds of things can have default targets!";
							}
						}
					} else if (loc.hasFieldSelector(field)) {
						assert (loc instanceof AccessPath) : "only AccessPath has field selectors!";
						// only AccessPath has field selectors
						AccessPath path = ((AccessPath) loc).getPrefix(field);
						Set<FieldElem> smashedFields = ((AccessPath) loc)
								.getPreSmashedFields(field);

						if (path instanceof LocalAccessPath) {
							ret = getLocalAccessPath((LocalAccessPath) path);
						} else if (path instanceof StaticAccessPath) {
							ret = Env
									.getStaticAccessPath((StaticAccessPath) path);
						} else {
							assert false : "only access path is allowed!";
						}
						// TODO changing the location potentially dangerous!
						ret.addSmashedFields(smashedFields);
					} else {
						if (loc instanceof StaticElem) {
							ret = Env.getStaticAccessPath((StaticElem) loc,
									field);
						} else if (loc instanceof ParamElem) {
							ret = getLocalAccessPath((ParamElem) loc, field);
						} else if (loc instanceof AccessPath) {
							// we should call the method without parameters
							// because loc is not a smashed access path!
							Set<FieldElem> smashedFields = ((AccessPath) loc)
									.getSmashedFields();
							if (loc instanceof StaticAccessPath) {
								ret = Env.getStaticAccessPath(
										(StaticAccessPath) loc, field);
								// TODO changing the location
								ret.addSmashedFields(smashedFields);
							} else if (loc instanceof LocalAccessPath) {
								ret = getLocalAccessPath((LocalAccessPath) loc,
										field);
								// TODO changing the location
								ret.addSmashedFields(smashedFields);
							} else {
								assert false : "only two kinds of access path!";
							}
						} else {
							assert false : "only three kinds of things can have default targets!";
						}
					}
				} else {
					assert false : "you can NOT get the default target for a non-arg derived mem loc!";
				}
			} else {
				if (loc.isArgDerived()) {
					if (loc.hasFieldSelector(field)) {
						assert (loc instanceof AccessPath) : "only AccessPath has field selectors!";
						// only AccessPath has field selectors
						AccessPath path = ((AccessPath) loc).getPrefix(field);
						if (path instanceof LocalAccessPath) {
							ret = getLocalAccessPath((LocalAccessPath) path);
						} else if (path instanceof StaticAccessPath) {
							ret = Env
									.getStaticAccessPath((StaticAccessPath) path);
						} else {
							assert false : "only access path is allowed!";
						}
					} else {
						if (loc instanceof StaticElem) {
							ret = Env.getStaticAccessPath((StaticElem) loc,
									field);
						} else if (loc instanceof ParamElem) {
							ret = getLocalAccessPath((ParamElem) loc, field);
						} else if (loc instanceof AccessPath) {
							if (loc instanceof StaticAccessPath) {
								ret = Env.getStaticAccessPath(
										(StaticAccessPath) loc, field);
							} else if (loc instanceof LocalAccessPath) {
								ret = getLocalAccessPath((LocalAccessPath) loc,
										field);
							} else {
								assert false : "only two kinds of access path!";
							}
						} else {
							assert false : "only three kinds of things can have default targets!";
						}
					}
				} else {
					assert false : "you can NOT get the default target for a non-arg derived mem loc!";
				}
			}
		} else if (SummariesEnv.v().level == SummariesEnv.FieldSmashLevel.CTRLLENGTH) {
			if (loc.isArgDerived()) {
				if (loc.length >= SummariesEnv.v().ctrlLength) {
					assert (loc instanceof AccessPath) : "only AccessPath has field selectors!";
					if (loc instanceof LocalAccessPath) {
						ret = getLocalAccessPath((LocalAccessPath) loc);
					} else if (loc instanceof StaticAccessPath) {
						ret = Env.getStaticAccessPath((StaticAccessPath) loc);
					} else {
						assert false : "only two kinds of access paths!";
					}
					ret.addSmashedField(field);
					ret.addEndingField(field);
				} else {
					if (loc instanceof StaticElem) {
						ret = Env.getStaticAccessPath((StaticElem) loc, field);
					} else if (loc instanceof ParamElem) {
						ret = getLocalAccessPath((ParamElem) loc, field);
					} else if (loc instanceof AccessPath) {
						if (loc instanceof StaticAccessPath) {
							ret = Env.getStaticAccessPath(
									(StaticAccessPath) loc, field);

						} else if (loc instanceof LocalAccessPath) {
							ret = getLocalAccessPath((LocalAccessPath) loc,
									field);
						} else {
							assert false : "only two kinds of access path!";
						}
					} else {
						assert false : "only three kinds of things can have default targets!";
					}
				}
			} else {
				assert false : "you can NOT get the default target for a non-arg derived mem loc!";
			}
		} else if (SummariesEnv.v().level == SummariesEnv.FieldSmashLevel.TYPESMASH) {
			if (loc.isArgDerived()) {
				jq_Type type = null;
				if (field instanceof NormalFieldElem) {
					type = ((NormalFieldElem) field).getField().getType();
				} else if (field instanceof EpsilonFieldElem) {
					type = loc.getType();
				} else if (field instanceof IndexFieldElem) {
					type = (loc.getType() instanceof jq_Array) ? ((jq_Array) loc
							.getType()).getElementType() : Program.g()
							.getClass("java.lang.Object");
				}
				assert (type != null);

				if (field instanceof IndexFieldElem) {
					// for index field we do this smashing for smashLength
					if (loc.countFieldSelector(field) >= SummariesEnv.v().smashLength
							&& loc.hasFieldType(type)) {
						// assert false;
						assert (loc instanceof AccessPath) : "only AccessPath has field selectors!";
						// only AccessPath has field selectors
						AccessPath path = ((AccessPath) loc)
								.getTypePrefix(type);
						Set<FieldElem> smashedFields = ((AccessPath) loc)
								.getPreSmashedFieldsForType(type);
						if (path instanceof LocalAccessPath) {
							ret = getLocalAccessPath((LocalAccessPath) path);
						} else if (path instanceof StaticAccessPath) {
							ret = Env
									.getStaticAccessPath((StaticAccessPath) path);
						} else {
							assert false : "only access path is allowed!";
						}
						// TODO changing the location potentially dangerous!
						ret.addSmashedFields(smashedFields);
						ret.addSmashedField(field);
						ret.addEndingField(field);
					} else {
						if (loc instanceof StaticElem) {
							ret = Env.getStaticAccessPath((StaticElem) loc,
									field);
						} else if (loc instanceof ParamElem) {
							ret = getLocalAccessPath((ParamElem) loc, field);
						} else if (loc instanceof AccessPath) {
							if (loc instanceof StaticAccessPath) {
								ret = Env.getStaticAccessPath(
										(StaticAccessPath) loc, field);
							} else if (loc instanceof LocalAccessPath) {
								ret = getLocalAccessPath((LocalAccessPath) loc,
										field);
							} else {
								assert false : "only two kinds of access path!";
							}
						} else {
							assert false : "only three kinds of things can have default targets!";
						}
					}
				} else if (loc.hasFieldType(type)) {
					assert (loc instanceof AccessPath) : "only AccessPath has field selectors!";
					// only AccessPath has field selectors
					AccessPath path = ((AccessPath) loc).getTypePrefix(type);
					Set<FieldElem> smashedFields = ((AccessPath) loc)
							.getPreSmashedFieldsForType(type);
					if (path instanceof LocalAccessPath) {
						ret = getLocalAccessPath((LocalAccessPath) path);
					} else if (path instanceof StaticAccessPath) {
						ret = Env.getStaticAccessPath((StaticAccessPath) path);
					} else {
						assert false : "only access path is allowed!";
					}
					// TODO changing the location potentially dangerous!
					ret.addSmashedFields(smashedFields);
					ret.addSmashedField(field);
					ret.addEndingField(field);
				} else {
					if (loc instanceof StaticElem) {
						ret = Env.getStaticAccessPath((StaticElem) loc, field);
					} else if (loc instanceof ParamElem) {
						ret = getLocalAccessPath((ParamElem) loc, field);
					} else if (loc instanceof AccessPath) {
						// we should call the method without parameters
						// because loc is not a smashed access path!
						Set<FieldElem> smashedFields = ((AccessPath) loc)
								.getSmashedFields();
						if (loc instanceof StaticAccessPath) {
							ret = Env.getStaticAccessPath(
									(StaticAccessPath) loc, field);
							// TODO changing the location
							ret.addSmashedFields(smashedFields);
						} else if (loc instanceof LocalAccessPath) {
							ret = getLocalAccessPath((LocalAccessPath) loc,
									field);
							// TODO changing the location
							ret.addSmashedFields(smashedFields);
						} else {
							assert false : "only two kinds of access path!";
						}
					} else {
						assert false : "only three kinds of things can have default targets!";
					}
				}
			} else {
				assert false : "you can NOT get the default target for a non-arg derived mem loc!";
			}
		} else if (SummariesEnv.v().level == SummariesEnv.FieldSmashLevel.TYPECOMPSMASH) {
			if (loc.isArgDerived()) {
				jq_Type type = null;
				if (field instanceof NormalFieldElem) {
					type = ((NormalFieldElem) field).getField().getType();
				} else if (field instanceof EpsilonFieldElem) {
					type = loc.getType();
				} else if (field instanceof IndexFieldElem) {
					type = (loc.getType() instanceof jq_Array) ? ((jq_Array) loc
							.getType()).getElementType() : Program.g()
							.getClass("java.lang.Object");
				}
				assert (type != null);

				if (field instanceof IndexFieldElem) {
					// for index field we do this smashing for smashLength
					if (loc.countFieldSelector(field) >= SummariesEnv.v().smashLength
							&& loc.hasFieldTypeComp(type)) {
						// assert false;
						assert (loc instanceof AccessPath) : "only AccessPath has field selectors!";
						// only AccessPath has field selectors
						AccessPath path = ((AccessPath) loc)
								.getTypeCompPrefix(type);
						Set<FieldElem> smashedFields = ((AccessPath) loc)
								.getPreSmashedFieldsForTypeComp(type);
						if (path instanceof LocalAccessPath) {
							ret = getLocalAccessPath((LocalAccessPath) path);
						} else if (path instanceof StaticAccessPath) {
							ret = Env
									.getStaticAccessPath((StaticAccessPath) path);
						} else {
							assert false : "only access path is allowed!";
						}
						// TODO changing the location potentially dangerous!
						ret.addSmashedFields(smashedFields);
						ret.addSmashedField(field);
						ret.addEndingField(field);
					} else {
						if (loc instanceof StaticElem) {
							ret = Env.getStaticAccessPath((StaticElem) loc,
									field);
						} else if (loc instanceof ParamElem) {
							ret = getLocalAccessPath((ParamElem) loc, field);
						} else if (loc instanceof AccessPath) {
							if (loc instanceof StaticAccessPath) {
								ret = Env.getStaticAccessPath(
										(StaticAccessPath) loc, field);
							} else if (loc instanceof LocalAccessPath) {
								ret = getLocalAccessPath((LocalAccessPath) loc,
										field);
							} else {
								assert false : "only two kinds of access path!";
							}
						} else {
							assert false : "only three kinds of things can have default targets!";
						}
					}
				} else if (loc.hasFieldTypeComp(type)) {
					assert (loc instanceof AccessPath) : "only AccessPath has field selectors!";
					// only AccessPath has field selectors
					AccessPath path = ((AccessPath) loc)
							.getTypeCompPrefix(type);
					Set<FieldElem> smashedFields = ((AccessPath) loc)
							.getPreSmashedFieldsForTypeComp(type);
					if (path instanceof LocalAccessPath) {
						ret = getLocalAccessPath((LocalAccessPath) path);
					} else if (path instanceof StaticAccessPath) {
						ret = Env.getStaticAccessPath((StaticAccessPath) path);
					} else {
						assert false : "only access path is allowed!";
					}
					// TODO changing the location potentially dangerous!
					ret.addSmashedFields(smashedFields);
					ret.addSmashedField(field);
					ret.addEndingField(field);
				} else {
					if (loc instanceof StaticElem) {
						ret = Env.getStaticAccessPath((StaticElem) loc, field);
					} else if (loc instanceof ParamElem) {
						ret = getLocalAccessPath((ParamElem) loc, field);
					} else if (loc instanceof AccessPath) {
						// we should call the method without parameters
						// because loc is not a smashed access path!
						Set<FieldElem> smashedFields = ((AccessPath) loc)
								.getSmashedFields();
						if (loc instanceof StaticAccessPath) {
							ret = Env.getStaticAccessPath(
									(StaticAccessPath) loc, field);
							// TODO changing the location
							ret.addSmashedFields(smashedFields);
						} else if (loc instanceof LocalAccessPath) {
							ret = getLocalAccessPath((LocalAccessPath) loc,
									field);
							// TODO changing the location
							ret.addSmashedFields(smashedFields);
						} else {
							assert false : "only two kinds of access path!";
						}
					} else {
						assert false : "only three kinds of things can have default targets!";
					}
				}
			} else {
				assert false : "you can NOT get the default target for a non-arg derived mem loc!";
			}
		}

		assert (ret != null) : "you can NOT get the default target for a non-arg derived mem loc!";
		return ret;
	}

	protected Pair<Boolean, Boolean> weakUpdate(
			Pair<AbsMemLoc, FieldElem> pair, P2Set p2Set) {
		Pair<Boolean, Boolean> ret = new Pair<Boolean, Boolean>(false, false);

		// first clean up the default targets in the p2set given the pair
		// cleanup(p2Set, pair);

		AbsMemLoc src = pair.val0;
		FieldElem f = pair.val1;
		// determine which locals to propagate
		// fillPropSet(src, p2Set);
		// src has field f even if the p2set might be empty
		if (src instanceof LocalVarElem) {
			assert f instanceof EpsilonFieldElem : "we should not " + "add "
					+ f + " field " + "for local " + src;
		}
		src.addField(f);
		// update the locations in the real heap graph
		heap.add(src);
		heap.addAll(p2Set.keySet());
		// then get the current heap given the memory location and the field
		P2Set currentP2Set = locToP2Set.get(pair);
		// create the empty p2st if not existed
		if (currentP2Set == null) {
			currentP2Set = new P2Set();
			locToP2Set.put(pair, currentP2Set);
		}

		// typeFilter = Program.g().getType("java.lang.Object");

		Pair<Boolean, Boolean> res = currentP2Set.join(p2Set, this, src, f);

		ret.val0 = res.val0;
		ret.val1 = res.val1;

		// this is a conservatively way to clear the cache
		if (SummariesEnv.v().useMemLocInstnCache) {
			if (ret.val0) {
				if (G.dbgCache) {
					System.out.println("[dbgCache] "
							+ "clearing the cache for " + src + " " + f);
				}
				clearCache(src, f);

				if (SummariesEnv.v().jump) {
					if (src instanceof LocalVarElem) {
						Register v = ((LocalVarElem) src).getLocal();
						if (SummariesEnv.v().toProp(v)) {
							// we need to reanalyze this method (conservative)
							if (summary == null) {
								return ret;
							}
							for (Summary s : summary.jumpEffectSet) {
								s.jumpInstnSet.remove(summary);
							}
						}
					}
				}
			}
		}

		return ret;
	}

	// clear all the related cache including:
	// 1. memory location instantiation cache
	// 2. constraint instantiation cache
	protected Pair<Boolean, Boolean> clearCache(AbsMemLoc src, FieldElem f) {
		boolean ret1 = false;
		boolean ret2 = false;
		// this check is for the final summary (conclusion)
		if (summary == null) {
			return new Pair<Boolean, Boolean>(ret1, ret2);
		}
		// clear the memory location instantiation cache
		Map<MemLocInstnItem, Set<AccessPath>> deps = summary.locDepMap
				.get(new Pair<AbsMemLoc, FieldElem>(src, f));
		if (G.dbgCache) {
			System.out.println("[dbgCache] " + "locDepMap: ");
			summary.locDepMap.dump();
			System.out.println("[dbgCache] " + "deps: " + deps);
		}
		// possible that no one currently depends on src
		if (deps == null) {
			return new Pair<Boolean, Boolean>(ret1, ret2);
		}
		for (Iterator<Map.Entry<MemLocInstnItem, Set<AccessPath>>> it = deps
				.entrySet().iterator(); it.hasNext();) {
			Map.Entry<MemLocInstnItem, Set<AccessPath>> entry = it.next();
			MemLocInstnItem item = entry.getKey();
			Set<AccessPath> aps = entry.getValue();
			for (AccessPath ap : aps) {
				if (G.dbgCache) {
					System.out.println("[dbgCache] "
							+ "really removing cache for " + ap + " in item ");
				}
				ret1 = true;
				item.remove(ap);

				// reset the boolean flag in smartSkip to let the caller
				// instantiate the callee corresponding to the item
				// TODO currently we keep this to be conservative
				if (SummariesEnv.v().smartSkip) {
					summary.smartSkip.remove(item);
				}

				// clear the constraint instantiation cache
				if (SummariesEnv.v().isUsingCstCache()) {
					ret2 = clearCstCache(ap, item) | ret2;
				}
			}
		}
		return new Pair<Boolean, Boolean>(ret1, ret2);
	}

	protected boolean clearCstCache(AccessPath ap, MemLocInstnItem item) {
		boolean ret = false;
		Set<BoolExpr> exprs = ConstraintManager.getCstDepMap().getExprs(ap,
				item);
		for (BoolExpr expr : exprs) {
			ret = true;
			ConstraintManager.getCstInstnCache().removeExpr(item, expr);
		}

		return ret;
	}

	protected void cleanup(P2Set p2Set, Pair<AbsMemLoc, FieldElem> pair) {

		assert (p2Set != null) : "p2 set is null when doing the cleanup.";

		AbsMemLoc loc = pair.val0;
		FieldElem f = pair.val1;

		if (!loc.isArgDerived())
			return;

		AccessPath defaultTarget = getDefaultTarget(loc, f);

		if (p2Set.contains(defaultTarget)) {
			p2Set.remove(defaultTarget);
		}
	}

	public Map<Pair<AbsMemLoc, FieldElem>, P2Set> getHeap() {
		return this.locToP2Set;
	}

	public Set<AbsMemLoc> getAllMemLocs() {
		return this.memLocFactory.keySet();
	}

	// check whether some abstract memory location is contained in the factory
	public boolean hasCreated(AbsMemLoc loc) {
		return memLocFactory.containsKey(loc);
	}

	// check whether some abstract memory location is in the heap
	public boolean contains(AbsMemLoc loc) {
		return heap.contains(loc);
	}

	// mark whether the heap has changed.
	public void markChanged(Pair<Boolean, Boolean> flag) {
		isChanged.val0 = flag.val0;
		isChanged.val1 = flag.val1;
	}

	public boolean heapIsChanged() {
		return isChanged.val0;
	}

	public boolean sumIsChange() {
		return isChanged.val1;
	}

	public Pair<Boolean, Boolean> isChanged() {
		return new Pair<Boolean, Boolean>(isChanged.val0, isChanged.val1);
	}

	public int size() {
		int ret = 0;
		for (Pair<AbsMemLoc, FieldElem> loc : locToP2Set.keySet()) {
			ret += locToP2Set.get(loc).size();
		}
		return ret;
	}

	public BoolExpr remove(AbsMemLoc src, FieldElem f, HeapObject tgt) {
		P2Set tgts = locToP2Set.get(new Pair<AbsMemLoc, FieldElem>(src, f));

		if (tgts.contains(tgt)) {
			return tgts.remove(tgt);
		} else {
			assert false : "null";
			return null;
		}
	}

	public void validate() {
		for (Pair<AbsMemLoc, FieldElem> pair : locToP2Set.keySet()) {
			AbsMemLoc src = pair.val0;
			FieldElem f = pair.val1;
			P2Set p2set = locToP2Set.get(pair);
			if (src.isArgDerived()) {
				AccessPath dtgt = getDefaultTarget(src, f);
				assert (!p2set.contains(dtgt)) : "You cannot have default target in the heap!";
			}
		}
	}

	// print the heapObjectsToP2Set mapping in file
	public void dumpHeapMappingToFile(String count) {
		StringBuilder b = new StringBuilder("");
		for (Pair<AbsMemLoc, FieldElem> pair : locToP2Set.keySet()) {
			AbsMemLoc loc = pair.val0;
			FieldElem f = pair.val1;
			P2Set p2set = locToP2Set.get(pair);
			b.append("(" + loc + "," + f + ")\n");
			b.append(p2set + "\n");
		}

		try {
			BufferedWriter bufw = new BufferedWriter(new FileWriter(
					G.dotOutputPath + count + "_heapMapping"));
			bufw.write(b.toString());
			bufw.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	public void dumpAllMemLocsToFile(String count) {
		StringBuilder b = new StringBuilder("");
		for (AbsMemLoc loc : memLocFactory.keySet()) {
			b.append(loc + "\n");
		}

		try {
			BufferedWriter bufw = new BufferedWriter(new FileWriter(
					G.dotOutputPath + count + ".dot"));
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

		Set<AbsMemLoc> allLocs = new HashSet<AbsMemLoc>();

		for (Pair<AbsMemLoc, FieldElem> pair : locToP2Set.keySet()) {
			allLocs.add(pair.val0);
			for (HeapObject hObj : locToP2Set.get(pair).keySet()) {
				allLocs.add(hObj);
			}
		}

		for (AbsMemLoc loc : allLocs) {
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
				assert false : "wired things! Unknow memory location";
			}
		}

		for (Pair<AbsMemLoc, FieldElem> pair : locToP2Set.keySet()) {
			AbsMemLoc loc = pair.val0;
			FieldElem f = pair.val1;
			P2Set p2Set = locToP2Set.get(pair);
			for (HeapObject hObj : p2Set.keySet()) {
				BoolExpr cst = p2Set.get(hObj);
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
					G.dotOutputPath + count + ".dot"));
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

		for (AbsMemLoc loc : memLocFactory.keySet()) {
			if (loc instanceof LocalAccessPath) {
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
				assert false : "wired things! Unknown memory location.";
			}
		}

		for (AbsMemLoc loc : memLocFactory.keySet()) {
			Set<FieldElem> fields = loc.getFields();
			for (FieldElem f : fields) {
				// we should not use the following commented to print the p2set
				// P2Set p2Set = heapObjectsToP2Set.get(getAbstractMemLoc(loc,
				// f));
				// instead we should use the following
				P2Set p2Set = lookup(loc, f);
				assert (p2Set != null) : "get a null p2 set!";

				for (HeapObject obj : p2Set.keySet()) {
					BoolExpr cst = p2Set.get(obj);
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

	// actually we do not use this method for Scuba project
	// because we use Chord in SSA form so that it is not necessary to do strong
	// update for local variables (they will have different names if assigned
	// more than once), also we do weak update for heap objects and the local
	// variables in SCC (e.g loops)
	protected boolean strongUpdate(Pair<AbsMemLoc, FieldElem> pair, P2Set p2Set) {
		assert (pair.val0 instanceof StackObject) : "Only stack objects can do strong update!";
		assert (pair.val1 instanceof EpsilonFieldElem) : "Only stack objects with epsilon field"
				+ " can do strong update!";
		pair.val0.addField(pair.val1);
		locToP2Set.put(pair, p2Set);
		return false;
	}

	public boolean toProp(AbsMemLoc loc) {
		return toProp.contains(loc);
	}

	public Set<AbsMemLoc> getProps() {
		return toProp;
	}

	// fill the toProp set which determines what kinds of locations to propagate
	// upwards to the callers, and this is done after terminating some method
	public void fillPropSet(AbsMemLoc src, P2Set p2Set) {
		if (src instanceof LocalVarElem) {
			Register v = ((LocalVarElem) src).getLocal();
			if (SummariesEnv.v().toProp(v)) {
				toProp.add(src);
				Set<AllocElem> wl = new HashSet<AllocElem>();
				for (HeapObject hObj : p2Set.keySet()) {
					if (hObj instanceof AllocElem) {
						wl.add((AllocElem) hObj);
					}
				}
				findPropAllocs(wl);
			}
		} else if (src instanceof AccessPath || src instanceof ParamElem
				|| src instanceof StaticElem || src instanceof RetElem) {
			for (HeapObject hObj : p2Set.keySet()) {
				if (hObj instanceof AllocElem) {
					toProp.add((AllocElem) hObj);
				}
			}
		} else if (src instanceof AllocElem) {
			if (toProp.contains(src)) {
				Set<AllocElem> wl = new HashSet<AllocElem>();
				for (HeapObject hObj : p2Set.keySet()) {
					if (hObj instanceof AllocElem) {
						wl.add((AllocElem) hObj);
					}
				}
				findPropAllocs(wl);
			}
		}
	}

	private void findPropAllocs(Set<AllocElem> wl) {
		Set<AllocElem> set = new HashSet<AllocElem>();
		while (!wl.isEmpty()) {
			toProp.addAll(wl);
			for (AllocElem alloc : wl) {
				Set<FieldElem> fields = alloc.getFields();
				for (FieldElem f : fields) {
					P2Set p2set = locToP2Set
							.get(new Pair<AbsMemLoc, FieldElem>(alloc, f));
					if (p2set == null) {
						return;
					}
					for (HeapObject hObj : p2set.keySet()) {
						if (hObj instanceof AllocElem && !toProp.contains(hObj)) {
							set.add((AllocElem) hObj);
						}
					}
				}
			}
			wl.clear();
			wl.addAll(set);
			set.clear();
		}
	}

	public void fillPropSet() {
		Set<AllocElem> wl = new HashSet<AllocElem>();
		Set<AbsMemLoc> locals = new HashSet<AbsMemLoc>();

		if (SummariesEnv.v().propParams) {
			// this is a post-processing which create pseudo-locals for
			// parameters which we want to propagate
			for (ParamElem param : summary.formals) {
				Register v = param.getParameter();
				if (SummariesEnv.v().toProp(v)) {
					// create a pseudo-local element
					LocalVarElem pLocal = getLocalVarElem(summary.getMethod()
							.getDeclaringClass(), summary.getMethod(), v,
							param.getType());
					// do an extra assign for local = parameter
					// put this pseudo-local element into the heap
					handleAssignStmt(summary.getMethod().getDeclaringClass(),
							summary.getMethod(), v,
							VariableType.LOCAL_VARIABLE, param.getType(), v,
							VariableType.PARAMEMTER, param.getType());
					// instead, we prop this pseudo-local element
					locals.add(pLocal);
				}
			}
		}
		// add all locations that are guaranteed to be propagated to the caller
		for (Iterator<Map.Entry<Pair<AbsMemLoc, FieldElem>, P2Set>> it = locToP2Set
				.entrySet().iterator(); it.hasNext();) {
			Entry<Pair<AbsMemLoc, FieldElem>, P2Set> entry = it.next();
			Pair<AbsMemLoc, FieldElem> pair = entry.getKey();
			AbsMemLoc loc = entry.getKey().val0;

			// this is the normal propagation part
			if (loc instanceof AccessPath || loc instanceof ParamElem
					|| loc instanceof StaticElem || loc instanceof RetElem) {
				// add all potential allocs into wl
				P2Set p2set = locToP2Set.get(pair);
				for (HeapObject hObj : p2set.keySet()) {
					if (hObj instanceof AllocElem) {
						wl.add((AllocElem) hObj);
					}
				}
			} else if (loc instanceof LocalVarElem) {
				Register v = ((LocalVarElem) loc).getLocal();
				if (SummariesEnv.v().toProp(v)) {
					locals.add(loc);
					// add all potential allocs into wl
					P2Set p2set = locToP2Set.get(pair);
					for (HeapObject hObj : p2set.keySet()) {
						if (hObj instanceof AllocElem) {
							locals.add((AllocElem) hObj);
						}
					}
				}
			} else {
				assert (loc instanceof AllocElem) : "wrong!";
			}
		}
		// use a worklist algorithm to find all allocs to propagate
		Set<AllocElem> set = new HashSet<AllocElem>();
		while (!wl.isEmpty()) {
			toProp.addAll(wl);
			for (AllocElem alloc : wl) {
				Set<FieldElem> fields = alloc.getFields();
				for (FieldElem f : fields) {
					P2Set p2set = locToP2Set
							.get(new Pair<AbsMemLoc, FieldElem>(alloc, f));
					for (HeapObject hObj : p2set.keySet()) {
						if (hObj instanceof AllocElem && !toProp.contains(hObj)) {
							set.add((AllocElem) hObj);
						}
					}
				}
			}
			wl.clear();
			wl.addAll(set);
			set.clear();
		}
		toProp.addAll(locals);
	}

	protected void addDepEdges(MemLocInstnItem item, AbsMemLoc src,
			HeapObject tgt, FieldElem f) {
		// add the edge into instantiated edges
		Trio<AbsMemLoc, HeapObject, FieldElem> edge = new Trio<AbsMemLoc, HeapObject, FieldElem>(
				src, tgt, f);
		Set<Trio<AbsMemLoc, HeapObject, FieldElem>> instnedEdgs = summary.instnedEdges
				.get(item);
		if (instnedEdgs == null) {
			instnedEdgs = new HashSet<Trio<AbsMemLoc, HeapObject, FieldElem>>();
			summary.instnedEdges.put(item, instnedEdgs);
		}
		instnedEdgs.add(edge);
		// mark dependence relation
		Map<AbsMemLoc, Set<Trio<AbsMemLoc, HeapObject, FieldElem>>> edges = summary.edgeDepMap
				.get(item);
		if (edges == null) {
			edges = new HashMap<AbsMemLoc, Set<Trio<AbsMemLoc, HeapObject, FieldElem>>>();
			summary.edgeDepMap.put(item, edges);
		}
		// for src
		Set<Trio<AbsMemLoc, HeapObject, FieldElem>> deps = edges.get(src);
		if (deps == null) {
			deps = new HashSet<Trio<AbsMemLoc, HeapObject, FieldElem>>();
			edges.put(src, deps);
		}
		deps.add(edge);
		// for tgt
		deps = edges.get(tgt);
		if (deps == null) {
			deps = new HashSet<Trio<AbsMemLoc, HeapObject, FieldElem>>();
			edges.put(tgt, deps);
		}
		deps.add(edge);

	}
}
