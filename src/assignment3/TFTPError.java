package assignment3;

/**
 * RFC1350 TFTP error codes used by the server.
 */
public final class TFTPError
{
	public static final int NOT_DEFINED = 0;
	public static final int FILE_NOT_FOUND = 1;
	public static final int ACCESS_VIOLATION = 2;
	public static final int ILLEGAL_OPERATION = 4;
	public static final int FILE_ALREADY_EXISTS = 6;

	/**
	 * Private constructor to prevent instantiation of this constants class.
	 */
	private TFTPError()
	{
	}
}
