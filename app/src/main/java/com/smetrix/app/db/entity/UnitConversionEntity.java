// app/src/main/java/com/smetrix/app/db/entity/UnitConversionEntity.java
package com.smetrix.app.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.math.BigDecimal;

/**
 * Room Entity для таблицы "unit_conversion".
 *
 * Хранит правила перевода единиц измерения из системы ФГИС ЦС
 * в единицы, используемые в приложении Smetrix.
 *
 * Проблема несовпадения единиц:
 *  Реестр ФГИС ЦС использует собственные обозначения единиц измерения
 *  (например, "м2", "кв.м", "m2" — всё это может означать одно и то же).
 *  В приложении принята единая нотация. Данная таблица хранит словарь
 *  соответствий: что означает единица ФГИС и как её перевести в нашу систему.
 *
 * Как используется conversionFactor:
 *  Если fgisUnit = "дм2" (квадратный дециметр) и appUnit = "м2" (кв. метр),
 *  то conversionFactor = 0.01 (1 дм2 = 0.01 м2).
 *  Формула: значение_в_appUnit = значение_в_fgisUnit × conversionFactor.
 *
 * Поле fgisUnit — уникально:
 *  Каждая единица из ФГИС ЦС имеет ровно одно правило преобразования.
 *  Это ограничение выражено через @Index(unique = true).
 *
 * Поле id:
 *  Суррогатный первичный ключ типа int. Используется, потому что Room
 *  требует @PrimaryKey, а String fgisUnit уже используется как уникальный
 *  ключ для поиска. Значение генерируется автоматически при вставке (autoGenerate = true).
 */
@Entity(
    tableName = "unit_conversion",
    indices = {
        @Index(value = {"fgis_unit"}, unique = true)
    }
)
public class UnitConversionEntity {

    /**
     * Суррогатный первичный ключ таблицы.
     * Генерируется автоматически базой данных при каждой вставке.
     * Тип int подходит — количество уникальных единиц измерения ФГИС
     * ограничено и не превысит нескольких сотен записей.
     */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    public int id;

    /**
     * Обозначение единицы измерения в реестре ФГИС ЦС.
     * Это исходное строковое обозначение, как оно приходит с сервера ФГИС.
     * Уникально — каждой единице ФГИС соответствует ровно одна запись.
     * Примеры: "м2", "кв.м", "дм2", "м3", "куб.м", "кг", "шт", "пог.м".
     */
    @NonNull
    @ColumnInfo(name = "fgis_unit")
    public String fgisUnit;

    /**
     * Обозначение единицы измерения в приложении Smetrix.
     * Нормализованное, единое обозначение для отображения пользователю.
     * Примеры: "м²", "м³", "кг", "шт", "м".
     * Несколько разных fgisUnit могут соответствовать одному appUnit
     * (например, "м2" и "кв.м" оба → "м²").
     */
    @ColumnInfo(name = "app_unit")
    public String appUnit;

    /**
     * Коэффициент перевода из единицы ФГИС в единицу приложения.
     * Хранится как TEXT через BigDecimalConverter для точного хранения
     * числа с произвольным количеством знаков после запятой.
     *
     * Формула:
     *  значение_в_appUnit = значение_в_fgisUnit × conversionFactor
     *
     * Примеры:
     *  fgisUnit="м2"    → appUnit="м²",  conversionFactor = 1.0
     *  fgisUnit="дм2"   → appUnit="м²",  conversionFactor = 0.01
     *  fgisUnit="кв.м"  → appUnit="м²",  conversionFactor = 1.0
     *  fgisUnit="см2"   → appUnit="м²",  conversionFactor = 0.0001
     */
    @ColumnInfo(name = "conversion_factor")
    public BigDecimal conversionFactor;

    /**
     * Пустой конструктор, обязательный для Room.
     * Room использует его при десериализации строк из базы данных в объекты Java.
     */
    public UnitConversionEntity() {
        // Пустой конструктор требуется Room для десериализации строк БД.
    }
}
