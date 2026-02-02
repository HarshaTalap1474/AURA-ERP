from django.urls import path
from django.contrib.auth import views as auth_views  # 👈 Import Django Auth Views
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
    mark_attendance,
    hardware_sync,
)

urlpatterns = [
    # ============================================
    # 🔐 WEB PORTAL AUTHENTICATION (The Fix)
    # ============================================
    # These override the default Django Admin login pages
    path('accounts/login/', auth_views.LoginView.as_view(template_name='login.html'), name='login'),
    path('accounts/logout/', auth_views.LogoutView.as_view(next_page='login'), name='logout'),

    # ============================================
    # 📱 MOBILE APP APIs (Android)
    # ============================================
    # ⚠️ Added 'api/' prefix to prevent conflicts with Web URLs
    
    # Authentication
    path('api/auth/login/', app_login, name='app_login'),
    path('api/auth/change-password/', change_password, name='change_password'),
    
    # Profile
    path('api/profile/update/', update_profile, name='update_profile'),

    # Attendance Features
    path('api/attendance/history/', attendance_history, name='attendance_history'),
    path('api/attendance/mark/', mark_attendance, name='mark_attendance'),

    # ============================================
    # 📡 IOT HARDWARE APIs (ESP32)
    # ============================================
    path('api/attendance/hardware-sync/', hardware_sync, name='hardware_sync'),

    # ============================================
    # 💻 WEB PORTAL VIEWS (Browser)
    # ============================================
    
    # Root URL -> Auto-redirects to the correct Dashboard
    path('', dashboard_redirect, name='home'),
    
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