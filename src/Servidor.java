import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.security.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Donantes por la Vida - servidor del prototipo (solo usa el JDK, sin librerias externas). */
public class Servidor {
    static final Path WEB = Path.of("web").toAbsolutePath().normalize(), DATA = Path.of("data");
    static final Object CANDADO = new Object();
    // ---------- administrador: sesiones y control de intentos ----------
    static final Map<String, Long> SESIONES = new ConcurrentHashMap<>();   // token -> vencimiento (ms)
    static final Map<String, long[]> INTENTOS = new ConcurrentHashMap<>(); // ip -> {fallos, desde}
    static final long SESION_MS = 30 * 60 * 1000L;
    static final SecureRandom AZAR = new SecureRandom();

    static class Preg { String t; List<String[]> o = new ArrayList<>(); }

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("clave")) {   // java src/Servidor.java clave NUEVA_CLAVE
            guardarAdmin("admin", args[1]);
            System.out.println("Clave del administrador actualizada.");
            return;
        }
        prepararAdmin();
        int puerto = 8080;
        HttpServer s = HttpServer.create(new InetSocketAddress(puerto), 0);
        s.createContext("/api/preguntas", ex -> json(ex, preguntasJson()));
        s.createContext("/api/jornadas", ex -> json(ex, jornadasJson()));
        s.createContext("/api/evaluar", ex -> { if (post(ex)) json(ex, evaluar(form(ex))); });
        s.createContext("/api/solicitud", ex -> { if (post(ex)) json(ex, solicitud(form(ex))); });
        s.createContext("/api/admin/login", ex -> { if (post(ex)) adminLogin(ex); });
        s.createContext("/api/admin/logout", ex -> { if (post(ex)) adminLogout(ex); });
        s.createContext("/api/admin/estado", ex -> json(ex, "{\"admin\":" + esAdmin(ex) + "}"));
        s.createContext("/api/admin/solicitudes", ex -> { if (esAdmin(ex)) json(ex, solicitudesJson()); else prohibido(ex); });
        s.createContext("/api/admin/jornada/agregar", ex -> { if (post(ex)) { if (esAdmin(ex)) json(ex, agregarJornada(form(ex))); else prohibido(ex); } });
        s.createContext("/api/admin/jornada/eliminar", ex -> { if (post(ex)) { if (esAdmin(ex)) json(ex, eliminarJornada(form(ex))); else prohibido(ex); } });
        s.createContext("/", Servidor::estatico);
        s.start();
        System.out.println("Donantes por la Vida funcionando en http://localhost:" + puerto + "  (Ctrl+C para detener)");
    }

    // ---------- datos (archivos de texto editables en la carpeta data) ----------
    static List<String[]> lineas(String archivo) throws IOException {
        List<String[]> r = new ArrayList<>();
        for (String l : Files.readAllLines(DATA.resolve(archivo), StandardCharsets.UTF_8)) {
            l = l.strip();
            if (l.startsWith("\uFEFF")) l = l.substring(1);
            if (l.isEmpty() || l.startsWith("#")) continue;
            r.add(l.split("\\|", -1));
        }
        return r;
    }

    static List<Preg> preguntas() throws IOException {
        List<Preg> r = new ArrayList<>();
        for (String[] l : lineas("preguntas.txt")) {
            if (l[0].equals("P")) { Preg p = new Preg(); p.t = l[1]; r.add(p); }
            else if (l[0].equals("O") && !r.isEmpty()) r.get(r.size() - 1).o.add(new String[]{l[1], l[2], l[3]});
        }
        return r;
    }

    static String resultado(String nivel, int campo) throws IOException {
        for (String[] l : lineas("preguntas.txt")) if (l[0].equals("R") && l[1].equals(nivel)) return l[campo];
        return "";
    }

    static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", "");
    }

    static String preguntasJson() throws IOException {
        StringJoiner todo = new StringJoiner(",", "[", "]");
        for (Preg p : preguntas()) {
            StringJoiner ops = new StringJoiner(",", "[", "]");
            for (String[] o : p.o) ops.add("\"" + esc(o[0]) + "\"");
            todo.add("{\"t\":\"" + esc(p.t) + "\",\"o\":" + ops + "}");
        }
        return todo.toString();
    }

    static String jornadasJson() throws IOException {
        StringJoiner todo = new StringJoiner(",", "[", "]");
        for (String[] j : lineas("jornadas.txt"))
            todo.add("{\"id\":" + j[0] + ",\"titulo\":\"" + esc(j[1]) + "\",\"fecha\":\"" + esc(j[2])
                    + "\",\"hora\":\"" + esc(j[3]) + "\",\"lugar\":\"" + esc(j[4]) + "\"}");
        return todo.toString();
    }

    // ---------- autoevaluacion orientativa (nunca certifica aptitud) ----------
    static String evaluar(Map<String, String> f) throws IOException {
        List<Preg> ps = preguntas();
        String[] r = f.getOrDefault("r", "").split(",");
        if (r.length != ps.size()) return "{\"error\":\"Responde todas las preguntas.\"}";
        int peor = 0;
        StringJoiner motivos = new StringJoiner(",", "[", "]");
        for (int i = 0; i < ps.size(); i++) {
            int k;
            try { k = Integer.parseInt(r[i].trim()); } catch (NumberFormatException e) { return "{\"error\":\"Respuesta no valida.\"}"; }
            if (k < 0 || k >= ps.get(i).o.size()) return "{\"error\":\"Respuesta no valida.\"}";
            String[] op = ps.get(i).o.get(k);
            int nivel = op[1].equals("consultar") ? 2 : op[1].equals("diferir") ? 1 : 0;
            if (nivel > 0) motivos.add("\"" + esc(op[2]) + "\"");
            peor = Math.max(peor, nivel);
        }
        String n = peor == 2 ? "consultar" : peor == 1 ? "diferir" : "favorable";
        // las respuestas NO se guardan (datos minimos)
        return "{\"nivel\":\"" + n + "\",\"titulo\":\"" + esc(resultado(n, 2)) + "\",\"mensaje\":\""
                + esc(resultado(n, 3)) + "\",\"motivos\":" + motivos + "}";
    }

    // ---------- solicitud de hora + recordatorio (simulado) ----------
    static String solicitud(Map<String, String> f) throws IOException {
        String nombre = f.getOrDefault("nombre", "").replaceAll("[\\r\\n]+", " ").strip(), correo = f.getOrDefault("correo", "").strip();
        boolean acepta = "1".equals(f.get("recordatorio"));
        if (nombre.isEmpty() || nombre.length() > 100) return "{\"ok\":false,\"error\":\"Escribe tu nombre.\"}";
        if (!correo.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") || correo.length() > 100)
            return "{\"ok\":false,\"error\":\"Escribe un correo v\u00e1lido.\"}";
        String[] jor = null;
        for (String[] j : lineas("jornadas.txt")) if (j[0].equals(f.get("jornada"))) jor = j;
        if (jor == null) return "{\"ok\":false,\"error\":\"Elige una jornada.\"}";
        String ahora = LocalDateTime.now().withNano(0).toString();
        synchronized (CANDADO) {
            anexar("solicitudes.csv", "fecha_registro,jornada,nombre,correo,acepta_recordatorios",
                    ahora, jor[1] + " " + jor[2], nombre, correo, acepta ? "si" : "no");
            if (acepta) {
                anexar("recordatorios.csv", "fecha_registro,correo,jornada,envio_previsto,estado",
                        ahora, correo, jor[1] + " " + jor[2], "1 dia antes de la jornada", "SIMULADO");
                System.out.println("[RECORDATORIO SIMULADO] Para: " + correo + " | " + jor[1] + " | " + jor[2]);
            }
        }
        return "{\"ok\":true,\"recordatorio\":" + acepta + "}";
    }

    static void anexar(String archivo, String cabecera, String... campos) throws IOException {
        Path p = DATA.resolve(archivo);
        StringBuilder sb = new StringBuilder();
        if (!Files.exists(p)) sb.append(cabecera).append("\n");
        StringJoiner sj = new StringJoiner(",");
        for (String c : campos) {
            if (!c.isEmpty() && "=+-@".indexOf(c.charAt(0)) >= 0) c = "'" + c;   // evita formulas en Excel
            sj.add("\"" + c.replace("\"", "\"\"") + "\"");
        }
        sb.append(sj).append("\n");
        Files.writeString(p, sb.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    // ---------- administrador: clave cifrada (PBKDF2 con sal), sesion y rutas protegidas ----------
    static byte[] pbkdf2(String clave, byte[] sal) throws IOException {
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(new PBEKeySpec(clave.toCharArray(), sal, 120_000, 256)).getEncoded();
        } catch (GeneralSecurityException e) { throw new IOException(e); }
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    static byte[] deHex(String h) {
        byte[] r = new byte[h.length() / 2];
        for (int i = 0; i < r.length; i++) r[i] = (byte) Integer.parseInt(h.substring(2 * i, 2 * i + 2), 16);
        return r;
    }

    static void guardarAdmin(String usuario, String clave) throws IOException {
        byte[] sal = new byte[16];
        AZAR.nextBytes(sal);
        Files.writeString(DATA.resolve("admin.txt"), usuario + "|" + hex(sal) + "|" + hex(pbkdf2(clave, sal)) + "\n", StandardCharsets.UTF_8);
    }

    static void prepararAdmin() throws IOException {
        if (!Files.exists(DATA.resolve("admin.txt"))) {
            guardarAdmin("admin", "admin123");
            System.out.println("AVISO: se creo el administrador 'admin' con la clave 'admin123'.");
            System.out.println("       Cambiala con:  java src/Servidor.java clave NUEVA_CLAVE");
        }
    }

    static void adminLogin(HttpExchange ex) throws IOException {
        String ip = ex.getRemoteAddress().getAddress().getHostAddress();
        long ahora = System.currentTimeMillis();
        long[] in = INTENTOS.computeIfAbsent(ip, k -> new long[]{0, ahora});
        if (ahora - in[1] > 300_000) { in[0] = 0; in[1] = ahora; }
        Map<String, String> f = form(ex);
        if (in[0] >= 5) { json(ex, "{\"ok\":false,\"error\":\"Demasiados intentos. Espera 5 minutos.\"}"); return; }
        List<String[]> reg = lineas("admin.txt");
        boolean ok = false;
        if (!reg.isEmpty() && f.getOrDefault("usuario", "").strip().equals(reg.get(0)[0]))
            ok = MessageDigest.isEqual(pbkdf2(f.getOrDefault("clave", ""), deHex(reg.get(0)[1])), deHex(reg.get(0)[2]));
        if (!ok) { in[0]++; json(ex, "{\"ok\":false,\"error\":\"Usuario o clave incorrectos.\"}"); return; }
        in[0] = 0;
        byte[] t = new byte[24];
        AZAR.nextBytes(t);
        String token = hex(t);
        SESIONES.put(token, ahora + SESION_MS);
        ex.getResponseHeaders().add("Set-Cookie", "sid=" + token + "; HttpOnly; SameSite=Strict; Path=/; Max-Age=1800");
        json(ex, "{\"ok\":true}");
    }

    static String tokenDe(HttpExchange ex) {
        String c = ex.getRequestHeaders().getFirst("Cookie");
        if (c == null) return null;
        for (String p : c.split(";")) { p = p.strip(); if (p.startsWith("sid=")) return p.substring(4); }
        return null;
    }

    static boolean esAdmin(HttpExchange ex) {
        String t = tokenDe(ex);
        Long v = t == null ? null : SESIONES.get(t);
        return v != null && v > System.currentTimeMillis();
    }

    static void adminLogout(HttpExchange ex) throws IOException {
        String t = tokenDe(ex);
        if (t != null) SESIONES.remove(t);
        ex.getResponseHeaders().add("Set-Cookie", "sid=; HttpOnly; SameSite=Strict; Path=/; Max-Age=0");
        json(ex, "{\"ok\":true}");
    }

    static void prohibido(HttpExchange ex) throws IOException {
        enviar(ex, 401, "application/json; charset=utf-8", "{\"error\":\"No autorizado\"}".getBytes(StandardCharsets.UTF_8));
    }

    static List<String> campos(String l) {   // lee una fila CSV con comillas
        List<String> r = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < l.length(); i++) {
            char ch = l.charAt(i);
            if (q) {
                if (ch == '"') { if (i + 1 < l.length() && l.charAt(i + 1) == '"') { sb.append('"'); i++; } else q = false; }
                else sb.append(ch);
            } else if (ch == '"') q = true;
            else if (ch == ',') { r.add(sb.toString()); sb.setLength(0); }
            else sb.append(ch);
        }
        r.add(sb.toString());
        return r;
    }

    static String solicitudesJson() throws IOException {
        Path p = DATA.resolve("solicitudes.csv");
        if (!Files.exists(p)) return "[]";
        List<String> ls = Files.readAllLines(p, StandardCharsets.UTF_8);
        StringJoiner todo = new StringJoiner(",", "[", "]");
        for (int i = ls.size() - 1; i >= 1; i--) {   // las mas recientes primero
            List<String> c = campos(ls.get(i));
            if (c.size() < 5) continue;
            todo.add("{\"fecha\":\"" + esc(c.get(0)) + "\",\"jornada\":\"" + esc(c.get(1)) + "\",\"nombre\":\"" + esc(c.get(2))
                    + "\",\"correo\":\"" + esc(c.get(3)) + "\",\"recordatorio\":\"" + esc(c.get(4)) + "\"}");
        }
        return todo.toString();
    }

    static String limpiar(String s) { return s == null ? "" : s.replaceAll("[|\\r\\n]+", " ").strip(); }

    static String agregarJornada(Map<String, String> f) throws IOException {
        String[] v = {limpiar(f.get("titulo")), limpiar(f.get("fecha")), limpiar(f.get("hora")), limpiar(f.get("lugar"))};
        for (String x : v) if (x.isEmpty() || x.length() > 120) return "{\"ok\":false,\"error\":\"Completa todos los campos (m\u00e1ximo 120 caracteres).\"}";
        synchronized (CANDADO) {
            int id = 0;
            for (String[] j : lineas("jornadas.txt")) id = Math.max(id, Integer.parseInt(j[0].strip()));
            Files.writeString(DATA.resolve("jornadas.txt"), (id + 1) + "|" + v[0] + "|" + v[1] + "|" + v[2] + "|" + v[3] + "\n",
                    StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        }
        return "{\"ok\":true}";
    }

    static String eliminarJornada(Map<String, String> f) throws IOException {
        String id = limpiar(f.get("id"));
        synchronized (CANDADO) {
            List<String> sale = new ArrayList<>();
            boolean quito = false;
            for (String l : Files.readAllLines(DATA.resolve("jornadas.txt"), StandardCharsets.UTF_8)) {
                if (!l.strip().startsWith("#") && l.strip().startsWith(id + "|")) { quito = true; continue; }
                sale.add(l);
            }
            if (!quito) return "{\"ok\":false,\"error\":\"No existe esa jornada.\"}";
            Files.write(DATA.resolve("jornadas.txt"), sale, StandardCharsets.UTF_8);
        }
        return "{\"ok\":true}";
    }

    // ---------- utilidades HTTP ----------
    static boolean post(HttpExchange ex) throws IOException {
        if (ex.getRequestMethod().equals("POST")) return true;
        ex.sendResponseHeaders(405, -1); ex.close(); return false;
    }

    static Map<String, String> form(HttpExchange ex) throws IOException {
        String cuerpo = new String(ex.getRequestBody().readNBytes(10_000), StandardCharsets.UTF_8);
        Map<String, String> m = new HashMap<>();
        for (String par : cuerpo.split("&")) {
            int i = par.indexOf('=');
            if (i > 0) m.put(URLDecoder.decode(par.substring(0, i), StandardCharsets.UTF_8),
                             URLDecoder.decode(par.substring(i + 1), StandardCharsets.UTF_8));
        }
        return m;
    }

    static void enviar(HttpExchange ex, int codigo, String tipo, byte[] datos) throws IOException {
        ex.getResponseHeaders().set("Content-Type", tipo);
        ex.sendResponseHeaders(codigo, datos.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(datos); }
    }

    static void json(HttpExchange ex, String cuerpo) throws IOException {
        enviar(ex, 200, "application/json; charset=utf-8", cuerpo.getBytes(StandardCharsets.UTF_8));
    }

    static String tipoDe(String n) {
        if (n.endsWith(".html")) return "text/html; charset=utf-8";
        if (n.endsWith(".css")) return "text/css; charset=utf-8";
        if (n.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".mp4")) return "video/mp4";
        if (n.endsWith(".webm")) return "video/webm";
        return "application/octet-stream";
    }

    static void estatico(HttpExchange ex) throws IOException {
        String ruta = ex.getRequestURI().getPath();
        if (ruta.equals("/")) ruta = "/index.html";
        Path p = WEB.resolve(ruta.substring(1)).normalize();
        if (!p.startsWith(WEB) || !Files.isRegularFile(p)) { enviar(ex, 404, "text/plain; charset=utf-8", "No encontrado".getBytes()); return; }
        String n = p.getFileName().toString().toLowerCase();
        String tipo = tipoDe(n);
        enviar(ex, 200, tipo, Files.readAllBytes(p));
    }
}
