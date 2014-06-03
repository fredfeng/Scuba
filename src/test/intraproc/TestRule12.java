package test.intraproc;

import java.util.List;


/**
 * Test case from Array load and store.
 *
 */
public class TestRule12 {
	
	class Z {
		List x;
	}
	
	class X {
		Z[] fu = new Z[10];
	}
	
	
	//v1.f = v2.
	void foo(Z a) {

        X y1 = new X();
		Z[] fu = new Z[10];

        Z[] g = y1.fu;
        fu[0] = new Z();
        fu[1] = new Z();
        Z z1 = fu[0];
        Z z2 = fu[1];


	}
}
