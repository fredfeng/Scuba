package test.interproc;

//Example to demo interface invoke.
public class TestInter4 {

	public static void main(String[] args) {
		new TestInter4().run();
	}

	public void run() {
		X y = this.foo();
		
		I1 i = new Y();
		i.boo();
	}

	public  X foo() {
		X x = new X();
		return x;
	}
	
	class X {

	}
	
	class Y implements I1, I2{
		public void boo() {
			
		}
	}
	
	interface I1 {
		public void boo();
	}

	interface I2 extends I1 {
		public void boo();
	}
}
