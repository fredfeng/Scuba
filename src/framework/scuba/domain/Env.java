package framework.scuba.domain;

import java.util.HashMap;
import java.util.Map;

import joeq.Class.jq_Class;
import joeq.Class.jq_Field;
import framework.scuba.helper.ArgDerivedHelper;

public class Env {

	// this is the global fields factory (StaticElem, e.g. A.f)
	public static Map<StaticElem, StaticElem> staticElemFactory = new HashMap<StaticElem, StaticElem>();

	// get the StaticElem given the declaring class and the corresponding field
	// in the IR
	public static StaticElem getStaticElem(jq_Class clazz, jq_Field field) {
		// create a wrapper
		StaticElem ret = new StaticElem(clazz, field);
		// try to look up this wrapper in the factory
		if (staticElemFactory.containsKey(ret)) {
			return (StaticElem) staticElemFactory.get(ret);
		}
		// not found in the factory
		// every time generating a staticElem, do this marking
		ArgDerivedHelper.markArgDerived(ret);
		staticElemFactory.put(ret, ret);

		return ret;
	}

	// get the StaticElem in the mem loc factory by an StaticElem with the
	// same content (we want to use exactly the same instance)
	public static StaticElem getStaticElem(StaticElem other) {

		if (staticElemFactory.containsKey(other)) {
			return (StaticElem) staticElemFactory.get(other);
		}

		ArgDerivedHelper.markArgDerived(other);
		staticElemFactory.put(other, other);

		return other;
	}
}
