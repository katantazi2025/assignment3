package assignment3;

public final class TFTPPacket
{
	public static final int OP_RRQ = 1;
	public static final int OP_WRQ = 2;
	public static final int OP_DAT = 3;
	public static final int OP_ACK = 4;
	public static final int OP_ERR = 5;

	private TFTPPacket()
	{
	}

	public static int getOpcode(byte[] packetData)
	{
		// Step 8:
		// Read the first two bytes and return the opcode.
		return -1;
	}

	public static String extractFilename(byte[] packetData)
	{
		// Step 9:
		// Read the filename field from an RRQ/WRQ packet.
		return "";
	}

	public static String extractMode(byte[] packetData)
	{
		// Step 10:
		// Read the transfer mode field, for example "octet".
		return "";
	}

	public static byte[] createDataPacket(int blockNumber, byte[] data)
	{
		// Step 11:
		// Build a TFTP DATA packet.
		return new byte[0];
	}

	public static byte[] createAckPacket(int blockNumber)
	{
		// Step 12:
		// Build a TFTP ACK packet.
		return new byte[0];
	}

	public static byte[] createErrorPacket(int errorCode, String errorMessage)
	{
		// Step 13:
		// Build a TFTP ERROR packet.
		return new byte[0];
	}
}
