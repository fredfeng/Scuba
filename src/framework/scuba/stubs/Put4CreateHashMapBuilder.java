package framework.scuba.stubs;

import joeq.Class.jq_Class;
import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.ICFGBuilder;
import joeq.Compiler.Quad.Operand.FieldOperand;
import joeq.Compiler.Quad.Operand.IConstOperand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator.ALoad;
import joeq.Compiler.Quad.Operator.ALoad.ALOAD_A;
import joeq.Compiler.Quad.Operator.Getfield;
import joeq.Compiler.Quad.Operator.Move;
import joeq.Compiler.Quad.Operator.Move.MOVE_A;
import joeq.Compiler.Quad.Operator.Putfield;
import joeq.Compiler.Quad.Operator.Putfield.PUTFIELD_A;
import joeq.Compiler.Quad.Operator.Return;
import joeq.Compiler.Quad.Operator.Return.RETURN_A;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.RegisterFactory.Register;

/**
 * Stub for method "putForCreate:(Ljava/lang/Object;Ljava/lang/Object;)V@java.util.HashMap".
 * 
 * @author Yu Feng (yufeng@cs.utexas.edu)
 */
public class Put4CreateHashMapBuilder implements ICFGBuilder {
	@Override
	public ControlFlowGraph run(jq_Method m) {
		jq_Type[] argTypes = m.getParamTypes();
		int n = argTypes.length;
		RegisterFactory rf = new RegisterFactory(0, n);
		for (int i = 0; i < n; i++) {
			jq_Type t = argTypes[i];
    		rf.getOrCreateLocal(i, t);
		}
		
		jq_Type thisType = jq_Type.parseType("java.util.HashMap");
		jq_Type tabEntryType = jq_Type.parseType("java.util.HashMap$Entry");
		jq_Type tabType = jq_Type.parseType("java.util.HashMap$Entry[]");
		jq_Type objType = jq_Type.parseType("java.lang.Object");

		//hashtable
		Register r0 = rf.getOrCreateLocal(0, thisType);
		RegisterOperand ro0 = new RegisterOperand(r0, thisType);
		//value
		Register r2 = rf.getOrCreateLocal(2, objType);
		Register t1 = rf.getOrCreateStack(1, objType);
		Register t2 = rf.getOrCreateStack(2, tabType);
		Register t3 = rf.getOrCreateStack(3, tabEntryType);

		ControlFlowGraph cfg = new ControlFlowGraph(m, 1, 0, rf);
		BasicBlock bb = cfg.createBasicBlock(1, 1, 4, null);
		tabType.load();
		tabEntryType.load();
		jq_Field tab = ((jq_Class)thisType).getDeclaredField("table");
		FieldOperand tabF = new FieldOperand(tab);
		
		jq_Field val = ((jq_Class)tabEntryType).getDeclaredField("value");
		FieldOperand valF = new FieldOperand(val);

		Quad q1 = Move.create(0, bb, MOVE_A.INSTANCE, new RegisterOperand(t1,
				objType), new RegisterOperand(r2, objType));
		Quad q2 = Getfield.create(0, bb, Getfield.GETFIELD_A.INSTANCE,
				new RegisterOperand(t2, tabType), ro0, tabF, null);
		Quad q3 = ALoad.create(1, bb, ALOAD_A.INSTANCE, new RegisterOperand(t3,
				tabEntryType), new RegisterOperand(t2, tabType),
				new IConstOperand(1), null);
		Quad q4 = Putfield.create(3, bb, PUTFIELD_A.INSTANCE,
				new RegisterOperand(t3, tabEntryType), valF,
				new RegisterOperand(t1, objType), null);
		Quad ret = Return.create(4, bb, Return.RETURN_V.INSTANCE);
		bb.appendQuad(q1);
		bb.appendQuad(q2);
		bb.appendQuad(q3);
		bb.appendQuad(q4);
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
