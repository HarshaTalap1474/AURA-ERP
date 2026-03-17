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
from django.db.models import Count, Q
from django.utils import timezone
from django.contrib.auth import authenticate, update_session_auth_hash, logout
from django.contrib.auth.views import PasswordChangeView
from django.contrib import messages
from django.urls import reverse_lazy
from django.core.signing import TimestampSigner

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
    User, StudentProfile, TeacherProfile, Department, Batch, Semester,
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
    """ Bulletproof logout view that forces a redirect to the login page. """
    logout(request) 
    return redirect('login') 

@login_required
def dashboard_redirect(request):
    user = request.user
    if user.role == User.Role.TEACHER:
        return redirect('teacher_dashboard')
    elif user.role == User.Role.STUDENT:
        return redirect('student_dashboard')
    elif user.role == User.Role.ADMIN:
        return redirect('manage_students')
    return redirect('login')

# =========================================
# 2. TEACHER DASHBOARD (Web Portal)
# =========================================
@login_required
def teacher_dashboard(request):
    if request.user.role != User.Role.TEACHER:
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

    todays_lectures = []
    for slot in todays_timetable:
        is_active = slot.start_time <= now.time() <= slot.end_time
        time_str = f"{slot.start_time.strftime('%I:%M %p')} - {slot.end_time.strftime('%I:%M %p')}"
        
        todays_lectures.append({
            'id': slot.id,
            'time': time_str,
            'subject': {'name': slot.course.name},
            'batch': f"{slot.course.code}", 
            'room': slot.classroom.room_number if slot.classroom else "TBD",
            'is_active': is_active
        })

    context = {
        'stats': {
            'today_lectures': todays_timetable.count(),
            'students_present': students_present_count,
            'pending_leaves': 0 
        },
        'todays_lectures': todays_lectures,
        'username': request.user.username
    }
    return render(request, 'teacher_dashboard.html', context)

# =========================================
# 3. STUDENT DASHBOARD (Web Portal)
# =========================================
@login_required
def student_dashboard(request):
    # Security: Only allow Students
    if request.user.role != User.Role.STUDENT:
        return redirect('teacher_dashboard')

    # 1. Fetch Basic History
    attendance_history = Attendance.objects.filter(student=request.user).order_by('-timestamp')

    # 2. CALCULATE OVERALL ATTENDANCE PERCENTAGE
    # Get the student's profile to know their batch/semester
    profile = getattr(request.user, 'student_profile', None)
    
    attendance_percentage = 0
    if profile:
        # Total lectures conducted for the courses in the student's current semester
        total_conducted = Lecture.objects.filter(
            course__semester=profile.current_semester,
            is_active=False # Only count finished classes
        ).count()

        # Total lectures this student actually attended
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
# 5. BULK UPLOAD LOGIC (Web Portal)
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

            data_set = csv_file.read().decode('UTF-8')
            io_string = io.StringIO(data_set)
            next(io_string, None) 
            
            errors = []
            for index, row in enumerate(csv.reader(io_string, delimiter=',', quotechar="|")):
                try:
                    if not row or len(row) < 6: continue
                    
                    roll_no, first_name, last_name, dept_code, batch_year_str, division = [r.strip() for r in row[:6]]

                    try:
                        batch_year = int(batch_year_str)
                    except ValueError:
                        errors.append(f"Row {index+2}: Year '{batch_year_str}' must be a number.")
                        continue

                    try:
                        dept = Department.objects.get(code=dept_code)
                        batch = Batch.objects.get(year=batch_year, department=dept)
                    except (Department.DoesNotExist, Batch.DoesNotExist) as e:
                        errors.append(f"Row {index+2}: {str(e)} not found.")
                        continue

                    if not User.objects.filter(username=roll_no).exists():
                        user = User.objects.create_user(username=roll_no, password=roll_no, first_name=first_name, last_name=last_name, role=User.Role.STUDENT)
                        StudentProfile.objects.create(user=user, roll_no=roll_no, department=dept, batch=batch, division=division, current_semester=Semester.objects.filter(is_active=True).first())
                except Exception as inner_e:
                    errors.append(f"Row {index+2}: Error - {str(inner_e)}")
            
            if errors:
                for err in errors: print(err)

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
# 7. IOT HARDWARE API (Room Scans Phone)
# =========================================
@api_view(['POST'])
@permission_classes([AllowAny])
def hardware_sync(request):
    data = request.data
    gateway_id = data.get('gateway_id')
    detected_students = data.get('detected_students', [])

    if not gateway_id or not detected_students:
        return Response({"status": "error", "message": "Missing gateway_id or detected_students"}, status=400)

    try:
        classroom = Classroom.objects.get(esp_device_id__iexact=gateway_id)
    except Classroom.DoesNotExist:
        return Response({"status": "error", "message": "Gateway not registered"}, status=404)

    active_lecture = Lecture.objects.filter(classroom=classroom, is_active=True).first()

    if not active_lecture:
        now = timezone.localtime(timezone.now())
        timetable_slot = TimeTable.objects.filter(
            classroom=classroom, day_of_week=now.weekday(),
            start_time__lte=now.time(), end_time__gte=now.time()
        ).first()

        if timetable_slot:
            active_lecture = Lecture.objects.create(
                course=timetable_slot.course, classroom=timetable_slot.classroom,
                teacher=timetable_slot.teacher, is_active=True
            )
        else:
            return Response({"status": "ignored", "message": "No class scheduled."}, status=200)

    marked_count = 0
    for identifier in detected_students:
        student_user = User.objects.filter(Q(username=identifier) | Q(device_fingerprint=identifier)).first()
        if student_user:
            obj, created = Attendance.objects.get_or_create(
                student=student_user, lecture=active_lecture,
                defaults={'status': 'PRESENT', 'device_id': gateway_id}
            )
            if created: marked_count += 1

    return Response({
        "status": "success", "room": classroom.room_number,
        "class": active_lecture.course.code, "marked_new": marked_count
    })

# =========================================
# 8. ANDROID API (Phone Scans Room)
# =========================================
@api_view(['POST'])
@permission_classes([AllowAny]) 
def mark_attendance(request):
    """
    Called by Android App/Scanner when it scans a Student's Dynamic QR Code.
    """
    scanned_qr_token = request.data.get('student_id') # This is now the encrypted token!
    esp_mac_address = request.data.get('device_id')

    if not scanned_qr_token or not esp_mac_address:
        return Response({"status": "error", "message": "Missing scan data"}, status=400)

    # 🔐 Step 1: Decrypt and Verify the QR Code
    signer = TimestampSigner()
    try:
        # max_age=15 means the QR code is immediately rejected if it's older than 15 seconds!
        student_username = signer.unsign(scanned_qr_token, max_age=15)
        
    except SignatureExpired:
        return Response({"status": "error", "message": "QR Code Expired! Please scan the live screen."}, status=403)
    except BadSignature:
        return Response({"status": "error", "message": "Invalid or Tampered QR Code!"}, status=403)

    # 🏫 Step 2: Proceed with standard Attendance Logic
    try:
        classroom = Classroom.objects.get(esp_device_id__iexact=esp_mac_address)
        current_lecture = Lecture.objects.get(classroom=classroom, is_active=True)
        user = User.objects.get(username=student_username)
    except (Classroom.DoesNotExist, Lecture.DoesNotExist, User.DoesNotExist):
        return Response({"status": "error", "message": "Invalid room scan or session."}, status=404)

    # 🛑 Step 3: Prevent duplicate scans
    if Attendance.objects.filter(student=user, lecture=current_lecture).exists():
        return Response({"status": "warning", "message": "Attendance already marked!"})

    # ✅ Step 4: Mark them Present!
    Attendance.objects.create(student=user, lecture=current_lecture, status='PRESENT', device_id=esp_mac_address)
    
    return Response({
        "status": "success", 
        "message": f"Present marked for {user.first_name} ({current_lecture.course.code})!"
    })
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

    # 📥 GET: Mobile app wants to display the user's current info
    if request.method == 'GET':
        return Response({
            "status": "success",
            "user": {
                "username": user.username,
                "first_name": user.first_name,
                "last_name": user.last_name,
                "email": user.email,
                "phone_number": user.phone_number,
                "blood_group": user.blood_group,
                "address": user.address,
                "emergency_contact": user.emergency_contact,
                "dob": user.dob,
                "role": user.role
            }
        })

    # 📤 POST: Mobile app is sending new data to save
    if request.method == 'POST':
        data = request.data
        
        if 'first_name' in data: user.first_name = data['first_name']
        if 'last_name' in data: user.last_name = data['last_name']
        if 'email' in data: user.email = data['email']
        if 'phone_number' in data and hasattr(user, 'phone_number'): user.phone_number = data['phone_number']
        
        # ✅ NEW ERP PROFILE FIELDS
        if 'blood_group' in data: user.blood_group = data['blood_group']
        if 'address' in data: user.address = data['address']
        if 'emergency_contact' in data: user.emergency_contact = data['emergency_contact']
        if 'dob' in data and data['dob']: user.dob = data['dob']
            
        user.save()

        return Response({
            "status": "success", 
            "message": "Profile updated",
            "user": {
                "name": f"{user.first_name} {user.last_name}", 
                "email": user.email, 
                "username": user.username
            }
        })

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

    courses = Course.objects.filter(department=profile.department, semester=profile.current_semester)
    history_data, overall_present, overall_total_lectures = [], 0, 0

    for course in courses:
        total_lectures = Lecture.objects.filter(course=course, is_active=False).count()
        present_count = Attendance.objects.filter(student=request.user, lecture__course=course, status='PRESENT').count()
        pct = (present_count / total_lectures * 100) if total_lectures > 0 else 0.0

        teacher_name = "Not Allocated"
        if (tt := TimeTable.objects.filter(course=course).first()): teacher_name = tt.teacher.get_full_name()
        elif (last_lec := Lecture.objects.filter(course=course).first()): teacher_name = last_lec.teacher.get_full_name()

        history_data.append({
            "subject_name": course.name, "subject_code": course.code, "teacher_name": teacher_name,
            "present": present_count, "total": total_lectures, "percentage": round(pct, 1)
        })
        overall_present += present_count
        overall_total_lectures += total_lectures

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
@login_required
def profile(request):
    """ Web Portal Profile View """
    user = request.user
    
    if request.method == "POST":
        user.first_name = request.POST.get('first_name', user.first_name)
        user.last_name = request.POST.get('last_name', user.last_name)
        user.email = request.POST.get('email', user.email)
        if hasattr(user, 'phone_number'): 
            user.phone_number = request.POST.get('phone_number', user.phone_number)
            
        # The new ERP fields
        user.blood_group = request.POST.get('blood_group', user.blood_group)
        user.address = request.POST.get('address', user.address)
        user.emergency_contact = request.POST.get('emergency_contact', user.emergency_contact)
        new_dob = request.POST.get('dob')
        if new_dob: 
            user.dob = new_dob

        user.save()
        messages.success(request, "Your profile has been updated successfully!")
        return redirect('profile') 

    # ✅ THIS WAS THE MISSING PART CAUSING THE 'None' CRASH!
    context = {'user': user}
    if user.role == User.Role.STUDENT and hasattr(user, 'student_profile'): 
        context['student_profile'] = user.student_profile
    elif user.role == User.Role.TEACHER and hasattr(user, 'teacher_profile'): 
        context['teacher_profile'] = user.teacher_profile
        
    return render(request, 'profile.html', context)

def coming_soon(request, module_name="Feature"):
    return render(request, 'coming_soon.html', {'feature_name': module_name.replace('-', ' ').title()})

from datetime import datetime
import calendar
from django.contrib.auth.decorators import login_required
from django.shortcuts import render, redirect

@login_required
def student_analytics(request):
    # सुरक्षा check
    if request.user.role != User.Role.STUDENT or not hasattr(request.user, 'student_profile'): 
        return redirect('dashboard')
        
    profile = request.user.student_profile
    user = request.user
    
    # =========================
    # 1. SUBJECT ANALYSIS
    # =========================
    courses = Course.objects.filter(
        department=profile.department,
        semester=profile.current_semester
    )

    subject_data = []
    total_lec_overall = 0
    total_pres_overall = 0

    for course in courses:
        total_lec = Lecture.objects.filter(course=course, is_active=False).count()

        attended = Attendance.objects.filter(
            student=user,
            lecture__course=course,
            status__in=['PRESENT', 'EXCUSED']  # ✅ consistent
        ).count()

        pct = (attended / total_lec * 100) if total_lec > 0 else 0.0

        subject_data.append({
            'name': course.name,
            'code': course.code,
            'attended': attended,
            'total': total_lec,
            'percentage': round(pct, 1),
            'status': "Safe" if pct >= 75 else "Critical"
        })

        total_lec_overall += total_lec
        total_pres_overall += attended

    # =========================
    # 2. OVERALL %
    # =========================
    overall_percentage = (
        total_pres_overall / total_lec_overall * 100
        if total_lec_overall > 0 else 0.0
    )

    # SVG circle math (circumference = 440)
    svg_offset = 440 - (440 * overall_percentage / 100)

    # =========================
    # 3. CALENDAR HEATMAP
    # =========================
    now = datetime.now()
    today = now.date()
    num_days = calendar.monthrange(now.year, now.month)[1]

    # Days where student is present/excused
    present_days = set(
        Attendance.objects.filter(
            student=user,
            timestamp__year=now.year,
            timestamp__month=now.month,
            status__in=['PRESENT', 'EXCUSED']
        ).values_list('timestamp__day', flat=True).distinct()
    )

    calendar_events = []

    for day in range(1, num_days + 1):
        curr_date = datetime(now.year, now.month, day).date()

        if day in present_days:
            status = 'P'   # Present
        elif curr_date.weekday() == 6:
            status = 'H'   # Holiday (Sunday)
        elif curr_date < today:
            status = 'A'   # Absent (past)
        elif curr_date > today:
            status = 'U'   # Upcoming ✅ FIX
        else:
            status = 'N'   # Today but no data yet

        calendar_events.append({
            'date': str(day),
            'day_name': curr_date.strftime('%a'),
            'full_date': curr_date.strftime('%Y-%m-%d'),
            'status': status
        })

    # =========================
    # 4. CONTEXT
    # =========================
    context = {
        'overall_percentage': round(overall_percentage, 1),
        'total_days_present': total_pres_overall,
        'total_days_working': total_lec_overall,
        'svg_offset': svg_offset,
        'subject_analysis': subject_data,
        'calendar_events': calendar_events,
        'current_month_name': now.strftime("%B %Y")
    }

    return render(request, 'attendance_detailed.html', context)

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
# 13. LIVE MONITOR & UI APIs
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
    leave_request, action = get_object_or_404(LeaveRequest, id=request_id), request.POST.get('action')
    
    if action == 'approve':
        leave_request.status = 'APPROVED'
        leave_request.save()
        _process_excused_attendance(leave_request)
        messages.success(request, "Leave approved. Attendance updated to 'Excused'.")
    elif action == 'reject':
        leave_request.status = 'REJECTED'
        leave_request.save()
        messages.warning(request, "Leave request rejected.")
    return redirect('leave_approvals')

def _process_excused_attendance(leave_req):
    student_profile = leave_req.student.student_profile
    current_date = leave_req.start_date
    while current_date <= leave_req.end_date:
        daily_slots = TimeTable.objects.filter(course__department=student_profile.department, course__semester=student_profile.current_semester, day_of_week=current_date.weekday())
        for slot in daily_slots:
            lecture, _ = Lecture.objects.get_or_create(course=slot.course, teacher=slot.teacher, is_active=False)
            Attendance.objects.update_or_create(student=leave_req.student, lecture=lecture, defaults={'status': 'EXCUSED'})
        current_date += timedelta(days=1)

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

@login_required
def send_mentor_message(request):
    if request.user.role == User.Role.STUDENT and request.method == 'POST':
        messages.success(request, "Your message has been sent to your mentor successfully!")
    return redirect('contact_mentor')

@login_required
def get_dynamic_qr_token(request):
    """ Generates a cryptographically signed token that expires in 15 seconds. """
    if request.user.role != User.Role.STUDENT:
        return JsonResponse({'error': 'Unauthorized'}, status=403)
    # Sign the user's ID/Roll Number securely
    return JsonResponse({'token': TimestampSigner().sign(request.user.username)})

# =========================================
# 16. WEB AJAX APIs (Modals & Async UI)
# =========================================
@login_required
def student_daily_attendance_api(request, date_string):
    """
    AJAX API: Returns a breakdown of lectures and attendance status for a specific date.
    Works for both explicit records (testing/manual) and implicit absences (production).
    """
    if request.user.role != User.Role.STUDENT:
        return JsonResponse({"error": "Unauthorized"}, status=403)

    try:
        target_date = datetime.strptime(date_string, '%Y-%m-%d').date()
    except ValueError:
        return JsonResponse({"error": "Invalid date format"}, status=400)

    user = request.user
    profile = getattr(user, 'student_profile', None)

    # 1. Fetch the records they ACTUALLY have (Present, Excused, or Fake Absents)
    actual_attendances = Attendance.objects.filter(
        student=user,
        timestamp__date=target_date
    ).select_related('lecture__course').order_by('timestamp')

    # Dictionary to quickly check if they attended a specific lecture
    attended_dict = {a.lecture.id: a for a in actual_attendances}
    lecture_data = []

    # 2. Add all the records we actually found in the database
    for a in actual_attendances:
        lecture_data.append({
            "time": a.timestamp.strftime('%I:%M %p'),
            "subject": a.lecture.course.name,
            "status": a.status.title()
        })

    # 3. PRODUCTION CHECK: Find lectures they missed entirely (No DB record exists)
    if profile:
        scheduled_lectures = Lecture.objects.filter(
            course__department=profile.department,
            course__semester=profile.current_semester,
            start_time__date=target_date
        ).select_related('course')

        for lec in scheduled_lectures:
            # If the lecture happened, but they don't have a record for it, they were Absent!
            if lec.id not in attended_dict:
                lecture_data.append({
                    "time": lec.start_time.strftime('%I:%M %p'),
                    "subject": lec.course.name,
                    "status": "Absent" # Implicit absence
                })

    # Sort the final list by time so it looks neat in the UI
    lecture_data = sorted(lecture_data, key=lambda x: x['time'])

    return JsonResponse({
        "date": date_string,
        "lectures": lecture_data
    })