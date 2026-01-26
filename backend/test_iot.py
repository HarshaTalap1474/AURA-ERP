import requests
import json

# URL of your local server
url = "http://127.0.0.1:8000/api/attendance/scan/"

# The Fake ESP32 Packet
payload = {
    "device_id": "ESP_ROOM_101",
    "scans": ["2526B071"]  # The Roll No of the student you added
}

try:
    print(f"📡 Sending IoT Data to {url}...")
    response = requests.post(url, json=payload)
    
    print("\n✅ Server Response:")
    print(response.json())
except Exception as e:
    print(f"\n❌ Failed: {e}")