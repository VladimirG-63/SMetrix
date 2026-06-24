// app/src/main/java/com/smetrix/app/viewmodel/WorkerViewModel.java
package com.smetrix.app.viewmodel;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.smetrix.app.db.AppDatabase;
import com.smetrix.app.db.entity.WorkerEntity;
import com.smetrix.app.network.sync.SyncManager;
import com.smetrix.app.repository.WorkerRepository;
import com.smetrix.app.utils.SecurePrefsHelper;

import androidx.work.WorkManager;

import java.util.List;

/**
 * ViewModel для экрана управления рабочими (Workers).
 *
 * <p><b>Архитектурная роль (MVVM):</b><br>
 * {@code WorkerViewModel} — посредник между UI (Fragment/Activity) и
 * {@link WorkerRepository}. ViewModel:
 * <ul>
 *   <li>Предоставляет реактивный список рабочих через {@link LiveData}.</li>
 *   <li>Принимает команду сохранения рабочего из UI.</li>
 *   <li>Перехватывает исключения бизнес-логики из репозитория и транслирует
 *       их в сообщения для пользователя через {@code errorMessage}.</li>
 * </ul>
 *
 * <p><b>Обработка ошибок:</b><br>
 * Метод {@link #saveWorker(String, String, String)} перехватывает два типа
 * исключений из репозитория:
 * <ul>
 *   <li>{@link WorkerRepository.ValidationException} — нарушение правил валидации
 *       (имя слишком длинное, телефон не соответствует формату и т.п.).</li>
 *   <li>{@link WorkerRepository.DuplicateEntityException} — конфликт уникального
 *       ключа в SQLite при дублировании записи.</li>
 * </ul>
 * Текст каждого исключения публикуется в {@code errorMessage} для отображения
 * пользователю (Snackbar, Toast, ошибка поля ввода).
 *
 * <p><b>Получение userId:</b><br>
 * Аналогично {@link ProjectListViewModel} — читается из {@link SharedPreferences}
 * по ключу {@code "user_id"}.
 *
 * <p><b>Пример использования в Fragment:</b>
 * <pre>
 *   WorkerViewModel vm = new ViewModelProvider(this).get(WorkerViewModel.class);
 *
 *   vm.getWorkers().observe(getViewLifecycleOwner(), new Observer&lt;List&lt;WorkerEntity&gt;&gt;() {
 *       {@literal @}Override
 *       public void onChanged(List&lt;WorkerEntity&gt; workers) {
 *           adapter.submitList(workers);
 *       }
 *   });
 *
 *   vm.getErrorMessage().observe(getViewLifecycleOwner(), new Observer&lt;String&gt;() {
 *       {@literal @}Override
 *       public void onChanged(String error) {
 *           if (error != null) {
 *               Snackbar.make(root, error, Snackbar.LENGTH_LONG).show();
 *               vm.clearError();
 *           }
 *       }
 *   });
 * </pre>
 *
 * @see WorkerRepository
 * @see WorkerRepository.ValidationException
 * @see WorkerRepository.DuplicateEntityException
 */
public class WorkerViewModel extends AndroidViewModel {

    // ─────────────────────────────────────────────────────────────────────────
    // Константы
    // ─────────────────────────────────────────────────────────────────────────

    /** Тег для LogCat. */
    private static final String TAG = "WorkerViewModel";

    /** Ключ идентификатора пользователя в SharedPreferences. */
    private static final String PREFS_KEY_USER_ID = "user_id";

    /** Имя файла SharedPreferences с данными авторизации. */
    private static final String PREFS_NAME = "smetrix_auth_prefs";

    // ─────────────────────────────────────────────────────────────────────────
    // Зависимости
    // ─────────────────────────────────────────────────────────────────────────

    /** Репозиторий для работы с таблицей «worker». */
    private final WorkerRepository workerRepository;

    /** Идентификатор текущего авторизованного пользователя. */
    private final String userId;

    // ─────────────────────────────────────────────────────────────────────────
    // LiveData — данные для UI
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Реактивный список рабочих текущего пользователя.
     *
     * <p>Получается из {@code WorkerRepository.getWorkers(userId)}.
     * Обновляется автоматически при любом изменении таблицы «worker»
     * (вставка, обновление, удаление).
     *
     * <p>RecyclerView.Adapter в Fragment подписывается на этот LiveData
     * через {@code observe(getViewLifecycleOwner(), observer)}.
     */
    private final LiveData<List<WorkerEntity>> workers;

    /**
     * Канал передачи сообщений об ошибках валидации и дублирования в UI.
     *
     * <p>Публикуется при перехвате исключений из {@link #saveWorker}.
     * Fragment должен вызвать {@link #clearError()} после отображения ошибки,
     * чтобы избежать повторного показа при пересоздании View (поворот экрана).
     */
    private final MutableLiveData<String> errorMessage;

    // ─────────────────────────────────────────────────────────────────────────
    // Конструктор
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Конструктор ViewModel, автоматически вызываемый фреймворком.
     *
     * <p>Инициализирует {@link WorkerRepository} через
     * {@link AppDatabase#getInstance(Context)}, читает {@code userId} из
     * {@link SharedPreferences} и подписывается на список рабочих.
     *
     * @param application экземпляр {@link Application}. Никогда не {@code null}.
     */
    public WorkerViewModel(@NonNull Application application) {
        super(application);

        // ── Шаг 1: Читаем userId из EncryptedSharedPreferences ────────────────
        SharedPreferences prefs = SecurePrefsHelper.get(application);
        String storedUserId = prefs.getString(PREFS_KEY_USER_ID, null);

        if (storedUserId == null || storedUserId.isEmpty()) {
            throw new IllegalStateException("WorkerViewModel создан без активной сессии");
        }
        this.userId = storedUserId;

        // ── Шаг 2: Строим зависимости ─────────────────────────────────────────
        AppDatabase database = AppDatabase.getInstance(application);
        WorkManager workManager = WorkManager.getInstance(application);
        SyncManager syncManager = new SyncManager(workManager);

        // ── Шаг 3: Создаём репозиторий ────────────────────────────────────────
        this.workerRepository = new WorkerRepository(
                database.workerDao(),
                syncManager
        );

        // ── Шаг 4: Инициализируем LiveData ────────────────────────────────────
        this.workers = workerRepository.getWorkers(userId);
        this.errorMessage = new MutableLiveData<String>();

        Log.d(TAG, "WorkerViewModel инициализирован.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Публичные методы — геттеры LiveData
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Возвращает реактивный список рабочих текущего пользователя.
     *
     * <p>Fragment подписывается через
     * {@code vm.getWorkers().observe(viewLifecycleOwner, observer)}.
     *
     * @return {@code LiveData} со списком {@link WorkerEntity}. Никогда не null.
     */
    public LiveData<List<WorkerEntity>> getWorkers() {
        return workers;
    }

    /**
     * Возвращает канал сообщений об ошибках для отображения в UI.
     *
     * <p>Значение {@code null} означает отсутствие ошибки (штатное состояние).
     *
     * @return {@link LiveData} с текстом ошибки или {@code null}.
     */
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Публичные методы — команды от UI
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Сохраняет нового рабочего в локальную базу данных.
     *
     * <p>Делегирует операцию в {@link WorkerRepository#saveWorker(String, String, String, String)}.
     * Репозиторий выполняет валидацию синхронно на вызывающем потоке, а запись
     * в БД — асинхронно в {@code AppExecutors.diskIO()}.
     *
     * <p><b>Перехватываемые исключения:</b>
     * <ul>
     *   <li>{@link WorkerRepository.ValidationException} — нарушение правил валидации.
     *       Примеры: имя пустое или длиннее 100 символов; нормализованный телефон
     *       длиннее 20 символов. Текст исключения публикуется в {@code errorMessage}.</li>
     *   <li>{@link WorkerRepository.DuplicateEntityException} — конфликт уникального
     *       ключа в SQLite (повторный UUID, что крайне маловероятно, но обрабатывается).
     *       Текст исключения публикуется в {@code errorMessage}.</li>
     * </ul>
     *
     * <p><b>Важно:</b> валидация в репозитории происходит синхронно на главном потоке.
     * Исключения прилетают немедленно — ещё до запуска фонового потока записи.
     *
     * @param fullName  полное имя рабочего (до trim). Длина 1–100 символов после trim.
     * @param phone     телефон в любом формате. Нормализуется (удаляются пробелы,
     *                  скобки, дефисы). Максимум 20 символов после нормализации.
     * @param specialty специализация рабочего (плиточник, маляр и т.п.).
     *                  Произвольная строка, может быть пустой.
     */
    public void saveWorker(
            final String fullName,
            final String phone,
            final String specialty
    ) {
        Log.d(TAG, "saveWorker: сохранение рабочего.");

        try {
            // Делегируем сохранение в репозиторий.
            // Валидация (trim, проверка длин) выполняется синхронно в репозитории.
            // Запись в БД — асинхронно через AppExecutors.diskIO().
            workerRepository.saveWorker(fullName, phone, specialty, userId);

        } catch (WorkerRepository.ValidationException validationException) {
            // Нарушение правил валидации входных данных.
            // Публикуем понятное сообщение для пользователя.
            Log.w(TAG, "saveWorker: ValidationException — " + validationException.getMessage());
            errorMessage.setValue(validationException.getMessage());

        } catch (WorkerRepository.DuplicateEntityException duplicateException) {
            // Конфликт уникального ключа в SQLite.
            // На практике почти невозможно (UUID v7 уникален), но обрабатываем.
            Log.e(TAG, "saveWorker: DuplicateEntityException — "
                    + duplicateException.getMessage(), duplicateException);
            errorMessage.setValue(
                    "Не удалось сохранить рабочего: запись уже существует в базе данных. "
                    + "Попробуйте ещё раз."
            );
        }
    }

    /**
     * Сбрасывает текущее сообщение об ошибке.
     *
     * <p>Вызывается из Fragment после того, как пользователь увидел ошибку
     * (Snackbar скрылся, Toast исчез и т.п.). Предотвращает повторное
     * отображение ошибки при пересоздании Fragment.
     */
    public void clearError() {
        errorMessage.setValue(null);
    }

    /**
     * Удаляет рабочего из базы данных.
     *
     * @param workerId идентификатор рабочего.
     */
    public void deleteWorker(final String workerId) {
        Log.d(TAG, "deleteWorker: workerId=" + workerId);
        workerRepository.deleteWorker(workerId);
    }

    /**
     * Обновляет данные существующего рабочего.
     *
     * @param workerId  идентификатор рабочего.
     * @param fullName  новое полное имя.
     * @param phone     новый телефон.
     * @param specialty новая специализация.
     */
    public void updateWorker(
            final String workerId,
            final String fullName,
            final String phone,
            final String specialty) {
        Log.d(TAG, "updateWorker: обновление рабочего.");
        try {
            workerRepository.updateWorker(workerId, fullName, phone, specialty);
        } catch (WorkerRepository.ValidationException validationException) {
            Log.w(TAG, "updateWorker: ValidationException — " + validationException.getMessage());
            errorMessage.setValue(validationException.getMessage());
        }
    }
}
