package com.example.networktoggle.bridge.server;

import android.os.Process;
import com.example.networktoggle.bridge.IPrivilegedService;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

public class PrivilegedServiceImpl extends IPrivilegedService.Stub {
    private final String expectedToken;
    private final String authorizedPackage;

    public PrivilegedServiceImpl(String token, String authorizedPackage) {
        this.expectedToken = token;
        this.authorizedPackage = authorizedPackage;
    }

    private void enforceAuthorized(String token) {
        if (expectedToken == null || !expectedToken.equals(token)) {
            throw new SecurityException("Unauthorized access to Privileged Backend Service: invalid token");
        }
    }

    @Override
    public int getUid() {
        return Process.myUid();
    }

    @Override
    public int getPid() {
        return Process.myPid();
    }

    @Override
    public boolean ping(String token) {
        enforceAuthorized(token);
        return true;
    }

    @Override
    public int executeShell(String token, String command, List<String> outputLines) {
        enforceAuthorized(token);
        try {
            java.lang.Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
            BufferedReader stdOut = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader stdErr = new BufferedReader(new InputStreamReader(p.getErrorStream()));

            String line;
            while ((line = stdOut.readLine()) != null) {
                if (outputLines != null) outputLines.add(line);
            }
            while ((line = stdErr.readLine()) != null) {
                if (outputLines != null) outputLines.add(line);
            }
            return p.waitFor();
        } catch (Exception e) {
            if (outputLines != null) outputLines.add("Execution error: " + e.getMessage());
            return -1;
        }
    }

    @Override
    public boolean setPreferredNetworkType(String token, int subId, int networkType) {
        enforceAuthorized(token);
        try {
            // Proxy commands to Android System Server using elevated shell privileges
            int exit = 0;
            if (subId >= 0) {
                java.lang.Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", "cmd phone set-preferred-network-type " + subId + " " + networkType});
                exit = p.waitFor();
            } else {
                for (int s = 0; s < 3; s++) {
                    Runtime.getRuntime().exec(new String[]{"sh", "-c", "cmd phone set-preferred-network-type " + s + " " + networkType}).waitFor();
                }
            }

            // Write global settings as direct persistence fallback
            Runtime.getRuntime().exec(new String[]{"sh", "-c", "settings put global preferred_network_mode " + networkType}).waitFor();
            for (int s = 0; s < 3; s++) {
                Runtime.getRuntime().exec(new String[]{"sh", "-c", "settings put global preferred_network_mode" + s + " " + networkType}).waitFor();
            }
            return exit == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean setAllowedNetworkTypesForReason(String token, int subId, int reason, long allowedBitmask) {
        enforceAuthorized(token);
        try {
            int exit = 0;
            if (subId >= 0) {
                java.lang.Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", "cmd phone set-allowed-network-types-for-reason " + subId + " " + reason + " " + allowedBitmask});
                exit = p.waitFor();
            } else {
                for (int s = 0; s < 3; s++) {
                    Runtime.getRuntime().exec(new String[]{"sh", "-c", "cmd phone set-allowed-network-types-for-reason " + s + " " + reason + " " + allowedBitmask}).waitFor();
                }
            }
            return exit == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean putGlobalSetting(String token, String key, String value) {
        enforceAuthorized(token);
        try {
            java.lang.Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", "settings put global " + key + " " + value});
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean grantRuntimePermission(String token, String packageName, String permission) {
        enforceAuthorized(token);
        try {
            java.lang.Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", "pm grant " + packageName + " " + permission});
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void exit(String token) {
        enforceAuthorized(token);
        new Thread(() -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {}
            System.exit(0);
        }).start();
    }
}
