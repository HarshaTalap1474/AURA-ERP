from django.contrib import admin
from django.urls import path, include
from django.contrib.auth.views import LoginView, LogoutView

from core.views import (
    teacher_dashboard,
    student_dashboard,
    dashboard_redirect,
)

urlpatterns = [
    # ======================
    # Admin
    # ======================
    path('admin/', admin.site.urls),

    # ======================
    # API routes
    # ======================
    path('api/', include('core.urls')),

    # ======================
    # Auth (Web)
    # ======================
    path('login/', LoginView.as_view(template_name='login.html'), name='login'),
    path('logout/', LogoutView.as_view(next_page='login'), name='logout'),

    # ======================
    # Dashboards
    # ======================
    path('dashboard/', dashboard_redirect, name='dashboard'),
    path('dashboard/teacher/', teacher_dashboard, name='teacher_dashboard'),
    path('dashboard/student/', student_dashboard, name='student_dashboard'),

    # ======================
    # Root → Login
    # ======================
    path('', LoginView.as_view(template_name='login.html')),
]
