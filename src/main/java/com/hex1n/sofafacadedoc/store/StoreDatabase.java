package com.hex1n.sofafacadedoc.store;

import com.hex1n.sofafacadedoc.config.AppConfigLoader;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

@Component
public class StoreDatabase {
    private final AppConfigLoader configLoader;
    private String jdbcUrl;

    public StoreDatabase(AppConfigLoader configLoader) {
        this.configLoader = configLoader;
    }

    @PostConstruct
    public void init() throws Exception {
        File dir = new File(configLoader.current().server.dataDir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("cannot create dataDir " + dir);
        }
        jdbcUrl = "jdbc:sqlite:" + new File(dir, "app.db").getAbsolutePath();
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS snapshots (id INTEGER PRIMARY KEY AUTOINCREMENT, project TEXT NOT NULL, branch TEXT NOT NULL, commit_hash TEXT NOT NULL, structure_hash TEXT NOT NULL, created_at TEXT NOT NULL, document_json TEXT NOT NULL)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_snapshots_latest ON snapshots(project, branch, id DESC)");
            st.execute("CREATE TABLE IF NOT EXISTS scan_reports (id INTEGER PRIMARY KEY AUTOINCREMENT, project TEXT NOT NULL, branch TEXT NOT NULL, commit_hash TEXT, status TEXT NOT NULL, message TEXT, created_at TEXT NOT NULL)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_scan_reports_latest ON scan_reports(project, branch, id DESC)");
            st.execute("CREATE TABLE IF NOT EXISTS cases (id INTEGER PRIMARY KEY AUTOINCREMENT, project TEXT NOT NULL, branch TEXT NOT NULL, service TEXT NOT NULL, method_id TEXT NOT NULL, name TEXT NOT NULL, note TEXT, args_json TEXT NOT NULL, created_at TEXT NOT NULL, updated_at TEXT NOT NULL)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_cases_method ON cases(project, branch, service, method_id)");
            st.execute("CREATE VIRTUAL TABLE IF NOT EXISTS search_index USING fts5(project UNINDEXED, branch UNINDEXED, snapshot_id UNINDEXED, service, method_id UNINDEXED, method, content)");
        }
    }

    public Connection connect() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }
}
