#!/usr/bin/env python3
"""
SilentExec Deterministic Bilingual Dataset Generator
Generates ChatML JSONL datasets (train/val/test) for training zero-chatter OS execution agents.
Enforces 100% schema validation against schemas/action_schema.json using jsonschema.
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

# Linguistic Templates and Entities
TEMPLATES = {
    "open_folder": {
        "colloquial": [
            ("ادخل مجلد {target}", {"action": "open_folder", "target": "{target}", "platform": "pc"}),
            ("افتح لي مجلد {target} فورا", {"action": "open_folder", "target": "{target}", "platform": "pc"}),
            ("شوف لي ايش بمجلد {target}", {"action": "open_folder", "target": "{target}", "platform": "pc"}),
            ("افتح فولدر {target} دحين", {"action": "open_folder", "target": "{target}", "platform": "any"}),
            ("فك لي ملفات {target}", {"action": "open_folder", "target": "{target}", "platform": "any"}),
        ],
        "msa": [
            ("افتح مجلد {target}", {"action": "open_folder", "target": "{target}", "platform": "pc"}),
            ("قم بالدخول إلى المجلد {target}", {"action": "open_folder", "target": "{target}", "platform": "pc"}),
            ("تصفح مجلد {target}", {"action": "open_folder", "target": "{target}", "platform": "any"}),
            ("استعرض محتويات مجلد {target}", {"action": "open_folder", "target": "{target}", "platform": "pc"}),
        ],
        "english": [
            ("open folder {target}", {"action": "open_folder", "target": "{target}", "platform": "pc"}),
            ("navigate to folder {target}", {"action": "open_folder", "target": "{target}", "platform": "pc"}),
            ("افتح folder {target}", {"action": "open_folder", "target": "{target}", "platform": "any"}),
            ("open directory {target}", {"action": "open_folder", "target": "{target}", "platform": "pc"}),
        ],
        "adversarial": [
            ("وريني الملفات القديمة مدري وين", {"action": "open_folder", "target": "Documents", "platform": "pc"}),
            ("فولدر المشاريع يمكن", {"action": "open_folder", "target": "Projects", "platform": "any"}),
            ("folder stuff?", {"action": "open_folder", "target": "Downloads", "platform": "pc"}),
        ],
        "targets": ["المشاريع", "التنزيلات", "الصور", "المستندات", "Downloads", "Documents/Work", "Projects/Rust", "Desktop/Reports", "النسخ_الاحتياطية"]
    },
    "open_file": {
        "colloquial": [
            ("افتح لي ملف {target}", {"action": "open_file", "target": "{target}", "platform": "pc"}),
            ("شغل ملف {target} من مجلد {folder}", {"action": "open_file", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
            ("فك لي تقرير {target} بسرعة", {"action": "open_file", "target": "{target}", "platform": "any"}),
            ("افتح الورقة حق {target}", {"action": "open_file", "target": "{target}", "platform": "pc"}),
        ],
        "msa": [
            ("قم بفتح الملف {target}", {"action": "open_file", "target": "{target}", "platform": "pc"}),
            ("افتح المستند {target} في مجلد {folder}", {"action": "open_file", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
            ("اعرض ملف {target}", {"action": "open_file", "target": "{target}", "platform": "any"}),
        ],
        "english": [
            ("open file {target}", {"action": "open_file", "target": "{target}", "platform": "pc"}),
            ("open document {target} in {folder}", {"action": "open_file", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
            ("افتح لي file {target}", {"action": "open_file", "target": "{target}", "platform": "any"}),
            ("view file {target}", {"action": "open_file", "target": "{target}", "platform": "pc"}),
        ],
        "adversarial": [
            ("الملف داك حق الاجتماع", {"action": "open_file", "target": "meeting_notes.txt", "platform": "pc"}),
            ("ورقة الحسابات الأخيرة", {"action": "open_file", "target": "accounts.xlsx", "platform": "pc"}),
            ("open the pdf thing", {"action": "open_file", "target": "document.pdf", "platform": "any"}),
        ],
        "targets": ["مشروع_التخرج.docx", "فاتورة_الكهرباء.pdf", "report_2026.pdf", "presentation.pptx", "data.csv", "contract.pdf", "ملخص_الدرس.pdf"],
        "folders": ["Documents", "المستندات", "Downloads", "Work"]
    },
    "launch_app": {
        "colloquial": [
            ("شغل برنامج {target}", {"action": "launch_app", "target": "{target}", "platform": "pc"}),
            ("افتح لي {target} دحين", {"action": "launch_app", "target": "{target}", "platform": "pc"}),
            ("فك تطبيق {target} بالله", {"action": "launch_app", "target": "{target}", "platform": "pc"}),
            ("بدي اشغل {target}", {"action": "launch_app", "target": "{target}", "platform": "pc"}),
        ],
        "msa": [
            ("قم بتشغيل تطبيق {target}", {"action": "launch_app", "target": "{target}", "platform": "pc"}),
            ("ابدأ تشغيل برنامج {target}", {"action": "launch_app", "target": "{target}", "platform": "pc"}),
            ("افتح تطبيق {target}", {"action": "launch_app", "target": "{target}", "platform": "pc"}),
        ],
        "english": [
            ("launch {target}", {"action": "launch_app", "target": "{target}", "platform": "pc"}),
            ("open app {target}", {"action": "launch_app", "target": "{target}", "platform": "pc"}),
            ("start {target} application", {"action": "launch_app", "target": "{target}", "platform": "pc"}),
            ("افتح برنامج {target}", {"action": "launch_app", "target": "{target}", "platform": "pc"}),
        ],
        "adversarial": [
            ("شغل داك البرنامج حق الكود", {"action": "launch_app", "target": "VS Code", "platform": "pc"}),
            ("المتصفح الأزرق داك", {"action": "launch_app", "target": "Chrome", "platform": "pc"}),
            ("start that music player", {"action": "launch_app", "target": "Spotify", "platform": "pc"}),
        ],
        "targets": ["Chrome", "VS Code", "Notepad", "Spotify", "Terminal", "Calculator", "Slack", "الآلة الحاسبة", "المفكرة", "المتصفح"]
    },
    "search_files": {
        "colloquial": [
            ("دور لي على ملف {target}", {"action": "search_files", "target": "{target}", "platform": "pc"}),
            ("ابحث وين موجود {target} بمجلد {folder}", {"action": "search_files", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
            ("شوف وين حطيت {target}", {"action": "search_files", "target": "{target}", "platform": "any"}),
            ("فتش على ملفات {target}", {"action": "search_files", "target": "{target}", "platform": "pc"}),
        ],
        "msa": [
            ("ابحث عن الملف {target}", {"action": "search_files", "target": "{target}", "platform": "pc"}),
            ("قم بالبحث عن كافة الملفات التي تحتوي على {target}", {"action": "search_files", "target": "{target}", "platform": "any"}),
            ("ابحث في مجلد {folder} عن {target}", {"action": "search_files", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
        ],
        "english": [
            ("search for file {target}", {"action": "search_files", "target": "{target}", "platform": "pc"}),
            ("find all files matching {target} in {folder}", {"action": "search_files", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
            ("search {target}", {"action": "search_files", "target": "{target}", "platform": "any"}),
            ("ابحث عن {target} files", {"action": "search_files", "target": "{target}", "platform": "pc"}),
        ],
        "adversarial": [
            ("وين راحت ملفات امس؟", {"action": "search_files", "target": "yesterday", "platform": "pc"}),
            ("دور على اي شي فيه أرقام", {"action": "search_files", "target": "*2026*", "platform": "any"}),
            ("where did i put my doc?", {"action": "search_files", "target": "doc", "platform": "pc"}),
        ],
        "targets": ["الفاتورة", "العقد", "تقرير", "budget_2026", "*.log", "receipt.pdf", "backup"],
        "folders": ["Downloads", "Documents", "المستندات", "Projects"]
    },
    "set_volume": {
        "colloquial": [
            ("وطي الصوت لثلاثين", {"action": "set_volume", "target": "30", "args": {"level": 30}, "platform": "pc"}),
            ("قصر الصوت شوي ل {target}", {"action": "set_volume", "target": "{target}", "args": {"level": "{level}"}, "platform": "any"}),
            ("علي الصوت لسبعين في المية", {"action": "set_volume", "target": "70", "args": {"level": 70}, "platform": "pc"}),
            ("ارفع الصوت ل {target}", {"action": "set_volume", "target": "{target}", "args": {"level": "{level}"}, "platform": "any"}),
        ],
        "msa": [
            ("اضبط مستوى الصوت على {target} بالمئة", {"action": "set_volume", "target": "{target}", "args": {"level": "{level}"}, "platform": "pc"}),
            ("اخفض مستوى الصوت إلى {target}", {"action": "set_volume", "target": "{target}", "args": {"level": "{level}"}, "platform": "any"}),
            ("ارفع الصوت إلى {target}", {"action": "set_volume", "target": "{target}", "args": {"level": "{level}"}, "platform": "pc"}),
        ],
        "english": [
            ("set volume to {target}%", {"action": "set_volume", "target": "{target}", "args": {"level": "{level}"}, "platform": "pc"}),
            ("turn volume down to {target}", {"action": "set_volume", "target": "{target}", "args": {"level": "{level}"}, "platform": "any"}),
            ("change sound volume to {target}", {"action": "set_volume", "target": "{target}", "args": {"level": "{level}"}, "platform": "pc"}),
            ("حط الصوت على {target}%", {"action": "set_volume", "target": "{target}", "args": {"level": "{level}"}, "platform": "any"}),
        ],
        "adversarial": [
            ("سو شي بالصوت مو سامع", {"action": "set_volume", "target": "80", "args": {"level": 80}, "platform": "any"}),
            ("الصوت عالي مره", {"action": "set_volume", "target": "20", "args": {"level": 20}, "platform": "pc"}),
            ("make it quieter", {"action": "set_volume", "target": "15", "args": {"level": 15}, "platform": "any"}),
        ],
        "levels": [10, 20, 30, 40, 50, 60, 70, 80, 90, 100]
    },
    "set_brightness": {
        "colloquial": [
            ("زيد إضاءة الشاشة ل {target}", {"action": "set_brightness", "target": "{target}", "args": {"level": "{level}"}, "platform": "pc"}),
            ("وطي نور الشاشة شوي ل {target}", {"action": "set_brightness", "target": "{target}", "args": {"level": "{level}"}, "platform": "any"}),
            ("قصر السطوع ل {target}", {"action": "set_brightness", "target": "{target}", "args": {"level": "{level}"}, "platform": "any"}),
        ],
        "msa": [
            ("اضبط سطوع الشاشة على {target} بالمئة", {"action": "set_brightness", "target": "{target}", "args": {"level": "{level}"}, "platform": "pc"}),
            ("قم بزيادة مستوى السطوع إلى {target}", {"action": "set_brightness", "target": "{target}", "args": {"level": "{level}"}, "platform": "any"}),
            ("اخفض سطوع الشاشة إلى {target}", {"action": "set_brightness", "target": "{target}", "args": {"level": "{level}"}, "platform": "pc"}),
        ],
        "english": [
            ("set brightness to {target}%", {"action": "set_brightness", "target": "{target}", "args": {"level": "{level}"}, "platform": "pc"}),
            ("dim screen brightness to {target}", {"action": "set_brightness", "target": "{target}", "args": {"level": "{level}"}, "platform": "any"}),
            ("increase brightness to {target}", {"action": "set_brightness", "target": "{target}", "args": {"level": "{level}"}, "platform": "pc"}),
            ("حط brightness على {target}%", {"action": "set_brightness", "target": "{target}", "args": {"level": "{level}"}, "platform": "any"}),
        ],
        "adversarial": [
            ("الشاشة تعمي عيوني خففها", {"action": "set_brightness", "target": "25", "args": {"level": 25}, "platform": "any"}),
            ("مو شايف شي ارفع النور", {"action": "set_brightness", "target": "85", "args": {"level": 85}, "platform": "pc"}),
            ("too dark screen", {"action": "set_brightness", "target": "75", "args": {"level": 75}, "platform": "any"}),
        ],
        "levels": [15, 30, 45, 50, 60, 75, 80, 90, 100]
    },
    "create_note": {
        "colloquial": [
            ("سجل لي ملاحظة سريعة {target}", {"action": "create_note", "target": "{target}", "platform": "any"}),
            ("اكتب نوتة جديدة بعنوان {target} في مجلد {folder}", {"action": "create_note", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
            ("حط لي تذكير {target}", {"action": "create_note", "target": "{target}", "platform": "any"}),
        ],
        "msa": [
            ("أنشئ ملاحظة جديدة بعنوان {target}", {"action": "create_note", "target": "{target}", "platform": "any"}),
            ("أضف ملاحظة {target} في مجلد {folder}", {"action": "create_note", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
            ("دون الملاحظة التالية: {target}", {"action": "create_note", "target": "{target}", "platform": "any"}),
        ],
        "english": [
            ("create note titled {target}", {"action": "create_note", "target": "{target}", "platform": "any"}),
            ("take a quick note {target}", {"action": "create_note", "target": "{target}", "platform": "pc"}),
            ("new note {target} in folder {folder}", {"action": "create_note", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
            ("سوي note بعنوان {target}", {"action": "create_note", "target": "{target}", "platform": "any"}),
        ],
        "adversarial": [
            ("اكتب اللي في بالي الحين", {"action": "create_note", "target": "Quick Thought", "platform": "any"}),
            ("تذكير حق بكرة الصباح", {"action": "create_note", "target": "Morning Reminder", "platform": "any"}),
            ("save random text note", {"action": "create_note", "target": "Random Note", "platform": "pc"}),
        ],
        "targets": ["قائمة التسوق", "مهام الأسبوع القادم", "أفكار المشروع الجديد", "Meeting Notes", "Sprint Goals", "مراجعة الامتحان", "Password Reminder"],
        "folders": ["ملاحظاتي", "Personal", "Work", "Daily"]
    },
    "copy_to_clipboard": {
        "colloquial": [
            ("انسخ لي هذا النص {target}", {"action": "copy_to_clipboard", "target": "{target}", "platform": "any"}),
            ("سوي نسخ ل {target}", {"action": "copy_to_clipboard", "target": "{target}", "platform": "pc"}),
            ("انسخ هذا الكود للسبورة {target}", {"action": "copy_to_clipboard", "target": "{target}", "platform": "pc"}),
        ],
        "msa": [
            ("انسخ النص التالي إلى الحافظة: {target}", {"action": "copy_to_clipboard", "target": "{target}", "platform": "any"}),
            ("قم بنسخ {target} إلى الحافظة", {"action": "copy_to_clipboard", "target": "{target}", "platform": "pc"}),
            ("احفظ في الحافظة {target}", {"action": "copy_to_clipboard", "target": "{target}", "platform": "any"}),
        ],
        "english": [
            ("copy {target} to clipboard", {"action": "copy_to_clipboard", "target": "{target}", "platform": "any"}),
            ("copy to clipboard: {target}", {"action": "copy_to_clipboard", "target": "{target}", "platform": "pc"}),
            ("انسخ {target} to clipboard", {"action": "copy_to_clipboard", "target": "{target}", "platform": "any"}),
            ("copy token {target}", {"action": "copy_to_clipboard", "target": "{target}", "platform": "pc"}),
        ],
        "adversarial": [
            ("انسخ الشي داك", {"action": "copy_to_clipboard", "target": "selected_text", "platform": "any"}),
            ("حط الكلام في الكليب بورد", {"action": "copy_to_clipboard", "target": "clipboard_data", "platform": "pc"}),
            ("copy whatever is there", {"action": "copy_to_clipboard", "target": "selection", "platform": "any"}),
        ],
        "targets": ["ghp_1234567890abcdef", "https://github.com/tahadeab-ai/silent-exec", "0501234567", "taha@example.com", "sudo systemctl restart", "بسم الله الرحمن الرحيم"]
    },
    "delete_file": {
        "colloquial": [
            ("احذف ملف {target} من {folder}", {"action": "delete_file", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
            ("امسح ملف {target} دحين", {"action": "delete_file", "target": "{target}", "platform": "pc"}),
            ("شيل لي هدا الملف {target}", {"action": "delete_file", "target": "{target}", "platform": "any"}),
            ("تخلص من ملف {target}", {"action": "delete_file", "target": "{target}", "platform": "pc"}),
        ],
        "msa": [
            ("قم بحذف الملف {target}", {"action": "delete_file", "target": "{target}", "platform": "pc"}),
            ("احذف المستند {target} من مجلد {folder}", {"action": "delete_file", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
            ("أزل الملف {target} نهائيا", {"action": "delete_file", "target": "{target}", "platform": "any"}),
        ],
        "english": [
            ("delete file {target}", {"action": "delete_file", "target": "{target}", "platform": "pc"}),
            ("remove file {target} from {folder}", {"action": "delete_file", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
            ("احذف file {target}", {"action": "delete_file", "target": "{target}", "platform": "any"}),
            ("delete document {target}", {"action": "delete_file", "target": "{target}", "platform": "pc"}),
        ],
        "adversarial": [
            ("احذف لي كل الملفات المؤقتة بالداونلودز", {"action": "delete_file", "target": "*.tmp", "folder": "Downloads", "platform": "pc"}),
            ("امسح ملف المسودة القديمة", {"action": "delete_file", "target": "draft_old.txt", "platform": "any"}),
            ("kill that broken file", {"action": "delete_file", "target": "corrupt.bin", "platform": "pc"}),
        ],
        "targets": ["temp.log", "old_draft.docx", "ملف_المسودة.txt", "cache.dat", "unused.zip", "test_out.tmp"],
        "folders": ["Downloads", "التنزيلات", "Temp", "Desktop"]
    },
    "move_file": {
        "colloquial": [
            ("انقل ملف {target} لمجلد {folder}", {"action": "move_file", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
            ("حول الملف {target} على {folder}", {"action": "move_file", "target": "{target}", "folder": "{folder}", "platform": "any"}),
            ("شيل {target} وحطه في {folder}", {"action": "move_file", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
        ],
        "msa": [
            ("قم بنقل الملف {target} إلى مجلد {folder}", {"action": "move_file", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
            ("انقل المستند {target} إلى {folder}", {"action": "move_file", "target": "{target}", "folder": "{folder}", "platform": "any"}),
            ("غير مسار ملف {target} ليصبح في {folder}", {"action": "move_file", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
        ],
        "english": [
            ("move file {target} to folder {folder}", {"action": "move_file", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
            ("transfer {target} to {folder}", {"action": "move_file", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
            ("انقل file {target} to {folder}", {"action": "move_file", "target": "{target}", "folder": "{folder}", "platform": "any"}),
            ("relocate {target} to {folder}", {"action": "move_file", "target": "{target}", "folder": "{folder}", "platform": "pc"}),
        ],
        "adversarial": [
            ("انقل الصورة لمكانها الصح", {"action": "move_file", "target": "photo.jpg", "folder": "Pictures", "platform": "pc"}),
            ("حط الملف في الأرشيف", {"action": "move_file", "target": "data.csv", "folder": "Archive", "platform": "any"}),
            ("move report somewhere safe", {"action": "move_file", "target": "report.pdf", "folder": "Secure", "platform": "pc"}),
        ],
        "targets": ["report_final.pdf", "البيانات.xlsx", "صورة_شخصية.png", "notes.txt", "backup.tar.gz"],
        "folders": ["Archive", "الأرشيف", "Documents", "المستندات", "Projects/Archive"]
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
            ("اطفئ الـ PC", {"action": "system_power", "target": "shutdown", "platform": "pc"}),
        ],
        "adversarial": [
            ("ارتاح شوي وقفل كل شي", {"action": "system_power", "target": "shutdown", "platform": "pc"}),
            ("الكمبيوتر معلق سوي له ريستارت", {"action": "system_power", "target": "reboot", "platform": "pc"}),
            ("kill power now", {"action": "system_power", "target": "shutdown", "platform": "pc"}),
        ]
    },
    "open_android_app": {
        "colloquial": [
            ("افتح لي الواتساب", {"action": "open_android_app", "target": "WhatsApp", "platform": "android"}),
            ("شغل التليجرام دحين", {"action": "open_android_app", "target": "Telegram", "platform": "android"}),
            ("فك تطبيق {target}", {"action": "open_android_app", "target": "{target}", "platform": "android"}),
            ("افتح الكاميرا بسرعة بصور", {"action": "open_android_app", "target": "Camera", "platform": "android"}),
        ],
        "msa": [
            ("قم بتشغيل تطبيق {target} على الهاتف", {"action": "open_android_app", "target": "{target}", "platform": "android"}),
            ("افتح تطبيق {target}", {"action": "open_android_app", "target": "{target}", "platform": "android"}),
            ("ابدأ تشغيل {target}", {"action": "open_android_app", "target": "{target}", "platform": "android"}),
        ],
        "english": [
            ("open android app {target}", {"action": "open_android_app", "target": "{target}", "platform": "android"}),
            ("launch {target} on phone", {"action": "open_android_app", "target": "{target}", "platform": "android"}),
            ("open {target} app", {"action": "open_android_app", "target": "{target}", "platform": "android"}),
            ("افتح app {target}", {"action": "open_android_app", "target": "{target}", "platform": "android"}),
        ],
        "adversarial": [
            ("افتح البرنامج الأخضر حق الرسايل", {"action": "open_android_app", "target": "WhatsApp", "platform": "android"}),
            ("شغل الخرائط وين انا", {"action": "open_android_app", "target": "Google Maps", "platform": "android"}),
            ("open the chat thing on mobile", {"action": "open_android_app", "target": "Telegram", "platform": "android"}),
        ],
        "targets": ["WhatsApp", "Telegram", "Camera", "YouTube", "Settings", "Gallery", "Google Maps", "Gmail"]
    },
    "toggle_setting": {
        "colloquial": [
            ("قفل الواي فاي", {"action": "toggle_setting", "target": "wifi", "args": {"state": "off"}, "platform": "android"}),
            ("شغل البلوتوث الحين", {"action": "toggle_setting", "target": "bluetooth", "args": {"state": "on"}, "platform": "android"}),
            ("ولع كشاف الجوال", {"action": "toggle_setting", "target": "flashlight", "args": {"state": "on"}, "platform": "android"}),
            ("طفي النت من الجوال", {"action": "toggle_setting", "target": "mobile_data", "args": {"state": "off"}, "platform": "android"}),
            ("حط وضع الطيران", {"action": "toggle_setting", "target": "airplane_mode", "args": {"state": "on"}, "platform": "android"}),
        ],
        "msa": [
            ("قم بتعطيل الاتصال اللاسلكي واي فاي", {"action": "toggle_setting", "target": "wifi", "args": {"state": "off"}, "platform": "android"}),
            ("فعل البلوتوث", {"action": "toggle_setting", "target": "bluetooth", "args": {"state": "on"}, "platform": "android"}),
            ("قم بتشغيل المصباح اليدوي", {"action": "toggle_setting", "target": "flashlight", "args": {"state": "on"}, "platform": "android"}),
            ("فعل وضع الطيران في الهاتف", {"action": "toggle_setting", "target": "airplane_mode", "args": {"state": "on"}, "platform": "android"}),
        ],
        "english": [
            ("turn off wifi", {"action": "toggle_setting", "target": "wifi", "args": {"state": "off"}, "platform": "android"}),
            ("turn on bluetooth", {"action": "toggle_setting", "target": "bluetooth", "args": {"state": "on"}, "platform": "android"}),
            ("toggle flashlight on", {"action": "toggle_setting", "target": "flashlight", "args": {"state": "on"}, "platform": "android"}),
            ("disable mobile data", {"action": "toggle_setting", "target": "mobile_data", "args": {"state": "off"}, "platform": "android"}),
            ("طفي الـ bluetooth", {"action": "toggle_setting", "target": "bluetooth", "args": {"state": "off"}, "platform": "android"}),
        ],
        "adversarial": [
            ("ما بدي انترنت دحين", {"action": "toggle_setting", "target": "wifi", "args": {"state": "off"}, "platform": "android"}),
            ("ظلام نور لي", {"action": "toggle_setting", "target": "flashlight", "args": {"state": "on"}, "platform": "android"}),
            ("airplane mode please", {"action": "toggle_setting", "target": "airplane_mode", "args": {"state": "on"}, "platform": "android"}),
        ]
    },
    "send_message": {
        "colloquial": [
            ("ارسل رسالة لأحمد {text}", {"action": "send_message", "target": "أحمد", "args": {"text": "{text}"}, "platform": "android"}),
            ("ابعث واتساب للوالد {text}", {"action": "send_message", "target": "الوالد", "args": {"text": "{text}"}, "platform": "android"}),
            ("دز مسج لسالم {text}", {"action": "send_message", "target": "سالم", "args": {"text": "{text}"}, "platform": "android"}),
            ("ارسل ل {target} قله {text}", {"action": "send_message", "target": "{target}", "args": {"text": "{text}"}, "platform": "android"}),
        ],
        "msa": [
            ("أرسل رسالة نصية إلى {target} محتواها: {text}", {"action": "send_message", "target": "{target}", "args": {"text": "{text}"}, "platform": "android"}),
            ("قم بإرسال رسالة إلى الرقم {target} تقول فيها {text}", {"action": "send_message", "target": "{target}", "args": {"text": "{text}"}, "platform": "android"}),
            ("أرسل إلى {target}: {text}", {"action": "send_message", "target": "{target}", "args": {"text": "{text}"}, "platform": "android"}),
        ],
        "english": [
            ("send message to {target}: {text}", {"action": "send_message", "target": "{target}", "args": {"text": "{text}"}, "platform": "android"}),
            ("text {target} saying {text}", {"action": "send_message", "target": "{target}", "args": {"text": "{text}"}, "platform": "android"}),
            ("ارسل message to {target}: {text}", {"action": "send_message", "target": "{target}", "args": {"text": "{text}"}, "platform": "android"}),
            ("send sms to {target} with content {text}", {"action": "send_message", "target": "{target}", "args": {"text": "{text}"}, "platform": "android"}),
        ],
        "adversarial": [
            ("قل له جاي في الطريق", {"action": "send_message", "target": "Recent Contact", "args": {"text": "جاي في الطريق"}, "platform": "android"}),
            ("ارسل تم بنجاح", {"action": "send_message", "target": "+967770000000", "args": {"text": "تم بنجاح"}, "platform": "android"}),
            ("text mom im on my way", {"action": "send_message", "target": "Mom", "args": {"text": "I'm on my way"}, "platform": "android"}),
        ],
        "targets": ["أحمد", "الوالد", "محمد", "سارة", "+966501234567", "+967771234567", "Manager", "Tech Support"],
        "texts": ["أنا في الطريق الآن", "هل تم تأكيد الموعد؟", "يرجى مراجعة البريد الإلكتروني", "وصلت للبيت الحمد لله", "All good", "See you tomorrow"]
    },
    "open_url": {
        "colloquial": [
            ("افتح موقع {target}", {"action": "open_url", "target": "{target}", "platform": "any"}),
            ("ادخل على رابط {target} دحين", {"action": "open_url", "target": "{target}", "platform": "pc"}),
            ("شوف لي هدا الموقع {target}", {"action": "open_url", "target": "{target}", "platform": "any"}),
            ("افتح لينك {target}", {"action": "open_url", "target": "{target}", "platform": "any"}),
        ],
        "msa": [
            ("افتح الموقع الإلكتروني {target}", {"action": "open_url", "target": "{target}", "platform": "pc"}),
            ("قم بزيارة الرابط {target}", {"action": "open_url", "target": "{target}", "platform": "any"}),
            ("تصفح الرابط التالي: {target}", {"action": "open_url", "target": "{target}", "platform": "pc"}),
        ],
        "english": [
            ("open url {target}", {"action": "open_url", "target": "{target}", "platform": "any"}),
            ("navigate to website {target}", {"action": "open_url", "target": "{target}", "platform": "pc"}),
            ("browse to {target}", {"action": "open_url", "target": "{target}", "platform": "any"}),
            ("افتح site {target}", {"action": "open_url", "target": "{target}", "platform": "pc"}),
        ],
        "adversarial": [
            ("افتح الرابط داك مدري وينه", {"action": "open_url", "target": "https://google.com", "platform": "any"}),
            ("وريني الموقع حقهم", {"action": "open_url", "target": "https://github.com", "platform": "pc"}),
            ("go to that page", {"action": "open_url", "target": "https://news.ycombinator.com", "platform": "any"}),
        ],
        "targets": ["https://google.com", "https://github.com/tahadeab-ai/silent-exec", "https://youtube.com", "https://wikipedia.org", "https://arxiv.org", "https://kaggle.com"]
    }
}


def build_example(action: str, category: str, rng: random.Random) -> Tuple[str, Dict[str, Any]]:
    config = TEMPLATES[action]
    templates = config.get(category, config.get("msa"))
    template_str, payload_template = rng.choice(templates)

    # Sub-entities
    target = rng.choice(config.get("targets", ["default"])) if "targets" in config else ""
    folder = rng.choice(config.get("folders", ["Documents"])) if "folders" in config else ""
    text = rng.choice(config.get("texts", ["Hello"])) if "texts" in config else ""
    level = rng.choice(config.get("levels", [50])) if "levels" in config else 50

    # Format user prompt
    user_prompt = template_str.format(target=target, folder=folder, text=text, level=level)

    # Format payload
    payload: Dict[str, Any] = {}
    for k, v in payload_template.items():
        if isinstance(v, str):
            payload[k] = v.format(target=target, folder=folder, text=text, level=level)
        elif isinstance(v, dict):
            sub_dict = {}
            for sk, sv in v.items():
                if isinstance(sv, str) and "{level}" in sv:
                    sub_dict[sk] = int(level)
                elif isinstance(sv, str):
                    sub_dict[sk] = sv.format(target=target, folder=folder, text=text, level=level)
                else:
                    sub_dict[sk] = sv
            payload[k] = sub_dict
        else:
            payload[k] = v

    # Confidence score assignment
    if category == "adversarial":
        # Adversarial / ambiguous cases have confidence between 0.45 and 0.62
        payload["confidence"] = round(rng.uniform(0.45, 0.62), 2)
    else:
        # Standard unambiguous cases have high confidence between 0.82 and 0.99
        payload["confidence"] = round(rng.uniform(0.82, 0.99), 2)

    return user_prompt, payload


def generate_dataset(total_count: int, seed: int) -> List[Dict[str, Any]]:
    rng = random.Random(seed)

    # Target distribution:
    # 40% Colloquial
    # 35% Modern Standard Arabic (MSA)
    # 25% English / Code-switching
    # with ~10% adversarial overall within each or selected
    # We allocate 10% adversarial, 35% colloquial, 32% msa, 23% english
    # which sums to 100% and fulfills the requirements nicely.
    categories = ["colloquial"] * 36 + ["msa"] * 30 + ["english"] * 24 + ["adversarial"] * 10

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
            parsed_json = json.loads(raw_assistant)
            validator.validate(parsed_json)
        except Exception as e:
            print(f"FATAL: Schema validation failed at sample #{idx}: {e}", file=sys.stderr)
            print(f"Payload: {raw_assistant}", file=sys.stderr)
            sys.exit(1)

        total_validated += 1
        meta = sample["meta"]
        action_counts[meta["action"]] = action_counts.get(meta["action"], 0) + 1
        category_counts[meta["category"]] = category_counts.get(meta["category"], 0) + 1

    # Split: 80% train, 10% val, 10% test
    n_total = len(dataset)
    n_train = int(n_total * 0.8)
    n_val = int(n_total * 0.1)

    train_set = dataset[:n_train]
    val_set = dataset[n_train:n_train + n_val]
    test_set = dataset[n_train + n_val:]

    out_dir.mkdir(parents=True, exist_ok=True)

    def write_jsonl(path: Path, data: List[Dict[str, Any]]) -> None:
        with open(path, "w", encoding="utf-8") as f:
            for item in data:
                # Strip internal metadata before exporting clean ChatML JSONL
                clean_record = {"messages": item["messages"]}
                f.write(json.dumps(clean_record, ensure_ascii=False) + "\n")

    write_jsonl(out_dir / "train.jsonl", train_set)
    write_jsonl(out_dir / "val.jsonl", val_set)
    write_jsonl(out_dir / "test.jsonl", test_set)

    return {
        "total": n_total,
        "train": len(train_set),
        "val": len(val_set),
        "test": len(test_set),
        "validated": total_validated,
        "action_counts": action_counts,
        "category_counts": category_counts
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Deterministic Bilingual Dataset Generator for SilentExec")
    parser.add_argument("--count", type=int, default=3600, help="Total number of examples to generate")
    parser.add_argument("--seed", type=int, default=42, help="Random seed for deterministic generation")
    parser.add_argument("--out-dir", type=str, default="/tmp/silent_exec_data", help="Output directory for jsonl files")
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parent.parent
    schema_path = repo_root / "schemas" / "action_schema.json"

    if not schema_path.is_file():
        print(f"Schema not found at {schema_path}", file=sys.stderr)
        return 1

    with open(schema_path, "r", encoding="utf-8") as f:
        action_schema = json.load(f)

    out_dir = Path(args.out_dir)
    print(f"Generating {args.count} examples with seed {args.seed}...")
    dataset = generate_dataset(args.count, args.seed)

    print("Validating all payloads against schemas/action_schema.json and saving...")
    stats = validate_and_save(dataset, out_dir, action_schema)

    print("\n" + "=" * 60)
    print("SILENTEXEC DATASET GENERATION SUMMARY")
    print("=" * 60)
    print(f"Total Generated & Validated: {stats['validated']} / {stats['total']} (100% pass)")
    print(f"Train set: {stats['train']} ({stats['train']/stats['total']*100:.1f}%)")
    print(f"Val set:   {stats['val']} ({stats['val']/stats['total']*100:.1f}%)")
    print(f"Test set:  {stats['test']} ({stats['test']/stats['total']*100:.1f}%)")
    print("\nLinguistic Distribution:")
    for cat, count in stats["category_counts"].items():
        print(f"  - {cat:12s}: {count:4d} ({count/stats['total']*100:.1f}%)")
    print("\nAction Distribution (all 15 balanced):")
    for action, count in stats["action_counts"].items():
        print(f"  - {action:18s}: {count:4d} ({count/stats['total']*100:.1f}%)")
    print("=" * 60)
    print(f"Datasets written successfully to: {out_dir}\n")

    return 0


if __name__ == "__main__":
    sys.exit(main())
