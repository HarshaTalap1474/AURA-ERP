from django.urls import path
from .views import (
    # Web Views
    dashboard_redirect,
    teacher_dashboard,
    student_dashboard,
    manage_students,
    bulk_upload_students,
    manage_timetable,
    add_schedule,
    
    # API Views (Mobile & IoT)
    app_login,
    update_profile,
    change_password,
    attendance_history,
    mark_attendance,  # ⚠️ Added back (Required for Phone->Room Scan)
    ESP32ScanView,
)

urlpatterns = [
    # ============================================
    # 📱 MOBILE APP APIs (Android)
    # ============================================
    
    # Authentication
    path('auth/login/', app_login, name='app_login'),
    path('auth/change-password/', change_password, name='change_password'),
    
    # Profile
    path('profile/update/', update_profile, name='update_profile'),

    # Attendance Features
    path('attendance/history/', attendance_history, name='attendance_history'),
    path('attendance/mark/', mark_attendance, name='mark_attendance'),

    # ============================================
    # 📡 IOT HARDWARE APIs (ESP32)
    # ============================================
    path('hardware/scan/', ESP32ScanView.as_view(), name='esp32_scan'),

    # ============================================
    # 💻 WEB PORTAL VIEWS (Browser)
    # ============================================
    
    # Dashboard Router
    path('dashboard/', dashboard_redirect, name='dashboard'),

    # Role-Specific Dashboards
    path('teacher/', teacher_dashboard, name='teacher_dashboard'),
    path('student/', student_dashboard, name='student_dashboard'),

    # Registrar Tools
    path('registrar/students/', manage_students, name='manage_students'),
    path('registrar/upload/', bulk_upload_students, name='upload_students'),

    # Scheduling
    path('schedule/', manage_timetable, name='manage_timetable'),
    path('schedule/add/', add_schedule, name='add_schedule'),
]