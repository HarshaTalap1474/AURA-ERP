from django.contrib import admin
from django.urls import path, include
from django.contrib.auth.views import LoginView, LogoutView
from django.conf import settings
from django.conf.urls.static import static

from core.views import (
    teacher_dashboard,
    student_dashboard,
    dashboard_redirect,
    custom_logout,
)

urlpatterns = [
    # ======================
    # Admin
    # ======================
    path('admin/', admin.site.urls),

    # ======================
    # App Routes (Includes API and other Core views)
    # ======================
    path('api/', include('core.urls')),

    # ======================
    # Auth (Web)
    # ======================
    path('login/', LoginView.as_view(
        template_name='login.html', 
        redirect_authenticated_user=True # Prevents logged-in users from seeing the login screen
    ), name='login'),
    
    # ======================
    # Dashboards
    # ======================
    path('dashboard/', dashboard_redirect, name='dashboard'),
    path('dashboard/teacher/', teacher_dashboard, name='teacher_dashboard'),
    path('dashboard/student/', student_dashboard, name='student_dashboard'),
    path('logout/', custom_logout, name='logout'),

    # ======================
    # Root → Login
    # ======================
    path('', LoginView.as_view(
        template_name='login.html', 
        redirect_authenticated_user=True
    )),
]

# ==========================================
# ✅ MEDIA FIX: SERVE FILES IN DEVELOPMENT
# ==========================================
# This tells Django exactly where to find your PDFs and Images
if settings.DEBUG:
    urlpatterns += static(settings.MEDIA_URL, document_root=settings.MEDIA_ROOT)