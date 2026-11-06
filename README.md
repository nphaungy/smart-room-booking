# Smart Room Booking

ระบบค้นหา จอง และอนุมัติการใช้ห้องในมหาวิทยาลัย ประกอบด้วยแอป Android ที่พัฒนาด้วย Kotlin และ Jetpack Compose กับ REST API ที่พัฒนาด้วย FastAPI และ PostgreSQL

ฐานข้อมูลปฏิเสธการจองห้องเดียวกันในช่วงเวลาที่ทับกัน จึงป้องกันการจองซ้อนได้แม้มีผู้ใช้หลายคนกดจองพร้อมกัน

```text
smart-room-booking/
├── app/       แอป Android
└── backend/   REST API และฐานข้อมูล
```

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
| REST API | Python, FastAPI, Uvicorn |
| ฐานข้อมูลฝั่งเซิร์ฟเวอร์ | PostgreSQL 16 |
| การยืนยันตัวตน | JWT, bcrypt |

แอปรองรับ Android 8.0 ขึ้นไป (`minSdk 26`, `targetSdk 35`)

## การติดตั้งและรัน

```bash
git clone https://github.com/nphaungy/smart-room-booking.git
cd smart-room-booking
```

### 1. เปิด Backend

#### วิธี A: ใช้ Docker (แนะนำ)

ต้องมี Docker และพอร์ต `5432` กับ `8000` ต้องว่าง

```bash
cd backend
docker compose up --build
```

คำสั่งนี้เปิด PostgreSQL สร้างตาราง ใส่ข้อมูลตัวอย่าง และเปิด API ให้อัตโนมัติ

#### วิธี B: ใช้ Python และ PostgreSQL ในเครื่อง

ต้องมี Python และ PostgreSQL 16 จากนั้นสร้างฐานข้อมูลชื่อ `roombooking` แล้วรัน

```bash
cd backend
python -m venv .venv
source .venv/bin/activate    # Windows: .venv\Scripts\activate
pip install -r requirements.txt

# Windows PowerShell: $env:DATABASE_URL="postgresql://postgres:<รหัสผ่าน>@localhost:5432/roombooking"
export DATABASE_URL="postgresql://postgres:<รหัสผ่าน>@localhost:5432/roombooking"

python -m app.seed           # สร้างตารางและใส่ข้อมูลตัวอย่าง
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

เมื่อ API ทำงานแล้ว เปิด <http://localhost:8000/docs> เพื่อดูและทดลองเรียก API ผ่าน Swagger UI

### 2. บัญชีสำหรับทดสอบ

ข้อมูลตัวอย่างสร้างบัญชีเหล่านี้ให้

| บทบาท | อีเมล | รหัสผ่าน |
|---|---|---|
| ผู้ดูแลระบบ | `admin@example.com` | `Admin1234` |
| ผู้อนุมัติ | `approver@example.com` | `Approver1234` |
| ผู้จอง | `student1@example.com` | `Student1234` |
| ผู้จอง | `student2@example.com` | `Student1234` |

> **คำเตือน:** บัญชีเหล่านี้ใช้สำหรับทดสอบในเครื่องเท่านั้น ห้ามรันข้อมูลตัวอย่างบนเซิร์ฟเวอร์จริง

### 3. รันแอป Android

ต้องมีเครื่องมือต่อไปนี้

- Android Studio รุ่นที่รองรับ Android Gradle Plugin 8.13
- Android SDK 35
- JDK 17 ขึ้นไป (ใช้ JDK ที่มากับ Android Studio ได้)

#### รันบน Emulator

1. เปิดโฟลเดอร์ `smart-room-booking` ด้วย Android Studio แล้วรอ Gradle Sync ให้เสร็จ
2. กด **Run** แอปจะเชื่อมต่อไปที่ `http://10.0.2.2:8000/api/` ซึ่งหมายถึง `localhost` ของคอมพิวเตอร์

#### รันบนมือถือจริง

ให้มือถือและคอมพิวเตอร์อยู่ใน Wi-Fi เดียวกัน แล้วแก้ 2 จุดเป็น IP ของคอมพิวเตอร์ เช่น `192.168.1.50`

1. `app/build.gradle.kts`

   ```kotlin
   buildConfigField("String", "BASE_URL", "\"http://192.168.1.50:8000/api/\"")
   ```

2. `app/src/main/res/xml/network_security_config.xml`

   ```xml
   <domain includeSubdomains="false">192.168.1.50</domain>
   ```

## การทดสอบ

### แอป Android

```bash
./gradlew :app:assembleDebug       # สร้าง APK
./gradlew :app:testDebugUnitTest   # รัน unit test
```

บน Windows ให้ใช้ `gradlew.bat` แทน `./gradlew`

### Backend

> **คำเตือน:** ชุดทดสอบจะลบข้อมูลทั้งหมดในฐานข้อมูลที่ `DATABASE_URL` ชี้อยู่ทุกครั้งที่รัน จึงต้องใช้ฐานข้อมูลแยกสำหรับทดสอบ ห้ามชี้ไปที่ `roombooking`

ถ้าเปิดฐานข้อมูลด้วย Docker อยู่แล้ว ให้ติดตั้ง dependencies ตามวิธี B แล้วรัน

```bash
cd backend
docker compose exec db createdb -U postgres roombooking_test

# Windows PowerShell: $env:DATABASE_URL="postgresql://postgres:postgres@localhost:5432/roombooking_test"
export DATABASE_URL="postgresql://postgres:postgres@localhost:5432/roombooking_test"

pytest -v
```

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

backend/
├── app/
│   ├── routers/        เส้นทาง API
│   ├── services/       ตรรกะการจอง สถิติ และการแจ้งเตือน
│   ├── config.py       ค่าตั้งค่าและกติกาการจอง
│   ├── schema.sql      โครงสร้างฐานข้อมูล
│   └── seed.py         ข้อมูลตัวอย่าง
├── tests/              ชุดทดสอบ
├── docker-compose.yml
└── openapi.json        รายละเอียด API
```

## การนำไปใช้งานจริง

ค่าใน repo นี้เหมาะสำหรับพัฒนาในเครื่องเท่านั้น ก่อนนำไปใช้จริงต้องทำอย่างน้อยดังนี้

- ตั้ง `JWT_SECRET` เป็นค่าสุ่มที่ยาวและเก็บเป็นความลับ ไม่เช่นนั้นคนอื่นอาจปลอม token เข้าระบบได้
- เปลี่ยนรหัสผ่านฐานข้อมูลใน `DATABASE_URL`
- ไม่รัน `app.seed` เพราะจะสร้างบัญชีสาธิตที่มีรหัสผ่านเปิดเผย
- เปลี่ยน `BASE_URL` ของแอปเป็นที่อยู่จริงแบบ HTTPS

## ข้อจำกัด

- การแจ้งเตือนดึงข้อมูลจากเซิร์ฟเวอร์ทุก 15 นาที และทุกครั้งที่เปิดแอป จึงไม่ใช่การแจ้งเตือนแบบทันที
- กติกาการจอง ได้แก่ ช่วงละ 30 นาที, เปิด 08:00–20:00 น. และจองล่วงหน้าได้ไม่เกิน 30 วัน ถูกกำหนดไว้ทั้งใน `backend/app/config.py` และ `app/src/main/java/com/example/roombooking/domain/BookingRules.kt` ถ้าเปลี่ยนกติกา ต้องแก้ทั้งสองไฟล์ให้ตรงกัน

## ฟอนต์

แอปใช้ฟอนต์ [Prompt](https://fonts.google.com/specimen/Prompt) และ [Sarabun](https://fonts.google.com/specimen/Sarabun) ภายใต้สัญญาอนุญาต SIL Open Font License 1.1 ดูรายละเอียดได้ที่ `app/src/main/assets/licenses/`
