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
	final protected Map<AbstractMemLoc, Set<Pair<MemLocInstnItem, AccessPath>>> map;

	public DependenceMap(Summary summary) {
		this.summary = summary;
		map = new HashMap<AbstractMemLoc, Set<Pair<MemLocInstnItem, AccessPath>>>();
	}

	public Summary getSum() {
		return summary;
	}

	public Set<Pair<MemLocInstnItem, AccessPath>> add(AbstractMemLoc loc,
			Pair<MemLocInstnItem, AccessPath> pair) {
		Set<Pair<MemLocInstnItem, AccessPath>> ret = map.get(loc);
		if (ret == null) {
			ret = new HashSet<Pair<MemLocInstnItem, AccessPath>>();
			ret.add(pair);
		}
		return ret;
	}

	public Set<Pair<MemLocInstnItem, AccessPath>> put(AbstractMemLoc loc,
			Set<Pair<MemLocInstnItem, AccessPath>> set) {
		return map.put(loc, set);
	}

	public Set<Pair<MemLocInstnItem, AccessPath>> get(AbstractMemLoc loc) {
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

	public Set<Pair<MemLocInstnItem, AccessPath>> remove(AbstractMemLoc loc) {
		return map.remove(loc);
	}

}
