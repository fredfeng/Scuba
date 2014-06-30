package framework.scuba.stubs;

import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.ICFGBuilder;
import joeq.Compiler.Quad.Operand.AConstOperand;
import joeq.Compiler.Quad.Operand.IConstOperand;
import joeq.Compiler.Quad.Operator.Return;
import joeq.Compiler.Quad.Operator.Return.RETURN_I;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory;

/**
 * Stub for method "indexOf:(Ljava/lang/Object;)I@java.util.Vector" in
 * class java.util.Vector.
 * 
 * @author Yu Feng (yufeng@cs.utexas.edu)
 */
public class IndexOfVectorBuilder implements ICFGBuilder {
	@Override
	public ControlFlowGraph run(jq_Method m) {
		jq_Type[] argTypes = m.getParamTypes();
		int n = argTypes.length;
		RegisterFactory rf = new RegisterFactory(0, n);
		for (int i = 0; i < n; i++) {
			jq_Type t = argTypes[i];
    		rf.getOrCreateLocal(i, t);
		}
		
		ControlFlowGraph cfg = new ControlFlowGraph(m, 1, 0, rf);
		BasicBlock bb = cfg.createBasicBlock(1, 1, 1, null);
		Quad ret = Return.create(1, bb, RETURN_I.INSTANCE, new IConstOperand(0));
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
