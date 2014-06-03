package test.intraproc;


/**
 * Test case for assign a local to arg.
 *
 */
public class TestRule13 {
	
	class X {
	}
	
	
	//a = v2.
	void foo(X a, X b) {
        X y1 = new X();
        a = y1;
        X y2 = a;
	}
}
