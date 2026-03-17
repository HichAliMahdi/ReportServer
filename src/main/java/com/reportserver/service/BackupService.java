package com.reportserver.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Scheduled backup service.
 *
 * Backs up data/reports/ and data/generated-reports/ to data/backups/YYYY-MM-DD-HHmm/
 * Default schedule: daily at 02:00 (overridable via reportserver.backup.cron).
 * Old backups are pruned after reportserver.backup.retention-days (default 30).
 */
@Service
public class BackupService {

    private static final Logger logger = LoggerFactory.getLogger(BackupService.class);
    private static final DateTimeFormatter BACKUP_DIR_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm");

    @Value("${reportserver.backup.enabled:true}")
    private boolean enabled;

    @Value("${reportserver.backup.dir:data/backups}")
    private String backupDir;

    @Value("${reportserver.backup.retention-days:30}")
    private int retentionDays;

    @Value("${reportserver.upload-dir:data/reports/}")
    private String uploadDir;

    private static final String GENERATED_DIR = "data/generated-reports/";

    // ─── Scheduled backup ─────────────────────────────────────────────────────

    @Scheduled(cron = "${reportserver.backup.cron:0 0 2 * * ?}")
    public void scheduledBackup() {
        if (!enabled) {
            logger.debug("Backup is disabled via configuration; skipping.");
            return;
        }
        try {
            performBackup();
        } catch (Exception e) {
            logger.error("Scheduled backup failed", e);
        }
        try {
            pruneOldBackups();
        } catch (Exception e) {
            logger.error("Backup pruning failed", e);
        }
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Perform a backup immediately. Called by the manual-trigger endpoint.
     *
     * @return the path of the newly created backup directory
     */
    public String performBackup() throws IOException {
        String timestamp = LocalDateTime.now().format(BACKUP_DIR_FORMAT);
        Path dest = Paths.get(backupDir, timestamp);
        Files.createDirectories(dest);

        copyDir(Paths.get(uploadDir), dest.resolve("reports"));
        copyDir(Paths.get(GENERATED_DIR), dest.resolve("generated-reports"));

        logger.info("Backup created at {}", dest.toAbsolutePath());
        return dest.toString();
    }

    /**
     * List existing backups (directory names), newest first.
     */
    public List<String> listBackups() throws IOException {
        Path root = Paths.get(backupDir);
        if (!Files.exists(root)) return Collections.emptyList();

        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    names.add(entry.getFileName().toString());
                }
            }
        }
        names.sort(Comparator.reverseOrder());
        return names;
    }

    /**
     * Remove backup directories older than retentionDays.
     */
    public int pruneOldBackups() throws IOException {
        Path root = Paths.get(backupDir);
        if (!Files.exists(root)) return 0;

        int removed = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path entry : stream) {
                if (!Files.isDirectory(entry)) continue;
                try {
                    LocalDateTime created = LocalDateTime.parse(
                            entry.getFileName().toString(), BACKUP_DIR_FORMAT);
                    if (created.isBefore(LocalDateTime.now().minusDays(retentionDays))) {
                        deleteRecursively(entry);
                        removed++;
                        logger.info("Pruned old backup: {}", entry);
                    }
                } catch (Exception ignored) {
                    // Directory name doesn't match timestamp format — leave it alone
                }
            }
        }
        return removed;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void copyDir(Path src, Path dest) throws IOException {
        if (!Files.exists(src)) {
            logger.debug("Source directory does not exist, skipping: {}", src);
            return;
        }
        Files.createDirectories(dest);
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path target = dest.resolve(src.relativize(dir));
                Files.createDirectories(target);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, dest.resolve(src.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void deleteRecursively(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
