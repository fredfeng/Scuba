package framework.scuba.domain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import framework.scuba.helper.ConstraintManager;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;

public class MemLocInstantiation {

	// this maps from the abstract memory locations in the heap of the callee to
	// the abstract memory locations in the heap of the caller
	protected Map<AbstractMemLoc, InstantiatedLocSet> instantiatedMemLocMapping;

	MemLocInstantiation(Quad callsite, jq_Method caller, jq_Method callee) {
		instantiatedMemLocMapping = new HashMap<AbstractMemLoc, InstantiatedLocSet>();

	}

	// initialized the formal-to-actual mapping
	public void initFormalToActualMapping(List<ParamElem> formals,
			List<StackObject> actuals) {
		assert (actuals.size() == formals.size()) : "unmatched formal-to-actual mapping!";

		for (int i = 0; i < actuals.size(); i++) {
			StackObject actual = actuals.get(i);
			if (actual instanceof ConstantElem) {
				continue;
			} else {
				StackObject formal = formals.get(i);
				instantiatedMemLocMapping.put(formal, new InstantiatedLocSet(
						actual, ConstraintManager.genTrue()));
			}
		}
	}

	public void instantiate() {

	}

}
