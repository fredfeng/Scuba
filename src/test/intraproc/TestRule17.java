package test.intraproc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * Test case for multi-array.
 *
 */
public class TestRule17 {
	
	class Node {
		Node next;
	}
	
	class X {
		
	}
	
	//a = v2.
	void foo(X a, X b) {
		Node[][] mul = new Node[3][3];
		mul[0][0] = new Node();
		mul[1][1] = new Node();
		Node x = mul[1][2];
		Node y = mul[0][1];
		//x and y should be alias.
	}
}
