// app/src/main/java/com/smetrix/app/utils/UnitConversionEngine.java
package com.smetrix.app.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.smetrix.app.db.entity.UnitConversionEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Движок конвертации единиц измерения между системой ФГИС ЦС и приложением Smetrix.
 *
 * <p><b>Архитектурная роль:</b><br>
 * Чистый stateless-утилитный класс. Не держит состояния, не работает с БД напрямую.
 * Принимает справочник правил {@link UnitConversionEntity} извне (dependency injection
 * через параметры метода) — это делает его легко тестируемым.
 *
 * <p><b>Направление конвертации (поле {@code conversionFactor}):</b>
 * <ul>
 *   <li>Прямое (fgisUnit → appUnit): {@code result = value × conversionFactor}.</li>
 *   <li>Обратное (appUnit → fgisUnit): {@code result = value ÷ conversionFactor}.</li>
 * </ul>
 *
 * <p><b>Пример использования:</b>
 * <pre>
 *   List&lt;UnitConversionEntity&gt; rules = dao.getAllRules();
 *   BigDecimal result = UnitConversionEngine.convert(
 *       new BigDecimal("100"), "дм2", "м²", rules);
 *   // result = 1.000000 (т.к. 1 дм2 = 0.01 м2, 100 × 0.01 = 1)
 * </pre>
 *
 * @see UnitConversionEntity
 */
public final class UnitConversionEngine {

    // ─────────────────────────────────────────────────────────────────────────
    // Константы
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Количество знаков после запятой в результате конвертации.
     * Выбрано 6 как компромисс между точностью и читаемостью.
     */
    private static final int SCALE = 6;

    /**
     * Режим округления: HALF_UP соответствует стандарту бухгалтерского учёта.
     */
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    // ─────────────────────────────────────────────────────────────────────────
    // Запрет создания экземпляров
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Приватный конструктор: класс является утилитным, инстанциирование запрещено.
     */
    private UnitConversionEngine() {
        throw new UnsupportedOperationException(
                "UnitConversionEngine — утилитный класс, инстанциирование запрещено.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Публичный API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Конвертирует числовое значение из одной единицы измерения в другую.
     *
     * <p><b>Алгоритм:</b>
     * <ol>
     *   <li>Если {@code fromUnit == toUnit} (или оба null) — возвращаем {@code value} без изменений.</li>
     *   <li>Ищем правило, где {@code fgisUnit == fromUnit} и {@code appUnit == toUnit}
     *       (прямая конвертация: ФГИС → приложение).</li>
     *   <li>Если прямого правила нет — ищем обратное: {@code fgisUnit == toUnit} и {@code appUnit == fromUnit}
     *       (обратная конвертация: приложение → ФГИС).</li>
     *   <li>Если правило не найдено — выбрасываем {@link IllegalArgumentException}
     *       с кодом {@code UNITS_MISMATCH}.</li>
     * </ol>
     *
     * <p><b>Точность вычислений:</b> результат всегда устанавливается с
     * {@code scale = 6, HALF_UP}. Это предотвращает накопление ошибок
     * округления при цепочечных конвертациях.
     *
     * @param value    конвертируемое значение. Не должно быть {@code null}.
     * @param fromUnit исходная единица измерения (например, {@code "дм2"}, {@code "кв.м"}).
     *                 Может быть {@code null} — тогда конвертация не производится.
     * @param toUnit   целевая единица измерения (например, {@code "м²"}).
     *                 Может быть {@code null} — тогда конвертация не производится.
     * @param rules    список правил из таблицы {@code unit_conversion}. Не должен быть {@code null}.
     * @return конвертированное значение с scale=6, RoundingMode.HALF_UP.
     *         Если единицы совпадают — возвращает исходное {@code value}.
     * @throws IllegalArgumentException если правило конвертации не найдено (UNITS_MISMATCH).
     * @throws NullPointerException     если {@code value} или {@code rules} равен {@code null}.
     */
    @NonNull
    public static BigDecimal convert(
            @NonNull BigDecimal value,
            @Nullable String fromUnit,
            @Nullable String toUnit,
            @NonNull List<UnitConversionEntity> rules) {

        // ── Шаг 1: Тривиальный случай — единицы совпадают ───────────────────
        if (fromUnit == null && toUnit == null) {
            return value;
        }
        if (sameUnit(fromUnit, toUnit)) {
            return value;
        }

        // ── Шаг 2: Прямая конвертация — fgisUnit→appUnit ────────────────────
        // Ищем правило, где fromUnit — это единица ФГИС, а toUnit — единица приложения.
        Optional<UnitConversionEntity> directRule = rules.stream()
                .filter(rule -> rule != null
                        && rule.conversionFactor != null
                        && rule.conversionFactor.compareTo(BigDecimal.ZERO) > 0
                        && sameUnit(fromUnit, rule.fgisUnit)
                        && sameUnit(toUnit, rule.appUnit))
                .findFirst();

        if (directRule.isPresent()) {
            // result = value × conversionFactor  (ФГИС → App)
            return value
                    .multiply(directRule.get().conversionFactor)
                    .setScale(SCALE, ROUNDING);
        }

        // ── Шаг 3: Обратная конвертация — appUnit→fgisUnit ──────────────────
        // Ищем правило, где toUnit — это единица ФГИС, а fromUnit — единица приложения.
        Optional<UnitConversionEntity> inverseRule = rules.stream()
                .filter(rule -> rule != null
                        && rule.conversionFactor != null
                        && rule.conversionFactor.compareTo(BigDecimal.ZERO) != 0
                        && sameUnit(toUnit, rule.fgisUnit)
                        && sameUnit(fromUnit, rule.appUnit))
                .findFirst();

        if (inverseRule.isPresent()) {
            // result = value ÷ conversionFactor  (App → ФГИС)
            return value
                    .divide(inverseRule.get().conversionFactor, SCALE, ROUNDING);
        }

        // ── Шаг 4: Правило не найдено — UNITS_MISMATCH ──────────────────────
        throw new IllegalArgumentException(
                "UNITS_MISMATCH: не найдено правило конвертации «"
                + fromUnit + "» → «" + toUnit + "». "
                + "Проверьте таблицу unit_conversion или добавьте недостающее правило.");
    }

    private static boolean sameUnit(@Nullable String first, @Nullable String second) {
        return first != null && second != null
                && first.trim().equalsIgnoreCase(second.trim());
    }
}
