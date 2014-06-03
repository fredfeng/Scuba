package test.intraproc;

import java.util.ArrayList;
import java.util.List;


/**
 * Test case from Xinyu to demo field access problem caused by subtyping.
 *
 */
public class TestRule11 {
	
	class Z {
		List x;
	}
	
	class X {
		Z f;
		Z f3;
		Z f4;
		
	}
	
	class Y extends X {
		Z f;
		Z f2;
	}
	
	//v1.f = v2.
	void foo(Z a) {

        Y y1 = new Y();
        y1.f = new Z();
        X x1 = y1;
        Z f1 = x1.f;
        Z f4 = x1.f3;

        Z f2 = y1.f;
        Z f3 = y1.f2;


	}
}
