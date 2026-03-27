from django.db.models.signals import post_save
from django.dispatch import receiver
from .models import Attendance, NotificationInbox
from .tasks import dispatch_fcm_push

@receiver(post_save, sender=Attendance)
def trigger_absent_notification(sender, instance, created, **kwargs):
    """
    Automated Communication Hub Trigger:
    When a student represents an 'ABSENT' state, dispatch local DB alerts
    and trigger a Celery task to hit Google Firebase Cloud Messaging (FCM).
    """
    if instance.status == 'ABSENT':
        student = instance.student
        
        # 1. Notify Student locally
        NotificationInbox.objects.create(
            user=student,
            title="Attendance Alert",
            message=f"You have been marked ABSENT for {instance.lecture.course.name} on {instance.lecture.start_time.strftime('%Y-%m-%d')}.",
            fcm_dispatched=bool(student.fcm_device_token)
        )
        if student.fcm_device_token:
            dispatch_fcm_push.delay("Attendance Alert", f"Marked ABSENT for {instance.lecture.course.name}", student.fcm_device_token)
            
        # 2. Notify Parent locally
        try:
            parent_profile = getattr(student, 'student_profile', None).parent
            if parent_profile:
                NotificationInbox.objects.create(
                    user=parent_profile.user,
                    title="Ward Attendance Alert",
                    message=f"Your ward, {student.get_full_name() or student.username}, was marked ABSENT for {instance.lecture.course.name}.",
                    fcm_dispatched=bool(parent_profile.user.fcm_device_token)
                )
                if parent_profile.user.fcm_device_token:
                    dispatch_fcm_push.delay("Ward Attendance Alert", f"Your ward was marked ABSENT for {instance.lecture.course.name}.", parent_profile.user.fcm_device_token)
        except Exception:
            pass # No linked parent profile found
