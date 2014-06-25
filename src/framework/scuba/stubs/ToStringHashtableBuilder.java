package framework.scuba.stubs;

import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.ICFGBuilder;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operand.TypeOperand;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.Return;
import joeq.Compiler.Quad.Operator.Return.RETURN_A;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.RegisterFactory.Register;

/**
 * Stub for method "String toString()" in
 * class java.util.Hashtable.
 * 
 * @author Yu Feng (yufeng@cs.utexas.edu)
 */
public class ToStringHashtableBuilder implements ICFGBuilder {
	@Override
	public ControlFlowGraph run(jq_Method m) {
		jq_Type[] argTypes = m.getParamTypes();
		int n = argTypes.length;
		RegisterFactory rf = new RegisterFactory(0, n);
		for (int i = 0; i < n; i++) {
			jq_Type t = argTypes[i];
    		rf.getOrCreateLocal(i, t);
		}
		
		jq_Type strType = jq_Type.parseType("java.lang.String");
		Register r1 = rf.getOrCreateStack(1, strType);

		RegisterOperand ro = new RegisterOperand(r1, strType);

		ControlFlowGraph cfg = new ControlFlowGraph(m, 1, 0, rf);
		BasicBlock bb = cfg.createBasicBlock(1, 1, 1, null);
		
		Quad q1 = New.create(1, bb, New.NEW.INSTANCE, ro, new TypeOperand(strType));
		Quad ret = Return.create(5, bb, RETURN_A.INSTANCE);
		Return.setSrc(ret, ro);
		
		bb.appendQuad(q1);
		bb.appendQuad(ret);
		BasicBlock entry = cfg.entry();
		BasicBlock exit = cfg.exit();
		bb.addPredecessor(entry);
		bb.addSuccessor(exit);
		entry.addSuccessor(bb);
		exit.addPredecessor(bb);
		return cfg;
	}
}
