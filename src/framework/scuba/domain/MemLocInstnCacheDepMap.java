package framework.scuba.domain;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import chord.util.tuple.object.Pair;

public class MemLocInstnCacheDepMap extends DependenceMap {

	// the summary this dependence map belongs to
	final protected Summary summary;

	// Map<K, Set<Pair<Item, V>>>
	// K: the location in the caller's heap
	// V: the location in the callee's heap whose instantiation will be
	// influenced by the change of the P2Set of K
	// Item: the instantiation location result for K is cached in Item
	final protected Map<Pair<AbsMemLoc, FieldElem>, Map<MemLocInstnItem, Set<AccessPath>>> depMap;

	public MemLocInstnCacheDepMap(Summary summary) {
		this.summary = summary;
		depMap = new HashMap<Pair<AbsMemLoc, FieldElem>, Map<MemLocInstnItem, Set<AccessPath>>>();
	}

	public Summary getSum() {
		return summary;
	}

	public Map<MemLocInstnItem, Set<AccessPath>> add(
			Pair<AbsMemLoc, FieldElem> pair1,
			Pair<MemLocInstnItem, Set<AccessPath>> pair2) {
		Map<MemLocInstnItem, Set<AccessPath>> ret = depMap.get(pair1);
		if (ret == null) {
			ret = new HashMap<MemLocInstnItem, Set<AccessPath>>();
			ret.put(pair2.val0, pair2.val1);
		}
		Set<AccessPath> aps = ret.get(pair2.val0);
		if (aps == null) {
			aps = new HashSet<AccessPath>();
			ret.put(pair2.val0, aps);
		}
		aps.addAll(pair2.val1);
		return ret;
	}

	public Map<MemLocInstnItem, Set<AccessPath>> put(
			Pair<AbsMemLoc, FieldElem> pair,
			Map<MemLocInstnItem, Set<AccessPath>> toAdd) {
		return depMap.put(pair, toAdd);
	}

	public Map<MemLocInstnItem, Set<AccessPath>> get(
			Pair<AbsMemLoc, FieldElem> pair) {
		return depMap.get(pair);
	}

	public boolean isEmpty() {
		return depMap.isEmpty();
	}

	public int size() {
		return depMap.size();
	}

	public boolean containsKey(AbsMemLoc loc) {
		return depMap.containsKey(loc);
	}

	public Set<Pair<AbsMemLoc, FieldElem>> keySet() {
		return depMap.keySet();
	}

	public Map<MemLocInstnItem, Set<AccessPath>> remove(
			Pair<AbsMemLoc, FieldElem> pair) {
		return depMap.remove(pair);
	}

}
