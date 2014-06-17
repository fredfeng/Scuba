package framework.scuba.domain;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class MemLocInstnItemCache {

	protected final MemLocInstnItem parent;
	// this map caches the location instantiation results for some memory
	// location instantiation item (per call site in a caller, per callee)
	protected final Map<AbstractMemLoc, MemLocInstnSet> cache;

	public MemLocInstnItemCache(MemLocInstnItem parent) {
		this.parent = parent;
		cache = new HashMap<AbstractMemLoc, MemLocInstnSet>();
	}

	public MemLocInstnSet put(AbstractMemLoc loc, MemLocInstnSet set) {
		return cache.put(loc, set);
	}

	public MemLocInstnSet get(AbstractMemLoc loc) {
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
		for (Iterator<Map.Entry<AbstractMemLoc, MemLocInstnSet>> it = cache
				.entrySet().iterator(); it.hasNext();) {
			ret += it.next().getValue().size();
		}
		return ret;
	}

	public MemLocInstnSet remove(AbstractMemLoc loc) {
		return cache.remove(loc);
	}

	public void clear() {
		cache.clear();
	}

	public Set<AbstractMemLoc> keySet() {
		return cache.keySet();
	}

	public boolean containsKey(AbstractMemLoc loc) {
		return cache.containsKey(loc);
	}

}
