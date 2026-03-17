from core.models import User, Lecture, Attendance
import random
from django.utils import timezone
from datetime import timedelta

# Get your test student and an active lecture
student = User.objects.get(username="2526B071")
lecture = Lecture.objects.last()

# Generate 10 days of random attendance history
for i in range(10):
    Attendance.objects.create(
        student=student,
        lecture=lecture,
        status=random.choice(['PRESENT', 'ABSENT', 'EXCUSED']),
        timestamp=timezone.now() - timedelta(days=i)
    )
print("Test data injected!")