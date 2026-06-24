// app/src/main/java/com/smetrix/app/db/converter/BigDecimalConverter.java
package com.smetrix.app.db.converter;

import androidx.room.TypeConverter;

import java.math.BigDecimal;

/**
 * TypeConverter для Room: позволяет хранить значения BigDecimal в SQLite.
 *
 * SQLite не имеет встроенного числового типа с произвольной точностью, поэтому
 * мы сохраняем BigDecimal как TEXT в формате, который возвращает toPlainString().
 *
 * Почему toPlainString(), а не toString():
 *  - toString() может вернуть экспоненциальную нотацию, например "1.5E+3".
 *  - toPlainString() всегда возвращает обычную запись: "1500.00".
 *  - Это критично для финансовых расчётов: "1.5E+3" при чтении через
 *    new BigDecimal("1.5E+3") будет корректен, но хранить в БД читаемую
 *    форму значительно проще для отладки и миграций.
 *
 * Null-safety: оба метода явно обрабатывают null-значения, потому что Room
 * может передавать null для nullable-полей (например, manualAreaOverride).
 *
 * Регистрация: этот класс указывается в @TypeConverters({BigDecimalConverter.class})
 * на уровне AppDatabase, и Room автоматически применяет его ко всем полям типа
 * BigDecimal во всех Entity-классах, связанных с этой базой данных.
 */
public class BigDecimalConverter {

    /**
     * Преобразует BigDecimal → String для сохранения в SQLite.
     *
     * @param value значение BigDecimal, которое нужно сохранить; может быть null.
     * @return строковое представление числа в формате plain (без экспоненты),
     *         или null, если входное значение равно null.
     */
    @TypeConverter
    public static String fromBigDecimal(BigDecimal value) {
        if (value == null) {
            return null;
        }
        // toPlainString() гарантирует формат "12345.67", а не "1.234567E+4"
        return value.toPlainString();
    }

    /**
     * Преобразует String → BigDecimal при чтении из SQLite.
     *
     * @param value строка, прочитанная из столбца базы данных; может быть null.
     * @return объект BigDecimal, восстановленный из строки,
     *         или null, если строка в базе данных была NULL.
     * @throws NumberFormatException если строка содержит некорректное число
     *         (это не должно происходить в нормальной работе, но защитит от
     *         ручного повреждения данных в базе).
     */
    @TypeConverter
    public static BigDecimal toBigDecimal(String value) {
        if (value == null) {
            return null;
        }
        // Конструктор BigDecimal(String) точнее, чем BigDecimal(double),
        // так как не вносит ошибки двоичного представления с плавающей точкой.
        return new BigDecimal(value);
    }
}
