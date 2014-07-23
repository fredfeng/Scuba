package framework.scuba.domain;

import java.util.HashMap;
import java.util.Map;

import joeq.Class.jq_Field;

public class StaticFieldElemFactory {

	public final static Map<jq_Field, StaticFieldElem> staticFieldElemFactory = new HashMap<jq_Field, StaticFieldElem>();

	public final static Map<jq_Field, Integer> staticFieldElemToId = new HashMap<jq_Field, Integer>();

	// numbers of StaticFieldElem start from 1
	public static int maxNum;

	public static StaticFieldElem getStaticFieldElem(jq_Field staticField) {
		StaticFieldElem ret = staticFieldElemFactory.get(staticField);
		if (ret == null) {
			ret = new StaticFieldElem(staticField, ++maxNum);
			updateMap(staticField, ++maxNum, ret);
		}
		return ret;
	}

	private static void updateMap(jq_Field staticField, int number,
			StaticFieldElem staticFieldElem) {
		staticFieldElemFactory.put(staticField, staticFieldElem);
		staticFieldElemToId.put(staticField, number);
	}

	public static void clear() {
		staticFieldElemFactory.clear();
		staticFieldElemToId.clear();
		maxNum = 0;
	}
}
