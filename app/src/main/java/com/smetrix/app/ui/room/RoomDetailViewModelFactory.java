// app/src/main/java/com/smetrix/app/ui/room/RoomDetailViewModelFactory.java
package com.smetrix.app.ui.room;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.smetrix.app.viewmodel.RoomDetailViewModel;

/**
 * Фабрика ViewModelProvider для создания {@link RoomDetailViewModel} с аргументом roomId.
 *
 * <p>Стандартный {@link ViewModelProvider} не умеет передавать аргументы в конструктор ViewModel.
 * Кастомная фабрика решает эту проблему: она создаёт ViewModel с нужными параметрами,
 * а ViewModelProvider управляет его жизненным циклом (сохраняет при поворотах экрана).
 *
 * <h3>Использование:</h3>
 * <pre>
 *   viewModel = new ViewModelProvider(
 *       this,
 *       new RoomDetailViewModelFactory(requireActivity().getApplication(), roomId)
 *   ).get(RoomDetailViewModel.class);
 * </pre>
 */
public class RoomDetailViewModelFactory implements ViewModelProvider.Factory {

    private final Application application;
    private final String roomId;

    /**
     * @param application экземпляр Application для передачи в AndroidViewModel.
     * @param roomId      идентификатор комнаты (UUID), к которой привязан экран.
     */
    public RoomDetailViewModelFactory(
            @NonNull Application application,
            @NonNull String roomId) {
        this.application = application;
        this.roomId = roomId;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(RoomDetailViewModel.class)) {
            return modelClass.cast(new RoomDetailViewModel(application, roomId));
        }
        throw new IllegalArgumentException(
                "RoomDetailViewModelFactory: неизвестный класс ViewModel: "
                + modelClass.getName());
    }
}
