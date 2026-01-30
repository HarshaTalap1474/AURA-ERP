from django.urls import path
from .views import (
    dashboard_redirect,
    teacher_dashboard,
    student_dashboard,
    ESP32ScanView,
    manage_students,
    bulk_upload_students,
    manage_timetable,
    add_schedule,
    app_login,
    update_profile,
)

urlpatterns = [
    # API Endpoints
    path('attendance/scan/', ESP32ScanView.as_view(), name='esp32_scan'),
    path('auth/login/', app_login, name='app_login'),
    

    # Web Views
    path('dashboard/', dashboard_redirect, name='dashboard'),
    path('teacher/', teacher_dashboard, name='teacher_dashboard'),
    path('student/', student_dashboard, name='student_dashboard'),
    path('registrar/students/', manage_students, name='manage_students'),
    path('registrar/upload/', bulk_upload_students, name='upload_students'),
    path('schedule/', manage_timetable, name='manage_timetable'),
    path('schedule/add/', add_schedule, name='add_schedule'),
    path('profile/update/', update_profile, name='update_profile'),
]