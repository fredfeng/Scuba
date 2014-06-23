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
	final protected Map<AbsMemLoc, Map<MemLocInstnItem, Set<AccessPath>>> depMap;

	public MemLocInstnCacheDepMap(Summary summary) {
		this.summary = summary;
		depMap = new HashMap<AbsMemLoc, Map<MemLocInstnItem, Set<AccessPath>>>();
	}

	public Summary getSum() {
		return summary;
	}

	public Map<MemLocInstnItem, Set<AccessPath>> add(AbsMemLoc loc,
			Pair<MemLocInstnItem, Set<AccessPath>> pair) {
		Map<MemLocInstnItem, Set<AccessPath>> ret = depMap.get(loc);
		if (ret == null) {
			ret = new HashMap<MemLocInstnItem, Set<AccessPath>>();
			ret.put(pair.val0, pair.val1);
		}
		Set<AccessPath> aps = ret.get(pair.val0);
		if (aps == null) {
			aps = new HashSet<AccessPath>();
			ret.put(pair.val0, aps);
		}
		aps.addAll(pair.val1);
		return ret;
	}

	public Map<MemLocInstnItem, Set<AccessPath>> put(AbsMemLoc loc,
			Map<MemLocInstnItem, Set<AccessPath>> toAdd) {
		return depMap.put(loc, toAdd);
	}

	public Map<MemLocInstnItem, Set<AccessPath>> get(AbsMemLoc loc) {
		return depMap.get(loc);
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

	public Set<AbsMemLoc> keySet() {
		return depMap.keySet();
	}

	public Map<MemLocInstnItem, Set<AccessPath>> remove(
			Pair<AbsMemLoc, FieldElem> pair) {
		return depMap.remove(pair);
	}

}
