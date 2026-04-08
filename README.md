# TFTP Server

This project is a Java-based TFTP server developed for Assignment 3 in the
1DV701 course. The server uses UDP and follows the RFC1350 TFTP protocol for
basic file transfer operations.

## Features

- Supports `RRQ` (Read Request)
- Supports `WRQ` (Write Request)
- Uses `octet` transfer mode
- Handles files smaller and larger than 512 bytes
- Splits larger transfers into multiple TFTP blocks
- Implements timeout handling and retransmission for read requests
- Supports TFTP error handling for common RFC1350 error codes
- Validates uploaded files by file type and file size

## Project Structure

```text
src/assignment3/
├── TFTPServer.java
├── TFTPRequestHandler.java
├── TFTPPacket.java
├── TFTPError.java
└── FileValidator.java
```

## Requirements

- Java JDK installed
- Terminal access
- Optional:
  - `tftp` client for testing
  - Wireshark for packet capture and analysis

## Compile the Project

Open a terminal in the repository folder:

```bash
cd ~/Desktop/1DV701-assignment3/assignment3
javac -d out src/assignment3/*.java
```

This compiles the Java source files and places the generated `.class` files in
the `out/` directory.

## Run the Server

Start the server with:

```bash
java -cp out assignment3.TFTPServer
```

If the server starts correctly, it will listen on UDP port `4970`.

## Server Directories

The server currently uses these local directories:

- Read directory:

```text
/Users/harrisonkatantazi/Desktop/1DV701-assignment3/read/
```

- Write directory:

```text
/Users/harrisonkatantazi/Desktop/1DV701-assignment3/write/
```

Create them if they do not already exist:

```bash
mkdir -p ~/Desktop/1DV701-assignment3/read
mkdir -p ~/Desktop/1DV701-assignment3/write
```

## Testing with TFTP

### Download a File from the Server

Create a test file in the read directory:

```bash
echo "Hello TFTP server" > ~/Desktop/1DV701-assignment3/read/test.txt
```

Then use the TFTP client:

```bash
tftp
connect localhost 4970
mode octet
get test.txt
```

### Upload a File to the Server

Create a local file to upload:

```bash
echo "Hello upload" > ~/Desktop/1DV701-assignment3/upload.txt
```

Then upload it:

```bash
tftp
connect localhost 4970
mode octet
put upload.txt
```

Uploaded files are written to the write directory.

## File Validation Rules

Uploaded files are validated using the following rules.

### Allowed file extensions

- `.txt`
- `.pdf`
- `.doc`
- `.docx`
- `.jpg`
- `.png`

### File size limits

- Minimum file size: `1 byte`
- Maximum file size: `10 MB`

## Error Handling

The server implements TFTP error handling for these RFC1350 error codes:

- `0` Not defined
- `1` File not found
- `2` Access violation
- `6` File already exists

Examples:

- requesting a missing file returns `1`
- uploading a file with a disallowed extension returns `0`
- uploading a file that already exists returns `6`

## Notes

- The server currently supports the `octet` transfer mode.
- One socket listens for new client requests on port `4970`.
- A separate transfer socket is used for each client request.
- If port `4970` is already in use, stop the old Java process before restarting
  the server.

To find and stop a process using the port:

```bash
lsof -nP -iUDP:4970
kill <PID>
```

## Example Workflow

```bash
cd ~/Desktop/1DV701-assignment3/assignment3
javac -d out src/assignment3/*.java
java -cp out assignment3.TFTPServer
```

## Author

Harrison Katantazi
