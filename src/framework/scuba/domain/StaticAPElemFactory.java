package framework.scuba.domain;

import java.util.HashMap;
import java.util.Map;

import joeq.Class.jq_Type;
import chord.util.tuple.object.Pair;
import framework.scuba.helper.AccessPathHelper;

public class StaticAPElemFactory {

	public final static Map<Pair<AbsMemLoc, FieldElem>, StaticAccessPathElem> staticAPElemFactory = new HashMap<Pair<AbsMemLoc, FieldElem>, StaticAccessPathElem>();

	public final static Map<Pair<AbsMemLoc, FieldElem>, Integer> staticAPElemToId = new HashMap<Pair<AbsMemLoc, FieldElem>, Integer>();

	// numbers of LocalAccessPathElem start from 1
	public static int maxNum;

	public static StaticAccessPathElem getStaticAccessPathElem(AbsMemLoc inner,
			FieldElem outer) {
		Pair<AbsMemLoc, FieldElem> pair = new Pair<AbsMemLoc, FieldElem>(inner,
				outer);
		StaticAccessPathElem ret = staticAPElemFactory.get(pair);
		if (ret == null) {
			jq_Type type = AccessPathHelper.rslvAPType(inner, outer);
			ret = new StaticAccessPathElem(inner, outer, type, ++maxNum);
			updateMap(inner, outer, maxNum, ret);
		}
		return ret;
	}

	private static void updateMap(AbsMemLoc inner, FieldElem outer, int number,
			StaticAccessPathElem localAPElem) {
		Pair<AbsMemLoc, FieldElem> pair = new Pair<AbsMemLoc, FieldElem>(inner,
				outer);
		staticAPElemFactory.put(pair, localAPElem);
		staticAPElemToId.put(pair, number);
	}

	public static void clear() {
		staticAPElemFactory.clear();
		staticAPElemToId.clear();
		maxNum = 0;
	}
}
