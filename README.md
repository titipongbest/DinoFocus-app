# DinoFocus

แอพ Pomodoro สำหรับ Android (Kotlin + Jetpack Compose) จับเวลาโฟกัส ทำรอบสำเร็จได้ไดโนเสาร์สะสม ยอมแพ้แล้วไดโนเสาร์กลายเป็นหลุมศพ

## ฟีเจอร์
- Start Focus · ยกเลิกได้ใน 10 วินาที · หลังจากนั้นต้อง "ยอมแพ้" (แตะสองครั้ง) ไดโนเสาร์ตายเป็นหลุมศพ
- ครบเวลาแล้วนับเวลาเกินต่อจนกด "หยุดและบันทึก" บันทึกเวลาเรียนจริง
- เลือกไดโนเสาร์จริง 12 ชนิด (ภาพพิกเซล) และ tag ของรอบ (เพิ่ม/ลบได้)
- หน้าพักสั้น: เริ่มพัก → ยกเลิก หรือ Start Focus
- เมนูสรุป: วันนี้ (นาที รอบ พื้นหญ้าไอโซเมตริก แยกตาม tag) และย้อนหลัง (กราฟทีละ 7 วัน ดูตาม tag พื้นหญ้ารายวัน)

## Build
GitHub Actions สร้าง APK ทุกครั้งที่ push ไป `main` ดาวน์โหลดได้จากหน้า Actions → run ล่าสุด → Artifacts → `DinoFocus-debug-apk`

ในเครื่อง: เปิดโฟลเดอร์นี้ด้วย Android Studio หรือรัน `./gradlew assembleDebug` (JDK 17)

### Release signing
ใส่ 2 ค่าใน GitHub → Settings → Secrets and variables → Actions:
- `DINOFOCUS_KEYSTORE_BASE64` — ไฟล์ `dinofocus-release.jks` แปลงเป็น base64
- `DINOFOCUS_PASSWORD` — รหัสผ่านเดียว ใช้ทั้ง keystore และ key (alias `dinofocus`)

ถ้ายังไม่ตั้ง secrets CI จะเซ็นด้วยคีย์ debug แทน ห้าม commit ไฟล์ .jks ลง repo

เลขเวอร์ชันเพิ่มอัตโนมัติตามจำนวน commit (v1.N) และทุก build เซ็นด้วยคีย์เดียวกัน (`app/dinofocus.keystore`) ติดตั้งเวอร์ชันใหม่ทับของเดิมได้โดยข้อมูลไม่หาย

by NorthXbar

ฟอนต์ Press Start 2P ใช้สัญญาอนุญาต SIL Open Font License
