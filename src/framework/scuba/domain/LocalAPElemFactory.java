package framework.scuba.domain;

import java.util.HashMap;
import java.util.Map;

import joeq.Class.jq_Type;
import chord.util.tuple.object.Pair;
import framework.scuba.helper.AccessPathHelper;

public class LocalAPElemFactory {

	public final static Map<Pair<AbsMemLoc, FieldElem>, LocalAccessPathElem> localAPElemFactory = new HashMap<Pair<AbsMemLoc, FieldElem>, LocalAccessPathElem>();

	public final static Map<Pair<AbsMemLoc, FieldElem>, Integer> localAPElemToId = new HashMap<Pair<AbsMemLoc, FieldElem>, Integer>();

	// numbers of LocalAccessPathElem start from 1
	public static int maxNum;

	public static LocalAccessPathElem getLocalAccessPathElem(AbsMemLoc inner,
			FieldElem outer) {
		Pair<AbsMemLoc, FieldElem> pair = new Pair<AbsMemLoc, FieldElem>(inner,
				outer);
		LocalAccessPathElem ret = localAPElemFactory.get(pair);
		if (ret == null) {
			jq_Type type = AccessPathHelper.rslvAPType(inner, outer);
			ret = new LocalAccessPathElem(inner, outer, type, ++maxNum);
			updateMap(inner, outer, maxNum, ret);
		}
		return ret;
	}

	private static void updateMap(AbsMemLoc inner, FieldElem outer, int number,
			LocalAccessPathElem localAPElem) {
		Pair<AbsMemLoc, FieldElem> pair = new Pair<AbsMemLoc, FieldElem>(inner,
				outer);
		localAPElemFactory.put(pair, localAPElem);
		localAPElemToId.put(pair, number);
	}

	public static void clear() {
		localAPElemFactory.clear();
		localAPElemToId.clear();
		maxNum = 0;
	}

}