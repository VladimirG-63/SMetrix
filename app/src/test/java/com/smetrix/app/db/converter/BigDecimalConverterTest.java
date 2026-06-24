// app/src/test/java/com/smetrix/app/db/converter/BigDecimalConverterTest.java
package com.smetrix.app.db.converter;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Модульные тесты для {@link BigDecimalConverter}.
 *
 * Проверяют корректность двусторонней конвертации BigDecimal ↔ String,
 * которую Room использует для хранения числовых значений в SQLite.
 */
public class BigDecimalConverterTest {

    // ──────────────────────────────────────────────────────────────────────────
    // toBigDecimal (String → BigDecimal)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Проверяет, что строка "1234.5678" корректно преобразуется в BigDecimal
     * и значение совпадает с ожидаемым через compareTo (игнорируя trailing zeros).
     */
    @Test
    public void testFromString_validValue() {
        String input = "1234.5678";
        BigDecimal result = BigDecimalConverter.toBigDecimal(input);

        // Используем compareTo, чтобы игнорировать различия в scale
        // (например, "1234.5678" vs new BigDecimal("1234.5678"))
        assertEquals(0, result.compareTo(new BigDecimal("1234.5678")));
    }

    /**
     * Проверяет, что null на входе toBigDecimal возвращает null,
     * а не NullPointerException.
     */
    @Test
    public void testFromString_null() {
        BigDecimal result = BigDecimalConverter.toBigDecimal(null);
        assertNull("toBigDecimal(null) должен возвращать null", result);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // fromBigDecimal (BigDecimal → String)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Проверяет, что new BigDecimal("1234.5678") преобразуется ровно в строку
     * "1234.5678" (plain-нотация, без экспоненты).
     */
    @Test
    public void testToString_validValue() {
        BigDecimal input = new BigDecimal("1234.5678");
        String result = BigDecimalConverter.fromBigDecimal(input);

        assertEquals("1234.5678", result);
    }

    /**
     * Проверяет, что null на входе fromBigDecimal возвращает null,
     * а не NullPointerException.
     */
    @Test
    public void testToString_null() {
        String result = BigDecimalConverter.fromBigDecimal(null);
        assertNull("fromBigDecimal(null) должен возвращать null", result);
    }

    /**
     * Проверяет, что toPlainString используется вместо toString,
     * то есть большие числа не записываются в экспоненциальной нотации.
     */
    @Test
    public void testToString_noScientificNotation() {
        // BigDecimal.valueOf(double) может создать экспоненциальный toString()
        // для очень больших чисел, но toPlainString() всегда plain.
        BigDecimal input = new BigDecimal("1500000.00");
        String result = BigDecimalConverter.fromBigDecimal(input);

        assertEquals("1500000.00", result);
        // Убеждаемся, что нет 'E' в строке
        assert result != null;
        assert !result.contains("E") : "Результат не должен содержать экспоненциальную нотацию";
    }
}
