package framework.scuba.domain;

import java.util.HashMap;
import java.util.Map;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.RegisterFactory.Register;

public class ParamElemFactory {

	public final static Map<Register, ParamElem> paramElemFactory = new HashMap<Register, ParamElem>();

	public final static Map<Register, Integer> paramElemToId = new HashMap<Register, Integer>();

	public final static Map<Integer, ParamElem> IdToParamElem = new HashMap<Integer, ParamElem>();

	// numbers of ParamElem start from 1
	public static int maxNum;

	public static ParamElem getParamElem(Register r, jq_Method meth,
			jq_Class clazz, jq_Type type) {
		ParamElem ret = paramElemFactory.get(r);
		if (ret == null) {
			ret = new ParamElem(r, meth, clazz, type, ++maxNum);
			updateMap(r, maxNum, ret);
		}
		return ret;
	}

	public static ParamElem findParamElem(Register r) {
		return paramElemFactory.get(r);
	}

	private static void updateMap(Register r, int number, ParamElem paramElem) {
		paramElemFactory.put(r, paramElem);
		paramElemToId.put(r, number);
		IdToParamElem.put(number, paramElem);
	}

	public static void clear() {
		paramElemFactory.clear();
		paramElemToId.clear();
		IdToParamElem.clear();
		maxNum = 0;
	}
}