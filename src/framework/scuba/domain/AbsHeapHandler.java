package framework.scuba.domain;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Array;
import joeq.Class.jq_Class;
import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.util.tuple.object.Pair;

import com.microsoft.z3.BoolExpr;

import framework.scuba.domain.AbsHeap.VariableType;
import framework.scuba.helper.AllocElemHelper;
import framework.scuba.helper.CstManager;
import framework.scuba.helper.P2SetHelper;

public class AbsHeapHandler {

	protected AbsHeap absHeap;

	public void setAbsHeap(AbsHeap absHeap) {
		this.absHeap = absHeap;
	}

	// v1 = v2
	public Pair<Boolean, Boolean> handleAssignStmt(jq_Class clazz,
			jq_Method meth, Register lr, jq_Type lt, VariableType lvt,
			Register rr, jq_Type rt, VariableType rvt) {
		Pair<Boolean, Boolean> ret = new Pair<Boolean, Boolean>(false, false);
		StackObject v1 = null, v2 = null;

		// generate memory location for lhs
		if (lvt == VariableType.LOCAL_VARIABLE) {
			v1 = Env.getLocalVarElem(lr, meth, clazz, lt);
		} else if (lvt == VariableType.PARAMETER) {
			v1 = Env.getParamElem(lr, meth, clazz, lt);
		} else {
			assert false : "for assign stmt, lhs must be LocalElem " + lr + " "
					+ rr;
		}
		assert (v1 != null) : "v1 is null!";

		// generate the memory location for rhs
		if (rvt == VariableType.PARAMETER) {
			v2 = Env.getParamElem(rr, meth, clazz, rt);
		} else if (rvt == VariableType.LOCAL_VARIABLE) {
			v2 = Env.getLocalVarElem(rr, meth, clazz, rt);
		} else {
			assert false : "for assign stmt, rhs must be LocalElem or ParamElem";
		}
		assert (v2 != null) : "v2 is null!";

		P2Set p2Setv2 = absHeap.lookup(v2, Env.getEpsilonFieldElem());
		assert (p2Setv2 != null) : "get a null p2 set!";

		Pair<AbsMemLoc, FieldElem> pair = new Pair<AbsMemLoc, FieldElem>(v1,
				Env.getEpsilonFieldElem());

		Pair<Boolean, Boolean> res = weakUpdate(pair, p2Setv2);
		ret.val0 = res.val0;
		ret.val1 = res.val1;

		return ret;
	}

	// v1 = CHECKCAST(v2)
	public Pair<Boolean, Boolean> handleCheckCastStmt(jq_Class clazz,
			jq_Method meth, Register lr, jq_Type lt, VariableType lvt,
			Register rr, jq_Type rt, VariableType rvt) {
		Pair<Boolean, Boolean> ret = new Pair<Boolean, Boolean>(false, false);
		Pair<Boolean, Boolean> res = handleAssignStmt(clazz, meth, lr, lt, lvt,
				rr, rt, rvt);
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
		P2Set p2Set = new P2Set(right, CstManager.genTrue());
		assert p2Set != null : "p2 set can not be null!";
		Pair<Boolean, Boolean> res = weakUpdate(pair, p2Set);
		ret.val0 = res.val0;
		ret.val1 = res.val1;
		return ret;
	}

	// v1 = v2.f
	public Pair<Boolean, Boolean> handleLoadStmt(jq_Class clazz,
			jq_Method meth, Register lr, jq_Type lt, VariableType lvt,
			Register rr, jq_Type rt, VariableType rvt, jq_Field rf) {
		Pair<Boolean, Boolean> ret = new Pair<Boolean, Boolean>(false, false);

		// generates StackObject (either ParamElem or LocalVarElem)
		LocalVarElem v1 = null;
		StackObject v2 = null;

		// generate the memory location for lhs
		if (lvt == VariableType.LOCAL_VARIABLE) {
			v1 = Env.getLocalVarElem(lr, meth, clazz, lt);
		} else {
			assert false : "for non-static load stmt, lhs must be LocalElem";
		}
		assert (v1 != null) : "v1 is null!";

		// generate the memory location for rhs's base
		if (rvt == VariableType.PARAMETER) {
			v2 = Env.getParamElem(rr, meth, clazz, rt);
		} else if (lvt == VariableType.LOCAL_VARIABLE) {
			v2 = Env.getLocalVarElem(rr, meth, clazz, rt);
		} else {
			assert false : "for non-static load stmt, rhs base must be LocalElem or ParamElem!";
		}
		assert (v2 != null) : "v2 is null!";

		P2Set p2Setv2 = absHeap.lookup(v2, Env.getEpsilonFieldElem());
		assert (p2Setv2 != null) : "get a null p2 set!";

		RegFieldElem f = Env.getRegFieldElem(rf);
		P2Set p2Setv2f = absHeap.lookup(p2Setv2, f);
		assert (p2Setv2f != null) : "get a null p2 set!";

		Pair<AbsMemLoc, FieldElem> pair = new Pair<AbsMemLoc, FieldElem>(v1,
				Env.getEpsilonFieldElem());
		Pair<Boolean, Boolean> res = weakUpdate(pair, p2Setv2f);
		ret.val0 = res.val0;
		ret.val1 = res.val1;
		return ret;
	}

	// v1 = v2[0] where v2 is an array, e.g. v2 = new X[10][10]
	public Pair<Boolean, Boolean> handleALoadStmt(jq_Class clazz,
			jq_Method meth, Register lr, jq_Type lt, VariableType lvt,
			Register rr, jq_Type rt, VariableType rvt) {

		Pair<Boolean, Boolean> ret = new Pair<Boolean, Boolean>(false, false);

		LocalVarElem v1 = Env.getLocalVarElem(lr, meth, clazz, lt);
		assert (v1 != null) : "v1 is null!";
		LocalVarElem v2 = Env.getLocalVarElem(rr, meth, clazz, rt);
		assert (v2 != null) : "v2 is null!";

		P2Set p2Setv2 = absHeap.lookup(v2, Env.getEpsilonFieldElem());
		assert (p2Setv2 != null) : "get a null p2 set!";

		// TODO need to verifying this getArrayType method for Chord
		assert (v2.getType() instanceof jq_Array) : "rhs of ALoad must be jq_Array!";
		IndexFieldElem index = Env.getIndexFieldElem();
		P2Set p2Setv2i = absHeap.lookup(p2Setv2, index);
		assert (p2Setv2i != null) : "get a null p2 set!";

		Pair<AbsMemLoc, FieldElem> pair = new Pair<AbsMemLoc, FieldElem>(v1,
				Env.getEpsilonFieldElem());

		Pair<Boolean, Boolean> res = weakUpdate(pair, p2Setv2i);
		ret.val0 = res.val0;
		ret.val1 = res.val1;
		return ret;
	}

	// v1 = A.f
	public Pair<Boolean, Boolean> handleStatLoadStmt(jq_Class clazz,
			jq_Method meth, Register lr, jq_Type lt, VariableType lvt,
			jq_Field rf) {

		Pair<Boolean, Boolean> ret = new Pair<Boolean, Boolean>(false, false);

		StackObject v1 = null;
		// generate the memory location for lhs
		if (lvt == VariableType.LOCAL_VARIABLE) {
			v1 = Env.getLocalVarElem(lr, meth, clazz, lt);
		} else {
			assert false : "for static load stmt, lhs must be LocalElem!";
		}
		assert (v1 != null) : "v1 is null!";

		// generate the memory location for rhs's base
		StaticFieldElem v2 = Env.getStaticFieldElem(rf);
		assert (v2 != null) : "v2 is null!";

		P2Set p2Setv2 = absHeap.lookup(v2, Env.getEpsilonFieldElem());
		assert (p2Setv2 != null) : "get a null p2 set!";

		Pair<AbsMemLoc, FieldElem> pair = new Pair<AbsMemLoc, FieldElem>(v1,
				Env.getEpsilonFieldElem());

		Pair<Boolean, Boolean> res = weakUpdate(pair, p2Setv2);
		ret.val0 = res.val0;
		ret.val1 = res.val1;
		return ret;
	}

	// v1[0] = v2 where v1 = new V[10][10]
	public Pair<Boolean, Boolean> handleAStoreStmt(jq_Class clazz,
			jq_Method meth, Register lr, jq_Type lt, VariableType lvt,
			Register rr, jq_Type rt, VariableType rvt) {

		Pair<Boolean, Boolean> ret = new Pair<Boolean, Boolean>(false, false);

		StackObject v1 = null, v2 = null;

		// generate the memory location for rhs
		if (lvt == VariableType.PARAMETER) {
			v1 = Env.getParamElem(lr, meth, clazz, lt);
		} else if (lvt == VariableType.LOCAL_VARIABLE) {
			v1 = Env.getLocalVarElem(lr, meth, clazz, lt);
		} else {
			assert false : "for array store stmt,"
					+ " lhs Base must be LocalElem or ParamElem!";
		}
		assert (v1 != null) : "v1 is null!";

		// generate the memory location for rhs's base
		if (rvt == VariableType.PARAMETER) {
			v2 = Env.getParamElem(rr, meth, clazz, rt);
		} else if (rvt == VariableType.LOCAL_VARIABLE) {
			v2 = Env.getLocalVarElem(rr, meth, clazz, rt);
		} else {
			assert false : "for non-static store stmt, rhs must be LocalElem or ParamElem!";
		}
		assert (v2 != null) : "v2 is null!";

		P2Set p2Setv1 = absHeap.lookup(v1, Env.getEpsilonFieldElem());
		assert (p2Setv1 != null) : "get a null p2 set!";
		P2Set p2Setv2 = absHeap.lookup(v2, Env.getEpsilonFieldElem());
		assert (p2Setv2 != null) : "get a null p2 set!";

		// TODO need verifying this
		// assert (v1.getType() instanceof jq_Array) :
		// "lhs of AStore must be jq_Array!";
		IndexFieldElem index = Env.getIndexFieldElem();
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

	// v1.f = v2
	public Pair<Boolean, Boolean> handleStoreStmt(jq_Class clazz,
			jq_Method meth, Register lr, jq_Type lt, VariableType lvt,
			jq_Field lf, Register rr, jq_Type rt, VariableType rvt) {
		Pair<Boolean, Boolean> ret = new Pair<Boolean, Boolean>(false, false);

		StackObject v1 = null, v2 = null;
		// generate the memory location for lhs
		if (lvt == VariableType.PARAMETER) {
			v1 = Env.getParamElem(lr, meth, clazz, lt);
		} else if (lvt == VariableType.LOCAL_VARIABLE) {
			v1 = Env.getLocalVarElem(lr, meth, clazz, lt);
		} else {
			assert false : "for non-static store load stmt,"
					+ " lhs base must be LocalElem or ParamElem!";
		}
		assert (v1 != null) : "v1 is null!";

		// generate the memory location for rhs's base
		if (rvt == VariableType.PARAMETER) {
			v2 = Env.getParamElem(rr, meth, clazz, rt);
		} else if (rvt == VariableType.LOCAL_VARIABLE) {
			v2 = Env.getLocalVarElem(rr, meth, clazz, rt);
		} else {
			assert false : "for non-static store stmt, rhs must be LocalElem or ParamElem!";
		}
		assert (v2 != null) : "v2 is null!";

		P2Set p2Setv1 = absHeap.lookup(v1, Env.getEpsilonFieldElem());
		assert (p2Setv1 != null) : "get a null p2 set!";
		P2Set p2Setv2 = absHeap.lookup(v2, Env.getEpsilonFieldElem());
		assert (p2Setv2 != null) : "get a null p2 set!";

		RegFieldElem f = Env.getRegFieldElem(lf);
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

	// A.f = v2
	public Pair<Boolean, Boolean> handleStaticStoreStmt(jq_Class clazz,
			jq_Method meth, jq_Field lf, Register rr, jq_Type rt,
			VariableType rvt) {

		Pair<Boolean, Boolean> ret = new Pair<Boolean, Boolean>(false, false);

		// generate the memory location for lhs's base
		StaticFieldElem v1 = Env.getStaticFieldElem(lf);
		assert (v1 != null) : "v1 is null!";

		StackObject v2 = null;
		// generate the memory location for rhs
		if (rvt == VariableType.PARAMETER) {
			v2 = Env.getParamElem(rr, meth, clazz, rt);
		} else if (rvt == VariableType.LOCAL_VARIABLE) {
			v2 = Env.getLocalVarElem(rr, meth, clazz, rt);
		} else {
			assert false : "for static store stmt, rhs must be LocalElem or ParamElem!";
		}
		assert (v2 != null) : "v2 is null!";

		P2Set p2Setv2 = absHeap.lookup(v2, Env.getEpsilonFieldElem());
		assert (p2Setv2 != null) : "get a null p2 set!";

		Pair<AbsMemLoc, FieldElem> pair = new Pair<AbsMemLoc, FieldElem>(v1,
				Env.getEpsilonFieldElem());

		Pair<Boolean, Boolean> res = weakUpdate(pair, p2Setv2);
		ret.val0 = res.val0;
		ret.val1 = res.val1;
		return ret;
	}

	// v = new T
	public Pair<Boolean, Boolean> handleNewStmt(jq_Class clazz, jq_Method meth,
			Register lr, jq_Type lt, VariableType lvt, Quad stmt) {

		Pair<Boolean, Boolean> ret = new Pair<Boolean, Boolean>(false, false);

		// generate the allocElem for rhs
		jq_Type type = AllocElemHelper.rslvNewType(stmt);
		AllocElem allocT = Env.getAllocElem(stmt, type);
		assert (allocT != null) : "allocT is null!";

		LocalVarElem v = null;
		// generate the localVarElem for lhs
		if (lvt == VariableType.LOCAL_VARIABLE) {
			v = Env.getLocalVarElem(lr, meth, clazz, lt);
		} else {
			assert false : "lhs of a new stmt must be a local variable!";
		}
		assert (v != null) : "v is null!";

		Pair<AbsMemLoc, FieldElem> pair = new Pair<AbsMemLoc, FieldElem>(v,
				Env.getEpsilonFieldElem());

		Pair<Boolean, Boolean> res = weakUpdate(pair, new P2Set(allocT,
				CstManager.genTrue()));
		ret.val0 = res.val0;
		ret.val1 = res.val1;
		return ret;
	}

	// X x1 = new X[10] by calling handleMultiNewArrayStmt method with dim = 1
	public Pair<Boolean, Boolean> handleNewArrayStmt(jq_Class clazz,
			jq_Method meth, Register lr, jq_Type lt, VariableType lvt, Quad stmt) {
		return handleMultiNewArrayStmt(clazz, meth, lr, lt, lvt, 1, stmt);
	}

	// handle multi-new stmt, e.g. X x1 = new X[1][2][3]
	// dim is the dimension of this array, dim >= 2
	public Pair<Boolean, Boolean> handleMultiNewArrayStmt(jq_Class clazz,
			jq_Method meth, Register lr, jq_Type lt, VariableType lvt, int dim,
			Quad stmt) {

		Pair<Boolean, Boolean> ret = new Pair<Boolean, Boolean>(false, false);

		LocalVarElem v = null;
		// generate the localVarElem for lhs
		if (lvt == VariableType.LOCAL_VARIABLE) {
			v = Env.getLocalVarElem(lr, meth, clazz, lt);
		} else {
			assert false : "lhs of a new stmt must be a local variable!";
		}
		assert (v != null) : "v is null!";
		// generate the ArrayAllocElem for rhs
		jq_Type type = AllocElemHelper.rslvNewArrayType(stmt);
		AllocElem allocT = Env.getAllocElem(stmt, type);
		assert (allocT != null) : "null alloc!";

		Pair<AbsMemLoc, FieldElem> pair = new Pair<AbsMemLoc, FieldElem>(v,
				Env.getEpsilonFieldElem());
		// update the LHS's P2Set weakly
		ret = weakUpdate(pair, new P2Set(allocT, CstManager.genTrue()));

		int i = dim;
		jq_Type lt1 = type;
		jq_Type rt1 = null;
		while (i >= 2) {
			assert (lt1 instanceof jq_Array) : "lhs of multi-new must be jq_Array!";
			rt1 = ((jq_Array) lt1).getElementType();
			AllocElem leftAllocT = Env.getAllocElem(stmt, lt1);
			AllocElem rightAllocT = Env.getAllocElem(stmt, rt1);
			Pair<Boolean, Boolean> res = handleArrayLoad(leftAllocT,
					Env.getIndexFieldElem(), rightAllocT);
			ret.val0 = res.val0 | ret.val0;
			ret.val1 = res.val1 | ret.val1;
			assert (lt1 instanceof jq_Array) : "lhs of multi-new must be jq_Array!";
			lt1 = ((jq_Array) lt1).getElementType();
			i--;
		}

		return ret;
	}

	// return v;
	public Pair<Boolean, Boolean> handleRetStmt(jq_Class clazz, jq_Method meth,
			Register rr, jq_Type rt, VariableType rvt) {

		Pair<Boolean, Boolean> ret = new Pair<Boolean, Boolean>(false, false);
		// first try to find the corresponding local or parameter that has been
		// declared before returning
		StackObject v = null;
		if (rvt == VariableType.LOCAL_VARIABLE) {
			v = Env.getLocalVarElem(rr, meth, clazz, rt);
		} else if (rvt == VariableType.PARAMETER) {
			v = Env.getParamElem(rr, meth, clazz, rt);
		} else {
			assert false : "we are only considering return value to be local or parameter!";
		}

		// create return value element (only one return value for one method)
		RetElem retElem = Env.getRetElem(meth);
		// update the p2set of the return value
		Pair<AbsMemLoc, FieldElem> pair = new Pair<AbsMemLoc, FieldElem>(
				retElem, Env.getEpsilonFieldElem());
		P2Set p2Set = absHeap.lookup(v, Env.getEpsilonFieldElem());
		assert (p2Set != null) : "get a null p2set!";

		Pair<Boolean, Boolean> res = weakUpdate(pair, p2Set);
		ret.val0 = res.val0;
		ret.val1 = res.val1;
		return ret;
	}

	public Pair<Boolean, Boolean> handleInvokeStmt(Quad callsite,
			AbsHeap calleeHeap, MemLocInstnItem memLocInstn, BoolExpr typeCst) {
		Pair<Boolean, Boolean> ret = new Pair<Boolean, Boolean>(false, false);
		ProgPoint point = Env.getProgPoint(callsite);
		// this is used for recursive call
		boolean isRecursive = false;
		if (absHeap.equals(calleeHeap)) {
			isRecursive = true;
		}
		// this is the real updating
		if (!isRecursive) {
			while (true) {
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

						ret.val0 = res.val0 | ret.val0;
						ret.val1 = res.val1 | ret.val1;
						// changing the heap means we need go (conservative)
						go = res.val0 | go;
					}
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

				if (!go) {
					break;
				}
				if (!SummariesEnv.v().useFixPoint) {
					break;
				}
			}
		}

		return ret;
	}

	private Pair<Boolean, Boolean> instnEdge(AbsMemLoc src, HeapObject dst,
			FieldElem field, MemLocInstnItem memLocInstn, AbsHeap calleeHeap,
			ProgPoint point, BoolExpr typeCst) {

		Pair<Boolean, Boolean> ret = new Pair<Boolean, Boolean>(false, false);

		assert (src != null && dst != null && field != null) : "nulls!";
		assert (calleeHeap.contains(src)) : "callee's heap should contain the source of the edge!";
		assert (calleeHeap.lookup(src, field).contains(dst)) : ""
				+ "the p2 set should contain the destination of the edge!";

		if (!propFilter(src)) {
			return ret;
		}

		BoolExpr calleeCst = calleeHeap.lookup(src, field).get(dst);
		assert (calleeCst != null) : "constraint is null!";

		// instantiate the calleeCst
		BoolExpr instnCst = instnCst(calleeCst, absHeap, point, memLocInstn);

		MemLocInstnSet instnSrc = memLocInstn.instnMemLoc(src, absHeap, point);
		MemLocInstnSet instnDst = memLocInstn.instnMemLoc(dst, absHeap, point);

		assert (instnDst != null) : "instantiation of dst cannot be null!";
		if (instnSrc == null) {
			assert (src instanceof RetElem) : "only return value in the callee"
					+ " is allowed not having an instantiated location in the callee";
		}

		if (instnSrc == null) {
			return ret;
		}

		for (AbsMemLoc newSrc : instnSrc.keySet()) {

			for (AbsMemLoc newDst : instnDst.keySet()) {
				assert (newDst instanceof HeapObject) : ""
						+ "dst should be instantiated as a heap object!";
				HeapObject newDst1 = (HeapObject) newDst;

				assert (newDst1 != null) : "null!";

				BoolExpr cst1 = instnSrc.get(newSrc);
				BoolExpr cst2 = instnDst.get(newDst1);
				BoolExpr cst = CstManager.intersect(
						CstManager.intersect(cst1, cst2),
						CstManager.intersect(instnCst, typeCst));

				assert (cst != null) : "null cst!";
				assert (cst1 != null && cst2 != null && cst != null) : "get null constraints!";
				Pair<AbsMemLoc, FieldElem> pair = new Pair<AbsMemLoc, FieldElem>(
						newSrc, field);

				Pair<Boolean, Boolean> res = weakUpdate(pair, new P2Set(
						newDst1, cst));
				ret.val0 = res.val0 | ret.val0;
				ret.val1 = res.val1 | ret.val1;

			}
		}

		return ret;
	}

	private Pair<Boolean, Boolean> instnEdge4RecurCall(AbsMemLoc src,
			HeapObject dst, FieldElem field, MemLocInstnItem memLocInstn,
			AbsHeap calleeHeap, ProgPoint point, BoolExpr typeCst,
			Set<Pair<AbsMemLoc, P2Set>> toAdd) {

		Pair<Boolean, Boolean> ret = new Pair<Boolean, Boolean>(false, false);

		assert (src != null && dst != null && field != null) : "nulls!";
		assert (calleeHeap.contains(src)) : "callee's heap should contain the source of the edge!";
		assert (calleeHeap.lookup(src, field).contains(dst)) : ""
				+ "the p2 set should contain the destination of the edge!";

		if (!propFilter(src)) {
			return ret;
		}

		BoolExpr calleeCst = calleeHeap.lookup(src, field).get(dst);
		assert (calleeCst != null) : "constraint is null!";

		// instantiate the calleeCst
		BoolExpr instnCst = instnCst(calleeCst, absHeap, point, memLocInstn);

		MemLocInstnSet instnSrc = memLocInstn.instnMemLoc(src, absHeap, point);
		MemLocInstnSet instnDst = memLocInstn.instnMemLoc(dst, absHeap, point);

		assert (instnDst != null) : "instantiation of dst cannot be null!";
		if (instnSrc == null) {
			assert (src instanceof RetElem) : "only return value in the callee"
					+ " is allowed not having an instantiated location in the callee";
		}

		if (instnSrc == null) {
			return ret;
		}

		for (AbsMemLoc newSrc : instnSrc.keySet()) {
			for (AbsMemLoc newDst : instnDst.keySet()) {

				assert (newDst instanceof HeapObject) : ""
						+ "dst should be instantiated as a heap object!";
				HeapObject newDst1 = (HeapObject) newDst;

				assert (newDst1 != null) : "null!";

				BoolExpr cst1 = instnSrc.get(newSrc);
				BoolExpr cst2 = instnDst.get(newDst);

				BoolExpr cst = CstManager.intersect(
						CstManager.intersect(cst1, cst2),
						CstManager.intersect(instnCst, typeCst));
				assert (cst != null) : "null cst1";
				assert (cst1 != null && cst2 != null && cst != null) : "get null constraints!";
				toAdd.add(new Pair<AbsMemLoc, P2Set>(newSrc, new P2Set(newDst1,
						cst)));
			}
		}

		return ret;
	}

	/* Constraint instantiation. */
	private BoolExpr instnCst(BoolExpr cst, AbsHeap callerHeap,
			ProgPoint point, MemLocInstnItem memLocInstn) {
		if (SummariesEnv.v().disableCst() || absHeap.summary.isInBadScc())
			return CstManager.genTrue();
		assert cst != null : "Invalid Constrait before instantiation.";
		// return directly.
		if (CstManager.isScala(cst))
			return cst;

		BoolExpr instC = CstManager.instnConstaintNew(cst, callerHeap, point,
				memLocInstn);

		assert instC != null : "Invalid instantiated Constrait.";
		return instC;
	}

	protected Pair<Boolean, Boolean> weakUpdate(
			Pair<AbsMemLoc, FieldElem> pair, P2Set p2Set) {
		Pair<Boolean, Boolean> ret = new Pair<Boolean, Boolean>(false, false);

		AbsMemLoc src = pair.val0;
		FieldElem f = pair.val1;

		if (src instanceof LocalVarElem) {
			assert f instanceof EpsilonFieldElem : "we should not " + "add "
					+ f + " field " + "for local " + src;
		}
		src.addField(f);
		// update the locations in the real heap graph
		absHeap.heap.add(src);
		absHeap.heap.addAll(p2Set.keySet());
		// then get the current heap given the memory location and the field
		P2Set currentP2Set = absHeap.locToP2Set.get(pair);
		// create the empty p2st if not existed
		if (currentP2Set == null) {
			currentP2Set = new P2Set();
			absHeap.locToP2Set.put(pair, currentP2Set);
		}

		Pair<Boolean, Boolean> res = currentP2Set.join(p2Set, absHeap, src, f);

		ret.val0 = res.val0;
		ret.val1 = res.val1;

		return ret;
	}

	protected boolean propFilter(AbsMemLoc src) {
		if (SummariesEnv.v().localType == SummariesEnv.PropType.NOLOCAL) {
			if (src instanceof LocalVarElem) {
				return false;
			} else if (src instanceof AllocElem) {
				if (!Env.toProp(src)) {
					return false;
				}
			}
		} else if (SummariesEnv.v().localType == SummariesEnv.PropType.ALL) {

		} else if (SummariesEnv.v().localType == SummariesEnv.PropType.NOTHING) {
			if (src.isNotArgDvd()) {
				return false;
			}
		} else if (SummariesEnv.v().localType == SummariesEnv.PropType.DOWNCAST
				|| SummariesEnv.v().localType == SummariesEnv.PropType.APPLOCAL) {
			if (src instanceof LocalVarElem || src instanceof AllocElem) {
				if (!Env.toProp(src)) {
					return false;
				}
			}
		} else {
			assert false : "unsupported prop type.";
		}
		return true;
	}

}