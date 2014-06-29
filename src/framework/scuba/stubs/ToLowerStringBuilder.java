package framework.scuba.stubs;

import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.ICFGBuilder;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator.Move;
import joeq.Compiler.Quad.Operator.Return;
import joeq.Compiler.Quad.Operator.Return.RETURN_A;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.RegisterFactory.Register;

/**
 * Stub for method "String toLowercase()" in
 * class java.lang.String.
 * 
 * @author Yu Feng (yufeng@cs.utexas.edu)
 */
public class ToLowerStringBuilder implements ICFGBuilder {
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
		Register r0 = rf.getOrCreateLocal(0, strType);
		RegisterOperand r0o = new RegisterOperand(r0, strType);
		
		Register r1 = rf.getOrCreateStack(1, strType);
		RegisterOperand r1o = new RegisterOperand(r1, strType);

		ControlFlowGraph cfg = new ControlFlowGraph(m, 1, 0, rf);
		BasicBlock bb = cfg.createBasicBlock(1, 1, 1, null);
		
		Quad q1 = Move.create(0, bb, Move.MOVE_A.INSTANCE, r1o, r0o);

		Quad ret = Return.create(5, bb, RETURN_A.INSTANCE);
		Return.setSrc(ret, r1o);
		
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
