package com.aura.attendix;

public class NetworkConfig {

    // 1. CHANGE THIS ONE LINE when your Tunnel changes
    public static final String BASE_URL = "https://river-analog-hudson-violations.trycloudflare.com";

    // 2. The Endpoints are automatically built on top of the Base URL
    public static final String URL_LOGIN = BASE_URL + "/api/auth/login/";
    public static final String URL_PROFILE_UPDATE = BASE_URL + "/api/profile/update/";
    public static final String URL_CHANGE_PASSWORD = BASE_URL + "/api/auth/change-password/";
    public static final String URL_TIMETABLE_CURRENT = BASE_URL + "/api/timetable/current/";
    public static final String URL_ATTENDANCE_HISTORY = BASE_URL + "/api/attendance/history/";

    // Helper to prevent instantiation
    private NetworkConfig() {}
}