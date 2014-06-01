package framework.scuba.domain;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.QuadVisitor;

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
	}

	public void dump() {
		absHeap.dump();
	}

	public void dumpAllLocs() {
		absHeap.dumpAllMemLocs();
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
