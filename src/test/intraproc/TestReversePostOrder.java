package test.intraproc;

import framework.scuba.utils.Graph;
import framework.scuba.utils.Node;

public class TestReversePostOrder {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		Graph gg = new Graph();
		Node a = new Node("A");
		Node b = new Node("B");
		Node c = new Node("C");
		Node d = new Node("D");
		Node e = new Node("E");
		Node f = new Node("F");
		Node g = new Node("G");
		Node h = new Node("H");
		Node i = new Node("I");
		f.addSuccessor(b);
		f.addSuccessor(g);
		g.addSuccessor(i);
		i.addSuccessor(h);
		b.addSuccessor(a);
		b.addSuccessor(d);
		d.addSuccessor(c);
		d.addSuccessor(e);
		
		gg.addNode(a);
		gg.addNode(b);
		gg.addNode(c);
		gg.addNode(d);
		gg.addNode(e);
		gg.addNode(f);
		gg.addNode(g);
		gg.addNode(h);
		gg.addNode(i);
		gg.setEntry(f);
		
		System.out.println(gg.getReversePostOrder());

	}

}
