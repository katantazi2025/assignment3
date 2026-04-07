package assignment3;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Helper class for parsing and creating TFTP packets.
 *
 * This class groups together the low-level packet operations used by the
 * server, such as reading request fields and creating ACK, DATA, and ERROR
 * packets.
 */
public final class TFTPPacket
{
	public static final int OP_RRQ = 1;
	public static final int OP_WRQ = 2;
	public static final int OP_DAT = 3;
	public static final int OP_ACK = 4;
	public static final int OP_ERR = 5;

	/**
	 * Private constructor to prevent instantiation of this utility class.
	 */
	private TFTPPacket()
	{
	}

	/**
	 * Extracts the TFTP opcode from the first two bytes of a packet.
	 *
	 * @param packetData raw packet bytes
	 * @return the opcode as an unsigned 16-bit integer
	 */
	public static int getOpcode(byte[] packetData)
	{
		ByteBuffer wrap = ByteBuffer.wrap(packetData);
		return wrap.getShort() & 0xFFFF;
	}

	/**
	 * Extracts the filename from an RRQ or WRQ packet.
	 *
	 * The filename begins after the opcode and ends at the first null byte.
	 *
	 * @param packetData raw request packet bytes
	 * @return the filename stored in the request
	 */
	public static String extractFilename(byte[] packetData)
	{
		int i = 2;
		StringBuilder sb = new StringBuilder();
		while (i < packetData.length && packetData[i] != 0)
		{
			sb.append((char) packetData[i]);
			i++;
		}
		return sb.toString();
	}

	/**
	 * Extracts the transfer mode from an RRQ or WRQ packet.
	 *
	 * The mode field comes immediately after the filename terminator and ends at
	 * the next null byte.
	 *
	 * @param packetData raw request packet bytes
	 * @return the transfer mode, for example {@code octet}
	 */
	public static String extractMode(byte[] packetData)
	{
		int i = 2;

		while (i < packetData.length && packetData[i] != 0)
		{
			i++;
		}
		i++;

		StringBuilder sb = new StringBuilder();
		while (i < packetData.length && packetData[i] != 0)
		{
			sb.append((char) packetData[i]);
			i++;
		}
		return sb.toString();
	}

	/**
	 * Creates a TFTP DATA packet.
	 *
	 * This method is intended to build a packet containing the opcode, block
	 * number, and file data payload.
	 *
	 * @param blockNumber block number for the DATA packet
	 * @param data payload bytes
	 * @return the encoded DATA packet bytes
	 */
	public static byte[] createDataPacket(int blockNumber, byte[] data)
	{
		ByteBuffer buffer = ByteBuffer.allocate(4 + data.length);
		buffer.putShort((short) OP_DAT);
		buffer.putShort((short) blockNumber);
		buffer.put(data);
		return buffer.array();
	}

	/**
	 * Creates a TFTP ACK packet for a specific block number.
	 *
	 * @param blockNumber block number being acknowledged
	 * @return the encoded ACK packet bytes
	 */
	public static byte[] createAckPacket(int blockNumber)
	{
		ByteBuffer buffer = ByteBuffer.allocate(4);
		buffer.putShort((short) OP_ACK);
		buffer.putShort((short) blockNumber);
		return buffer.array();
	}

	/**
	 * Creates a packet containing file data for a given block number.
	 *
	 * Although this method is currently named like an ACK builder, the contents
	 * being created are actually for a DATA packet.
	 *
	 * @param blockNumber block number for the packet
	 * @param fileData source data buffer
	 * @param length number of bytes to copy from the source buffer
	 * @return the encoded packet bytes
	 */
	public static byte[] createAckPacket(int blockNumber, byte[] fileData, int length)
	{
		ByteBuffer buffer = ByteBuffer.allocate(4 + length);
		buffer.putShort((short) OP_DAT);
		buffer.putShort((short) blockNumber);
		buffer.put(fileData, 0, length);
		return buffer.array();
	}

	/**
	 * Creates a TFTP ERROR packet with an error code and error message.
	 *
	 * @param errorCode TFTP error code
	 * @param errorMessage error text to include in the packet
	 * @return the encoded ERROR packet bytes
	 */
	public static byte[] createErrorPacket(int errorCode, String errorMessage)
	{
		byte[] errorMessageBytes = errorMessage.getBytes(StandardCharsets.UTF_8);
		ByteBuffer buffer = ByteBuffer.allocate(4 + errorMessageBytes.length + 1);
		buffer.putShort((short) OP_ERR);
		buffer.putShort((short) errorCode);
		buffer.put(errorMessageBytes);
		buffer.put((byte) 0);
		return buffer.array();
	}

	/**
	 * Sends a TFTP ERROR packet through the provided socket.
	 *
	 * @param socket socket used to send the packet
	 * @param errorCode TFTP error code
	 * @param errorMessage error text to send
	 */
	public static void sendError(DatagramSocket socket, int errorCode, String errorMessage)
	{
		try
		{
			byte[] errorPacket = createErrorPacket(errorCode, errorMessage);
			DatagramPacket packet = new DatagramPacket(errorPacket, errorPacket.length);
			socket.send(packet);
		}
		catch (IOException e)
		{
			System.err.println("Failed to send error packet: " + e.getMessage());
		}
	}
}
