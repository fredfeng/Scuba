package framework.scuba.helper;

import joeq.Class.jq_Type;
import joeq.Compiler.Quad.Operand.TypeOperand;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Compiler.Quad.Quad;

public class AllocElemHelper {

	public static jq_Type rslvNewType(Quad stmt) {
		TypeOperand to = New.getType(stmt);
		assert (to != null) : "Fail to get type of New.";
		return to.getType();
	}

	public static jq_Type rslvNewArrayType(Quad stmt) {
		TypeOperand to = NewArray.getType(stmt);
		assert (to != null) : "Fail to get type of NewArray.";
		return to.getType();
	}

}