package test.intraproc;

import java.util.ArrayList;
import java.util.List;

/**
 * Test case for Array Load.
 * 
 * @author yufeng
 * 
 */
public class TestRule10 {

	class Z {
		List x;
	}

	class X {

	}

	// v1.f = v2.
	void foo(Z a) {
		Z z = new Z();
		z.x = new ArrayList();
		Z y = z;
		List b = y.x;
	}
}

// PASSED!
// this case can somehow test the field selector (but not recursive)
// but some unmatched things about set and list of field base happened

// public class Harness {
// public static void main(String[] args) {
// new Harness().foo(new Harness().new Z());
// }
//
// class Z {
// List x;
// }
//
// class X {
//
// }
//
// // v1.f = v2.
// void foo(Z a) {
// Z z = new Z();
// z.x = new ArrayList();
// Z y = z;
// List b = y.x;
// }
// }