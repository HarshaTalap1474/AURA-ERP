from django.contrib import admin
from django.contrib.auth.admin import UserAdmin
from django.utils.html import format_html
from .models import (
    User, Department, Batch, Semester, Classroom, Course, 
    StudentProfile, TeacherProfile, TimeTable, Lecture, Attendance
)

@admin.action(description='🔓 RESET DEVICE LOCK')
def reset_device_lock(modeladmin, request, queryset):
    queryset.update(device_fingerprint=None)
    modeladmin.message_user(request, "Selected devices reset.")

@admin.register(User)
class CustomUserAdmin(UserAdmin):
    actions = [reset_device_lock]
    list_display = ('username', 'get_full_name', 'role', 'phone_number', 'device_status_icon')
    list_filter = ('role', 'is_active')
    search_fields = ('username', 'first_name', 'last_name')
    fieldsets = UserAdmin.fieldsets + (
        ('Aura Details', {'fields': ('role', 'phone_number')}),
        ('Hardware Security', {'fields': ('device_fingerprint',)}),
    )

    def device_status_icon(self, obj):
        return format_html('<span style="color:green;">🔒 Linked</span>') if obj.device_fingerprint else format_html('<span style="color:orange;">⚠️ No Device</span>')
    
    def get_full_name(self, obj): return f"{obj.first_name} {obj.last_name}"

# Register other models standardly...
admin.site.register(Department)
admin.site.register(Batch)
admin.site.register(Semester)
admin.site.register(Classroom)
admin.site.register(Course)
admin.site.register(StudentProfile)
admin.site.register(TeacherProfile)
admin.site.register(TimeTable)
admin.site.register(Lecture)
admin.site.register(Attendance)