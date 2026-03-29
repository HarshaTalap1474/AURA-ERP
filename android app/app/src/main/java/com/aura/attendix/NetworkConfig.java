package com.aura.attendix;

public class NetworkConfig {

    // BASE_URL injected via build.gradle.kts → currently: https://demo.harshalabs.online
    public static final String BASE_URL = BuildConfig.BASE_URL;

    // ── Auth ──────────────────────────────────────────────────
    public static final String URL_LOGIN           = BASE_URL + "/api/auth/login/";
    public static final String URL_CHANGE_PASSWORD = BASE_URL + "/api/auth/change-password/";

    // ── Profile ───────────────────────────────────────────────
    public static final String URL_PROFILE_DETAIL  = BASE_URL + "/api/profile/";
    public static final String URL_PROFILE_UPDATE  = BASE_URL + "/api/profile/update/";

    // ── Attendance ────────────────────────────────────────────
    public static final String URL_ATTENDANCE_HISTORY = BASE_URL + "/api/attendance/history/";

    // ── Teacher: Timetable & Class Control ────────────────────
    public static final String URL_TEACHER_TIMETABLE    = BASE_URL + "/api/teacher/timetable/";
    public static final String URL_START_CLASS          = BASE_URL + "/api/teacher/lecture/start/";
    public static final String URL_START_EXTRA_CLASS    = BASE_URL + "/api/teacher/lecture/start-extra/";
    public static final String URL_END_CLASS            = BASE_URL + "/api/teacher/lecture/end/";
    // Usage: String.format(URL_LIVE_MONITOR, lectureId)
    public static final String URL_LIVE_MONITOR         = BASE_URL + "/api/teacher/lecture/%d/live/";

    // ── Leave Management ─────────────────────────────────────
    public static final String URL_LEAVE_REQUESTS       = BASE_URL + "/api/leaves/";
    // Usage: String.format(URL_PROCESS_LEAVE, requestId)
    public static final String URL_PROCESS_LEAVE        = BASE_URL + "/api/leaves/%d/action/";

    // ── Student: Leave & QR ───────────────────────────────────
    public static final String URL_STUDENT_APPLY_LEAVE  = BASE_URL + "/api/student/leave/apply/";
    public static final String URL_STUDENT_LEAVE_HISTORY= BASE_URL + "/api/student/leave/history/";
    // ⚡ Correct QR token endpoint (api_qr_token view, not the old web-view)
    public static final String URL_QR_TOKEN             = BASE_URL + "/api/student/qr-token/";

    // ── Security (gate pass QR scan) ─────────────────────────
    // Always use verify-virtual-id for scanning a student's Virtual ID QR
    public static final String URL_VERIFY_VIRTUAL_ID    = BASE_URL + "/api/verify-virtual-id/";
    // Only use verify-gate-pass when scanning an approved leave gate-pass QR
    public static final String URL_VERIFY_GATE_PASS     = BASE_URL + "/api/verify-gate-pass/";

    // ── OTA Updates ───────────────────────────────────────────
    public static final String URL_API_LATEST_APP       = BASE_URL + "/api/app/latest/";

    // ── HOD ───────────────────────────────────────────────────
    public static final String URL_HOD_STATS            = BASE_URL + "/api/hod/stats/";

    // ── Parent ────────────────────────────────────────────────
    public static final String URL_PARENT_CHILDREN      = BASE_URL + "/api/parent/children/";

    // ── Finance ───────────────────────────────────────────────
    public static final String URL_FEE_INVOICES         = BASE_URL + "/api/finance/invoices/";
    // Usage: String.format(URL_PAY_INVOICE, invoiceId)
    public static final String URL_PAY_INVOICE          = BASE_URL + "/api/finance/pay/%d/";

    // ── IoT Hardware (ESP32 only — NOT called by app) ─────────
    public static final String URL_HARDWARE_SYNC        = BASE_URL + "/api/hardware/sync/";

    // Helper to prevent instantiation
    private NetworkConfig() {}
}
