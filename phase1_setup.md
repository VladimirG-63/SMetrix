# Фаза 1: Setup — Smetrix Android
> Шаги 1.1 – 1.9 | Версии актуальны на май 2026

---

## Версии библиотек (справочная таблица)

| Библиотека | Версия |
|---|---|
| `androidx.room` | **2.8.4** |
| `com.squareup.retrofit2` | **3.0.0** |
| `com.squareup.okhttp3` | **5.3.2** |
| `androidx.work` | **2.11.2** |
| `androidx.security:security-crypto` | **1.1.0** |
| `androidx.lifecycle` | **2.10.0** |
| `com.google.guava:guava` | **33.6.0-android** |

> [!NOTE]
> Room 3.0 пока в alpha и требует KSP + Kotlin. Для Java-проекта используем стабильный **2.8.4** с `annotationProcessor`.
> Retrofit 3.0.0 обратно совместим с Java, annotation processor для него не нужен.

---

## `build.gradle (app)` — полный блок `android { ... }`

```groovy
android {
    namespace "com.smetrix.app"
    compileSdk 35

    defaultConfig {
        applicationId "com.smetrix.app"
        minSdk 26
        targetSdk 35
        versionCode 1
        versionName "1.0.0"
    }

    // ШАГ 1.6 — buildConfig + buildConfigField для API_BASE_URL
    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            debuggable true
            minifyEnabled false
            buildConfigField "String", "API_BASE_URL", "\"https://api-dev.smetrix.ru/api/v1/\""
        }
        release {
            debuggable false
            minifyEnabled true
            shrinkResources true
            proguardFiles getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            buildConfigField "String", "API_BASE_URL", "\"https://api.smetrix.ru/api/v1/\""
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
}
```

---

## `build.gradle (app)` — полный блок `dependencies { ... }`

```groovy
dependencies {

    // ─────────────────────────────────────────────────────────
    // ШАГ 1.1 — Room (ORM / локальная БД)
    // ─────────────────────────────────────────────────────────
    def room_version = "2.8.4"

    // Основной рантайм Room
    implementation "androidx.room:room-runtime:$room_version"

    // ШАГ 1.7 — annotation processor для генерации кода Room (только Java!)
    annotationProcessor "androidx.room:room-compiler:$room_version"

    // Поддержка LiveData в @Query-методах DAO
    implementation "androidx.room:room-ktx:$room_version"

    // Тестовые утилиты Room (для androidTest)
    androidTestImplementation "androidx.room:room-testing:$room_version"

    // ─────────────────────────────────────────────────────────
    // ШАГ 1.2 — Retrofit + OkHttp (сетевой стек)
    // ─────────────────────────────────────────────────────────
    def retrofit_version = "3.0.0"
    def okhttp_version  = "5.3.2"

    // HTTP-клиент (retrofit внутри использует okhttp, но явная зависимость
    // даёт контроль над версией)
    implementation "com.squareup.okhttp3:okhttp:$okhttp_version"

    // Interceptor для логирования HTTP-запросов/ответов (только debug!)
    debugImplementation "com.squareup.okhttp3:logging-interceptor:$okhttp_version"

    // Retrofit — типобезопасный HTTP-клиент
    implementation "com.squareup.retrofit2:retrofit:$retrofit_version"

    // Конвертер: автоматическая (де)сериализация JSON через Gson
    implementation "com.squareup.retrofit2:converter-gson:$retrofit_version"

    // ─────────────────────────────────────────────────────────
    // ШАГ 1.3 — WorkManager (фоновая синхронизация)
    // ─────────────────────────────────────────────────────────
    def work_version = "2.11.2"

    // Java-совместимый артефакт WorkManager (без KTX)
    implementation "androidx.work:work-runtime:$work_version"

    // ─────────────────────────────────────────────────────────
    // ШАГ 1.4 — Security-Crypto (EncryptedSharedPreferences для токена)
    // ─────────────────────────────────────────────────────────
    implementation "androidx.security:security-crypto:1.1.0"

    // ─────────────────────────────────────────────────────────
    // ШАГ 1.5 — Вспомогательные библиотеки
    // ─────────────────────────────────────────────────────────

    // Guava (Android-вариант): Lists.partition() в SyncWorker,
    // и другие утилиты коллекций
    implementation "com.google.guava:guava:33.6.0-android"

    // Lifecycle: LiveData (для Repository → UI) + ViewModel
    def lifecycle_version = "2.10.0"
    implementation "androidx.lifecycle:lifecycle-livedata:$lifecycle_version"
    implementation "androidx.lifecycle:lifecycle-viewmodel:$lifecycle_version"

    // ─────────────────────────────────────────────────────────
    // Стандартные зависимости проекта (оставить как есть)
    // ─────────────────────────────────────────────────────────
    implementation "androidx.appcompat:appcompat:1.7.0"
    implementation "com.google.android.material:material:1.12.0"
    implementation "androidx.constraintlayout:constraintlayout:2.2.1"

    // Unit-тесты
    testImplementation "junit:junit:4.13.2"
    androidTestImplementation "androidx.test.ext:junit:1.2.1"
    androidTestImplementation "androidx.test.espresso:espresso-core:3.6.1"
}
```

> [!IMPORTANT]
> `logging-interceptor` объявлен через `debugImplementation` — он **не попадёт в release-сборку**.
> В коде используйте его инициализацию внутри `if (BuildConfig.DEBUG)` блока.

> [!TIP]
> `room-ktx` добавлен для поддержки `LiveData`-возвращаемых типов в DAO (`LiveData<List<T>>`).
> Без него Room не сможет генерировать корректный код для таких методов даже в Java-проекте.

---

## ШАГ 1.9 — Структура пакетов для ручного создания

Базовый путь: `app/src/main/java/com/smetrix/app/`

```
com.smetrix.app/
│
├── db/
│   ├── converter/        ← BigDecimalConverter (Фаза 2)
│   ├── entity/           ← все @Entity-классы (Фаза 2)
│   └── dao/              ← все @Dao-интерфейсы (Фаза 3)
│
├── model/                ← ENUM-ы: SyncState, ItemStatus; UuidGenerator (Фаза 2/3)
│
├── repository/           ← ProjectRepository, RoomRepository, и др. (Фаза 3)
│
├── network/
│   ├── dto/              ← DTO-классы для Retrofit (Фаза 2+)
│   └── sync/             ← SyncManager, SyncWorker (Фаза 2+)
│
├── viewmodel/            ← ViewModel-классы (Фаза 4+)
│
└── ui/
    ├── project/          ← фрагменты/активити списка проектов (Фаза 5+)
    ├── room/             ← фрагменты комнат (Фаза 5+)
    ├── worker/           ← фрагменты работников (Фаза 5+)
    └── common/           ← базовые/общие UI-компоненты (Фаза 5+)
```

### Как создать в Android Studio

1. В панели **Project** переключиться в режим **Project** (не Android).
2. Развернуть путь `app → src → main → java → com → smetrix → app`.
3. ПКМ на `app` → **New → Package** → вводить каждый пакет (например, `com.smetrix.app.db.converter`).
4. Повторить для всех 11 пакетов выше.

> [!NOTE]
> Android Studio может не показывать пустые пакеты в режиме **Android view**. Переключитесь в **Project view** для проверки.

---

## Чеклист Фазы 1

- [ ] **1.1** Room runtime + compiler + testing добавлены
- [ ] **1.2** Retrofit + converter-gson + okhttp + logging-interceptor добавлены
- [ ] **1.3** WorkManager добавлен
- [ ] **1.4** Security-Crypto добавлен
- [ ] **1.5** Guava + Lifecycle LiveData/ViewModel добавлены
- [ ] **1.6** `buildConfigField` для `API_BASE_URL` прописан в debug и release
- [ ] **1.7** `annotationProcessor` для Room Compiler указан
- [ ] **1.8** `Sync Now` → сборка без ошибок ✅
- [ ] **1.9** Все 11 пакетов созданы вручную ✅
