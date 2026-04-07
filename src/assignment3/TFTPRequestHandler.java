package assignment3;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.file.Files;

/**
 * Handles one TFTP client request in a separate thread.
 *
 * This class receives the first request packet from {@code TFTPServer}, parses
 * the request information, and decides whether the client wants to read or
 * write a file. The actual transfer logic is still scaffolded and can be built
 * step by step.
 */
public class TFTPRequestHandler implements Runnable
{
	private static final int DATA_SIZE = TFTPServer.BUFSIZE - 4;
	private static final int SOCKET_TIMEOUT_MS = 3000;

	private final InetSocketAddress clientAddress;
	private final byte[] requestData;
	private final String readDir;
	private final String writeDir;

	/**
	 * Creates a handler for one client request.
	 *
	 * @param clientAddress address and port of the client
	 * @param requestData the first RRQ or WRQ packet received from the client
	 */
	public TFTPRequestHandler(InetSocketAddress clientAddress, byte[] requestData)
	{
		this.clientAddress = clientAddress;
		this.requestData = requestData;
		this.readDir = TFTPServer.READDIR;
		this.writeDir = TFTPServer.WRITEDIR;
	}

	/**
	 * Opens a transfer socket, parses the request packet, and dispatches the
	 * request to either the RRQ or WRQ handler.
	 */
	@Override
	public void run()
	{
		try (DatagramSocket transferSocket = new DatagramSocket(0))
		{
			transferSocket.connect(clientAddress);
			transferSocket.setSoTimeout(SOCKET_TIMEOUT_MS);

			int opcode = TFTPPacket.getOpcode(requestData);
			String filename = TFTPPacket.extractFilename(requestData);
			String mode = TFTPPacket.extractMode(requestData);

			System.out.println("Received request from " + clientAddress
					+ ": " + (opcode == TFTPPacket.OP_RRQ ? "RRQ" : "WRQ")
					+ " for file: " + filename + " in mode: " + mode);

			if (!mode.equalsIgnoreCase("octet"))
			{
				sendError(transferSocket, TFTPError.ILLEGAL_OPERATION,
						"Only octet mode is supported.");
				return;
			}

			if (opcode == TFTPPacket.OP_RRQ)
			{
				handleReadRequest(transferSocket, filename);
			}
			else if (opcode == TFTPPacket.OP_WRQ)
			{
				handleWriteRequest(transferSocket, filename);
			}
			else
			{
				sendError(transferSocket, TFTPError.ILLEGAL_OPERATION,
						"Invalid request type.");
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * Handles a read request from the client.
	 *
	 * This implementation validates the file, sends it in 512-byte DATA blocks,
	 * and waits for an ACK after each block before continuing. The transfer ends
	 * when the final block contains fewer than 512 bytes.
	 *
	 * @param socket transfer socket connected to the client
	 * @param filename name of the file requested by the client
	 * @throws IOException if sending an error packet fails
	 */
	public void handleReadRequest(DatagramSocket socket, String filename) throws IOException
	{
		File file = new File(readDir, filename);
		if (!file.exists())
		{
			sendError(socket, TFTPError.FILE_NOT_FOUND, "File not found: " + filename);
			return;
		}

		if (file.isDirectory())
		{
			sendError(socket, TFTPError.ACCESS_VIOLATION, "Requested path is a directory.");
			return;
		}

		byte[] fileBytes = Files.readAllBytes(file.toPath());
		System.out.println("RRQ received for: " + file.getAbsolutePath());

		int blockNumber = 1;
		int offset = 0;

		do
		{
			int bytesRemaining = fileBytes.length - offset;
			int chunkLength = Math.min(bytesRemaining, DATA_SIZE);
			byte[] chunk = new byte[chunkLength];
			System.arraycopy(fileBytes, offset, chunk, 0, chunkLength);

			byte[] dataPacket = TFTPPacket.createDataPacket(blockNumber, chunk);
			socket.send(new DatagramPacket(dataPacket, dataPacket.length));

			if (!receiveAck(socket, blockNumber))
			{
				sendError(socket, TFTPError.ILLEGAL_OPERATION,
						"Did not receive expected ACK for block " + blockNumber + ".");
				return;
			}

			offset += chunkLength;
			blockNumber++;
		}
		while (offset < fileBytes.length);
	}

	/**
	 * Handles a write request from the client.
	 *
	 * At this stage the method only validates whether the file may be created and
	 * prints a message. Receiving DATA packets and writing the file can be added
	 * next.
	 *
	 * @param socket transfer socket connected to the client
	 * @param filename name of the file the client wants to upload
	 * @throws IOException if sending an error packet fails
	 */
	private void handleWriteRequest(DatagramSocket socket, String filename) throws IOException
	{
		File file = new File(writeDir, filename);
		if (file.exists())
		{
			sendError(socket, TFTPError.FILE_ALREADY_EXISTS,
					"File already exists: " + filename);
			return;
		}

		if (!FileValidator.isValidForUpload(file.toPath(), 0))
		{
			sendError(socket, TFTPError.ILLEGAL_OPERATION,
					"Upload rejected by file validator.");
			return;
		}

		System.out.println("WRQ received for: " + file.getAbsolutePath());
	}

	/**
	 * Builds and sends a TFTP ERROR packet to the connected client.
	 *
	 * @param socket transfer socket connected to the client
	 * @param errorCode TFTP error code
	 * @param message human-readable error message
	 * @throws IOException if sending the packet fails
	 */
	private void sendError(DatagramSocket socket, int errorCode, String message) throws IOException
	{
		byte[] errorData = TFTPPacket.createErrorPacket(errorCode, message);
		DatagramPacket errorPacket = new DatagramPacket(errorData, errorData.length);
		socket.send(errorPacket);
	}

	/**
	 * Waits for an ACK packet that matches the expected block number.
	 *
	 * @param socket transfer socket connected to the client
	 * @param expectedBlockNumber block number that must be acknowledged
	 * @return {@code true} if the correct ACK is received, otherwise {@code false}
	 * @throws IOException if receiving the ACK packet fails
	 */
	private boolean receiveAck(DatagramSocket socket, int expectedBlockNumber) throws IOException
	{
		byte[] ackBuffer = new byte[TFTPServer.BUFSIZE];
		DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);

		try
		{
			socket.receive(ackPacket);
		}
		catch (SocketTimeoutException e)
		{
			return false;
		}

		int opcode = TFTPPacket.getOpcode(ackPacket.getData());
		int blockNumber = TFTPPacket.getBlockNumber(ackPacket.getData());
		return opcode == TFTPPacket.OP_ACK && blockNumber == expectedBlockNumber;
	}
}
