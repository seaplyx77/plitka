# AGENTS.md — «Плитка» (Plitka)

Плиточный лаунчер для Android в духе старых Windows Phone / Lumia, но современный.
Проект пишется вручную, без сторонних библиотек, без Google-сервисов и без сети.

---

## 1. Что это за проект

| | |
|---|---|
| Название | **Плитка** (Plitka) |
| Пакет | `com.plitka.launcher` |
| Язык | Kotlin |
| Сборка | Gradle 8.7 + AGP 8.5.2 + Kotlin 1.9.24 |
| compileSdk / targetSdk | 34 (Android 14) |
| minSdk | 26 (Android 8) |
| Зависимости | **нет** (только Android framework, AndroidX отключён) |
| Интернет | не используется |
| GApps / Play Services | **запрещены** |

Целевое устройство: Android 14 (LineageOS 21), root через Magisk (только для отладки).
Проект не привязан к конкретной модели — работает на любом Android 8.0+.

---

## 2. Расположение инструментов (всё на диске G:)

Пути ниже — с машины автора; в коде и сборке они нигде не зашиты.

```
G:\Android\jdk17\jdk-17.0.20.1+1     портативный JDK 17 (Temurin)
G:\Android\Sdk                        Android SDK (platform-34, build-tools 34.0.0)
G:\Android\gradle-8.7                 Gradle
G:\Android\gradle-home                GRADLE_USER_HOME (кэш, не засорять C:)
G:\Android\Plitka                     сам проект
G:\Android\plitka-release.jks        ключ подписи release
G:\Android\keystore.properties       пароли к ключу (хранить только вне проекта)
```

**Правило: ничего не скачивать и не устанавливать на диск C:.**

---

## 3. Сборка

PowerShell:

```powershell
$env:JAVA_HOME='G:\Android\jdk17\jdk-17.0.20.1+1'
$env:ANDROID_HOME='G:\Android\Sdk'
$env:ANDROID_SDK_ROOT='G:\Android\Sdk'
$env:GRADLE_USER_HOME='G:\Android\gradle-home'
$env:Path="$env:JAVA_HOME\bin;$env:Path"

& 'G:\Android\gradle-8.7\bin\gradle.bat' -p 'G:\Android\Plitka' assembleDebug --console=plain
```

APK: `G:\Android\Plitka\app\build\outputs\apk\debug\app-debug.apk`

Фильтр только ошибок:

```powershell
... assembleDebug 2>&1 | Select-String -Pattern '^e: |error:|BUILD ' | Out-String -Width 220
```

---

## 4. Установка на телефон

Отладка по Wi-Fi, порт меняется после перезагрузки телефона. Серийник ищем динамически:

```powershell
adb devices
$d = (adb devices | Select-String 'device$' | ForEach-Object { ($_ -split '\s+')[0] } | Select-Object -First 1)

adb -s $d install -r 'G:\Android\Plitka\app\build\outputs\apk\debug\app-debug.apk'
adb -s $d shell cmd package set-home-activity com.plitka.launcher/.MainActivity
adb -s $d shell input keyevent KEYCODE_HOME
```

Скриншот для проверки:

```powershell
adb -s $d shell screencap -p /sdcard/shot.png
adb -s $d pull /sdcard/shot.png 'G:\Android\shot.png'
```

Разрешение на счётчики уведомлений (если не выдано через настройки):

```powershell
adb -s $d shell settings put secure enabled_notification_listeners "com.plitka.launcher/com.plitka.launcher.service.NotiListener"
```

Если телефон пропал из `adb devices` — искать через mDNS:

```powershell
adb mdns services
adb connect <ip>:<port>
```

Спаривание (первый раз):

```powershell
adb pair <ip>:<pair-port> <6-цифр>
```

Порт спаривания живёт только пока открыт диалог с кодом.

---

## 5. Структура кода

```
app/src/main/java/com/plitka/launcher/
├── MainActivity.kt            домашний экран: ScrollView + TileBoardView, жесты, меню плитки
├── data/
│   ├── Tile.kt                модель плитки + JSON (id, type, pkg, label, color, spanX, spanY)
│   ├── TileStore.kt           загрузка/сохранение раскладки в SharedPreferences, дефолтный набор
│   ├── AppRepo.kt             список запускаемых приложений, иконки, запуск
│   └── Prefs.kt               настройки: columns, accent, radiusDp, opacity, labels
├── ui/
│   ├── TileBoardView.kt       кастомный ViewGroup: сетка 3–6 колонок, вертикальный скролл
│   ├── TileView.kt            отрисовка одной плитки (скругление, иконка, подпись, часы)
│   ├── AppListActivity.kt     список приложений по алфавиту с буквенными заголовками
│   └── SettingsActivity.kt    настройки (программный UI, без XML)
└── util/Ui.kt                 dp-хелперы для Context и View
```

Типы плиток (`Tile.type`): `app`, `clock`, `apps`, `settings`.

---

## 6. Правила разработки

1. **Никаких зависимостей.** Только `android.*` и `org.json`. AndroidX отключён (`android.useAndroidX=false`).
2. **Никакого интернета и никаких GApps.** Не добавлять `com.google.*`, Play Services, аналитику.
3. **UI на русском**, комментарии в коде — тоже по-русски.
4. **Тёмная тема по умолчанию**, чёрный фон `#0B0B0D`, акцент — палитра Windows из `Tile.PALETTE`.
5. **Без XML-разметки**, где это проще: экраны настроек и списка собираются кодом.
6. `dp`/`sp` — только через хелперы из `util/Ui.kt`, не хардкодить пиксели.
7. Не ломать совместимость с Android 8 (minSdk 26): избегать API 27+ без проверок.

---

## 7. Известные грабли

- **`<queries>` в манифесте обязателен.** Без него на Android 11+ видно только часть приложений
  (у нас было 6 вместо всех). Уже добавлен — не удалять.
- **`dpf`/`dpi` объявлены для `Context` и для `View`.** Внутри `View` вызывать без `context.`,
  в инициализаторах полей — через `context.dpf(...)`.
- **Split-APK.** Публичные APK лаунчеров часто приходят как base + split — ставить только
  `adb install-multiple`, иначе `INSTALL_FAILED_MISSING_SPLIT`.
- **`View.getX()` уже включает translationX.** В расчёте точки броска нельзя делать
  `view.x + view.translationX` — плитка «уедет» на двойное смещение. Только `view.left + view.translationX`.
- **`window.insetsController` падает с NPE до создания DecorView** — вызывать только через
  `window.decorView.windowInsetsController` (или после `setContentView`).
- **`set-home-activity`** работает, но штатный лаунчер лучше не отключать, пока новый не проверен.
- **Экран/лок** — при блокировке телефона `scrcpy` и adb-сессия могут отвалиться.

---

## 8. Статус и роадмап

- [x] **M0** — тулчейн, пустой проект, сборка и установка
- [x] **M1** — сетка плиток, запуск приложений, список по алфавиту, базовые настройки
- [x] **M2** — счётчики уведомлений (NotificationListenerService), добавление приложений долгим нажатием в списке
- [x] **M3** — перетаскивание плиток (с анимацией, порядок сохраняется), папки (зажать над плиткой), скрытие подписей
- [ ] **M4** — обои, темы, анимации запуска, изменение размера 2×2, edge-to-edge в остальных экранах
- [ ] **M5** — иконка, экран «О приложении», подпись release-сборки

---

## 9. Как проверять изменения

1. Собрать (`assembleDebug`).
2. Поставить на телефон (`adb install -r`).
3. `adb shell input keyevent KEYCODE_HOME`.
4. Скриншот → посмотреть глазами.
5. Если краш — `adb logcat -d | Select-String 'plitka|AndroidRuntime'`.
6. Жесты: `input swipe` не подходит для «долгое нажатие + перетаскивание» — он начинает движение сразу
   и сбивает таймер. Работает только последовательность `input motionevent DOWN x y` → пауза 600–800 мс
   → несколько `MOVE` → `UP`. Координаты брать из `uiautomator dump`, а не на глаз.
7. Порядок плиток проверять в `run-as com.plitka.launcher cat .../shared_prefs/plitka.xml`.

---

## 10. Публикация на GitHub

- В репозиторий идут только исходники: `.gitignore` исключает `build/`, `.gradle/`, `dist/`,
  `local.properties`, `keystore.properties`, `*.jks`, `*.apk`.
- Ключ подписи и пароли хранятся **вне проекта**: `G:\Android\plitka-release.jks` и
  `G:\Android\keystore.properties`.
- Gradle wrapper лежит в репозитории, сборка у любого пользователя: `gradlew assembleDebug`.
- Готовые APK прикладывать к GitHub Releases, а не коммитить.
- `README.md` — лицо проекта; при изменении функций обновлять список возможностей.
