import requests
import sys
import os
import django

# --- SETUP DJANGO ENVIRONMENT FOR UTILS ---
# We need this to use AESCipher from core/utils
sys.path.append(os.getcwd())
os.environ.setdefault("DJANGO_SETTINGS_MODULE", "ble_attendance.settings")
django.setup()

from core.utils import AESCipher

# --- CONFIGURATION ---
URL = "http://127.0.0.1:8000/api/attendance/scan/"
API_KEY = "secret_key_123"

# --- DATA GENERATION ---
# 1. Create the Raw Data (Matches what Android sends: "R_01|Timestamp")
# Note: Ensure "roll_101" exists in your DB, or "R_01" if you shortened it.
raw_identity = "roll_B069|1700000000" 

print(f"🔒 Encrypting Identity: {raw_identity}")

# 2. Encrypt it using the Server's own utility
# This ensures the keys match perfectly for the test
encrypted_payload = AESCipher.encrypt(raw_identity)

print(f"📦 Payload: {encrypted_payload}")

# --- SEND REQUEST ---
headers = {
    "X-ESP32-API-KEY": API_KEY,
    "Content-Type": "application/json"
}

# The ESP32 sends a JSON object with a list of scans
data = {
    "scans": [encrypted_payload]
}

print(f"\n🚀 Sending to {URL}...")
try:
    response = requests.post(URL, json=data, headers=headers)
    
    print(f"📡 Status Code: {response.status_code}")
    print(f"📄 Response: {response.text}")
    
    if response.status_code == 200:
        print("\n✅ SUCCESS: Attendance Marked!")
        print("Go check http://127.0.0.1:8000/dashboard/ to see it live.")
    else:
        print("\n❌ FAILED. Check server logs.")

except requests.exceptions.ConnectionError:
    print("\n❌ ERROR: Could not connect to server.")
    print("Make sure 'python manage.py runserver' is running in another terminal.")