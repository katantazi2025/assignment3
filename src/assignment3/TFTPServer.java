package assignment3;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;

/**
 * Main server class for the TFTP application.
 *
 * This class starts the UDP server, waits for the first request packet from a
 * client, and passes the received request to a {@code TFTPRequestHandler}
 * running in its own thread.
 */
public class TFTPServer
{
	public static final int TFTPPORT = 4970;
	public static final int BUFSIZE = 516;
	public static final String READDIR = "/Users/harrisonkatantazi/Desktop/1DV701-assignment3/read/";
	public static final String WRITEDIR = "/Users/harrisonkatantazi/Desktop/1DV701-assignment3/write/";

	/**
	 * Starts the server application.
	 *
	 * @param args command-line arguments
	 */
	public static void main(String[] args)
	{
		try
		{
			new TFTPServer().start();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * Creates the listening socket and keeps the server running in a loop.
	 *
	 * @throws SocketException if the UDP socket cannot be created or bound
	 */
	private void start() throws SocketException
	{
		byte[] buf = new byte[BUFSIZE];
		DatagramSocket socket = new DatagramSocket(null);
		SocketAddress localBindPoint = new InetSocketAddress(TFTPPORT);
		socket.bind(localBindPoint);
		System.out.printf("Listening at port %d for new requests%n", TFTPPORT);

		while (true)
		{
			InetSocketAddress clientAddress = receiveFrom(socket, buf);
			if (clientAddress == null)
			{
				continue;
			}

			byte[] requestData = new byte[BUFSIZE];
			System.arraycopy(buf, 0, requestData, 0, BUFSIZE);
			new Thread(new TFTPRequestHandler(clientAddress, requestData)).start();
		}
	}

	/**
	 * Receives the initial request packet from a client.
	 *
	 * @param socket server socket used to receive packets
	 * @param buf buffer where the incoming packet bytes are stored
	 * @return the client address and port, or {@code null} if receiving fails
	 */
	private InetSocketAddress receiveFrom(DatagramSocket socket, byte[] buf)
	{
		try
		{
			DatagramPacket packet = new DatagramPacket(buf, buf.length);
			socket.receive(packet);
			return new InetSocketAddress(packet.getAddress(), packet.getPort());
		}
		catch (IOException e)
		{
			System.err.println("Error receiving packet: " + e.getMessage());
			return null;
		}
	}
}
