package framework.scuba.helper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import chord.util.tuple.object.Pair;
import framework.scuba.domain.AbsMemLoc;
import framework.scuba.domain.AbstractHeap;
import framework.scuba.domain.FieldElem;
import framework.scuba.domain.P2Set;

public class SCCHelper4Heap {
	protected final List<Set<AbsMemLoc>> componentList = new ArrayList<Set<AbsMemLoc>>();

	protected int index = 0;

	protected Map<Object, Integer> indexForNode, lowlinkForNode;

	protected Stack<AbsMemLoc> s;

	protected AbstractHeap g;

	/**
	 * @param g: Abstract heap for which we want to compute the strongly connected
	 * components.
	 */
	public SCCHelper4Heap(AbstractHeap g, Set roots) {
		this.g = g;
		s = new Stack<AbsMemLoc>();
		Set<Object> heads = roots;

		indexForNode = new HashMap<Object, Integer>();
		lowlinkForNode = new HashMap<Object, Integer>();

		for (Iterator<Object> headsIt = heads.iterator(); headsIt.hasNext();) {
			Object head = headsIt.next();
			if (!indexForNode.containsKey(head)) {
				recurse((AbsMemLoc) head);
			}
		}

		// free memory
		indexForNode = null;
		lowlinkForNode = null;
		s = null;
		g = null;
	}

	protected void recurse(AbsMemLoc v) {
		indexForNode.put(v, index);
		lowlinkForNode.put(v, index);
		index++;
		s.push(v);

		for (AbsMemLoc succ : getSuccs(g, v)) {
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
			Set<AbsMemLoc> scc = new HashSet<AbsMemLoc>();
			AbsMemLoc v2;
			do {
				v2 = s.pop();
				scc.add(v2);
			} while (v != v2);
			componentList.add(scc);
		}
	}
	
	protected Set<AbsMemLoc> getSuccs(AbstractHeap heap, AbsMemLoc v) {
		Set<AbsMemLoc> succ = new HashSet<AbsMemLoc>();

		for(Pair<AbsMemLoc, FieldElem> pair : heap.keySet())
			if(pair.val0.equals(v)) {
				P2Set tgts = heap.locToP2Set.get(pair);
				succ.addAll(tgts.keySet());
			}
		return succ;
	}

	/**
	 * @return the list of the strongly-connected components
	 */
	public List<Set<AbsMemLoc>> getComponents() {
		return componentList;
	}
}
