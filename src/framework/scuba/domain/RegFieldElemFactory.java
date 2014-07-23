package framework.scuba.domain;

import java.util.HashMap;
import java.util.Map;

import joeq.Class.jq_Field;

public class RegFieldElemFactory {

	public final static Map<jq_Field, RegFieldElem> regFieldElemFactory = new HashMap<jq_Field, RegFieldElem>();

	public final static Map<jq_Field, Integer> regFieldElemToId = new HashMap<jq_Field, Integer>();

	// numbers of RegFieldElem start from 1
	public static int maxNum;

	public static RegFieldElem getRegFieldElem(jq_Field field) {
		RegFieldElem ret = regFieldElemFactory.get(field);
		if (ret == null) {
			ret = new RegFieldElem(field, ++maxNum);
			updateMap(field, maxNum, ret);
		}
		return ret;
	}

	private static void updateMap(jq_Field field, int number,
			RegFieldElem regFieldElem) {
		regFieldElemFactory.put(field, regFieldElem);
		regFieldElemToId.put(field, number);
	}

	public static void clear() {
		regFieldElemFactory.clear();
		regFieldElemToId.clear();
	}
}
