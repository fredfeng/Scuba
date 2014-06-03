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
	void foo(X a, X b) {
		Node[] set = new Node[3];
		Node node1 = new Node();
		set[0] = node1;
		
		Node node2 = new Node();
		set[1] = node2;

		Node node3 = new Node();
		set[2] = node3;

		node1.next = node2;

		node2.next = node3;
		
		Node x = node1.next.next; //x and node 3 alias
        
        Node y = set[2];   //x and y should be alias.
	}
}
