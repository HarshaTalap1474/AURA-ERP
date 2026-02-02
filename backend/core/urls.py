from django.urls import path
from django.contrib.auth import views as auth_views
from .views import (
    # Web Views
    dashboard_redirect,
    teacher_dashboard,
    student_dashboard,
    manage_students,
    bulk_upload_students,
    manage_timetable,
    add_schedule,
    profile,
    coming_soon,
    
    # API Views
    app_login,
    update_profile,
    change_password,
    attendance_history,
    mark_attendance,
    hardware_sync,
    student_analytics,
)

urlpatterns = [
    # ============================================
    # 🔐 WEB PORTAL AUTHENTICATION
    # ============================================
    path('accounts/login/', auth_views.LoginView.as_view(
        template_name='login.html',
        redirect_authenticated_user=True  # ✅ Prevents Ghost Sidebar
    ), name='login'),
    
    path('accounts/logout/', auth_views.LogoutView.as_view(
        next_page='login'
    ), name='logout'),

    # ============================================
    # 📱 MOBILE APP APIs (Android)
    # ============================================
    path('api/auth/login/', app_login, name='app_login'),
    path('api/auth/change-password/', change_password, name='change_password'),
    path('api/profile/update/', update_profile, name='update_profile'),
    path('api/attendance/history/', attendance_history, name='attendance_history'),
    path('api/attendance/mark/', mark_attendance, name='mark_attendance'),

    # ============================================
    # 📡 IOT HARDWARE APIs (ESP32)
    # ============================================
    path('api/attendance/hardware-sync/', hardware_sync, name='hardware_sync'),

    # ============================================
    # 💻 WEB PORTAL VIEWS (Browser)
    # ============================================
    path('', dashboard_redirect, name='home'),
    path('dashboard/', dashboard_redirect, name='dashboard'),
    path('profile/', profile, name='profile'),
    path('student/analytics/', student_analytics, name='student_analytics'),
    
    # ✅ DYNAMIC COMING SOON PAGE
    # Matches URLs like: /coming-soon/reports/ or /coming-soon/settings/
    path('coming-soon/<str:module_name>/', coming_soon, name='coming_soon'),

    path('teacher/', teacher_dashboard, name='teacher_dashboard'),
    path('student/', student_dashboard, name='student_dashboard'),

    path('registrar/students/', manage_students, name='manage_students'),
    path('registrar/upload/', bulk_upload_students, name='upload_students'),

    path('schedule/', manage_timetable, name='manage_timetable'),
    path('schedule/add/', add_schedule, name='add_schedule'),
]