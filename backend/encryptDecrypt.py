from core.utils import AESCipher
import base64

# --- PASTE CODE FROM PHONE BELOW ---
android_code = "OGmJ/SOTYZZdnbLykbbw2Q=="

# 1. Try Standard Decrypt
print(f"Decrypting: {android_code}")
result = AESCipher.decrypt(android_code)

if result:
    print(f"✅ SUCCESS! Decrypted: {result}")
    print("The system is READY for the ESP32.")
else:
    print("❌ FAILED. Keys still don't match.")
    # Debug Helper: Check what raw bytes we have
    try:
        raw = base64.b64decode(android_code)
        print(f"Raw Hex received: {raw.hex()}")
    except:
        print("Invalid Base64 string.")
        
