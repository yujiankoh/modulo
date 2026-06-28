# modulo
A companion platform for Singaporean students to manage academic tasks, parse their timetables, and stay focused through gamified study sessions.

## Level of Achievement
Apollo 11

## Team
- Koh Yu Jian
- Chen Ling Song

## Overview
modulo turns a photo of a school timetable into a structured, editable schedule, then layers task management, a calendar view, and a gamified study experience on top of it. The goal is a single, easy-to-use home for everything academic.

## Features
### Google Drive Sync
Use Google Drive to sync notes and data between the app and website. Users can also choose to continue with local save only.

### Task Tracker
A task section where the students can categorise their task into different sub-categories. When adding the task, students can select task deadline, type of task, and frequency of reminders

### Timetable Parser
Snap or upload a timetable image and have it parsed into structured data using the Gemini vision API, with a manual edit screen for corrections.

### Study Session
Users can start a study session and keep track of their productivity. Once the session ends, the app will update the calendar to reflect on the productivity of the day, which the user can select. A city-building mechanic that rewards focused study time. The longer the user studies in the session, the bigger the simulated city game will grow.

### PDF and Notes Support
Support for students to store personal notes and other documents needed for school (links or pdf) in the modules page, and support the ability to annotate on the documents directly.

## Tech Stack
| Area | Technology |
|------|-----------|
| Design | Figma |
| Mobile app | Kotlin, Jetpack Compose (Material 3) |
| Website | HTML / CSS / JavaScript |
| Navigation | Navigation Compose |
| Auth & Sync | Google Sign-In (Credential Manager), Google Drive API (`drive.appdata` scope) |
| Timetable parsing | Gemini Vision API |
| Build | Gradle (Kotlin DSL), AGP |
