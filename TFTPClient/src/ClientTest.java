import java.io.IOException;
import java.util.Scanner;

public class ClientTest {

	public static void main(String[] args) throws InterruptedException, IOException {
		Client client = new Client();

		Scanner scan = new Scanner(System.in);

		System.out.println("Enter 1 to use default values or use any other number for manual inputs");
		if (scan.nextInt() == 1) {
			
			System.out.println("Enter the ip address to connect to, example: 192.168.0.8");
			client.setAddress(scan.next());

			System.out.println("Enter 1 for read request or enter any other number for write request");
			if (scan.nextInt() == 1) {
				client.sendRRQ();
			} else {
				System.out.println("sending write request");
				client.sendWRQ();
			}

		} // end of default values
		else {//start of manual inputs
			// sets the file name
			System.out
					.println("Enter the name of the file you want to send/receive, include file extension  like .txt");
			client.setFileName(scan.next());

			System.out.println("Enter octet or netascii for the mode you wish to use");
			client.setMode(scan.next().toLowerCase());

			System.out.println("Enter the ip address to connect to, example: 192.168.0.8");
			client.setAddress(scan.next());

			System.out.println("Enter 1 for read request or enter any other number for write request");
			if (scan.nextInt() == 1) {
				client.sendRRQ();
			} else {
				client.sendWRQ();
			}
		} // end of manual inputs

	}

}
