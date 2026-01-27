import requests

# URL of your local server
url = "http://127.0.0.1:8000/api/auth/login/"

def try_login(username, password):
    print(f"\n🔑 Attempting Login: {username} ...")
    try:
        response = requests.post(url, json={
            "username": username,
            "password": password
        })
        
        # 🔍 DEBUGGING BLOCK: Check if we got an error page
        if response.status_code != 200:
            print(f"❌ Server Error (Status {response.status_code})")
            print("   Response Text (First 200 chars):")
            print(f"   {response.text[:200]}...") # Prints the HTML error
            return

        data = response.json()
        print(f"✅ SUCCESS!")
        print(f"   Detected Role: {data['role']}")

    except Exception as e:
        print(f"⚠️ Script Error: {e}")
        
# --- TEST CASES ---
if __name__ == "__main__":
    # 1. Test a Student (Should return "STUDENT")
    try_login("roll_B069", "Pass@123")  # Use the password you set earlier

    # 2. Test a Teacher (Should return "TEACHER")
    try_login("prof_smith", "Pass@123")

    # 3. Test Invalid User
    try_login("hacker_123", "wrongpass")