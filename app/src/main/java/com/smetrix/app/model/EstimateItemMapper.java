// app/src/main/java/com/smetrix/app/model/EstimateItemMapper.java
package com.smetrix.app.model;

import com.smetrix.app.db.entity.EstimateItemEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Статический маппер для преобразования Entity-объектов базы данных
 * ({@link EstimateItemEntity}) в Display-модели ({@link EstimateItemDisplay}).
 *
 * <h3>Архитектурное правило:</h3>
 * <p>Этот маппер вызывается <b>исключительно</b> из ViewModel-слоя
 * (например, из {@code RoomDetailViewModel}). UI-слой (Fragment, Activity,
 * RecyclerView.Adapter) <b>никогда</b> не должен видеть {@code EstimateItemEntity}
 * напрямую. Это обеспечивает изоляцию слоёв и позволяет изменять схему
 * базы данных без правки UI-кода.
 *
 * <h3>Правила округления при маппинге:</h3>
 * <ul>
 *   <li>{@code quantity}   — scale = 6, {@code RoundingMode.HALF_UP}.</li>
 *   <li>{@code finalPrice} — scale = 2, {@code RoundingMode.HALF_UP}.</li>
 *   <li>{@code totalPrice} — scale = 2, {@code RoundingMode.HALF_UP}.</li>
 * </ul>
 *
 * <h3>Обработка null:</h3>
 * <p>Если любое из денежных полей в Entity равно {@code null} (что теоретически
 * недопустимо, но может произойти при некорректной миграции базы),
 * маппер подставляет {@code BigDecimal.ZERO} с соответствующим scale,
 * чтобы UI не упал с {@code NullPointerException}.
 */
public class EstimateItemMapper {

    /**
     * Scale для денежных полей (finalPrice, totalPrice).
     */
    private static final int SCALE_MONEY = 2;

    /**
     * Scale для поля количества (quantity).
     */
    private static final int SCALE_QUANTITY = 6;

    /**
     * Режим округления для всех финансовых операций в маппере.
     */
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /**
     * Приватный конструктор — класс является утилитным и не должен иметь экземпляров.
     *
     * <p>Все методы класса статические. Создание экземпляра через {@code new}
     * принципиально невозможно благодаря этому приватному конструктору.
     */
    private EstimateItemMapper() {
        // Утилитный класс. Создание экземпляра запрещено.
    }

    /**
     * Преобразует одну {@link EstimateItemEntity} из базы данных
     * в {@link EstimateItemDisplay} для отображения в UI.
     *
     * <p>Метод выполняет следующие преобразования:
     * <ol>
     *   <li>Копирует строковые поля ({@code id}, {@code name}, {@code unitMeasure},
     *       {@code status}, {@code syncState}) без изменений.</li>
     *   <li>Нормализует {@code quantity} до scale = 6 с округлением HALF_UP.
     *       Если поле {@code null} — подставляет {@code BigDecimal.ZERO.setScale(6)}.</li>
     *   <li>Нормализует {@code finalPrice} и {@code totalPrice} до scale = 2
     *       с округлением HALF_UP. Если поле {@code null} — подставляет ZERO.setScale(2).</li>
     * </ol>
     *
     * @param entity объект из базы данных, который нужно преобразовать.
     *               Не должен быть {@code null} — если Entity {@code null},
     *               метод вернёт {@code null}.
     * @return объект {@link EstimateItemDisplay} для UI-слоя,
     *         или {@code null}, если входной {@code entity} равен {@code null}.
     */
    public static EstimateItemDisplay fromEntity(EstimateItemEntity entity) {
        // Явная проверка на null — лямбды и Stream API не используются.
        if (entity == null) {
            return null;
        }

        // ── Нормализация поля quantity (scale = 6) ─────────────────────────
        BigDecimal quantity;
        if (entity.quantity == null) {
            quantity = BigDecimal.ZERO.setScale(SCALE_QUANTITY, ROUNDING);
        } else {
            quantity = entity.quantity.setScale(SCALE_QUANTITY, ROUNDING);
        }

        // ── Нормализация поля finalPrice (scale = 2) ───────────────────────
        BigDecimal finalPrice;
        if (entity.finalPrice == null) {
            finalPrice = BigDecimal.ZERO.setScale(SCALE_MONEY, ROUNDING);
        } else {
            finalPrice = entity.finalPrice.setScale(SCALE_MONEY, ROUNDING);
        }

        // ── Нормализация поля totalPrice (scale = 2) ───────────────────────
        BigDecimal totalPrice;
        if (entity.totalPrice == null) {
            totalPrice = BigDecimal.ZERO.setScale(SCALE_MONEY, ROUNDING);
        } else {
            totalPrice = entity.totalPrice.setScale(SCALE_MONEY, ROUNDING);
        }

        // ── Сборка Display-объекта ─────────────────────────────────────────
        return new EstimateItemDisplay(
                entity.id,
                entity.name,
                entity.unitMeasure,
                entity.status,
                entity.syncState,
                quantity,
                finalPrice,
                totalPrice
        );
    }

    /**
     * Преобразует список {@link EstimateItemEntity} из базы данных
     * в список {@link EstimateItemDisplay} для отображения в UI.
     *
     * <p>Метод использует классический цикл {@code for} (без Stream API и лямбд)
     * для явности логики и наглядности при обучении.
     *
     * <p>Если входной список равен {@code null}, метод возвращает пустой
     * список (а не {@code null}), чтобы RecyclerView.Adapter мог безопасно
     * получить его через {@code submitList()}.
     *
     * @param list список Entity-объектов из базы данных.
     *             Допускается {@code null} — в таком случае будет возвращён
     *             пустой список.
     * @return список {@link EstimateItemDisplay} для UI-слоя.
     *         Никогда не возвращает {@code null}.
     */
    public static List<EstimateItemDisplay> fromList(List<EstimateItemEntity> list) {
        // Создаём результирующий список заранее.
        List<EstimateItemDisplay> result = new ArrayList<EstimateItemDisplay>();

        // Явная проверка на null входного списка.
        if (list == null) {
            return result;
        }

        // Классический цикл for — без лямбд и Stream API.
        for (int i = 0; i < list.size(); i++) {
            EstimateItemEntity entity = list.get(i);

            // Пропускаем null-элементы внутри списка (защитное программирование).
            if (entity == null) {
                continue;
            }

            // Преобразуем каждый Entity и добавляем в результат.
            EstimateItemDisplay display = fromEntity(entity);
            if (display != null) {
                result.add(display);
            }
        }

        return result;
    }
}
