package org.sketchware.daygreen.ai;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import pro.sketchware.utility.FileUtil;

public final class AiChatClient {

    public static final class Proposal {
        public final String javaName;
        public final String eventKey;
        public final List<AiBlockWriter.Step> steps;
        public final String summary;

        Proposal(String javaName, String eventKey, List<AiBlockWriter.Step> steps, String summary) {
            this.javaName = javaName;
            this.eventKey = eventKey;
            this.steps = steps;
            this.summary = summary;
        }
    }

    public interface Callback {
        /** Dipanggil setiap satu tool dijalankan. Dipanggil dari background thread. */
        void onStep(String label);

        void onResult(String reply, List<Proposal> proposals);

        void onError(String error);
    }

    private static final int MAX_ROUNDS = 8;
    private static final int MAX_LIST = 200;
    private static final int MAX_READ_CHARS = 40_000;
    private static final int DEFAULT_READ_LINES = 300;
    private static final int MAX_READ_LINES = 1000;
    private static final int MAX_SEARCH_HITS = 60;
    private static final int MAX_FIND_HITS = 100;
    private static final int MAX_SCAN = 4000;
    private static final long MAX_TEXT_BYTES = 512L * 1024L;
    private static final List<String> SKIP_DIRS = Arrays.asList("build", ".git", "gen", "bin");

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .build();

    private static final String RULES =
            "\nAturan block:\n"
                    + "1. Panggil list_event_keys dulu untuk memilih event yang valid.\n"
                    + "2. Panggil list_blocks untuk mencari opcode. Jangan mengarang opcode.\n"
                    + "3. Jumlah params harus sama dengan params dari list_blocks.\n"
                    + "4. Gunakan append_blocks untuk menambah block. User harus menyetujui sebelum ditulis.\n"
                    + "5. Jangan bilang block sudah tertulis sebelum user menyetujui.\n"
                    + "\nAturan membaca project:\n"
                    + "6. Untuk pertanyaan tentang kode project, baca dulu dengan list_dir, find_files, search, atau read_file.\n"
                    + "7. Jangan mengarang isi file. Kalau belum dibaca, bilang belum dibaca.\n";

    private final AiSettingsStore store;
    private final String scId;
    private final String javaName;
    private final File projectRoot;
    private final JSONArray messages = new JSONArray();

    public AiChatClient(Context context, String scId, String javaName) {
        this.store = new AiSettingsStore(context);
        this.scId = scId;
        this.javaName = javaName;
        this.projectRoot = new File(FileUtil.getExternalStorageDir(), ".sketchware/mysc/" + scId);
        try {
            messages.put(message("system", store.getSystemPrompt(
                    "You are an AI assistant for Sketchware DayGreen. Be concise and precise.")
                    + "\nRoot project: .sketchware/mysc/" + scId + " (semua path relatif terhadap root ini)."
                    + "\nJawab singkat, jelas, dan pakai bahasa yang sama dengan user." + RULES));
        } catch (JSONException ignored) {
        }
    }

    /**
     * Jalan di background thread. Callback juga dipanggil dari background thread.
     */
    public void ask(String userText, Callback callback) {
        new Thread(() -> run(userText, callback)).start();
    }

    private void run(String userText, Callback callback) {
        try {
            int providerIndex = store.getProviderIndex();
            AiProviders.Provider provider = AiProviders.ALL[providerIndex];
            if (!provider.openAiCompatible) {
                callback.onError("Provider " + provider.name + " belum didukung di chat AI. "
                        + "Pilih OpenAI, DeepSeek, OpenRouter, atau Kimi.");
                return;
            }

            String apiKey = store.getApiKey(providerIndex);
            if (apiKey.isEmpty()) {
                callback.onError("API key belum diisi di pengaturan AI.");
                return;
            }

            String model = provider.models[store.getModelIndex()];
            messages.put(message("user", userText));

            List<Proposal> proposals = new ArrayList<>();
            for (int round = 0; round < MAX_ROUNDS; round++) {
                JSONObject reply = post(provider, apiKey, model);
                JSONObject assistant = reply.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message");
                messages.put(assistant);

                JSONArray calls = assistant.optJSONArray("tool_calls");
                if (calls == null || calls.length() == 0) {
                    callback.onResult(assistant.optString("content", ""), proposals);
                    return;
                }

                for (int i = 0; i < calls.length(); i++) {
                    JSONObject call = calls.getJSONObject(i);
                    JSONObject fn = call.getJSONObject("function");
                    String name = fn.getString("name");
                    JSONObject args = parseArgs(fn.optString("arguments", ""));
                    callback.onStep(describe(name, args));

                    String result;
                    try {
                        result = execute(name, args, proposals);
                    } catch (Exception e) {
                        result = "Error: " + (e.getMessage() == null ? e.toString() : e.getMessage());
                    }
                    messages.put(toolMessage(call.getString("id"), result));
                }
            }
            callback.onResult("(Batas putaran tool tercapai. Coba pertanyaan yang lebih sempit.)", proposals);
        } catch (Exception e) {
            callback.onError(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private String execute(String name, JSONObject args, List<Proposal> proposals) throws IOException, JSONException {
        switch (name) {
            case "list_blocks": {
                if (!store.isToolEnabled("list_blocks")) {
                    return disabled("list_blocks");
                }
                JSONArray out = new JSONArray();
                for (AiBlockCatalog.Entry e : AiBlockCatalog.search(args.optString("query", ""), 40)) {
                    JSONObject o = new JSONObject();
                    o.put("op", e.opCode);
                    o.put("spec", e.spec);
                    o.put("type", e.type);
                    o.put("params", new JSONArray(e.paramTypes));
                    out.put(o);
                }
                return out.toString();
            }

            case "list_event_keys": {
                if (!store.isToolEnabled("list_blocks")) {
                    return disabled("list_blocks");
                }
                return new JSONArray(AiBlockWriter.listEventKeys(scId, javaName)).toString();
            }

            case "append_blocks": {
                if (!store.isToolEnabled("append_blocks")) {
                    return disabled("append_blocks");
                }
                String eventKey = args.getString("event_key");
                JSONArray stepsJson = args.getJSONArray("steps");

                List<AiBlockWriter.Step> steps = new ArrayList<>();
                StringBuilder summary = new StringBuilder();
                for (int i = 0; i < stepsJson.length(); i++) {
                    JSONObject s = stepsJson.getJSONObject(i);
                    List<String> params = new ArrayList<>();
                    JSONArray p = s.optJSONArray("params");
                    if (p != null) {
                        for (int j = 0; j < p.length(); j++) {
                            params.add(p.optString(j, ""));
                        }
                    }
                    steps.add(new AiBlockWriter.Step(s.getString("op"), params));
                    summary.append(i + 1).append(". ").append(s.getString("op")).append(params).append('\n');
                }

                proposals.add(new Proposal(javaName, eventKey, steps, summary.toString().trim()));
                return "Menunggu persetujuan user. Jangan anggap sudah tertulis.";
            }

            case "list_dir": {
                if (!store.isToolEnabled("list_dir")) return disabled("list_dir");
                return listDir(args.optString("path", ""));
            }

            case "file_info": {
                if (!store.isToolEnabled("file_info")) return disabled("file_info");
                return fileInfo(args.optString("path", ""));
            }

            case "read_file": {
                if (!store.isToolEnabled("read_file")) return disabled("read_file");
                return readFile(args.optString("path", ""), args.optInt("offset", 1), args.optInt("limit", 0));
            }

            case "search": {
                if (!store.isToolEnabled("search")) return disabled("search");
                return search(args.optString("query", ""), args.optString("path", ""));
            }

            case "find_files": {
                if (!store.isToolEnabled("find_files")) return disabled("find_files");
                return findFiles(args.optString("name", ""));
            }

            default:
                return "Tool tidak dikenal: " + name;
        }
    }

    // ------------------------------------------------------------------ baca project (read-only)

    private String listDir(String rel) throws IOException, JSONException {
        File dir = resolve(rel);
        if (!dir.isDirectory()) {
            return "Bukan direktori: " + relative(dir);
        }
        File[] items = dir.listFiles();
        JSONArray out = new JSONArray();
        if (items != null) {
            Arrays.sort(items, (a, b) -> {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });
            for (File f : items) {
                if (out.length() >= MAX_LIST) break;
                JSONObject o = new JSONObject()
                        .put("name", f.getName())
                        .put("type", f.isDirectory() ? "dir" : "file");
                if (f.isFile()) o.put("size", f.length());
                out.put(o);
            }
        }
        return out.toString();
    }

    private String fileInfo(String rel) throws IOException, JSONException {
        File f = resolve(rel);
        JSONObject o = new JSONObject()
                .put("path", relative(f))
                .put("exists", f.exists())
                .put("type", f.isDirectory() ? "dir" : (f.isFile() ? "file" : "none"));
        if (f.isFile()) o.put("size", f.length());
        return o.toString();
    }

    private String readFile(String rel, int offset, int limit) throws IOException {
        File f = resolve(rel);
        if (!f.isFile()) return "Bukan file: " + relative(f);
        if (isBinary(f)) return "File biner, tidak bisa dibaca sebagai teks: " + relative(f);

        int start = Math.max(1, offset);
        int max = limit <= 0 ? DEFAULT_READ_LINES : Math.min(limit, MAX_READ_LINES);
        StringBuilder body = new StringBuilder();
        int line = 0;
        int shown = 0;
        boolean more = false;
        try (BufferedReader r = reader(f)) {
            String s;
            while ((s = r.readLine()) != null) {
                line++;
                if (line < start) continue;
                if (shown >= max || body.length() >= MAX_READ_CHARS) {
                    more = true;
                    break;
                }
                body.append(line).append(": ").append(s).append('\n');
                shown++;
            }
        }
        if (shown == 0) {
            return relative(f) + ": tidak ada baris pada offset " + start + ".";
        }
        String head = relative(f) + " (baris " + start + "-" + (start + shown - 1) + ")";
        if (more) head += ", lanjut dengan offset=" + (start + shown);
        return head + "\n" + body;
    }

    private String search(String query, String rel) throws IOException {
        if (query.isEmpty()) return "query kosong.";
        File base = resolve(rel);
        List<String> hits = new ArrayList<>();
        searchIn(base, query.toLowerCase(Locale.ROOT), hits, new int[]{0});
        if (hits.isEmpty()) return "Tidak ditemukan.";
        String out = String.join("\n", hits);
        return hits.size() >= MAX_SEARCH_HITS ? out + "\n(hasil dibatasi " + MAX_SEARCH_HITS + ")" : out;
    }

    private void searchIn(File f, String q, List<String> hits, int[] scanned) throws IOException {
        if (hits.size() >= MAX_SEARCH_HITS || scanned[0] >= MAX_SCAN) return;
        scanned[0]++;
        if (f.isDirectory()) {
            if (SKIP_DIRS.contains(f.getName())) return;
            File[] kids = f.listFiles();
            if (kids != null) {
                for (File k : kids) searchIn(k, q, hits, scanned);
            }
            return;
        }
        if (!f.isFile() || f.length() > MAX_TEXT_BYTES || isBinary(f)) return;
        try (BufferedReader r = reader(f)) {
            String s;
            int n = 0;
            while ((s = r.readLine()) != null && hits.size() < MAX_SEARCH_HITS) {
                n++;
                if (s.toLowerCase(Locale.ROOT).contains(q)) {
                    hits.add(relative(f) + ":" + n + ": " + clip(s.trim(), 200));
                }
            }
        }
    }

    private String findFiles(String name) throws IOException {
        if (name.isEmpty()) return "name kosong.";
        File root = resolve("");
        List<String> hits = new ArrayList<>();
        findIn(root, name.toLowerCase(Locale.ROOT), hits, new int[]{0});
        if (hits.isEmpty()) return "Tidak ditemukan.";
        return String.join("\n", hits);
    }

    private void findIn(File f, String q, List<String> hits, int[] scanned) throws IOException {
        if (hits.size() >= MAX_FIND_HITS || scanned[0] >= MAX_SCAN) return;
        scanned[0]++;
        String rel = relative(f);
        if (!rel.isEmpty() && f.getName().toLowerCase(Locale.ROOT).contains(q)) {
            hits.add(rel + (f.isDirectory() ? "/" : ""));
        }
        if (f.isDirectory() && !SKIP_DIRS.contains(f.getName())) {
            File[] kids = f.listFiles();
            if (kids != null) {
                for (File k : kids) findIn(k, q, hits, scanned);
            }
        }
    }

    /** Pastikan path tetap di dalam folder project (termasuk lewat ".." atau symlink). */
    private File resolve(String rel) throws IOException {
        if (!projectRoot.isDirectory()) {
            throw new IOException("Folder project tidak ditemukan: .sketchware/mysc/" + scId);
        }
        String root = projectRoot.getCanonicalPath();
        String clean = rel == null ? "" : rel.trim();
        File target = clean.isEmpty() ? new File(root) : new File(root, clean).getCanonicalFile();
        String tp = target.getCanonicalPath();
        if (!tp.equals(root) && !tp.startsWith(root + File.separator)) {
            throw new IOException("Path di luar folder project.");
        }
        return target;
    }

    private String relative(File f) throws IOException {
        String root = projectRoot.getCanonicalPath();
        String p = f.getCanonicalPath();
        return p.length() <= root.length() ? "" : p.substring(root.length() + 1);
    }

    private static BufferedReader reader(File f) throws IOException {
        return new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8));
    }

    private static boolean isBinary(File f) throws IOException {
        byte[] buf = new byte[1024];
        int n;
        try (FileInputStream in = new FileInputStream(f)) {
            n = in.read(buf);
        }
        for (int i = 0; i < n; i++) {
            if (buf[i] == 0) return true;
        }
        return false;
    }

    private static String clip(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    private static JSONObject parseArgs(String raw) throws JSONException {
        return raw == null || raw.trim().isEmpty() ? new JSONObject() : new JSONObject(raw);
    }

    /** Label singkat untuk daftar "langkah berpikir" di UI. */
    private static String describe(String name, JSONObject args) {
        String path = args.optString("path", "");
        switch (name) {
            case "list_blocks":
                return "Mencari block: " + args.optString("query", "semua");
            case "list_event_keys":
                return "Membaca event yang sudah ada";
            case "append_blocks":
                return "Menyiapkan usulan block";
            case "list_dir":
                return "Melihat folder " + (path.isEmpty() ? "root project" : path);
            case "file_info":
                return "Mengecek " + path;
            case "read_file":
                return "Membaca " + path;
            case "search":
                return "Mencari \"" + args.optString("query", "") + "\"";
            case "find_files":
                return "Mencari file \"" + args.optString("name", "") + "\"";
            default:
                return name;
        }
    }

    private static String disabled(String tool) {
        return "Tool " + tool + " dinonaktifkan user di Settings. Minta user mengaktifkannya dulu.";
    }

    private JSONObject post(AiProviders.Provider provider, String apiKey, String model)
            throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("messages", messages);
        body.put("tools", toolSchemas());
        body.put("tool_choice", "auto");

        Request request = new Request.Builder()
                .url(provider.baseUrl)
                .header("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        try (Response response = HTTP.newCall(request).execute()) {
            ResponseBody rb = response.body();
            String text = rb == null ? "" : rb.string();
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + ": " + text);
            }
            return new JSONObject(text);
        }
    }

    /** Tool yang ditawarkan ke model; yang dinonaktifkan user tidak dikirim sama sekali. */
    private JSONArray toolSchemas() throws JSONException {
        JSONArray tools = new JSONArray();

        if (store.isToolEnabled("list_blocks")) {
            tools.put(tool("list_blocks", "Cari block yang tersedia. Kosongkan query untuk daftar umum.", props(prop("query", "string", "Kata kunci opcode atau teks block")), new String[0]));
            tools.put(tool("list_event_keys", "Daftar event yang sudah punya block di file Java aktif.", props(), new String[0]));
        }

        if (store.isToolEnabled("append_blocks")) {
            JSONObject stepItem = new JSONObject();
            stepItem.put("type", "object");
            JSONObject stepProps = new JSONObject();
            stepProps.put("op", new JSONObject().put("type", "string"));
            stepProps.put("params", new JSONObject().put("type", "array").put("items", new JSONObject().put("type", "string")));
            stepItem.put("properties", stepProps);
            stepItem.put("required", new JSONArray().put("op").put("params"));

            JSONObject appendProps = new JSONObject();
            appendProps.put("event_key", new JSONObject().put("type", "string"));
            appendProps.put("steps", new JSONObject().put("type", "array").put("items", stepItem));
            tools.put(tool("append_blocks", "Usulkan block baru ditambahkan ke ekor event. User harus menyetujui.", appendProps, new String[]{"event_key", "steps"}));
        }

        if (store.isToolEnabled("list_dir")) {
            tools.put(tool("list_dir", "Daftar isi folder di dalam project. Path kosong = root project.",
                    props(prop("path", "string", "Path relatif dari root project, misal app/src/main/java")), new String[0]));
        }

        if (store.isToolEnabled("file_info")) {
            tools.put(tool("file_info", "Cek apakah path ada, tipenya (file/dir), dan ukurannya.",
                    props(prop("path", "string", "Path relatif dari root project")), new String[]{"path"}));
        }

        if (store.isToolEnabled("read_file")) {
            tools.put(tool("read_file", "Baca isi file teks project dengan nomor baris. Pakai offset dan limit untuk file panjang.",
                    props(prop("path", "string", "Path relatif file"),
                            prop("offset", "integer", "Nomor baris awal, mulai dari 1"),
                            prop("limit", "integer", "Jumlah baris, maks 1000")), new String[]{"path"}));
        }

        if (store.isToolEnabled("search")) {
            tools.put(tool("search", "Cari teks (tidak case-sensitive) di file teks project.",
                    props(prop("query", "string", "Teks yang dicari"),
                            prop("path", "string", "Folder pencarian, kosong = seluruh project")), new String[]{"query"}));
        }

        if (store.isToolEnabled("find_files")) {
            tools.put(tool("find_files", "Cari file atau folder yang namanya mengandung kata tertentu.",
                    props(prop("name", "string", "Potongan nama file")), new String[]{"name"}));
        }

        return tools;
    }

    private static JSONObject tool(String name, String description, JSONObject properties, String[] required) throws JSONException {
        JSONObject params = new JSONObject();
        params.put("type", "object");
        params.put("properties", properties);
        params.put("required", new JSONArray(required));

        JSONObject fn = new JSONObject();
        fn.put("name", name);
        fn.put("description", description);
        fn.put("parameters", params);

        JSONObject tool = new JSONObject();
        tool.put("type", "function");
        tool.put("function", fn);
        return tool;
    }

    /** Tiap entry hasil prop(...): {nama, schema}. */
    private static JSONObject props(Object[]... entries) throws JSONException {
        JSONObject o = new JSONObject();
        for (Object[] e : entries) {
            o.put((String) e[0], e[1]);
        }
        return o;
    }

    private static Object[] prop(String name, String type, String desc) throws JSONException {
        return new Object[]{name, new JSONObject().put("type", type).put("description", desc)};
    }

    private static JSONObject message(String role, String content) throws JSONException {
        return new JSONObject().put("role", role).put("content", content);
    }

    private static JSONObject toolMessage(String id, String content) throws JSONException {
        return new JSONObject().put("role", "tool").put("tool_call_id", id).put("content", content);
    }
}
