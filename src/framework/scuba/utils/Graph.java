package framework.scuba.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Graph {
	List<Node> nodes;
	
	//suppose only one entry.
	List<Node> entries;
	
	public Graph() {
		nodes = new ArrayList<Node>();
	}
	
	public void setEntry(Node n) {
		entries.add(n);
	}
	
	public void addNode(Node n) {
		nodes.add(n);
	}
	
	public List<Node> getNodes() {
		return nodes;
	}
	
	public List<Node> getReversePostOrder() {
		List<Node> result = new ArrayList<Node>();
		Set<Node> visited = new HashSet<Node>();
		reversePostOrderHelper(result, visited, entries.get(0));
        java.util.Collections.reverse(result);

		return result;
	}
	
	public void reversePostOrderHelper(List<Node> result, Set<Node> visited,
			Node node) {
		if(visited.contains(node)) return;
		visited.add(node);
		for(Node succ : node.getSuccessors())
			this.reversePostOrderHelper(result, visited, succ);
		
		result.add(node);
		
	}
}
