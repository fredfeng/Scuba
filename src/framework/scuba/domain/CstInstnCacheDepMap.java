package framework.scuba.domain;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import chord.util.tuple.object.Pair;

import com.microsoft.z3.BoolExpr;

public class CstInstnCacheDepMap {

	protected final Map<AccessPath, Map<MemLocInstnItem, Set<BoolExpr>>> depMap;

	public CstInstnCacheDepMap() {
		this.depMap = new HashMap<AccessPath, Map<MemLocInstnItem, Set<BoolExpr>>>();
	}

	public Map<MemLocInstnItem, Set<BoolExpr>> add(AccessPath ap,
			Pair<MemLocInstnItem, BoolExpr> pair) {
		Map<MemLocInstnItem, Set<BoolExpr>> ret = depMap.get(ap);
		if (ret == null) {
			ret = new HashMap<MemLocInstnItem, Set<BoolExpr>>();
			depMap.put(ap, ret);
		}
		Set<BoolExpr> exprs = ret.get(pair.val0);
		if (exprs == null) {
			exprs = new HashSet<BoolExpr>();
			ret.put(pair.val0, exprs);
		}
		exprs.add(pair.val1);

		return ret;
	}

	public Map<MemLocInstnItem, Set<BoolExpr>> put(AccessPath ap,
			Map<MemLocInstnItem, Set<BoolExpr>> toAdd) {
		return depMap.put(ap, toAdd);
	}

	public Map<MemLocInstnItem, Set<BoolExpr>> get(AccessPath ap) {
		return depMap.get(ap);
	}

	public Set<BoolExpr> getExprs(AccessPath ap, MemLocInstnItem item) {
		Set<BoolExpr> ret = new HashSet<BoolExpr>();
		Map<MemLocInstnItem, Set<BoolExpr>> map = depMap.get(ap);
		if (map == null) {
			return ret;
		}
		ret = map.get(item);
		return ret;
	}

	public int size() {
		return depMap.size();
	}

	public boolean isEmpty() {
		return depMap.isEmpty();
	}
}
