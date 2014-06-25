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
 * class java.util.AbstractCollection.
 * 
 * @author Yu Feng (yufeng@cs.utexas.edu)
 */
public class ToStringAbsCollectionBuilder implements ICFGBuilder {
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
//		Register t1 = rf.getOrCreateLocal(2, strType);

		RegisterOperand ro = new RegisterOperand(r1, strType);
//		RegisterOperand to = new RegisterOperand(t1, strType);

		ControlFlowGraph cfg = new ControlFlowGraph(m, 1, 0, rf);
		BasicBlock bb = cfg.createBasicBlock(1, 1, 1, null);
		
		Quad q1 = New.create(1, bb, New.NEW.INSTANCE, ro, new TypeOperand(strType));
//		Quad q2 = Move.create(2, bb, Move.MOVE_A.INSTANCE, to, ro);
//		Quad q2 = Move.create(2, bb, t1, r1, strType);
//		Quad q3 = Move.create(3, bb, MOVE_A, r2, t)
//		Quad q4 = Invoke.create(4, bb, INVOKESTATIC_V, res, mo, length);
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
		
//		1: NEW T1, java.lang.String
//		2: MOVE_A T2, T1
//		4: MOVE_A T3, AConst: "A"
//		3: INVOKESTATIC_V <init>:(Ljava/lang/String;)V@java.lang.String, (T2, T3)
//		5: RETURN_A T1
	}
}
