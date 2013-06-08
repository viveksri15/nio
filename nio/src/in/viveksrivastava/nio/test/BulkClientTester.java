package in.viveksrivastava.nio.test;

import in.viveksrivastava.nio.Client;

import java.io.IOException;
import java.util.ArrayList;

public class BulkClientTester {

	public static void main(String[] args) throws IOException {

		int port = Integer.parseInt(args[0]);

		int numberOfClient = 10;
		ArrayList<Client> clients = new ArrayList<Client>();
		for (int i = 0; i < numberOfClient; i++) {
			Client client = new Client(port);
			client.startClientDaemon();
			clients.add(client);
		}

		for (int i = 0; i < 1000; i++) {
			int j = 0;
			for (Client client : clients) {
				String toSend = "TEST_" + j + "_" + i;
				String result = client.write(toSend);
				System.out.println(toSend + "=" + " " + result.length() + ","
						+ result);
				j++;
			}
		}
	}
}