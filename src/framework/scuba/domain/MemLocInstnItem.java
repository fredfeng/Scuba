package framework.scuba.domain;

import java.util.List;
import java.util.Map;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;

import com.microsoft.z3.BoolExpr;

import framework.scuba.helper.ConstraintManager;

public class MemLocInstnItem {

	// this maps from the abstract memory locations in the heap of the callee to
	// the abstract memory locations in the heap of the caller
	// callee locations --> caller locations
	protected MemLocInstnItemCache cache;
	// protected Map<AbstractMemLoc, MemLocInstnSet> instnMemLocMapping;

	// the caller that this instantiation belongs to
	protected jq_Method caller;

	// the call site that this instantiation is for
	protected Quad callsite;

	// the callee that this instantiation is for
	protected jq_Method callee;

	// whether use cache for instantiating AccessPath
	protected boolean useCache = false;

	// hasRet = true: there is a location mapped in the caller's heap
	protected boolean hasRet = false;

	public MemLocInstnItem(jq_Method caller, Quad callsite, jq_Method callee) {
		this.caller = caller;
		this.callsite = callsite;
		this.callee = callee;
		cache = new MemLocInstnItemCache();
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

	public int size() {
		return cache.size();
	}

	public void print() {
		System.out
				.println("\n----------------Mem Loc Instantion-----------------");
		System.out.println("Caller: " + caller);
		System.out.println("Callee: " + callee);
		System.out.println("Call site: " + callsite);
		for (AbstractMemLoc loc : cache.keySet()) {
			System.out.println("*****************************");
			System.out.println("Location: " + loc + " is instantiated to:");
			Map<AbstractMemLoc, BoolExpr> ret = cache.get(loc).getInstnLocSet();
			for (AbstractMemLoc loc1 : ret.keySet()) {
				System.out.println("Location: " + loc1);
				System.out.println("Constraint: " + ret.get(loc1));
			}
		}
		System.out.println("*****************************");
		System.out.println("\n-----------------------------------------");

	}

	public void enableCache() {
		useCache = true;
	}

	public void disableCache() {
		useCache = false;
	}

	public boolean isUsingCache() {
		return useCache;
	}

	// initialized the formal-to-actual mapping
	public void initFormalToActualMapping(List<ParamElem> formals,
			List<StackObject> actuals) {

		assert (actuals.size() == formals.size()) : "unmatched formal-to-actual mapping!";

		for (int i = 0; i < actuals.size(); i++) {
			StackObject actual = actuals.get(i);
			if (actual instanceof PrimitiveElem) {
				// we just ignore the primitives
				continue;
			} else {
				ParamElem formal = formals.get(i);
				cache.put(formal,
						new MemLocInstnSet(actual, ConstraintManager.genTrue()));
			}
		}
	}

	// initialize the return value and lhs mapping
	public void initReturnToLHS(RetElem ret, StackObject lhs) {
		hasRet = true;
		cache.put(ret, new MemLocInstnSet(lhs, ConstraintManager.genTrue()));
	}

	// this method implements the inference rules in figure 11 of the paper
	// loc is the location in the callee's heap
	public MemLocInstnSet instnMemLoc(AbstractMemLoc loc,
			AbstractHeap callerHeap, ProgramPoint point) {

		MemLocInstnSet ret = cache.get(loc);

		if (loc instanceof ParamElem) {
			assert (ret != null) : "parameters should have been instantiated"
					+ " when the first time init the instantiation";
		}

		if (loc instanceof RetElem) {
			if (hasRet) {
				assert (ret != null) : "return value should have been instantiated"
						+ " when the first time init the instantiation!";
			} else {
				assert (ret == null) : "if there is no LHS in the call site, "
						+ "we cannot instantiate the return value";
			}
		}

		if (loc instanceof LocalVarElem || loc instanceof ParamElem
				|| loc instanceof StaticElem) {
			// this is my little cute cache
			if (ret != null) {
				return ret;
			}
			// not hitting cache
			ret = new MemLocInstnSet(loc, ConstraintManager.genTrue());
			// put into the map
			cache.put(loc, ret);
		} else if (loc instanceof RetElem) {
			if (hasRet) {
				assert (ret != null) : "return value should be mapped"
						+ " the first time init the instantiation";
				return ret;
			} else {
				assert (ret == null) : "return value should"
						+ " not be instantiated if there is no LHS!";
				return null;
			}
		} else if (loc instanceof AllocElem) {
			// we also instantiated allocElem only once!
			// wow! I guess it will save a LOT of time by doing this
			if (ret != null) {
				return ret;
			}
			// not hitting the cache
			if (SummariesEnv.v().allocDepth == 0
					|| ((AllocElem) loc).length() < SummariesEnv.v().allocDepth) {

				if (((AllocElem) loc).contains(point)) {
					AllocElem allocElem = callerHeap
							.getAllocElem(((AllocElem) loc));
					ret = new MemLocInstnSet(allocElem,
							ConstraintManager.genTrue());
				} else {
					AllocElem allocElem = callerHeap.getAllocElem(
							((AllocElem) loc), point);
					ret = new MemLocInstnSet(allocElem,
							ConstraintManager.genTrue());
				}
			} else if (((AllocElem) loc).length() == SummariesEnv.v().allocDepth) {

				AllocElem allocElem = callerHeap.getAllocElem((AllocElem) loc);
				ret = new MemLocInstnSet(allocElem, ConstraintManager.genTrue());
			} else {
				assert false : "wrong!";
			}
			// put into the map
			cache.put(loc, ret);
		} else if (loc instanceof AccessPath) {
			if (useCache) {
				// hitting cache TODO
				if (ret != null) {
					return ret;
				}
			}
			// currently we do not do cache here
			AbstractMemLoc base = ((AccessPath) loc).getBase();
			FieldElem field = ((AccessPath) loc).getField();
			MemLocInstnSet instnLocSet = instnMemLoc(base, callerHeap, point);
			ret = callerHeap.instnLookup(instnLocSet, field);
			// put into the map
			cache.put(loc, ret);

		} else {
			assert false : "wried things happen! Unknow type.";
		}

		return ret;
	}
}
