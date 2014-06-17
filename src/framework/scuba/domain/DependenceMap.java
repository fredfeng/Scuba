package framework.scuba.domain;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import chord.util.tuple.object.Pair;

public class DependenceMap {

	// the summary this dependence map belongs to
	final protected Summary summary;

	// Map<K, Set<Pair<Item, V>>>
	// K: the location in the caller's heap
	// V: the location in the callee's heap whose instantiation will be
	// influenced by the change of the P2Set of K
	// Item: the instantiation location result for K is cached in Item
	final protected Map<AbstractMemLoc, Map<MemLocInstnItem, Set<AccessPath>>> map;

	public DependenceMap(Summary summary) {
		this.summary = summary;
		map = new HashMap<AbstractMemLoc, Map<MemLocInstnItem, Set<AccessPath>>>();
	}

	public Summary getSum() {
		return summary;
	}

	public Map<MemLocInstnItem, Set<AccessPath>> add(AbstractMemLoc loc,
			Pair<MemLocInstnItem, Set<AccessPath>> pair) {
		Map<MemLocInstnItem, Set<AccessPath>> ret = map.get(loc);
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

	public Map<MemLocInstnItem, Set<AccessPath>> put(AbstractMemLoc loc,
			Map<MemLocInstnItem, Set<AccessPath>> set) {
		return map.put(loc, set);
	}

	public Map<MemLocInstnItem, Set<AccessPath>> get(AbstractMemLoc loc) {
		return map.get(loc);
	}

	public boolean isEmpty() {
		return map.isEmpty();
	}

	public int size() {
		return map.size();
	}

	public boolean containsKey(AbstractMemLoc loc) {
		return map.containsKey(loc);
	}

	public Set<AbstractMemLoc> keySet() {
		return map.keySet();
	}

	public Map<MemLocInstnItem, Set<AccessPath>> remove(AbstractMemLoc loc) {
		return map.remove(loc);
	}

}
