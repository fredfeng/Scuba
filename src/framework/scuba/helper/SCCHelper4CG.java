package framework.scuba.helper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import joeq.Class.jq_Method;
import chord.analyses.alias.ICICG;

//TODO we should combine SCCHelper and SCCHelper4CG in the future.
//Currently it's ugly.
public class SCCHelper4CG {
	protected final List<Set<jq_Method>> componentList = new ArrayList<Set<jq_Method>>();

	protected int index = 0;

	protected Map<Object, Integer> indexForNode, lowlinkForNode;

	protected Stack<jq_Method> s;

	protected ICICG g;

	/**
	 * @param g: a Callgraph for which we want to compute the strongly connected
	 * components.
	 */
	public SCCHelper4CG(ICICG g, Set roots) {
		this.g = g;
		s = new Stack<jq_Method>();
		Set<Object> heads = roots;

		indexForNode = new HashMap<Object, Integer>();
		lowlinkForNode = new HashMap<Object, Integer>();

		for (Iterator<Object> headsIt = heads.iterator(); headsIt.hasNext();) {
			Object head = headsIt.next();
			if (!indexForNode.containsKey(head)) {
				recurse((jq_Method) head);
			}
		}

		// free memory
		indexForNode = null;
		lowlinkForNode = null;
		s = null;
		g = null;
	}

	protected void recurse(jq_Method v) {
		indexForNode.put(v, index);
		lowlinkForNode.put(v, index);
		index++;
		s.push(v);

		for (jq_Method succ : g.getSuccs(v)) {
			if (!indexForNode.containsKey(succ)) {
				recurse(succ);
				lowlinkForNode.put(
						v,
						Math.min(lowlinkForNode.get(v),
								lowlinkForNode.get(succ)));
			} else if (s.contains(succ)) {
				lowlinkForNode
						.put(v,
								Math.min(lowlinkForNode.get(v),
										indexForNode.get(succ)));
			}
		}
		if (lowlinkForNode.get(v).intValue() == indexForNode.get(v).intValue()) {
			Set<jq_Method> scc = new HashSet<jq_Method>();
			jq_Method v2;
			do {
				v2 = s.pop();
				scc.add(v2);
			} while (v != v2);
			componentList.add(scc);
		}
	}

	/**
	 * @return the list of the strongly-connected components
	 */
	public List<Set<jq_Method>> getComponents() {
		return componentList;
	}
}
