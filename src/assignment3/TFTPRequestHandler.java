package assignment3;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;

public class TFTPRequestHandler implements Runnable
{
	private final InetSocketAddress clientAddress;
	private final byte[] requestData;

	public TFTPRequestHandler(InetSocketAddress clientAddress, byte[] requestData)
	{
		this.clientAddress = clientAddress;
		this.requestData = requestData;
	}

	@Override
	public void run()
	{
		try
		{
			DatagramSocket transferSocket = new DatagramSocket(0);
			transferSocket.connect(clientAddress);

			// Step 4:
			// Use TFTPPacket to parse the request and determine RRQ or WRQ.

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
		// Step 6:
		// Open the requested file from READDIR.
		// Send DATA packets.
		// Wait for ACK packets.
	}

	public void handleWriteRequest(DatagramSocket socket, String filename)
	{
		// Step 7:
		// Send ACK block 0.
		// Receive DATA packets.
		// Write the uploaded file into WRITEDIR.
	}
}
