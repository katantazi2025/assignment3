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
	private static final long MIN_FILE_SIZE_BYTES = 1;
	private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;

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
	public static ValidationResult validateUpload(Path filePath, long fileSizeBytes)
	{
		if (filePath == null || filePath.getFileName() == null)
		{
			return ValidationResult.invalid(TFTPError.NOT_DEFINED, "Invalid file type");
		}

		String fileName = filePath.getFileName().toString();
		if (fileName.isBlank())
		{
			return ValidationResult.invalid(TFTPError.NOT_DEFINED, "Invalid file type");
		}

		if (fileName.contains("/") || fileName.contains("\\"))
		{
			return ValidationResult.invalid(TFTPError.NOT_DEFINED, "Invalid file type");
		}

		if (!hasAllowedExtension(fileName))
		{
			return ValidationResult.invalid(TFTPError.NOT_DEFINED, "Invalid file type");
		}

		if (fileSizeBytes < MIN_FILE_SIZE_BYTES)
		{
			return ValidationResult.invalid(TFTPError.NOT_DEFINED, "File size below minimum limit");
		}

		if (fileSizeBytes > MAX_FILE_SIZE_BYTES)
		{
			return ValidationResult.invalid(TFTPError.NOT_DEFINED, "File exceeds size limit");
		}

		return ValidationResult.valid();
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

	/**
	 * Result object returned by the upload validator.
	 */
	public static final class ValidationResult
	{
		private final boolean valid;
		private final int errorCode;
		private final String errorMessage;

		private ValidationResult(boolean valid, int errorCode, String errorMessage)
		{
			this.valid = valid;
			this.errorCode = errorCode;
			this.errorMessage = errorMessage;
		}

		public static ValidationResult valid()
		{
			return new ValidationResult(true, -1, "");
		}

		public static ValidationResult invalid(int errorCode, String errorMessage)
		{
			return new ValidationResult(false, errorCode, errorMessage);
		}

		public boolean isValid()
		{
			return valid;
		}

		public int errorCode()
		{
			return errorCode;
		}

		public String errorMessage()
		{
			return errorMessage;
		}
	}
}
