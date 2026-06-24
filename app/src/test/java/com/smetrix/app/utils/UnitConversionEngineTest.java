// app/src/test/java/com/smetrix/app/utils/UnitConversionEngineTest.java
package com.smetrix.app.utils;

import com.smetrix.app.db.entity.UnitConversionEntity;

import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Модульные тесты для {@link UnitConversionEngine}.
 *
 * Тесты работают на JVM без Android-зависимостей (local unit test).
 *
 * ── Моковое правило ────────────────────────────────────────────────────────
 * fgisUnit = "мешок", appUnit = "кг", conversionFactor = 25
 *
 * Логика движка:
 *  - Прямая  (fgisUnit→appUnit): fromUnit="мешок", toUnit="кг"
 *      result = value × 25  →  3 мешка × 25 = 75 кг
 *  - Обратная(appUnit→fgisUnit): fromUnit="кг",    toUnit="мешок"
 *      result = value ÷ 25  →  50 кг ÷ 25 = 2 мешка
 */
public class UnitConversionEngineTest {

    // ──────────────────────────────────────────────────────────────────────────
    // Константы правила
    // ──────────────────────────────────────────────────────────────────────────

    /** fgisUnit: "мешок" — единица, пришедшая из ФГИС ЦС */
    private static final String FGIS_UNIT = "мешок";

    /** appUnit: "кг" — нормализованная единица приложения */
    private static final String APP_UNIT  = "кг";

    /** 1 мешок = 25 кг */
    private static final String FACTOR    = "25";

    private List<UnitConversionEntity> rules;

    // ──────────────────────────────────────────────────────────────────────────
    // Подготовка: инициализируем список правил перед каждым тестом
    // ──────────────────────────────────────────────────────────────────────────

    @Before
    public void setUp() {
        UnitConversionEntity rule = new UnitConversionEntity();
        rule.id               = 1;
        rule.fgisUnit         = FGIS_UNIT;
        rule.appUnit          = APP_UNIT;
        rule.conversionFactor = new BigDecimal(FACTOR);

        rules = new ArrayList<>();
        rules.add(rule);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Тест 1 — Прямая конвертация: мешок → кг
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Прямая конвертация (fgisUnit → appUnit):
     * fromUnit="мешок"=fgisUnit, toUnit="кг"=appUnit.
     *
     * result = 3 × 25 = 75 кг (с scale=6 → 75.000000)
     */
    @Test
    public void testDirectConversion() {
        BigDecimal value  = new BigDecimal("3");
        BigDecimal result = UnitConversionEngine.convert(value, FGIS_UNIT, APP_UNIT, rules);

        assertEquals(
                "3 мешка должны конвертироваться в 75 кг",
                0,
                result.compareTo(new BigDecimal("75.000000"))
        );
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Тест 2 — Обратная конвертация: кг → мешок
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Обратная конвертация (appUnit → fgisUnit):
     * fromUnit="кг"=appUnit, toUnit="мешок"=fgisUnit.
     *
     * result = 50 ÷ 25 = 2 мешка (с scale=6 → 2.000000)
     */
    @Test
    public void testInverseConversion() {
        BigDecimal value  = new BigDecimal("50");
        BigDecimal result = UnitConversionEngine.convert(value, APP_UNIT, FGIS_UNIT, rules);

        assertEquals(
                "50 кг должны конвертироваться в 2 мешка",
                0,
                result.compareTo(new BigDecimal("2.000000"))
        );
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Тест 3 — Одинаковые единицы: значение не меняется
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Если fromUnit == toUnit, движок возвращает исходное значение без изменений.
     * Эта ветка не зависит от таблицы rules.
     */
    @Test
    public void testSameUnit() {
        BigDecimal value  = new BigDecimal("42");
        BigDecimal result = UnitConversionEngine.convert(value, APP_UNIT, APP_UNIT, rules);

        assertEquals(
                "Конвертация в те же единицы должна возвращать исходное значение",
                0,
                value.compareTo(result)
        );
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Тест 4 — Неизвестные единицы → IllegalArgumentException
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Если правило для пары (fromUnit, toUnit) не найдено ни прямым, ни
     * обратным поиском — движок должен выбросить {@link IllegalArgumentException}
     * с ключевым словом "UNITS_MISMATCH" в сообщении.
     */
    @Test
    public void testUnknownUnitThrowsException() {
        try {
            UnitConversionEngine.convert(
                    new BigDecimal("10"),
                    "фунт",   // отсутствует в rules
                    "тонна",  // отсутствует в rules
                    rules
            );
            fail("Ожидалось IllegalArgumentException для неизвестных единиц");
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            assert msg != null && msg.contains("UNITS_MISMATCH")
                    : "Сообщение должно содержать UNITS_MISMATCH, было: " + msg;
        }
    }
}
