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

			StringBuffer requestedFile = new StringBuffer();
			StringBuffer transferMode = new StringBuffer();
			int opcode = ParseRQ(requestData, requestedFile, transferMode);
			String filename = requestedFile.toString();
			String mode = transferMode.toString();

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
	 * Parses the initial RRQ or WRQ packet received from the client.
	 *
	 * This method mirrors the role of the original starter-code {@code ParseRQ()}
	 * method, but it delegates the low-level byte parsing to {@code TFTPPacket}.
	 *
	 * @param packetData raw request packet bytes
	 * @param requestedFile output buffer for the requested filename
	 * @param transferMode output buffer for the transfer mode
	 * @return the parsed opcode
	 */
	private int ParseRQ(byte[] packetData, StringBuffer requestedFile, StringBuffer transferMode)
	{
		int opcode = TFTPPacket.getOpcode(packetData);
		requestedFile.append(TFTPPacket.extractFilename(packetData));
		transferMode.append(TFTPPacket.extractMode(packetData));
		return opcode;
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
	 * This implementation acknowledges the initial WRQ with ACK block 0, receives
	 * DATA packets from the client, acknowledges each block, and writes the
	 * uploaded file to disk when the transfer is complete.
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

		byte[] ackZero = TFTPPacket.createAckPacket(0);
		socket.send(new DatagramPacket(ackZero, ackZero.length));

		java.io.ByteArrayOutputStream fileBuffer = new java.io.ByteArrayOutputStream();
		int expectedBlockNumber = 1;
		boolean transferComplete = false;

		while (!transferComplete)
		{
			byte[] dataBuffer = new byte[TFTPServer.BUFSIZE];
			DatagramPacket dataPacket = new DatagramPacket(dataBuffer, dataBuffer.length);

			try
			{
				socket.receive(dataPacket);
			}
			catch (SocketTimeoutException e)
			{
				sendError(socket, TFTPError.ILLEGAL_OPERATION,
						"Timed out while waiting for DATA block " + expectedBlockNumber + ".");
				return;
			}

			int opcode = TFTPPacket.getOpcode(dataPacket.getData());
			int blockNumber = TFTPPacket.getBlockNumber(dataPacket.getData());
			if (opcode != TFTPPacket.OP_DAT || blockNumber != expectedBlockNumber)
			{
				sendError(socket, TFTPError.ILLEGAL_OPERATION,
						"Unexpected DATA packet received.");
				return;
			}

			int dataLength = dataPacket.getLength() - 4;
			if (dataLength > 0)
			{
				fileBuffer.write(dataPacket.getData(), 4, dataLength);
			}

			byte[] ackPacket = TFTPPacket.createAckPacket(blockNumber);
			socket.send(new DatagramPacket(ackPacket, ackPacket.length));

			transferComplete = dataLength < DATA_SIZE;
			expectedBlockNumber++;
		}

		Files.write(file.toPath(), fileBuffer.toByteArray());
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
