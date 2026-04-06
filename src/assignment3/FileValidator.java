package assignment3;

import java.nio.file.Path;

/**
 * Validates files before they are accepted for upload.
 *
 * This helper class can be extended later with stricter assignment-specific
 * rules, but it already provides basic checks for filename, file extension, and
 * maximum file size.
 */
public final class FileValidator
{
	private static final long MAX_FILE_SIZE_BYTES = 1024 * 1024;

	private FileValidator()
	{
	}

	/**
	 * Checks whether a file is valid to upload.
	 *
	 * @param filePath path of the file that will be created on the server
	 * @param fileSizeBytes size of the incoming file in bytes
	 * @return {@code true} if the file passes validation, otherwise {@code false}
	 */
	public static boolean isValidForUpload(Path filePath, long fileSizeBytes)
	{
		if (filePath == null || filePath.getFileName() == null)
		{
			return false;
		}

		String fileName = filePath.getFileName().toString();
		if (fileName.isBlank())
		{
			return false;
		}

		if (fileName.contains("/") || fileName.contains("\\"))
		{
			return false;
		}

		if (!hasAllowedExtension(fileName))
		{
			return false;
		}

		return fileSizeBytes >= 0 && fileSizeBytes <= MAX_FILE_SIZE_BYTES;
	}

	/**
	 * Checks whether the filename uses an allowed extension.
	 *
	 * @param fileName name of the file being uploaded
	 * @return {@code true} if the extension is allowed
	 */
	private static boolean hasAllowedExtension(String fileName)
	{
		String lowerName = fileName.toLowerCase();
		return lowerName.endsWith(".txt")
				|| lowerName.endsWith(".pdf")
				|| lowerName.endsWith(".doc")
				|| lowerName.endsWith(".docx")
				|| lowerName.endsWith(".jpg")
				|| lowerName.endsWith(".png");
	}
}
