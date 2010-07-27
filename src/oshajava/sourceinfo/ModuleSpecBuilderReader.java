package oshajava.sourceinfo;

import java.io.IOException;

import oshajava.util.ColdStorage;

public class ModuleSpecBuilderReader {
	public static void main(String[] args) {
		for (String a : args) {
			try {
				System.out.println(((ModuleSpecBuilder)ColdStorage.load(a)).generateSpec());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
	}
}
