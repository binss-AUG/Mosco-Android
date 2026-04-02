package com.vn.jet.mosco.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A generic wrapper class that holds data along with its loading status.
 * Used to communicate state from ViewModel → UI layer via LiveData.
 *
 * Pattern: Single-source-of-truth state management.
 * Instead of separate LiveData for isLoading, result, error,
 * we use one LiveData<Resource<T>> that covers all states.
 *
 * @param <T> The type of data held by this resource.
 */
public class Resource<T> {

    public enum Status { SUCCESS, ERROR, LOADING }

    @NonNull
    private final Status status;
    @Nullable
    private final T data;
    @Nullable
    private final String message;

    private Resource(@NonNull Status status, @Nullable T data, @Nullable String message) {
        this.status = status;
        this.data = data;
        this.message = message;
    }

    /**
     * Creates a Resource in a successful state with data.
     */
    public static <T> Resource<T> success(@NonNull T data) {
        return new Resource<>(Status.SUCCESS, data, null);
    }

    /**
     * Creates a Resource in an error state with a message.
     */
    public static <T> Resource<T> error(@NonNull String message) {
        return new Resource<>(Status.ERROR, null, message);
    }

    /**
     * Creates a Resource in a loading state.
     */
    public static <T> Resource<T> loading() {
        return new Resource<>(Status.LOADING, null, null);
    }

    @NonNull
    public Status getStatus() { return status; }

    @Nullable
    public T getData() { return data; }

    @Nullable
    public String getMessage() { return message; }
}
