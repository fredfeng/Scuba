package framework.scuba.domain;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import chord.util.tuple.object.Pair;

public class MemLocInstn4Method {

	final protected Summary summary;

	final protected Map<Pair<Quad, jq_Method>, MemLocInstnItem> memLocInstnResult;

	public MemLocInstn4Method(Summary summary) {
		this.summary = summary;
		memLocInstnResult = new HashMap<Pair<Quad, jq_Method>, MemLocInstnItem>();
	}

	public Summary getSum() {
		return summary;
	}

	public MemLocInstnItem get(Pair<Quad, jq_Method> pair) {
		return memLocInstnResult.get(pair);
	}

	public MemLocInstnItem put(Pair<Quad, jq_Method> pair, MemLocInstnItem item) {
		return memLocInstnResult.put(pair, item);
	}

	public boolean constainsKey(Pair<Quad, jq_Method> pair) {
		return memLocInstnResult.containsKey(pair);
	}

	public Set<Pair<Quad, jq_Method>> keySet() {
		return memLocInstnResult.keySet();
	}

	public boolean isEmpty() {
		return memLocInstnResult.isEmpty();
	}

	public int size() {
		return memLocInstnResult.size();
	}

	public int itemSize() {
		int ret = 0;
		for (Iterator<Map.Entry<Pair<Quad, jq_Method>, MemLocInstnItem>> it = memLocInstnResult
				.entrySet().iterator(); it.hasNext();) {
			ret += it.next().getValue().size();
		}
		return ret;
	}
}
