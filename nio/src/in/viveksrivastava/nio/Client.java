package in.viveksrivastava.nio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;
import java.util.Set;

public class Client {

	ByteBuffer buffer = null;
	String result = null;
	Integer contentLengh = null;
	private final static byte NEXT_LINE = "\n".getBytes()[0];
	private final static int MAX_HEADER_LOCATION = (Integer.MAX_VALUE + "\n")
			.getBytes().length;
	SocketChannel channel = SocketChannel.open();
	int port;

	public static void main(String[] args) throws IOException,
			InterruptedException {
		Client client = new Client(Integer.parseInt(args[0]));
		client.startClientDaemon();

		BufferedReader bufferedReader = new BufferedReader(
				new InputStreamReader(System.in));
		String line = null;
		while ((line = bufferedReader.readLine()) != null) {
			//if (line.isEmpty())
			//continue;
			client.write(line);
			System.out.println("GOT=" + client.result);
		}
	}

	public void startClientDaemon() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				startClient();
			}
		}).start();
	}

	Selector selector = null;

	public Client(int port) throws IOException {
		this.port = port;
		selector = SelectorProvider.provider().openSelector();
	}

	public String write(String toSend) {
		buffer = ByteBuffer.wrap(toSend.getBytes());
		synchronized (this) {
			this.notify();
		}
		/*System.out.println("STARTING_WRITE " + " "
				+ new String(buffer.array(), 0, buffer.limit()));*/
		selector.wakeup();

		synchronized (this) {
			try {
				this.wait();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		return result;
	}

	public void startClient() {
		try {
			channel.configureBlocking(false);
			channel.register(selector, SelectionKey.OP_CONNECT);
			channel.connect(new InetSocketAddress(port));

			while (true) {
				int selected = selector.select();
				selector.select();
				System.out
						.println("SENDING_SOMETHING_NOW SELECTOR=" + selected);

				Set<SelectionKey> selectedKeys = selector.selectedKeys();
				printKeyStates(selectedKeys);
				Iterator<SelectionKey> iterator = selectedKeys.iterator();
				while (iterator.hasNext()) {
					SelectionKey key = iterator.next();
					if (!key.isValid()) {
						//						System.out.println("INVALID KEY FOUND");
						continue;
					}

					if (key.isConnectable()) {
						SocketChannel keyChannel = (SocketChannel) key
								.channel();
						keyChannel.finishConnect();
						//						System.out.println("CONNECTED TO SERVER");
						key.interestOps(SelectionKey.OP_WRITE);
					} else if (key.isReadable()) {
						System.out
								.println("STARTING READ " + key.interestOps());
						if (buffer == null) {
							buffer = ByteBuffer.allocate(5);
						}

						if (buffer.remaining() == 0) {
							ByteBuffer buffer1 = ByteBuffer.allocate(buffer
									.capacity() + 1024);
							int oldPos = buffer.position();
							buffer.flip();
							buffer1.put(buffer);
							buffer1.limit(buffer1.capacity());
							buffer1.position(oldPos);
							buffer = buffer1;
						}

						SocketChannel keyChannel = (SocketChannel) key
								.channel();
						int read = keyChannel.read(buffer);

						//Isolating the header!
						if (contentLengh == null && read > 0) {
							byte[] b = buffer.array();
							int i = 0;
							for (i = 0; i < b.length && i < MAX_HEADER_LOCATION; i++) {
								if (b[i] == NEXT_LINE) {
									/*System.out.println("HEADER="
											+ new String(b));*/
									contentLengh = Integer.parseInt(new String(
											b, 0, i));
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
							}
						}

						System.out.println("READ " + read + " "
								+ buffer.position() + " " + buffer.limit()
								+ " " + new String(buffer.array()));
						if ((read < 0 && buffer.position() > 0)
								|| buffer.position() == contentLengh) {
							if (keyChannel.isConnected()) {
								System.out.println("READ COMPLETE");
								buffer.flip();
								result = new String(buffer.array(), 0,
										buffer.limit());
								contentLengh = null;
								buffer = null;
								key.interestOps(SelectionKey.OP_WRITE);
								iterator.remove();
								synchronized (this) {
									this.notify();
								}
							}
						} else if (read < 0)
							throw new IOException("SERVER CLOSED CONNECTION");

						System.out.println("READ DONE");
					} else if (key.isWritable()) {
						//						System.out.println("WRITE START " + key.interestOps());
						SocketChannel keyChannel = (SocketChannel) key
								.channel();
						if (buffer == null)
							synchronized (this) {
								try {
									this.wait();
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
								//								System.out.println("XXX SOMETHING TO WRITE!");
							}

						if (buffer.position() == 0) {
							String header = buffer.limit() + "\n";
							//							System.out.println("SENDING HEADER " + header);
							keyChannel
									.write(ByteBuffer.wrap(header.getBytes()));
						}

						keyChannel.write(buffer);

						//						System.out.println("WRITE END " + buffer.remaining());
						if (buffer.remaining() == 0) {
							buffer = null;
							key.interestOps(SelectionKey.OP_READ);
							System.out.println("WRITE COMPLETE "
									+ key.interestOps());
							iterator.remove();
						}
					}
				}
			}
		} catch (IOException e) {
			try {
				channel.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
	}

	private void printKeyStates(Set<SelectionKey> keys) {
		if (keys != null)
			for (SelectionKey key : keys)
				if (key.isValid())
					System.out.println("KEY=" + key.isAcceptable() + " "
							+ key.isConnectable() + " " + key.isReadable()
							+ " " + key.isWritable());
		System.out.println("----");
	}
}