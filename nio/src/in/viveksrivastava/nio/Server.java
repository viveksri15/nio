package in.viveksrivastava.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

	private static ExecutorService executors = Executors.newFixedThreadPool(10);

	int port;

	static Map<SelectionKey, ByteBuffer> byteBufferStore = Collections
			.synchronizedMap(new HashMap<SelectionKey, ByteBuffer>());
	static Map<SelectionKey, Integer> contentLenths = Collections
			.synchronizedMap(new HashMap<SelectionKey, Integer>());

	private final static byte NEXT_LINE = "\n".getBytes()[0];
	private final static int MAX_HEADER_LOCATION = (Integer.MAX_VALUE + "\n")
			.getBytes().length;

	public Server(int port) {
		this.port = port;
	}

	public void startServer() throws IOException {
		Selector selector = Selector.open();
		ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();

		//TODO: test what happens if true
		serverSocketChannel.configureBlocking(false);

		ServerSocket socket = serverSocketChannel.socket();
		socket.bind(new InetSocketAddress(port));

		@SuppressWarnings("unused")
		SelectionKey selectionKey = serverSocketChannel.register(selector,
				SelectionKey.OP_ACCEPT);

		System.out.println("LISTINGING ON " + port);

		while (true) {
			printKeyStates(selector.keys());
			System.out.println("WAITING_FOR_SELECTOR");

			int select = selector.select();

			System.out.println("SELECTORS=" + select);

			Set<SelectionKey> selectedKeys = selector.selectedKeys();

			Iterator<SelectionKey> iterator = selectedKeys.iterator();

			while (iterator.hasNext()) {

				SelectionKey key = iterator.next();

				if (!key.isValid())
					continue;

				if (key.isAcceptable()) {

					ServerSocketChannel channel = (ServerSocketChannel) key
							.channel();
					SocketChannel accept = channel.accept();
					accept.configureBlocking(false);

					accept.register(selector, SelectionKey.OP_READ);

					iterator.remove();
					System.out.println("ACCEPTED CONNECTION");
				} else if (key.isReadable()) {
					System.out.println("STARTING READ");

					Integer contentLengh = contentLenths.get(key);

					ByteBuffer buffer = byteBufferStore.get(key);
					if (buffer == null) {
						buffer = ByteBuffer.allocate(5);
						byteBufferStore.put(key, buffer);
					}

					if (buffer.remaining() == 0) {
						ByteBuffer buffer1 = ByteBuffer.allocate(buffer
								.capacity() + 1024);
						int oldPos = buffer.position();
						buffer.flip();
						buffer1.put(buffer);
						buffer1.limit(buffer1.capacity());
						buffer1.position(oldPos);
						byteBufferStore.put(key, buffer1);
					}

					SocketChannel channel = (SocketChannel) key.channel();
					int read;
					try {
						read = channel.read(buffer);

						//Isolating the header!
						if (contentLengh == null && read > 0) {
							byte[] b = buffer.array();
							int i = 0;
							for (i = 0; i < b.length && i < MAX_HEADER_LOCATION; i++) {
								if (b[i] == NEXT_LINE) {
									System.out.println("HEADER="
											+ new String(b));
									contentLengh = Integer.parseInt(new String(
											b, 0, i));
									contentLenths.put(key, contentLengh);
									System.out.println("HEADER_FOUND="
											+ contentLengh + " " + i + " "
											+ b.length);
									break;
								}
							}
							if (contentLengh != null) {
								ByteBuffer buffer1 = ByteBuffer.allocate(buffer
										.capacity());
								byte[] b1 = new byte[buffer1.capacity()];
								System.out.println("HEEAADDEERR ="
										+ buffer.position() + " " + i);
								int pos = buffer.position();
								if (pos > i + 1) {
									System.arraycopy(b, i + 1, b1, 0, b.length
											- i - 1);
									System.out.println("COPIED='"
											+ new String(b1) + "'");
									buffer = ByteBuffer.wrap(b1);
									buffer.position(pos - i - 1);
								} else {
									buffer = ByteBuffer.wrap(b1);
									buffer.position(0);
								}
								buffer.limit(buffer.capacity());
								byteBufferStore.put(key, buffer);
							}
						}

						System.out.println("READ "
								+ read
								+ " "
								+ buffer.position()
								+ " "
								+ buffer.limit()
								+ " "
								+ new String(buffer.array(), 0, buffer
										.position()));
						if (buffer != null
								&& contentLengh != null
								&& ((read < 0 && buffer.position() > 0) || buffer
										.position() == contentLengh)) {
							if (channel.isConnected()) {
								System.out.println("STARTING WORKER");
								iterator.remove();
								executors.submit(new Worker(key,
										byteBufferStore.remove(key)));
							} else
								throw new IOException(
										"CLIENT CLOSED CONNECTION. WHY SHOULD I WORK?");
						} else if (read < 0)
							throw new IOException("CLIENT CLOSED CONNECTION");

					} catch (IOException e) {
						e.printStackTrace();
						if (buffer != null)
							byteBufferStore.remove(key);
						try {
							key.channel().close();
						} catch (IOException e1) {
							e1.printStackTrace();
						}
						key.cancel();
						System.out.println("KEY CANCELLED");
						iterator.remove();
					}
					System.out.println("READ DONE");
				} else if (key.isWritable()) {

					ByteBuffer buffer = byteBufferStore.get(key);
					System.out.println("STARTING WRITE "
							+ new String(buffer.array()));
					if (buffer == null || buffer.remaining() == 0) {
						if (buffer != null) {
							System.out.println("WRITE COMPLETE ="
									+ new String(buffer.array()));
							byteBufferStore.remove(key);
							contentLenths.remove(key);
							iterator.remove();
						}
						key.interestOps(SelectionKey.OP_READ);
						continue;
					}
					SocketChannel channel = (SocketChannel) key.channel();
					/*if (buffer.position() == 0) {
						String header = buffer.limit() + "\n";
						System.out.println("SENDING CONTENT LENGTH  " + "'"
								+ header + "'");
						channel.write(ByteBuffer.wrap(header.getBytes()));
					}*/
					try {
						channel.write(buffer);
					} catch (IOException e) {
						e.printStackTrace();
						if (buffer != null)
							byteBufferStore.remove(key);
						try {
							key.channel().close();
						} catch (IOException e1) {
							e1.printStackTrace();
						}
						key.cancel();
						System.out.println("KEY CANCELLED");
						iterator.remove();
					}
					System.out.println("WRITE DONE");
				}
			}
		}
	}
	private void printKeyStates(Set<SelectionKey> keys) {
		if (keys != null)
			for (SelectionKey key : keys)
				if (key.isValid())
					System.out.println("KEY=" + key.interestOps());
		System.out.println("----");
	}

	public static void main(String[] args) throws IOException {
		Server server = new Server(Integer.parseInt(args[0]));
		server.startServer();
	}
}