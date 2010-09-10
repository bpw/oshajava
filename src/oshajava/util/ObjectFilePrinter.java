package oshajava.util;

import java.io.IOException;


public class ObjectFilePrinter {
	public static void main(String[] args) {
		for (String a : args) {
			try {
				System.out.println(a + ":");
				System.out.println(ColdStorage.load(a));
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
