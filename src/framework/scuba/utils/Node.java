package framework.scuba.utils;

import java.util.HashSet;
import java.util.Set;

public class Node {

	private String id;

	private boolean terminated;
	
	private boolean selfLoop;
	
	Set<Node> successors;

	Set<Node> preds;

	public Node(String nodeId) {
		id = nodeId;
		preds = new HashSet<Node>();
		successors = new HashSet<Node>();
	}

	public Set<Node> getSuccessors() {
		return successors;
	}

	public Set<Node> getPreds() {
		return preds;
	}

	public void addPred(Node pred) {
		preds.add(pred);
	}

	public void addSuccessor(Node succ) {
		successors.add(succ);
	}

	public boolean isTerminated() {
		return terminated;
	}

	public void setTerminated(boolean terminated) {
		this.terminated = terminated;
	}
	
	public boolean isSelfLoop() {
		return selfLoop;
	}

	public void setSelfLoop(boolean selfLoop) {
		this.selfLoop = selfLoop;
	}

	public String toString() {
		return "[Node:]" + id;
	}
}
