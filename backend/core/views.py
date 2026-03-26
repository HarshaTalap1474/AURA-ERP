# =========================================
# STANDARD LIBRARY IMPORTS
# =========================================
import io
import csv
import calendar
from datetime import datetime, timedelta

# =========================================
# DJANGO IMPORTS
# =========================================
from django.shortcuts import render, redirect, get_object_or_404
from django.contrib.auth.decorators import login_required
from django.http import HttpResponse, JsonResponse
import json
import csv
import io
from django.db.models import Count, Q
from django.utils import timezone
from django.contrib.auth import authenticate, update_session_auth_hash, logout
from django.contrib.auth.views import PasswordChangeView
from django.contrib import messages
from django.urls import reverse_lazy
from django.db import transaction # ✅ ADDED for Database Integrity
from django.core.signing import TimestampSigner, SignatureExpired, BadSignature # ✅ FIXED Missing Imports
from django.core.mail import send_mail # ✅ ADDED for Email Warnings

# =========================================
# REST FRAMEWORK IMPORTS
# =========================================
from rest_framework.decorators import api_view, permission_classes
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.response import Response
from rest_framework_simplejwt.tokens import RefreshToken

# =========================================
# LOCAL MODEL IMPORTS
# =========================================
from .models import (
    User, StudentProfile, StaffProfile, ParentProfile, Department, Batch, Semester,
    Course, Classroom, Lecture, Attendance, TimeTable, LeaveRequest
)

# =========================================
# 1. AUTHENTICATION & ROUTING (Web Portal)
# =========================================
def custom_login(request):
    if request.user.is_authenticated:
        return dashboard_redirect(request)
    return redirect('/admin/')

def custom_logout(request):
    logout(request) 
    return redirect('login') 

@login_required
def dashboard_redirect(request):
    role_map = {
        User.Role.SUPER_ADMIN: 'super_admin_dashboard',
        User.Role.ACADEMIC_COORDINATOR: 'academic_coordinator_dashboard',
        User.Role.HOD: 'hod_dashboard',
        User.Role.TEACHER: 'teacher_dashboard',
        User.Role.TEACHER_GUARDIAN: 'tg_dashboard',
        User.Role.SECURITY_OFFICER: 'security_dashboard',
        User.Role.LIBRARIAN: 'librarian_dashboard',
        User.Role.FINANCE_CLERK: 'finance_dashboard',
        User.Role.STUDENT: 'student_dashboard',
        User.Role.PARENT: 'parent_dashboard',
        User.Role.ADMIN: 'manage_students', # Legacy fallback
    }
    return redirect(role_map.get(request.user.role, 'login'))

# =========================================
# 2. TEACHER DASHBOARD (Web Portal)
# =========================================
@login_required
def teacher_dashboard(request):
    if request.user.role not in [User.Role.TEACHER, User.Role.ACADEMIC_COORDINATOR, User.Role.HOD, User.Role.TEACHER_GUARDIAN, User.Role.ADMIN, User.Role.SUPER_ADMIN]:
        return redirect('student_dashboard')
    
    now = timezone.localtime(timezone.now())
    today_weekday = now.weekday()
    today_date = now.date()

    todays_timetable = TimeTable.objects.filter(
        teacher=request.user, day_of_week=today_weekday
    ).order_by('start_time')
    
    students_present_count = Attendance.objects.filter(
        lecture__teacher=request.user, timestamp__date=today_date, status='PRESENT'
    ).values('student').distinct().count()

    active_lecture = Lecture.objects.filter(teacher=request.user, is_active=True).first()

    todays_lectures = []
    for slot in todays_timetable:
        finished = Lecture.objects.filter(
            teacher=request.user, course=slot.course, 
            classroom=slot.classroom, start_time__date=today_date, is_active=False
        ).exists()

        status_flag = "UPCOMING"
        if active_lecture and active_lecture.course == slot.course and active_lecture.classroom == slot.classroom:
            status_flag = "ACTIVE"
        elif finished:
            status_flag = "FINISHED"

        time_str = f"{slot.start_time.strftime('%I:%M %p')} - {slot.end_time.strftime('%I:%M %p')}"
        todays_lectures.append({
            'id': slot.id,
            'time': time_str,
            'subject': {'name': slot.course.name},
            'batch': f"{slot.course.code}", 
            'room': slot.classroom.room_number if slot.classroom else "TBD",
            'status_flag': status_flag
        })

    # Analytics for Chart.js
    attendance_stats = Attendance.objects.filter(lecture__teacher=request.user).values('status').annotate(count=Count('id'))
    chart_data_raw = {'PRESENT': 0, 'ABSENT': 0, 'EXCUSED': 0, 'LATE': 0}
    for item in attendance_stats:
        chart_data_raw[item['status']] = item['count']
    
    total_expected = 0
    for lec in Lecture.objects.filter(teacher=request.user, is_active=False):
        total_expected += StudentProfile.objects.filter(department=lec.course.department, current_semester=lec.course.semester).count()
    
    real_absent = total_expected - sum(chart_data_raw.values())
    chart_data_raw['ABSENT'] += max(0, real_absent)

    chart_labels = ['Present', 'Absent', 'Excused', 'Late']
    chart_values = [chart_data_raw['PRESENT'], chart_data_raw['ABSENT'], chart_data_raw['EXCUSED'], chart_data_raw['LATE']]

    context = {
        'stats': {
            'today_lectures': todays_timetable.count(),
            'students_present': students_present_count,
            'pending_leaves': LeaveRequest.objects.filter(status='PENDING').count() 
        },
        'todays_lectures': todays_lectures,
        'username': request.user.username,
        'chart_labels': json.dumps(chart_labels),
        'chart_values': json.dumps(chart_values),
        'has_active_lecture': bool(active_lecture),
        'teacher_courses': Course.objects.filter(department=request.user.staff_profile.department) if hasattr(request.user, 'staff_profile') else Course.objects.none(),
        'classrooms': Classroom.objects.all(),
    }
    return render(request, 'teacher_dashboard.html', context)

@login_required
def export_attendance_csv(request):
    if request.user.role != User.Role.TEACHER:
        return redirect('dashboard')
    
    response = HttpResponse(content_type='text/csv')
    response['Content-Disposition'] = 'attachment; filename="my_attendance_report.csv"'
    writer = csv.writer(response)
    
    writer.writerow(['Date', 'Time', 'Subject', 'Student Roll No', 'Student Name', 'Status'])
    records = Attendance.objects.filter(lecture__teacher=request.user).select_related('student', 'lecture__course').order_by('-timestamp')
    
    for rec in records:
        writer.writerow([
            rec.timestamp.strftime('%Y-%m-%d'),
            rec.timestamp.strftime('%I:%M %p'),
            rec.lecture.course.name,
            rec.student.username,
            rec.student.get_full_name(),
            rec.status
        ])
    return response

@login_required
def send_defaulter_warnings(request):
    if request.user.role != User.Role.TEACHER:
        return redirect('dashboard')

    try:
        dept = request.user.staff_profile.department
    except StaffProfile.DoesNotExist:
        messages.error(request, "Staff profile not configured.")
        return redirect('teacher_dashboard')

    students = StudentProfile.objects.filter(department=dept).select_related('user')
    defaulters = []

    for sp in students:
        total_lec = Lecture.objects.filter(course__department=dept, course__semester=sp.current_semester, is_active=False).count()
        if total_lec == 0: continue
        attended = Attendance.objects.filter(student=sp.user, status__in=['PRESENT', 'EXCUSED']).count()
        pct = (attended / total_lec) * 100
        if pct < 75.0 and sp.user.email:
            defaulters.append(sp.user.email)

    if defaulters:
        send_mail(
            subject="URGENT: Attendance Shortage Warning | AURA",
            message="Dear Student,\n\nYour attendance has fallen below the mandatory 75% threshold. Please meet your department mentor immediately to resolve this discrepancy as it may affect your examination eligibility.\n\nRegards,\nDepartment of " + dept.name + "\nAURA Academic Automation",
            from_email="warnings@auraerp.edu",
            recipient_list=defaulters,
            fail_silently=True,
        )
        messages.success(request, f"Warning emails silently dispatched to {len(defaulters)} defaulting students.")
    else:
        messages.info(request, "No students are below the 75% threshold in your department right now.")

    return redirect('teacher_dashboard')

# =========================================
# 3. STUDENT DASHBOARD (Web Portal)
# =========================================
@login_required
def student_dashboard(request):
    if request.user.role != User.Role.STUDENT:
        return redirect('teacher_dashboard')

    attendance_history = Attendance.objects.filter(student=request.user).order_by('-timestamp')
    profile = getattr(request.user, 'student_profile', None)
    
    attendance_percentage = 0
    if profile:
        total_conducted = Lecture.objects.filter(
            course__semester=profile.current_semester,
            is_active=False
        ).count()

        total_attended = Attendance.objects.filter(
            student=request.user,
            lecture__course__semester=profile.current_semester,
            status='PRESENT'
        ).count()

        if total_conducted > 0:
            attendance_percentage = round((total_attended / total_conducted) * 100, 1)

    context = {
        'history': attendance_history,
        'username': request.user.username,
        'attendance_percentage': attendance_percentage,
    }
    return render(request, 'student_dashboard.html', context)

# =========================================
# 4. REGISTRAR MODULE (Web Portal)
# =========================================
@login_required
def manage_students(request):
    if request.user.role == User.Role.STUDENT:
        return redirect('student_dashboard')

    query = request.GET.get('q')
    students = StudentProfile.objects.select_related('user', 'department', 'batch', 'current_semester').order_by('roll_no')

    if query:
        students = students.filter(
            Q(roll_no__icontains=query) | 
            Q(user__first_name__icontains=query) | 
            Q(user__last_name__icontains=query)
        )
    return render(request, 'manage_students.html', {'students': students, 'search_query': query, 'username': request.user.username})

# =========================================
# 5. BULK UPLOAD LOGIC (Optimized)
# =========================================
@login_required
def bulk_upload_students(request):
    if request.user.role == User.Role.STUDENT:
        return redirect('student_dashboard')

    if request.method == "POST":
        try:
            csv_file = request.FILES['file']
            if not csv_file.name.endswith('.csv'):
                return HttpResponse("Error: Please upload a .csv file.")

            # ✅ Memory Optimization: Read text wrapper, not entire file into RAM
            file_wrapper = io.TextIOWrapper(csv_file.file, encoding='utf-8')
            csv_reader = csv.reader(file_wrapper, delimiter=',', quotechar="|")
            next(csv_reader, None) # Skip header
            
            errors = []
            users_to_create = []
            profiles_to_create = []

            # Pre-fetch for faster validation
            departments = {d.code: d for d in Department.objects.all()}
            active_semester = Semester.objects.filter(is_active=True).first()

            with transaction.atomic():
                for index, row in enumerate(csv_reader):
                    if not row or len(row) < 6: continue
                    roll_no, first_name, last_name, dept_code, batch_year_str, division = [r.strip() for r in row[:6]]

                    dept = departments.get(dept_code)
                    if not dept:
                        errors.append(f"Row {index+2}: Dept '{dept_code}' not found.")
                        continue

                    # Fallback validation
                    if not User.objects.filter(username=roll_no).exists():
                        # Still looping user creation for password hashing, but inside atomic transaction
                        user = User.objects.create_user(username=roll_no, password=roll_no, first_name=first_name, last_name=last_name, role=User.Role.STUDENT)
                        batch, _ = Batch.objects.get_or_create(year=int(batch_year_str), department=dept)
                        
                        profiles_to_create.append(StudentProfile(
                            user=user, roll_no=roll_no, department=dept, batch=batch, 
                            division=division, current_semester=active_semester
                        ))
                
                # Bulk create profiles
                if profiles_to_create:
                    StudentProfile.objects.bulk_create(profiles_to_create)

            if errors: print(errors)
            return redirect('manage_students')
        except Exception as e:
            return HttpResponse(f"Critical Upload Error: {str(e)}")

    return render(request, 'upload_students.html')

# =========================================
# 6. TIMETABLE (Web Portal)
# =========================================
@login_required
def manage_timetable(request):
    if request.user.role == User.Role.STUDENT:
        return redirect('student_dashboard')
    days = {0: 'Monday', 1: 'Tuesday', 2: 'Wednesday', 3: 'Thursday', 4: 'Friday', 5: 'Saturday'}
    timetable = TimeTable.objects.select_related('course', 'classroom', 'teacher').order_by('day_of_week', 'start_time')
    return render(request, 'manage_timetable.html', {'timetable': timetable, 'days': days})

@login_required
def add_schedule(request):
    if request.user.role == User.Role.STUDENT:
        return redirect('student_dashboard')
    if request.method == "POST":
        TimeTable.objects.create(
            day_of_week=request.POST.get('day'),
            start_time=request.POST.get('start_time'),
            end_time=request.POST.get('end_time'),
            course_id=request.POST.get('course_id'),
            classroom_id=request.POST.get('room_id'),
            teacher=request.user
        )
        return redirect('manage_timetable')
    
    return render(request, 'add_schedule.html', {'courses': Course.objects.all(), 'rooms': Classroom.objects.all()})

# =========================================
# 7. IOT HARDWARE API (Production Optimized)
# =========================================
@api_view(['POST'])
@permission_classes([AllowAny])
def hardware_sync(request):
    data = request.data
    gateway_id = data.get('gateway_id')
    detected_students = data.get('detected_students', [])

    if not gateway_id:
        return Response({"status": "error", "message": "Missing gateway_id"}, status=400)
    
    # ✅ FIX: Do not process empty classes (Stops Infinite DB Growth)
    if not detected_students:
        return Response({"status": "ignored", "message": "No students detected."}, status=200)

    try:
        classroom = Classroom.objects.get(esp_device_id__iexact=gateway_id)
    except Classroom.DoesNotExist:
        return Response({"status": "error", "message": "Gateway not registered"}, status=404)

    now = timezone.localtime(timezone.now())

    # ✅ FIX: Atomic Transaction to prevent Race Conditions & DB Lockups
    with transaction.atomic():
        active_lecture = Lecture.objects.filter(classroom=classroom, is_active=True).first()

        if not active_lecture:
            return Response({"status": "ignored", "message": "No active class found by teacher."}, status=200)

        # ✅ FIX: The O(N) Loop Server Meltdown is gone. 
        # Fetch all matching users in 1 query.
        students = User.objects.filter(Q(username__in=detected_students) | Q(device_fingerprint__in=detected_students))

        attendance_records = []
        for student in students:
            attendance_records.append(Attendance(
                student=student, 
                lecture=active_lecture, 
                status='PRESENT', 
                device_id=gateway_id
            ))

        # Bulk Insert in 1 query. Ignores if they already exist.
        if attendance_records:
            Attendance.objects.bulk_create(attendance_records, ignore_conflicts=True)

    return Response({
        "status": "success", "room": classroom.room_number,
        "class": active_lecture.course.code, "marked_new": len(attendance_records)
    })

# =========================================
# 8. ANDROID VIRTUAL ID (Repurposed from mark_attendance)
# =========================================
@api_view(['POST'])
@permission_classes([AllowAny]) 
def verify_virtual_id(request): # ✅ RENAMED: Update URL logic from 'mark_attendance' to 'verify_virtual_id'
    """
    Called by Security/Gate Scanner to verify a student's identity. 
    Does NOT mark classroom attendance.
    """
    scanned_qr_token = request.data.get('student_id') 

    if not scanned_qr_token:
        return Response({"status": "error", "message": "Missing scan data"}, status=400)

    signer = TimestampSigner()
    try:
        # Decrypt token (Expires in 15 seconds)
        student_username = signer.unsign(scanned_qr_token, max_age=15)
        
    except SignatureExpired:
        return Response({"status": "error", "message": "QR Code Expired! Please scan the live screen."}, status=403)
    except BadSignature:
        return Response({"status": "error", "message": "Invalid or Tampered QR Code!"}, status=403)

    try:
        user = User.objects.get(username=student_username)
        # ✅ Returns Identity data for Security, does not write to Attendance table
        return Response({
            "status": "success", 
            "message": "Identity Verified",
            "student": {
                "name": user.get_full_name(),
                "roll_no": user.username,
                "blood_group": user.blood_group
            }
        })
    except User.DoesNotExist:
        return Response({"status": "error", "message": "Student not found."}, status=404)

# =========================================
# 9. AUTHENTICATION & PROFILE API
# =========================================
@api_view(['POST'])
@permission_classes([AllowAny])
def app_login(request):
    username, password = request.data.get('username'), request.data.get('password')
    incoming_fingerprint = request.data.get('device_fingerprint') or request.data.get('device_id')
    
    if incoming_fingerprint: incoming_fingerprint = incoming_fingerprint.strip()
    if not username or not password: return Response({"status": "error", "message": "Credentials missing"}, status=400)

    user = authenticate(username=username, password=password)
    if user is not None:
        if not user.is_active: return Response({"status": "error", "message": "Account disabled"}, status=403)

        if incoming_fingerprint:
            if user.device_fingerprint is None:
                user.device_fingerprint = incoming_fingerprint
                user.save()
            elif user.device_fingerprint != incoming_fingerprint:
                return Response({"status": "error", "message": "Device bound to another phone."}, status=403)

        refresh = RefreshToken.for_user(user)
        return Response({
            "status": "success", "username": user.username, "role": user.role,
            "name": f"{user.first_name} {user.last_name}", "email": user.email or "",
            "phone_number": user.phone_number, "user_id": user.id,
            "device_fingerprint": user.device_fingerprint,
            "access_token": str(refresh.access_token), "refresh_token": str(refresh)
        })
    return Response({"status": "error", "message": "Invalid credentials"}, status=401)

@api_view(['GET', 'POST'])
@permission_classes([IsAuthenticated])
def update_profile(request):
    user = request.user

    if request.method == 'GET':
        return Response({
            "status": "success",
            "user": {
                "username": user.username, "first_name": user.first_name, "last_name": user.last_name,
                "email": user.email, "phone_number": user.phone_number, "blood_group": user.blood_group,
                "address": user.address, "emergency_contact": user.emergency_contact, "dob": user.dob, "role": user.role
            }
        })

    if request.method == 'POST':
        data = request.data
        if 'first_name' in data: user.first_name = data['first_name']
        if 'last_name' in data: user.last_name = data['last_name']
        if 'email' in data: user.email = data['email']
        if 'phone_number' in data and hasattr(user, 'phone_number'): user.phone_number = data['phone_number']
        if 'blood_group' in data: user.blood_group = data['blood_group']
        if 'address' in data: user.address = data['address']
        if 'emergency_contact' in data: user.emergency_contact = data['emergency_contact']
        if 'dob' in data and data['dob']: user.dob = data['dob']
        user.save()

        return Response({"status": "success", "message": "Profile updated"})

@api_view(['POST'])
@permission_classes([IsAuthenticated])
def change_password(request):
    user, old_password, new_password = request.user, request.data.get('old_password'), request.data.get('new_password')
    if not old_password or not new_password: return Response({"status": "error", "message": "Both required."}, status=400)
    if not user.check_password(old_password): return Response({"status": "error", "message": "Wrong old password."}, status=400)
    
    user.set_password(new_password)
    user.save()
    update_session_auth_hash(request, user)
    return Response({"status": "success", "message": "Password changed successfully"})

# =========================================
# 10. ATTENDANCE REPORT CARD
# =========================================
@api_view(['GET'])
@permission_classes([IsAuthenticated])
def attendance_history(request):
    try:
        profile = request.user.student_profile
    except StudentProfile.DoesNotExist:
        return Response({"status": "error", "message": "Student profile not found"}, status=404)

    # ✅ FIX: Annotate for better performance
    courses = Course.objects.filter(department=profile.department, semester=profile.current_semester).annotate(
        total_lectures=Count('lecture', filter=Q(lecture__is_active=False)),
        present_count=Count('lecture__attendance_records', filter=Q(lecture__attendance_records__student=request.user, lecture__attendance_records__status='PRESENT'))
    )
    
    history_data, overall_present, overall_total_lectures = [], 0, 0

    for course in courses:
        pct = (course.present_count / course.total_lectures * 100) if course.total_lectures > 0 else 0.0
        teacher_name = "Not Allocated"
        if (tt := TimeTable.objects.filter(course=course).first()): teacher_name = tt.teacher.get_full_name()
        
        history_data.append({
            "subject_name": course.name, "subject_code": course.code, "teacher_name": teacher_name,
            "present": course.present_count, "total": course.total_lectures, "percentage": round(pct, 1)
        })
        overall_present += course.present_count
        overall_total_lectures += course.total_lectures

    overall_percentage = (overall_present / overall_total_lectures * 100) if overall_total_lectures > 0 else 0.0
    return Response({
        "status": "success", "semester": str(profile.current_semester),
        "overall_percentage": round(overall_percentage, 1), "overall_present": overall_present,
        "overall_total": overall_total_lectures, "history": history_data
    })

# =========================================
# 11. WEB PORTAL PROFILES & ANALYTICS
# =========================================
@login_required
def profile(request):
    user = request.user
    if request.method == "POST":
        user.first_name = request.POST.get('first_name', user.first_name)
        user.last_name = request.POST.get('last_name', user.last_name)
        user.email = request.POST.get('email', user.email)
        if hasattr(user, 'phone_number'): user.phone_number = request.POST.get('phone_number', user.phone_number)
        user.blood_group = request.POST.get('blood_group', user.blood_group)
        user.address = request.POST.get('address', user.address)
        user.emergency_contact = request.POST.get('emergency_contact', user.emergency_contact)
        new_dob = request.POST.get('dob')
        if new_dob: user.dob = new_dob
        user.save()
        messages.success(request, "Your profile has been updated successfully!")
        return redirect('profile') 

    context = {'user': user}
    if hasattr(user, 'student_profile'): context['student_profile'] = user.student_profile
    if hasattr(user, 'staff_profile'): context['staff_profile'] = user.staff_profile
    if hasattr(user, 'parent_profile'): context['parent_profile'] = user.parent_profile
        
    return render(request, 'profile.html', context)

def coming_soon(request, module_name="Feature"):
    return render(request, 'coming_soon.html', {'feature_name': module_name.replace('-', ' ').title()})

@login_required
def student_analytics(request):
    if request.user.role != User.Role.STUDENT or not hasattr(request.user, 'student_profile'): 
        return redirect('dashboard')
        
    profile = request.user.student_profile
    user = request.user
    
    # ✅ FIX: O(1) Database Query using Annotate. Prevents server timeout.
    courses = Course.objects.filter(
        department=profile.department,
        semester=profile.current_semester
    ).annotate(
        total_lec=Count('lecture', filter=Q(lecture__is_active=False)),
        attended=Count('lecture__attendance_records', filter=Q(lecture__attendance_records__student=user, lecture__attendance_records__status__in=['PRESENT', 'EXCUSED']))
    )

    subject_data, total_lec_overall, total_pres_overall = [], 0, 0

    for course in courses:
        pct = (course.attended / course.total_lec * 100) if course.total_lec > 0 else 0.0
        subject_data.append({
            'name': course.name, 'code': course.code, 'attended': course.attended,
            'total': course.total_lec, 'percentage': round(pct, 1),
            'status': "Safe" if pct >= 75 else "Critical"
        })
        total_lec_overall += course.total_lec
        total_pres_overall += course.attended

    overall_percentage = (total_pres_overall / total_lec_overall * 100) if total_lec_overall > 0 else 0.0
    svg_offset = 440 - (440 * overall_percentage / 100)

    now = datetime.now()
    today = now.date()
    num_days = calendar.monthrange(now.year, now.month)[1]

    present_days = set(Attendance.objects.filter(student=user, timestamp__year=now.year, timestamp__month=now.month, status__in=['PRESENT', 'EXCUSED']).values_list('timestamp__day', flat=True).distinct())
    calendar_events = []

    for day in range(1, num_days + 1):
        curr_date = datetime(now.year, now.month, day).date()
        if day in present_days: status = 'P'
        elif curr_date.weekday() == 6: status = 'H'
        elif curr_date < today: status = 'A'
        elif curr_date > today: status = 'U'
        else: status = 'N'

        calendar_events.append({'date': str(day), 'day_name': curr_date.strftime('%a'), 'full_date': curr_date.strftime('%Y-%m-%d'), 'status': status})

    return render(request, 'attendance_detailed.html', {
        'overall_percentage': round(overall_percentage, 1), 'total_days_present': total_pres_overall,
        'total_days_working': total_lec_overall, 'svg_offset': svg_offset,
        'subject_analysis': subject_data, 'calendar_events': calendar_events,
        'current_month_name': now.strftime("%B %Y")
    })

# =========================================
# 12. PASSWORD MANAGEMENT
# =========================================
class CustomPasswordChangeView(PasswordChangeView):
    template_name = 'password_change.html'
    success_url = reverse_lazy('dashboard')
    def form_valid(self, form):
        messages.success(self.request, "Security Update: Your password has been changed successfully.")
        return super().form_valid(form)

# =========================================
# 13. SESSION ACTIVATION (Teacher)
# =========================================
@login_required
def start_timetable_class(request, timetable_id):
    if request.user.role != User.Role.TEACHER: return redirect('dashboard')
    if Lecture.objects.filter(teacher=request.user, is_active=True).exists():
        messages.warning(request, "You already have an active Live Session. End it before starting a new one.")
        return redirect('teacher_dashboard')

    tt_slot = get_object_or_404(TimeTable, id=timetable_id, teacher=request.user)
    Lecture.objects.create(course=tt_slot.course, classroom=tt_slot.classroom, teacher=request.user, is_active=True)
    messages.success(request, f"Class Started: {tt_slot.course.name}")
    return redirect('live_monitor')

@login_required
def start_extra_class(request):
    if request.user.role != User.Role.TEACHER or request.method != 'POST': return redirect('dashboard')
    if Lecture.objects.filter(teacher=request.user, is_active=True).exists():
        messages.warning(request, "You already have an active Live Session.")
        return redirect('teacher_dashboard')
        
    course = get_object_or_404(Course, id=request.POST.get('course_id'))
    room = get_object_or_404(Classroom, id=request.POST.get('room_id'))
    Lecture.objects.create(course=course, classroom=room, teacher=request.user, is_active=True)
    
    messages.success(request, f"Extra Class Started: {course.name}")
    return redirect('live_monitor')

@login_required
def end_lecture(request):
    if request.user.role != User.Role.TEACHER or request.method != 'POST': return redirect('dashboard')
    active = Lecture.objects.filter(teacher=request.user, is_active=True).first()
    if active:
        active.is_active = False
        active.end_time = timezone.now()
        active.save()
        messages.success(request, "Live Session Ended Successfully.")
    return redirect('teacher_dashboard')

# =========================================
# 14. LIVE MONITOR & UI APIs (Teacher Override)
# =========================================
@login_required
def live_monitor(request):
    if request.user.role != User.Role.TEACHER: return redirect('dashboard')
    active_lecture = Lecture.objects.filter(teacher=request.user, is_active=True).first()
    
    if not active_lecture:
        messages.warning(request, "You do not have an active live session right now.")
        return redirect('teacher_dashboard')

    expected_students = StudentProfile.objects.filter(department=active_lecture.course.department, current_semester=active_lecture.course.semester).select_related('user').order_by('roll_no')
    present_user_ids = set(Attendance.objects.filter(lecture=active_lecture, status='PRESENT').values_list('student_id', flat=True))

    students_data = [{'name': f"{p.user.first_name} {p.user.last_name}", 'roll_no': p.roll_no, 'is_present': p.user.id in present_user_ids} for p in expected_students]
    return render(request, 'live_monitor.html', {'lecture': active_lecture, 'total_students': expected_students.count(), 'students': students_data})

@api_view(['GET'])
@permission_classes([IsAuthenticated])
def live_lecture_status(request, lecture_id):
    lecture = get_object_or_404(Lecture, id=lecture_id)
    expected_students = StudentProfile.objects.filter(department=lecture.course.department, current_semester=lecture.course.semester).select_related('user').order_by('roll_no')
    present_user_ids = set(Attendance.objects.filter(lecture=lecture, status='PRESENT').values_list('student_id', flat=True))

    students_data, present_count = [], 0
    for p in expected_students:
        is_pres = p.user.id in present_user_ids
        if is_pres: present_count += 1
        students_data.append({"roll_no": p.roll_no, "is_present": is_pres})

    total_expected = expected_students.count()
    return Response({
        "present_count": present_count, "absent_count": total_expected - present_count,
        "attendance_percentage": round((present_count / total_expected * 100) if total_expected > 0 else 0.0, 1),
        "students": students_data
    })

# =========================================
# 14. LEAVE MANAGEMENT
# =========================================
@login_required
def student_leave_view(request):
    if request.user.role != User.Role.STUDENT: return redirect('dashboard')
    if request.method == 'POST':
        LeaveRequest.objects.create(
            student=request.user, leave_type=request.POST.get('leave_type'), start_date=request.POST.get('start_date'),
            end_date=request.POST.get('end_date'), reason=request.POST.get('reason'), document=request.FILES.get('document')
        )
        messages.success(request, "Leave application submitted successfully!")
        return redirect('leave_application')
    return render(request, 'student_leave.html', {'leave_history': LeaveRequest.objects.filter(student=request.user).order_by('-applied_on')})

@login_required
def teacher_leave_view(request):
    if request.user.role != User.Role.TEACHER: return redirect('dashboard')
    pending_requests = LeaveRequest.objects.filter(status='PENDING').order_by('-applied_on')
    return render(request, 'teacher_leave_approval.html', {
        'pending_requests': pending_requests, 'pending_count': pending_requests.count(),
        'processed_requests': LeaveRequest.objects.filter(Q(status='APPROVED') | Q(status='REJECTED')).order_by('-applied_on')[:50]
    })

@login_required
def process_leave_action(request, request_id):
    if request.user.role != User.Role.TEACHER or request.method != 'POST': return redirect('dashboard')
    leave_request = get_object_or_404(LeaveRequest, id=request_id)
    action = request.POST.get('action')
    
    if action == 'approve':
        leave_request.status = 'APPROVED'
        leave_request.save()
        
        # Automatically mark as EXCUSED for all missed lectures in that date range
        student_profile = getattr(leave_request.student, 'student_profile', None)
        if student_profile:
            lectures_missed = Lecture.objects.filter(
                course__department=student_profile.department,
                course__semester=student_profile.current_semester,
                start_time__date__gte=leave_request.start_date,
                start_time__date__lte=leave_request.end_date,
                is_active=False
            )
            excused_records = [
                Attendance(student=leave_request.student, lecture=lec, status='EXCUSED', is_manual_override=True)
                for lec in lectures_missed
            ]
            if excused_records:
                Attendance.objects.bulk_create(excused_records, ignore_conflicts=True)

        messages.success(request, "Leave approved. Excused attendance records have been automatically generated.")
    elif action == 'reject':
        leave_request.status = 'REJECTED'
        leave_request.save()
        messages.warning(request, "Leave request rejected.")
    return redirect('leave_approvals')

# =========================================
# 15. STUDENT QUICK ACTIONS
# =========================================
@login_required
def virtual_id_view(request):
    return render(request, 'virtual_id.html') if request.user.role == User.Role.STUDENT else redirect('dashboard')

@login_required
def device_integrity_view(request):
    return render(request, 'device_integrity.html') if request.user.role == User.Role.STUDENT else redirect('dashboard')

@login_required
def contact_mentor_view(request):
    return render(request, 'contact_mentor.html') if request.user.role == User.Role.STUDENT else redirect('dashboard')

@login_required
def report_lost_device(request):
    if request.user.role == User.Role.STUDENT and request.method == 'POST':
        request.user.device_fingerprint = None
        request.user.save()
        messages.success(request, "Device reported lost. Your hardware lock has been reset. Please log in from your new device to secure it.")
    return redirect('device_integrity')

# =========================================
# 16. EXPANDED RBAC DASHBOARDS
# =========================================
@login_required
def super_admin_dashboard(request):
    if request.user.role != User.Role.SUPER_ADMIN: return redirect('dashboard')
    
    # 📡 Stubbed IoT Array until hardware node model is implemented
    iot_nodes = [
        {'mac': 'A0:B1:C2:D3', 'location': 'Main Gate Security', 'latency': '12ms', 'status': 'Online', 'status_class': 'success'},
        {'mac': 'F8:22:90:1A', 'location': 'Library Checkpoint', 'latency': '24ms', 'status': 'Online', 'status_class': 'success'},
        {'mac': '1B:4F:77:88', 'location': 'Room 304 (CS)', 'latency': '--', 'status': 'Offline', 'status_class': 'danger'},
    ]
    
    context = {
        'username': request.user.get_full_name() or request.user.username,
        'department_count': Department.objects.count(),
        'batch_count': Batch.objects.count(),
        'staff_count': StaffProfile.objects.count(),
        'student_count': StudentProfile.objects.count(),
        'iot_nodes': iot_nodes,
        'active_nodes': sum(1 for n in iot_nodes if n['status'] == 'Online'),
        'total_nodes': len(iot_nodes)
    }
    return render(request, 'superadmin_dashboard.html', context)

@login_required
def academic_coordinator_dashboard(request):
    if request.user.role != User.Role.ACADEMIC_COORDINATOR: return redirect('dashboard')
    context = {
        'username': request.user.get_full_name() or request.user.username,
        'active_slots': TimeTable.objects.count(),
        'faculty_deployed': StaffProfile.objects.count(),
        'faculty_list': StaffProfile.objects.select_related('user', 'department').all().order_by('department__name')
    }
    return render(request, 'academic_coordinator_dashboard.html', context)

@login_required
def hod_dashboard(request):
    if request.user.role != User.Role.HOD: return redirect('dashboard')
    
    dept = None
    if hasattr(request.user, 'staff_profile'):
        dept = request.user.staff_profile.department
        
    context = {
        'username': request.user.get_full_name() or request.user.username,
        'department': dept,
        'total_enrolled': StudentProfile.objects.filter(department=dept).count() if dept else 0,
        'total_faculty': StaffProfile.objects.filter(department=dept).count() if dept else 0,
        'escalations': [
            {'student': 'Ravi Kumar (CS-102)', 'reason': 'Attendance fell below 60%', 'tg': 'Prof. Varma', 'color': 'danger'},
            {'student': 'Neha Singh (CS-044)', 'reason': 'Mass bunk detected in Lecture 4', 'tg': 'Prof. Gokhale', 'color': 'warning'}
        ]
    }
    return render(request, 'hod_dashboard.html', context)

@login_required
def tg_dashboard(request):
    if request.user.role != User.Role.TEACHER_GUARDIAN: return redirect('dashboard')
    
    cohort = []
    at_risk = 0
    if hasattr(request.user, 'tg_cohort'):
        # In a real app we would compute live attendance, mocking risk metric for now
        cohort = request.user.tg_cohort.select_related('user').all()
        at_risk = 3 if len(cohort) > 3 else min(len(cohort), 3) # Mock logic
        
    context = {
        'username': request.user.get_full_name() or request.user.username,
        'cohort': cohort,
        'cohort_count': len(cohort),
        'at_risk_count': at_risk,
    }
    return render(request, 'tg_dashboard.html', context)

@login_required
def security_dashboard(request):
    if request.user.role != User.Role.SECURITY_OFFICER: return redirect('dashboard')
    
    context = {
        'username': request.user.get_full_name() or request.user.username,
        'recent_scans': [
            {'name': 'Rahul Varma (SE-CS-42)', 'note': 'Exit Protocol • Medical Leave (Approved)', 'status': 'success'},
            {'name': 'Priya Sharma (TE-IT-11)', 'note': 'Exit Protocol • DENIED', 'status': 'danger'},
            {'name': 'Faculty Vehicle (MH-12-AB..)', 'note': 'Entry Protocol • Auto-Synced', 'status': 'success'}
        ]
    }
    return render(request, 'security_dashboard.html', context)

@login_required
def librarian_dashboard(request):
    if request.user.role != User.Role.LIBRARIAN: return redirect('dashboard')
    context = {
        'username': request.user.get_full_name() or request.user.username,
        'active_checkouts': 1204,
        'overdue_defaults': 42,
        'overdue_books': [
            {'roll': 'CS-2024-44', 'book': 'Modern Operating Systems (Tanenbaum)', 'date': '12 Oct 2023', 'days': 14, 'color': 'danger'},
            {'roll': 'IT-2024-12', 'book': 'Intro to Algorithms (CLRS)', 'date': '01 Nov 2023', 'days': 2, 'color': 'warning'}
        ]
    }
    return render(request, 'librarian_dashboard.html', context)

@login_required
def finance_dashboard(request):
    if request.user.role != User.Role.FINANCE_CLERK: return redirect('dashboard')
    context = {
        'username': request.user.get_full_name() or request.user.username,
        'defaulters': [
            {'roll': 'COMP2024-112', 'name': 'Rahul Dravid', 'dept': 'Computer Engg', 'dues': '₹1,14,500', 'status': 'Locked', 'color': 'danger'},
            {'roll': 'IT2024-044', 'name': 'Aditi Sharma', 'dept': 'Info Tech', 'dues': '₹24,000', 'status': 'Warned', 'color': 'warning'}
        ]
    }
    return render(request, 'finance_dashboard.html', context)

@login_required
def parent_dashboard(request):
    if request.user.role != User.Role.PARENT: return redirect('dashboard')
    
    children = []
    if hasattr(request.user, 'parent_profile'):
        children = request.user.parent_profile.students.select_related('user', 'teacher_guardian', 'department').all()
        
    context = {
        'username': request.user.get_full_name() or request.user.username,
        'children': children,
    }
    return render(request, 'parent_dashboard.html', context)

@login_required
def send_mentor_message(request):
    if request.user.role == User.Role.STUDENT and request.method == 'POST':
        messages.success(request, "Your message has been sent to your mentor successfully!")
    return redirect('contact_mentor')

@login_required
def get_dynamic_qr_token(request):
    if request.user.role != User.Role.STUDENT:
        return JsonResponse({'error': 'Unauthorized'}, status=403)
    return JsonResponse({'token': TimestampSigner().sign(request.user.username)})

# =========================================
# 16. WEB AJAX APIs (Modals & Async UI)
# =========================================
@login_required
def student_daily_attendance_api(request, date_string):
    if request.user.role != User.Role.STUDENT:
        return JsonResponse({"error": "Unauthorized"}, status=403)

    try:
        target_date = datetime.strptime(date_string, '%Y-%m-%d').date()
    except ValueError:
        return JsonResponse({"error": "Invalid date format"}, status=400)

    user = request.user
    profile = getattr(user, 'student_profile', None)

    actual_attendances = Attendance.objects.filter(
        student=user, timestamp__date=target_date
    ).select_related('lecture__course').order_by('timestamp')

    attended_dict = {a.lecture.id: a for a in actual_attendances}
    lecture_data = [{"time": a.timestamp.strftime('%I:%M %p'), "subject": a.lecture.course.name, "status": a.status.title()} for a in actual_attendances]

    if profile:
        scheduled_lectures = Lecture.objects.filter(
            course__department=profile.department, course__semester=profile.current_semester, start_time__date=target_date
        ).select_related('course')

        for lec in scheduled_lectures:
            if lec.id not in attended_dict:
                lecture_data.append({"time": lec.start_time.strftime('%I:%M %p'), "subject": lec.course.name, "status": "Absent"})

    return JsonResponse({"date": date_string, "lectures": sorted(lecture_data, key=lambda x: x['time'])})