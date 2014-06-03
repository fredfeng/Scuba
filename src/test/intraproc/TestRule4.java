package test.intraproc;



/**
 * Test case for Rule 4 in figure 8.
 * @author yufeng
 *
 */
public class TestRule4 {
	
	class Z {
	}
	
	//v1 = new XX.
	void foo(Z a) {
		Z v = new Z();
	}
}

// PASSED!

//public class Harness {
//	public static void main(String[] args) {
//		new Harness().foo(new Harness().new Z());
//	}
//
//	class Z {
//	}
//	
//	//v1 = new XX.
//	void foo(Z a) {
//		Z v = new Z();
//	}
//}
