#include <Arduino.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEScan.h>
#include <BLEAdvertisedDevice.h>
#include <set>

// ==========================================
// 1. CONFIGURATION
// ==========================================
const char* WIFI_SSID     = "Room_6_7";
const char* WIFI_PASSWORD = "Suraj@123";

// UPDATE THIS with your PC's IP address (e.g., 192.168.1.10:8000)
// The endpoint matches your new finalized specs
const char* SERVER_URL    = "https://command-hay-inquire-moderator.trycloudflare.com/api/attendance/hardware-sync/";

// The Service UUID to filter (Aura Attendix App)
const char* TARGET_SERVICE_UUID = "0000b81d-0000-1000-8000-00805f9b34fb";

// Batching Settings
const int SCAN_TIME = 5;       // Scan duration (seconds)
const int UPLOAD_INTERVAL = 10000; // Upload every 10 seconds

// ==========================================
// 2. GLOBALS
// ==========================================
BLEScan* pBLEScan;
std::set<String> detectedStudents; // Set handles debouncing automatically
unsigned long lastUploadTime = 0;
String myMacAddress;

// ==========================================
// 3. BLE CALLBACKS
// ==========================================
class MyAdvertisedDeviceCallbacks: public BLEAdvertisedDeviceCallbacks {
    void onResult(BLEAdvertisedDevice advertisedDevice) {
      // STRICT FILTER: Only process devices with the Aura Service UUID
      if (advertisedDevice.haveServiceUUID() && advertisedDevice.isAdvertisingService(BLEUUID(TARGET_SERVICE_UUID))) {
        
        String studentId = "";

        // STRATEGY 1: Extract from Manufacturer Data (Preferred)
        if (advertisedDevice.haveManufacturerData()) {
            // FIX FOR ESP32 CORE 3.x: Returns String directly now
            String data = advertisedDevice.getManufacturerData();
            studentId = data; 
        }
        // STRATEGY 2: Fallback to Device Name if Manuf Data is empty
        else {
             studentId = advertisedDevice.getName().c_str();
        }

        // Add to buffer (Set prevents duplicates)
        if (studentId.length() > 0) {
            Serial.print("Filtered & Found: ");
            Serial.println(studentId);
            detectedStudents.insert(studentId);
        }
      }
    }
};

// ==========================================
// 4. NETWORK & API
// ==========================================
void connectToWiFi() {
  if (WiFi.status() == WL_CONNECTED) return;

  Serial.print("Connecting to WiFi");
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 20) {
    delay(500);
    Serial.print(".");
    attempts++;
  }
  
  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\nWiFi Connected!");
    myMacAddress = WiFi.macAddress();
    Serial.print("Gateway ID (MAC): "); Serial.println(myMacAddress);
  } else {
    Serial.println("\nWiFi Connection Failed.");
  }
}

void uploadData() {
  // If buffer is empty, skip upload (save bandwidth)
  if (detectedStudents.empty()) return;

  if (WiFi.status() != WL_CONNECTED) {
    connectToWiFi();
    return;
  }

  HTTPClient http;
  http.begin(SERVER_URL);
  http.addHeader("Content-Type", "application/json");

  // Create JSON Payload based on FINALIZED SPECS
  StaticJsonDocument<2048> doc;
  doc["gateway_id"] = myMacAddress;
  
  JsonArray studentsArray = doc.createNestedArray("detected_students");
  for (auto const& id : detectedStudents) {
    studentsArray.add(id);
  }

  String requestBody;
  serializeJson(doc, requestBody);

  Serial.println("Pushing Batch: " + requestBody);

  int httpResponseCode = http.POST(requestBody);

  if (httpResponseCode > 0) {
    String response = http.getString();
    Serial.print("Server Response: ");
    Serial.println(httpResponseCode);
    
    // Clear buffer only on successful transmission
    detectedStudents.clear(); 
  } else {
    Serial.print("Error sending POST: ");
    Serial.println(httpResponseCode);
  }

  http.end();
}

// ==========================================
// 5. SETUP & LOOP
// ==========================================
void setup() {
  Serial.begin(115200);
  Serial.println("Aura ESP32 Gateway - Starting...");

  connectToWiFi();

  BLEDevice::init("Aura_Gateway_01");
  pBLEScan = BLEDevice::getScan();
  pBLEScan->setAdvertisedDeviceCallbacks(new MyAdvertisedDeviceCallbacks());
  pBLEScan->setActiveScan(true); 
  pBLEScan->setInterval(100);    
  pBLEScan->setWindow(99);       
}

void loop() {
  // 1. SCAN
  // FIX FOR ESP32 CORE 3.x: start() returns a pointer now (*)
  BLEScanResults *foundDevices = pBLEScan->start(SCAN_TIME, false);
  
  // Clean up scan results to free memory
  pBLEScan->clearResults();

  // 2. UPLOAD CHECK
  if (millis() - lastUploadTime > UPLOAD_INTERVAL) {
    uploadData();
    lastUploadTime = millis();
  }
  
  delay(50);
}