import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.commons.net.io.*;

public class Client {
	private DatagramSocket socket;
	private DatagramPacket sendPacket;// sends packet to server
	private DatagramPacket incoming;
	private static byte[] data = new byte[516];// information that is sent through to the server
	private byte[] incData = new byte[512]; // set packet up, from incoming data
	private final static int size = 512;
	private static int bNum = 0;
	private int expectedBNum = 0; // should always be one ahead except for the start
	private byte[] request;
	private static String fName = "placeholder.txt";// name of file to be read/written
	private String mode = "netascii";// default mode is set to octet
	private boolean received = false;
	private boolean terminate = false;

	static File file = new File(fName);
	FileInputStream fStream = null;

	private String inetAddress;
	

	private int serverPort = 69;
	
	private static int errorCode;

	// Opcodes
	static final byte[] opRRQ = {(byte) 0, (byte) 1};
	static final byte opWRQ = 2;
	static final byte opDATA = 3;
	static final byte[] opACK = {(byte) 0,(byte) 4};
	static final byte opERROR = 5;

	// timeout variable
	private final static int timeout = 3000; // use socket.setSOTimeout(timeout)
	private final static int waitTime = 500;
	private static int tries = 10;
	private final static int numberTries = 10;

	
	private final static int bSizeMax = 65535;

	public Client() {

		try // create DatagramSocket
		{
			socket = new DatagramSocket();

		} // end try
		catch (SocketException socketException) {
			socketException.printStackTrace();
			System.exit(1);
		} // end catch
	}

	// sends the initial read request to the server
	public void sendRRQ() throws InterruptedException, IOException {
		System.out.println("init receiving " + fName + "...");
		sendRequestNew("read", fName, mode);

		// receive first data
		File newfile = new File(fName);
		FileOutputStream receivedFileOctet = new FileOutputStream(newfile);
		FromNetASCIIOutputStream receivedFileNetascii = null;

		boolean done = false;
		int block = 1;
		int times = 0;
		int timeReceive = 30000;
		socket.setSoTimeout(timeReceive);

		// loop through this
		while (!done) {
			byte [] receive = new byte[size + 4];

			sendPacket = new DatagramPacket(receive, receive.length,  InetAddress.getByName(inetAddress), serverPort);
			socket.receive(sendPacket);
			
			incData = sendPacket.getData();
			
			serverPort = sendPacket.getPort();

			if (incData[1] == 5) { // error

				System.out.println("ERROR: " + new String(Arrays.copyOfRange(receive, 4, receive.length)));
				System.out.println("error lol");
				done = true;
				socket.close();

			} else if (incData[1] == 3) { // data
				if (!newfile.exists()) {
					newfile.createNewFile();
				}

				byte[] expectedBlock = getByteArray(block);
				byte[] datareceived = Arrays.copyOfRange(receive, 4, sendPacket.getLength());
			
			 if (expectedBlock[0] == incData[2] && expectedBlock[1] == incData[3]) {
					if (sendPacket.getLength() >= size) { // file still transfering
						if(block % 10000 == 0) {
						System.out.println("block: " + block);
						}
						if (mode.equals("octet")) {
							receivedFileOctet.write(datareceived);
						} else if (mode.equals("netascii")) {
							receivedFileNetascii = new FromNetASCIIOutputStream(receivedFileOctet);
							receivedFileNetascii.write(datareceived);
						}
						
						sendAck(block);
						
					} else { // file is done transfering
						System.out.println("(last) block: " + block);

						if (mode.equals("octet")) {
							receivedFileOctet.write(datareceived);
							receivedFileOctet.close();

						} // octet data
						else if (mode.equals("netascii")) {
							receivedFileNetascii = new FromNetASCIIOutputStream(receivedFileOctet);
							receivedFileNetascii.write(datareceived);
							receivedFileNetascii.close();
						} // was netascii data

						// send ack
						sendAck(block);
						System.out.println("We made it");
						done = true;
					} // finished reading the file

					block++;

					if (block > bSizeMax) { // block is of size 16 bits
						block = 0; // reinit if bigger that max size
					} // block > 65535
					
				} else if(expectedBlock[0] != incData[2] && expectedBlock[1] != incData[3]){
					if (times < numberTries) {
						Thread.sleep(waitTime);
						sendAck(block - 1);
						times++;
					} // times < numberTries
					else {
						sendError(4);
						System.out.println("tries maxed");
						socket.close();
					} // tries were  = 10
				} else {
					sendError(4);
				} // failed all inc data checks
			} // op code was data op code
		} //while not done
		receivedFileOctet.close();

	}

	public void sendWRQ() throws InterruptedException {
		int j = 0;
		int k = 0;
		int i = 2;
		
		byte[] fileBytes = stringToByte(fName);
		byte[] modeBytes = stringToByte(mode);

		int length = 4 + fileBytes.length + modeBytes.length;
		
		byte[] blockNum = getByteArray(bNum);
		
		request = new byte[length];
		
		request[0] = 0;
		request[1] = 2;
		// sets a request packet up with the required amount of space
		

		while (i < request.length) {
			// fills data[] up until end of string, including the termination byte
			if (j < fileBytes.length) {

				request[i] = fileBytes[j];
				j++;
				i++;

			}
			// if fileName is filled do this
			else {
				request[i] = 0; // byte that takes the place after filename and mode
								// fills to end of packet
				i++;

				while (k < modeBytes.length) {
					request[i] = modeBytes[k];

					k++;
					i++;
				} // end of while
				

			} // end of else
		} // end of while request loop

		try {
			sendRequest(request);
			receiveAck(blockNum);
			sendDATA();

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} // end of catch

	}// end of method

	// get packets for read request
	public void Receive() throws IOException, InterruptedException {
		int count = 0;
		// used to check the incoming block number
		byte[] blockNum = getByteArray(expectedBNum);

		incoming = new DatagramPacket(data, data.length);
		socket.setSoTimeout(timeout);
		while (received != true && count < tries) {
			try {
				socket.receive(incoming); // wait for packet

				received = true;
			} catch (IOException e) {
				Thread.sleep(3000);
				send();
				count++;
			}
		} // end of count while
		received = false;

		incData = incoming.getData();

		if (incData[2] == blockNum[0] && incData[3] == blockNum[1]) {
			// do nothing
		} else {
			// call the send ack method and send the expected block number to the server
		}
	}// end of receive

	public void send() throws UnknownHostException {

		byte[] blockNum = getByteArray(bNum);
		data[2] = blockNum[0];
		data[3] = blockNum[1];
		// placeholder packet to send
		sendPacket = new DatagramPacket(data, data.length, InetAddress.getByName(inetAddress), serverPort);

		try {
			socket.send(sendPacket);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}// end of send

	////////////////////////////////// Send Packet Methods
	////////////////////////////////// //////////////////////////////
	public void sendDATA() throws IOException, InterruptedException {
		fStream = new FileInputStream(file);
		ToNetASCIIInputStream asciiInput = new ToNetASCIIInputStream(fStream);
		byte[] blockNum = getByteArray(bNum);
		// use a file input stream and fill the bytes of the packet with data from the
		// file
		int i = 4;
		int fileInt = 0;
		data[0] = 0;
		data[1] = 3;
		data[2] = blockNum[0];
		data[3] = blockNum[1];

		if (mode.contains("octet")) {
			while ((fileInt = fStream.read()) != -1) {

				data[i] = (byte) (fileInt & 0xff);
				i++;
				if (i == 516) {
					send();
					receiveAck(blockNum);
					blockNum = getByteArray(bNum);
					data[2] = blockNum[0];
					data[3] = blockNum[1];

					i = 4;

				} // end of full packet

			} // end of while
			
			data[i] = (byte) (fileInt & 0xff);
			byte[] fPack = new byte[i];
			i = 0;
			while(i < fPack.length) {
				fPack[i] = data[i];
				i++;
			} //fills last packet
			sendPacket = new DatagramPacket(fPack, fPack.length, InetAddress.getByName(inetAddress), serverPort);
			socket.send(sendPacket);
			receiveAck(blockNum);
			
			
			
		} // end of check of octect mode

		else {
			while ((fileInt = asciiInput.read()) != -1) {

				data[i] = (byte) (fileInt & 0xff);
				i++;
				if (i == 516) {
					send();
					receiveAck(blockNum);
					blockNum = getByteArray(bNum);
					data[2] = blockNum[0];
					data[3] = blockNum[1];

					i = 4;

				} // end of full packet

			} // end of while
			
			data[i] = (byte) (fileInt & 0xff);
			byte[] fPack = new byte[i];
			i = 0;
			while(i < fPack.length) {
				fPack[i] = data[i];
				i++;
			} //fills last packet
			sendPacket = new DatagramPacket(fPack, fPack.length, InetAddress.getByName(inetAddress), serverPort);
			socket.send(sendPacket);
			receiveAck(blockNum);

		} // end of netascii mode

	}// end of method

	public void sendError(int errorCode) throws IOException {
		byte[] error = ErrorPacket(errorCode);
		DatagramPacket packet = new DatagramPacket(error, error.length, InetAddress.getLocalHost(),
				serverPort);
		try {
			socket.send(packet);

		} catch (IOException e) {
			e.printStackTrace();
		}

	}// end of method

	private void sendAck(int block) throws IOException {
		byte[] ACK = AckPacket(block);
		// Gets new port and sends the ack
		try {
			DatagramPacket ack = new DatagramPacket(ACK, ACK.length, InetAddress.getByName(inetAddress), serverPort);
			socket.send(ack);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}// end of sendAck

	////////////////////////////////// Conversion Methods
	////////////////////////////////// //////////////////////////////
	/*
	 * Converts an int to a byte array of size 2
	 */
	public static byte[] getByteArray(int integer) {
		byte[] array = new byte[2];
		array[1] = (byte) (integer & 0xFF);
		array[0] = (byte) ((integer >> 8) & 0xFF);
		return array;
	}

	/*
	 * If we have a method for int to byte, might as well have byte to int
	 */
	public static int getInt(byte[] array) {
		int integer;
		integer = ((array[0] & 0xff) << 8) | (array[1] & 0xff);
		return integer;
	}

	// gets a string and converts to a byte array
	public byte[] stringToByte(String chara) {
		
		byte[] conversion = chara.getBytes(StandardCharsets.US_ASCII);

		return conversion;

	}// end do of stringToByte
	
	public void sendRequestNew(String type, String filename, String mode) throws IOException {
		byte[] request = RequestPacket(type, filename, mode);

		try {
			sendPacket = new DatagramPacket(request, request.length, InetAddress.getByName(inetAddress),
					serverPort);
			socket.send(sendPacket);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void sendRequest(byte[] a) throws IOException {

		sendPacket = new DatagramPacket(a, a.length, InetAddress.getByName(inetAddress), serverPort);
		socket.send(sendPacket);
	}

	
	////////////////////////////////// PACKETS //////////////////////////////
	public static byte[] ErrorPacket(int errorCode) throws IOException {
		ByteArrayOutputStream outputPacket = new ByteArrayOutputStream();

		String errorMessage = null;

		switch (errorCode) {
		case 0:
			errorMessage = "Undefined";
			break;
		case 1:
			errorMessage = "File not found";
			break;
		case 2:
			errorMessage = "Access Violation.";
			break;
		case 3:
			errorMessage = "Disk full or allocation exceeded.";
			break;
		case 4:
			errorMessage = "Bad TFTP operation.";
			break;
		case 5:
			errorMessage = "Unknown transfer ID";
			break;
		case 6:
			errorMessage = "File already exists.";
			break;
		case 7:
			errorMessage = "Not a user.";
			break;
		}

		outputPacket.write(opERROR);
		outputPacket.write(getByteArray(errorCode));
		outputPacket.write(errorMessage.getBytes());
		outputPacket.write(opERROR);
		outputPacket.write((byte) 0);

		return outputPacket.toByteArray();
	}

	public static byte[] AckPacket(int block) throws IOException {
		ByteArrayOutputStream outputPacket = new ByteArrayOutputStream();

		outputPacket.write(opACK);
		outputPacket.write(getByteArray(block));

		return outputPacket.toByteArray();
	}

	public static byte[] ReadPacket(String filename, String mode) throws IOException {
		ByteArrayOutputStream outputPacket = new ByteArrayOutputStream();

		outputPacket.write(opRRQ);
		outputPacket.write(filename.getBytes());
		outputPacket.write((byte) 0);
		outputPacket.write(mode.getBytes());
		outputPacket.write((byte) 0);

		return outputPacket.toByteArray();
	}

	public static byte[] WritePacket(String filename, String mode) throws IOException {
		ByteArrayOutputStream outputPacket = new ByteArrayOutputStream();

		outputPacket.write(opWRQ);
		outputPacket.write(filename.getBytes());
		outputPacket.write((byte) 0);
		outputPacket.write(mode.getBytes());
		outputPacket.write((byte) 0);

		return outputPacket.toByteArray();

	}

	public static byte[] DataPacket(int block, byte[] data) throws IOException {
		ByteArrayOutputStream outputPacket = new ByteArrayOutputStream();

		outputPacket.write(opDATA);
		outputPacket.write(getByteArray(bNum));
		outputPacket.write(data);

		return outputPacket.toByteArray();

	}
	
	public static byte[] RequestPacket(String type, String filename, String mode) throws IOException {
		ByteArrayOutputStream outputPacket = new ByteArrayOutputStream();

		if (type == "read") {
			outputPacket.write(opRRQ);
		} else if (type == "write") {
			outputPacket.write(opWRQ);
		} else {
			System.out.println("Error in request type");
		}
		outputPacket.write(filename.getBytes());
		outputPacket.write((byte) 0);
		outputPacket.write(mode.getBytes());
		outputPacket.write((byte) 0);

		return outputPacket.toByteArray();
	}

	public void receiveAck(byte[] block) throws InterruptedException, UnknownHostException, SocketException {
		byte[] ack = new byte[4];
		incoming = new DatagramPacket(ack, ack.length);

		socket.setSoTimeout(timeout);
		while (!received && tries > 0) {
			try {
				socket.receive(incoming);

				byte[] obtained = incoming.getData();
				if (block[0] == obtained[2] && block[1] == obtained[3]) {
					tries = 10;
					serverPort = incoming.getPort();
					bNum++;
					if(bNum == 65535) {
						bNum = 0;
					}//resets the bNum
					break;
				}
				else {
					tries--;
					send();
				}//end of incorrect packet check
			} catch (IOException e) {

				e.printStackTrace();

			}//end of catch
		} // end of while not received or count = 10
		if (tries == 0)
			terminate = true;

	}
	
	public void setAddress(String addr) {
		inetAddress = addr;
	}

	public void setFileName(String name) {
		fName = name;
	}
	
	public void setMode(String md) {
		mode = md;
	}
}

