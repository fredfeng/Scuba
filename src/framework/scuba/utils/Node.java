package framework.scuba.utils;

import java.util.ArrayList;
import java.util.List;

public class Node {
	
	private String id;
	
	private boolean terminated;
	
	List<Node> successors = new ArrayList<Node>();
	
	List<Node> preds = new ArrayList<Node>();

	
	public Node(String nodeId) {
		id = nodeId;
	}

	public List<Node> getSuccessors() {
		return successors;
	}
	
	public List<Node> getPreds() {
		return preds;
	}
	
	public void addPred(Node pred) {
		if(!preds.contains(pred))
			preds.add(pred);
	}

	public void addSuccessor(Node succ) {
		if(!successors.contains(succ))
			successors.add(succ);
	}
	
	public boolean isTerminated() {
		return terminated;
	}

	public void setTerminated(boolean terminated) {
		this.terminated = terminated;
	}

	public String toString() {
		return "[Node:]" + id;
	}
}
