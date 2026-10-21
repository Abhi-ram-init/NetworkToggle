package com.example.networktoggle.bridge.server;

import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import com.example.networktoggle.bridge.IPrivilegedService;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class PrivilegedServer {
    public static final String ACTION_REGISTER = "register_service";
    public static final String EXTRA_BINDER = "privileged_binder";
    public static final String EXTRA_TOKEN = "auth_token";
    public static final String EXTRA_PID = "server_pid";
    public static final String EXTRA_UID = "server_uid";

    public static void main(String[] args) {
        System.out.println("[PrivilegedServer] Initializing Privileged Backend Service (PID: " 
                + Process.myPid() + ", UID: " + Process.myUid() + ")...");

        if (args.length < 2) {
            System.err.println("[PrivilegedServer] ERROR: Missing arguments.");
            System.err.println("[PrivilegedServer] Usage: app_process /system/bin " 
                    + PrivilegedServer.class.getName() + " <auth_token> <package_name>");
            System.exit(1);
            return;
        }

        String authToken = args[0];
        String targetPackage = args[1];

        try {
            Looper.prepareMainLooper();
        } catch (Throwable t) {
            System.out.println("[PrivilegedServer] Looper notice: " + t.getMessage());
        }

        PrivilegedServiceImpl serviceImpl = new PrivilegedServiceImpl(authToken, targetPackage);

        // Initial Binder IPC dispatch
        boolean initialSent = sendBinderToClient(serviceImpl, authToken, targetPackage);
        if (initialSent) {
            System.out.println("[PrivilegedServer] Binder successfully transferred to " + targetPackage);
        } else {
            System.out.println("[PrivilegedServer] Initial connection attempt queued. Background watcher active.");
        }

        // Start background watcher daemon thread to ensure connectivity whenever the app opens
        Thread watcher = new Thread(() -> {
            int attempts = 0;
            while (true) {
                try {
                    Thread.sleep(attempts < 10 ? 2000 : 5000);
                    attempts++;
                    sendBinderToClient(serviceImpl, authToken, targetPackage);
                } catch (InterruptedException e) {
                    break;
                } catch (Throwable ignored) {}
            }
        }, "PrivilegedServer-Watcher");
        watcher.setDaemon(true);
        watcher.start();

        System.out.println("[PrivilegedServer] Privileged Backend Service is active and listening for Binder IPC.");
        Looper.loop();
    }

    public static boolean sendBinderToClient(IPrivilegedService.Stub service, String authToken, String packageName) {
        String authority = packageName + ".bridge.provider";
        try {
            // 1. Obtain IActivityManager from ServiceManager
            Class<?> smClass = Class.forName("android.os.ServiceManager");
            Method getService = smClass.getMethod("getService", String.class);
            IBinder amBinder = (IBinder) getService.invoke(null, "activity");
            if (amBinder == null) {
                return false;
            }

            Class<?> amStubClass;
            try {
                amStubClass = Class.forName("android.app.IActivityManager$Stub");
            } catch (ClassNotFoundException e) {
                amStubClass = Class.forName("android.app.ActivityManagerNative");
            }
            Method asInterface = amStubClass.getMethod("asInterface", IBinder.class);
            Object activityManager = asInterface.invoke(null, amBinder);
            if (activityManager == null) return false;

            // 2. Obtain ContentProviderHolder via getContentProviderExternal
            Object holder = null;
            Method[] methods = activityManager.getClass().getMethods();
            for (Method m : methods) {
                if (m.getName().equals("getContentProviderExternal")) {
                    try {
                        Class<?>[] pTypes = m.getParameterTypes();
                        if (pTypes.length == 4) {
                            holder = m.invoke(activityManager, authority, 0, null, "privileged_bridge");
                            if (holder != null) break;
                        } else if (pTypes.length == 3) {
                            holder = m.invoke(activityManager, authority, 0, null);
                            if (holder != null) break;
                        }
                    } catch (Throwable ignored) {}
                }
            }

            if (holder == null) {
                return false;
            }

            // 3. Extract IContentProvider from holder.provider
            Field providerField = holder.getClass().getDeclaredField("provider");
            providerField.setAccessible(true);
            Object provider = providerField.get(holder);
            if (provider == null) {
                return false;
            }

            // 4. Build Bundle payload containing our custom Binder and authorization credentials
            Bundle bundle = new Bundle();
            bundle.putBinder(EXTRA_BINDER, service);
            bundle.putString(EXTRA_TOKEN, authToken);
            bundle.putInt(EXTRA_PID, Process.myPid());
            bundle.putInt(EXTRA_UID, Process.myUid());

            // 5. Invoke IContentProvider.call(...)
            Method[] providerMethods = provider.getClass().getMethods();
            for (Method m : providerMethods) {
                if (m.getName().equals("call")) {
                    try {
                        Class<?>[] pTypes = m.getParameterTypes();
                        Object[] callArgs = new Object[pTypes.length];
                        for (int i = 0; i < pTypes.length; i++) {
                            Class<?> t = pTypes[i];
                            if (t == Bundle.class) {
                                callArgs[i] = bundle;
                            } else if (t == String.class) {
                                if (i == pTypes.length - 2) {
                                    callArgs[i] = ACTION_REGISTER;
                                } else if (i == pTypes.length - 3) {
                                    callArgs[i] = authority;
                                } else {
                                    callArgs[i] = packageName;
                                }
                            } else {
                                callArgs[i] = null;
                            }
                        }
                        m.invoke(provider, callArgs);
                        return true;
                    } catch (Throwable ignored) {}
                }
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }
}
