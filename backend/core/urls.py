# backend/core/urls.py

from django.urls import path
from django.contrib.auth import views as auth_views
from django.conf import settings
from django.conf.urls.static import static

from .views import (
    # Web Views
    dashboard_redirect,
    teacher_dashboard,
    student_dashboard,
    export_attendance_csv,
    send_defaulter_warnings,
    start_timetable_class,
    start_extra_class,
    end_lecture,
    manage_students,
    bulk_upload_students,
    manage_timetable,
    add_schedule,
    profile,
    coming_soon,
    student_analytics,
    live_monitor,
    CustomPasswordChangeView,
    virtual_id_view, 
    device_integrity_view, 
    report_lost_device, 
    contact_mentor_view, 
    send_mentor_message,
    student_leave_view, 
    teacher_leave_view, 
    process_leave_action,
    
    # API Views
    app_login,
    update_profile,
    change_password,
    attendance_history,
    hardware_sync,
    live_lecture_status,
    get_dynamic_qr_token,
    student_daily_attendance_api,
    verify_virtual_id,
    verify_gate_pass,

    # New RBAC Views
    super_admin_dashboard, academic_coordinator_dashboard, hod_dashboard,
    tg_dashboard, security_dashboard, librarian_dashboard, finance_dashboard,
    parent_dashboard, reset_student_device,

    # Core Engine Expansions
    generate_transcript,
    initiate_payment, webhook_payment_success,

    # ── Phase B: Full Feature Parity APIs ────────────────────
    api_teacher_timetable,
    api_start_class,
    api_start_extra_class,
    api_end_class,
    api_live_monitor,
    api_leave_requests,
    api_process_leave,
    api_student_apply_leave,
    api_student_leave_history,
    api_app_latest,
    download_latest_app,
    api_qr_token,
    api_hod_stats,
    api_parent_children,
    api_fee_invoices,
)

urlpatterns = [
    # ============================================
    # 🔐 1. WEB PORTAL AUTHENTICATION
    # ============================================
    path('accounts/login/', auth_views.LoginView.as_view(
        template_name='login.html',
        redirect_authenticated_user=True
    ), name='login'),
    
    path('accounts/logout/', auth_views.LogoutView.as_view(
        next_page='login'
    ), name='logout'),

    path('accounts/password-change/', CustomPasswordChangeView.as_view(), name='change_password'),

    # ============================================
    # 💻 2. GENERAL WEB VIEWS (Dashboard & Profile)
    # ============================================
    path('', dashboard_redirect, name='home'),
    path('dashboard/', dashboard_redirect, name='dashboard'),
    path('profile/', profile, name='profile'),
    path('coming-soon/<str:module_name>/', coming_soon, name='coming_soon'),

    # ============================================
    # 👨‍🏫 3. TEACHER PORTAL VIEWS
    # ============================================
    path('dashboard/teacher/', teacher_dashboard, name='teacher_dashboard'),
    
    # Active feature
    path('dashboard/teacher/live/', live_monitor, name='live_monitor'),
    path('dashboard/teacher/start-class/<int:timetable_id>/', start_timetable_class, name='start_timetable_class'),
    path('dashboard/teacher/start-extra-class/', start_extra_class, name='start_extra_class'),
    path('dashboard/teacher/end-lecture/', end_lecture, name='end_lecture'),
    
    path('dashboard/teacher/export-csv/', export_attendance_csv, name='export_attendance_csv'),
    path('dashboard/teacher/send-warnings/', send_defaulter_warnings, name='send_warnings'),

    # Stubbed features (Prevents NoReverseMatch crashes)
    path('dashboard/teacher/subjects/', coming_soon, {'module_name': 'My Subjects'}, name='my_subjects'),
    path('dashboard/teacher/reports/', coming_soon, {'module_name': 'Attendance Reports'}, name='attendance_reports'),

    # ============================================
    # 🎓 4. STUDENT PORTAL VIEWS
    # ============================================
    path('dashboard/student/', student_dashboard, name='student_dashboard'),
    path('dashboard/student/analytics/', student_analytics, name='attendance_detailed'),

    # ============================================
    # 🏛️ 5. REGISTRAR / ADMIN VIEWS
    # ============================================
    path('registrar/students/', manage_students, name='manage_students'),
    path('registrar/upload/', bulk_upload_students, name='upload_students'),
    path('registrar/schedule/', manage_timetable, name='manage_timetable'),
    path('registrar/schedule/add/', add_schedule, name='add_schedule'),

    # ============================================
    # 📱 6. MOBILE APP APIs (Android / JSON)
    # ============================================
    path('auth/login/', app_login, name='app_login'),
    path('auth/change-password/', change_password, name='api_change_password'),
    path('profile/', update_profile, name='api_profile_detail'),       # GET: fetch profile
    path('profile/update/', update_profile, name='api_update_profile'), # POST: save profile
    path('attendance/history/', attendance_history, name='attendance_history'),
    
    # ✅ SECURITY SCANNER API VIEWS
    path('verify-virtual-id/', verify_virtual_id, name='verify_virtual_id'),
    path('verify-gate-pass/', verify_gate_pass, name='verify_gate_pass'),

    # ============================================
    # 📡 7. IOT HARDWARE & LIVE MONITOR APIs
    # ============================================
    # ESP32 Pushes data here (The passive BLE engine)
    path('hardware/sync/', hardware_sync, name='hardware_sync'),

    # Frontend polls data from here
    path('lecture/<int:lecture_id>/live-status/', live_lecture_status, name='live_status_api'),

    # ============================================
    # 📝 8. LEAVE MANAGEMENT
    # ============================================
    path('dashboard/student/leave/', student_leave_view, name='leave_application'),
    path('dashboard/teacher/leave/', teacher_leave_view, name='leave_approvals'),
    path('dashboard/teacher/leave/process/<int:request_id>/', process_leave_action, name='process_leave'),
    
    # ============================================
    # ⚡ STUDENT QUICK ACTIONS
    # ============================================
    path('dashboard/student/virtual-id/', virtual_id_view, name='virtual_id'),
    path('dashboard/student/device-integrity/', device_integrity_view, name='device_integrity'),
    path('dashboard/student/device/report-lost/', report_lost_device, name='report_lost_device'),
    path('dashboard/student/contact-mentor/', contact_mentor_view, name='contact_mentor'),
    path('dashboard/student/contact-mentor/send/', send_mentor_message, name='send_mentor_message'),
    path('download-app/', download_latest_app, name='download_latest_app'),
    
    # Generates the cryptographically signed token for the Virtual ID
    path('dashboard/student/api/qr-token/', get_dynamic_qr_token, name='get_qr_token'),
    
    # ============================================
    # 📡 AJAX & MODAL APIs
    # ============================================
    path('student/attendance/day/<str:date_string>/', student_daily_attendance_api, name='daily_attendance_api'),

    # ============================================
    # 🌐 10. NEW RBAC DASHBOARDS
    # ============================================
    path('dashboard/super-admin/', super_admin_dashboard, name='super_admin_dashboard'),
    path('dashboard/academic-coordinator/', academic_coordinator_dashboard, name='academic_coordinator_dashboard'),
    path('dashboard/hod/', hod_dashboard, name='hod_dashboard'),
    path('dashboard/tg/', tg_dashboard, name='tg_dashboard'),
    path('dashboard/tg/reset-device/<int:student_id>/', reset_student_device, name='reset_student_device'),
    path('dashboard/security/', security_dashboard, name='security_dashboard'),
    path('dashboard/librarian/', librarian_dashboard, name='librarian_dashboard'),
    path('dashboard/parent/', parent_dashboard, name='parent_dashboard'),
    
    # ============================================
    # 📄 11. ACADEMIC ENGINES (Transcripts)
    # ============================================
    path('student/<int:student_id>/transcript/', generate_transcript, name='generate_transcript'),
    
    # ============================================
    # 💳 12. FINANCIAL GATEWAYS
    # ============================================
    path('finance/pay/<int:invoice_id>/', initiate_payment, name='initiate_payment'),
    path('finance/webhook/success/', webhook_payment_success, name='webhook_payment_success'),

    # ============================================
    # 🚀 13. PHASE B: FULL FEATURE PARITY APIs
    # ============================================

    # ── Teacher: Class Control ─────────────────
    path('teacher/timetable/', api_teacher_timetable, name='api_teacher_timetable'),
    path('teacher/lecture/start/', api_start_class, name='api_start_class'),
    path('teacher/lecture/start-extra/', api_start_extra_class, name='api_start_extra_class'),
    path('teacher/lecture/end/', api_end_class, name='api_end_class'),
    path('teacher/lecture/<int:lecture_id>/live/', api_live_monitor, name='api_live_monitor'),

    # ── Leave Management ───────────────────────
    path('leaves/', api_leave_requests, name='api_leave_requests'),
    path('leaves/<int:request_id>/action/', api_process_leave, name='api_process_leave'),

    # ── Student: Leave & QR ────────────────────
    path('student/leave/apply/', api_student_apply_leave, name='api_student_apply_leave'),
    path('student/leave/history/', api_student_leave_history, name='api_student_leave_history'),

    # ============================================
    # 📱 14. OTA DISTRIBUTION API
    # ============================================
    path('app/latest/', api_app_latest, name='api_app_latest'),
    path('student/qr-token/', api_qr_token, name='api_qr_token'),

    # ── HOD ────────────────────────────────────
    path('hod/stats/', api_hod_stats, name='api_hod_stats'),

    # ── Parent ─────────────────────────────────
    path('parent/children/', api_parent_children, name='api_parent_children'),

    # ── Finance ────────────────────────────────
    path('finance/invoices/', api_fee_invoices, name='api_fee_invoices'),
]