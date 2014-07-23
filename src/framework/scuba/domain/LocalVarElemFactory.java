package framework.scuba.domain;

import java.util.HashMap;
import java.util.Map;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.RegisterFactory.Register;

public class LocalVarElemFactory {

	public final static Map<Register, LocalVarElem> localVarElemFactory = new HashMap<Register, LocalVarElem>();

	public final static Map<Register, Integer> localVarElemToId = new HashMap<Register, Integer>();

	public final static Map<Integer, LocalVarElem> IdToLocalVarElem = new HashMap<Integer, LocalVarElem>();

	// numbers of LocalVarElem start from 1
	public static int maxNum;

	public static LocalVarElem getLocalVarElem(Register r, jq_Method meth,
			jq_Class clazz, jq_Type type) {
		LocalVarElem ret = localVarElemFactory.get(r);
		if (ret == null) {
			ret = new LocalVarElem(r, meth, clazz, type, ++maxNum);
			updateMap(r, maxNum, ret);
		}
		return ret;
	}

	public static LocalVarElem findLocalVarElem(Register r) {
		return localVarElemFactory.get(r);
	}

	private static void updateMap(Register r, int number,
			LocalVarElem localVarElem) {
		localVarElemFactory.put(r, localVarElem);
		localVarElemToId.put(r, number);
		IdToLocalVarElem.put(number, localVarElem);
	}

	public static void clear() {
		localVarElemFactory.clear();
		localVarElemToId.clear();
		maxNum = 0;
	}
}
