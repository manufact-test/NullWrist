package com.manufacttest.nullwrist.time2;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * QMP transport for a *local, app-private* emulator socket.
 * A single worker thread owns the connection; never execute on Android UI
 * thread. Replies are consumed in order; unsolicited QMP events are skipped.
 * Must only be used after a real Emery QEMU instance is alive.
 */
public final class EmeryQmpClient implements Closeable {
    private final LocalSocket socket;
    private final BufferedReader incoming;
    private final BufferedWriter outgoing;
    private int nextId = 1;
    private boolean closed;

    public EmeryQmpClient(String filesystemSocketPath) throws IOException {
        if (filesystemSocketPath == null || filesystemSocketPath.isEmpty()) {
            throw new IllegalArgumentException("QMP socket path required");
        }
        socket = new LocalSocket();
        try {
            socket.connect(new LocalSocketAddress(
                    filesystemSocketPath, LocalSocketAddress.Namespace.FILESYSTEM));
            socket.setSoTimeout(3_000);
            incoming = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            outgoing = new BufferedWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8));
            JSONObject greeting = readJsonLine();
            if (!greeting.has("QMP")) throw new IOException("No QMP greeting from emulator");
            request(EmeryQmpCommands.capabilities());
        } catch (IOException | RuntimeException error) {
            try { socket.close(); } catch (IOException ignored) {}
            throw error;
        }
    }

    public synchronized void press(EmeryQmpCommands.Button button) throws IOException {
        request(EmeryQmpCommands.button(button));
    }

    public synchronized void touch(EmeryQmpCommands.TouchPhase phase,
                                    int x, int y) throws IOException {
        request(EmeryQmpCommands.touch(phase,x,y));
    }

    /**
     * Explicit screendump makes QEMU refresh its graphic console while
     * -display none is in use: with no display refresh listener, the normal
     * GUI timer never drives gfx_update and native framebuffer export stalls.
     * The PPM file is diagnostic; the real LCD pixels are read from the
     * independent 200x228 ARGB8888 shared-file exporter.
     */
    public synchronized void refreshHeadlessDisplay(String privatePpmPath)
            throws IOException {
        if(privatePpmPath==null || privatePpmPath.isEmpty())
            throw new IllegalArgumentException("Missing diagnostic screenshot path");
        try {
            JSONObject cmd=new JSONObject();
            cmd.put("execute","screendump");
            JSONObject args=new JSONObject();
            args.put("filename",privatePpmPath);
            cmd.put("arguments",args);
            request(cmd.toString());
        } catch(JSONException bad) {
            throw new IOException("Cannot build QEMU screenshot command",bad);
        }
    }

    /** QEMU shutdown protocol, not a SIGTERM. Call on the sole QMP worker. */
    public synchronized void quit() throws IOException {
        // Some QEMU builds close the socket immediately after accepting quit.
        // The process termination (not just a JSON reply) is authoritative.
        try {
            request("{\\"execute\\":\\"quit\\"}");
        } catch(IOException disconnected) {
            if(socket.isConnected() && !closed) {
                // The caller waits for actual Process termination and retains
                // the raw flash backing file in all failure cases.
            }
        }
    }

    private JSONObject readJsonLine() throws IOException {
        // Bound a single line to protect against corrupted/malicious output.
        StringBuilder line = new StringBuilder();
        for (;;) {
            int c = incoming.read();
            if (c == -1) throw new IOException("QMP closed unexpectedly");
            if (c == '\n') break;
            if (line.length() >= 65_536) throw new IOException("QMP response too long");
            line.append((char)c);
        }
        try { return new JSONObject(line.toString()); }
        catch (JSONException bad) { throw new IOException("Invalid QMP JSON",bad); }
    }

    private synchronized void request(String command) throws IOException {
        if (closed) throw new IOException("QMP socket is closed");
        final int id = nextId++;
        final JSONObject message;
        try {
            message = new JSONObject(command);
            message.put("id",id);
        } catch (JSONException bad) {
            throw new IOException("Invalid QMP command",bad);
        }
        outgoing.write(message.toString());
        outgoing.write('\n');
        outgoing.flush();

        for (int received=0; received<100; received++) {
            JSONObject result=readJsonLine();
            if (result.has("event")) continue;
            // Only one request is in flight; still validate the reply ID.
            if (result.optInt("id",-1)!=id)
                throw new IOException("QMP reply ID mismatch");
            if (result.has("error"))
                throw new IOException("QMP rejected command: "
                        + result.optJSONObject("error"));
            if (!result.has("return"))
                throw new IOException("Missing QMP return");
            return;
        }
        throw new IOException("Too many unrelated QMP events");
    }

    @Override public synchronized void close() throws IOException {
        closed=true;
        socket.close();
    }
}
