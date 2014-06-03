package framework.scuba.utils;

import joeq.Class.jq_Class;

public class ChordUtil {
	
	//either a is the subclass of b or b is the subclass of a.
	//or one implement other's interface.
	public static boolean checkCompatible(jq_Class a, jq_Class b) {
		return a.implementsInterface(b) || b.implementsInterface(a)
				|| a.extendsClass(b) || b.extendsClass(a);
	}
}
