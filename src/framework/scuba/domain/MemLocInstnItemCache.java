package framework.scuba.domain;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class MemLocInstnItemCache {

	protected final MemLocInstnItem parent;
	// this map caches the location instantiation results for some memory
	// location instantiation item (per call site in a caller, per callee)
	protected final Map<AbsMemLoc, MemLocInstnSet> cache;

	public MemLocInstnItemCache(MemLocInstnItem parent) {
		this.parent = parent;
		cache = new HashMap<AbsMemLoc, MemLocInstnSet>();
	}

	public MemLocInstnSet put(AbsMemLoc loc, MemLocInstnSet set) {
		return cache.put(loc, set);
	}

	public MemLocInstnSet get(AbsMemLoc loc) {
		return cache.get(loc);
	}

	public boolean isEmpty() {
		return cache.isEmpty();
	}

	public int size() {
		return cache.size();
	}

	public int instnLocSize() {
		int ret = 0;
		for (Iterator<Map.Entry<AbsMemLoc, MemLocInstnSet>> it = cache
				.entrySet().iterator(); it.hasNext();) {
			ret += it.next().getValue().size();
		}
		return ret;
	}

	public MemLocInstnSet remove(AbsMemLoc loc) {
		return cache.remove(loc);
	}

	public void clear() {
		cache.clear();
	}

	public Set<AbsMemLoc> keySet() {
		return cache.keySet();
	}

	public boolean containsKey(AbsMemLoc loc) {
		return cache.containsKey(loc);
	}

}
