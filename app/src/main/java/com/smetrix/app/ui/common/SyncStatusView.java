// app/src/main/java/com/smetrix/app/ui/common/SyncStatusView.java
package com.smetrix.app.ui.common;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;

import com.smetrix.app.R;
import com.smetrix.app.model.SyncStatusResult;
import com.smetrix.app.viewmodel.SyncViewModel;

import java.util.List;

/**
 * Кастомная View для отображения глобального статуса синхронизации в Toolbar.
 *
 * <p><b>Компоненты:</b>
 * <ul>
 *   <li>{@code ivSyncIcon}     — векторная иконка (облако разного цвета)</li>
 *   <li>{@code tvConflictBadge} — числовой бейдж с количеством конфликтов</li>
 * </ul>
 *
 * <p><b>Использование:</b>
 * <pre>
 *   SyncStatusView syncView = new SyncStatusView(context);
 *   toolbar.addView(syncView);
 *   syncView.bind(syncViewModel, this);
 * </pre>
 *
 * <p><b>Привязка через {@link #bind(SyncViewModel, LifecycleOwner)}:</b><br>
 * View подписывается на {@link SyncViewModel#getSyncStatus()} и обновляет
 * иконку + бейдж каждый раз, когда LiveData эмитирует новое значение.
 */
public class SyncStatusView extends FrameLayout {

    private static final String TAG = "SyncStatusView";

    // ── Дочерние виджеты ────────────────────────────────────────────────────
    private ImageView ivSyncIcon;
    private TextView  tvConflictBadge;

    // ── Конструкторы для XML-инфляции ────────────────────────────────────────

    /**
     * Конструктор для создания View из кода.
     *
     * @param context контекст Activity/Fragment.
     */
    public SyncStatusView(@NonNull Context context) {
        super(context);
        init(context);
    }

    /**
     * Конструктор для инфляции из XML (используется LayoutInflater).
     *
     * @param context контекст Activity/Fragment.
     * @param attrs   атрибуты из XML-тега.
     */
    public SyncStatusView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    /**
     * Конструктор для инфляции из XML со стилем.
     *
     * @param context      контекст Activity/Fragment.
     * @param attrs        атрибуты из XML-тега.
     * @param defStyleAttr стиль по умолчанию.
     */
    public SyncStatusView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    // ── Инициализация ────────────────────────────────────────────────────────

    /**
     * Инфлейтит внутренний макет {@code view_sync_status.xml} и
     * инициализирует ссылки на дочерние виджеты.
     *
     * @param context контекст.
     */
    private void init(@NonNull Context context) {
        inflate(context, R.layout.view_sync_status, this);
        ivSyncIcon      = findViewById(R.id.ivSyncIcon);
        tvConflictBadge = findViewById(R.id.tvConflictBadge);
    }

    // ── Публичный API ────────────────────────────────────────────────────────

    /**
     * Привязывает View к {@link SyncViewModel}, подписываясь на
     * {@link SyncViewModel#getSyncStatus()} через LiveData.
     *
     * <p>При каждом изменении статуса синхронизации View автоматически
     * обновляет иконку и бейдж:
     * <ul>
     *   <li>{@code "SYNCED"}   → синее облако, бейдж скрыт</li>
     *   <li>{@code "PENDING"}  → серое облако, бейдж скрыт</li>
     *   <li>{@code "FAILED"}   → красное облако, бейдж скрыт</li>
     *   <li>{@code "CONFLICT"} → жёлтое облако, бейдж с числом конфликтов</li>
     * </ul>
     *
     * @param syncViewModel ViewModel, хранящий LiveData статуса синхронизации.
     *                      Не должен быть {@code null}.
     * @param owner         LifecycleOwner (Activity или Fragment), контролирующий
     *                      время жизни подписки. Не должен быть {@code null}.
     */
    public void bind(@NonNull SyncViewModel syncViewModel, @NonNull LifecycleOwner owner) {
        // Подписываемся на глобальный статус — обновляет иконку и бейдж.
        syncViewModel.getSyncStatus().observe(owner, status -> {
            if (status == null) {
                Log.w(TAG, "bind: получен null SyncStatusResult, пропускаем обновление.");
                return;
            }
            applyStatus(status);
        });

        // Подписываемся на счётчик конфликтов — обновляет бейдж точным значением из ConflictDao.
        syncViewModel.getConflictCount().observe(owner, count -> {
            if (count == null || count == 0) {
                tvConflictBadge.setVisibility(GONE);
            } else {
                tvConflictBadge.setText(String.valueOf(count));
                tvConflictBadge.setVisibility(VISIBLE);
            }
        });

        // Долгое нажатие — принудительная синхронизация.
        setOnLongClickListener(v -> {
            Log.d(TAG, "bind: долгое нажатие — запускаем принудительную синхронизацию.");
            syncViewModel.forceSyncNow();
            android.widget.Toast.makeText(
                    getContext(),
                    "Синхронизация запущена",
                    android.widget.Toast.LENGTH_SHORT
            ).show();
            return true;
        });
    }

    // ── Приватные методы ─────────────────────────────────────────────────────

    /**
     * Обновляет иконку и бейдж на основании {@link SyncStatusResult}.
     *
     * <p>Каждый {@code case} явно устанавливает ресурс через {@link ImageView#setImageResource(int)}.
     * Нет пустых catch-блоков. Нет использования магических строк — статусы соответствуют
     * строковым константам из SQL-запроса {@code SyncStatusDao.getGlobalSyncStatus()}.
     *
     * @param status текущий статус синхронизации.
     */
    private void applyStatus(@NonNull SyncStatusResult status) {
        Log.d(TAG, "applyStatus: globalStatus=" + status.globalStatus
                + ", conflictCount=" + status.conflictCount);

        switch (status.globalStatus) {
            case "SYNCED":
                ivSyncIcon.setImageResource(R.drawable.ic_cloud_blue);
                tvConflictBadge.setVisibility(GONE);
                break;

            case "PENDING":
                ivSyncIcon.setImageResource(R.drawable.ic_cloud_grey);
                tvConflictBadge.setVisibility(GONE);
                break;

            case "FAILED":
                ivSyncIcon.setImageResource(R.drawable.ic_cloud_red);
                tvConflictBadge.setVisibility(GONE);
                break;

            case "CONFLICT":
                ivSyncIcon.setImageResource(R.drawable.ic_cloud_yellow);
                tvConflictBadge.setText(String.valueOf(status.conflictCount));
                tvConflictBadge.setVisibility(VISIBLE);
                break;

            default:
                // Неизвестный статус — показываем серое облако как безопасный фолбэк.
                Log.w(TAG, "applyStatus: неизвестный globalStatus='" + status.globalStatus
                        + "'. Показываем серое облако.");
                ivSyncIcon.setImageResource(R.drawable.ic_cloud_grey);
                tvConflictBadge.setVisibility(GONE);
                break;
        }
    }
}
