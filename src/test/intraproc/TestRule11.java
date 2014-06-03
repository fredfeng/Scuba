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
	}
	
	class Y extends X {
		Z f;
	}
	
	//v1.f = v2.
	void foo(Z a) {

        Y y1 = new Y();
        y1.f = new Z();
        X x1 = y1;
        Z f1 = x1.f;
	}
}
