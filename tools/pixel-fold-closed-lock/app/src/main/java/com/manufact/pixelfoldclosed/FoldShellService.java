package com.manufact.pixelfoldclosed;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Executed by Shizuku as UID shell. Only three hardcoded commands are permitted. */
public final class FoldShellService extends IFoldControl.Stub {
    public FoldShellService() {}

    @Override public String execute(int action) {
        switch (action) {
            case 0: {
                Result r = run("cmd", "device_state", "state", "0");
                if (r.exit != 0) return "ERROR: state 0: " + r.message;
                return "OK_STATE\n" + r.message + "\n" + run("cmd", "device_state", "state").message;
            }
            case 1: {
                Result a = run("cmd", "device_state", "base-state", "reset");
                Result b = run("cmd", "device_state", "state", "reset");
                if (a.exit != 0 || b.exit != 0) return "ERROR: reset failed\n" + a.message + "\n" + b.message;
                return "OK_RESET\n" + a.message + "\n" + b.message;
            }
            case 2: {
                Result a = run("cmd", "device_state", "state");
                Result b = run("cmd", "device_state", "print-states-simple");
                return "DIAGNOSTICS\n" + a.message + "\nSupported: " + b.message;
            }
            default: return "ERROR: invalid action";
        }
    }

    private static final class Result {
        final int exit;
        final String message;
        Result(int exit, String message) { this.exit = exit; this.message = message; }
    }
    private static Result run(String... args) {
        Process p = null;
        try {
            p = new ProcessBuilder(args).redirectErrorStream(true).start();
            // Outputs are tiny for these whitelisted commands; drain to avoid blocking waitFor.
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] bytes = new byte[1024];
            int n;
            while ((n = p.getInputStream().read(bytes)) != -1 && out.size() < 16000) out.write(bytes, 0, n);
            if (!p.waitFor(6, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return new Result(-1, "timeout: " + String.join(" ", args));
            }
            int exit = p.exitValue();
            return new Result(exit, "exit=" + exit + " " +
                    new String(out.toByteArray(), StandardCharsets.UTF_8).trim());
        } catch (Throwable t) {
            if (p != null) p.destroyForcibly();
            return new Result(-1, t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }
}
