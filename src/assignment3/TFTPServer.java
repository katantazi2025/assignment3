package assignment3;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;

public class TFTPServer
{
	public static final int TFTPPORT = 4970;
	public static final int BUFSIZE = 516;
	public static final String READDIR =
			"/Users/harrisonkatantazi/Desktop/1DV701-assignment3/read/";
	public static final String WRITEDIR =
			"/Users/harrisonkatantazi/Desktop/1DV701-assignment3/write/";

	public static void main(String[] args)
	{
		if (args.length > 0)
		{
			System.err.printf("usage: java %s%n", TFTPServer.class.getCanonicalName());
			System.exit(1);
		}

		try
		{
			new TFTPServer().start();
		}
		catch (SocketException e)
		{
			e.printStackTrace();
		}
	}

	private void start() throws SocketException
	{
		byte[] buf = new byte[BUFSIZE];
		DatagramSocket socket = new DatagramSocket(null);
		SocketAddress localBindPoint = new InetSocketAddress(TFTPPORT);
		socket.bind(localBindPoint);

		System.out.printf("Listening at port %d for new requests%n", TFTPPORT);

		while (true)
		{
			// Step 1:
			// Receive the first request packet from a client.

			// Step 2:
			// Extract the client address and copy the request bytes.

			// Step 3:
			// Start a new TFTPRequestHandler thread for that client.
		}
	}
}
