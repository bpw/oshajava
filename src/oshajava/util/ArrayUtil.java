package oshajava.util;

public class ArrayUtil {

	/**
	 * Expand the given array to size n.
	 * @param oldArray
	 * @param newSize
	 * @return
	 */
	public static Object[] copy(final Object[] oldArray, int newSize) {
		final Object[] newArray = new Object[newSize];
		System.arraycopy(oldArray, 0, newArray, 0, newSize > oldArray.length ? oldArray.length : newSize);
		return newArray;
	}
	
	/**
	 * Expand the given array to size n.
	 * @param oldArray
	 * @param newSize
	 * @return
	 */
	public static int[] copy(final int[] oldArray, int newSize) {
		final int[] newArray = new int[newSize];
		System.arraycopy(oldArray, 0, newArray, 0, newSize > oldArray.length ? oldArray.length : newSize);
		return newArray;
	}
}
