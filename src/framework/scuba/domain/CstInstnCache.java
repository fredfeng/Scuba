package framework.scuba.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import chord.util.tuple.object.Pair;

import com.microsoft.z3.BoolExpr;

public class CstInstnCache {
	// this my little cute cache for constraint instantiation
	protected final Map<MemLocInstnItem, Map<BoolExpr, BoolExpr>> cache;

	public CstInstnCache() {
		cache = new HashMap<MemLocInstnItem, Map<BoolExpr, BoolExpr>>();
	}

	public Map<BoolExpr, BoolExpr> put(MemLocInstnItem item,
			Map<BoolExpr, BoolExpr> map) {
		return cache.put(item, map);
	}

	public Map<BoolExpr, BoolExpr> add(MemLocInstnItem item,
			Pair<BoolExpr, BoolExpr> pair) {
		Map<BoolExpr, BoolExpr> ret = cache.get(item);
		if (ret == null) {
			ret = new HashMap<BoolExpr, BoolExpr>();
			cache.put(item, ret);
		}
		ret.put(pair.val0, pair.val1);
		return ret;
	}

	public Map<BoolExpr, BoolExpr> get(MemLocInstnItem item) {
		return cache.get(item);
	}

	public BoolExpr getBoolExpr(MemLocInstnItem item, BoolExpr expr) {
		Map<BoolExpr, BoolExpr> map = cache.get(item);
		if (map == null) {
			return null;
		}
		return map.get(expr);
	}

	public int size() {
		return cache.size();
	}

	public boolean isEmpty() {
		return cache.isEmpty();
	}

	public Map<BoolExpr, BoolExpr> remove(MemLocInstnItem item) {
		return cache.remove(item);
	}

	public BoolExpr removeExpr(MemLocInstnItem item, BoolExpr expr) {
		Map<BoolExpr, BoolExpr> map = cache.get(item);
		if (map == null) {
			return null;
		}
		return map.remove(expr);
	}

	public Set<MemLocInstnItem> keySet() {
		return cache.keySet();
	}

}
