package framework.scuba.domain;

import java.util.HashMap;
import java.util.Map;

import joeq.Class.jq_Method;

public class RetElemFactory {

	public final static Map<jq_Method, RetElem> retElemFactory = new HashMap<jq_Method, RetElem>();

	public final static Map<jq_Method, Integer> retElemToId = new HashMap<jq_Method, Integer>();

	// numbers of RetElem start from 1
	public static int maxNum;

	public static RetElem getRetElem(jq_Method meth) {
		RetElem ret = retElemFactory.get(meth);
		if (ret == null) {
			ret = new RetElem(meth, ++maxNum);
			updateMap(meth, maxNum, ret);
		}
		return ret;
	}

	private static void updateMap(jq_Method meth, int number, RetElem retElem) {
		retElemFactory.put(meth, retElem);
		retElemToId.put(meth, number);
	}

	public static void clear() {
		retElemFactory.clear();
		retElemToId.clear();
		maxNum = 0;
	}
}
