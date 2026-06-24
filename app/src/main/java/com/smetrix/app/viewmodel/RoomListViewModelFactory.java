// app/src/main/java/com/smetrix/app/viewmodel/RoomListViewModelFactory.java
package com.smetrix.app.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

/**
 * Фабрика для создания {@link RoomListViewModel} с параметром {@code projectId}.
 *
 * <p>Стандартный {@link ViewModelProvider} не поддерживает передачу параметров
 * в конструктор ViewModel. Фабрика решает эту проблему.
 */
public class RoomListViewModelFactory implements ViewModelProvider.Factory {

    private final Application application;
    private final String projectId;

    public RoomListViewModelFactory(@NonNull Application application, @NonNull String projectId) {
        this.application = application;
        this.projectId = projectId;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(RoomListViewModel.class)) {
            return modelClass.cast(new RoomListViewModel(application, projectId));
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
