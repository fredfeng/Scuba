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
import joeq.Compiler.Quad.Operator.AStore;
import joeq.Compiler.Quad.Operator.AStore.ASTORE_A;
import joeq.Compiler.Quad.Operator.Getfield;
import joeq.Compiler.Quad.Operator.Move;
import joeq.Compiler.Quad.Operator.Move.MOVE_A;
import joeq.Compiler.Quad.Operator.Return;
import joeq.Compiler.Quad.Operator.Return.RETURN_A;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.RegisterFactory.Register;

/**
 * Stub for method "boolean put(k,v)" in
 * class java.util.Hashtable.
 * 
 * @author Yu Feng (yufeng@cs.utexas.edu)
 */
public class PutHashtableBuilder implements ICFGBuilder {
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

		//hashtable
		Register r0 = rf.getOrCreateLocal(0, thisType);
		RegisterOperand ro0 = new RegisterOperand(r0, thisType);
		//value
		Register r2 = rf.getOrCreateLocal(2, tabType);
		Register t1 = rf.getOrCreateStack(1, objType);
		Register t2 = rf.getOrCreateStack(2, tabType);
		
		ControlFlowGraph cfg = new ControlFlowGraph(m, 1, 0, rf);
		BasicBlock bb = cfg.createBasicBlock(1, 1, 3, null);
		tabType.load();
		tabEntryType.load();
		jq_Field tab = ((jq_Class)thisType).getDeclaredField("table");
		FieldOperand tabF = new FieldOperand(tab);

		Quad q1 = Move.create(0, bb, MOVE_A.INSTANCE, new RegisterOperand(t1,
				objType), new RegisterOperand(r2, objType));
		Quad q2 = Getfield.create(0, bb, Getfield.GETFIELD_A.INSTANCE,
				new RegisterOperand(t2, tabType), ro0, tabF, null);
		Quad q3 = AStore.create(1, bb, ASTORE_A.INSTANCE, new RegisterOperand(
				t1, tabEntryType), new RegisterOperand(t2, tabType),
				new IConstOperand(1), null);
		Quad ret = Return.create(3, bb, RETURN_A.INSTANCE, new RegisterOperand(
				t1, objType));
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
