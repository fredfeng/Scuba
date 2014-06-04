package test.intraproc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * Test case for recursive field.
 *
 */
public class TestRule16 {
	
	class Node {
		Node next;
	}
	
	class X {
		
	}
	
	//a = v2.
	void foo(Node arg, X b) {
		Node[] set = new Node[3];
		Node node1 = arg;
		set[0] = arg.next;
		
		Node node2 = new Node();
		set[1] = node2;
		arg.next = set[1];

		Node node3 = new Node();
		set[2] = node3;
		arg.next.next = node3;

		node1.next = node2;

		node2.next = node3;
		
		Node x = node1.next.next; //x and node 3 alias
        
        Node y = set[2];   //x and y should be alias.
        
        Node p = arg.next.next.next;
	}
}
