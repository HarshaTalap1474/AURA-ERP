from django.shortcuts import render, redirect, get_object_or_404
from django.contrib.auth.decorators import login_required
from django.http import HttpResponse
from django.db.models import Q
from django.utils import timezone
from django.contrib.auth import authenticate

# REST API Imports
from rest_framework.views import APIView
from rest_framework.decorators import api_view, permission_classes
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.response import Response
from rest_framework_simplejwt.tokens import RefreshToken
from django.contrib.auth import update_session_auth_hash

import io
import csv
import uuid

# ✅ IMPORT THE MODELS
from .models import (
    User, StudentProfile, TeacherProfile, Department, Batch, Semester,
    Course, Classroom, Lecture, Attendance, TimeTable
)

# =========================================
# 1. AUTHENTICATION & ROUTING
# =========================================
def custom_login(request):
    if request.user.is_authenticated:
        return dashboard_redirect(request)
    return redirect('/admin/')

@login_required
def dashboard_redirect(request):
    user = request.user
    if user.role == User.Role.TEACHER:
        return redirect('teacher_dashboard')
    elif user.role == User.Role.STUDENT:
        return redirect('student_dashboard')
    elif user.role == User.Role.ADMIN:
        return redirect('/admin/')
    return redirect('/admin/')

# =========================================
# 2. TEACHER DASHBOARD
# =========================================
@login_required
def teacher_dashboard(request):
    if request.user.role != User.Role.TEACHER:
        return redirect('student_dashboard')
    active_lectures = Lecture.objects.filter(teacher=request.user, is_active=True)
    return render(request, 'dashboard.html', {'active_lectures': active_lectures, 'username': request.user.username})

# =========================================
# 3. STUDENT DASHBOARD
# =========================================
@login_required
def student_dashboard(request):
    if request.user.role != User.Role.STUDENT:
        return redirect('teacher_dashboard')
    attendance_history = Attendance.objects.filter(student=request.user).order_by('-timestamp')
    return render(request, 'student_dashboard.html', {'history': attendance_history, 'username': request.user.username})

# =========================================
# 4. REGISTRAR MODULE
# =========================================
@login_required
def manage_students(request):
    if request.user.role == User.Role.STUDENT:
        return redirect('student_dashboard')

    query = request.GET.get('q')
    students = StudentProfile.objects.select_related('user', 'department', 'batch', 'current_semester').all().order_by('roll_no')

    if query:
        students = students.filter(
            Q(roll_no__icontains=query) | 
            Q(user__first_name__icontains=query) | 
            Q(user__last_name__icontains=query)
        )
    return render(request, 'manage_students.html', {'students': students, 'search_query': query, 'username': request.user.username})

# =========================================
# 5. BULK UPLOAD LOGIC
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
            next(io_string, None) # Skip Header
            
            errors = []
            
            for index, row in enumerate(csv.reader(io_string, delimiter=',', quotechar="|")):
                try:
                    if not row or len(row) < 6: continue
                    
                    roll_no = row[0].strip()
                    first_name = row[1].strip()
                    last_name = row[2].strip()
                    dept_code = row[3].strip()
                    batch_year_str = row[4].strip()
                    division = row[5].strip()

                    try:
                        batch_year = int(batch_year_str)
                    except ValueError:
                        errors.append(f"Row {index+2}: Year '{batch_year_str}' must be a number.")
                        continue

                    try:
                        dept = Department.objects.get(code=dept_code)
                    except Department.DoesNotExist:
                        errors.append(f"Row {index+2}: Dept '{dept_code}' not found.")
                        continue

                    try:
                        batch = Batch.objects.get(year=batch_year, department=dept)
                    except Batch.DoesNotExist:
                        errors.append(f"Row {index+2}: Batch '{batch_year}' not found.")
                        continue

                    if not User.objects.filter(username=roll_no).exists():
                        user = User.objects.create_user(username=roll_no, password=roll_no, first_name=first_name, last_name=last_name, role=User.Role.STUDENT)
                        StudentProfile.objects.create(user=user, roll_no=roll_no, department=dept, batch=batch, division=division, current_semester=Semester.objects.filter(is_active=True).first())
                    else:
                        print(f"Skipping {roll_no}: Already exists.")

                except Exception as inner_e:
                    errors.append(f"Row {index+2}: Error - {str(inner_e)}")
                    continue
            
            if errors:
                for err in errors: print(err)

            return redirect('manage_students')

        except Exception as e:
            return HttpResponse(f"Critical Upload Error: {str(e)}")

    return render(request, 'upload_students.html')

# =========================================
# 6. TIMETABLE
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
# ⚠️ I ADDED THIS BACK - THIS WAS CAUSING THE ERROR ⚠️
class ESP32ScanView(APIView):
    """
    Receives data from ESP32 hardware scanners installed in rooms.
    Payload: {"device_id": "ESP_ROOM_101", "scans": ["2526B069", "2526B070"]}
    """
    permission_classes = [] # Allow hardware to hit this without login

    def post(self, request):
        data = request.data
        esp_id = data.get('device_id')
        scanned_ids = data.get('scans', [])

        if not esp_id or not scanned_ids:
            return Response({"error": "Missing device_id or scans"}, status=400)

        # 1. Identify Room
        try:
            classroom = Classroom.objects.get(esp_device_id=esp_id)
        except Classroom.DoesNotExist:
            return Response({"error": f"Device {esp_id} not registered"}, status=404)

        # 2. Find Active Lecture (or Auto-Start)
        active_lecture = Lecture.objects.filter(classroom=classroom, is_active=True).first()

        if not active_lecture:
            now = timezone.localtime(timezone.now())
            timetable_slot = TimeTable.objects.filter(
                classroom=classroom,
                day_of_week=now.weekday(),
                start_time__lte=now.time(),
                end_time__gte=now.time()
            ).first()

            if timetable_slot:
                active_lecture = Lecture.objects.create(
                    course=timetable_slot.course,
                    classroom=timetable_slot.classroom,
                    teacher=timetable_slot.teacher,
                    is_active=True
                )
            else:
                return Response({"message": "No class scheduled. Scans ignored."}, status=200)

        # 3. Mark Attendance
        marked_count = 0
        for roll_no in scanned_ids:
            try:
                # Find User by Username (Roll No)
                student_user = User.objects.get(username=roll_no)
                
                # Link to USER, not StudentProfile
                obj, created = Attendance.objects.get_or_create(
                    student=student_user,
                    lecture=active_lecture,
                    defaults={'status': 'PRESENT', 'device_id': esp_id}
                )
                if created: marked_count += 1
            except User.DoesNotExist:
                continue

        return Response({
            "status": "success",
            "class": active_lecture.course.name,
            "marked_new": marked_count
        })

# =========================================
# 8. ANDROID API (Phone Scans Room)
# =========================================
@api_view(['POST'])
@permission_classes([AllowAny]) 
def mark_attendance(request):
    """
    Called by Android App when it detects a Classroom Beacon.
    Payload: {"student_id": "roll_B069", "device_id": "ESP_MAC_ADDRESS"}
    """
    student_username = request.data.get('student_id')
    esp_mac_address = request.data.get('device_id')

    if not student_username or not esp_mac_address:
        return Response({"status": "error", "message": "Missing data"}, status=400)

    try:
        classroom = Classroom.objects.get(esp_device_id__iexact=esp_mac_address)
    except Classroom.DoesNotExist:
        return Response({"status": "error", "message": "Invalid Classroom Beacon"}, status=404)

    try:
        current_lecture = Lecture.objects.get(classroom=classroom, is_active=True)
    except Lecture.DoesNotExist:
        return Response({"status": "error", "message": "No active class here."}, status=404)

    try:
        user = User.objects.get(username=student_username)
    except User.DoesNotExist:
        return Response({"status": "error", "message": "Student not found"}, status=404)

    if Attendance.objects.filter(student=user, lecture=current_lecture).exists():
        return Response({"status": "warning", "message": "Attendance already marked!"})

    Attendance.objects.create(
        student=user,
        lecture=current_lecture,
        status='PRESENT',
        device_id=esp_mac_address
    )

    return Response({"status": "success", "message": f"Present marked for {current_lecture.course.code}!"})


# =========================================
# 9. AUTHENTICATION API (Login, Profile & Logout)
# =========================================
@api_view(['POST'])
@permission_classes([AllowAny])
def app_login(request):
    """
    Secure Login with Debugging for Hardware Lock Issues.
    """
    username = request.data.get('username')
    password = request.data.get('password')
    
    # 1. ROBUST KEY FETCH (Check both potential keys)
    incoming_fingerprint = request.data.get('device_fingerprint') or request.data.get('device_id')
    
    # Clean whitespace if it exists
    if incoming_fingerprint:
        incoming_fingerprint = incoming_fingerprint.strip()

    if not username or not password:
        return Response({"status": "error", "message": "Credentials missing"}, status=400)

    user = authenticate(username=username, password=password)

    if user is not None:
        if not user.is_active:
            return Response({"status": "error", "message": "Account disabled"}, status=403)

        # =========================================================
        # 🔍 DEBUGGING LOGS (Requested by App Team)
        # =========================================================
        print(f"\n--- DEBUG LOGIN FINGERPRINT ---")
        print(f"Username: {user.username}")
        print(f"Stored DB Fingerprint: '{user.device_fingerprint}'")
        print(f"Incoming App Fingerprint: '{incoming_fingerprint}'")
        
        # Check matching status
        match_status = "MATCH" if user.device_fingerprint == incoming_fingerprint else "MISMATCH"
        if user.device_fingerprint is None: match_status = "NEW DEVICE (Will Bind)"
        print(f"Status: {match_status}")
        print(f"-------------------------------\n")
        # =========================================================

        # 2. 🛡️ HARDWARE BINDING LOGIC
        if incoming_fingerprint:
            # Case A: First time login (Bind the device)
            if user.device_fingerprint is None:
                user.device_fingerprint = incoming_fingerprint
                user.save()
                print(f"✅ Device Bound Successfully: {incoming_fingerprint}")
            
            # Case B: Device mismatch (Block the login)
            # We strictly check: If DB has a value, and it doesn't match Incoming -> BLOCK
            elif user.device_fingerprint != incoming_fingerprint:
                print(f"❌ BLOCKED: Stored '{user.device_fingerprint}' != Incoming '{incoming_fingerprint}'")
                return Response({
                    "status": "error", 
                    "message": "Security Alert: This device is linked with another device."
                }, status=403)

        # 3. GENERATE TOKENS
        refresh = RefreshToken.for_user(user)

        return Response({
            "status": "success",
            "username": user.username,
            "role": user.role,
            "name": f"{user.first_name} {user.last_name}",
            "email": user.email if user.phone_number else "",
            "phone_number": user.phone_number,
            "user_id": user.id,
            "device_fingerprint": user.device_fingerprint,
            "access_token": str(refresh.access_token),
            "refresh_token": str(refresh)
        })

    else:
        return Response({"status": "error", "message": "Invalid credentials"}, status=401)


@api_view(['POST'])
@permission_classes([IsAuthenticated])
def update_profile(request):
    user = request.user
    data = request.data

    if 'first_name' in data: user.first_name = data['first_name']
    if 'last_name' in data: user.last_name = data['last_name']
    if 'email' in data: user.email = data['email']
    if 'phone_number' in data: user.phone_number = data['phone_number']

    user.save()

    return Response({
        "status": "success",
        "message": "Profile updated",
        "user": {
            "name": f"{user.first_name} {user.last_name}",
            "email": user.email,
            "phone": user.phone_number,
            "username": user.username
        }
    })


@api_view(['POST'])
@permission_classes([IsAuthenticated])
def change_password(request):
    """
    Allows a logged-in user to change their password.
    Requires 'old_password' verification for security.
    """
    user = request.user
    data = request.data
    
    old_password = data.get('old_password')
    new_password = data.get('new_password')

    # 1. Validate Input
    if not old_password or not new_password:
        return Response({
            "status": "error",
            "message": "Both old and new passwords are required."
        }, status=400)

    # 2. Verify Old Password
    if not user.check_password(old_password):
        return Response({
            "status": "error",
            "message": "Wrong old password."
        }, status=400)

    # 3. Set New Password (Handles hashing automatically)
    user.set_password(new_password)
    user.save()

    # 4. Keep User Logged In (For Session Auth) / Optional for JWT
    # This prevents the current session from being invalidated immediately
    update_session_auth_hash(request, user)

    return Response({
        "status": "success",
        "message": "Password changed successfully"
    })