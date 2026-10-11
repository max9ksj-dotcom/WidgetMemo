# 위젯 메모 (갤럭시용 위젯 메모장)

Kotlin + Jetpack Compose + Room + DataStore + App Widget(RemoteViews).

## 빌드
1. Android Studio(Koala 이상 권장, JDK 17)에서 이 폴더를 연다. Gradle 동기화가 끝나면 자동으로 래퍼가 준비된다.
2. 휴대폰 연결 후 Run, 또는 메뉴 Build > Build APK(s).
   CLI: Gradle 8.7 설치 후 `gradle wrapper` 한 번 실행 → `./gradlew assembleDebug`
   결과물: app/build/outputs/apk/debug/app-debug.apk

## 갤럭시에 설치
- 설정 > 휴대전화 정보 > 소프트웨어 정보 > 빌드번호 7번 탭 → 개발자 옵션 > USB 디버깅 켜기 → USB 연결 후 Android Studio에서 Run
- 또는 app-debug.apk를 휴대폰으로 옮겨 설치(‘출처를 알 수 없는 앱’ 허용 필요)

## 위젯 추가 (One UI)
홈 화면 빈 곳 길게 누르기 → 위젯 → ‘위젯 메모’ → 홈 화면에 추가 → 표시할 메모 선택.
위젯을 길게 눌러 ‘설정’을 고르면 메모를 다시 선택할 수 있다.

## 위젯 동작
- 체크리스트 위젯: 항목 행을 누르면 앱을 열지 않고 바로 체크/해제 (BroadcastReceiver가 DB를 직접 갱신)
- 제목 또는 ‘+’ 버튼: 앱의 편집 화면으로 이동(‘+’는 새 항목이 추가된 상태로 열림)
- 위젯 안에서 글자를 직접 타이핑하는 기능은 App Widget 구조상 지원되지 않아 앱 편집 화면으로 연결한다.

## 주요 파일
app/src/main/java/com/example/widgetmemo/
- Data.kt — Room 엔티티/DAO/DB (Memo, CheckItem, WidgetBinding)
- NoteRepository.kt — 앱 쓰기 경로(뮤텍스 직렬화 + 위젯 갱신)
- MemoWidgetProvider.kt / MemoWidgetService.kt / ToggleReceiver.kt / WidgetUpdater.kt — 위젯
- WidgetConfigActivity.kt — 위젯별 메모 선택
- MainActivity.kt, NotesViewModel.kt, Screens.kt, Theme.kt, Settings.kt — 앱 UI
app/src/androidTest/.../NoteDaoTest.kt — DAO 계측 테스트
