from django.contrib import admin
from django.contrib.auth.admin import UserAdmin
from django.utils.html import format_html
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

# =========================================
# 1. CUSTOM ACTIONS (Registrar Tools)
# =========================================

@admin.action(description='🔓 RESET DEVICE LOCK (Allow new phone)')
def reset_device_lock(modeladmin, request, queryset):
    """
    Clears the device_fingerprint for selected users, 
    allowing them to login on a new device.
    """
    updated_count = queryset.update(device_fingerprint=None)
    
    if updated_count == 1:
        message = "1 student device was successfully reset."
    else:
        message = f"{updated_count} student devices were successfully reset."
    
    modeladmin.message_user(request, message)


# =========================================
# 2. USER MANAGEMENT (The Security Center)
# =========================================

@admin.register(User)
class CustomUserAdmin(UserAdmin):
    # Add the "Reset" action to the dropdown menu
    actions = [reset_device_lock]

    # Columns to show in the list
    list_display = ('username', 'get_full_name', 'role', 'device_status_icon', 'is_active')
    
    # Filters for the sidebar
    list_filter = ('role', 'is_active', 'groups')
    
    # Search box (Finds by Username, First Name, Last Name, or Email)
    search_fields = ('username', 'first_name', 'last_name', 'email')

    # Edit Page Layout
    fieldsets = UserAdmin.fieldsets + (
        ('Aura Details', {'fields': ('role', 'phone_number')}),
        ('Hardware Security', {'fields': ('device_fingerprint',)}),
    )

    # Helper: Show a visual icon for Device Status
    def device_status_icon(self, obj):
        if obj.device_fingerprint:
            return format_html('<span style="color:green; font-weight:bold;">🔒 Linked</span>')
        return format_html('<span style="color:orange;">⚠️ No Device</span>')
    
    device_status_icon.short_description = "Device Lock"
    
    # Helper: Show full name
    def get_full_name(self, obj):
        return f"{obj.first_name} {obj.last_name}"
    get_full_name.short_description = "Name"


# =========================================
# 3. ACADEMIC STRUCTURE
# =========================================

@admin.register(Department)
class DepartmentAdmin(admin.ModelAdmin):
    list_display = ('name', 'code')
    search_fields = ('name', 'code')

@admin.register(Batch)
class BatchAdmin(admin.ModelAdmin):
    list_display = ('get_batch_name', 'department', 'year')
    list_filter = ('department', 'year')
    
    def get_batch_name(self, obj):
        return str(obj)
    get_batch_name.short_description = "Batch Name"

@admin.register(Semester)
class SemesterAdmin(admin.ModelAdmin):
    list_display = ('number', 'is_active')
    list_editable = ('is_active',)  # Allow quick activation/deactivation from list

@admin.register(Classroom)
class ClassroomAdmin(admin.ModelAdmin):
    list_display = ('room_number', 'capacity', 'esp_device_id')
    search_fields = ('room_number', 'esp_device_id')

@admin.register(Course)
class CourseAdmin(admin.ModelAdmin):
    list_display = ('code', 'name', 'department', 'semester')
    list_filter = ('department', 'semester')
    search_fields = ('code', 'name')


# =========================================
# 4. PROFILES (Student & Teacher Details)
# =========================================

@admin.register(StudentProfile)
class StudentProfileAdmin(admin.ModelAdmin):
    list_display = ('roll_no', 'get_student_name', 'batch', 'current_semester')
    search_fields = ('roll_no', 'user__username', 'user__first_name')
    list_filter = ('batch', 'current_semester')
    autocomplete_fields = ['user']  # Requires User search to be enabled

    def get_student_name(self, obj):
        return obj.user.get_full_name()
    get_student_name.short_description = "Student Name"

@admin.register(TeacherProfile)
class TeacherProfileAdmin(admin.ModelAdmin):
    list_display = ('employee_id', 'get_teacher_name', 'department')
    search_fields = ('employee_id', 'user__username')
    list_filter = ('department',)

    def get_teacher_name(self, obj):
        return obj.user.get_full_name()
    get_teacher_name.short_description = "Teacher Name"


# =========================================
# 5. OPERATIONS (Timetable & Attendance)
# =========================================

@admin.register(TimeTable)
class TimeTableAdmin(admin.ModelAdmin):
    list_display = ('day_of_week', 'start_time', 'course', 'classroom')
    list_filter = ('day_of_week', 'classroom', 'course__department')

@admin.register(Lecture)
class LectureAdmin(admin.ModelAdmin):
    list_display = ('course', 'teacher', 'classroom', 'start_time', 'is_active')
    list_filter = ('is_active', 'start_time', 'course')
    actions = ['mark_lecture_active', 'mark_lecture_inactive']

    @admin.action(description='🟢 Start Selected Lectures')
    def mark_lecture_active(self, request, queryset):
        queryset.update(is_active=True)

    @admin.action(description='🔴 End Selected Lectures')
    def mark_lecture_inactive(self, request, queryset):
        queryset.update(is_active=False)

@admin.register(Attendance)
class AttendanceAdmin(admin.ModelAdmin):
    list_display = ('student', 'get_course', 'status', 'timestamp')
    list_filter = ('status', 'timestamp', 'lecture__course')
    search_fields = ('student__user__username', 'lecture__course__code')
    readonly_fields = ('timestamp',) # Prevent tampering with timestamps

    def get_course(self, obj):
        return obj.lecture.course.code
    get_course.short_description = "Course"