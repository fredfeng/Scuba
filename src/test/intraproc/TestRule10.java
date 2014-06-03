package test.intraproc;

import java.util.ArrayList;
import java.util.List;


/**
 * Test case for Array Load.
 * @author yufeng
 *
 */
public class TestRule10 {
	
	class Z {
		List x;
	}
	
	class X {
		
	}
	
	//v1.f = v2.
	void foo(Z a) {
		Z z = new Z();
		z.x = new ArrayList();
		Z y = z;
		List b = y.x;
	}
}
