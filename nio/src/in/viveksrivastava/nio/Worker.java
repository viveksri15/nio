package in.viveksrivastava.nio;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.concurrent.Callable;

public class Worker implements Callable<Void> {

	SelectionKey key;
	ByteBuffer byteBuffer = null;
	public Worker(SelectionKey key, ByteBuffer byteBuffer) {
		this.key = key;
		byteBuffer.flip();
		this.byteBuffer = byteBuffer;
	}

	@Override
	public Void call() throws Exception {
		byte[] b = byteBuffer.array();
		StringBuffer stringBuffer = new StringBuffer(new String(b, 0,
				byteBuffer.limit()));
		System.out.println("WORKING ON " + stringBuffer);

		//DO WORK HERRE
		String data = stringBuffer.reverse().toString();

		data = data.getBytes().length + "\n" + data;
		byte[] returnData = data.getBytes();

		if (byteBuffer.capacity() < returnData.length) {
			Server.byteBufferStore.remove(key);
			byteBuffer = ByteBuffer.wrap(returnData);
			System.out.println("****INCREASING SIZE " + byteBuffer.position() + " "
					+ byteBuffer.limit());
		} else {
			byteBuffer.clear();
			byteBuffer.put(returnData);
			byteBuffer.flip();
			System.out.println("****NOT INCREASING SIZE " + byteBuffer.position() + " "
					+ byteBuffer.limit());
		}

		Server.byteBufferStore.put(key, byteBuffer);
		System.out.println("WORKER COMPLETED '"
				+ new String(byteBuffer.array(), 0, byteBuffer.limit()) + "' "
				+ byteBuffer.limit() + " " + new String(data));
		key.interestOps(SelectionKey.OP_WRITE);
		key.selector().wakeup();
		return null;
	}
}