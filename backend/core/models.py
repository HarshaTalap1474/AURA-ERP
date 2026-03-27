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
        SUPER_ADMIN = "SUPER_ADMIN", "Super Admin"
        ACADEMIC_COORDINATOR = "ACADEMIC_COORDINATOR", "Academic Coordinator"
        HOD = "HOD", "Head of Department"
        TEACHER = "TEACHER", "Teacher"
        TEACHER_GUARDIAN = "TEACHER_GUARDIAN", "Teacher Guardian"
        SECURITY_OFFICER = "SECURITY_OFFICER", "Security Officer"
        LIBRARIAN = "LIBRARIAN", "Librarian"
        FINANCE_CLERK = "FINANCE_CLERK", "Finance Clerk"
        STUDENT = "STUDENT", "Student"
        PARENT = "PARENT", "Parent"
        ADMIN = "ADMIN", "Admin" # Legacy Fallback

    role = models.CharField(max_length=50, choices=Role.choices, default=Role.STUDENT)
    phone_number = models.CharField(max_length=15, blank=True, null=True)
    fcm_device_token = models.CharField(max_length=255, blank=True, null=True, help_text="Firebase Cloud Messaging Device Token for Android Push Notifications")
    
    # Security: Locks the account to a specific phone (Anti-Proxy)
    device_fingerprint = models.CharField(
        max_length=255,
        null=True,
        blank=True,
        help_text="Unique Android ID of the student's registered device."
    )

    # ==========================
    # ERP PROFILE DETAILS
    # ==========================
    BLOOD_GROUPS = (
        ('A+', 'A+'), ('A-', 'A-'), ('B+', 'B+'), ('B-', 'B-'),
        ('O+', 'O+'), ('O-', 'O-'), ('AB+', 'AB+'), ('AB-', 'AB-'),
    )
    
    dob = models.DateField(null=True, blank=True, verbose_name="Date of Birth")
    blood_group = models.CharField(max_length=5, choices=BLOOD_GROUPS, null=True, blank=True)
    address = models.TextField(null=True, blank=True)
    emergency_contact = models.CharField(max_length=15, null=True, blank=True, help_text="Emergency contact number")


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
    credits = models.IntegerField(default=3)

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
    # Pastoral Linkage constraint
    teacher_guardian = models.ForeignKey(User, on_delete=models.SET_NULL, null=True, blank=True, related_name='tg_cohort')
    
    @property
    def cgpa(self):
        records = self.grades.filter(is_absent=False)
        if not records.exists():
            return 0.0
        
        total_points = sum(r.grade_point * r.course.credits for r in records if r.course.credits)
        total_credits = sum(r.course.credits for r in records if r.course.credits)
        return round(float(total_points / total_credits), 2) if total_credits > 0 else 0.0

    def __str__(self):
        return f"{self.roll_no} ({self.user.username})"

class StaffProfile(models.Model):
    user = models.OneToOneField(User, on_delete=models.CASCADE, related_name='staff_profile')
    department = models.ForeignKey(Department, on_delete=models.SET_NULL, null=True, blank=True)
    employee_id = models.CharField(max_length=20, unique=True)

    def __str__(self):
        return self.user.get_full_name()

class ParentProfile(models.Model):
    user = models.OneToOneField(User, on_delete=models.CASCADE, related_name='parent_profile')
    students = models.ManyToManyField(StudentProfile, related_name='parents')
    
    def __str__(self):
        return f"Parent: {self.user.get_full_name()}"

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


# ==========================================
# 6. LEAVE MANAGEMENT
# ==========================================
class LeaveRequest(models.Model):
    LEAVE_TYPES = (
        ('MEDICAL', 'Medical Leave'),
        ('EVENT', 'College Event'),
        ('PERSONAL', 'Personal Reason'),
    )
    STATUS_CHOICES = (
        ('DRAFT', 'Draft'),
        ('PENDING_TG', 'Pending TG Approval'),
        ('APPROVED', 'Approved'),
        ('REJECTED', 'Rejected'),
    )
    
    student = models.ForeignKey(User, on_delete=models.CASCADE, limit_choices_to={'role': 'STUDENT'}, related_name='leave_requests')
    leave_type = models.CharField(max_length=20, choices=LEAVE_TYPES)
    start_date = models.DateField()
    end_date = models.DateField()
    reason = models.TextField()
    document = models.FileField(upload_to='leave_docs/', null=True, blank=True)
    status = models.CharField(max_length=20, choices=STATUS_CHOICES, default='PENDING_TG')
    applied_on = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return f"{self.student.username} | {self.leave_type} | {self.status}"

# ==========================================
# 7. ASSESSMENT & GRADING ENGINE
# ==========================================
class Exam(models.Model):
    EXAM_TYPES = [
        ('MID', 'Mid-Term'),
        ('END', 'End-Semester'),
        ('PRAC', 'Practical'),
        ('UNIT', 'Unit Test')
    ]
    name = models.CharField(max_length=100)
    exam_type = models.CharField(max_length=20, choices=EXAM_TYPES)
    date = models.DateField()
    semester = models.ForeignKey(Semester, on_delete=models.CASCADE)
    max_marks = models.PositiveIntegerField(default=100)
    passing_marks = models.PositiveIntegerField(default=40)
    
    def __str__(self):
        return f"{self.name} ({self.get_exam_type_display()})"

class GradeRecord(models.Model):
    student = models.ForeignKey(StudentProfile, on_delete=models.CASCADE, related_name='grades')
    exam = models.ForeignKey(Exam, on_delete=models.CASCADE, related_name='results')
    course = models.ForeignKey(Course, on_delete=models.CASCADE)
    marks_obtained = models.DecimalField(max_digits=5, decimal_places=2, null=True, blank=True)
    is_absent = models.BooleanField(default=False)
    
    @property
    def passed(self):
        if self.is_absent or self.marks_obtained is None:
            return False
        return self.marks_obtained >= self.exam.passing_marks
        
    @property
    def grade_point(self):
        if self.is_absent or self.marks_obtained is None:
            return 0
        percentage = (self.marks_obtained / self.exam.max_marks) * 100
        if percentage >= 90: return 10
        elif percentage >= 80: return 9
        elif percentage >= 70: return 8
        elif percentage >= 60: return 7
        elif percentage >= 50: return 6
        elif percentage >= 40: return 5
        elif percentage >= 35: return 4
        return 0

    class Meta:
        unique_together = ('student', 'exam', 'course')
        
    def __str__(self):
        return f"{self.student.roll_no} - {self.course.code}: {self.marks_obtained}/{self.exam.max_marks}"

# ==========================================
# 8. AUTOMATED WORKFLOWS & GATE PASS
# ==========================================
class GatePass(models.Model):
    leave_request = models.OneToOneField(LeaveRequest, on_delete=models.CASCADE, related_name='gate_pass')
    qr_token = models.UUIDField(default=uuid.uuid4, editable=False, unique=True)
    generated_at = models.DateTimeField(auto_now_add=True)
    scanned_at = models.DateTimeField(null=True, blank=True)
    scanned_by = models.ForeignKey(User, on_delete=models.SET_NULL, null=True, blank=True, related_name='scanned_passes')
    is_active = models.BooleanField(default=True)
    
    def __str__(self):
        return f"Pass for {self.leave_request.student.username} (Active: {self.is_active})"

# ==========================================
# 9. FINANCE & INVENTORY ENGINE
# ==========================================
class FeeInvoice(models.Model):
    FEE_TYPES = (
        ('TUITION', 'Tuition Fee'),
        ('HOSTEL', 'Hostel Fee'),
        ('TRANSPORT', 'Transport Fee'),
        ('EXAM', 'Examination Fee'),
        ('PENALTY', 'Late/Disciplinary Penalty')
    )
    student = models.ForeignKey(StudentProfile, on_delete=models.CASCADE, related_name='invoices')
    fee_type = models.CharField(max_length=20, choices=FEE_TYPES)
    amount = models.DecimalField(max_digits=10, decimal_places=2)
    due_date = models.DateField()
    is_paid = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)
    
    def __str__(self):
        return f"{self.fee_type} - {self.student.roll_no} - ₹{self.amount}"

class PaymentTransaction(models.Model):
    invoice = models.ForeignKey(FeeInvoice, on_delete=models.CASCADE, related_name='transactions')
    transaction_id = models.CharField(max_length=100, unique=True, help_text="e.g. Razorpay/Stripe Payment ID")
    amount_paid = models.DecimalField(max_digits=10, decimal_places=2)
    payment_method = models.CharField(max_length=50, default="RAZORPAY")
    timestamp = models.DateTimeField(auto_now_add=True)
    is_successful = models.BooleanField(default=False)
    
    def __str__(self):
        return f"TXN {self.transaction_id} - Success: {self.is_successful}"

class LibraryAction(models.Model):
    student = models.ForeignKey(StudentProfile, on_delete=models.CASCADE, related_name='library_books')
    book_uid = models.CharField(max_length=50, help_text="Physical RFID tag or Barcode")
    book_title = models.CharField(max_length=200)
    issued_on = models.DateTimeField(auto_now_add=True)
    due_date = models.DateField()
    returned_on = models.DateTimeField(null=True, blank=True)
    fine_accrued = models.DecimalField(max_digits=6, decimal_places=2, default=0.00)
    
    @property
    def is_overdue(self):
        if not self.returned_on:
            from django.utils import timezone
            return timezone.now().date() > self.due_date
        return False
        
    def __str__(self):
        return f"{self.book_title} -> {self.student.roll_no}"

# ==========================================
# 10. NOTIFICATION HUB & FCM
# ==========================================
class NotificationInbox(models.Model):
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='notifications')
    title = models.CharField(max_length=200)
    message = models.TextField()
    is_read = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)
    fcm_dispatched = models.BooleanField(default=False)
    
    def __str__(self):
        return f"{self.user.username} - {self.title[:30]}"