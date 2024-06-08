package com.Ox08.noframeworks;
import java.net.*;
import java.nio.*;
import java.util.*;
import java.io.*;
import java.util.logging.*;
import com.sun.net.httpserver.*;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.function.Function;
import java.util.regex.Pattern;
/**
 * <b>My hand-made guestbook.</b>
 * <p>
 * This class is 'all-in-one' server application, that feels like ordinary Spring/JEE
 * but written COMPLETELY WITHOUT any external libraries or frameworks.
 * So it can be executed on plain JRE, without any additional modules or libraries.
 * <p>
 * The HTTP server core features are provided by
 * <a href="https://docs.oracle.com/javase/8/docs/jre/api/net/httpserver/spec/com/sun/net/httpserver/package-summary.html">...</a>
 * package, available since 1.8
 * <p>
 * Features:
 * 0) Tiny Dependency Injection
 * 1) html templating with variables substitutions and conditional blocks like
 * ${if(gb.isAuthenticated)
 * <a href="#" id="deleteBtn" class="btn btn-sm btn-danger">Delete</a>
 * }
 * <p>
 * 2) JSON parsing & generation - yep, also hand made. Simulates RESTful APIs
 * <p>
 * 3) User authentication and sessions - uses cookies, has session expiration, login/logout
 * <p>
 * 4) Persistent and in-memory store for sample records.
 * <p>
 * 5) I18N and locale switching
 * @author <a href="mailto:alex3.145@gmail.com">Alex Chernyshev</a>
 */
public class FeelsLikeAServer {
    private final static Logger LOG = Logger.getLogger("NOFRAMEWORKS");
    private static boolean debugMessages; // if debug messages enabled
    /**
     * The start point
     */
    public static void main(String[] args) throws IOException {
        // listening port
        final int port = Integer.parseInt(System.getProperty("appPort", "8500"));
        // check if debug messages enabled
        debugMessages = Boolean.parseBoolean(System.getProperty("appDebug", "false"));
        if (debugMessages) { LOG.setUseParentHandlers(false);
            final Handler systemOut = new ConsoleHandler();systemOut.setLevel(Level.FINE);
            LOG.addHandler(systemOut);LOG.setLevel(Level.FINE);
        }
        // create and configure our Tiny Dependency Injection Manager
        final TinyDI notDI = new TinyDI(); notDI.setup(List.of(Users.class,Sessions.class,LocaleStorage.class,
                BookRecordStorage.class,RestAPI.class,Expression.class,
                Json.class,PageHandler.class,ResourceHandler.class));
        // create users service and load users
        final Users users = notDI.getInstance(Users.class); users.load();
        // create GB storage and load records from data file on disk
        final BookRecordStorage storage = notDI.getInstance(BookRecordStorage.class); storage.load();
        // create i18n service and load bundles
        final LocaleStorage localeStorage = notDI.getInstance(LocaleStorage.class); localeStorage.load();
        // create http server
        final HttpServer server = HttpServer.create(new InetSocketAddress(port), 50);
        // setup page handler and bind it to /
        server.createContext("/").setHandler(notDI.getInstance(PageHandler.class));
        // create resource handler
        final ResourceHandler rs = notDI.getInstance(ResourceHandler.class);
        // bind to serve static content
        server.createContext("/static").setHandler(rs); server.createContext("/favicon.ico").setHandler(rs);
        // bind 'REST' API
        server.createContext("/api").setHandler(notDI.getInstance(RestAPI.class));
        LOG.info("FeelsLikeAServer started: http://%s:%d . Press CTRL-C to stop".formatted(server.getAddress().getHostString(), port));
        // start http server
        server.start();
    }
    /**
     * This is guest book 'entity' record
     *
     * @param id      record id
     * @param title   title
     * @param author  record author
     * @param message message text
     * @param created date and time when message created
     */
    record BookRecord(UUID id, String title, String author, String message, Date created) {}
    /**
     * Marker for our tiny dependency injection class
     * If class implements this interface - it's the injectable dependency
     */
    interface Dependency {}
    /**
     * Simplest DI ever.
     * Based on Topological sort, supports only constructor injection
     */
    static class TinyDI {
        // Function to return list containing vertices in Topological order.
        static int[] topoSort(ArrayList<ArrayList<Integer>> adj) {
            final int[] indegree = new int[adj.size()];
            for (ArrayList<Integer> integers : adj) for (int it : integers) indegree[it]++;
            final Queue<Integer> q = new LinkedList<>();
            for (int i = 0; i < adj.size(); i++) if (indegree[i] == 0) q.add(i);
            final int[] topo = new int[adj.size()]; int i = 0;
            while (!q.isEmpty()) {
                topo[i++] = q.remove(); for (int it : adj.get(topo[i - 1])) if (--indegree[it] == 0) q.add(it);
            }
            return topo;
        }
        // No. of vertices
        private int totalDeps;
        // An Array of List which contains
        // references to the Adjacency List of
        // each vertex
        final ArrayList<ArrayList<Integer>> adj = new ArrayList<>();
        private int cdc; // current index count, used when adding new class
        private Class<?>[] cl; // all classes with indexes
        private final TypedHashMap<Class<?>, Object> services = new TypedHashMap<>(); // stores all instances
        /**
         * Get instance of specified dependency
         * @param clazz
         *          class of dependency
         * @return
         *          dependency instance
         * @param <T>
         *          instance type (class)
         */
        public <T> T getInstance(Class<T> clazz) {
            return services.containsKey(clazz) ? services.getTyped(clazz, null) : null;
        }
        /**
         * Setup dependencies
         * @param inputCl
         *          list of all classes that should be wired together
         */
        public synchronized void setup(List<Class<?>> inputCl) {
            if (this.totalDeps > 0) throw new IllegalStateException("Already initialized!");
            if (inputCl == null || inputCl.isEmpty()) throw new IllegalStateException("There should be dependencies!");
            // we use 0 as marker for 'no dependencies'
            this.totalDeps = inputCl.size() + 1;
            // build adjuction array
            for (int i = 0; i < totalDeps; i++) adj.add(new ArrayList<>());
            // build classes indexes, set initial class number
            this.cl = new Class[totalDeps]; this.cdc = 1;
            // build dependencies tree, based on class constructor
            for (Class<?> c : inputCl) {
                final List<Class<?>> dependsOn = new ArrayList<>();
                for (Class<?> p : c.getDeclaredConstructors()[0].getParameterTypes())
                    if (Dependency.class.isAssignableFrom(p)) dependsOn.add(p);
                // add class number
                addClassNum(c, dependsOn);
            }
            // make topological sort
            final int[] ans = topoSort(adj); final List<Integer> deps = new ArrayList<>();
            // put marks for 'zero-dependency', when class does not depend on others
            for (int node : ans) if (node > 0) deps.add(node);
            // reverse to get least depend on top
            Collections.reverse(deps);
            // and instantiate one by one
            for (int i : deps) instantiate(cl[i]);
        }
        private void addClassNum(Class<?> c, List<Class<?>> deps) {
            LOG.log(Level.FINE, "add class %s with deps %d".formatted(c.getName(), deps.size()));
            final int pos = addClassNum(c);
            // if class has no dependencies - put 'zero-deps' marks and leave
            if (deps.isEmpty()) { adj.get(pos).add(0); return; }
            // for all others - links deps
            for (Class<?> cc : deps) adj.get(pos).add(addClassNum(cc));
        }
        /**
         * Add dependency class to numbered array
         * @param c
         *          dependency class
         * @return
         *          class number
         */
        private int addClassNum(Class<?> c) {
            // this line just checks for duplications, to avoid double registrations of same class
            for (int i = 0; i < cl.length; i++) if (cl[i] != null && cl[i].equals(c)) return i;
            // actual adding
            cl[cdc] = c; return cdc++;
        }
        /**
         * Instantiates dependency class, pass its 'depended on' instances to constructor.
         * Puts instance to 'instances' storage.
         * @param clazz
         *          dependency class
         */
        private void instantiate(Class<?> clazz) {
            if (clazz == null) throw new IllegalStateException("Cannot create instance for null!");
            LOG.log(Level.FINE, "Creating instance of %s".formatted(clazz.getName()));
            // we just take first public constructor for simplicity
            final java.lang.reflect.Constructor<?> c = clazz.getDeclaredConstructors()[0];final List<Object> params = new ArrayList<>();
            // lookups constructor params in 'instances storage'
            for (Class<?> p : c.getParameterTypes())
                if (Dependency.class.isAssignableFrom(p) && services.containsKey(p)) params.add(services.get(p));
            // try to instantiate
            try { services.put(clazz, c.newInstance(params.toArray())); } catch
                (InstantiationException | java.lang.reflect.InvocationTargetException | IllegalAccessException e) {
                throw new RuntimeException("Cannot instantiate class: %s".formatted(clazz.getName()), e);
            }
        }
    }
    /**
     * Support for sessions
     */
    static class Sessions implements Dependency {
        public static final int MAX_SESSIONS = 5,//max allowed sessions
                SESSION_EXPIRE_HOURS = 8; // session expiration, in hours
        private final Map<String, Session> sessions = new HashMap<>(); // stores sessions objects
        private final Map<String, String> registeredUsers = new HashMap<>(); // maps username and session id
        /**
         * Get session object by session id
         *
         * @param sessionId session id
         * @return session object
         * @see Session
         */
        public Session getSession(String sessionId) { return !isSessionExist(sessionId) ? null : sessions.get(sessionId);}
        /**
         * Checks if session exist
         *
         * @param sessionId session id to seek
         * @return true -if session found, false - otherwise
         */
        public boolean isSessionExist(String sessionId) {
            // if there is no session registered with such id  - respond false
            if (!sessions.containsKey(sessionId)) return false;
            // extract session entity
            final Session s = sessions.get(sessionId);
            // checks for expiration time
            // Logic is: [session created]...[now,session not expired]....[+8 hours]....[now,session expired]
            if (s.created.plusHours(SESSION_EXPIRE_HOURS).isBefore(java.time.LocalDateTime.now())) {
                LOG.log(Level.INFO, "removing expired session: %s for user: %s".formatted(s.sessionId, s.user.username));
                sessions.remove(sessionId); return false;
            }
            return true;
        }
        /**
         * Register new user session
         *
         * @param user user entity
         * @return new session id
         */
        public synchronized String registerSessionFor(Users.User user) {
            // disallow creation if max sessions limit is reached
            if (registeredUsers.size() > MAX_SESSIONS) return null;
            // disallow creation if there is existing session
            if (registeredUsers.containsKey(user.username)) return null;
            // create new session id
            final String newSessionId = UUID.randomUUID().toString();
            sessions.put(newSessionId, new Session(newSessionId, java.time.LocalDateTime.now(), user));
            registeredUsers.put(user.username, newSessionId); return newSessionId;
        }
        /**
         * Unregister existing session (Logout)
         *
         * @param sessionId session id
         * @return true if session has been removed, false - otherwise
         */
        public synchronized boolean unregisterSession(String sessionId) {
            if (!sessions.containsKey(sessionId)) return false;
            registeredUsers.remove(sessions.remove(sessionId).user.username);return true;
        }
        /**
         * Session record
         *
         * @param sessionId session id
         * @param created   creation date
         * @param user      session user
         */
        public record Session(String sessionId, java.time.LocalDateTime created, Users.User user) {}
    }
    /**
     * Support for users
     */
    static class Users implements Dependency {
        private final Map<String, User> users = new TreeMap<>();
        /**
         * Load embedded users
         */
        public void load() {
            addUser(new User("admin", "admin", "Administrator", true));
            addUser(new User("alex", "alex", "Alex", false));
        }
        /**
         * Check if user exist
         *
         * @param username username
         * @return true if user with provided username exists, false - otherwise
         */
        public boolean isUserExists(String username) {
            return username != null && !username.isBlank() && users.containsKey(username);
        }
        /**
         * Return user entity by username
         *
         * @param username username/login
         * @return User entity
         */
        public User getUserByUsername(String username) { return users.getOrDefault(username, null); }
        /**
         * Adds new user
         *
         * @param user user entity
         */
        public void addUser(User user) { users.put(user.username(), user); }
        /**
         * User entity
         *
         * @param username username or login
         * @param password password as plaintext
         * @param name     Full name
         * @param isAdmin  true - user is administrator
         *                 false - otherwise
         */
        public record User(String username, String password, String name, boolean isAdmin) {}
    }
    /**
     * Simple data storage. Combines in-memory and persistent.
     */
    static class BookRecordStorage implements Dependency {
        static final int MAX_RECORDS = 100;
        private AsynchronousFileChannel fc; // we use async access for more fun, have both read & write into same file.
        // in-memory store for GB records
        private final Map<UUID, BookRecord> records = new TreeMap<>();
        /**
         * Loads GB records into memory
         *
         * @throws IOException on I/O errors
         */
        public synchronized void load() throws IOException {
            if (fc != null) throw new IllegalStateException("Already loaded!");
            final File storageFile = new File("data.json");
            fc = AsynchronousFileChannel.open(storageFile.toPath(),StandardOpenOption.WRITE,
                    StandardOpenOption.CREATE,StandardOpenOption.READ);
            LOG.log(Level.FINE, "Reading data file %s".formatted(storageFile.getAbsolutePath()));
            // create some initial records, if none exist
            if (storageFile.length() == 0) {
                LOG.log(Level.FINE,"Adding initial records");
                for (int i = 1; i <= 10; i++) {
                    final BookRecord r = new BookRecord(UUID.randomUUID(),
                            "Some title %d".formatted(i),
                            "alex " + i, "test message " + i, new Date());
                    records.put(r.id, r);
                }
                final StringBuilder sb = new StringBuilder(); Json.toJson(sb, records.values());
                fc.write(ByteBuffer.wrap(sb.toString().getBytes()), fc.size());
            } else {
                LOG.log(Level.FINE,"Reading existing records");
                // reads persisted data
                AsyncFileReaderJson.readBlocks(fc, (json) -> {
                    // just don't add more records to memory store if there were too many in data file
                    if (records.size() > MAX_RECORDS) return null;
                    final Map<String, String> jsonParsed = Json.parseJson(json);
                    LOG.log(Level.FINE, "loaded %d values from json block: %s".formatted(jsonParsed.size(), json));
                    final BookRecord newRecord = new BookRecord(UUID.fromString(jsonParsed.get("id")),
                            jsonParsed.get("title"),
                            jsonParsed.get("author"),
                            jsonParsed.get("message"),
                            new Date(Long.parseLong(jsonParsed.get("created"))));
                    records.put(newRecord.id, newRecord); return null;
                });
            }
        }
        /**
         * Adds new  book record to storage
         *
         * @param record new book record
         * @return true - if record added successfully, false - otherwise
         */
        public boolean addRecord(BookRecord record) {
            if (records.size() > MAX_RECORDS) return false;
            boolean wasEmpty = records.isEmpty(); records.put(record.id, record);
            final StringBuilder sb = new StringBuilder(); if (!wasEmpty) sb.append(",");
            Json.toJson(sb, record); sb.append("]");
            try { // size -1 is required to wipe out previous ']' char
                return fc.write(ByteBuffer.wrap(sb.toString().getBytes()), fc.size() - 1).get() > 0;
            } catch (IOException | java.util.concurrent.ExecutionException | InterruptedException e) {
                throw new RuntimeException("Cannot add record", e);
            }
        }
        /**
         * Removes record
         *
         * @param uuid records id
         * @return true if record has been removed, false - otherwise
         */
        public synchronized boolean deleteRecord(String uuid) {
            final UUID u = UUID.fromString(uuid);
            if (!records.containsKey(u)) return false; else records.remove(u);
            // For maximum simplicity, we put position at the beginning of data file and then dump all json
            // Then we just truncate data file to required size - to wipe out garbage.
            final StringBuilder sb = new StringBuilder(); Json.toJson(sb, records.values());
            final byte[] b = sb.toString().getBytes(); fc.write(ByteBuffer.wrap(b), 0);
            try { return fc.truncate(b.length)!=null; } catch (IOException e) {
                throw new RuntimeException("Cannot truncate data file", e);
            }
        }
        /**
         * Retrieve all guestbook records.
         *
         * @return a collection with all records
         */
        public Collection<BookRecord> getAllRecords() { return Collections.unmodifiableCollection(records.values()); }
        /**
         * This class allows block read from async file channel. Yep, we THAT cool.
         */
        static class AsyncFileReaderJson {
            private static final int MAX_LINE_SIZE = 4096 * 2;
            /**
             * Read bytes from an {@code AsynchronousFileChannel}, which are decoded into characters
             * using the UTF-8 charset.
             * The resulting characters are parsed by line and passed to the destination buffer.
             *
             * @param asyncFile the nio associated file channel.
             */
            static void readBlocks(
                    AsynchronousFileChannel asyncFile,
                    Function<String, Void> onReadJson) {
                readBlocks(asyncFile, 0, 0,
                        new byte[1024],new byte[MAX_LINE_SIZE], 0, false, onReadJson);
            }
            /**
             * There is a recursion on `readLines()`establishing a serial order among:
             * `readLines()` -> `produceLine()` -> `onProduceLine()` -> `readLines()` -> and so on.
             * It finishes with a call to `close()`.
             *
             * @param asyncFile   the nio associated file channel.
             * @param position    current read or write position in file.
             * @param bufSize     total bytes in buffer.
             * @param buffer      buffer for current producing line.
             * @param targetBlock the transfer buffer.
             * @param blockPos     current position in producing line.
             */
            static void readBlocks(
                    AsynchronousFileChannel asyncFile,
                    long position,  // current read or write position in file
                    int bufSize,    // total bytes in buffer
                    byte[] buffer,  // buffer for current producing line
                    byte[] targetBlock, // the transfer buffer
                    int blockPos, boolean foundStart, Function<String, Void> onReadJson) {
                for (int bp = 0; bp < bufSize; bp++) {
                        if (buffer[bp] == '{') { foundStart = true; continue; }
                        if (foundStart && buffer[bp] == '}') {
                            produceJson(targetBlock, blockPos, onReadJson); blockPos = 0; foundStart = false; continue;
                        }
                        if (foundStart) targetBlock[blockPos++] = buffer[bp];
                }
                final int lastBlockPos = blockPos; // we need a final variable captured in the next lambda
                final boolean finalFoundStart = foundStart;
                readBytes(asyncFile, position,
                        buffer,buffer.length, (err, res) -> {
                            if (err != null) return;
                            if (res <= 0) { if (lastBlockPos > 0) produceJson(targetBlock, lastBlockPos, onReadJson);
                            } else readBlocks(asyncFile, position + res,
                                        res, buffer, targetBlock, lastBlockPos, finalFoundStart, onReadJson);
                        });
            }
            /*
             * Asynchronous read chunk operation, callback based.
             */
            static void readBytes(
                    AsynchronousFileChannel asyncFile,
                    long pos, // current read or write pos in file
                    byte[] data,   // buffer for current producing line
                    int size,
                    java.util.function.ObjIntConsumer<Throwable> completed) {
                if (completed == null) throw new RuntimeException("completed cannot be null");
                if (size > data.length) size = data.length;
                if (size == 0) { completed.accept(null, 0); return; }
                asyncFile.read(ByteBuffer.wrap(data, 0, size), pos, null,
                        new java.nio.channels.CompletionHandler<>() {
                    @Override
                    public void completed(Integer result, Object attachment) { completed.accept(null, result); }
                    @Override
                    public void failed(Throwable exc, Object attachment) { completed.accept(exc, 0); }
                });
            }
            /**
             * This is called only from readLines() callback and performed from a background IO thread.
             *
             * @param jsonBlock the transfer buffer.
             * @param bpos   current position in producing json block.
             */
            private static void produceJson(byte[] jsonBlock, int bpos, Function<String, Void> onReadJson) {
                LOG.log(Level.FINE, "Produce json ,sz: %d".formatted(jsonBlock.length));
                onReadJson.apply(new String(jsonBlock, 0, bpos, StandardCharsets.UTF_8));
            }
        }
    }
    /**
     * Expressions support
     */
    static class Expression implements Dependency {
        private final LocaleStorage localeStorage;
        Expression(LocaleStorage localeStorage) {
            this.localeStorage = localeStorage;
        }
        /**
         * This is DTO to pass single expression line with current context into lambda function
         * @param expr
         * @param runtime
         */
        record Line(String expr,TypedHashMap<String,Object> runtime) {}
        /**
         * Parses page template, does substitution.
         *
         * @param pageData page template
         * @param runtime  key-value map that stores values for substitutions in expressions
         * @return parsed and substituted page content
         */
        public String parseTemplate(String pageData, TypedHashMap<String, Object> runtime,Function<Line, String> onReadExpr) {
            final StringBuilder out = new StringBuilder(),expr = new StringBuilder();
            boolean startExpr = false; int[] counts = new int[2];
            // we need to iterate over each character in page content
            for (int i = 0; i < pageData.length(); i++) {
                // get single character
                char c = pageData.charAt(i);
                // if expression started
                if (startExpr) {
                    // end of expression
                    if (c == '}') { counts[1]++;
                        LOG.log(Level.FINE, "count start=%d close=%d expr: %s".formatted(counts[0], counts[1], expr));
                        if (counts[0] == counts[1]) {
                            startExpr = false; counts = new int[]{0, 0};
                            out.append(onReadExpr.apply(new Line(expr.toString(), runtime)));
                            expr.setLength(0); continue;
                        }
                        // this is start of internal expression, that's inside current!
                    } else if (c == '{') counts[0]++;
                    expr.append(c); continue;
                }
                // first character in expression start: ${
                if (c == '$') {
                    // here we do checking for next character and if it matches '{' - we have expression block.
                    final int ni = i + 1;
                    if (ni < pageData.length() && pageData.charAt(ni) == '{') { startExpr = true; counts[0]++; }
                    i = ni; continue;
                }
                out.append(c);
            }
            return out.toString();
        }
        /**
         * This is simple template record
         * @param name
         *          name, used in $template tag, like that: ${template(template/main.html)}
         * @param body
         *          template content
         * @param sections
         *          set of parsed sections
         */
        record Template(String name,String body,Map<String,String> sections) {}
        /**
         * Merges multiple templates into single string. Ordinary tags are not processed on this step.
         * @param template
         *          parsed template, with extracted sections
         * @param runtime
         *          current runtime
         * @return
         *      merged template as string
         */
        public String mergeTemplate(Template template,TypedHashMap<String,Object> runtime) {
            return parseTemplate(template.body,runtime,(line)-> {
                if (line.expr.startsWith("inject(")) {
                    String data = line.expr.substring("inject(".length());
                    data = data.substring(0, data.indexOf(")"));
                    LOG.log(Level.FINE, "inject template section: '%s' ".formatted(data));
                    if (!template.sections.containsKey(data)) {
                        LOG.warning("section not found: '%s' ".formatted(data)); return "??";
                    }
                    return template.sections.get(data);
                }
                // keep all other expressions!
                return "${%s}".formatted(line.expr);
            });
        }

        static final String PAGE_TEMPLATE_KEY= "_PAGE_TEMPLATE_", // runtime key, used to store/retrieve current page
                            ALL_TEMPLATES_KEY= "_TEMPLATES_"; // .. to store/retrieve all available templates
        /**
         * Build page with template
         * @param expr
         *          parsed expression block (what is inside ${}
         * @param runtime
         *          current runtime
         * @return
         *      empty string
         */
        String buildTemplate(String expr, TypedHashMap<String,Object> runtime) {
            // check for template tag
            if (expr.startsWith("template(")) {
                // extract template key
                String key = expr.substring("template(".length()); key = key.substring(0, key.indexOf(")"));
                LOG.log(Level.FINE, "found template key: '%s'".formatted(key));
                final Map<String, String> templates = runtime.getTyped(ALL_TEMPLATES_KEY,Collections.emptyMap());
                // check if template exist
                if (!templates.containsKey(key)) {
                    LOG.log(Level.FINE, "template not found: '%s'".formatted(key)); return "";
                }
                // check if template was already loaded
                if (runtime.containsKey(PAGE_TEMPLATE_KEY)) {
                    LOG.log(Level.WARNING, "template already loaded: '%s'".formatted(key)); return "";
                }
                // put current template into runtime context
                runtime.put(PAGE_TEMPLATE_KEY, new Template(key, templates.get(key),new HashMap<>()));
            // check for section tag
            } else if (expr.startsWith("section(")) {
                String data = expr.substring("section(".length());
                final int i = data.indexOf(")");
                // extract conditional block
                final String code = data.substring(i + 1);
                data = data.substring(0, i);
                LOG.log(Level.FINE, "found section key: '%s' , data sz: %d".formatted(data,code.length()));
                // add parsed section to current template
                if (runtime.containsKey(PAGE_TEMPLATE_KEY)) {
                    final Template t = runtime.getTyped(PAGE_TEMPLATE_KEY,null); t.sections.put(data,code);
                }
            }
            return "";
        }
        /**
         * Parse template expression and replace with produced values
         *
         * @param expr    single expression block, extracted from page template
         * @param runtime map with values to substitute in expression
         * @return template block with substituted values
         */
        private String parseExpr(String expr, TypedHashMap<String, Object> runtime) {
            LOG.log(Level.FINE, "parsing expr: %s".formatted(expr));
                // parse messages
            if (expr.startsWith("msg(")) {
                // extract variable name from expression block
                String data = expr.substring("msg(".length()); data = data.substring(0, data.indexOf(")"));
                LOG.log(Level.FINE, "key: '%s'".formatted(data));
                /*
                 * We support 2 cases:
                 * 1) direct substitution from provided key-value map
                 * 2) attempt to get value from i18n bundle
                 */
                return runtime.containsKey(data) ? runtime.get(data).toString() :
                        localeStorage.resolveKey(data, (String) runtime.get("lang"));
                // parse conditions
            } else if (expr.startsWith("if(")) {
                // extract condition expression
                String data = expr.substring("if(".length()); final int i = data.indexOf(")");
                // extract conditional block
                final String code = data.substring(i + 1); data = data.substring(0, i);
                final StringBuilder out = new StringBuilder();
                String pp = null; // previous element
                boolean equals = false; // mark of equals expression
                int lastSz = 0; // last recorded buffer size
                // split conditional expression by spaces
                for (String p : data.split(" ")) {
                    LOG.log(Level.FINE, "p=%s".formatted(p));
                    if (p.isBlank()) continue; // skip empty elements
                    if (equals) { // if there is equal mark
                        out.setLength(lastSz); // trim to previous size to remove left part of equals
                        equals = false; // remove 'equals' mark
                        Object l = pp,r = p; // set left and right parts of equals expression
                        if (runtime.containsKey(pp)) l = runtime.get(pp); // extract left value
                        if (runtime.containsKey(p)) r = runtime.get(p); // extract right value
                        LOG.log(Level.FINE, "l=%s r=%s".formatted(l, r));
                        out.append(l.equals(r)); continue;
                    }
                    // check for 'equals' expression
                    if (pp!=null && p.equals("eq")) { equals = true; continue; }
                    // check for negate expression, keep negate symbol!
                    if (p.startsWith("!")) { p = p.substring(1); out.append("!"); }
                    // record previous buffer length
                    lastSz = out.length();
                    // try to replace variable name with value from provided map
                    if (runtime.containsKey(p)) {
                        final Object v = runtime.get(p); LOG.log(Level.FINE, "got runtime value: %s".formatted(v)); out.append(v);
                    } else out.append(p);
                    pp = p; // record previous element
                }
                // evaluate boolean condition
                if (evaluateExpression(out.toString())) {
                    // if evaluated as 'true' and we require to render conditional block - do recurse parsing
                    final String recurseParse = parseTemplate((code), runtime, (line) -> parseExpr(line.expr,line.runtime));
                    LOG.log(Level.FINE, "recurse parse: %s".formatted(recurseParse));
                    return recurseParse;
                } else
                    // otherwise - respond empty block
                    return "";
            }
            // if we have something else - just respond 'as-is'
            return expr;
        }
        /**
         * String s = "true && ( false || ( false && true ) )";
         */
        static boolean evaluateExpression(String s) {
            LOG.log(Level.FINE, "parsing expression: %s".formatted(s)); return new ConditionalParser(s).evaluate();
        }
        /**
         * This is our 'conditional parser'
         * Allows to evaluate simple boolean expressions like:
         * <p>
         * String s = "true && ( false || ( false && true ) )";
         * <p>
         * True or false - are substituted from runtime key-value map
         */
        private static class ConditionalParser {
            private final String s; int index = 0;
            ConditionalParser(String src) { this.s = src; }
            private boolean match(String expect) {
                while (index < s.length() && Character.isWhitespace(s.charAt(index))) ++index;
                if (index >= s.length()) return false;
                if (s.startsWith(expect, index)) { index += expect.length(); return true; } return false;
            }
            private boolean element() {
                if (match(Boolean.TRUE.toString())) return true; if (match(Boolean.FALSE.toString())) return false;
                if (match("(")) {
                    boolean result = expression();
                    if (!match(")")) throw new RuntimeException("')' expected"); return result;
                } else throw new RuntimeException("unknown token found: %s".formatted(s));
            }
            private boolean term() { return match("!") != element(); }
            private boolean factor() {
                boolean result = term(); while (match("&&")) result &= term(); return result;
            }
            private boolean expression() {
                boolean result = factor(); while (match("||")) result |= factor(); return result;
            }
            public boolean evaluate() { final boolean result = expression();
                if (index < s.length()) throw new RuntimeException(
                            "extra string '%s'".formatted(s.substring(index))); else return result;
            }
        }
    }
    /**
     * Support for JSON parsing & generation
     */
    static class Json implements Dependency {
        final static Pattern PATTERN_JSON = Pattern.compile("\"([^\"]+)\":\"*([^,^}\"]+)", Pattern.CASE_INSENSITIVE);
        /**
         * That's how we do it: parse JSON as grandpa!
         * No nested objects allowed.
         *
         * @param json json string
         * @return key-value map parsed from json string
         */
        public static Map<String, String> parseJson(String json) {
            // yep, we just parse JSON with pattern and extract keys and values
            final java.util.regex.Matcher matcher = PATTERN_JSON.matcher(json);
            // output map
            final Map<String, String> params = new HashMap<>();
            // loop over all matches
            while (matcher.find()) {
                String key = null, value = null;
                // skip first match group (0 index) , because it responds whole text
                for (int i = 1; i <= matcher.groupCount(); i++) {
                    // First match will be key, second - value
                    // So we need to read them one by one
                    final String g = matcher.group(i); if (key != null) value = g; else key = g;
                    LOG.log(Level.FINE, "key=%s value=%s g=%s".formatted(key, value, g));
                    if (key != null && value != null) { params.put(key, value); key = null; value = null; }
                }
            }
            return params;
        }
        public static void toJson(StringBuilder out, Collection<BookRecord> records) {
            // yep, we build json manually
            out.append("["); boolean first = true;
            // build list of objects
            for (BookRecord r : records) { if (first) first = false; else out.append(","); Json.toJson(out, r); }
            out.append("]");
        }
        /**
         * Build JSON string from BookRecord object
         */
        public static void toJson(StringBuilder out, BookRecord r) {
            out.append("{\n"); toJson(out, "id", r.id, true);
            toJson(out, "title", r.title, true); toJson(out, "author", r.author, true);
            toJson(out, "created", r.created.getTime(), true);
            toJson(out, "message", r.message, false); out.append("}");
        }
        /**
         * Build JSON string with key-value pair
         */
        public static void toJson(StringBuilder sb, String key, Object value, boolean next) {
            sb.append("\"").append(key).append("\":\"").append(value).append("\"");
            if (next) sb.append(","); sb.append("\n");
        }
    }
    /**
     * HTTP Handler that simulates REST API
     */
    static class RestAPI extends AbstractHandler implements HttpHandler, Dependency {
        private final BookRecordStorage storage;
        private final Users users;
        private final Sessions sessions;
        private final LocaleStorage localeStorage;
        RestAPI(BookRecordStorage storage, Users users, Sessions sessions, LocaleStorage localeStorage) {
            this.storage = storage; this.localeStorage = localeStorage; this.users = users; this.sessions = sessions;
        }
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // extract url
            final String url = getUrl(exchange.getRequestURI()), query = exchange.getRequestURI().getQuery();
            // extract url params
            final Map<String, String> params = query != null && !query.trim().isBlank() ?
                    parseParams(exchange.getRequestURI().getQuery()) : Collections.emptyMap();
            // for output json
            final StringBuilder out = new StringBuilder();
            // we use simple case-switch with end urls
            switch (url) {
                // respond list of records
                case "/api/records" -> {
                    final List<BookRecord> rvalues = new ArrayList<>(storage.getAllRecords());
                    // process sorting
                    if (params.containsKey("sort")) {
                        final String sortKey = params.get("sort");
                        switch (sortKey) {
                            case "id" -> rvalues.sort(Comparator.comparing(u -> u.id));
                            case "title" -> rvalues.sort(Comparator.comparing(u -> u.title));
                            case "created" -> rvalues.sort(Comparator.comparing(u -> u.created));
                        }
                    } else rvalues.sort(Comparator.comparing(u -> u.created, Comparator.reverseOrder()));
                    // build list of objects
                    Json.toJson(out, rvalues);
                }
                // process 'add new record' feature
                case "/api/add" -> {
                    if (checkIfNonPostRequest(exchange)) return;
                    // read input json
                    final String req = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    LOG.log(Level.FINE, "req=%s".formatted(req));
                    // parse it into key-value
                    final Map<String, String> jsonParsed = Json.parseJson(req);
                    if (debugMessages)
                        for (Map.Entry<String, String> e : jsonParsed.entrySet())
                            LOG.log(Level.FINE, "k=%s v=%s".formatted(e.getKey(), e.getValue()));
                    // create new record
                    final BookRecord newRecord = new BookRecord(UUID.randomUUID(),
                            jsonParsed.getOrDefault("title", null),
                            jsonParsed.getOrDefault("author", null),
                            jsonParsed.getOrDefault("message", null), new Date());
                    if (!validateLikeJsr303(newRecord)) { respondBadRequest(exchange); return; }
                    // store
                    if (!storage.addRecord(newRecord)) { respondBadRequest(exchange); return; }
                    // add to response
                    Json.toJson(out, newRecord);
                }
                // delete record
                case "/api/delete" -> {
                    // we allow only POST there
                    if (checkIfNonPostRequest(exchange)) {
                        LOG.log(Level.FINE, "bad request: not a POST method");return;
                    }
                    // To remove record, user must be authenticated first,
                    // so here we try to get session id from provided cookie header
                    final String sessionId = getCookieValue(exchange, SESSION_KEY);
                    // if there is no session id - respond bad request
                    if (sessionId == null || sessionId.isBlank()) {
                        LOG.log(Level.FINE, "bad request: no session header");
                        respondBadRequest(exchange); return;
                    }
                    // if session is not exist - respond bad request
                    if (!sessions.isSessionExist(sessionId)) {
                        LOG.log(Level.FINE, "bad request: session not found");
                        respondBadRequest(exchange); return;
                    }
                    // get the id of record needs to be removed
                    final String recordId = params.getOrDefault("id", null);
                    // if id is blank  - respond bad request
                    if (recordId == null || recordId.isBlank()) {
                        LOG.log(Level.FINE, "bad request: recordId is not set");
                        respondBadRequest(exchange); return;
                    }
                    // try to delete record
                    if (!storage.deleteRecord(recordId)) {
                        LOG.log(Level.FINE, "bad request: cannot delete record: %s".formatted(recordId));
                        respondBadRequest(exchange); return;
                    }
                }
                /*
                 * Support for locale switching
                 */
                case "/api/locale" -> {
                    if (!params.containsKey("lang")) { LOG.log(Level.FINE, "bad request: no 'lang' parameter");
                        respondBadRequest(exchange); return;
                    }
                    String lang = params.get("lang");
                    if (lang == null || lang.isBlank()) {
                        LOG.log(Level.FINE, "bad request: 'lang' parameter is empty");
                        respondBadRequest(exchange); return;
                    }
                    lang = lang.toLowerCase().trim();
                    if (!localeStorage.getSupportedLocales().contains(lang)) {
                        LOG.log(Level.FINE, "bad request: unsupported locale: %s".formatted(lang));
                        respondBadRequest(exchange); return;
                    }
                    exchange.getResponseHeaders().add("Set-Cookie", "%s=%s; Path=/;  Secure; HttpOnly".formatted(LANG_KEY, lang));
                    respondRedirect(exchange, "/index.html");
                    LOG.log(Level.FINE, "changed lang to: %s".formatted(lang));
                    return;
                }
                /*
                 * Logout
                 */
                case "/api/logout" -> {
                    if (checkIfNonPostRequest(exchange)) return;
                    final String sessionId = getCookieValue(exchange, SESSION_KEY);
                    if (sessionId == null || sessionId.isBlank()) { respondBadRequest(exchange); return; }
                    if (sessions.unregisterSession(sessionId)) {
                        exchange.getResponseHeaders().add("Set-Cookie", "%s=; Path=/;  Max-Age=-1; Secure; HttpOnly".formatted(SESSION_KEY));
                        respondRedirect(exchange, "/");
                    } else { LOG.warning("Cannot destroy session: %s".formatted(sessionId)); respondBadRequest(exchange);}
                    return;
                }
                /*
                 * Password based authentication
                 */
                case "/api/auth" -> {
                    if (checkIfNonPostRequest(exchange)) return;
                    final String req2 = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    LOG.info("req=%s".formatted(req2));
                    final Map<String, String> jsonParsed = Json.parseJson(req2);
                    if (!jsonParsed.containsKey("username") || !jsonParsed.containsKey("password")) {
                        LOG.info("bad request: no required fields"); respondBadRequest(exchange); return;
                    }
                    final String username = jsonParsed.get("username"), password = jsonParsed.get("password");
                    if (username == null || username.isBlank() || password == null || password.isBlank()) {
                        LOG.info("bad request: some required fields are blank");
                        respondBadRequest(exchange); return;
                    }
                    if (!users.isUserExists(username)) {
                        LOG.info("bad request: user not found"); respondBadRequest(exchange); return;
                    }
                    final Users.User u = users.getUserByUsername(username);
                    if (!u.password.equals(password)) {
                        LOG.info("bad request: password incorrect"); respondBadRequest(exchange); return;
                    }
                    final String sessionId = sessions.registerSessionFor(u);
                    if (sessionId != null) { exchange.getResponseHeaders()
                                .add("Set-Cookie", "%s=%s; Path=/; Secure; HttpOnly".formatted(SESSION_KEY, sessionId));
                        LOG.info("set sessionId cookie: %s".formatted(sessionId)); respondRedirect(exchange, "/");
                        return;
                    }
                }
            }
            respondData(exchange, out.toString().getBytes(StandardCharsets.UTF_8));
        }
        /**
         * Validates entity record
         *
         * @param newRecord guest book record
         * @return true if record is valid
         * false - otherwise
         */
        private boolean validateLikeJsr303(BookRecord newRecord) {
            if (newRecord == null) return false;
            if (newRecord.author == null || newRecord.author.isBlank()) return false;
            if (newRecord.title == null || newRecord.title.isBlank()) return false;
            return newRecord.message != null && !newRecord.message.isBlank();
        }
    }
    /**
     * Page handler, serves html pages with pre-processing
     */
    static class PageHandler extends AbstractHandler implements HttpHandler, Dependency {
        private final Map<String, StaticResource> resources = new HashMap<>();
        private final Map<String,String> templates = new HashMap<>();
        private final Sessions sessions;
        private final Expression expr;
        PageHandler(Sessions sessions, Expression expr) {
            this.sessions = sessions; this.expr = expr;
            try {templates.put("template/main.html",
                        new String(getResource("/static/html/template/main.html")));
                resources.put("/index.html",
                        new StaticResource(getResource("/static/html/index.html"), "text/html"));
                resources.put("/login.html",
                        new StaticResource(getResource("/static/html/login.html"), "text/html"));
            } catch (IOException e) { throw new RuntimeException("Cannot load page template",e); }
        }
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String url = getUrl(exchange.getRequestURI());
            if (url == null || "/".equals(url) || url.isEmpty()) url = "/index.html";
            // if passed url is not mapped - respond 404
            if (!resources.containsKey(url)) { respondNotFound(exchange); return; }
            // get static resource
            final StaticResource resource = resources.get(url);
            // set mime type header
            exchange.getResponseHeaders().add("Content-type", resource.mime);
            // if resource is not a page template - just write bytes and go away.
            if (!"text/html".equals(resource.mime)) { respondData(exchange, resource.data); return; }
            // retrieve session id
            final String sessionId = getCookieValue(exchange, SESSION_KEY), lang = getCookieValue(exchange, LANG_KEY);
            // build rendering runtime
            final TypedHashMap<String, Object> runtime = new TypedHashMap<>();
            // put all available templates to let expression parser found them
            runtime.put(Expression.ALL_TEMPLATES_KEY,templates);
            // put current language and current page url
            runtime.put("lang", lang == null || lang.isBlank() ? "en" : lang); runtime.put("url",url);
            // check if user session exist
            final boolean sessionExist = sessions.isSessionExist(sessionId);
            LOG.info("got session: %s exist? %s".formatted(sessionId, sessionExist));
            runtime.put("gb.isAuthenticated", sessionExist);
            // put current user's name to been displayed in top of page
            if (sessionExist) runtime.put("user.name", sessions.getSession(sessionId).user.name);
            try { final String source = new String(resource.data);
                // at first, we need to build parts of template
                expr.parseTemplate(source, runtime, (line)-> expr.buildTemplate(line.expr,line.runtime));
                // if template was used and found - merge it with extracted sections, otherwise - just re-use source
                final String merged = runtime.containsKey(Expression.PAGE_TEMPLATE_KEY) ?
                        expr.mergeTemplate(runtime.getTyped(Expression.PAGE_TEMPLATE_KEY,null),runtime) : source;
                // render everything
                respondData(exchange, expr.parseTemplate(merged, runtime,
                                (line)-> expr.parseExpr(line.expr,line.runtime)).getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) { LOG.log(Level.WARNING, "Cannot parse template: %s".formatted(e.getMessage()), e);
                respondBadRequest(exchange);
            }
        }
    }
    /**
     * Support for I18N
     * Loads resource bundles, resolves translation keys
     */
    static class LocaleStorage implements Dependency {
        private final Map<String, Properties> locales = new HashMap<>();
        private Properties mainLocale; // this is fallback bundle,
                                      // if some key not present in specific locale - it will be taken from here
        /**
         *  Loads all bundles
         */
        public void load() {
            try { mainLocale = loadProperties("/i18n/gbMessages.properties");
                  locales.put("ru", loadProperties("/i18n/gbMessages_ru.properties"));
            } catch (IOException e) { throw new RuntimeException("Cannot load i18n bundle",e); }
        }
        public List<String> getSupportedLocales() { return List.of("en", "ru"); }
        /**
         * Resolves translation for specified key
         * @param key
         *          string key, like 'gb.text.login.accessDenied'
         * @param locale
         *          specified locale
         * @return
         *          translated string
         */
        public String resolveKey(String key, String locale) {
            if (locale != null && !locale.isBlank() && locales.containsKey(locale) && locales.get(locale).containsKey(key))
                return (String) locales.get(locale).get(key);
            return mainLocale.containsKey(key) ? (String) mainLocale.get(key) : "??%s??".formatted(key);
        }

        private Properties loadProperties(String name) throws IOException {
            LOG.log(Level.FINE,"load properties %s".formatted(name));
            final URL u = getClass().getResource(name);
            if (u == null) throw new FileNotFoundException("Resource not found: %s".formatted(name));
            final Properties p = new Properties(); try (InputStream in = u.openStream()) { p.load(in); } return p;
        }
    }
    /**
     * Handler for static content: serves images,css and js
     */
    static class ResourceHandler extends AbstractHandler implements Dependency {
        private final Map<String, StaticResource> resources = new HashMap<>();
        ResourceHandler() { try {
                resources.put("/favicon.ico",
                        new StaticResource(getResource("/static/img/favicon.ico"), "image/x-icon"));
                resources.put("/static/css/lit.css",
                        new StaticResource(getResource("/static/css/lit.css"), "text/css"));
                resources.put("/static/js/gb.js",
                        new StaticResource(getResource("/static/js/gb.js"), "application/javascript"));
                resources.put("/static/js/login.js",
                        new StaticResource(getResource("/static/js/login.js"), "application/javascript"));
            } catch (IOException e) { throw new RuntimeException("Cannot load static resource",e); } }
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            final String url = getUrl(exchange.getRequestURI());
            if (resources.containsKey(url)) { final StaticResource resource = resources.get(url);
                exchange.getResponseHeaders().add("Content-type", resource.mime); respondData(exchange, resource.data);
            } else respondNotFound(exchange);
        }
    }
    /**
     * Abstract shared handler, contains some useful stuff.
     */
    abstract static class AbstractHandler implements HttpHandler {
        // a record that holds  binary data and content mime
        record StaticResource(byte[] data, String mime) {}
        protected String getUrl(URI u) { return (u != null ? u.getPath() : "").toLowerCase().trim(); }
        /**
         * Reads resource from classpath
         *
         * @param name resource name
         * @return full content as string
         */
        protected byte[] getResource(String name) throws IOException {
            LOG.info("reading resource %s".formatted(name));
            try (InputStream in = getUrl(name).openStream(); ByteArrayOutputStream bo = new ByteArrayOutputStream()) {
                int nRead; final byte[] d = new byte[16384];
                while ((nRead = in.read(d, 0, d.length)) != -1) bo.write(d, 0, nRead);return bo.toByteArray();
            }
        }
        protected URL getUrl(String name) throws FileNotFoundException {
            final URL u = getClass().getResource(name);
            if (u == null) throw new FileNotFoundException("Resource not found: %s".formatted(name)); else return u;
        }
        /**
         * Here we extract URL parameters
         *
         * @param query some network url, like "<a href="http://blablabla/aaa.com?x=y">...</a>"
         * @return key-value map with parsed query parameters
         */
        static Map<String, String> parseParams(String query) { return Arrays.stream(query.split("&"))
                    .map(pair -> pair.split("=", 2))
                    .collect(java.util.stream.Collectors.toMap(pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                            pair -> pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "")
                    );
        }
        protected static final String SESSION_KEY = "NotJSESSIONID", // name of key in cookies values,
        // used to store session id
        LANG_KEY = "LANG"; // .. to store current locale
        /**
         * Retrieve value from cookie
         *
         * @param exchange http exchange context
         * @param key      key to seek in cookies values (ex. NonJSESSIONID)
         */
        protected String getCookieValue(HttpExchange exchange, String key) {
            // get cookie headers
            final List<String> cookies = exchange.getRequestHeaders().getOrDefault("Cookie", Collections.emptyList());
            if (cookies.isEmpty()) return null;
            // Sample header:
            // Cookie SID=31d4d96e407aad42; NonJSESSIONID=26288ab0-bc8f-4c4c-8982-fa5e2408db19
            for (String c : cookies) {
                if (c == null || c.isBlank() ||!c.contains(key) || !c.contains("=") ||!c.contains(";")) continue;
                LOG.log(Level.FINE, "cookie value: %s".formatted(c));
                final String[] parts = c.split(";");
                for (String p : parts) {
                        LOG.log(Level.FINE, "cookie value part %s".formatted(p));
                        // check if part is null or blank and skip if so, trim - otherwise
                        if (p == null || p.isBlank()) continue; else p = p.trim();
                        // if part does not start with key or does not contain '=' symbol - skip
                        if (!p.startsWith(key) || !p.contains("=")) continue;
                        // otherwise - split with '=' and return second element, that will be our value
                        final String[] kv = p.split("=");return kv[1];
                }
                // if cookie starts with our key - just strip it and return
                if (c.startsWith(key)) return c.substring((key + "=").length());
            }
            return null;
        }
        /**
         * Checks that current request is not POST
         * We allow only POST for some endpoints.
         *
         * @param exchange current http context
         * @return true if this request is not POST
         * false - otherwise
         */
        protected boolean checkIfNonPostRequest(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) return false;
            LOG.log(Level.FINE, "bad request: only POST allowed");
            respondBadRequest(exchange); return true;
        }
        /**
         * Respond redirect message, browser should redirect user to specified url
         *
         * @param exchange  current exchange context
         * @param targetUrl target url
         */
        protected void respondRedirect(HttpExchange exchange, String targetUrl) throws IOException {
            exchange.getResponseHeaders().add("Location", targetUrl);
            exchange.sendResponseHeaders(301, 0); exchange.close();
        }
        protected void respondNotFound(HttpExchange exchange) throws IOException {
            exchange.sendResponseHeaders(404, 0);exchange.close();
        }
        /**
         * Respond 400 Bad Request
         * This is used as universal 'bad request' answer.
         *
         * @param exchange current http exchange context
         */
        protected void respondBadRequest(HttpExchange exchange) throws IOException {
            exchange.sendResponseHeaders(400, 0);exchange.close();
        }
        /**
         * Responds binary data with 200 OK status
         *
         * @param exchange current http context
         * @param data     array with binary data
         */
        protected void respondData(HttpExchange exchange, byte[] data) throws IOException {
            boolean gzip = false;
            if (exchange.getRequestHeaders().containsKey("Accept-Encoding"))
                for (String part : exchange.getRequestHeaders().get("Accept-Encoding"))
                    if (part != null && part.toLowerCase().contains("gzip")) { gzip = true; break; }
            if (gzip) {
                exchange.getResponseHeaders().add("Content-Encoding", "gzip");
                exchange.sendResponseHeaders(200,0);
                LOG.log(Level.FINE, "respond gzipped content sz: %d".formatted(data.length));
                try (OutputStream out = new java.util.zip.GZIPOutputStream(exchange.getResponseBody())) { out.write(data); out.flush();}
            } else { exchange.sendResponseHeaders(200, data.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(data); os.flush(); }
            }
        }
    }
    /**
     * This class used to avoid 'unchecked casts' all the time when I need plain Object as key or value.
     * @param <K>
     * @param <V>
     */
    static class TypedHashMap<K,V> extends HashMap<K,V> {
        /**
         * Gets value with specified type
         * @param key
         *          provided key
         * @param defaultValue
         *          default value - with return if not found
         * @return
         *      typed value
         * @param <T>
         *          specified value type
         */
        @SuppressWarnings("unchecked")
        <T> T getTyped(K key,T defaultValue) { return super.containsKey(key) ? (T)get(key) : defaultValue;}
    }
}
