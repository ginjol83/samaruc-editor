package com.retroeditor.service.ai;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Ciclo de vida de {@code opencode serve}: lo arranca, espera a que responda y lo apaga.
 *
 * opencode separa el motor del cliente: el TUI es un cliente más del mismo servidor. Aquí se
 * lanza el servidor en headless y se habla por HTTP, que es lo único que permite streaming de
 * verdad para el panel de chat.
 *
 * Decisiones que importan:
 * - Se elige un puerto libre y se le pasa a {@code --port}: leer el puerto del stdout del
 *   proceso obliga a parsear sus logs y es frágil entre versiones.
 * - Se espera a {@code /global/health} en lugar de dormir unos segundos: si el arranque falla
 *   (falta Node, provider sin autenticar) se nota en segundos y con un mensaje útil.
 * - El proceso se lanza con el directorio del proyecto como cwd, que es lo que opencode
 *   entiende por "workspace".
 * - El transporte es {@link HttpURLConnection}, no {@code java.net.http.HttpClient}: contra el
 *   servidor HTTP embebido de opencode, {@code HttpClient} se cuelga en los {@code POST}
 *   (timeout hasta que responde jamás), algo reproducible en Windows con JDK 17 y 24, mientras
 *   que {@code HttpURLConnection} con la misma peticion obtiene 200 en milisegundos. No usar
 *   {@code HttpClient} para este proveedor.
 */
public class OpenCodeServerBridge implements AutoCloseable {

    /** Version del protocolo usada para elegir un puerto libre. */
    private static final int ATTEMPTS_PORT = 10;
    private static final Duration HEALTH_INTERVAL = Duration.ofMillis(250);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration HEALTH_ATTEMPT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);

    private final AiSettings settings;
    private final OpenCodeRuntime runtime;
    private final Duration startupTimeout;

    private Process process;
    private volatile URI baseUri;
    private volatile Path workspace;

    public OpenCodeServerBridge(AiSettings settings) {
        this(settings, new OpenCodeRuntime(settings), Duration.ofSeconds(30));
    }

    /** Visible para tests: permite sustituir el runtime y el tiempo de espera. */
    OpenCodeServerBridge(AiSettings settings, OpenCodeRuntime runtime, Duration startupTimeout) {
        this.settings = settings != null ? settings : AiSettings.defaults();
        this.runtime = runtime;
        this.startupTimeout = startupTimeout != null ? startupTimeout : Duration.ofSeconds(30);
    }

    public boolean isAvailable() {
        return runtime != null && runtime.isAvailable();
    }

    public String unavailableReason() {
        return runtime != null ? runtime.unavailableReason() : "opencode no esta disponible";
    }

    public boolean isRunning() {
        Process current = process;
        return current != null && current.isAlive();
    }

    /** Base del servidor activo, o vacio si no arrancó. */
    public Optional<URI> baseUri() {
        return Optional.ofNullable(baseUri);
    }

    /**
     * Arranca el servidor si no lo esta. Es idempotente y seguro desde un hilo de trabajo:
     * el proceso se lanza con {@code directory(workspace)}, que es la unica forma de que
     * opencode entienda cual es el proyecto.
     *
     * Si el servidor ya esta arrancado con otro workspace se reinicia: el cwd se fija al
     * arrancar y no hay forma de cambiarlo, asi que dejarlo vivo haria que el agente leyera el
     * directorio del proyecto anterior.
     *
     * @return {@code true} si se lanzo un proceso nuevo (el llamante tiene que volver a
     *         suscribirse al stream de eventos)
     */
    public synchronized boolean start(Path workspace) throws AiProvider.AiException {
        Path workingDirectory = workspace != null ? workspace : settings.workingDirectory();
        if (isRunning()) {
            if (sameWorkspace(workingDirectory, this.workspace)) {
                return false;
            }
            stopProcess();
        }
        Optional<Path> executable = runtime.resolveExecutable();
        if (executable.isEmpty()) {
            throw new AiProvider.AiException(unavailableReason());
        }
        int port = settings.port() > 0 ? settings.port() : freePort();
        Process started;
        try {
            ProcessBuilder builder = new ProcessBuilder(executable.get().toString());
            builder.command().addAll(serveArguments(port));
            builder.redirectErrorStream(true);
            if (workingDirectory != null) {
                builder.directory(workingDirectory.toFile());
            }
            started = builder.start();
        } catch (IOException e) {
            throw new AiProvider.AiException("no se pudo arrancar opencode serve: " + e.getMessage(), e);
        }
        this.process = started;
        this.workspace = workingDirectory;
        // El servidor escribe mucho en stdout/stderr: sin draining acaba bloqueado.
        drainQuietly(started.getInputStream());
        URI candidate = URI.create("http://127.0.0.1:" + port);
        try {
            awaitHealthy(candidate);
        } catch (RuntimeException e) {
            stopProcess();
            throw e;
        }
        this.baseUri = candidate;
        return true;
    }

    static boolean sameWorkspace(Path requested, Path running) {
        if (requested == null || running == null) {
            return requested == running;
        }
        return requested.toAbsolutePath().normalize().equals(running.toAbsolutePath().normalize());
    }

    /** Espera activa a que {@code /global/health} responda sano. */
    private void awaitHealthy(URI candidate) throws AiProvider.AiException {
        long deadline = System.nanoTime() + startupTimeout.toNanos();
        IOException lastFailure = null;
        while (System.nanoTime() < deadline) {
            if (process != null && !process.isAlive()) {
                throw new AiProvider.AiException("opencode serve termino antes de arrancar "
                        + "(revisa `opencode auth`)");
            }
            try {
                HttpURLConnection connection = connect(candidate);
                try {
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout((int) HEALTH_ATTEMPT_TIMEOUT.toMillis());
                    connection.setReadTimeout((int) HEALTH_ATTEMPT_TIMEOUT.toMillis());
                    if (connection.getResponseCode() == 200) {
                        try (InputStream ignored = connection.getInputStream()) {
                            // cuerpo de bienvenida, no importa
                        }
                        return;
                    }
                    lastFailure = new IOException("health devolvio " + connection.getResponseCode());
                } finally {
                    connection.disconnect();
                }
            } catch (IOException e) {
                lastFailure = e;
            }
            sleep(HEALTH_INTERVAL);
        }
        throw new AiProvider.AiException("opencode serve no respondio en "
                + startupTimeout.toSeconds() + "s"
                + (lastFailure != null ? " (" + lastFailure.getMessage() + ")" : ""));
    }

    /** Puerto libre del sistema. Si el handshake falla se reintenta con otro. */
    private int freePort() throws AiProvider.AiException {
        for (int attempt = 0; attempt < ATTEMPTS_PORT; attempt++) {
            try (ServerSocket socket = new ServerSocket(0)) {
                socket.setReuseAddress(true);
                return socket.getLocalPort();
            } catch (IOException e) {
                // otro proceso se ha adelantado: vuelve a intentarlo
            }
        }
        throw new AiProvider.AiException("no se encontro un puerto libre para opencode serve");
    }

    /** Abre una conexion HTTP contra una ruta del servidor activo. */
    private HttpURLConnection connect(URI path) throws AiProvider.AiException {
        URI base = baseUri != null ? baseUri : URI.create("http://127.0.0.1:1");
        try {
            return (HttpURLConnection) base.resolve(path).toURL().openConnection();
        } catch (IOException e) {
            throw new AiProvider.AiException("no se pudo conectar con opencode: " + e.getMessage(), e);
        }
    }

    /** GET simple que devuelve el cuerpo crudo de la respuesta. */
    public String get(URI path) throws AiProvider.AiException {
        try {
            HttpURLConnection connection = connect(path);
            try {
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                connection.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
                connection.setReadTimeout((int) REQUEST_TIMEOUT.toMillis());
                return respond(connection, "GET " + path);
            } finally {
                connection.disconnect();
            }
        } catch (IOException e) {
            throw new AiProvider.AiException("fallo de red contra opencode: " + e.getMessage(), e);
        }
    }

    /** POST con cuerpo JSON ya serializado. */
    public String post(URI path, String jsonBody) throws AiProvider.AiException {
        try {
            HttpURLConnection connection = connect(path);
            try {
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
                connection.setReadTimeout((int) REQUEST_TIMEOUT.toMillis());
                connection.setDoOutput(true);
                byte[] payload = jsonBody != null ? jsonBody.getBytes(StandardCharsets.UTF_8) : new byte[0];
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(payload);
                }
                return respond(connection, "POST " + path);
            } finally {
                connection.disconnect();
            }
        } catch (IOException e) {
            throw new AiProvider.AiException("fallo de red contra opencode: " + e.getMessage(), e);
        }
    }

    /**
     * Abre el stream de eventos y devuelve su cuerpo. Sin timeout de lectura: el stream
     * dura lo que dure el agente y un tope fijo cortaria justo en el turno largo. El
     * llamante es quien cierra el stream.
     */
    public InputStream openEventStream(URI path) throws AiProvider.AiException {
        HttpURLConnection connection = connect(path);
        try {
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "text/event-stream");
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
            return connection.getInputStream();
        } catch (IOException e) {
            connection.disconnect();
            throw new AiProvider.AiException("no se pudo suscribir al stream de eventos de opencode: "
                    + e.getMessage(), e);
        }
    }

    private static String respond(HttpURLConnection connection, String what)
            throws IOException, AiProvider.AiException {
        int status = connection.getResponseCode();
        String body = readBody(connection, status);
        if (status >= 200 && status < 300) {
            return body != null ? body : "";
        }
        throw new AiProvider.AiException(what + " devolvio " + status + ": " + summarize(body));
    }

    private static String readBody(HttpURLConnection connection, int status) throws IOException {
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (stream == null) {
            return "";
        }
        try (InputStream in = stream) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String summarize(String body) {
        if (body == null || body.isBlank()) {
            return "sin cuerpo";
        }
        String flat = body.replaceAll("\\s+", " ").trim();
        return flat.length() > 300 ? flat.substring(0, 300) + "..." : flat;
    }

    private static void sleep(Duration duration) throws AiProvider.AiException {
        try {
            Thread.sleep(Math.max(1, duration.toMillis()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiProvider.AiException("espera de arranque interrumpida", e);
        }
    }

    private static void drainQuietly(InputStream stream) {
        if (stream == null) {
            return;
        }
        Thread reader = new Thread(() -> {
            byte[] buffer = new byte[4096];
            try (InputStream input = stream) {
                while (input.read(buffer) >= 0) {
                    // descartar: el diagnostico real llega por HTTP
                }
            } catch (IOException ignored) {
                // proceso terminado
            }
        }, "opencode-serve-output");
        reader.setDaemon(true);
        reader.start();
    }

    private void stopProcess() {
        Process current = process;
        process = null;
        baseUri = null;
        workspace = null;
        if (current == null) {
            return;
        }
        current.destroy();
        try {
            if (!current.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                current.destroyForcibly();
            }
        } catch (InterruptedException e) {
            current.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public synchronized void close() {
        stopProcess();
    }

    /** Argumentos que se pasan a {@code opencode serve}. Visible para tests. */
    static List<String> serveArguments(int port) {
        List<String> arguments = new ArrayList<>();
        arguments.add("serve");
        arguments.add("--port");
        arguments.add(Integer.toString(port));
        arguments.add("--hostname");
        arguments.add("127.0.0.1");
        return arguments;
    }
}