package com.example.networktoggle.bridge;

import java.util.List;

interface IPrivilegedService {
    int getUid();
    int getPid();
    boolean ping(String token);
    int executeShell(String token, String command, out List<String> outputLines);
    boolean setPreferredNetworkType(String token, int subId, int networkType);
    boolean setAllowedNetworkTypesForReason(String token, int subId, int reason, long allowedBitmask);
    boolean putGlobalSetting(String token, String key, String value);
    boolean grantRuntimePermission(String token, String packageName, String permission);
    void exit(String token);
}
