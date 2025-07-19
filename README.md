# UploadPlugin - Minecraft SFTP File Upload Plugin

A modern Minecraft Paper plugin that allows server administrators to upload files from the server root directory to a remote SFTP server with real-time progress tracking.

## Features

- **Modern SSH Support**: Uses SSHJ library with full support for modern algorithms including `ssh-ed25519`
- **Async Operations**: Never blocks the main server thread, even with large files
- **Progress Tracking**: Real-time progress updates during file transfer (configurable intervals)
- **Stream-based Transfer**: Handles large files efficiently without loading them entirely into memory
- **Thread-safe Messaging**: All player messages are sent from the main thread for compatibility
- **Robust Error Handling**: User-friendly error messages with detailed logging for administrators
- **Flexible Configuration**: Host key verification, custom remote directories, and more

## Requirements

- Minecraft Server: Paper 1.20+ (tested on 1.21.4)
- Java: 21+
- Maven: 3.6+

## Installation

1. **Build the plugin:**
   ```bash
   mvn clean package
   ```

2. **Install the plugin:**
   - Copy `target/uploadplugin-1.0.jar` to your server's `plugins/` folder
   - Restart your server

3. **Configure SFTP settings:**
   - Edit `plugins/UploadPlugin/config.yml`
   - Set your SFTP server details:
     ```yaml
     sftp:
       host: "your-server-ip"
       port: 22
       username: "your-username"
       password: "your-password"
       remote-dir: "/uploads"  # Optional: target directory
       ignore-host-key: true   # Recommended for ease of use
     ```

## Usage

### Command
```
/uploadfile <filename>
```

### Permission
- `uploadplugin.use` (default: op level)

### Examples
```
/uploadfile world_backup.zip
/uploadfile logs/latest.log
/uploadfile plugins/MyPlugin/config.yml
```

## Configuration Options

```yaml
sftp:
  host: "your-vps-ip"           # SFTP server hostname/IP
  port: 22                      # SFTP port (default: 22)
  username: "your-username"     # Username for authentication
  password: "your-password"     # Password for authentication
  remote-dir: "."               # Remote directory (default: home directory)
  ignore-host-key: true         # Skip host key verification (recommended)

progress:
  interval-percent: 10          # Progress update interval (10 = every 10%)

logging:
  verbose: false                # Enable detailed console logging
```

## Security Notes

- **Host Key Verification**: Set `ignore-host-key: false` for production environments and manually verify host keys
- **Password Storage**: Consider using SSH key authentication in future versions
- **File Access**: Plugin can only access files in the server root directory and subdirectories
- **Permissions**: Only users with `uploadplugin.use` permission can execute uploads

## Troubleshooting

### Common Error Messages

- **"Connection refused"**: Check host and port settings
- **"Authentication failed"**: Verify username and password
- **"Host not found"**: Check hostname/IP address
- **"Connection timeout"**: Verify network connectivity and firewall settings

### Algorithm Negotiation Issues
This plugin uses SSHJ library which supports modern SSH algorithms including:
- ssh-ed25519
- ecdsa-sha2-nistp256/384/521
- ssh-rsa
- And more

If you encounter "Algorithm negotiation fail" errors, ensure your SFTP server supports at least one of these algorithms.

### Large File Uploads
The plugin streams files efficiently and provides progress updates. For very large files:
- Ensure stable network connection
- Monitor server memory usage (should remain stable)
- Check disk space on both source and destination

## Technical Details

- **Library**: SSHJ 0.38.0 (modern SSH/SFTP implementation)
- **Threading**: Async operations with main-thread message delivery
- **Memory**: Stream-based transfer, no full file loading
- **Progress**: Byte-level tracking with configurable update intervals
- **Error Handling**: Comprehensive logging with user-friendly messages

## License

This plugin is provided as-is for educational and practical use. Feel free to modify and distribute according to your needs.

## Support

For issues related to:
- SSH/SFTP connectivity: Check your server configuration and network
- Plugin errors: Enable verbose logging and check console output
- Performance: Monitor server resources during large file transfers