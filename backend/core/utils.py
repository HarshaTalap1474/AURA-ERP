from Crypto.Cipher import AES
from Crypto.Util.Padding import unpad, pad
import base64

# --- SECURITY CONFIGURATION ---
# These keys MUST match exactly what we put in the ESP32 code later.
# Both must be exactly 16 bytes long.
AES_KEY = b'ProjectFortress1'  # 16 chars
AES_IV  = b'InitializationVt'  # 16 chars

class AESCipher:
    """
    Standard AES-128-CBC Decryption tool.
    Bridge between ESP32 (C++) and Django (Python).
    """
    
    @staticmethod
    def decrypt(encrypted_data_b64):
        """
        Input: Base64 String (e.g., "d3f4...")
        Output: Decrypted String (e.g., "STU12345|1706543210") or None.
        """
        try:
            # 1. Decode Base64 to get raw bytes
            encrypted_bytes = base64.b64decode(encrypted_data_b64)
            
            # 2. Initialize Cipher
            cipher = AES.new(AES_KEY, AES.MODE_CBC, AES_IV)
            
            # 3. Decrypt and Unpad
            decrypted_bytes = unpad(cipher.decrypt(encrypted_bytes), AES.block_size)
            
            # 4. Return as string
            return decrypted_bytes.decode('utf-8')
            
        except Exception as e:
            # If decryption fails (wrong key or hacked data), return None
            return None

    @staticmethod
    def encrypt(raw_text):
        """
        Helper for testing: Encrypts data just like the ESP32 would.
        """
        raw_bytes = raw_text.encode('utf-8')
        cipher = AES.new(AES_KEY, AES.MODE_CBC, AES_IV)
        encrypted_bytes = cipher.encrypt(pad(raw_bytes, AES.block_size))
        return base64.b64encode(encrypted_bytes).decode('utf-8')