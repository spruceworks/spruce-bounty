package dev.spruceworks.bounty.storage;

import dev.spruceworks.bounty.model.Bounty;
import dev.spruceworks.bounty.model.Contribution;
import dev.spruceworks.bounty.model.CooldownEntry;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;

/**
 * SQLite-backed {@link BountyStorage}. A single JDBC connection is reused for
 * the plugin's lifetime and every method is synchronized: SQLite serializes
 * writers internally anyway, so this avoids pulling in a pooling library for
 * a single-file embedded database.
 *
 * <p>Driver registration relies on JDBC 4 auto-discovery via
 * {@code META-INF/services/java.sql.Driver} (shadowJar relocates that file's
 * contents along with the class), not an explicit {@code Class.forName} —
 * a string literal class name would not survive relocation.
 */
public final class SqliteBountyStorage implements BountyStorage {

    private static final int SCHEMA_VERSION = 1;

    private final File databaseFile;
    private final Logger logger;
    private Connection connection;

    public SqliteBountyStorage(File dataFolder, Logger logger) {
        this.databaseFile = new File(dataFolder, "bounty.db");
        this.logger = logger;
    }

    @Override
    public synchronized void open() {
        try {
            File parent = this.databaseFile.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + this.databaseFile.getAbsolutePath());
            try (Statement st = this.connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA foreign_keys=ON");
            }
            migrate();
        } catch (SQLException e) {
            throw new StorageException("Failed to open SQLite database at " + this.databaseFile, e);
        }
    }

    private void migrate() throws SQLException {
        try (Statement st = this.connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
        }
        int current = currentVersion();
        if (current < 1) {
            try (Statement st = this.connection.createStatement()) {
                st.execute("""
                        CREATE TABLE bounties (
                            target_uuid TEXT PRIMARY KEY,
                            first_placed_at INTEGER NOT NULL,
                            last_updated_at INTEGER NOT NULL
                        )""");
                st.execute("""
                        CREATE TABLE bounty_contributions (
                            target_uuid TEXT NOT NULL REFERENCES bounties(target_uuid) ON DELETE CASCADE,
                            placer_uuid TEXT NOT NULL,
                            amount REAL NOT NULL,
                            placed_at INTEGER NOT NULL,
                            PRIMARY KEY (target_uuid, placer_uuid)
                        )""");
                st.execute("""
                        CREATE TABLE claim_cooldowns (
                            killer_uuid TEXT NOT NULL,
                            victim_uuid TEXT NOT NULL,
                            expires_at INTEGER NOT NULL,
                            PRIMARY KEY (killer_uuid, victim_uuid)
                        )""");
                st.execute("INSERT INTO schema_version(version) VALUES (1)");
            }
            this.logger.info("Initialized SpruceBounty database schema v1.");
            current = 1;
        }
        if (current != SCHEMA_VERSION) {
            throw new StorageException("Unsupported SpruceBounty database schema version " + current, null);
        }
    }

    private int currentVersion() throws SQLException {
        try (Statement st = this.connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT version FROM schema_version LIMIT 1")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    @Override
    public synchronized void close() {
        if (this.connection == null) {
            return;
        }
        try {
            this.connection.close();
        } catch (SQLException e) {
            this.logger.error("Failed to close the SpruceBounty database cleanly", e);
        }
    }

    @Override
    public synchronized Collection<Bounty> loadAllBounties() {
        Map<UUID, Bounty> bounties = new HashMap<>();
        try (Statement st = this.connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT target_uuid, first_placed_at, last_updated_at FROM bounties")) {
            while (rs.next()) {
                UUID target = UUID.fromString(rs.getString("target_uuid"));
                bounties.put(target, new Bounty(target,
                        Instant.ofEpochMilli(rs.getLong("first_placed_at")),
                        Instant.ofEpochMilli(rs.getLong("last_updated_at"))));
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to load bounties", e);
        }

        String contribSql = "SELECT target_uuid, placer_uuid, amount, placed_at FROM bounty_contributions";
        try (Statement st = this.connection.createStatement();
             ResultSet rs = st.executeQuery(contribSql)) {
            while (rs.next()) {
                UUID target = UUID.fromString(rs.getString("target_uuid"));
                Bounty bounty = bounties.get(target);
                if (bounty == null) {
                    continue; // orphaned row; FK cascade prevents this going forward
                }
                UUID placer = UUID.fromString(rs.getString("placer_uuid"));
                bounty.restoreContribution(placer, rs.getDouble("amount"), Instant.ofEpochMilli(rs.getLong("placed_at")));
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to load bounty contributions", e);
        }
        return bounties.values();
    }

    @Override
    public synchronized Collection<CooldownEntry> loadAllCooldowns() {
        List<CooldownEntry> entries = new ArrayList<>();
        String sql = "SELECT killer_uuid, victim_uuid, expires_at FROM claim_cooldowns";
        try (Statement st = this.connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                entries.add(new CooldownEntry(
                        UUID.fromString(rs.getString("killer_uuid")),
                        UUID.fromString(rs.getString("victim_uuid")),
                        Instant.ofEpochMilli(rs.getLong("expires_at"))));
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to load claim cooldowns", e);
        }
        return entries;
    }

    @Override
    public synchronized void saveContribution(Bounty bounty, UUID placer) {
        Contribution contribution = bounty.contributionOf(placer);
        if (contribution == null) {
            throw new IllegalArgumentException("No contribution from " + placer + " on bounty " + bounty.target());
        }
        String bountySql = """
                INSERT INTO bounties (target_uuid, first_placed_at, last_updated_at)
                VALUES (?, ?, ?)
                ON CONFLICT(target_uuid) DO UPDATE SET last_updated_at = excluded.last_updated_at
                """;
        String contribSql = """
                INSERT INTO bounty_contributions (target_uuid, placer_uuid, amount, placed_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(target_uuid, placer_uuid) DO UPDATE SET amount = excluded.amount, placed_at = excluded.placed_at
                """;
        try {
            this.connection.setAutoCommit(false);
            try (PreparedStatement ps = this.connection.prepareStatement(bountySql)) {
                ps.setString(1, bounty.target().toString());
                ps.setLong(2, bounty.firstPlacedAt().toEpochMilli());
                ps.setLong(3, bounty.lastUpdatedAt().toEpochMilli());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = this.connection.prepareStatement(contribSql)) {
                ps.setString(1, bounty.target().toString());
                ps.setString(2, placer.toString());
                ps.setDouble(3, contribution.amount());
                ps.setLong(4, contribution.placedAt().toEpochMilli());
                ps.executeUpdate();
            }
            this.connection.commit();
        } catch (SQLException e) {
            rollbackQuietly();
            throw new StorageException("Failed to save contribution for " + placer + " on " + bounty.target(), e);
        } finally {
            autoCommitOnQuietly();
        }
    }

    @Override
    public synchronized void deleteContribution(UUID target, UUID placer) {
        String sql = "DELETE FROM bounty_contributions WHERE target_uuid = ? AND placer_uuid = ?";
        try (PreparedStatement ps = this.connection.prepareStatement(sql)) {
            ps.setString(1, target.toString());
            ps.setString(2, placer.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to delete contribution for " + placer + " on " + target, e);
        }
    }

    @Override
    public synchronized void deleteBounty(UUID target) {
        String sql = "DELETE FROM bounties WHERE target_uuid = ?"; // contributions cascade
        try (PreparedStatement ps = this.connection.prepareStatement(sql)) {
            ps.setString(1, target.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to delete bounty " + target, e);
        }
    }

    @Override
    public synchronized void deleteAllBounties() {
        try (Statement st = this.connection.createStatement()) {
            st.execute("DELETE FROM bounties"); // contributions cascade
        } catch (SQLException e) {
            throw new StorageException("Failed to clear all bounties", e);
        }
    }

    @Override
    public synchronized void saveCooldown(UUID killer, UUID victim, Instant expiresAt) {
        String sql = """
                INSERT INTO claim_cooldowns (killer_uuid, victim_uuid, expires_at)
                VALUES (?, ?, ?)
                ON CONFLICT(killer_uuid, victim_uuid) DO UPDATE SET expires_at = excluded.expires_at
                """;
        try (PreparedStatement ps = this.connection.prepareStatement(sql)) {
            ps.setString(1, killer.toString());
            ps.setString(2, victim.toString());
            ps.setLong(3, expiresAt.toEpochMilli());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to save cooldown for " + killer + " -> " + victim, e);
        }
    }

    @Override
    public synchronized void deleteCooldown(UUID killer, UUID victim) {
        String sql = "DELETE FROM claim_cooldowns WHERE killer_uuid = ? AND victim_uuid = ?";
        try (PreparedStatement ps = this.connection.prepareStatement(sql)) {
            ps.setString(1, killer.toString());
            ps.setString(2, victim.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to delete cooldown for " + killer + " -> " + victim, e);
        }
    }

    private void rollbackQuietly() {
        try {
            this.connection.rollback();
        } catch (SQLException ignored) {
            // best-effort; the original failure is already being reported
        }
    }

    private void autoCommitOnQuietly() {
        try {
            this.connection.setAutoCommit(true);
        } catch (SQLException ignored) {
            // best-effort; connection may already be broken
        }
    }
}
