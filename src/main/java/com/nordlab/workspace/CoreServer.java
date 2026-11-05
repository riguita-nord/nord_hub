package com.nordlab.workspace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.Executors;

public final class CoreServer {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final SecureRandom RNG = new SecureRandom();
    private static final Path DB = Path.of(System.getenv().getOrDefault("NORD_DB","./data/workspace.db")).toAbsolutePath();
    private static final String PUBLIC_ORIGIN = System.getenv().getOrDefault("NORD_PUBLIC_ORIGIN","");
    private static final int ADMIN_PORT = Integer.parseInt(System.getenv().getOrDefault("NORD_ADMIN_PORT","8092"));
    private static final String ADMIN_SECRET = System.getenv().getOrDefault("NORD_ADMIN_SECRET","");
    private record User(long id,String name,String role) {}
    private static class Failure extends RuntimeException {
        final int status; final String code;
        Failure(int status,String code){super(code);this.status=status;this.code=code;}
    }
    public static void main(String[] args) throws Exception {
        Files.createDirectories(DB.getParent());
        init();
        int port = Integer.parseInt(System.getenv().getOrDefault("NORD_PORT","8091"));
        String host = System.getenv().getOrDefault("NORD_HOST","127.0.0.1");
        HttpServer server = HttpServer.create(new InetSocketAddress(host,port),0);
        server.createContext("/",CoreServer::handle);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        System.out.println("Nord Workspace Core listening on "+host+":"+port);
    }
    private static Connection db() throws SQLException {
        Connection c=DriverManager.getConnection("jdbc:sqlite:"+DB);
        try(Statement s=c.createStatement()){s.execute("PRAGMA foreign_keys=ON");s.execute("PRAGMA busy_timeout=5000");}
        return c;
    }
    private static void init() throws SQLException {
        try(Connection c=db();Statement s=c.createStatement()){
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("CREATE TABLE IF NOT EXISTS users(id INTEGER PRIMARY KEY, name TEXT NOT NULL, email TEXT NOT NULL UNIQUE COLLATE NOCASE, password_hash TEXT NOT NULL, role TEXT NOT NULL CHECK(role IN ('PLATFORM_OWNER','USER')), created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            s.execute("CREATE UNIQUE INDEX IF NOT EXISTS one_platform_owner ON users(role) WHERE role='PLATFORM_OWNER'");
            s.execute("CREATE TABLE IF NOT EXISTS sessions(token_hash TEXT PRIMARY KEY, user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE, expires_at INTEGER NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS workspaces(id INTEGER PRIMARY KEY, name TEXT NOT NULL, icon TEXT NOT NULL DEFAULT 'N', owner_id INTEGER NOT NULL REFERENCES users(id), created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            s.execute("CREATE TABLE IF NOT EXISTS workspace_members(workspace_id INTEGER NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,role TEXT NOT NULL CHECK(role IN ('OWNER','MEMBER')),PRIMARY KEY(workspace_id,user_id))");
            s.execute("CREATE TABLE IF NOT EXISTS projects(id INTEGER PRIMARY KEY,workspace_id INTEGER NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,name TEXT NOT NULL,description TEXT NOT NULL DEFAULT '', git_url TEXT NOT NULL DEFAULT '',created_by INTEGER NOT NULL REFERENCES users(id),created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            s.execute("CREATE INDEX IF NOT EXISTS projects_workspace ON projects(workspace_id)");
            s.execute("CREATE TABLE IF NOT EXISTS project_members(project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,PRIMARY KEY(project_id,user_id))");
            s.execute("CREATE TABLE IF NOT EXISTS tasks(id INTEGER PRIMARY KEY,project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,title TEXT NOT NULL,status TEXT NOT NULL DEFAULT 'TODO' CHECK(status IN ('TODO','IN_PROGRESS','DONE')),priority TEXT NOT NULL DEFAULT 'MEDIUM' CHECK(priority IN ('LOW','MEDIUM','HIGH')),due_date TEXT,assignee_id INTEGER REFERENCES users(id),created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            s.execute("CREATE INDEX IF NOT EXISTS tasks_project ON tasks(project_id)");
            s.execute("CREATE TABLE IF NOT EXISTS documents(id INTEGER PRIMARY KEY,project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,title TEXT NOT NULL,url TEXT NOT NULL,created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            s.execute("CREATE TABLE IF NOT EXISTS messages(id INTEGER PRIMARY KEY,project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,user_id INTEGER NOT NULL REFERENCES users(id),body TEXT NOT NULL,created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            s.execute("CREATE INDEX IF NOT EXISTS messages_project ON messages(project_id,id)");
        }
    }
    private static void handle(HttpExchange e) {
        try {
            String path=e.getRequestURI().getPath(), method=e.getRequestMethod();
            e.getResponseHeaders().set("X-Content-Type-Options","nosniff");
            e.getResponseHeaders().set("Referrer-Policy","same-origin");
            e.getResponseHeaders().set("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'none'");
            if (!path.startsWith("/api/")) { staticFile(e,path,method);return; }
            e.getResponseHeaders().set("Cache-Control","no-store");
            if (!(method.equals("GET")||method.equals("POST")||method.equals("PATCH")||method.equals("DELETE"))) throw new Failure(405,"method_not_allowed");
            if (!method.equals("GET")) checkOrigin(e);
            try(Connection c=db()) { route(e,c,path,method); }
        } catch (Failure ex){send(e,ex.status,Map.of("error",ex.code));}
          catch(Exception ex){ex.printStackTrace();send(e,500,Map.of("error","internal_error"));}
    }
    private static void checkOrigin(HttpExchange e){
        String origin=e.getRequestHeaders().getFirst("Origin");
        if(origin==null) throw new Failure(403,"origin_required");
        try {
            URI u=URI.create(origin);
            if(!PUBLIC_ORIGIN.isBlank()){
                if(!origin.equals(PUBLIC_ORIGIN)) throw new Failure(403,"bad_origin");
            } else {
                String host=e.getRequestHeaders().getFirst("Host");
                if(host==null || !u.getAuthority().equalsIgnoreCase(host)) throw new Failure(403,"bad_origin");
            }
        }catch(IllegalArgumentException x){throw new Failure(403,"bad_origin");}
    }
    private static void route(HttpExchange e,Connection c,String path,String method) throws Exception {
        if(path.equals("/api/health") && method.equals("GET")){send(e,200,Map.of("status","ok","service","nord-workspace-core"));return;}
        if(path.equals("/api/auth/register") && method.equals("POST")){register(e,c);return;}
        if(path.equals("/api/auth/login") && method.equals("POST")){login(e,c);return;}
        User user=authentication(e,c);
        if(path.equals("/api/me") && method.equals("GET")){send(e,200,Map.of("id",user.id,"name",user.name,"role",user.role));return;}
        if(path.equals("/api/auth/logout") && method.equals("POST")){
            String token=cookie(e);
            try(PreparedStatement p=c.prepareStatement("DELETE FROM sessions WHERE token_hash=?")){p.setString(1,sha(token));p.executeUpdate();}
            e.getResponseHeaders().add("Set-Cookie","nord_session=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0"+secureCookie());
            send(e,200,Map.of("ok",true));return;
        }
        if(path.startsWith("/api/admin/")){
            if(!user.role.equals("PLATFORM_OWNER"))throw new Failure(403,"owner_required");
            if(!method.equals("GET"))throw new Failure(405,"method_not_allowed");
            String suffix=path.substring("/api/admin/".length());
            if(!Set.of("health","stats","users").contains(suffix))throw new Failure(404,"not_found");
            adminProxy(e,suffix);return;
        }
        if(path.equals("/api/workspaces")){
            if(method.equals("GET")){
                List<Map<String,Object>> v=query(c,"SELECT w.id,w.name,w.icon,w.owner_id AS ownerId FROM workspaces w JOIN workspace_members m ON m.workspace_id=w.id WHERE m.user_id=? ORDER BY w.id",user.id);
                send(e,200,Map.of("workspaces",v));return;
            }
            if(method.equals("POST")){
                JsonNode j=body(e);String name=required(j,"name",80);
                long id=insert(c,"INSERT INTO workspaces(name,icon,owner_id) VALUES (?,?,?)",name,"N",user.id);
                update(c,"INSERT INTO workspace_members(workspace_id,user_id,role) VALUES (?,?,'OWNER')",id,user.id);
                send(e,201,Map.of("id",id));return;
            }
        }
        String[] s=path.split("/");
        if(s.length>=4 && s[2].equals("workspaces")){
            long wid=number(s[3]);workspaceAccess(c,user,wid);
            if(s.length==5 && s[4].equals("members")){
                if(method.equals("GET")){send(e,200,Map.of("members",query(c,"SELECT u.id,u.name,m.role FROM workspace_members m JOIN users u ON m.user_id=u.id WHERE m.workspace_id=?",wid)));return;}
                if(method.equals("POST")){
                    workspaceOwner(c,user,wid);
                    JsonNode j=body(e);long uid=userByEmail(c,required(j,"email",254));
                    update(c,"INSERT OR IGNORE INTO workspace_members(workspace_id,user_id,role) VALUES (?,?,'MEMBER')",wid,uid);
                    send(e,200,Map.of("ok",true));return;
                }
            }
        }
        if(path.equals("/api/projects")){
            long wid=number(queryParam(e,"workspaceId"));
            workspaceAccess(c,user,wid);
            if(method.equals("GET")){send(e,200,Map.of("projects",query(c,"SELECT p.id,p.name,p.description,p.git_url AS gitUrl,p.created_at AS createdAt FROM projects p JOIN project_members pm ON pm.project_id=p.id WHERE p.workspace_id=? AND pm.user_id=? ORDER BY p.id DESC",wid,user.id)));return;}
            if(method.equals("POST")){
                JsonNode j=body(e);
                long id=insert(c,"INSERT INTO projects(workspace_id,name,description,created_by) VALUES (?,?,?,?)",wid,required(j,"name",100),optional(j,"description",1000),user.id);
                update(c,"INSERT INTO project_members(project_id,user_id) VALUES (?,?)",id,user.id);
                send(e,201,Map.of("id",id));return;
            }
        }
        if(s.length>=4 && s[2].equals("projects")){
            long project=number(s[3]);projectAccess(c,user,project);
            if(s.length==4 && method.equals("GET")){
                List<Map<String,Object>> data=query(c,"SELECT id,workspace_id AS workspaceId,name,description,git_url AS gitUrl FROM projects WHERE id=?",project);
                send(e,200,data.get(0));return;
            }
            if(s.length==5){
                String section=s[4];
                if(section.equals("tasks")){
                    if(method.equals("GET")){send(e,200,Map.of("tasks",query(c,"SELECT id,title,status,priority,due_date AS dueDate,assignee_id AS assigneeId,created_at AS createdAt FROM tasks WHERE project_id=? ORDER BY id DESC",project)));return;}
                    if(method.equals("POST")){
                        JsonNode j=body(e);long id=insert(c,"INSERT INTO tasks(project_id,title,priority,due_date) VALUES (?,?,?,?)",project,required(j,"title",240),choice(j,"priority",Set.of("LOW","MEDIUM","HIGH"),"MEDIUM"),nullable(j,"dueDate",10));
                        send(e,201,Map.of("id",id));return;
                    }
                }
                if(section.equals("documents")){
                    if(method.equals("GET")){send(e,200,Map.of("documents",query(c,"SELECT id,title,url,created_at AS createdAt FROM documents WHERE project_id=? ORDER BY id DESC",project)));return;}
                    if(method.equals("POST")){
                        JsonNode j=body(e);String url=required(j,"url",2048);
                        if(!url.startsWith("https://"))throw new Failure(400,"https_url_required");
                        long id=insert(c,"INSERT INTO documents(project_id,title,url) VALUES (?,?,?)",project,required(j,"title",160),url);
                        send(e,201,Map.of("id",id));return;
                    }
                }
                if(section.equals("git") && method.equals("PATCH")){
                    JsonNode j=body(e);String url=optional(j,"url",2048);
                    if(!url.isBlank() && !(url.startsWith("https://github.com/") || url.startsWith("https://gitlab.com/")))throw new Failure(400,"git_url_invalid");
                    update(c,"UPDATE projects SET git_url=? WHERE id=?",url,project);send(e,200,Map.of("ok",true));return;
                }
                if(section.equals("members")){
                    if(method.equals("GET")){send(e,200,Map.of("members",query(c,"SELECT u.id,u.name FROM users u JOIN project_members pm ON pm.user_id=u.id WHERE pm.project_id=?",project)));return;}
                    if(method.equals("POST")){
                        if(!projectCreator(c,user,project))throw new Failure(403,"project_owner_required");
                        JsonNode j=body(e);long uid=userByEmail(c,required(j,"email",254));
                        long ws=scalar(c,"SELECT workspace_id FROM projects WHERE id=?",project);
                        if(scalar(c,"SELECT COUNT(*) FROM workspace_members WHERE workspace_id=? AND user_id=?",ws,uid)==0)throw new Failure(400,"invite_to_workspace_first");
                        update(c,"INSERT OR IGNORE INTO project_members(project_id,user_id) VALUES (?,?)",project,uid);
                        send(e,200,Map.of("ok",true));return;
                    }
                }
                if(section.equals("messages")){
                    if(method.equals("GET")){send(e,200,Map.of("messages",query(c,"SELECT m.id,m.body,m.created_at AS createdAt,u.name AS author FROM messages m JOIN users u ON u.id=m.user_id WHERE m.project_id=? ORDER BY m.id DESC LIMIT 100",project)));return;}
                    if(method.equals("POST")){
                        if(scalar(c,"SELECT COUNT(*) FROM project_members WHERE project_id=?",project)<2)throw new Failure(409,"team_required");
                        long id=insert(c,"INSERT INTO messages(project_id,user_id,body) VALUES (?,?,?)",project,user.id,required(body(e),"body",2000));
                        send(e,201,Map.of("id",id));return;
                    }
                }
            }
        }
        if(s.length==4 && s[2].equals("tasks") && method.equals("PATCH")){
            long id=number(s[3]);long project=scalar(c,"SELECT project_id FROM tasks WHERE id=?",id);projectAccess(c,user,project);
            JsonNode j=body(e);String status=choice(j,"status",Set.of("TODO","IN_PROGRESS","DONE"),"TODO");
            update(c,"UPDATE tasks SET status=? WHERE id=?",status,id);send(e,200,Map.of("ok",true));return;
        }
        throw new Failure(404,"not_found");
    }
    private static void register(HttpExchange e,Connection c) throws Exception {
        JsonNode j=body(e);String email=required(j,"email",254).toLowerCase(Locale.ROOT).trim();
        String name=required(j,"name",80),password=required(j,"password",200);
        if(!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))throw new Failure(400,"invalid_email");
        if(password.length()<12)throw new Failure(400,"password_too_short");
        String hash=passwordHash(password);
        // A write lock serializes bootstrap so exactly the first committed registration becomes owner.
        try(Statement stmt=c.createStatement()){
            stmt.execute("BEGIN IMMEDIATE");
            try {
                boolean first=scalar(c,"SELECT COUNT(*) FROM users")==0;
                long id=insert(c,"INSERT INTO users(name,email,password_hash,role) VALUES (?,?,?,?)",name,email,hash,first?"PLATFORM_OWNER":"USER");
                if(first){
                    long wid=insert(c,"INSERT INTO workspaces(name,icon,owner_id) VALUES (?,?,?)","O meu espaço","N",id);
                    update(c,"INSERT INTO workspace_members(workspace_id,user_id,role) VALUES (?,?,'OWNER')",wid,id);
                }
                stmt.execute("COMMIT");session(e,c,id);
                send(e,201,Map.of("id",id,"role",first?"PLATFORM_OWNER":"USER"));
            } catch(Exception x){stmt.execute("ROLLBACK");if(x instanceof SQLException)throw new Failure(409,"email_already_exists");throw x;}
        }
    }
    private static void login(HttpExchange e,Connection c) throws Exception {
        JsonNode j=body(e);String email=required(j,"email",254);
        String password=required(j,"password",200);
        try(PreparedStatement p=c.prepareStatement("SELECT id,password_hash FROM users WHERE email=?")){
            p.setString(1,email);try(ResultSet r=p.executeQuery()){
                if(!r.next())throw new Failure(401,"invalid_credentials");
                long id=r.getLong(1);String hash=r.getString(2);
                if(!passwordVerify(password,hash))throw new Failure(401,"invalid_credentials");
                session(e,c,id);send(e,200,Map.of("ok",true));
            }
        }
    }
    private static void session(HttpExchange e,Connection c,long id) throws Exception {
        String token=random(32);
        update(c,"INSERT INTO sessions(token_hash,user_id,expires_at) VALUES (?,?,?)",sha(token),id,Instant.now().plus(Duration.ofDays(7)).getEpochSecond());
        e.getResponseHeaders().add("Set-Cookie","nord_session="+token+"; Path=/; HttpOnly; SameSite=Strict; Max-Age=604800"+secureCookie());
    }
    private static String secureCookie(){return PUBLIC_ORIGIN.startsWith("https://")?"; Secure":"";}
    private static User authentication(HttpExchange e,Connection c) throws SQLException {
        String token=cookie(e);if(token.isBlank())throw new Failure(401,"login_required");
        try(PreparedStatement p=c.prepareStatement("SELECT u.id,u.name,u.role FROM sessions s JOIN users u ON s.user_id=u.id WHERE s.token_hash=? AND s.expires_at>?")){
            p.setString(1,sha(token));p.setLong(2,Instant.now().getEpochSecond());
            try(ResultSet r=p.executeQuery()){if(r.next())return new User(r.getLong(1),r.getString(2),r.getString(3));}
        }
        throw new Failure(401,"login_required");
    }
    private static String cookie(HttpExchange e){
        String h=e.getRequestHeaders().getFirst("Cookie");if(h==null)return "";
        for(String p:h.split(";")){String[] x=p.trim().split("=",2);if(x.length==2 && x[0].equals("nord_session"))return x[1];}
        return "";
    }
    private static void workspaceAccess(Connection c,User u,long id)throws SQLException {
        if(scalar(c,"SELECT COUNT(*) FROM workspace_members WHERE workspace_id=? AND user_id=?",id,u.id)==0)throw new Failure(404,"workspace_not_found");
    }
    private static void workspaceOwner(Connection c,User u,long id)throws SQLException {
        if(scalar(c,"SELECT COUNT(*) FROM workspace_members WHERE workspace_id=? AND user_id=? AND role='OWNER'",id,u.id)==0)throw new Failure(403,"workspace_owner_required");
    }
    private static void projectAccess(Connection c,User u,long project)throws SQLException {
        if(scalar(c,"SELECT COUNT(*) FROM projects p JOIN project_members pm ON pm.project_id=p.id WHERE p.id=? AND pm.user_id=?",project,u.id)==0)throw new Failure(404,"project_not_found");
    }
    private static boolean projectCreator(Connection c,User u,long p)throws SQLException {return scalar(c,"SELECT COUNT(*) FROM projects WHERE id=? AND created_by=?",p,u.id)>0;}
    private static long userByEmail(Connection c,String email)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("SELECT id FROM users WHERE email=?")){p.setString(1,email);try(ResultSet r=p.executeQuery()){if(r.next())return r.getLong(1);}}
        throw new Failure(404,"user_not_found");
    }
    private static long scalar(Connection c,String sql,Object... params)throws SQLException{
        try(PreparedStatement p=prepare(c,sql,params);ResultSet r=p.executeQuery()){return r.next()?r.getLong(1):0;}
    }
    private static int update(Connection c,String sql,Object... params)throws SQLException{
        try(PreparedStatement p=prepare(c,sql,params)){return p.executeUpdate();}
    }
    private static long insert(Connection c,String sql,Object... params)throws SQLException{
        try(PreparedStatement p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){
            for(int i=0;i<params.length;i++)p.setObject(i+1,params[i]);p.executeUpdate();
            try(ResultSet keys=p.getGeneratedKeys()){if(keys.next())return keys.getLong(1);}
        }
        return scalar(c,"SELECT last_insert_rowid()");
    }
    private static PreparedStatement prepare(Connection c,String sql,Object...params)throws SQLException {
        PreparedStatement p=c.prepareStatement(sql);for(int i=0;i<params.length;i++)p.setObject(i+1,params[i]);return p;
    }
    private static List<Map<String,Object>> query(Connection c,String sql,Object...params)throws SQLException{
        List<Map<String,Object>> out=new ArrayList<>();
        try(PreparedStatement p=prepare(c,sql,params);ResultSet r=p.executeQuery()){
            ResultSetMetaData m=r.getMetaData();
            while(r.next()){Map<String,Object> row=new LinkedHashMap<>();for(int i=1;i<=m.getColumnCount();i++)row.put(m.getColumnLabel(i),r.getObject(i));out.add(row);}
        }return out;
    }
    private static JsonNode body(HttpExchange e)throws IOException{
        byte[] b=e.getRequestBody().readNBytes(65537);if(b.length>65536)throw new Failure(413,"request_too_large");
        try{return JSON.readTree(b);}catch(Exception x){throw new Failure(400,"invalid_json");}
    }
    private static String required(JsonNode j,String k,int max){
        if(j==null || !j.has(k)||!j.get(k).isTextual())throw new Failure(400,"required_"+k);
        String v=j.get(k).asText().trim();if(v.isBlank()||v.length()>max)throw new Failure(400,"invalid_"+k);return v;
    }
    private static String optional(JsonNode j,String k,int max){if(j==null||!j.has(k)||j.get(k).isNull())return "";String v=j.get(k).asText().trim();if(v.length()>max)throw new Failure(400,"invalid_"+k);return v;}
    private static String nullable(JsonNode j,String k,int max){String v=optional(j,k,max);return v.isBlank()?null:v;}
    private static String choice(JsonNode j,String k,Set<String> allowed,String fallback){String v=optional(j,k,30);if(v.isBlank())return fallback;if(!allowed.contains(v))throw new Failure(400,"invalid_"+k);return v;}
    private static long number(String value){try{long x=Long.parseLong(value);if(x<=0)throw new NumberFormatException();return x;}catch(Exception e){throw new Failure(400,"invalid_id");}}
    private static String queryParam(HttpExchange e,String key){
        String query=e.getRequestURI().getRawQuery();if(query!=null)for(String part:query.split("&")){String[] kv=part.split("=",2);if(kv[0].equals(key)&&kv.length==2)return URLDecoder.decode(kv[1],StandardCharsets.UTF_8);}
        throw new Failure(400,"missing_"+key);
    }
    private static void adminProxy(HttpExchange e,String name)throws Exception {
        if(ADMIN_SECRET.length()<32)throw new Failure(503,"admin_not_configured");
        HttpRequest req=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+ADMIN_PORT+"/internal/"+name))
            .header("X-Nord-Internal",ADMIN_SECRET).timeout(Duration.ofSeconds(4)).GET().build();
        HttpResponse<String> r=HttpClient.newHttpClient().send(req,HttpResponse.BodyHandlers.ofString());
        byte[] b=r.body().getBytes(StandardCharsets.UTF_8);
        e.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");
        e.sendResponseHeaders(r.statusCode(),b.length);try(OutputStream out=e.getResponseBody()){out.write(b);}
    }
    private static void staticFile(HttpExchange e,String path,String method)throws IOException{
        if(!method.equals("GET"))throw new Failure(405,"method_not_allowed");
        if(path.equals("/"))path="/index.html";
        if(!Set.of("/index.html","/app.js","/style.css").contains(path))throw new Failure(404,"not_found");
        try(InputStream in=CoreServer.class.getResourceAsStream("/web"+path)){
            if(in==null)throw new Failure(404,"not_found");
            byte[] b=in.readAllBytes();
            e.getResponseHeaders().set("Content-Type",path.endsWith(".js")?"text/javascript; charset=utf-8":path.endsWith(".css")?"text/css; charset=utf-8":"text/html; charset=utf-8");
            e.sendResponseHeaders(200,b.length);try(OutputStream o=e.getResponseBody()){o.write(b);}
        }
    }
    private static String passwordHash(String pass)throws Exception{
        byte[] salt=new byte[16];RNG.nextBytes(salt);byte[] hash=derive(pass,salt);
        return Base64.getEncoder().encodeToString(salt)+":"+Base64.getEncoder().encodeToString(hash);
    }
    private static boolean passwordVerify(String pass,String stored)throws Exception{
        String[] s=stored.split(":");if(s.length!=2)return false;
        return MessageDigest.isEqual(derive(pass,Base64.getDecoder().decode(s[0])),Base64.getDecoder().decode(s[1]));
    }
    private static byte[] derive(String pass,byte[] salt)throws Exception{
        PBEKeySpec spec=new PBEKeySpec(pass.toCharArray(),salt,210000,256);
        try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}finally{spec.clearPassword();}
    }
    private static String random(int n){byte[] v=new byte[n];RNG.nextBytes(v);return Base64.getUrlEncoder().withoutPadding().encodeToString(v);}
    private static String sha(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception x){throw new IllegalStateException(x);}}
    private static void send(HttpExchange e,int status,Object obj){
        try{byte[] b=JSON.writeValueAsBytes(obj);e.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");e.sendResponseHeaders(status,b.length);try(OutputStream o=e.getResponseBody()){o.write(b);}}catch(IOException ignore){}
    }
}
