package assignment3;

import java.io.File;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;

import javax.xml.crypto.Data;

public class TFTPRequestHandler implements Runnable
{
	private final InetSocketAddress clientAddress;
	private final byte[] requestData;
	private final String readDir;
	private final String writeDir;

	public TFTPRequestHandler(InetSocketAddress clientAddress, byte[] requestData, String readDir, String writeDir)
	{
		this.clientAddress = clientAddress;
		this.requestData = requestData;
		this.readDir = readDir; // Directory for reading files
		this.writeDir = writeDir; // Directory for writing files
	}

	@Override
	public void run()
	{
		try(DatagramSocket sendSocket = new DatagramSocket(0)) // TFTP default port
		{
			DatagramSocket transferSocket = new DatagramSocket(0);
			sendSocket.connect(clientAddress);

			// Use TFTPPacket to parse the request and determine RRQ or WRQ.
			int opcode = TFTPPacket.getOpcode(requestData);
			String filename = TFTPPacket.getFilename(requestData);
			String mode = TFTPPacket.getMode(requestData);

			System.out.println("Received request from " + clientAddress + ": " + (opcode == TFTPPacket.OP_RRQ ? "RRQ" : "WRQ") + " for file: " + filename + " in mode: " + mode);
			if (!mode.equalsIgnoreCase("octet"))
			{
				System.out.println("Unsupported mode: " + mode);
				// Send error packet for unsupported mode
				TFTPPacket errorPacket = TFTPPacket.createErrorPacket(TFTPPacket.ERR_UNSUPPORTED_MODE, "Only octet mode is supported.");
				sendSocket.send(errorPacket.toDatagramPacket(clientAddress));
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
				System.out.println("Invalid opcode: " + opcode);
				// Send error packet for invalid opcode
				TFTPPacket errorPacket = TFTPPacket.createErrorPacket(TFTPPacket.ERR_ILLEGAL_OPERATION, "Invalid request type.");
				sendSocket.send(errorPacket.toDatagramPacket(clientAddress));
			}

			// Step 5:
			// If RRQ, call handleReadRequest().
			// If WRQ, call handleWriteRequest().
			// Otherwise, send an error packet.

			transferSocket.close();
		}
		catch (SocketException e)
		{
			e.printStackTrace();
		}
	}

	public void handleReadRequest(DatagramSocket socket, String filename)
	{
		
		// Open the requested file from READDIR.
		// Send DATA packets.
		// Wait for ACK packets.
		File file = new File(readDir, filename);
		if (!file.exists()) {
			TFTPPacket.sendError(sendSocket, TFTPError.ERR_FILE_NOT_FOUND, "File not found:");
			return;

	}
	System.out.println("RRQ recieved for:" + file.getAbsolutePath())

	}


	private void handleWriteRequest(DatagramSocket socket, String filename)
	{
		
		// Send ACK block 0.
		// Receive DATA packets.
		// Write the uploaded file into WRITEDIR.
		File file = new File(writeDir, filename);
		if (file.exists()) {
			TFTPPacket.sendError(sendSocket, TFTPError.ERR_FILE_ALREADY_EXISTS, "File already exists:");
			return;
	}
	String validationError = FileValidator.validateFilename(filename);
	if (validationError != null) {
		TFTPPacket.sendError(sendSocket, TFTPError.ERR_ILLEGAL_FILENAME, validationError);
		return;
	}
	System.out.println("WRQ recieved for:" + file.getAbsolutePath());
}
