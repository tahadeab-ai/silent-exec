#!/usr/bin/env python3
"""
SilentExec Deterministic Bilingual Dataset Generator
Generates ChatML JSONL datasets (train/val/test) for training zero-chatter OS execution agents.
Enforces 100% schema validation against schemas/action_schema.json using jsonschema.
Supports rich entity mapping between Arabic/English mentions and canonical OS targets.
"""

import argparse
import json
import random
import sys
from pathlib import Path
from typing import Any, Dict, List, Tuple
import jsonschema

SYSTEM_PROMPT = (
    "You are SilentExec, a zero-chatter OS execution agent. "
    "Emit only a valid ActionCommand JSON matching the strict schema. "
    "No conversational responses."
)

ACTIONS = [
    "open_folder",
    "open_file",
    "launch_app",
    "search_files",
    "set_volume",
    "set_brightness",
    "create_note",
    "copy_to_clipboard",
    "delete_file",
    "move_file",
    "system_power",
    "open_android_app",
    "toggle_setting",
    "send_message",
    "open_url",
]

# Canonical entity and alias dictionaries for natural language extraction
FOLDER_TARGET_PAIRS: List[Tuple[str, str]] = [
    ("المشاريع", "Projects"),
    ("مجلد المشاريع", "Projects"),
    ("التنزيلات", "Downloads"),
    ("مجلد التنزيلات", "Downloads"),
    ("الداونلودز", "Downloads"),
    ("المستندات", "Documents"),
    ("مجلد المستندات", "Documents"),
    ("الدوكيومنتس", "Documents"),
    ("الصور", "Pictures"),
    ("مجلد الصور", "Pictures"),
    ("الصور الشخصية", "Pictures"),
    ("سطح المكتب", "Desktop"),
    ("الديسكتوب", "Desktop"),
    ("الموسيقى", "Music"),
    ("الفيديوهات", "Videos"),
    ("النسخ الاحتياطية", "Backups"),
    ("الأرشيف", "Archive"),
    ("التقارير", "Reports"),
    ("قاعدة البيانات", "Database"),
    ("ملفات الكود", "Source"),
    ("المستندات المالية", "Documents/Finance"),
    ("مشاريع العمل", "Projects/Work"),
    ("Projects", "Projects"),
    ("Downloads", "Downloads"),
    ("Documents", "Documents"),
    ("Pictures", "Pictures"),
    ("Desktop", "Desktop"),
    ("Music", "Music"),
    ("Videos", "Videos"),
    ("Backups", "Backups"),
    ("Archive", "Archive"),
    ("Reports", "Reports"),
    ("Projects/Rust", "Projects/Rust"),
    ("Downloads/Iso", "Downloads/Iso"),
    ("Documents/Invoices", "Documents/Invoices"),
    ("Desktop/Notes", "Desktop/Notes"),
    ("Temp", "Temp"),
    ("Logs", "Logs"),
]

FILE_TARGET_PAIRS: List[Tuple[str, str]] = [
    ("مشروع التخرج", "graduation_project.docx"),
    ("تقرير المبيعات", "sales_report.xlsx"),
    ("فاتورة الكهرباء", "electricity_bill.pdf"),
    ("ملف الحسابات", "accounts.xlsx"),
    ("العرض التقديمي", "presentation.pptx"),
    ("ملخص الاجتماع", "meeting_notes.txt"),
    ("عقد العمل", "contract.pdf"),
    ("ملف البيانات", "dataset.csv"),
    ("ملف الإعدادات", "config.json"),
    ("ملف السجلات", "system.log"),
    ("كود البايثون", "script.py"),
    ("ملف الراست", "main.rs"),
    ("صورة الهوية", "id_card.png"),
    ("تقرير شهر مارس", "march_report.pdf"),
    ("الميزانية السنوية", "budget_2026.xlsx"),
    ("فاتورة المشتريات", "purchase_invoice.pdf"),
    ("دراسة الجدوى", "feasibility_study.pdf"),
    ("قائمة العملاء", "clients.xlsx"),
    ("ملخص_الدراسة.pdf", "ملخص_الدراسة.pdf"),
    ("تقرير_2026.docx", "تقرير_2026.docx"),
    ("بيانات_المستخدمين.csv", "بيانات_المستخدمين.csv"),
    ("الخطة_التشغيلية.docx", "الخطة_التشغيلية.docx"),
    ("sales report", "sales_report.xlsx"),
    ("graduation project", "graduation_project.docx"),
    ("quarterly budget", "budget_q1.xlsx"),
    ("client proposal", "proposal.docx"),
    ("server log", "server.log"),
    ("database dump", "backup.sql"),
    ("readme file", "README.md"),
    ("dockerfile", "Dockerfile"),
    ("resume pdf", "resume.pdf"),
    ("test results", "results.json"),
    ("invoice.pdf", "invoice.pdf"),
    ("data.csv", "data.csv"),
    ("app.log", "app.log"),
    ("presentation.pptx", "presentation.pptx"),
    ("financial_audit.xlsx", "financial_audit.xlsx"),
    ("terms_of_service.pdf", "terms_of_service.pdf"),
]

PC_APP_TARGET_PAIRS: List[Tuple[str, str]] = [
    ("المتصفح", "Chrome"),
    ("متصفح كروم", "Chrome"),
    ("جوجل كروم", "Chrome"),
    ("محرر الأكواد", "VS Code"),
    ("فيجوال استوديو كود", "VS Code"),
    ("المفكرة", "Notepad"),
    ("النوت باد", "Notepad"),
    ("الآلة الحاسبة", "Calculator"),
    ("الحاسبة", "Calculator"),
    ("الطرفية", "Terminal"),
    ("التيرمينال", "Terminal"),
    ("السبوتيفاي", "Spotify"),
    ("سلاك", "Slack"),
    ("ديسكورد", "Discord"),
    ("برنامج الضغط", "WinRAR"),
    ("إكسل", "Excel"),
    ("الوورد", "Word"),
    ("Chrome", "Chrome"),
    ("VS Code", "VS Code"),
    ("Firefox", "Firefox"),
    ("Terminal", "Terminal"),
    ("Spotify", "Spotify"),
    ("Notepad", "Notepad"),
    ("Calculator", "Calculator"),
    ("Slack", "Slack"),
    ("Discord", "Discord"),
    ("Telegram Desktop", "Telegram Desktop"),
    ("Obsidian", "Obsidian"),
    ("VLC", "VLC"),
]

ANDROID_APP_TARGET_PAIRS: List[Tuple[str, str]] = [
    ("الواتساب", "WhatsApp"),
    ("تطبيق الواتس", "WhatsApp"),
    ("التليجرام", "Telegram"),
    ("تطبيق التلغرام", "Telegram"),
    ("الكاميرا", "Camera"),
    ("كاميرا الجوال", "Camera"),
    ("المعرض", "Gallery"),
    ("استوديو الصور", "Gallery"),
    ("الاستوديو", "Gallery"),
    ("اليوتيوب", "YouTube"),
    ("تطبيق اليوتيوب", "YouTube"),
    ("الضبط", "Settings"),
    ("الإعدادات", "Settings"),
    ("خرائط قوقل", "Google Maps"),
    ("الخرائط", "Google Maps"),
    ("الجيميل", "Gmail"),
    ("الإيميل", "Gmail"),
    ("المتجر", "Play Store"),
    ("متجر بلاي", "Play Store"),
    ("حاسبة الجوال", "Calculator"),
    ("ساعة المنبه", "Clock"),
    ("جهات الاتصال", "Contacts"),
    ("WhatsApp", "WhatsApp"),
    ("Telegram", "Telegram"),
    ("Camera", "Camera"),
    ("Gallery", "Gallery"),
    ("Settings", "Settings"),
    ("YouTube", "YouTube"),
    ("Google Maps", "Google Maps"),
    ("Gmail", "Gmail"),
    ("Play Store", "Play Store"),
]

URL_TARGET_PAIRS: List[Tuple[str, str]] = [
    ("موقع قوقل", "https://google.com"),
    ("محرك بحث قوقل", "https://google.com"),
    ("اليوتيوب", "https://youtube.com"),
    ("موقع يوتيوب", "https://youtube.com"),
    ("جيت هاب", "https://github.com"),
    ("مستودع المشروع", "https://github.com/tahadeab-ai/silent-exec"),
    ("ويكيبيديا", "https://wikipedia.org"),
    ("موسوعة ويكيبيديا", "https://wikipedia.org"),
    ("كاغل", "https://kaggle.com"),
    ("أرشيف الأبحاث", "https://arxiv.org"),
    ("تويتر / إكس", "https://x.com"),
    ("موقع الأخبار", "https://news.ycombinator.com"),
    ("ريديت", "https://reddit.com"),
    ("Google", "https://google.com"),
    ("GitHub", "https://github.com"),
    ("YouTube", "https://youtube.com"),
    ("Wikipedia", "https://wikipedia.org"),
    ("Kaggle", "https://kaggle.com"),
    ("ArXiv", "https://arxiv.org"),
    ("https://google.com", "https://google.com"),
    ("https://github.com", "https://github.com"),
    ("https://youtube.com", "https://youtube.com"),
    ("https://huggingface.co", "https://huggingface.co"),
    ("https://stackoverflow.com", "https://stackoverflow.com"),
]

LEVEL_MENTIONS: List[Tuple[str, str, int]] = [
    ("عشرة", "10", 10),
    ("عشرين", "20", 20),
    ("خمسة وعشرين", "25", 25),
    ("ثلاثين", "30", 30),
    ("أربعين", "40", 40),
    ("خمسين", "50", 50),
    ("ستين", "60", 60),
    ("سبعين", "70", 70),
    ("خمسة وسبعين", "75", 75),
    ("ثمانين", "80", 80),
    ("تسعين", "90", 90),
    ("مية", "100", 100),
    ("10%", "10", 10),
    ("20%", "20", 20),
    ("25%", "25", 25),
    ("30%", "30", 30),
    ("40%", "40", 40),
    ("50%", "50", 50),
    ("60%", "60", 60),
    ("70%", "70", 70),
    ("75%", "75", 75),
    ("80%", "80", 80),
    ("90%", "90", 90),
    ("100%", "100", 100),
]

SETTINGS_PAIRS: List[Tuple[str, str, str]] = [
    ("الواي فاي", "wifi", "off"),
    ("الواي فاي", "wifi", "on"),
    ("شبكة الوايرلس", "wifi", "off"),
    ("البلوتوث", "bluetooth", "on"),
    ("البلوتوث", "bluetooth", "off"),
    ("كشاف الجوال", "flashlight", "on"),
    ("الفلاش", "flashlight", "off"),
    ("بيانات الجوال", "mobile_data", "off"),
    ("بيانات الجوال", "mobile_data", "on"),
    ("نت الشريحة", "mobile_data", "off"),
    ("وضع الطيران", "airplane_mode", "on"),
    ("نمط الطيران", "airplane_mode", "off"),
    ("نقطة الاتصال", "hotspot", "on"),
    ("الهوت سبوت", "hotspot", "off"),
    ("تحديد الموقع", "location", "on"),
    ("الموقع", "location", "off"),
    ("وضع الصامت", "dnd", "on"),
    ("عدم الإزعاج", "dnd", "off"),
    ("wifi", "wifi", "off"),
    ("wifi", "wifi", "on"),
    ("bluetooth", "bluetooth", "on"),
    ("bluetooth", "bluetooth", "off"),
    ("flashlight", "flashlight", "on"),
    ("flashlight", "flashlight", "off"),
    ("mobile data", "mobile_data", "off"),
    ("airplane mode", "airplane_mode", "on"),
    ("hotspot", "hotspot", "on"),
    ("location", "location", "off"),
    ("do not disturb", "dnd", "on"),
]


# Linguistic Templates
TEMPLATES = {
    "open_folder": {
        "colloquial": [
            ("ادخل مجلد {mention}", {"action": "open_folder", "target": "{target}", "platform": "pc"}),
            ("افتح لي فولدر {mention} دحين", {"action": "open_folder", "target": "{target}", "platform": "pc"}),
            ("شوف لي ايش بمجلد {mention}", {"action": "open_folder", "target": "{target}", "platform": "pc"}),
            ("فك لي ملفات {mention} يا غالي", {"action": "open_folder", "target": "{target}", "platform": "pc"}),
            ("ادخل على دايركتوري {mention}", {"action": "open_folder", "target": "{target}", "platform": "pc"}),
            ("افتح فولدر {mention} بسرعة", {"action": "open_folder", "target": "{target}", "platform": "pc"}),
        ],
        "msa": [
            ("افتح مجلد {mention}", {"action": "open_folder", "target": "{target}", "platform": "pc"}),
            ("قم بالدخول إلى مجلد {mention}", {"action": "open_folder", "target": "{target}", "platform": "pc"}),
            ("استعرض محتويات مجلد {mention}", {"action": "open_folder", "target": "{target}", "platform": "pc"}),
            ("تصفح المجلد المسمى {mention}", {"action": "open_folder", "target": "{target}", "platform": "pc"}),
            ("انتقل إلى دليل {mention}", {"action": "open_folder", "target": "{target}", "platform": "pc"}),
        ],
        "english": [
            ("open folder {mention}", {"action": "open_folder", "target": "{target}", "platform": "pc"}),
            ("navigate to folder {mention}", {"action": "open_folder", "target": "{target}", "platform": "pc"}),
            ("open directory {mention}", {"action": "open_folder", "target": "{target}", "platform": "pc"}),
            ("browse to folder {mention}", {"action": "open_folder", "target": "{target}", "platform": "pc"}),
            ("open the {mention} directory", {"action": "open_folder", "target": "{target}", "platform": "pc"}),
            ("explore folder {mention}", {"action": "open_folder", "target": "{target}", "platform": "pc"}),
        ],
        "adversarial": [
            ("ودني للملفات القديمة مدري وين راحت", {"action": "open_folder", "target": "Documents", "platform": "pc"}),
            ("فولدر المشاريع حق الأسبوع اللي فات", {"action": "open_folder", "target": "Projects", "platform": "pc"}),
            ("where are my downloaded files located?", {"action": "open_folder", "target": "Downloads", "platform": "pc"}),
            ("افتح مكان الحفظ الافتراضي", {"action": "open_folder", "target": "Downloads", "platform": "pc"}),
        ],
    },
    "open_file": {
        "colloquial": [
            ("افتح لي ملف {mention}", {"action": "open_file", "target": "{target}", "platform": "pc"}),
            ("شغل ملف {mention} بسرعة", {"action": "open_file", "target": "{target}", "platform": "pc"}),
            ("فك لي تقرير {mention} من مجلد {folder}", {"action": "open_file", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
            ("وريني ملف {mention} دحين", {"action": "open_file", "target": "{target}", "platform": "pc"}),
            ("افتح الورقة حق {mention}", {"action": "open_file", "target": "{target}", "platform": "pc"}),
        ],
        "msa": [
            ("قم بفتح الملف {mention}", {"action": "open_file", "target": "{target}", "platform": "pc"}),
            ("افتح مستند {mention}", {"action": "open_file", "target": "{target}", "platform": "pc"}),
            ("اعرض ملف {mention} الموجود في مجلد {folder}", {"action": "open_file", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
            ("قم بعرض مستند {mention}", {"action": "open_file", "target": "{target}", "platform": "pc"}),
        ],
        "english": [
            ("open file {mention}", {"action": "open_file", "target": "{target}", "platform": "pc"}),
            ("view document {mention}", {"action": "open_file", "target": "{target}", "platform": "pc"}),
            ("open document {mention} in folder {folder}", {"action": "open_file", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
            ("display file {mention}", {"action": "open_file", "target": "{target}", "platform": "pc"}),
            ("open file {mention} in default editor", {"action": "open_file", "target": "{target}", "platform": "pc"}),
        ],
        "adversarial": [
            ("الملف داك حق الحسابات الأخير", {"action": "open_file", "target": "accounts.xlsx", "platform": "pc"}),
            ("ورقة الاجتماع اللي كتبناها امس", {"action": "open_file", "target": "meeting_notes.txt", "platform": "pc"}),
            ("open that generated pdf report", {"action": "open_file", "target": "report_final.pdf", "platform": "pc"}),
            ("شغل الكود البرمجي حق المشروع", {"action": "open_file", "target": "main.rs", "platform": "pc"}),
        ],
    },
    "launch_app": {
        "colloquial": [
            ("شغل برنامج {mention}", {"action": "launch_app", "target": "{target}", "platform": "pc"}),
            ("افتح لي {mention} دحين", {"action": "launch_app", "target": "{target}", "platform": "pc"}),
            ("فك تطبيق {mention} بالله", {"action": "launch_app", "target": "{target}", "platform": "pc"}),
            ("بدي اشغل {mention} على اللابتوب", {"action": "launch_app", "target": "{target}", "platform": "pc"}),
        ],
        "msa": [
            ("قم بتشغيل برنامج {mention}", {"action": "launch_app", "target": "{target}", "platform": "pc"}),
            ("ابدأ تشغيل تطبيق {mention}", {"action": "launch_app", "target": "{target}", "platform": "pc"}),
            ("افتح برنامج {mention}", {"action": "launch_app", "target": "{target}", "platform": "pc"}),
            ("قم بفتح تطبيق {mention} على الحاسوب", {"action": "launch_app", "target": "{target}", "platform": "pc"}),
        ],
        "english": [
            ("launch {mention}", {"action": "launch_app", "target": "{target}", "platform": "pc"}),
            ("open app {mention}", {"action": "launch_app", "target": "{target}", "platform": "pc"}),
            ("start {mention} application", {"action": "launch_app", "target": "{target}", "platform": "pc"}),
            ("launch program {mention}", {"action": "launch_app", "target": "{target}", "platform": "pc"}),
        ],
        "adversarial": [
            ("شغل محرر الأكواد اللي نبرمج فيه", {"action": "launch_app", "target": "VS Code", "platform": "pc"}),
            ("افتح المتصفح الأزرق", {"action": "launch_app", "target": "Chrome", "platform": "pc"}),
            ("start that music streaming desktop app", {"action": "launch_app", "target": "Spotify", "platform": "pc"}),
            ("افتح لي الشاشة السوداء حق الأوامر", {"action": "launch_app", "target": "Terminal", "platform": "pc"}),
        ],
    },
    "search_files": {
        "colloquial": [
            ("دور لي على ملف {target}", {"action": "search_files", "target": "{target}", "platform": "pc"}),
            ("ابحث وين موجود {target} بمجلد {folder}", {"action": "search_files", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
            ("شوف وين حطيت {target}", {"action": "search_files", "target": "{target}", "platform": "pc"}),
            ("فتش على ملفات {target}", {"action": "search_files", "target": "{target}", "platform": "pc"}),
        ],
        "msa": [
            ("ابحث عن الملف {target}", {"action": "search_files", "target": "{target}", "platform": "pc"}),
            ("قم بالبحث عن كافة الملفات التي تحتوي على {target}", {"action": "search_files", "target": "{target}", "platform": "pc"}),
            ("ابحث في مجلد {folder} عن {target}", {"action": "search_files", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
            ("استعلم عن مسار الملف {target}", {"action": "search_files", "target": "{target}", "platform": "pc"}),
        ],
        "english": [
            ("search for file {target}", {"action": "search_files", "target": "{target}", "platform": "pc"}),
            ("find all files matching {target} in {folder}", {"action": "search_files", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
            ("search {target} files", {"action": "search_files", "target": "{target}", "platform": "pc"}),
            ("locate file {target} on system", {"action": "search_files", "target": "{target}", "platform": "pc"}),
        ],
        "adversarial": [
            ("وين راحت ملفات امس؟", {"action": "search_files", "target": "*yesterday*", "platform": "pc"}),
            ("دور على اي شي صيغته بي دي اف", {"action": "search_files", "target": "*.pdf", "platform": "pc"}),
            ("find where that config went", {"action": "search_files", "target": "config.*", "platform": "pc"}),
        ],
        "targets": ["الفاتورة", "العقد", "تقرير", "budget_2026", "*.log", "receipt.pdf", "backup", "*.csv", "invoice", "notes"],
        "folders": ["Downloads", "Documents", "Projects", "Desktop", "Archive"]
    },
    "set_volume": {
        "colloquial": [
            ("وطي الصوت ل {mention}", {"action": "set_volume", "target": "{target_str}", "args": {"level": "{level_int}"}, "platform": "pc"}),
            ("قصر الصوت شوي ل {mention}", {"action": "set_volume", "target": "{target_str}", "args": {"level": "{level_int}"}, "platform": "pc"}),
            ("علي الصوت ل {mention}", {"action": "set_volume", "target": "{target_str}", "args": {"level": "{level_int}"}, "platform": "pc"}),
            ("ارفع صوت الجهاز ل {mention}", {"action": "set_volume", "target": "{target_str}", "args": {"level": "{level_int}"}, "platform": "pc"}),
            ("حط الصوت على {mention}", {"action": "set_volume", "target": "{target_str}", "args": {"level": "{level_int}"}, "platform": "pc"}),
        ],
        "msa": [
            ("اضبط مستوى الصوت على {mention}", {"action": "set_volume", "target": "{target_str}", "args": {"level": "{level_int}"}, "platform": "pc"}),
            ("اخفض مستوى الصوت إلى {mention}", {"action": "set_volume", "target": "{target_str}", "args": {"level": "{level_int}"}, "platform": "pc"}),
            ("ارفع الصوت إلى {mention}", {"action": "set_volume", "target": "{target_str}", "args": {"level": "{level_int}"}, "platform": "pc"}),
            ("غير مستوى الصوت ليصبح {mention}", {"action": "set_volume", "target": "{target_str}", "args": {"level": "{level_int}"}, "platform": "pc"}),
        ],
        "english": [
            ("set volume to {mention}", {"action": "set_volume", "target": "{target_str}", "args": {"level": "{level_int}"}, "platform": "pc"}),
            ("turn volume down to {mention}", {"action": "set_volume", "target": "{target_str}", "args": {"level": "{level_int}"}, "platform": "pc"}),
            ("change sound volume to {mention}", {"action": "set_volume", "target": "{target_str}", "args": {"level": "{level_int}"}, "platform": "pc"}),
            ("adjust volume level to {mention}", {"action": "set_volume", "target": "{target_str}", "args": {"level": "{level_int}"}, "platform": "pc"}),
        ],
        "adversarial": [
            ("سو شي بالصوت مو سامع شي ارفعه", {"action": "set_volume", "target": "80", "args": {"level": 80}, "platform": "pc"}),
            ("الصوت عالي مره قصر عليه فورا", {"action": "set_volume", "target": "20", "args": {"level": 20}, "platform": "pc"}),
            ("make audio volume louder", {"action": "set_volume", "target": "75", "args": {"level": 75}, "platform": "pc"}),
        ],
    },
    "set_brightness": {
        "colloquial": [
            ("زيد إضاءة الشاشة ل {mention}", {"action": "set_brightness", "target": "{target_str}", "args": {"level": "{level_int}"}, "platform": "pc"}),
            ("وطي نور الشاشة ل {mention}", {"action": "set_brightness", "target": "{target_str}", "args": {"level": "{level_int}"}, "platform": "pc"}),
            ("قصر السطوع ل {mention}", {"action": "set_brightness", "target": "{target_str}", "args": {"level": "{level_int}"}, "platform": "pc"}),
            ("ارفع سطوع الشاشة ل {mention}", {"action": "set_brightness", "target": "{target_str}", "args": {"level": "{level_int}"}, "platform": "pc"}),
        ],
        "msa": [
            ("اضبط سطوع الشاشة على {mention}", {"action": "set_brightness", "target": "{target_str}", "args": {"level": "{level_int}"}, "platform": "pc"}),
            ("قم بزيادة مستوى السطوع إلى {mention}", {"action": "set_brightness", "target": "{target_str}", "args": {"level": "{level_int}"}, "platform": "pc"}),
            ("اخفض سطوع الشاشة إلى {mention}", {"action": "set_brightness", "target": "{target_str}", "args": {"level": "{level_int}"}, "platform": "pc"}),
            ("غير مستوى الإضاءة إلى {mention}", {"action": "set_brightness", "target": "{target_str}", "args": {"level": "{level_int}"}, "platform": "pc"}),
        ],
        "english": [
            ("set brightness to {mention}", {"action": "set_brightness", "target": "{target_str}", "args": {"level": "{level_int}"}, "platform": "pc"}),
            ("dim screen brightness to {mention}", {"action": "set_brightness", "target": "{target_str}", "args": {"level": "{level_int}"}, "platform": "pc"}),
            ("increase brightness to {mention}", {"action": "set_brightness", "target": "{target_str}", "args": {"level": "{level_int}"}, "platform": "pc"}),
            ("adjust display brightness to {mention}", {"action": "set_brightness", "target": "{target_str}", "args": {"level": "{level_int}"}, "platform": "pc"}),
        ],
        "adversarial": [
            ("الشاشة تعمي عيوني خففها الحين", {"action": "set_brightness", "target": "25", "args": {"level": 25}, "platform": "pc"}),
            ("مو شايف شي بالمرة ارفع النور", {"action": "set_brightness", "target": "85", "args": {"level": 85}, "platform": "pc"}),
            ("screen is way too dark", {"action": "set_brightness", "target": "75", "args": {"level": 75}, "platform": "pc"}),
        ],
    },
    "create_note": {
        "colloquial": [
            ("سجل لي ملاحظة {target}", {"action": "create_note", "target": "{target}", "platform": "pc"}),
            ("اكتب نوتة جديدة بعنوان {target} بمجلد {folder}", {"action": "create_note", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
            ("حط لي تذكير سريع {target}", {"action": "create_note", "target": "{target}", "platform": "pc"}),
            ("دون لي ملاحظة {target}", {"action": "create_note", "target": "{target}", "platform": "any"}),
        ],
        "msa": [
            ("أنشئ ملاحظة جديدة بعنوان {target}", {"action": "create_note", "target": "{target}", "platform": "pc"}),
            ("أضف ملاحظة {target} في مجلد {folder}", {"action": "create_note", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
            ("دون الملاحظة التالية: {target}", {"action": "create_note", "target": "{target}", "platform": "any"}),
        ],
        "english": [
            ("create note titled {target}", {"action": "create_note", "target": "{target}", "platform": "pc"}),
            ("take a quick note {target}", {"action": "create_note", "target": "{target}", "platform": "pc"}),
            ("new note {target} in folder {folder}", {"action": "create_note", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
            ("write note {target}", {"action": "create_note", "target": "{target}", "platform": "any"}),
        ],
        "adversarial": [
            ("اكتب اللي في بالي بملف ملاحظات", {"action": "create_note", "target": "Quick Thought", "platform": "pc"}),
            ("تذكير حق موعد الغد الصباح", {"action": "create_note", "target": "Morning Reminder", "platform": "any"}),
            ("save random thought note", {"action": "create_note", "target": "Random Note", "platform": "pc"}),
        ],
        "targets": ["قائمة التسوق", "مهام الأسبوع", "أفكار المشروع الجديد", "Meeting Notes", "Sprint Goals", "مراجعة الامتحان", "Server Credentials"],
        "folders": ["Notes", "Personal", "Work", "Daily"]
    },
    "copy_to_clipboard": {
        "colloquial": [
            ("انسخ لي هذا النص {target}", {"action": "copy_to_clipboard", "target": "{target}", "platform": "any"}),
            ("سوي نسخ ل {target}", {"action": "copy_to_clipboard", "target": "{target}", "platform": "pc"}),
            ("انسخ هذا التوكن للسبورة {target}", {"action": "copy_to_clipboard", "target": "{target}", "platform": "pc"}),
        ],
        "msa": [
            ("انسخ النص التالي إلى الحافظة: {target}", {"action": "copy_to_clipboard", "target": "{target}", "platform": "any"}),
            ("قم بنسخ {target} إلى الحافظة", {"action": "copy_to_clipboard", "target": "{target}", "platform": "pc"}),
            ("احفظ في الحافظة النص {target}", {"action": "copy_to_clipboard", "target": "{target}", "platform": "any"}),
        ],
        "english": [
            ("copy {target} to clipboard", {"action": "copy_to_clipboard", "target": "{target}", "platform": "any"}),
            ("copy to clipboard: {target}", {"action": "copy_to_clipboard", "target": "{target}", "platform": "pc"}),
            ("copy text {target}", {"action": "copy_to_clipboard", "target": "{target}", "platform": "any"}),
            ("copy token {target} to clipboard", {"action": "copy_to_clipboard", "target": "{target}", "platform": "pc"}),
        ],
        "adversarial": [
            ("انسخ الشي المحدد داك", {"action": "copy_to_clipboard", "target": "selected_text", "platform": "any"}),
            ("حط الرابط في الكليب بورد", {"action": "copy_to_clipboard", "target": "https://github.com", "platform": "pc"}),
            ("copy whatever string is active", {"action": "copy_to_clipboard", "target": "active_selection", "platform": "any"}),
        ],
        "targets": ["ghp_1234567890abcdef", "https://github.com/tahadeab-ai/silent-exec", "0501234567", "dev@example.com", "cargo test --workspace", "بسم الله الرحمن الرحيم"]
    },
    "delete_file": {
        "colloquial": [
            ("احذف ملف {target} من {folder}", {"action": "delete_file", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
            ("امسح ملف {target} دحين", {"action": "delete_file", "target": "{target}", "platform": "pc"}),
            ("شيل لي هدا الملف {target}", {"action": "delete_file", "target": "{target}", "platform": "pc"}),
            ("تخلص من ملف {target}", {"action": "delete_file", "target": "{target}", "platform": "pc"}),
        ],
        "msa": [
            ("قم بحذف الملف {target}", {"action": "delete_file", "target": "{target}", "platform": "pc"}),
            ("احذف المستند {target} من مجلد {folder}", {"action": "delete_file", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
            ("أزل الملف {target} نهائيا من القرص", {"action": "delete_file", "target": "{target}", "platform": "pc"}),
        ],
        "english": [
            ("delete file {target}", {"action": "delete_file", "target": "{target}", "platform": "pc"}),
            ("remove file {target} from {folder}", {"action": "delete_file", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
            ("delete document {target}", {"action": "delete_file", "target": "{target}", "platform": "pc"}),
            ("permanently erase file {target}", {"action": "delete_file", "target": "{target}", "platform": "pc"}),
        ],
        "adversarial": [
            ("احذف لي كل الملفات المؤقتة بالداونلودز", {"action": "delete_file", "target": "*.tmp", "folder": "Downloads", "platform": "pc"}),
            ("امسح ملف المسودة القديمة المتروكة", {"action": "delete_file", "target": "draft_old.txt", "platform": "pc"}),
            ("kill that broken cache file", {"action": "delete_file", "target": "corrupt.bin", "platform": "pc"}),
        ],
        "targets": ["temp.log", "old_draft.docx", "ملف_المسودة.txt", "cache.dat", "unused.zip", "test_out.tmp", "debug.log", "backup_old.tar"],
        "folders": ["Downloads", "Temp", "Desktop", "Projects"]
    },
    "move_file": {
        "colloquial": [
            ("انقل ملف {target} لمجلد {folder}", {"action": "move_file", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
            ("حول الملف {target} على {folder}", {"action": "move_file", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
            ("شيل {target} وحطه في {folder}", {"action": "move_file", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
        ],
        "msa": [
            ("قم بنقل الملف {target} إلى مجلد {folder}", {"action": "move_file", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
            ("انقل المستند {target} إلى {folder}", {"action": "move_file", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
            ("غير مسار ملف {target} ليصبح في {folder}", {"action": "move_file", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
        ],
        "english": [
            ("move file {target} to folder {folder}", {"action": "move_file", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
            ("transfer {target} to {folder}", {"action": "move_file", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
            ("relocate {target} to {folder}", {"action": "move_file", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
        ],
        "adversarial": [
            ("انقل الصورة لمكانها الصح بالأرشيف", {"action": "move_file", "target": "photo.jpg", "folder": "Archive", "platform": "pc"}),
            ("حط الملف في مجلد المستندات", {"action": "move_file", "target": "data.csv", "folder": "Documents", "platform": "pc"}),
            ("move report somewhere safe in backup", {"action": "move_file", "target": "report.pdf", "folder": "Backup", "platform": "pc"}),
        ],
        "targets": ["report_final.pdf", "بيانات_الشركة.xlsx", "صورة_شخصية.png", "notes.txt", "backup.tar.gz", "summary.docx"],
        "folders": ["Archive", "Documents", "Projects", "Backup", "Secure"]
    },
    "system_power": {
        "colloquial": [
            ("سكر الجهاز دحين", {"action": "system_power", "target": "shutdown", "platform": "pc"}),
            ("طفي الكمبيوتر بالله", {"action": "system_power", "target": "shutdown", "platform": "pc"}),
            ("عيد تشغيل اللابتوب", {"action": "system_power", "target": "reboot", "platform": "pc"}),
            ("قفل شاشة الكمبيوتر", {"action": "system_power", "target": "lock", "platform": "pc"}),
            ("حط الجهاز بوضع السكون", {"action": "system_power", "target": "sleep", "platform": "pc"}),
        ],
        "msa": [
            ("أوقف تشغيل النظام", {"action": "system_power", "target": "shutdown", "platform": "pc"}),
            ("أعد تشغيل الحاسوب", {"action": "system_power", "target": "reboot", "platform": "pc"}),
            ("اقفل الحاسوب الآن", {"action": "system_power", "target": "lock", "platform": "pc"}),
            ("ضع الجهاز في وضع الاستعداد", {"action": "system_power", "target": "sleep", "platform": "pc"}),
        ],
        "english": [
            ("shutdown the computer", {"action": "system_power", "target": "shutdown", "platform": "pc"}),
            ("reboot the system", {"action": "system_power", "target": "reboot", "platform": "pc"}),
            ("lock the PC", {"action": "system_power", "target": "lock", "platform": "pc"}),
            ("put computer to sleep", {"action": "system_power", "target": "sleep", "platform": "pc"}),
            ("power off the PC", {"action": "system_power", "target": "shutdown", "platform": "pc"}),
            ("restart the computer", {"action": "system_power", "target": "reboot", "platform": "pc"}),
        ],
        "adversarial": [
            ("ارتاح شوي وقفل كل شي بالكمبيوتر", {"action": "system_power", "target": "shutdown", "platform": "pc"}),
            ("الكمبيوتر معلق سوي له ريستارت فوري", {"action": "system_power", "target": "reboot", "platform": "pc"}),
            ("kill system power right now", {"action": "system_power", "target": "shutdown", "platform": "pc"}),
        ]
    },
    "open_android_app": {
        "colloquial": [
            ("افتح لي {mention} بالجوال", {"action": "open_android_app", "target": "{target}", "platform": "android"}),
            ("شغل {mention} دحين على الهاتف", {"action": "open_android_app", "target": "{target}", "platform": "android"}),
            ("فك تطبيق {mention}", {"action": "open_android_app", "target": "{target}", "platform": "android"}),
            ("افتح {mention} فورا بصور", {"action": "open_android_app", "target": "{target}", "platform": "android"}),
        ],
        "msa": [
            ("قم بتشغيل تطبيق {mention} على الهاتف", {"action": "open_android_app", "target": "{target}", "platform": "android"}),
            ("افتح تطبيق {mention}", {"action": "open_android_app", "target": "{target}", "platform": "android"}),
            ("ابدأ تشغيل {mention} على نظام أندرويد", {"action": "open_android_app", "target": "{target}", "platform": "android"}),
        ],
        "english": [
            ("open android app {mention}", {"action": "open_android_app", "target": "{target}", "platform": "android"}),
            ("launch {mention} on phone", {"action": "open_android_app", "target": "{target}", "platform": "android"}),
            ("open {mention} app", {"action": "open_android_app", "target": "{target}", "platform": "android"}),
            ("start {mention} on mobile device", {"action": "open_android_app", "target": "{target}", "platform": "android"}),
        ],
        "adversarial": [
            ("افتح البرنامج الأخضر حق المحادثات بالجوال", {"action": "open_android_app", "target": "WhatsApp", "platform": "android"}),
            ("شغل الخرائط وين انا الحين", {"action": "open_android_app", "target": "Google Maps", "platform": "android"}),
            ("open the mobile messaging app", {"action": "open_android_app", "target": "Telegram", "platform": "android"}),
        ],
    },
    "toggle_setting": {
        "colloquial": [
            ("قفل {mention}", {"action": "toggle_setting", "target": "{target}", "args": {"state": "off"}, "platform": "android"}),
            ("طفي {mention} من الجوال", {"action": "toggle_setting", "target": "{target}", "args": {"state": "off"}, "platform": "android"}),
            ("شغل {mention} الحين", {"action": "toggle_setting", "target": "{target}", "args": {"state": "on"}, "platform": "android"}),
            ("ولع {mention}", {"action": "toggle_setting", "target": "{target}", "args": {"state": "on"}, "platform": "android"}),
            ("فعل {mention} بسرعة", {"action": "toggle_setting", "target": "{target}", "args": {"state": "on"}, "platform": "android"}),
        ],
        "msa": [
            ("قم بتعطيل {mention}", {"action": "toggle_setting", "target": "{target}", "args": {"state": "off"}, "platform": "android"}),
            ("أوقف تشغيل {mention}", {"action": "toggle_setting", "target": "{target}", "args": {"state": "off"}, "platform": "android"}),
            ("فعل {mention} في الهاتف", {"action": "toggle_setting", "target": "{target}", "args": {"state": "on"}, "platform": "android"}),
            ("قم بتشغيل {mention}", {"action": "toggle_setting", "target": "{target}", "args": {"state": "on"}, "platform": "android"}),
        ],
        "english": [
            ("turn off {mention}", {"action": "toggle_setting", "target": "{target}", "args": {"state": "off"}, "platform": "android"}),
            ("disable {mention}", {"action": "toggle_setting", "target": "{target}", "args": {"state": "off"}, "platform": "android"}),
            ("turn on {mention}", {"action": "toggle_setting", "target": "{target}", "args": {"state": "on"}, "platform": "android"}),
            ("enable {mention}", {"action": "toggle_setting", "target": "{target}", "args": {"state": "on"}, "platform": "android"}),
            ("toggle {mention} on", {"action": "toggle_setting", "target": "{target}", "args": {"state": "on"}, "platform": "android"}),
        ],
        "adversarial": [
            ("ما بدي انترنت بالجوال دحين اقطعه", {"action": "toggle_setting", "target": "wifi", "args": {"state": "off"}, "platform": "android"}),
            ("المكان مظلم نور لي كشاف التلفون", {"action": "toggle_setting", "target": "flashlight", "args": {"state": "on"}, "platform": "android"}),
            ("enable airplane mode right now", {"action": "toggle_setting", "target": "airplane_mode", "args": {"state": "on"}, "platform": "android"}),
        ],
    },
    "send_message": {
        "colloquial": [
            ("ارسل رسالة ل {target} {text}", {"action": "send_message", "target": "{target}", "args": {"text": "{text}"}, "platform": "android"}),
            ("ابعث واتساب ل {target} {text}", {"action": "send_message", "target": "{target}", "args": {"text": "{text}"}, "platform": "android"}),
            ("دز مسج ل {target} {text}", {"action": "send_message", "target": "{target}", "args": {"text": "{text}"}, "platform": "android"}),
            ("ارسل ل {target} قله {text}", {"action": "send_message", "target": "{target}", "args": {"text": "{text}"}, "platform": "android"}),
        ],
        "msa": [
            ("أرسل رسالة نصية إلى {target} محتواها: {text}", {"action": "send_message", "target": "{target}", "args": {"text": "{text}"}, "platform": "android"}),
            ("قم بإرسال رسالة إلى {target} تقول فيها {text}", {"action": "send_message", "target": "{target}", "args": {"text": "{text}"}, "platform": "android"}),
            ("أرسل إلى {target}: {text}", {"action": "send_message", "target": "{target}", "args": {"text": "{text}"}, "platform": "android"}),
        ],
        "english": [
            ("send message to {target}: {text}", {"action": "send_message", "target": "{target}", "args": {"text": "{text}"}, "platform": "android"}),
            ("text {target} saying {text}", {"action": "send_message", "target": "{target}", "args": {"text": "{text}"}, "platform": "android"}),
            ("send SMS to {target} with content: {text}", {"action": "send_message", "target": "{target}", "args": {"text": "{text}"}, "platform": "android"}),
        ],
        "adversarial": [
            ("قل له اني جاي في الطريق الحين", {"action": "send_message", "target": "Recent Contact", "args": {"text": "أنا جاي في الطريق"}, "platform": "android"}),
            ("ارسل للوالد تم بنجاح", {"action": "send_message", "target": "الوالد", "args": {"text": "تم بنجاح"}, "platform": "android"}),
            ("text boss that project is completed", {"action": "send_message", "target": "Boss", "args": {"text": "Project is completed"}, "platform": "android"}),
        ],
        "targets": ["أحمد", "الوالد", "محمد", "سارة", "+966501234567", "+967771234567", "Manager", "Tech Support", "Ali", "Fatima"],
        "texts": ["أنا في الطريق الآن", "هل تم تأكيد الموعد؟", "يرجى مراجعة البريد الإلكتروني", "وصلت للبيت الحمد لله", "All tasks done", "See you at the office tomorrow"]
    },
    "open_url": {
        "colloquial": [
            ("افتح {mention}", {"action": "open_url", "target": "{target}", "platform": "pc"}),
            ("ادخل على رابط {mention} دحين", {"action": "open_url", "target": "{target}", "platform": "pc"}),
            ("شوف لي هدا الموقع {mention}", {"action": "open_url", "target": "{target}", "platform": "pc"}),
            ("افتح لينك {mention}", {"action": "open_url", "target": "{target}", "platform": "pc"}),
        ],
        "msa": [
            ("افتح الموقع الإلكتروني {mention}", {"action": "open_url", "target": "{target}", "platform": "pc"}),
            ("قم بزيارة الرابط {mention}", {"action": "open_url", "target": "{target}", "platform": "pc"}),
            ("تصفح الرابط التالي: {mention}", {"action": "open_url", "target": "{target}", "platform": "pc"}),
            ("انتقل إلى موقع {mention}", {"action": "open_url", "target": "{target}", "platform": "pc"}),
        ],
        "english": [
            ("open url {mention}", {"action": "open_url", "target": "{target}", "platform": "pc"}),
            ("navigate to website {mention}", {"action": "open_url", "target": "{target}", "platform": "pc"}),
            ("browse to {mention}", {"action": "open_url", "target": "{target}", "platform": "pc"}),
            ("visit website {mention}", {"action": "open_url", "target": "{target}", "platform": "pc"}),
        ],
        "adversarial": [
            ("افتح الرابط داك حق الأبحاث", {"action": "open_url", "target": "https://arxiv.org", "platform": "pc"}),
            ("وريني المستودع حق الكود بالجيت هاب", {"action": "open_url", "target": "https://github.com", "platform": "pc"}),
            ("go to that tech news homepage", {"action": "open_url", "target": "https://news.ycombinator.com", "platform": "pc"}),
        ],
    }
}


def build_example(action: str, category: str, rng: random.Random) -> Tuple[str, Dict[str, Any]]:
    config = TEMPLATES[action]
    templates = config.get(category, config.get("msa", config["colloquial"]))
    template_str, payload_template = rng.choice(templates)

    # Filter pairs based on language category for natural linguistic realism
    def filter_pairs_by_lang(pairs: List[Tuple[str, str]], is_english: bool) -> List[Tuple[str, str]]:
        if is_english:
            en_pairs = [p for p in pairs if p[0].isascii() or any(c in p[0] for c in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ/._-")]
            return en_pairs if en_pairs else pairs
        else:
            ar_pairs = [p for p in pairs if any('\u0600' <= c <= '\u06FF' for c in p[0])]
            return ar_pairs if ar_pairs else pairs

    is_english = (category == "english")

    mention = ""
    target_str = ""
    folder_str = ""
    text_str = ""
    level_int = 50

    if action == "open_folder":
        candidates = filter_pairs_by_lang(FOLDER_TARGET_PAIRS, is_english)
        mention, target_str = rng.choice(candidates)
    elif action == "open_file":
        candidates = filter_pairs_by_lang(FILE_TARGET_PAIRS, is_english)
        mention, target_str = rng.choice(candidates)
        folder_str = rng.choice(["Documents", "Downloads", "Projects", "Desktop", "Archive"])
    elif action == "launch_app":
        candidates = filter_pairs_by_lang(PC_APP_TARGET_PAIRS, is_english)
        mention, target_str = rng.choice(candidates)
    elif action == "open_android_app":
        candidates = filter_pairs_by_lang(ANDROID_APP_TARGET_PAIRS, is_english)
        mention, target_str = rng.choice(candidates)
    elif action == "open_url":
        candidates = filter_pairs_by_lang(URL_TARGET_PAIRS, is_english)
        mention, target_str = rng.choice(candidates)
    elif action in ("set_volume", "set_brightness"):
        if is_english:
            candidates = [p for p in LEVEL_MENTIONS if "%" in p[0] or p[0].isdigit()]
        else:
            candidates = LEVEL_MENTIONS
        mention, target_str, level_int = rng.choice(candidates)
    elif action == "toggle_setting":
        if category != "adversarial":
            if is_english:
                candidates = [p for p in SETTINGS_PAIRS if p[0].isascii()]
            else:
                candidates = [p for p in SETTINGS_PAIRS if any('\u0600' <= c <= '\u06FF' for c in p[0])]
            mention, target_str, setting_state = rng.choice(candidates)
        else:
            mention = "wifi"
            target_str = "wifi"
            setting_state = "off"
    elif action == "search_files":
        target_str = rng.choice(config.get("targets", ["*.pdf"]))
        folder_str = rng.choice(config.get("folders", ["Documents"]))
    elif action == "send_message":
        if is_english:
            target_str = rng.choice(["Manager", "Tech Support", "John", "Sarah", "+966501234567", "+967771234567"])
            text_str = rng.choice(["All tasks done", "See you tomorrow", "Meeting link sent", "Will be there in 10 minutes"])
        else:
            target_str = rng.choice(["أحمد", "الوالد", "محمد", "سارة", "+966501234567", "+967771234567"])
            text_str = rng.choice(["أنا في الطريق الآن", "هل تم تأكيد الموعد؟", "يرجى مراجعة البريد الإلكتروني", "وصلت للبيت الحمد لله"])
    elif action in ("delete_file", "move_file", "create_note", "copy_to_clipboard"):
        target_str = rng.choice(config.get("targets", ["document.pdf"]))
        folder_str = rng.choice(config.get("folders", ["Archive"])) if "folders" in config else ""

    # Format user prompt
    format_kwargs = {
        "mention": mention or target_str,
        "target": target_str,
        "target_str": target_str,
        "folder": folder_str,
        "text": text_str,
        "level_int": level_int
    }
    user_prompt = template_str.format(**format_kwargs)

    # Format payload
    payload: Dict[str, Any] = {}
    for k, v in payload_template.items():
        if isinstance(v, str):
            payload[k] = v.format(**format_kwargs)
        elif isinstance(v, dict):
            sub_dict: Dict[str, Any] = {}
            for sk, sv in v.items():
                if isinstance(sv, str) and "{level_int}" in sv:
                    sub_dict[sk] = int(level_int)
                elif isinstance(sv, str):
                    sub_dict[sk] = sv.format(**format_kwargs)
                else:
                    sub_dict[sk] = sv
            payload[k] = sub_dict
        else:
            payload[k] = v

    # Confidence score assignment
    if category == "adversarial":
        payload["confidence"] = round(rng.uniform(0.48, 0.65), 2)
    else:
        payload["confidence"] = round(rng.uniform(0.85, 0.99), 2)

    return user_prompt, payload


def generate_dataset(total_count: int, seed: int) -> List[Dict[str, Any]]:
    rng = random.Random(seed)

    # Realistic linguistic distribution across 15 actions:
    # 38% Colloquial Yemeni/Gulf
    # 32% Modern Standard Arabic (MSA)
    # 20% English & Code-switching
    # 10% Adversarial & Ambiguous edge cases
    categories = ["colloquial"] * 38 + ["msa"] * 32 + ["english"] * 20 + ["adversarial"] * 10

    examples: List[Dict[str, Any]] = []

    # Ensure balanced representation across all 15 actions
    base_per_action = total_count // len(ACTIONS)
    remainder = total_count % len(ACTIONS)

    for i, action in enumerate(ACTIONS):
        count_for_action = base_per_action + (1 if i < remainder else 0)
        for _ in range(count_for_action):
            cat = rng.choice(categories)
            user_prompt, payload = build_example(action, cat, rng)

            chatml_sample = {
                "messages": [
                    {"role": "system", "content": SYSTEM_PROMPT},
                    {"role": "user", "content": user_prompt},
                    {
                        "role": "assistant",
                        "content": json.dumps(payload, separators=(",", ":"), ensure_ascii=False)
                    }
                ],
                "meta": {
                    "action": action,
                    "category": cat,
                    "confidence": payload["confidence"]
                }
            }
            examples.append(chatml_sample)

    rng.shuffle(examples)
    return examples


def validate_and_save(
    dataset: List[Dict[str, Any]],
    out_dir: Path,
    action_schema: Dict[str, Any]
) -> Dict[str, Any]:
    validator = jsonschema.Draft202012Validator(action_schema)

    total_validated = 0
    action_counts: Dict[str, int] = {a: 0 for a in ACTIONS}
    category_counts: Dict[str, int] = {"colloquial": 0, "msa": 0, "english": 0, "adversarial": 0}

    # Validate 100% of generated assistant JSON payloads
    for idx, sample in enumerate(dataset):
        raw_assistant = sample["messages"][2]["content"]
        try:
            parsed = json.loads(raw_assistant)
        except json.JSONDecodeError as e:
            raise ValueError(f"Sample #{idx} has invalid JSON content: {e}")

        errors = list(validator.iter_errors(parsed))
        if errors:
            raise ValueError(
                f"Sample #{idx} failed schema validation: {[e.message for e in errors]}. Payload: {parsed}"
            )

        total_validated += 1
        action_counts[sample["meta"]["action"]] += 1
        category_counts[sample["meta"]["category"]] += 1

    out_dir.mkdir(parents=True, exist_ok=True)

    # 80 / 10 / 10 split
    n = len(dataset)
    n_train = int(n * 0.80)
    n_val = int(n * 0.10)

    train_data = dataset[:n_train]
    val_data = dataset[n_train:n_train + n_val]
    test_data = dataset[n_train + n_val:]

    splits = {
        "train.jsonl": train_data,
        "val.jsonl": val_data,
        "test.jsonl": test_data,
    }

    for filename, rows in splits.items():
        filepath = out_dir / filename
        with open(filepath, "w", encoding="utf-8") as f:
            for row in rows:
                f.write(json.dumps(row, ensure_ascii=False) + "\n")

    summary = {
        "total_generated": len(dataset),
        "total_validated": total_validated,
        "splits": {k: len(v) for k, v in splits.items()},
        "action_distribution": action_counts,
        "category_distribution": category_counts,
        "out_dir": str(out_dir.resolve()),
    }
    return summary


def main():
    parser = argparse.ArgumentParser(description="SilentExec Deterministic Bilingual Dataset Generator")
    parser.add_argument("--count", type=int, default=3600, help="Total number of dataset rows to generate")
    parser.add_argument("--seed", type=int, default=42, help="Deterministic random seed")
    parser.add_argument("--out-dir", type=str, default="/tmp/silent_exec_data", help="Output directory for jsonl splits")
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parent.parent
    schema_path = repo_root / "schemas" / "action_schema.json"

    if not schema_path.is_file():
        print(f"ERROR: action_schema.json not found at {schema_path}", file=sys.stderr)
        sys.exit(1)

    with open(schema_path, "r", encoding="utf-8") as f:
        action_schema = json.load(f)

    print(f"Generating {args.count} samples deterministically (seed={args.seed})...")
    dataset = generate_dataset(total_count=args.count, seed=args.seed)

    print("Validating 100% of samples against JSON Schema (Draft 2020-12)...")
    out_path = Path(args.out_dir)
    summary = validate_and_save(dataset, out_path, action_schema)

    print("\nDataset generation and validation completed successfully!")
    print(f"Total samples: {summary['total_generated']} (100% passed Draft 2020-12 validation)")
    print(f"Splits: {summary['splits']}")
    print(f"Categories: {summary['category_distribution']}")
    print(f"Output directory: {summary['out_dir']}")


if __name__ == "__main__":
    main()
