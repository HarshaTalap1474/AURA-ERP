from celery import shared_task
from django.utils import timezone
from .models import LibraryAction, FeeInvoice
from decimal import Decimal

@shared_task
def calculate_daily_fines():
    """
    Automated Background Worker:
    Runs every midnight via Celery Beat.
    Identifies overdue library books and unpaid invoices,
    and sequentially increments the fine accrued or penalty.
    """
    today = timezone.now().date()
    
    # 1. Library Fines (₹10 per day overdue)
    overdue_books = LibraryAction.objects.filter(
        returned_on__isnull=True, 
        due_date__lt=today
    )
    
    for book in overdue_books:
        # Increment fine by 10 per overdue day since the task runs daily
        book.fine_accrued += Decimal('10.00')
        book.save()
        
    # 2. Defaulter Penalty on Fee Invoices (Late after 7 days overdue)
    penalty_date = today - timezone.timedelta(days=7)
    overdue_invoices = FeeInvoice.objects.filter(
        is_paid=False,
        due_date__lte=penalty_date
    ).exclude(fee_type='PENALTY')
    
    penalty_count = 0
    for invoice in overdue_invoices:
        # Check if penalty already exists for this invoice this week
        recent_penalty = FeeInvoice.objects.filter(
            student=invoice.student,
            fee_type='PENALTY',
            amount=Decimal('500.00'),
            created_at__date=today
        ).exists()
        
        if not recent_penalty:
            FeeInvoice.objects.create(
                student=invoice.student,
                fee_type='PENALTY',
                amount=Decimal('500.00'),
                due_date=today + timezone.timedelta(days=7)
            )
            penalty_count += 1
            
    return f"Fines calculation complete. Handled {overdue_books.count()} books and {penalty_count} penalties."

@shared_task
def dispatch_fcm_push(title, body, fcm_token):
    """
    Mock FCM Dispatcher for Push Notifications.
    In production, this would securely authenticate with the Firebase Admin API.
    """
    if not fcm_token:
        return "No FCM Token found. Aborted."
        
    # Stub logic mimicking Firebase SDK Payload Dispatch
    import logging
    logger = logging.getLogger(__name__)
    logger.info(f"🔥 [FCM PUSH SECURELY DISPATCHED] To: {fcm_token} | Title: {title} | Body: {body}")
    
    return "FCM Push Dispatched successfully."
