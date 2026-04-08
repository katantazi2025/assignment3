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
	private static final int MAX_RETRIES = 5;

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
	 * and waits for an ACK after each block before continuing. If the correct ACK
	 * does not arrive in time, or if an ACK for the wrong block arrives, the
	 * server retransmits the previous DATA packet up to a fixed retry limit. The
	 * transfer ends when the final block contains fewer than 512 bytes.
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

		byte[] fileBytes;
		try
		{
			fileBytes = Files.readAllBytes(file.toPath());
		}
		catch (IOException e)
		{
			sendError(socket, TFTPError.ACCESS_VIOLATION, "Access violation");
			return;
		}
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
			if (!sendDataWithRetry(socket, dataPacket, blockNumber))
			{
				sendError(socket, TFTPError.ILLEGAL_OPERATION,
						"Failed to receive ACK for block " + blockNumber
								+ " after " + MAX_RETRIES + " retries.");
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

		FileValidator.ValidationResult initialValidation =
				FileValidator.validateUpload(file.toPath(), 1);
		if (!initialValidation.isValid())
		{
			sendError(socket, initialValidation.errorCode(), initialValidation.errorMessage());
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
			if (opcode == TFTPPacket.OP_ERR)
			{
				return;
			}
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

			FileValidator.ValidationResult validationResult =
					FileValidator.validateUpload(file.toPath(), fileBuffer.size());
			if (!validationResult.isValid())
			{
				sendError(socket, validationResult.errorCode(), validationResult.errorMessage());
				return;
			}

			transferComplete = dataLength < DATA_SIZE;
			expectedBlockNumber++;
		}

		try
		{
			Files.write(file.toPath(), fileBuffer.toByteArray());
		}
		catch (IOException e)
		{
			sendError(socket, TFTPError.ACCESS_VIOLATION, "Access violation");
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
	 * Sends a DATA packet and retransmits it until the correct ACK arrives or the
	 * retry limit is reached.
	 *
	 * @param socket transfer socket connected to the client
	 * @param dataPacket encoded DATA packet to send
	 * @param expectedBlockNumber block number that must be acknowledged
	 * @return {@code true} if the correct ACK is eventually received, otherwise
	 *         {@code false}
	 * @throws IOException if sending or receiving packets fails
	 */
	private boolean sendDataWithRetry(DatagramSocket socket, byte[] dataPacket, int expectedBlockNumber)
			throws IOException
	{
		for (int attempt = 0; attempt < MAX_RETRIES; attempt++)
		{
			socket.send(new DatagramPacket(dataPacket, dataPacket.length));
			AckStatus ackStatus = receiveAck(socket, expectedBlockNumber);
			if (ackStatus == AckStatus.CORRECT)
			{
				return true;
			}
		}

		return false;
	}

	/**
	 * Waits for an ACK packet and checks whether it matches the expected block
	 * number.
	 *
	 * @param socket transfer socket connected to the client
	 * @param expectedBlockNumber block number that must be acknowledged
	 * @return the ACK status for the received packet
	 * @throws IOException if receiving the ACK packet fails
	 */
	private AckStatus receiveAck(DatagramSocket socket, int expectedBlockNumber) throws IOException
	{
		byte[] ackBuffer = new byte[TFTPServer.BUFSIZE];
		DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);

		try
		{
			socket.receive(ackPacket);
		}
		catch (SocketTimeoutException e)
		{
			return AckStatus.TIMEOUT;
		}

		int opcode = TFTPPacket.getOpcode(ackPacket.getData());
		if (opcode == TFTPPacket.OP_ERR)
		{
			return AckStatus.ERROR;
		}
		int blockNumber = TFTPPacket.getBlockNumber(ackPacket.getData());
		if (opcode == TFTPPacket.OP_ACK && blockNumber == expectedBlockNumber)
		{
			return AckStatus.CORRECT;
		}

		return AckStatus.INCORRECT;
	}

	/**
	 * Represents the result of waiting for an ACK packet.
	 */
	private enum AckStatus
	{
		CORRECT,
		INCORRECT,
		TIMEOUT,
		ERROR
	}
}
