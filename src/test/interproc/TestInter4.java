package test.interproc;

//Example to demo subtyping.
public class TestInter4 {

	public static void main(String[] args) {
		new TestInter4().run();
	}

	public void run() {
		X y = this.foo();
	}

	public  X foo() {
		X x = new X();
		return x;
	}
	
	class X {

	}

}
