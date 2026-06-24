// app/src/main/java/com/smetrix/app/repository/WorkerRepository.java
package com.smetrix.app.repository;

import android.database.sqlite.SQLiteConstraintException;

import androidx.lifecycle.LiveData;

import com.smetrix.app.db.dao.WorkerDao;
import com.smetrix.app.db.entity.WorkerEntity;
import com.smetrix.app.model.SyncState;
import com.smetrix.app.model.UuidGenerator;
import com.smetrix.app.network.sync.SyncManager;

import java.util.List;

/**
 * Репозиторий для управления данными рабочих (WorkerEntity).
 *
 * <p><b>Архитектурная роль (Clean Architecture):</b><br>
 * {@code WorkerRepository} находится между слоем данных (DAO) и слоем
 * бизнес-логики (ViewModel). Содержит логику валидации, нормализации
 * телефонного номера и сохранения записи о рабочем в локальную БД.
 *
 * <p><b>Правила валидации:</b>
 * <ul>
 *   <li>{@code fullName} — после trim() длина 1–100 символов,
 *       иначе {@link ValidationException}.</li>
 *   <li>{@code phone} — после trim() + replaceAll("[^\\d+]","") длина ≤ 20,
 *       иначе {@link ValidationException}.</li>
 *   <li>{@code specialty} — только trim(), без жёсткой валидации.</li>
 * </ul>
 *
 * <p><b>Offline-First:</b> новые рабочие сохраняются с {@code PENDING_CREATE}
 * и синхронизируются позже через {@link SyncManager}.
 */
public class WorkerRepository {

    // ─── Вложенные исключения ────────────────────────────────────────────────

    /**
     * Исключение при нарушении правил валидации входных данных.
     * Unchecked (extends RuntimeException) — ViewModel перехватывает его
     * через стандартные механизмы обработки ошибок.
     */
    public static class ValidationException extends RuntimeException {
        /**
         * @param message описание нарушенного правила (на русском, для логов и UI).
         */
        public ValidationException(String message) {
            super(message);
        }
    }

    /**
     * Исключение при нарушении уникального ограничения БД.
     * Оборачивает {@link SQLiteConstraintException}, пойманную при вставке.
     * Unchecked — ViewModel обрабатывает через стандартные механизмы.
     */
    public static class DuplicateEntityException extends RuntimeException {
        /**
         * @param message описание ошибки дублирования.
         * @param cause   исходное исключение SQLite (сохраняется для трассировки).
         */
        public DuplicateEntityException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // ─── Константы валидации ─────────────────────────────────────────────────

    /** Минимальная длина имени рабочего (после trim). */
    private static final int FULL_NAME_MIN_LENGTH = 1;

    /** Максимальная длина имени рабочего (после trim). */
    private static final int FULL_NAME_MAX_LENGTH = 100;

    /** Максимальная длина нормализованного телефона (только цифры и '+'). */
    private static final int PHONE_MAX_LENGTH = 20;

    // ─── Зависимости ─────────────────────────────────────────────────────────

    /** DAO для работы с таблицей «worker». */
    private final WorkerDao workerDao;

    /** Менеджер синхронизации. На Фазе 3 — заглушка. */
    private final SyncManager syncManager;

    // ─── Конструктор ─────────────────────────────────────────────────────────

    /**
     * Создаёт репозиторий. Зависимости — через конструктор (DI).
     *
     * @param workerDao   DAO таблицы «worker», получается из AppDatabase.
     * @param syncManager менеджер синхронизации.
     */
    public WorkerRepository(WorkerDao workerDao, SyncManager syncManager) {
        this.workerDao = workerDao;
        this.syncManager = syncManager;
    }

    // ─── Публичные методы ────────────────────────────────────────────────────

    /**
     * Возвращает реактивный список всех рабочих указанного пользователя.
     *
     * <p>Room выполняет SQL-запрос в фоновом потоке и доставляет результат
     * через {@code LiveData}. ViewModel подписывается и получает обновления
     * автоматически при любых изменениях в таблице «worker».
     *
     * @param userId идентификатор пользователя (поле {@code user_id}).
     * @return {@code LiveData} со списком рабочих. Никогда не null.
     */
    public LiveData<List<WorkerEntity>> getWorkers(String userId) {
        // Простое делегирование — Room сам управляет фоновым потоком.
        return workerDao.getAll(userId);
    }

    /**
     * Сохраняет нового рабочего в локальную БД с валидацией и нормализацией.
     *
     * <p><b>Алгоритм:</b>
     * <ol>
     *   <li>Trim + проверка длины fullName (1–100 символов).</li>
     *   <li>Trim + replaceAll("[^\\d+]","") для phone; проверка длины ≤ 20.</li>
     *   <li>Trim для specialty.</li>
     *   <li>Создание {@link WorkerEntity} с UUID, {@code PENDING_CREATE}, version=0.</li>
     *   <li>Вставка через {@link AppExecutors#diskIO()} в try/catch:
     *       {@link SQLiteConstraintException} → {@link DuplicateEntityException}.</li>
     *   <li>Вызов {@link SyncManager#scheduleSync()}.</li>
     * </ol>
     *
     * <p><b>Важно:</b> валидация происходит на вызывающем потоке — ViewModel
     * получает {@link ValidationException} мгновенно, до запуска Runnable.
     *
     * @param fullName  полное имя (до trim). Длина 1–100 символов после trim.
     * @param phone     телефон в любом формате. После нормализации ≤ 20 символов.
     * @param specialty специализация (произвольная строка, может быть пустой).
     * @param userId    идентификатор пользователя-владельца записи.
     * @throws ValidationException      при нарушении ограничений fullName или phone.
     * @throws DuplicateEntityException при конфликте первичного ключа в БД.
     */
    public void saveWorker(
            final String fullName,
            final String phone,
            final String specialty,
            final String userId
    ) {
        // ── Шаг 1: Нормализация и валидация fullName ──────────────────────────
        // Выполняется на вызывающем потоке, до отправки задачи в diskIO().
        final String trimmedFullName = fullName.trim();

        if (trimmedFullName.length() < FULL_NAME_MIN_LENGTH) {
            throw new ValidationException(
                    "Имя рабочего (fullName) не может быть пустым. "
                    + "Минимальная длина после обрезки пробелов: "
                    + FULL_NAME_MIN_LENGTH + " символ."
            );
        }

        if (trimmedFullName.length() > FULL_NAME_MAX_LENGTH) {
            throw new ValidationException(
                    "Имя рабочего (fullName) слишком длинное. "
                    + "Максимальная длина: " + FULL_NAME_MAX_LENGTH + " символов. "
                    + "Передано: " + trimmedFullName.length() + " символов."
            );
        }

        // ── Шаг 2: Нормализация и валидация phone ─────────────────────────────
        // [^\d+] — удаляет всё кроме цифр и символа '+'.
        // Пример: «+7 (916) 123-45-67» → «+79161234567» (12 символов).
        final String trimmedPhone = phone.trim();
        final String normalizedPhone = trimmedPhone.replaceAll("[^\\d+]", "");

        if (normalizedPhone.length() > PHONE_MAX_LENGTH) {
            throw new ValidationException(
                    "Телефон рабочего (phone) после нормализации слишком длинный. "
                    + "Максимум: " + PHONE_MAX_LENGTH + " символов. "
                    + "Нормализованный номер: «" + normalizedPhone + "» ("
                    + normalizedPhone.length() + " символов)."
            );
        }

        // ── Шаг 3: Нормализация specialty ─────────────────────────────────────
        final String trimmedSpecialty = specialty.trim();

        // ── Шаг 4: Фиксируем UUID и timestamp ДО запуска фонового потока ──────
        // Время создания должно соответствовать вызову saveWorker(),
        // а не моменту выполнения Runnable в очереди diskIO().
        final String newWorkerId = UuidGenerator.generate();
        final long currentTimeMillis = System.currentTimeMillis();

        // ── Шаг 5: Запись в БД через фоновый поток ────────────────────────────
        // Явный анонимный Runnable — без лямбд, согласно кодстайлу проекта.
        AppExecutors.diskIO().execute(new Runnable() {
            @Override
            public void run() {
                // Формируем сущность рабочего для сохранения в Room.
                WorkerEntity newWorker = new WorkerEntity();

                // Уникальный идентификатор (UUID v7, временно — UUID v4)
                newWorker.id = newWorkerId;

                // Идентификатор пользователя-владельца
                newWorker.userId = userId;

                // Нормализованные данные, прошедшие валидацию
                newWorker.fullName = trimmedFullName;
                newWorker.phone = normalizedPhone;
                newWorker.specialty = trimmedSpecialty;

                // Временные метки: при создании createdAt == updatedAt
                newWorker.createdAt = currentTimeMillis;
                newWorker.updatedAt = currentTimeMillis;

                // Версия 0 — запись ещё не синхронизировалась с сервером
                newWorker.version = 0L;

                // Состояние: ожидает первой отправки на сервер
                newWorker.syncState = SyncState.PENDING_CREATE.name();

                // ── Шаг 5а: Вставка с обработкой ошибок БД ───────────────────
                // WorkerDao.insert() использует OnConflictStrategy.ABORT:
                // при дублировании PK Room бросает SQLiteConstraintException.
                try {
                    workerDao.insert(newWorker);
                } catch (SQLiteConstraintException constraintException) {
                    // Оборачиваем низкоуровневое исключение SQLite в бизнес-исключение.
                    // ViewModel обработает DuplicateEntityException и покажет
                    // пользователю понятное сообщение об ошибке.
                    throw new DuplicateEntityException(
                            "Не удалось сохранить рабочего: запись с идентификатором «"
                            + newWorkerId + "» уже существует в базе данных.",
                            constraintException
                    );
                }

                // ── Шаг 5б: Планируем синхронизацию ──────────────────────────
                // Сигнализируем, что есть новые данные для отправки на сервер.
                // На Фазе 3 scheduleSync() — заглушка; реализация — в Фазе 5.
                syncManager.scheduleSync();
            }
        });
    }

    /**
     * Обновляет данные существующего рабочего (имя, телефон, специальность).
     *
     * <p>Валидация — те же правила, что и при создании.
     * Запись в БД — асинхронно через {@link AppExecutors#diskIO()}.
     *
     * @param workerId  идентификатор рабочего. Не должен быть null.
     * @param fullName  новое полное имя.
     * @param phone     новый телефон.
     * @param specialty новая специализация.
     */
    public void updateWorker(
            final String workerId,
            final String fullName,
            final String phone,
            final String specialty
    ) {
        final String trimmedFullName = fullName.trim();
        if (trimmedFullName.length() < FULL_NAME_MIN_LENGTH) {
            throw new ValidationException("Имя рабочего не может быть пустым.");
        }
        if (trimmedFullName.length() > FULL_NAME_MAX_LENGTH) {
            throw new ValidationException("Имя рабочего слишком длинное (максимум "
                    + FULL_NAME_MAX_LENGTH + " символов).");
        }
        final String normalizedPhone = phone.trim().replaceAll("[^\\d+]", "");
        if (normalizedPhone.length() > PHONE_MAX_LENGTH) {
            throw new ValidationException("Телефон слишком длинный после нормализации.");
        }
        final String trimmedSpecialty = specialty.trim();
        final long now = System.currentTimeMillis();

        AppExecutors.diskIO().execute(new Runnable() {
            @Override
            public void run() {
                // userId не меняем — мы не знаем его здесь; Room обновит только поля через @Update.
                // Чтобы @Update работал корректно, нужен полный объект.
                // Используем Query-обновление, чтобы не перезатирать userId.
                workerDao.updateFields(workerId, trimmedFullName, normalizedPhone,
                        trimmedSpecialty, now);
                syncManager.scheduleSync();
            }
        });
    }

    /**
     * Удаляет рабочего из базы данных.
     *
     * <p>Операция выполняется асинхронно в фоновом потоке.
     *
     * @param workerId идентификатор рабочего.
     */
    public void deleteWorker(final String workerId) {
        final long now = System.currentTimeMillis();
        AppExecutors.diskIO().execute(new Runnable() {
            @Override
            public void run() {
                WorkerEntity worker = workerDao.getById(workerId);
                if (worker != null && SyncState.PENDING_CREATE.name().equals(worker.syncState)) {
                    workerDao.deleteById(workerId);
                } else {
                    workerDao.markDeleted(workerId, now);
                }
                syncManager.scheduleSync();
            }
        });
    }
}
