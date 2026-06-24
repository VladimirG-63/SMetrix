// app/src/main/java/com/smetrix/app/repository/AppExecutors.java
package com.smetrix.app.repository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Синглтон-класс, предоставляющий общий пул потоков для выполнения
 * операций ввода/вывода с диском (операции с базой данных Room).
 *
 * <p><b>Почему нельзя работать с базой данных на главном потоке?</b><br>
 * Android запрещает выполнение операций ввода/вывода на главном (UI) потоке,
 * так как это блокирует отрисовку интерфейса и может вызвать ошибку
 * ANR (Application Not Responding). Room по умолчанию также выбрасывает
 * исключение при попытке выполнить запрос на главном потоке.
 *
 * <p><b>Почему один поток (newSingleThreadExecutor)?</b><br>
 * Один выделенный поток для операций с диском гарантирует:
 * <ol>
 *   <li>Последовательное выполнение запросов к БД без конкурентных конфликтов.</li>
 *   <li>Предсказуемый порядок операций (FIFO очередь задач).</li>
 *   <li>Минимальный расход ресурсов — нет накладных расходов на пул потоков.</li>
 * </ol>
 * Если в будущем потребуется параллельное выполнение нескольких
 * независимых запросов, можно заменить на {@link Executors#newFixedThreadPool(int)}.
 *
 * <p><b>Паттерн использования в репозитории:</b>
 * <pre>
 *   AppExecutors.diskIO().execute(new Runnable() {
 *      {@literal @}Override
 *       public void run() {
 *           // Здесь выполняются операции с Room DAO
 *           projectDao.insert(entity);
 *       }
 *   });
 * </pre>
 *
 * <p>Этот класс намеренно сделан non-instantiable (конструктор приватный),
 * так как содержит только статические методы и одно статическое поле.
 */
public final class AppExecutors {

    /**
     * Единственный экземпляр {@link ExecutorService} для операций с диском.
     *
     * <p>Инициализируется при загрузке класса (static initializer) —
     * это потокобезопасно по спецификации Java Language Specification
     * (§12.4.2: Class and Interface Initialization).
     *
     * <p>Используется одним потоком ({@code newSingleThreadExecutor}),
     * чтобы гарантировать последовательное выполнение запросов к БД.
     */
    private static final ExecutorService DISK_IO_EXECUTOR =
            Executors.newSingleThreadExecutor();

    /**
     * Приватный конструктор запрещает создание экземпляров этого класса.
     * Используйте статический метод {@link #diskIO()}.
     */
    private AppExecutors() {
        // Запрет создания экземпляров утилитарного класса
        throw new UnsupportedOperationException("AppExecutors — утилитарный класс, создавать экземпляры запрещено.");
    }

    /**
     * Возвращает общий {@link ExecutorService} для выполнения
     * операций ввода/вывода с диском в фоновом потоке.
     *
     * <p>Все репозитории используют этот метод для обёртывания
     * вызовов DAO в фоновые задачи:
     * <pre>
     *   AppExecutors.diskIO().execute(new Runnable() {
     *      {@literal @}Override
     *       public void run() {
     *           dao.insert(entity);
     *       }
     *   });
     * </pre>
     *
     * @return единственный экземпляр {@link ExecutorService},
     *         использующий один фоновый поток для всех операций с БД.
     */
    public static ExecutorService diskIO() {
        return DISK_IO_EXECUTOR;
    }
}
