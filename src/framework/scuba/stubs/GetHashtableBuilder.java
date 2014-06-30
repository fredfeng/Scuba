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
import joeq.Compiler.Quad.Operator.Getfield.GETFIELD_A;
import joeq.Compiler.Quad.Operator.Return;
import joeq.Compiler.Quad.Operator.Return.RETURN_A;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.RegisterFactory.Register;

/**
 * Stub for method "boolean get(k)" in
 * class java.util.Hashtable.
 * 
 * @author Yu Feng (yufeng@cs.utexas.edu)
 */
public class GetHashtableBuilder implements ICFGBuilder {
	@Override
	public ControlFlowGraph run(jq_Method m) {
		jq_Type[] argTypes = m.getParamTypes();
		int n = argTypes.length;
		RegisterFactory rf = new RegisterFactory(0, n);
		for (int i = 0; i < n; i++) {
			jq_Type t = argTypes[i];
    		rf.getOrCreateLocal(i, t);
		}
		
		jq_Type thisType = jq_Type.parseType("java.util.Hashtable");
		jq_Type tabEntryType = jq_Type.parseType("java.util.Hashtable$Entry");
		jq_Type tabType = jq_Type.parseType("java.util.Hashtable$Entry[]");
		jq_Type objType = jq_Type.parseType("java.lang.Object");

		assert tabType != null;

		//hashtable
		Register r0 = rf.getOrCreateLocal(0, thisType);
		RegisterOperand ro0 = new RegisterOperand(r0, thisType);
		//value
		Register t2 = rf.getOrCreateStack(2, tabType);
		Register t3 = rf.getOrCreateStack(3, tabEntryType);
		Register t4 = rf.getOrCreateStack(4, objType);

		ControlFlowGraph cfg = new ControlFlowGraph(m, 1, 0, rf);
		BasicBlock bb = cfg.createBasicBlock(1, 1, 4, null);
		tabType.load();
		tabEntryType.load();
		jq_Field tab = ((jq_Class)thisType).getDeclaredField("table");
		FieldOperand tabF = new FieldOperand(tab);
		
		jq_Field val = ((jq_Class)tabEntryType).getDeclaredField("value");
		FieldOperand valF = new FieldOperand(val);

		Quad q1 = Getfield.create(0, bb, Getfield.GETFIELD_A.INSTANCE,
				new RegisterOperand(t2, tabType), ro0, tabF, null);
		Quad q2 = ALoad.create(1, bb, ALOAD_A.INSTANCE, new RegisterOperand(t3,
				tabEntryType), new RegisterOperand(t2, tabType),
				new IConstOperand(1), null);
		Quad q3 = Getfield.create(2, bb, GETFIELD_A.INSTANCE,
				new RegisterOperand(t4, objType), new RegisterOperand(t3,
						tabEntryType), valF, null);
		Quad ret = Return.create(3, bb, RETURN_A.INSTANCE, new RegisterOperand(
				t4, objType));
		bb.appendQuad(q1);
		bb.appendQuad(q2);
		bb.appendQuad(q3);
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
