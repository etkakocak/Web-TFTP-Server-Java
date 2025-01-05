import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;

public class TFTPServer {
	public static final int TFTPPORT = 69;
	public static final int BUFSIZE = 516;
	public static final String READDIR = "YOUR DIR"; // custom address at
													// your PC
	public static final String WRITEDIR = "YOUR DIR"; // custom address
													// at your PC
	// OP codes
	public static final int OP_RRQ = 1;
	public static final int OP_WRQ = 2;
	public static final int OP_DAT = 3;
	public static final int OP_ACK = 4;
	public static final int OP_ERR = 5;

	public static void main(String[] args) {
		if (args.length > 0) {
			System.err.printf("usage: java %s\n", TFTPServer.class.getCanonicalName());
			System.exit(1);
		}
		// Starting the server
		try {
			TFTPServer server = new TFTPServer();
			server.start();
		} catch (SocketException e) {
			e.printStackTrace();
		}
	}

	private void start() throws SocketException {
		byte[] buf = new byte[BUFSIZE];

		// Create socket
		DatagramSocket socket = new DatagramSocket(null);

		// Create local bind point
		SocketAddress localBindPoint = new InetSocketAddress(TFTPPORT);
		socket.bind(localBindPoint);

		System.out.printf("Listening at port %d for new requests\n", TFTPPORT);

		// Loop to handle client requests
		while (true) {

			final InetSocketAddress clientAddress = receiveFrom(socket, buf);

			// If clientAddress is null, an error occurred in receiveFrom()
			if (clientAddress == null)
				continue;

			final StringBuffer requestedFile = new StringBuffer();
			final int reqtype = ParseRQ(buf, requestedFile);

			new Thread() {
				public void run() {
					try {
						DatagramSocket sendSocket = new DatagramSocket(0);
						sendSocket.connect(clientAddress);
						System.out.printf("%s request for %s from %s using port %s\n",
								(reqtype == OP_RRQ) ? "Read" : "Write",
								requestedFile.toString(),
								clientAddress.getAddress().getHostAddress(),
								Integer.toString(clientAddress.getPort()));
						HandleRQ(sendSocket, requestedFile.toString(), reqtype);
						sendSocket.close();
					} catch (SocketException e) {
						e.printStackTrace();
					} catch (IOException e) {
						System.err.println("IOException in thread: " + e.getMessage());
						// Optionally, send an error packet to the client if appropriate
					}
				}
			}.start();
		}
	}

	/**
	 * Reads the first block of data, i.e., the request for an action (read or
	 * write).
	 * 
	 * @param socket (socket to read from)
	 * @param buf    (where to store the read data)
	 * @return socketAddress (the socket address of the client)
	 */
	private InetSocketAddress receiveFrom(DatagramSocket socket, byte[] buf) {
		try {
			// Create datagram packet
			DatagramPacket packet = new DatagramPacket(buf, buf.length);

			// Receive packet
			socket.receive(packet);

			// Get client address and port from the packet
			InetAddress address = packet.getAddress();
			int port = packet.getPort();

			return new InetSocketAddress(address, port);
		} catch (IOException e) {
			System.err.println("Error receiving packet: " + e.getMessage());
			return null;
		}
	}

	/**
	 * Parses the request in buf to retrieve the type of request and requestedFile
	 * 
	 * @param buf           (received request)
	 * @param requestedFile (name of file to read/write)
	 * @return opcode (request type: RRQ or WRQ)
	 */
	private int ParseRQ(byte[] buf, StringBuffer requestedFile) {
		ByteBuffer wrap = ByteBuffer.wrap(buf);
		short opcode = wrap.getShort(); // Correctly read the opcode as a short

		if (opcode == OP_RRQ || opcode == OP_WRQ) {
			int i = 2; // Start after the opcode
			while (buf[i] != 0) {
				requestedFile.append((char) buf[i]);
				i++;
			}
			// Skipping mode extraction for simplicity as it's not used here
		} else {
			System.err.println("Unsupported opcode: " + opcode);
			return -1; // Indicate unsupported opcode
		}

		return opcode;
	}

	/**
	 * Handles RRQ and WRQ requests
	 * 
	 * @param sendSocket    (socket used to send/receive packets)
	 * @param requestedFile (name of file to read/write)
	 * @param opcode        (RRQ or WRQ)
	 */
	private void HandleRQ(DatagramSocket sendSocket, String requestedFile, int opcode) {
		try {
			if (opcode == OP_RRQ) {
				String filePath = sanitizeFilePath(READDIR, requestedFile);
				File file = new File(filePath);
				if (!file.exists()) {
					send_ERR(sendSocket, (short) 1, "File not found.");
					return;
				}
				boolean success = send_DATA_receive_ACK(sendSocket, filePath);
				if (!success) {
					System.err.println("Failed to complete the read request for " + requestedFile);
				}
			} else if (opcode == OP_WRQ) {
				// Ensuring the file path is within the expected directory
				requestedFile = sanitizeFilePath(WRITEDIR, requestedFile);
				// Sending an initial ACK for the WRQ to begin file transfer
				send_ACK(sendSocket, (short) 0);
				// Attempting to receive file data from the client and send ACK
				boolean success = receive_DATA_send_ACK(sendSocket, requestedFile);
				if (!success) {
					System.err.println("Failed to complete the write request for " + requestedFile);
				}
			} else {
				// Handling undefined opcodes by sending an appropriate error packet
				System.err.println("Received an invalid request opcode: " + opcode);
				send_ERR(sendSocket, (short) 4, "Illegal TFTP operation.");
			}
		} catch (IOException e) {
			// Logging and sending an error packet for IO exceptions
			System.err.println("IO Exception in HandleRQ: " + e.getMessage());
			send_ERR(sendSocket, (short) 0, "Server error.");
		} catch (SecurityException se) {
			// Logging and sending an error packet for security exceptions
			System.err.println("Security Exception: " + se.getMessage());
			send_ERR(sendSocket, (short) 2, "Access violation.");
		}
	}

	private void send_ACK(DatagramSocket socket, short blockNumber) throws IOException {
		ByteBuffer ackBuffer = ByteBuffer.allocate(4);
		ackBuffer.putShort((short) OP_ACK);
		ackBuffer.putShort(blockNumber);
		DatagramPacket ackPacket = new DatagramPacket(ackBuffer.array(), ackBuffer.position(),
				socket.getRemoteSocketAddress());
		socket.send(ackPacket);
	}

	private String sanitizeFilePath(String rootDir, String requestedFile) throws IOException {
		String filePath = new java.io.File(rootDir, requestedFile).getCanonicalPath();
		if (!filePath.startsWith(new java.io.File(rootDir).getCanonicalPath())) {
			throw new SecurityException("Invalid file path.");
		}
		return filePath;
	}

	private boolean send_DATA_receive_ACK(DatagramSocket sendSocket, String requestedFile) {
		try (FileInputStream fis = new FileInputStream(requestedFile)) {
			byte[] readBuffer = new byte[512];
			int blockNumber = 0;
			boolean transferComplete = false;
			int retries, MAX_RETRIES = 3;

			while (!transferComplete) {
				int bytesRead = fis.read(readBuffer);
				if (bytesRead == -1) {
					bytesRead = 0; // If no bytes read, we still need to send a final packet
					transferComplete = true;
				}

				ByteBuffer buffer = ByteBuffer.allocate(4 + bytesRead);
				buffer.putShort((short) OP_DAT);
				buffer.putShort((short) (++blockNumber));
				buffer.put(readBuffer, 0, bytesRead);

				DatagramPacket dataPacket = new DatagramPacket(buffer.array(), buffer.position(),
						sendSocket.getRemoteSocketAddress());
				retries = 0;

				while (retries < MAX_RETRIES) {
					try {
						sendSocket.send(dataPacket);
						sendSocket.setSoTimeout(1000); // Set timeout for ACK reception

						DatagramPacket ackPacket = new DatagramPacket(new byte[BUFSIZE], BUFSIZE);
						sendSocket.receive(ackPacket);
						break; // ACK received and validated, break the retry loop
					} catch (SocketTimeoutException ste) {
						retries++;
						System.err.println("ACK not received, retrying...");
					}
				}

				if (retries == MAX_RETRIES) {
					System.err.println("Failed to receive ACK after " + MAX_RETRIES + " retries.");
					return false;
				}

				if (bytesRead < 512)
					transferComplete = true; // Last block of data
			}
			return true; // File successfully sent
		} catch (IOException e) {
			System.err.println("Error: " + e.getMessage());
			return false;
		}
	}

	private boolean receive_DATA_send_ACK(DatagramSocket sendSocket, String requestedFile) {
		try {
			FileOutputStream fos = new FileOutputStream(requestedFile);
			int expectedBlockNumber = 0;
			boolean transferComplete = false;

			while (!transferComplete) {
				DatagramPacket receivePacket = new DatagramPacket(new byte[BUFSIZE], BUFSIZE);
				sendSocket.receive(receivePacket);
				ByteBuffer buffer = ByteBuffer.wrap(receivePacket.getData());
				short opcode = buffer.getShort();
				short blockNumber = buffer.getShort();

				if (opcode != OP_DAT || blockNumber != ++expectedBlockNumber) {
					System.err.println("Unexpected packet or block number.");
					fos.close();
					return false;
				}

				if (receivePacket.getLength() < 516) { // Check for last packet
					transferComplete = true;
				}

				fos.write(buffer.array(), 4, receivePacket.getLength() - 4);

				ByteBuffer ackBuffer = ByteBuffer.allocate(4);
				ackBuffer.putShort((short) OP_ACK);
				ackBuffer.putShort(blockNumber);
				DatagramPacket ackPacket = new DatagramPacket(ackBuffer.array(), ackBuffer.position(),
						receivePacket.getSocketAddress());
				sendSocket.send(ackPacket);
			}
			fos.close();
			return true;
		} catch (IOException e) {
			System.err.println("Error: " + e.getMessage());
			return false;
		}
	}

	private void send_ERR(DatagramSocket socket, short errorCode, String errorMessage) {
		try {
			ByteBuffer byteBuffer = ByteBuffer.allocate(4 + errorMessage.getBytes().length + 1);
			byteBuffer.putShort((short) OP_ERR); // Error opcode
			byteBuffer.putShort(errorCode); // Error code
			byteBuffer.put(errorMessage.getBytes());
			byteBuffer.put((byte) 0); // Null terminator
			DatagramPacket errorPacket = new DatagramPacket(byteBuffer.array(), byteBuffer.position(),
					socket.getRemoteSocketAddress());
			socket.send(errorPacket);
		} catch (IOException e) {
			System.err.println("Failed to send error packet: " + e.getMessage());
		}
	}

}
