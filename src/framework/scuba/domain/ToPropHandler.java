package framework.scuba.domain;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.util.tuple.object.Pair;

public class ToPropHandler {

	protected Summary sum;

	protected final AbsHeapHandler ahHandler;

	public ToPropHandler(AbsHeapHandler ahh) {
		ahHandler = ahh;
	}

	public void setSummary(Summary sum) {
		this.sum = sum;
	}

	public void fillToPropSet() {
		Set<AllocElem> wl = new HashSet<AllocElem>();
		Set<AbsMemLoc> locals = new HashSet<AbsMemLoc>();

		if (SummariesEnv.v().propParams) {
			// this is a post-processing which create pseudo-locals for
			// parameters which we want to propagate
			for (ParamElem param : sum.formals) {
				Register r = param.getRegister();
				// create a pseudo-local element
				LocalVarElem pLocal = Env.getLocalVarElem(r, param.meth,
						param.clazz, param.type);
				// do an extra assign for local = parameter
				// put this pseudo-local element into the heap
				ahHandler.handleAssignStmt(param.clazz, param.meth, r,
						param.type, AbsHeap.VariableType.LOCAL_VARIABLE, r,
						param.type, AbsHeap.VariableType.PARAMETER);
				// instead, we prop this pseudo-local element
				locals.add(pLocal);
			}
		}
		// add all locations that are guaranteed to be propagated to the caller
		for (Iterator<Map.Entry<Pair<AbsMemLoc, FieldElem>, P2Set>> it = sum
				.getAbsHeap().locToP2Set.entrySet().iterator(); it.hasNext();) {
			Entry<Pair<AbsMemLoc, FieldElem>, P2Set> entry = it.next();
			Pair<AbsMemLoc, FieldElem> pair = entry.getKey();
			AbsMemLoc loc = entry.getKey().val0;

			// this is the normal propagation part
			if (loc instanceof AccessPathElem || loc instanceof ParamElem
					|| loc instanceof StaticFieldElem || loc instanceof RetElem) {
				// add all potential allocs into wl
				P2Set p2set = sum.getAbsHeap().locToP2Set.get(pair);
				for (HeapObject hObj : p2set.keySet()) {
					if (hObj instanceof AllocElem) {
						wl.add((AllocElem) hObj);
					}
				}
			} else if (loc instanceof LocalVarElem) {
				Register r = ((LocalVarElem) loc).getRegister();
				if (SummariesEnv.v().toProp(r)) {
					locals.add(loc);
					// add all potential allocs into wl
					P2Set p2set = sum.getAbsHeap().locToP2Set.get(pair);
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
			Env.toProp.addAll(wl);
			for (AllocElem alloc : wl) {
				Set<FieldElem> fields = alloc.getFields();
				for (FieldElem f : fields) {
					P2Set p2set = sum.getAbsHeap().locToP2Set
							.get(new Pair<AbsMemLoc, FieldElem>(alloc, f));
					for (HeapObject hObj : p2set.keySet()) {
						if (hObj instanceof AllocElem
								&& !Env.toProp.contains(hObj)) {
							set.add((AllocElem) hObj);
						}
					}
				}
			}
			wl.clear();
			wl.addAll(set);
			set.clear();
		}
		Env.toProp.addAll(locals);
	}
}