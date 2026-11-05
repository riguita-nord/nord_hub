package com.nordlab.workspace;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Internal-only administrative process. Do not expose its TCP port publicly. */
public final class AdminServer {
    private static final ObjectMapper JSON = new ObjectMapper();
    public static void main(String[] args) throws Exception {
        String secret = System.getenv("NORD_ADMIN_SECRET");
        if (secret == null || secret.length() < 32) throw new IllegalStateException("NORD_ADMIN_SECRET must be at least 32 characters");
        Path db = Path.of(System.getenv().getOrDefault("NORD_DB", "./data/workspace.db")).toAbsolutePath();
        if (!Files.isRegularFile(db)) throw new IllegalStateException("Core database does not exist yet: " + db);
        int port = Integer.parseInt(System.getenv().getOrDefault("NORD_ADMIN_PORT", "8092"));
        HttpServer http = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        http.createContext("/internal/", e -> {
            try {
                String supplied = e.getRequestHeaders().getFirst("X-Nord-Internal");
                boolean auth = supplied != null && java.security.MessageDigest.isEqual(
                    secret.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
                if (!auth) { send(e, 403, Map.of("error", "forbidden")); return; }
                String path = e.getRequestURI().getPath();
                if (!e.getRequestMethod().equals("GET")) { send(e, 405, Map.of("error", "method_not_allowed")); return; }
                try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db)) {
                    if (path.equals("/internal/health")) {
                        send(e, 200, Map.of("service", "nord-workspace-admin", "status", "ok"));
                    } else if (path.equals("/internal/stats")) {
                        send(e, 200, Map.of("users", count(c,"users"), "workspaces",count(c,"workspaces"), "projects",count(c,"projects"), "tasks",count(c,"tasks")));
                    } else if (path.equals("/internal/users")) {
                        List<Map<String,Object>> users = new ArrayList<>();
                        try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT id,name,email,role,created_at FROM users ORDER BY id DESC LIMIT 100")) {
                            while (r.next()) users.add(Map.of("id",r.getLong(1),"name",r.getString(2),"email",r.getString(3),"role",r.getString(4),"createdAt",r.getString(5)));
                        }
                        send(e, 200, Map.of("users",users));
                    } else send(e, 404, Map.of("error", "not_found"));
                }
            } catch (Exception x) { send(e, 500, Map.of("error","internal_error")); }
        });
        http.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
        http.start();
        System.out.println("Nord Workspace Admin on loopback:" + port);
    }
    private static long count(Connection c,String table) throws SQLException {
        try (Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM "+table)) {return r.next()?r.getLong(1):0;}
    }
    private static void send(HttpExchange e,int code,Object value) throws java.io.IOException {
        byte[] b=JSON.writeValueAsBytes(value);
        e.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");
        e.getResponseHeaders().set("Cache-Control","no-store");
        e.sendResponseHeaders(code,b.length);
        try(var out=e.getResponseBody()){out.write(b);}
    }
}
