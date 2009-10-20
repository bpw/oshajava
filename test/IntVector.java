
public class IntVector {

	protected int[] array;
	protected int count = 0;
	
	public IntVector(int size) {
		array = new int[size];
	}
	
	public void add(int i) {
		if (count >= array.length) {
			resize(count * 2);
		}
		array[count++] = i;
	}
	
	private void resize(int size) {
		assert size >= count;
		final int[] tmp = new int[size];
		for (int i = 0; i < count; i++) {
			tmp[i] = array[i];
		}
		array = tmp;
	}
}
