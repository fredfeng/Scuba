package framework.scuba.domain;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import chord.util.tuple.object.Pair;

import com.microsoft.z3.BoolExpr;

import framework.scuba.helper.CstManager;

public class MemLocInstnItem {

	final protected MemLocInstn4Method result;

	// the caller that this instantiation belongs to
	final protected jq_Method caller;

	// the call site that this instantiation is for
	final protected Quad callsite;

	// the callee that this instantiation is for
	final protected jq_Method callee;

	// hasRet = true: there is a location mapped in the caller's heap
	protected boolean hasRet = false;

	protected Map<ParamElem, StackObject> fmlsToActls = new HashMap<ParamElem, StackObject>();
	protected Map<RetElem, StackObject> retToRecv = new HashMap<RetElem, StackObject>();

	public MemLocInstnItem(jq_Method caller, Quad callsite, jq_Method callee,
			MemLocInstn4Method result) {
		this.caller = caller;
		this.callsite = callsite;
		this.callee = callee;
		this.result = result;
	}

	public jq_Method getCaller() {
		return caller;
	}

	public jq_Method getCallee() {
		return callee;
	}

	public Quad getCallSite() {
		return callsite;
	}

	// initialized the formal-to-actual mapping
	public void initFormalToActualMapping(List<ParamElem> formals,
			List<StackObject> actuals) {

		assert (actuals.size() == formals.size()) : "unmatched formal-to-actual mapping!";

		for (int i = 0; i < actuals.size(); i++) {
			StackObject actual = actuals.get(i);
			if (actual instanceof PrimitiveElem) {
				continue;
			} else {
				ParamElem formal = formals.get(i);
				fmlsToActls.put(formal, actual);

			}
		}
	}

	// initialize the return value and lhs mapping
	public void initReturnToLHS(RetElem ret, StackObject lhs) {
		hasRet = true;
		retToRecv.put(ret, lhs);
		// memLocInstnCache.put(ret,
		// new MemLocInstnSet(lhs, ConstraintManager.genTrue()));
	}

	// a wrapper method for memory location instantiation
	public MemLocInstnSet instnMemLoc(AbsMemLoc loc, AbsHeap callerHeap,
			ProgPoint point) {
		MemLocInstnSet ret = null;
		ret = instnMemLocNoCache(loc, callerHeap, point);
		assert (ret != null) : "null instn!";
		return ret;
	}

	protected MemLocInstnSet instnMemLocNoCache(AbsMemLoc loc,
			AbsHeap callerHeap, ProgPoint point) {
		MemLocInstnSet ret = null;

		if (loc instanceof ParamElem) {
			assert (fmlsToActls.containsKey(loc)) : loc
					+ " should have been instantiated!";
			ret = new MemLocInstnSet(fmlsToActls.get(loc), CstManager.genTrue());
		} else if (loc instanceof LocalVarElem
				|| loc instanceof StaticFieldElem) {
			ret = new MemLocInstnSet(loc, CstManager.genTrue());
		} else if (loc instanceof RetElem) {
			if (hasRet) {
				assert retToRecv.containsKey(loc) : "ret should have been createad!";
				ret = new MemLocInstnSet(retToRecv.get(loc),
						CstManager.genTrue());
			} else {
				assert (ret == null) : "return value should"
						+ " not be instantiated if there is no lhs!";
				return null;
			}
		} else if (loc instanceof AllocElem) {
			AllocElem alc = (AllocElem) loc;
			if (SummariesEnv.v().allocDepth == 0
					|| alc.ctxtLength() < SummariesEnv.v().allocDepth) {
				if (alc.contains(point)) {
					ret = new MemLocInstnSet(alc, CstManager.genTrue());
				} else {
					if (SummariesEnv.v().dynAlloc) {
						// using dynamic allocation
						if (!SummariesEnv.v().getLibMeths()
								.contains(point.getMethod())) {
							// caller is NOT in java library
							Context ctx = Env.getContext(point,
									alc.getContext());
							AllocElem alc1 = Env.getAllocElem(alc.getSite(),
									alc.getType(), ctx);
							ret = new MemLocInstnSet(alc1, CstManager.genTrue());
						} else {
							ret = new MemLocInstnSet(alc, CstManager.genTrue());
						}
					} else {
						Context ctx = Env.getContext(point, alc.getContext());
						AllocElem alc1 = Env.getAllocElem(alc.getSite(),
								alc.getType(), ctx);
						ret = new MemLocInstnSet(alc1, CstManager.genTrue());
					}
				}
			} else if (alc.ctxtLength() == SummariesEnv.v().allocDepth) {
				ret = new MemLocInstnSet(alc, CstManager.genTrue());
			} else {
				assert false : "wrong!";
			}

		} else if (loc instanceof AccessPathElem) {
			// instantiation for smashed access path
			AbsMemLoc inner = ((AccessPathElem) loc).getInner();
			FieldElem outer = ((AccessPathElem) loc).getOuter();

			MemLocInstnSet instnLocSet = instnMemLocNoCache(inner, callerHeap,
					point);
			ret = callerHeap.instnLookup(instnLocSet, outer);

			// a work-list algorithm for transitive closure
			Set<AbsMemLoc> visited = new HashSet<AbsMemLoc>();
			// the candidates as instantiated locations
			LinkedHashSet<Pair<AbsMemLoc, BoolExpr>> wl = new LinkedHashSet<Pair<AbsMemLoc, BoolExpr>>();
			// the locations that can really be instantiated into
			MemLocInstnSet targets = new MemLocInstnSet();
			// initialized the work list
			AccessPathElem path = ((AccessPathElem) loc);
			if (path.isSmashed()) {
				for (AbsMemLoc loc1 : ret.keySet()) {
					BoolExpr expr1 = ret.get(loc1);
					wl.add(new Pair<AbsMemLoc, BoolExpr>(loc1, expr1));
				}
			}
			Set<FieldElem> smashedFields = path.getSmashedFields();
			Set<FieldElem> endingFields = path.getEndingFields();
			// the work list algorithm
			while (!wl.isEmpty()) {
				Pair<AbsMemLoc, BoolExpr> elem = wl.iterator().next();
				wl.remove(elem);
				AbsMemLoc canddt = elem.val0;
				BoolExpr cst = elem.val1;
				// mark as visited
				visited.add(canddt);
				// worker is a candidate
				MemLocInstnSet worker = new MemLocInstnSet(canddt, cst);

				for (FieldElem f1 : canddt.getFields()) {
					// ignore the in-compatible fields
					if (!smashedFields.contains(f1)) {
						continue;
					}
					// if ending fields match, then this is one target
					if (endingFields.contains(f1)) {
						MemLocInstnSet next = callerHeap
								.instnLookup(worker, f1);
						targets.addAll(next);
					}
					// if f1 is smashed, then add this into wl
					if (smashedFields.contains(f1)) {
						MemLocInstnSet next = callerHeap
								.instnLookup(worker, f1);
						for (AbsMemLoc loc2 : next.keySet()) {
							if (!visited.contains(loc2)) {
								BoolExpr cst2 = next.get(loc2);
								wl.add(new Pair<AbsMemLoc, BoolExpr>(loc2, cst2));
							}
						}
					}
				}
			}
			// then conjoin with the previous result
			ret.addAll(targets);
		} else {
			assert false : "wried things happen! Unknow type.";
		}

		return ret;
	}
}
