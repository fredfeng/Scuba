package framework.scuba.domain;

import java.util.HashSet;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Class.jq_Reference;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operand;
import joeq.Compiler.Quad.Operand.FieldOperand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Operator.Getfield;
import joeq.Compiler.Quad.Operator.Move;
import joeq.Compiler.Quad.Operator.MultiNewArray;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Compiler.Quad.Operator.Putfield;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.QuadVisitor;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.RegisterFactory.Register;

/**
 * Representing the summary for a method. Now it only contains abstractHeap.
 * 
 * @author yufeng
 * 
 */
public class Summary {

	private jq_Method method;
	private AbstractHeap absHeap;

	// finish current summary.
	private boolean terminated;

	public Summary(jq_Method meth) {
		method = meth;
		absHeap = new AbstractHeap();
		this.dumpSummary4Method(meth);
	}
	
    public void dumpSummary4Method(jq_Method meth) {
    	System.out.println("Summary for method: " + meth.getName());
    	System.out.println("**************************************");
		ControlFlowGraph cfg = meth.getCFG();
		String params = "";
		RegisterFactory rf = cfg.getRegisterFactory();
		int numArgs = meth.getParamTypes().length;
		for (int zIdx = 0; zIdx < numArgs; zIdx++) {
			Register v = rf.get(zIdx);
			params = params + " " + v;
		}
		
		Set<Register> locals = new HashSet();
        for (Register v : meth.getLiveRefVars()) {
        	if(!params.contains(v.toString()))
        		locals.add(v);
        }
        
		Set<String> allocs = new HashSet();
		Set<Operand> fieldsBase = new HashSet();

		for (BasicBlock bb : cfg.reversePostOrder()) {
			for (Quad q : bb.getQuads()) {
				Operator op = q.getOperator();
				if (op instanceof New)
					allocs.add(New.getType(q).getType().getName());

				if (op instanceof NewArray)
					allocs.add(NewArray.getType(q).getType().getName());

				if (op instanceof MultiNewArray) 
					allocs.add(MultiNewArray.getType(q).getType().getName());
				
				if (op instanceof Putfield) 
					fieldsBase.add(Putfield.getBase(q));

				if (op instanceof Getfield) 
					fieldsBase.add(Getfield.getBase(q));

			}
		}

		System.out.println("PARAM List: " + params);
		System.out.println("Local List: " + locals);
		System.out.println("Alloc List: " + allocs);
		System.out.println("Field access List: " + fieldsBase);


    	System.out.println("**************************************");
    }

	public void dumpSummaryToFile() {
		absHeap.dumpHeapToFile(2);
	}

	public void dumpAllMemLocsHeapToFile() {
		absHeap.dumpAllMemLocsHeapToFile(2);
	}

	public void validate() {
		absHeap.validate();
	}

	public boolean isTerminated() {
		return terminated;
	}

	public void setTerminated(boolean terminated) {
		this.terminated = terminated;
	}

	public AbstractHeap getAbstractHeap() {
		return absHeap;
	}

	public jq_Method getMethod() {
		return method;
	}

	public void handleStmt(Quad quad) {
		quad.accept(qv);
	}

	QuadVisitor qv = new QuadVisitor.EmptyVisitor() {

		public void visitALength(Quad stmt) {

		}

		public void visitALoad(Quad stmt) {
			// TODO
		}

		public void visitAStore(Quad stmt) {
			// TODO
		}

		public void visitBinary(Quad stmt) {
		}

		public void visitBoundsCheck(Quad stmt) {
		}

		public void visitBranch(Quad stmt) {
		}

		public void visitCheckCast(Quad stmt) {
		}

		// v1 = v2.f
		public void visitGetfield(Quad stmt) {
			// TODO
			FieldOperand field = Getfield.getField(stmt);
			if(field.getField().getType() instanceof jq_Reference)
				absHeap.handleGetfieldStmt(stmt);
		}

		public void visitGetstatic(Quad stmt) {
			// TODO
		}

		public void visitInstanceOf(Quad stmt) {
		}

		public void visitInvoke(Quad stmt) {
			// TODO

		}

		public void visitMemLoad(Quad stmt) {
		}

		public void visitMemStore(Quad stmt) {
		}

		public void visitMonitor(Quad stmt) {
		}

		public void visitMove(Quad stmt) {
			// TODO
			absHeap.handleMoveStmt(stmt);
		}

		public void visitMultiNewArray(Quad stmt) {
			// TODO
		}

		public void visitNew(Quad stmt) {
			// TODO
			absHeap.handleNewStmt(stmt);
		}

		public void visitNewArray(Quad stmt) {
			// TODO
		}

		public void visitNullCheck(Quad stmt) {
		}

		public void visitPhi(Quad stmt) {
		}

		public void visitPutfield(Quad stmt) {
			// TODO
			FieldOperand field = Putfield.getField(stmt);
			if(field.getField().getType() instanceof jq_Reference)
				absHeap.handlePutfieldStmt(stmt);
		}

		public void visitPutstatic(Quad stmt) {
			// TODO
		}

		public void visitReturn(Quad stmt) {
			// TODO
		}

		public void visitSpecial(Quad stmt) {
		}

		public void visitStoreCheck(Quad stmt) {
		}

		public void visitUnary(Quad stmt) {
		}

		public void visitZeroCheck(Quad stmt) {
		}

	};
}
