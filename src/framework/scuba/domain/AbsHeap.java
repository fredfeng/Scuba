package framework.scuba.domain;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Method;
import chord.util.tuple.object.Pair;

import com.microsoft.z3.BoolExpr;

import framework.scuba.helper.DefaultTargetHelper;
import framework.scuba.helper.P2SetHelper;

public class AbsHeap {

	protected final Summary summary;

	// all locations in the heap including both keys and values
	public final Set<AbsMemLoc> heap;

	public final Map<Pair<AbsMemLoc, FieldElem>, P2Set> locToP2Set;

	// whether the heap and summary has been changed: (heap, summary)
	private Pair<Boolean, Boolean> isChanged = new Pair<Boolean, Boolean>(
			false, false);

	public static enum VariableType {
		PARAMETER, LOCAL_VARIABLE;
	}

	public AbsHeap(Summary summary) {
		this.summary = summary;
		this.heap = new HashSet<AbsMemLoc>();
		this.locToP2Set = new HashMap<Pair<AbsMemLoc, FieldElem>, P2Set>();
	}

	// ------------ basic operations -------------
	public jq_Method getMethod() {
		return summary.getMethod();
	}

	public Map<Pair<AbsMemLoc, FieldElem>, P2Set> getHeap() {
		return this.locToP2Set;
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

	// ---------- heap operations ------------
	public Set<Pair<AbsMemLoc, FieldElem>> keySet() {
		return locToP2Set.keySet();
	}

	public P2Set get(Pair<AbsMemLoc, FieldElem> pair) {
		return locToP2Set.get(pair);
	}

	public boolean contains(AbsMemLoc loc) {
		return heap.contains(loc);
	}

	public int size() {
		int ret = 0;
		for (Pair<AbsMemLoc, FieldElem> loc : locToP2Set.keySet()) {
			ret += locToP2Set.get(loc).size();
		}
		return ret;
	}

	protected AccessPathElem getDefaultTarget(AbsMemLoc loc, FieldElem field) {
		AccessPathElem ret = null;
		if (SummariesEnv.v().level == SummariesEnv.FieldSmashLevel.REG) {
			if (field instanceof IndexFieldElem) {
				if (loc instanceof StaticFieldElem) {
					ret = Env.getStaticAccessPathElem((StaticFieldElem) loc,
							field);
				} else if (loc instanceof ParamElem) {
					ret = Env.getLocalAccessPathElem((ParamElem) loc, field);
				} else if (loc instanceof AccessPathElem) {
					AccessPathElem path = (AccessPathElem) loc;
					FieldElem f = path.getOuter();
					if (f instanceof IndexFieldElem) {
						path.addSmashedField(f);
						return path;
					}
					if (loc instanceof StaticAccessPathElem) {
						ret = Env.getStaticAccessPathElem(
								(StaticAccessPathElem) loc, field);
					} else if (loc instanceof LocalAccessPathElem) {
						ret = Env.getLocalAccessPathElem(
								(LocalAccessPathElem) loc, field);
					} else {
						assert false : "only two kinds of access path!";
					}
				} else {
					assert false : "only three kinds of things can have default targets!";
				}
			} else {
				if (loc instanceof StaticFieldElem) {
					ret = Env.getStaticAccessPathElem((StaticFieldElem) loc,
							field);
				} else if (loc instanceof ParamElem) {
					ret = Env.getLocalAccessPathElem((ParamElem) loc, field);
				} else if (loc instanceof AccessPathElem) {
					AccessPathElem path = ((AccessPathElem) loc)
							.getPrefix(field);
					if (path != null) {
						ret = path;
						Set<FieldElem> smashedFields = path
								.getPreSmashedFields(field);
						ret.addSmashedFields(smashedFields);
					} else {
						if (loc instanceof StaticAccessPathElem) {
							ret = Env.getStaticAccessPathElem(
									(StaticAccessPathElem) loc, field);
						} else if (loc instanceof LocalAccessPathElem) {
							ret = Env.getLocalAccessPathElem(
									(LocalAccessPathElem) loc, field);
						} else {
							assert false : "only two kinds of access path!";
						}
					}
				} else {
					assert false : "only three kind of things.";
				}
			}
		} else {
			assert false : "unsupported smashing strategy.";
		}

		assert (ret != null) : "you can NOT get the default target for a non-arg derived mem loc!";
		return ret;
	}

	// lookup for p2set which is described in definition 7 of the paper
	public P2Set lookup(AbsMemLoc loc, FieldElem field) {
		// create a pair wrapper for lookup
		Pair<AbsMemLoc, FieldElem> pair = new Pair<AbsMemLoc, FieldElem>(loc,
				field);
		if (DefaultTargetHelper.hasDefault(loc)) {
			// get the default target given the memory location and the field
			AccessPathElem defaultTarget = getDefaultTarget(loc, field);

			// always find the default p2set of (loc, field)
			P2Set defaultP2Set = new P2Set(defaultTarget);
			// return the p2set always including the default p2set
			if (locToP2Set.containsKey(pair)) {
				return P2SetHelper.join(locToP2Set.get(pair), defaultP2Set);
			} else {
				return defaultP2Set;
			}
		} else {
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
		}
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

}
