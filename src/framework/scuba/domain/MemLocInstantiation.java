package framework.scuba.domain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import chord.util.tuple.object.Pair;
import framework.scuba.helper.ConstraintManager;

public class MemLocInstantiation {

	// this maps from the abstract memory locations in the heap of the callee to
	// the abstract memory locations in the heap of the caller
	// callee locations --> caller locations
	protected Map<AbstractMemLoc, InstantiatedLocSet> instnMemLocMapping;

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

	public MemLocInstantiation(jq_Method caller, Quad callsite, jq_Method callee) {
		instnMemLocMapping = new HashMap<AbstractMemLoc, InstantiatedLocSet>();
		this.caller = caller;
		this.callsite = callsite;
		this.callee = callee;
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
			if (actual instanceof ConstantElem) {
				// we just ignore the primitives
				continue;
			} else {
				ParamElem formal = formals.get(i);
				instnMemLocMapping.put(formal, new InstantiatedLocSet(actual,
						ConstraintManager.genTrue()));
			}
		}
	}

	public void initReturnToLHS(RetElem ret, StackObject rhs) {
		hasRet = true;
		instnMemLocMapping.put(ret, new InstantiatedLocSet(rhs,
				ConstraintManager.genTrue()));
	}

	// this method implements the inference rules in figure 11 of the paper
	// loc is the location in the callee's heap
	public InstantiatedLocSet instantiate(AbstractMemLoc loc,
			AbstractHeap callerHeap, ProgramPoint point) {

		InstantiatedLocSet ret = instnMemLocMapping.get(loc);

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
			ret = new InstantiatedLocSet(loc, ConstraintManager.genTrue());
			// put into the map
			instnMemLocMapping.put(loc, ret);
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
			if (((AllocElem) loc).contains(point)) {
				AllocElem allocElem = callerHeap
						.getAllocElem(((AllocElem) loc));
				ret = new InstantiatedLocSet(allocElem,
						ConstraintManager.genTrue());
			} else {
				AllocElem allocElem = callerHeap.getAllocElem(
						((AllocElem) loc), point);
				ret = new InstantiatedLocSet(allocElem,
						ConstraintManager.genTrue());
			}
			// put into the map
			instnMemLocMapping.put(loc, ret);
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
			InstantiatedLocSet instnLocSet = instantiate(base, callerHeap,
					point);
			ret = callerHeap.instnLookup(instnLocSet, field);
			// put into the map
			instnMemLocMapping.put(loc, ret);

		} else {
			assert false : "wried things happen! Unknow type.";
		}

		return ret;
	}
}
