from django.db import models
from django.contrib.auth.models import AbstractUser
from django.utils import timezone
import uuid

# ==========================================
# 1. CORE AUTHENTICATION (The User)
# ==========================================
class User(AbstractUser):
    """
    Custom User model supporting Admin, Teachers, and Students.
    """
    class Role(models.TextChoices):
        ADMIN = "ADMIN", "Admin"
        TEACHER = "TEACHER", "Teacher"
        STUDENT = "STUDENT", "Student"

    role = models.CharField(max_length=10, choices=Role.choices, default=Role.STUDENT)
    phone_number = models.CharField(max_length=15, blank=True, null=True)
    
    # Security: Locks the account to a specific phone (Anti-Proxy)
    device_fingerprint = models.CharField(
        max_length=255,
        null=True,
        blank=True,
        help_text="Unique Android ID of the student's registered device."
    )

# ==========================================
# 2. ACADEMIC STRUCTURE
# ==========================================
class Department(models.Model):
    name = models.CharField(max_length=100)
    code = models.CharField(max_length=10, unique=True)

    def __str__(self):
        return self.name

class Batch(models.Model):
    year = models.IntegerField()
    department = models.ForeignKey(Department, on_delete=models.CASCADE)

    def __str__(self):
        return f"{self.department.code} - {self.year}"

class Semester(models.Model):
    number = models.IntegerField()
    is_active = models.BooleanField(default=False)

    def __str__(self):
        return f"Semester {self.number}"

class Classroom(models.Model):
    room_number = models.CharField(max_length=20, unique=True)
    capacity = models.IntegerField(default=60)
    # IoT Link: The ESP32 Device ID installed in this room
    esp_device_id = models.CharField(max_length=50, unique=True, null=True, blank=True)

    def __str__(self):
        return f"Room {self.room_number}"

class Course(models.Model):
    name = models.CharField(max_length=100)
    code = models.CharField(max_length=20, unique=True)
    department = models.ForeignKey(Department, on_delete=models.CASCADE)
    semester = models.ForeignKey(Semester, on_delete=models.CASCADE)

    def __str__(self):
        return f"{self.code} - {self.name}"

# ==========================================
# 3. PROFILES
# ==========================================
class StudentProfile(models.Model):
    user = models.OneToOneField(User, on_delete=models.CASCADE, related_name='student_profile')
    roll_no = models.CharField(max_length=20, unique=True)
    department = models.ForeignKey(Department, on_delete=models.CASCADE)
    batch = models.ForeignKey(Batch, on_delete=models.CASCADE)
    current_semester = models.ForeignKey(Semester, on_delete=models.SET_NULL, null=True)
    division = models.CharField(max_length=5, default="A")

    def __str__(self):
        return f"{self.roll_no} ({self.user.username})"

class TeacherProfile(models.Model):
    user = models.OneToOneField(User, on_delete=models.CASCADE, related_name='teacher_profile')
    department = models.ForeignKey(Department, on_delete=models.SET_NULL, null=True)
    employee_id = models.CharField(max_length=20, unique=True)

    def __str__(self):
        return self.user.get_full_name()

# ==========================================
# 4. SCHEDULING
# ==========================================
class TimeTable(models.Model):
    DAYS_OF_WEEK = [
        (0, 'Monday'), (1, 'Tuesday'), (2, 'Wednesday'),
        (3, 'Thursday'), (4, 'Friday'), (5, 'Saturday'), (6, 'Sunday')
    ]
    
    day_of_week = models.IntegerField(choices=DAYS_OF_WEEK)
    start_time = models.TimeField()
    end_time = models.TimeField()
    
    course = models.ForeignKey(Course, on_delete=models.CASCADE)
    classroom = models.ForeignKey(Classroom, on_delete=models.CASCADE)
    teacher = models.ForeignKey(User, on_delete=models.CASCADE)
    division = models.CharField(max_length=5, default="A")

    class Meta:
        unique_together = ('day_of_week', 'start_time', 'classroom')

    def __str__(self):
        return f"{self.get_day_of_week_display()} {self.start_time} - {self.course.code}"

# ==========================================
# 5. OPERATIONS (IoT Engine)
# ==========================================
class Lecture(models.Model):
    course = models.ForeignKey(Course, on_delete=models.CASCADE)
    classroom = models.ForeignKey(Classroom, on_delete=models.CASCADE)
    teacher = models.ForeignKey(User, on_delete=models.CASCADE)
    
    start_time = models.DateTimeField(auto_now_add=True)
    end_time = models.DateTimeField(null=True, blank=True)
    is_active = models.BooleanField(default=True)
    session_token = models.CharField(max_length=100, default=uuid.uuid4, unique=True)

    def __str__(self):
        return f"{self.course.code} ({self.start_time.date()})"

class Attendance(models.Model):
    STATUS_CHOICES = [
        ('PRESENT', 'Present'), ('ABSENT', 'Absent'),
        ('LATE', 'Late'), ('EXCUSED', 'On Duty/Medical')
    ]
    
    # ⚠️ LINKED TO USER, NOT PROFILE (Simplifies Login Logic)
    student = models.ForeignKey(User, on_delete=models.CASCADE)
    lecture = models.ForeignKey(Lecture, on_delete=models.CASCADE, related_name='attendance_records')
    timestamp = models.DateTimeField(auto_now_add=True)
    status = models.CharField(max_length=10, choices=STATUS_CHOICES, default='PRESENT')
    
    device_id = models.CharField(max_length=50, null=True, blank=True)
    is_manual_override = models.BooleanField(default=False)

    class Meta:
        unique_together = ('student', 'lecture')

    def __str__(self):
        return f"{self.student.username} - {self.status}"