package com.nordlab.workspace;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.io.*;

public final class AdminServer {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static Path db;
    private static String secret;
    public static void main(String[] args) throws Exception {
        secret = System.getenv("NORD_ADMIN_SECRET");
        if(secret == null || secret.length()<32)throw new IllegalStateException("NORD_ADMIN_SECRET must be at least 32 characters");
        db=Path.of(System.getenv().getOrDefault("NORD_DB","./data/workspace.db")).toAbsolutePath();
        if(!Files.isRegularFile(db))throw new IllegalStateException("Core database must exist: "+db);
        int port=Integer.parseInt(System.getenv().getOrDefault("NORD_ADMIN_PORT","8092"));
        String host=System.getenv().getOrDefault("NORD_ADMIN_HOST","127.0.0.1");
        HttpServer http=HttpServer.create(new InetSocketAddress(host,port),0);
        http.createContext("/",AdminServer::handle);
        http.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
        http.start();
        System.out.println("Nord Workspace Admin listening on "+host+":"+port);
    }
    private static void handle(HttpExchange e) {
        try {
            String path=e.getRequestURI().getPath();
            e.getResponseHeaders().set("X-Content-Type-Options","nosniff");
            e.getResponseHeaders().set("Referrer-Policy","no-referrer");
            e.getResponseHeaders().set("Cache-Control","no-store");
            e.getResponseHeaders().set("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'");
            if(!e.getRequestMethod().equals("GET")){send(e,405,Map.of("error","method_not_allowed"));return;}
            if(path.equals("/")||path.equals("/admin.js")||path.equals("/admin.css")){
                String resource=path.equals("/")?"/admin/index.html":"/admin"+path;
                try(InputStream in=AdminServer.class.getResourceAsStream(resource)){
                    if(in==null){send(e,404,Map.of("error","not_found"));return;}
                    byte[] b=in.readAllBytes();
                    String type=path.endsWith(".js")?"text/javascript":path.endsWith(".css")?"text/css":"text/html";
                    bytes(e,200,b,type+"; charset=utf-8");return;
                }
            }
            try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+db)){
                if(path.startsWith("/internal/")){
                    // Internal shared-secret API can only be called through localhost, never through the LAN.
                    if(!e.getRemoteAddress().getAddress().isLoopbackAddress()) {send(e,403,Map.of("error","loopback_required"));return;}
                    String supplied=e.getRequestHeaders().getFirst("X-Nord-Internal");
                    if(supplied==null||!MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))){
                        send(e,403,Map.of("error","forbidden"));return;
                    }
                    if(path.equals("/internal/health")){send(e,200,Map.of("service","nord-workspace-admin","status","ok"));return;}
                    if(path.equals("/internal/stats")){send(e,200,stats(c));return;}
                    if(path.equals("/internal/users")){send(e,200,Map.of("users",users(c)));return;}
                }
                if(path.startsWith("/api/")){
                    Map<String,Object> owner=authenticatedOwner(e,c);
                    if(owner==null){send(e,401,Map.of("error","platform_owner_login_required"));return;}
                    if(path.equals("/api/me")){send(e,200,owner);return;}
                    if(path.equals("/api/stats")){send(e,200,stats(c));return;}
                    if(path.equals("/api/users")){send(e,200,Map.of("users",users(c)));return;}
                }
                send(e,404,Map.of("error","not_found"));
            }
        }catch(Exception x){x.printStackTrace();try{send(e,500,Map.of("error","internal_error"));}catch(IOException ignore){}}
    }
    private static Map<String,Object> authenticatedOwner(HttpExchange e,Connection c)throws Exception {
        String header=e.getRequestHeaders().getFirst("Cookie");
        if(header==null)return null;
        String token=null;
        for(String part:header.split(";")){
            String[] p=part.trim().split("=",2);
            if(p.length==2&&p[0].equals("nord_session")){token=p[1];break;}
        }
        if(token==null||token.length()>128||token.length()<24)return null;
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        try(PreparedStatement p=c.prepareStatement("SELECT u.id,u.name FROM sessions s JOIN users u ON s.user_id=u.id WHERE s.token_hash=? AND s.expires_at>? AND u.role='PLATFORM_OWNER'")){
            p.setString(1,HexFormat.of().formatHex(hash));p.setLong(2,Instant.now().getEpochSecond());
            try(ResultSet r=p.executeQuery()){if(r.next())return Map.of("id",r.getLong(1),"name",r.getString(2),"role","PLATFORM_OWNER");}
        }
        return null;
    }
    private static Map<String,Object> stats(Connection c)throws SQLException{
        return Map.of("users",count(c,"users"),"workspaces",count(c,"workspaces"),"projects",count(c,"projects"),"tasks",count(c,"tasks"));
    }
    private static long count(Connection c,String table)throws SQLException{
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM "+table)){return r.next()?r.getLong(1):0;}
    }
    private static List<Map<String,Object>> users(Connection c)throws SQLException{
        List<Map<String,Object>> out=new ArrayList<>();
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT id,name,email,role,created_at FROM users ORDER BY id DESC LIMIT 100")){
            while(r.next())out.add(Map.of("id",r.getLong(1),"name",r.getString(2),"email",r.getString(3),"role",r.getString(4),"createdAt",r.getString(5)));
        }return out;
    }
    private static void send(HttpExchange e,int code,Object value)throws IOException{
        bytes(e,code,JSON.writeValueAsBytes(value),"application/json; charset=utf-8");
    }
    private static void bytes(HttpExchange e,int code,byte[] b,String type)throws IOException{
        e.getResponseHeaders().set("Content-Type",type);
        e.sendResponseHeaders(code,b.length);try(var out=e.getResponseBody()){out.write(b);}
    }
}
