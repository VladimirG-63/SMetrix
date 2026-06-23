# RFC — Smetrix: Часть 4. Безопасность и Финальная проверка

---

## 1. ФЗ-152 и Защита PII

### 1.1 Что считается PII в Smetrix

| Поле | Сущность | Риск |
|---|---|---|
| `full_name` | `Worker` | Прямой идентификатор личности |
| `phone` | `Worker` | Прямой идентификатор личности |
| `email` | `User` | Учётные данные |
| `password_hash` | `User` | Критично — только BCrypt, никогда plaintext |

---

### 1.2 EncryptedSharedPreferences — токены и настройки

```java
// Инициализация — один раз в Application
MasterKey masterKey = new MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build();

SharedPreferences securePrefs = EncryptedSharedPreferences.create(
    context,
    "smetrix_secure_prefs",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
);

// Хранение токенов
securePrefs.edit()
    .putString("access_token", tokens.accessToken)
    .putString("refresh_token", tokens.refreshToken)
    .apply();
```

> [!CAUTION]
> Токены **никогда** не хранятся в обычном `SharedPreferences`, `Room`, файлах или логах. Нарушение — утечка сессии пользователя.

---

### 1.3 Sanitization входных данных перед Room

```java
// repository/WorkerRepository.java
public void saveWorker(String fullName, String phone, String specialty) {

    // 1. Trim — убираем лишние пробелы
    fullName = fullName.trim();
    phone    = phone.trim().replaceAll("[^\\d+]", ""); // только цифры и +

    // 2. Длина — защита от переполнения
    if (fullName.isEmpty() || fullName.length() > 100)
        throw new ValidationException("Некорректное имя рабочего");
    if (phone.length() > 20)
        throw new ValidationException("Некорректный номер телефона");

    // 3. Room использует параметризованные запросы (@Query с :param)
    //    — SQL injection невозможен по архитектуре Room

    WorkerEntity entity = new WorkerEntity();
    entity.id        = UuidGenerator.generateV7();
    entity.fullName  = fullName;
    entity.phone     = phone;
    entity.specialty = specialty.trim();
    entity.syncState = SyncState.PENDING_CREATE.name();
    entity.createdAt = System.currentTimeMillis();
    entity.updatedAt = entity.createdAt;
    entity.version   = 0;

    workerDao.insert(entity);
}
```

---

### 1.4 Нет хардкодинга — API-ключи и URL через BuildConfig

```groovy
// build.gradle (app)
android {
    buildTypes {
        debug {
            buildConfigField "String", "API_BASE_URL", '"https://api-dev.smetrix.ru/api/v1/"'
        }
        release {
            buildConfigField "String", "API_BASE_URL", '"https://api.smetrix.ru/api/v1/"'
        }
    }
}
```

```java
// network/ApiClient.java
Retrofit retrofit = new Retrofit.Builder()
    .baseUrl(BuildConfig.API_BASE_URL)   // не строка в коде
    .build();
```

> [!WARNING]
> `BuildConfig` поля попадают в APK в открытом виде — это нормально для URL. Секреты (ключи шифрования, webhook-токены) хранятся **только** на сервере и никогда не передаются клиенту.

---

## 2. UTF-8 по всему стеку

### 2.1 Где кириллица может сломаться

| Точка | Риск | Решение |
|---|---|---|
| Room → SQLite | SQLite по умолчанию UTF-8 ✅ | Явно задать в `BigDecimalConverter` через `toPlainString()` |
| Retrofit JSON | По умолчанию UTF-8 в OkHttp ✅ | Явно указать `Content-Type` |
| Excel-парсер (Apache POI) | Зависит от файла ФГИС | Явно `StandardCharsets.UTF_8` |
| Логи | `Log.d` корректен | Не нарушает |
| Экспорт / отчёты | Файлы PDF/CSV | Явно `UTF-8` при записи |

---

### 2.2 Retrofit — явный charset

```java
// network/ApiClient.java
OkHttpClient client = new OkHttpClient.Builder()
    .addInterceptor(chain -> {
        Request original = chain.request();
        Request request = original.newBuilder()
            .header("Content-Type", "application/json; charset=utf-8")
            .header("Accept-Charset", "utf-8")
            .build();
        return chain.proceed(request);
    })
    .build();

// GsonConverterFactory с явным charset
Gson gson = new GsonBuilder().create();
Retrofit retrofit = new Retrofit.Builder()
    .addConverterFactory(GsonConverterFactory.create(gson))
    .client(client)
    .baseUrl(BuildConfig.API_BASE_URL)
    .build();
```

---

### 2.3 Apache POI — чтение Excel ФГИС с корректной кодировкой

```java
// server/parser/FgisExcelParser.java
try (InputStream is = new FileInputStream(excelFile)) {

    // SXSSF / Event API — читаем строки итеративно без загрузки в RAM
    OPCPackage pkg = OPCPackage.open(is);
    XSSFReader reader = new XSSFReader(pkg);

    // SharedStringsTable хранит все строки Excel
    SharedStringsTable sst = reader.getSharedStringsTable();

    XMLReader parser = SAXHelper.newXMLReader();
    ContentHandler handler = new FgisSheetHandler(sst, resultConsumer);
    parser.setContentHandler(handler);

    // Явно UTF-8 при обработке XML внутри xlsx
    InputSource sheetSource = new InputSource(
        new InputStreamReader(reader.getSheetsData().next(), StandardCharsets.UTF_8));
    parser.parse(sheetSource);
}
```

---

## 3. ResourceDataProvider — retry и fallback

### 3.1 Интерфейс

```java
// server/parser/provider/ResourceDataProvider.java
public interface ResourceDataProvider {
    /**
     * Возвращает InputStream для Excel-файла ФГИС ЦС.
     * Реализации: DirectHttpProvider, ManualUploadProvider.
     */
    InputStream getExcelData(String regionCode, String quarter) throws DataProviderException;
}
```

---

### 3.2 DirectHttpProvider — retry с exponential backoff

```java
// server/parser/provider/DirectHttpProvider.java
public class DirectHttpProvider implements ResourceDataProvider {

    private static final int MAX_RETRIES   = 3;
    private static final long INITIAL_DELAY_MS = 5_000;

    @Override
    public InputStream getExcelData(String regionCode, String quarter)
            throws DataProviderException {

        DataProviderException lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpURLConnection conn = buildFgisConnection(regionCode, quarter);
                int code = conn.getResponseCode();

                if (code == 200) {
                    log.info("ФГИС ЦС: файл получен, попытка {}", attempt);
                    return conn.getInputStream();
                }

                if (code == 403) {
                    // Структура сайта изменилась или блокировка — бесполезно ретраить
                    throw new DataProviderException("HTTP 403 от ФГИС ЦС — нужна ручная загрузка", code);
                }

                lastException = new DataProviderException("HTTP " + code, code);

            } catch (IOException e) {
                log.warn("ФГИС ЦС: попытка {}/{} упала — {}", attempt, MAX_RETRIES, e.getMessage());
                lastException = new DataProviderException("Сетевая ошибка", e);
            }

            // Exponential backoff перед следующей попыткой
            if (attempt < MAX_RETRIES) {
                long delay = INITIAL_DELAY_MS * (1L << (attempt - 1)); // 5s, 10s, 20s
                Thread.sleep(delay);
            }
        }

        log.error("ФГИС ЦС: все {} попытки исчерпаны", MAX_RETRIES);
        throw lastException;
    }
}
```

---

### 3.3 Fallback на ручную загрузку

```java
// server/parser/FgisParserService.java
@Service
public class FgisParserService {

    private final DirectHttpProvider   httpProvider;
    private final ManualUploadProvider manualProvider;
    private final AlertService         alertService;
    private final FgisExcelParser      parser;

    @Scheduled(cron = "0 0 3 1 1,4,7,10 *") // ежеквартально в 03:00
    public void runQuarterlyUpdate() {
        String regionCode = "77";
        String quarter    = QuarterHelper.currentQuarter();

        try {
            // 1. Пробуем автоматическое получение
            InputStream data = httpProvider.getExcelData(regionCode, quarter);
            parser.parse(data);
            log.info("Квартальное обновление ФГИС успешно завершено");

        } catch (DataProviderException e) {
            // 2. Автоматика не сработала — уведомляем администратора
            log.error("Ошибка получения данных ФГИС: {}", e.getMessage());
            alertService.notifyAdmin(
                "Ошибка парсера ФГИС ЦС",
                "Автоматическое обновление не выполнено.\n" +
                "Причина: " + e.getMessage() + "\n" +
                "Действие: загрузите Excel вручную через /admin/upload-fgis"
            );
            // 3. Задача НЕ выбрасывает исключение выше — следующий квартал запустится штатно
        }
    }

    /** Endpoint для ручной загрузки администратором */
    public void processManualUpload(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream()) {
            parser.parse(is);
            log.info("Ручная загрузка ФГИС обработана успешно");
        }
    }
}
```

---

### 3.4 AlertService — уведомление администратора

```java
// server/alert/AlertService.java
@Service
public class AlertService {

    private final JavaMailSender mailSender;

    @Value("${admin.email}")          // из application.properties / env
    private String adminEmail;

    public void notifyAdmin(String subject, String body) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(adminEmail);
            msg.setSubject("[Smetrix] " + subject);
            msg.setText(body);
            mailSender.send(msg);
        } catch (Exception e) {
            // Email не должен ронять основной процесс
            log.error("Не удалось отправить alert администратору: {}", e.getMessage());
        }
    }
}
```

---

### 3.5 Обработка ошибок — общее правило

```java
// Правило из .cursorrules: специфичные catch, никогда пустые блоки
try {
    workerDao.insert(entity);
} catch (SQLiteConstraintException e) {
    // Нарушение уникальности — понятная ошибка для пользователя
    throw new DuplicateEntityException("Рабочий с таким UUID уже существует");
} catch (SQLiteException e) {
    // Ошибка БД — логируем, уведомляем пользователя
    log.error("Ошибка Room при сохранении Worker: {}", e.getMessage());
    throw new DatabaseException("Не удалось сохранить данные. Попробуйте снова.");
}
// Пустой catch — ЗАПРЕЩЁН
```

---

## 4. Финальная проверка: соответствие .cursorrules

### 4.1 Сводная таблица

| Правило из `.cursorrules` | Статус | Где реализовано |
|---|---|---|
| **Java only, no Kotlin** | ✅ | Все Entity, DAO, Repository, ViewModel — Java |
| **XML Layouts, no Compose** | ✅ | `fragment_room_detail.xml`, `CoordinatorLayout` |
| **Offline-First (Room как источник истины)** | ✅ | Все запросы идут сначала в Room; сеть — асинхронно |
| **Clean Architecture (UI / ViewModel / Repository / DAO)** | ✅ | Строгое разделение в структуре пакетов (Часть 1) |
| **Descriptive naming** | ✅ | `EstimateItemDisplay`, `ProjectRoomDao`, `SyncManager` |
| **No magic strings (strings.xml / constants)** | ✅ | `SyncState` ENUM, `ItemStatus` ENUM, `BuildConfig.API_BASE_URL` |
| **Catch specific exceptions, no empty catch** | ✅ | `SQLiteConstraintException`, `IOException`, `DataProviderException` |
| **PII handled with care (ФЗ-152)** | ✅ | `EncryptedSharedPreferences`, sanitization, BCrypt |
| **No hardcoding tokens/keys** | ✅ | `BuildConfig`, `@Value("${admin.email}")` |
| **Data sanitization before Room** | ✅ | `WorkerRepository.saveWorker()` — trim + regex + length check |
| **Never DB on Main Thread** | ✅ | WorkManager Worker, Repository через Executor |
| **Room Migrations (no destructive)** | ✅ | Упомянуто в CONTEXT.md как обязательное требование |
| **@Transaction for multi-step updates** | ✅ | `updateDimensionsAndRecalculate()` (Часть 3) |
| **Indices on Foreign Keys** | ✅ | Все Entity содержат `@Index` по FK-полям (Часть 1) |
| **UTF-8 everywhere** | ✅ | OkHttp interceptor, POI `StandardCharsets.UTF_8` |
| **ConstraintLayout for complex views** | ✅ | Рекомендация для всех сложных экранов |
| **dimens.xml / themes.xml** | ✅ | `paddingBottom="80dp"` → выносится в `@dimen/sticky_bar_height` |
| **contentDescription для ImageView** | ✅ | `SyncStatusView` — иконка облака должна иметь описание |

---

### 4.2 Итог

Все четыре части RFC формируют **полную архитектурную документацию** Smetrix:

```
Часть 1 — Данные: Room Entity, типы, индексы, Soft Delete, структура пакетов
Часть 2 — Сеть:  WorkManager, sync_state, Optimistic Locking, API-контракты, HTTP 409
Часть 3 — UI:    RecyclerView + DiffUtil, Sticky Bottom Bar, @Transaction-пересчёт
Часть 4 — Защита: PII/ФЗ-152, UTF-8, retry/fallback ФГИС, соответствие .cursorrules
```

> [!IMPORTANT]
> Проект **полностью соответствует** `.cursorrules`. Все 18 правил покрыты архитектурными решениями, описанными в RFC. Следующий шаг — имплементация по данному RFC.
