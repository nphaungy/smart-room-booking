# Smart Room Booking

แอป Android สำหรับค้นหา จอง และอนุมัติการใช้ห้องในมหาวิทยาลัย พัฒนาด้วย Kotlin และ Jetpack Compose

> **หมายเหตุ:** repo นี้มีเฉพาะแอป Android แอปต้องเชื่อมต่อกับ REST API ซึ่งไม่ได้อยู่ใน repo นี้ จึงต้องมีเซิร์ฟเวอร์ที่ทำงานอยู่ก่อนจึงจะเข้าสู่ระบบและใช้งานได้

## ความสามารถ

| บทบาท | สิ่งที่ทำได้ |
|---|---|
| **ผู้จอง** | สมัครสมาชิกและเข้าสู่ระบบ, ค้นหาห้องว่างตามวัน เวลา จำนวนคน และประเภทห้อง, ดูตารางห้อง, จอง แก้ไข และยกเลิกการจอง, รับการแจ้งเตือน |
| **ผู้อนุมัติ** | ทุกอย่างของผู้จอง, อนุมัติหรือปฏิเสธคำขอ, ดูสถิติการใช้ห้อง |
| **ผู้ดูแลระบบ** | ทุกอย่างของผู้อนุมัติ, จัดการห้อง อุปกรณ์ ช่วงปิดปรับปรุง และสิทธิ์ผู้ใช้ |

ความสามารถอื่น ๆ:

- ดูรายการห้อง การจองของฉัน และการแจ้งเตือนที่บันทึกไว้ได้เมื่อไม่มีอินเทอร์เน็ต
- แจ้งเตือนเมื่อมีคำขอใหม่ เมื่อสถานะการจองเปลี่ยน และก่อนถึงเวลาใช้ห้อง
- แตะการแจ้งเตือนเพื่อเปิดการจองนั้นได้ทันที
- รองรับโหมดสว่างและโหมดมืด พร้อม animation ทั่วทั้งแอป

## เทคโนโลยี

| ส่วนประกอบ | ที่ใช้ |
|---|---|
| ภาษาและ UI | Kotlin 2.1, Jetpack Compose (BOM 2024.12), Material 3 |
| สถาปัตยกรรม | MVVM + Repository |
| ฐานข้อมูลในเครื่อง | Room 2.6 |
| เครือข่าย | Retrofit 2.11, OkHttp |
| งานเบื้องหลัง | Kotlin Coroutines, WorkManager 2.10 |
| การนำทาง | Navigation Compose |
| เก็บข้อมูลเซสชัน | DataStore Preferences |

รองรับ Android 8.0 ขึ้นไป (`minSdk 26`, `targetSdk 35`)

## การติดตั้งและรัน

### สิ่งที่ต้องมี

- Android Studio รุ่นที่รองรับ Android Gradle Plugin 8.13
- Android SDK 35
- JDK 17 ขึ้นไป (ใช้ JDK ที่มากับ Android Studio ได้)
- REST API ที่เปิดอยู่ที่พอร์ต `8000`

### รันบน Emulator

1. Clone โปรเจกต์แล้วเปิดโฟลเดอร์ด้วย Android Studio

   ```bash
   git clone https://github.com/nphaungy/smart-room-booking.git
   ```

2. รอ Gradle Sync ให้เสร็จ
3. เปิด REST API บนคอมพิวเตอร์เครื่องเดียวกัน
4. กด **Run** แอปจะเชื่อมต่อไปที่ `http://10.0.2.2:8000/api/` ซึ่งหมายถึง `localhost` ของคอมพิวเตอร์

### รันบนมือถือจริง

ให้มือถือและคอมพิวเตอร์อยู่ใน Wi-Fi เดียวกัน แล้วแก้ 2 จุดเป็น IP ของคอมพิวเตอร์ เช่น `192.168.1.50`

1. `app/build.gradle.kts`

   ```kotlin
   buildConfigField("String", "BASE_URL", "\"http://192.168.1.50:8000/api/\"")
   ```

2. `app/src/main/res/xml/network_security_config.xml`

   ```xml
   <domain includeSubdomains="false">192.168.1.50</domain>
   ```

### Build และทดสอบจาก command line

```bash
./gradlew :app:assembleDebug       # สร้าง APK
./gradlew :app:testDebugUnitTest   # รัน unit test
```

บน Windows ให้ใช้ `gradlew.bat` แทน `./gradlew`

## โครงสร้างโปรเจกต์

```text
app/src/main/java/com/example/roombooking/
├── data/
│   ├── local/          ฐานข้อมูล Room
│   ├── remote/         Retrofit API และ DTO
│   └── repository/     รวมข้อมูลจาก API และฐานข้อมูลในเครื่อง
├── domain/             โมเดลและกติกาการจอง
├── notification/       การแจ้งเตือนและงาน WorkManager
└── ui/
    ├── common/         component และ animation ที่ใช้ร่วมกัน
    ├── navigation/     เส้นทางระหว่างหน้าจอ
    ├── theme/          สีและฟอนต์
    └── ...             หน้าจอแยกตามฟีเจอร์
```

## ข้อจำกัด

- การแจ้งเตือนดึงข้อมูลจากเซิร์ฟเวอร์ทุก 15 นาที และทุกครั้งที่เปิดแอป จึงไม่ใช่การแจ้งเตือนแบบทันที
- กติกาการจอง ได้แก่ ช่วงละ 30 นาที, เปิด 08:00–20:00 น. และจองล่วงหน้าได้ไม่เกิน 30 วัน อยู่ใน `domain/BookingRules.kt` ถ้าเซิร์ฟเวอร์เปลี่ยนกติกา ต้องแก้ไฟล์นี้ให้ตรงกันด้วย

## ฟอนต์

แอปใช้ฟอนต์ [Prompt](https://fonts.google.com/specimen/Prompt) และ [Sarabun](https://fonts.google.com/specimen/Sarabun) ภายใต้สัญญาอนุญาต SIL Open Font License 1.1 ดูรายละเอียดได้ที่ `app/src/main/assets/licenses/`
