# core/views.py

from django.shortcuts import render, redirect, get_object_or_404
from django.contrib.auth import login, logout
from django.contrib.auth.decorators import login_required
from django.http import JsonResponse, HttpResponse
from django.db.models import Q
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated
from django.views.decorators.csrf import csrf_exempt
import uuid
import io
import csv

# ✅ IMPORT THE MODELS
from .models import (
    User, 
    StudentProfile, 
    TeacherProfile, 
    Department, 
    Batch,
    Semester,
    Course,       
    Classroom,    
    Lecture, 
    Attendance,
    TimeTable
)

# =========================================
# 1. AUTHENTICATION & ROUTING
# =========================================
def custom_login(request):
    """
    Temporary Login Redirect. 
    In the future, we will put a custom login page here.
    """
    if request.user.is_authenticated:
        return dashboard_redirect(request)
    return redirect('/admin/')

@login_required
def dashboard_redirect(request):
    """
    Smart Router: Sends user to the right dashboard based on Role.
    """
    user = request.user
    if user.role == User.Role.TEACHER:
        return redirect('teacher_dashboard')
    elif user.role == User.Role.STUDENT:
        return redirect('student_dashboard')
    elif user.role == User.Role.ADMIN:
        return redirect('/admin/')
    
    # Default fallback
    return redirect('/admin/')

# =========================================
# 2. TEACHER DASHBOARD
# =========================================
@login_required
def teacher_dashboard(request):
    # Security: Only allow Teachers
    if request.user.role != User.Role.TEACHER:
        return redirect('student_dashboard')

    # Get Active Lectures for this teacher
    active_lectures = Lecture.objects.filter(teacher=request.user, is_active=True)
    
    context = {
        'active_lectures': active_lectures,
        'username': request.user.username
    }
    return render(request, 'dashboard.html', context)

# =========================================
# 3. STUDENT DASHBOARD
# =========================================
@login_required
def student_dashboard(request):
    # Security: Only allow Students
    if request.user.role != User.Role.STUDENT:
        return redirect('teacher_dashboard')

    attendance_history = Attendance.objects.filter(student=request.user).order_by('-timestamp')
    
    context = {
        'history': attendance_history,
        'username': request.user.username
    }
    return render(request, 'student_dashboard.html', context)

# =========================================
# 4. REGISTRAR MODULE (Manage Students)
# =========================================
@login_required
def manage_students(request):
    # Security: Only Teachers or Admins can access this
    if request.user.role == User.Role.STUDENT:
        return redirect('student_dashboard')

    query = request.GET.get('q')
    
    # Efficiently fetch students with related info
    students = StudentProfile.objects.select_related('user', 'department', 'batch', 'current_semester').all().order_by('roll_no')
    
    # Search Logic
    if query:
        students = students.filter(
            Q(roll_no__icontains=query) | 
            Q(user__first_name__icontains=query) | 
            Q(user__last_name__icontains=query)
        )

    context = {
        'students': students, 
        'search_query': query,
        'username': request.user.username
    }
    return render(request, 'manage_students.html', context)

# =========================================
# 5. BULK UPLOAD LOGIC (The Fix)
# =========================================
@login_required
def bulk_upload_students(request):
    # Security Check
    if request.user.role == User.Role.STUDENT:
        return redirect('student_dashboard')

    if request.method == "POST":
        try:
            csv_file = request.FILES['file']
            
            # 1. Basic Validation
            if not csv_file.name.endswith('.csv'):
                return HttpResponse("Error: Please upload a .csv file.")

            # 2. Read File safely
            data_set = csv_file.read().decode('UTF-8')
            io_string = io.StringIO(data_set)
            
            # Skip Header Row
            next(io_string, None) 
            
            created_count = 0
            errors = []
            
            for index, row in enumerate(csv.reader(io_string, delimiter=',', quotechar="|")):
                try:
                    # Skip empty rows
                    if not row or len(row) < 6:
                        continue

                    # 3. CLEAN DATA (Strip spaces)
                    roll_no = row[0].strip()
                    first_name = row[1].strip()
                    last_name = row[2].strip()
                    dept_code = row[3].strip()
                    batch_year_str = row[4].strip() # Keep as string first
                    division = row[5].strip()

                    # 4. CONVERT YEAR TO INT (Critical Fix)
                    try:
                        batch_year = int(batch_year_str)
                    except ValueError:
                        errors.append(f"Row {index+2}: Year '{batch_year_str}' must be a number.")
                        continue

                    # 5. GET LINKED MODELS (Department & Batch)
                    try:
                        dept = Department.objects.get(code=dept_code)
                    except Department.DoesNotExist:
                        errors.append(f"Row {index+2}: Department '{dept_code}' does not exist.")
                        continue

                    try:
                        batch = Batch.objects.get(year=batch_year, department=dept)
                    except Batch.DoesNotExist:
                        errors.append(f"Row {index+2}: Batch '{batch_year}' for '{dept_code}' not found. Create it in Admin first.")
                        continue

                    # 6. CREATE USER & PROFILE
                    if not User.objects.filter(username=roll_no).exists():
                        # Create User
                        user = User.objects.create_user(
                            username=roll_no, 
                            password=roll_no, # Default password is Roll No
                            first_name=first_name, 
                            last_name=last_name,
                            role=User.Role.STUDENT
                        )
                        
                        # Create Profile
                        StudentProfile.objects.create(
                            user=user,
                            roll_no=roll_no,
                            department=dept,
                            batch=batch,
                            division=division,
                            # Assign to first active semester found
                            current_semester=Semester.objects.filter(is_active=True).first()
                        )
                        created_count += 1
                    else:
                        print(f"Skipping {roll_no}: Already exists.")

                except Exception as inner_e:
                    errors.append(f"Row {index+2}: Error - {str(inner_e)}")
                    continue
            
            # Debugging: Print errors to terminal
            if errors:
                print("--- UPLOAD ERRORS ---")
                for err in errors:
                    print(err)
                print("---------------------")

            return redirect('manage_students')

        except Exception as e:
            return HttpResponse(f"Critical Upload Error: {str(e)}")

    return render(request, 'upload_students.html')

# =========================================
# 6. SCHEDULER MODULE (Timetable)
# =========================================
@login_required
def manage_timetable(request):
    # Only Teachers/Admins
    if request.user.role == User.Role.STUDENT:
        return redirect('student_dashboard')

    # Grouping logic: We want to see the schedule day by day
    # 0=Monday, 1=Tuesday...
    days = {
        0: 'Monday', 1: 'Tuesday', 2: 'Wednesday', 
        3: 'Thursday', 4: 'Friday', 5: 'Saturday'
    }
    
    timetable = TimeTable.objects.select_related('course', 'classroom', 'teacher').order_by('day_of_week', 'start_time')

    return render(request, 'manage_timetable.html', {'timetable': timetable, 'days': days})

@login_required
def add_schedule(request):
    """
    Simple form to add a new class slot.
    In a real app, we would use Django Forms, but let's keep it raw HTML for clarity.
    """
    if request.user.role == User.Role.STUDENT:
        return redirect('student_dashboard')

    if request.method == "POST":
        day = request.POST.get('day')
        start_time = request.POST.get('start_time')
        end_time = request.POST.get('end_time')
        course_id = request.POST.get('course_id')
        room_id = request.POST.get('room_id')
        
        # Create the slot
        TimeTable.objects.create(
            day_of_week=day,
            start_time=start_time,
            end_time=end_time,
            course_id=course_id,
            classroom_id=room_id,
            teacher=request.user # Assigning the current logged-in teacher
        )
        return redirect('manage_timetable')

    # Pass data for dropdowns
    courses = Course.objects.all()
    rooms = Classroom.objects.all()
    
    return render(request, 'add_schedule.html', {'courses': courses, 'rooms': rooms})

# =========================================
# 7. API (The IoT Brain)
# =========================================
from django.utils import timezone
import datetime

class ESP32ScanView(APIView):
    """
    Receives data from ESP32 and marks attendance automatically.
    Payload: {"device_id": "ESP_ROOM_101", "scans": ["2526B069", "2526B070"]}
    """
    permission_classes = [] # Allow ESP32 to hit this without login (API Key used instead)

    def post(self, request):
        data = request.data
        esp_id = data.get('device_id')
        scanned_ids = data.get('scans', [])

        if not esp_id or not scanned_ids:
            return Response({"error": "Missing device_id or scans"}, status=400)

        # 1. IDENTIFY THE ROOM
        try:
            classroom = Classroom.objects.get(esp_device_id=esp_id)
        except Classroom.DoesNotExist:
            return Response({"error": f"Device {esp_id} not registered to any room"}, status=404)

        # 2. FIND ACTIVE LECTURE (Smart Check)
        # Option A: Is there a manually started class?
        active_lecture = Lecture.objects.filter(classroom=classroom, is_active=True).first()

        # Option B: If not, check the TIMETABLE and AUTO-START it (Automation!)
        if not active_lecture:
            now = timezone.localtime(timezone.now())
            current_time = now.time()
            current_day = now.weekday() # 0=Monday, 6=Sunday

            timetable_slot = TimeTable.objects.filter(
                classroom=classroom,
                day_of_week=current_day,
                start_time__lte=current_time,
                end_time__gte=current_time
            ).first()

            if timetable_slot:
                # Auto-create the lecture instance
                active_lecture = Lecture.objects.create(
                    course=timetable_slot.course,
                    classroom=timetable_slot.classroom,
                    teacher=timetable_slot.teacher,
                    is_active=True
                )
                print(f"✅ AUTO-STARTED Class: {active_lecture}")
            else:
                return Response({"message": "No class scheduled right now. Scans ignored."}, status=200)

        # 3. MARK ATTENDANCE
        marked_count = 0
        for roll_no in scanned_ids:
            try:
                # Find the student user by Roll No (Username)
                student_user = User.objects.get(username=roll_no)
                
                # Create Attendance Record (if not exists)
                obj, created = Attendance.objects.get_or_create(
                    student=student_user,
                    lecture=active_lecture,
                    defaults={'status': 'PRESENT', 'device_id': esp_id}
                )
                if created:
                    marked_count += 1
            except User.DoesNotExist:
                print(f"⚠️ Unknown Student ID scanned: {roll_no}")
                continue

        return Response({
            "status": "success",
            "class": active_lecture.course.name,
            "marked_new": marked_count,
            "total_present": active_lecture.attendance_records.count()
        })