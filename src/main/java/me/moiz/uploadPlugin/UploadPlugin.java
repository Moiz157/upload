package me.moiz.uploadPlugin;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.xfer.FileSystemFile;
import net.schmizz.sshj.common.StreamCopier;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * UploadPlugin - A Minecraft Paper plugin for uploading files via SFTP
 * 
 * Features:
 * - Async file uploads with progress tracking
 * - Modern SSH/SFTP support (including ssh-ed25519)
 * - Configurable connection settings
 * - Thread-safe progress reporting
 * 
 * Usage: /uploadfile <filename>
 * 
 * Build: mvn clean package
 * Install: Place the generated JAR in your server's plugins folder
 */
public class UploadPlugin extends JavaPlugin implements CommandExecutor {
    
    private FileConfiguration config;
    private int progressInterval;
    
    @Override
    public void onEnable() {
        // Save default config if it doesn't exist
        saveDefaultConfig();
        config = getConfig();
        progressInterval = config.getInt("progress.interval-percent", 10);
        
        // Register command
        getCommand("uploadfile").setExecutor(this);
        
        getLogger().info("UploadPlugin enabled! Use /uploadfile <filename> to upload files.");
        
        // Validate configuration
        if (isConfigurationMissing()) {
            getLogger().warning("Please configure SFTP settings in config.yml before using the plugin!");
        }
    }
    
    @Override
    public void onDisable() {
        getLogger().info("UploadPlugin disabled.");
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("uploadfile")) {
            return false;
        }
        
        // Check permission
        if (!sender.hasPermission("uploadplugin.use")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }
        
        // Validate arguments
        if (args.length != 1) {
            sender.sendMessage("§cUsage: /uploadfile <file_name>");
            return true;
        }
        
        String fileName = args[0];
        
        // Check if configuration is valid
        if (isConfigurationMissing()) {
            sender.sendMessage("§cSFTP configuration is missing or invalid. Please check config.yml");
            return true;
        }
        
        // Check if file exists
        File file = new File(fileName);
        if (!file.exists()) {
            sender.sendMessage("§cFile '" + fileName + "' not found in server root directory.");
            return true;
        }
        
        if (!file.isFile()) {
            sender.sendMessage("§c'" + fileName + "' is not a valid file.");
            return true;
        }
        
        // Start async upload
        sender.sendMessage("§aStarting upload of '" + fileName + "' (" + formatFileSize(file.length()) + ")...");
        uploadFileAsync(sender, file);
        
        return true;
    }
    
    private void uploadFileAsync(CommandSender sender, File file) {
        CompletableFuture.runAsync(() -> {
            try {
                uploadFile(sender, file);
            } catch (Exception e) {
                // Log full error to console
                getLogger().log(Level.SEVERE, "Upload failed for file: " + file.getName(), e);
                
                // Send user-friendly error message
                scheduleSync(() -> {
                    String errorMsg = getSimpleErrorMessage(e);
                    sender.sendMessage("§cUpload failed: " + errorMsg);
                });
            }
        });
    }
    
    private void uploadFile(CommandSender sender, File file) throws Exception {
        String host = config.getString("sftp.host");
        int port = config.getInt("sftp.port", 22);
        String username = config.getString("sftp.username");
        String password = config.getString("sftp.password");
        String remoteDir = config.getString("sftp.remote-dir", ".");
        boolean ignoreHostKey = config.getBoolean("sftp.ignore-host-key", true);
        
        SSHClient ssh = new SSHClient();
        
        try {
            // Configure host key verification
            if (ignoreHostKey) {
                ssh.addHostKeyVerifier(new PromiscuousVerifier());
            } else {
                ssh.loadKnownHosts();
            }
            
            // Connect and authenticate
            scheduleSync(() -> sender.sendMessage("§eConnecting to " + host + ":" + port + "..."));
            ssh.connect(host, port);
            
            scheduleSync(() -> sender.sendMessage("§eAuthenticating as " + username + "..."));
            ssh.authPassword(username, password);
            
            // Open SFTP channel
            scheduleSync(() -> sender.sendMessage("§eOpening SFTP channel..."));
            try (SFTPClient sftp = ssh.newSFTPClient()) {
                // Change to remote directory if specified
                if (!".".equals(remoteDir)) {
                    try {
                        sftp.statExistence(remoteDir);
                        // Directory exists, we can proceed
                    } catch (Exception e) {
                        // Directory might not exist, try to create it or use current directory
                        getLogger().warning("Remote directory '" + remoteDir + "' might not exist, using current directory");
                        remoteDir = ".";
                    }
                }
                
                // Create progress tracker
                String remotePath = remoteDir.equals(".") ? file.getName() : remoteDir + "/" + file.getName();
                
                // Upload with custom progress tracking
                uploadWithProgress(sftp, file, remotePath, sender, file.length());
                
                scheduleSync(() -> sender.sendMessage("§aUpload completed successfully!"));
            }
            
        } finally {
            try {
                ssh.disconnect();
            } catch (IOException e) {
                getLogger().log(Level.WARNING, "Error disconnecting SSH client", e);
            }
        }
    }
    
    private void uploadWithProgress(SFTPClient sftp, File file, String remotePath, CommandSender sender, long fileSize) throws IOException {
        long transferred = 0;
        int lastReportedPercent = 0;
        long startTime = System.currentTimeMillis();
        
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file);
             java.io.OutputStream os = sftp.getSFTPEngine().open(remotePath, 
                 java.util.EnumSet.of(net.schmizz.sshj.sftp.OpenMode.WRITE, 
                                     net.schmizz.sshj.sftp.OpenMode.CREAT, 
                                     net.schmizz.sshj.sftp.OpenMode.TRUNC)).new RemoteFileOutputStream()) {
            
            byte[] buffer = new byte[32768]; // 32KB buffer
            int bytesRead;
            
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
                transferred += bytesRead;
                
                // Calculate and report progress
                if (fileSize > 0) {
                    int currentPercent = (int) ((transferred * 100) / fileSize);
                    long currentTime = System.currentTimeMillis();
                    long elapsedTime = currentTime - startTime;
                    
                    // Report progress at 10% intervals
                    if (currentPercent >= lastReportedPercent + progressInterval && currentPercent <= 100) {
                        lastReportedPercent = (currentPercent / progressInterval) * progressInterval;
                        
                        final int percent = Math.min(currentPercent, 100);
                        final long finalTransferred = transferred;
                        final String speed = calculateSpeed(finalTransferred, elapsedTime);
                        scheduleSync(() -> {
                            sender.sendMessage("§e" + percent + "% complete (" +
                                             formatFileSize(finalTransferred) + "/" + 
                                             formatFileSize(fileSize) + ") - " + speed);
                        });
                    }
                }
            }
        }
    }
    
    private String calculateSpeed(long bytesTransferred, long elapsedTimeMs) {
        if (elapsedTimeMs <= 0) {
            return "0 B/s";
        }
        
        double bytesPerSecond = (double) bytesTransferred / (elapsedTimeMs / 1000.0);
        
        if (bytesPerSecond < 1024) {
            return String.format("%.0f B/s", bytesPerSecond);
        } else if (bytesPerSecond < 1024 * 1024) {
            return String.format("%.1f KB/s", bytesPerSecond / 1024);
        } else if (bytesPerSecond < 1024 * 1024 * 1024) {
            return String.format("%.1f MB/s", bytesPerSecond / (1024 * 1024));
        } else {
            return String.format("%.1f GB/s", bytesPerSecond / (1024 * 1024 * 1024));
        }
    }
    
    private boolean isConfigurationMissing() {
        return config.getString("sftp.host", "").equals("your-vps-ip") ||
               config.getString("sftp.username", "").equals("your-username") ||
               config.getString("sftp.password", "").equals("your-password") ||
               config.getString("sftp.host", "").isEmpty() ||
               config.getString("sftp.username", "").isEmpty() ||
               config.getString("sftp.password", "").isEmpty();
    }
    
    private String getSimpleErrorMessage(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return "Unknown error";
        }
        
        // Simplify common error messages
        if (message.contains("Connection refused")) {
            return "Connection refused - check host and port";
        } else if (message.contains("Auth fail")) {
            return "Authentication failed - check username and password";
        } else if (message.contains("UnknownHostException")) {
            return "Host not found - check hostname/IP";
        } else if (message.contains("timeout")) {
            return "Connection timeout - check network connectivity";
        } else {
            // Return first line of error message, avoid exposing sensitive details
            return message.split("\n")[0];
        }
    }
    
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
    
    private void scheduleSync(Runnable task) {
        Bukkit.getScheduler().runTask(this, task);
    }
}