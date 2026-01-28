from django.contrib import admin
from django.contrib.auth.admin import UserAdmin
from .models import (
    User, 
    Department, 
    Batch, 
    Semester, 
    Classroom, 
    Course, 
    StudentProfile, 
    TeacherProfile, 
    TimeTable, 
    Lecture, 
    Attendance
)

# 1. Custom User Admin (to show the "Role" field)
class CustomUserAdmin(UserAdmin):
    fieldsets = UserAdmin.fieldsets + (
        ('Aura Roles', {'fields': ('role', 'phone_number', 'device_fingerprint')}),
    )
    list_display = ['username', 'email', 'role', 'is_active']
    list_filter = ['role']

# 2. Register User Model
admin.site.register(User, CustomUserAdmin)

# 3. Register Academic Structure
@admin.register(Department)
class DepartmentAdmin(admin.ModelAdmin):
    list_display = ('name', 'code')

@admin.register(Batch)
class BatchAdmin(admin.ModelAdmin):
    list_display = ('department', 'year')

@admin.register(Semester)
class SemesterAdmin(admin.ModelAdmin):
    list_display = ('number', 'is_active')

@admin.register(Classroom)
class ClassroomAdmin(admin.ModelAdmin):
    list_display = ('room_number', 'capacity', 'esp_device_id')

@admin.register(Course)
class CourseAdmin(admin.ModelAdmin):
    list_display = ('code', 'name', 'department', 'semester')
    list_filter = ('department', 'semester')

# 4. Register Profiles
@admin.register(StudentProfile)
class StudentProfileAdmin(admin.ModelAdmin):
    list_display = ('roll_no', 'user', 'batch', 'current_semester')
    search_fields = ('roll_no', 'user__username')

@admin.register(TeacherProfile)
class TeacherProfileAdmin(admin.ModelAdmin):
    list_display = ('employee_id', 'user', 'department')

# 5. Register Operations
@admin.register(TimeTable)
class TimeTableAdmin(admin.ModelAdmin):
    list_display = ('day_of_week', 'start_time', 'course', 'classroom')
    list_filter = ('day_of_week', 'classroom')

@admin.register(Lecture)
class LectureAdmin(admin.ModelAdmin):
    list_display = ('course', 'teacher', 'classroom', 'start_time', 'is_active')
    list_filter = ('is_active', 'start_time')

@admin.register(Attendance)
class AttendanceAdmin(admin.ModelAdmin):
    list_display = ('student', 'lecture', 'status', 'timestamp')
    list_filter = ('status', 'lecture__course')
    

@admin.action(description='🔓 Reset Device Lock')
def reset_device_lock(modeladmin, request, queryset):
    # Sets the fingerprint to None for all selected users
    queryset.update(device_fingerprint=None)
    modeladmin.message_user(request, "Selected accounts have been reset. They can now bind a new device.")

# Register the updated User Admin
class CustomUserAdmin(UserAdmin):
    # Add the new action to the list
    actions = [reset_device_lock]
    
    # Optional: Display the fingerprint in the admin list view
    list_display = UserAdmin.list_display + ('role', 'device_fingerprint',)
    
    # Add the field to the edit page
    fieldsets = UserAdmin.fieldsets + (
        ('Hardware Security', {'fields': ('device_fingerprint',)}),
    )

# Unregister the default User model and register yours
# (If you haven't already done this in your project)
# admin.site.register(User, CustomUserAdmin)
# Note: If you already registered User, just add `actions = [reset_device_lock]` to it.