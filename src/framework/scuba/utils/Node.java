package framework.scuba.utils;

import java.util.ArrayList;
import java.util.List;

public class Node {
	
	private String id;
	
	List<Node> successors = new ArrayList<Node>();
	
	public Node(String nodeId) {
		id = nodeId;
	}

	public List<Node> getSuccessors() {
		return successors;
	}

	public void addSuccessor(Node succ) {
		successors.add(succ);
	}

	public String toString() {
		return "[Node:]" + id;
	}
}
