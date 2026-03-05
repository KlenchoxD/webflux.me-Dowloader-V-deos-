package org.schabi.newpipe.util.savedstate;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.AbsSavedState;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.SavedStateHandle;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SavedStateHandleStateSaver {
    private static final String BRIDGE_STATE_KEY =
            "org.schabi.newpipe.util.savedstate.SavedStateHandleStateSaver.STATE";

    private SavedStateHandleStateSaver() {
    }

    public static void saveInstanceState(@NonNull final Object target,
                                         @NonNull final Bundle state) {
        final SavedStateHandle handle = new SavedStateHandle();
        for (final Field field : getStateFields(target.getClass())) {
            try {
                field.setAccessible(true);
                final Object value = field.get(target);
                final String key = getFieldKey(field);
                handle.set(key, value);
            } catch (final IllegalArgumentException ignored) {
                // SavedStateHandle only supports a subset of value types.
            } catch (final IllegalAccessException ignored) {
            }
        }

        final Bundle bridgeState = new Bundle();
        for (final String key : handle.keys()) {
            putValue(bridgeState, key, handle.get(key));
        }
        state.putBundle(BRIDGE_STATE_KEY, bridgeState);
    }

    public static void restoreInstanceState(@NonNull final Object target,
                                            @Nullable final Bundle state) {
        if (state == null) {
            return;
        }

        final Bundle bridgeState = state.getBundle(BRIDGE_STATE_KEY);
        if (bridgeState == null) {
            return;
        }

        final SavedStateHandle handle = new SavedStateHandle(bundleToMap(bridgeState));
        for (final Field field : getStateFields(target.getClass())) {
            final String key = getFieldKey(field);
            if (!handle.contains(key)) {
                continue;
            }

            try {
                field.setAccessible(true);
                final Object value = handle.get(key);
                if (value == null && field.getType().isPrimitive()) {
                    continue;
                }
                field.set(target, value);
            } catch (final IllegalArgumentException ignored) {
                // Ignore mismatched runtime types and keep current field value.
            } catch (final IllegalAccessException ignored) {
            }
        }
    }

    @NonNull
    public static <T extends View> Parcelable saveInstanceState(
            @NonNull final T target,
            @Nullable final Parcelable parentState) {
        final Bundle bridgeState = new Bundle();
        saveInstanceState((Object) target, bridgeState);
        return new SavedViewState(parentState, bridgeState);
    }

    @Nullable
    public static <T extends View> Parcelable restoreInstanceState(
            @NonNull final T target,
            @Nullable final Parcelable state) {
        if (!(state instanceof SavedViewState)) {
            return state;
        }
        final SavedViewState savedViewState = (SavedViewState) state;
        restoreInstanceState((Object) target, savedViewState.bridgeState);
        return savedViewState.getSuperState();
    }

    @NonNull
    private static List<Field> getStateFields(@NonNull final Class<?> type) {
        final List<Field> fields = new ArrayList<>();
        Class<?> currentType = type;
        while (currentType != null && currentType != Object.class) {
            for (final Field field : currentType.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (field.isAnnotationPresent(State.class)) {
                    fields.add(field);
                }
            }
            currentType = currentType.getSuperclass();
        }
        return fields;
    }

    @NonNull
    private static String getFieldKey(@NonNull final Field field) {
        return field.getDeclaringClass().getName() + "#" + field.getName();
    }

    @NonNull
    private static Map<String, Object> bundleToMap(@NonNull final Bundle bundle) {
        final Map<String, Object> values = new HashMap<>();
        for (final String key : bundle.keySet()) {
            values.put(key, bundle.get(key));
        }
        return values;
    }

    private static void putValue(@NonNull final Bundle bundle,
                                 @NonNull final String key,
                                 @Nullable final Object value) {
        if (value == null) {
            bundle.putString(key, null);
            return;
        }
        if (value instanceof Bundle) {
            bundle.putBundle(key, (Bundle) value);
            return;
        }
        if (value instanceof Parcelable) {
            bundle.putParcelable(key, (Parcelable) value);
            return;
        }
        if (value instanceof byte[]) {
            bundle.putByteArray(key, (byte[]) value);
            return;
        }
        if (value instanceof short[]) {
            bundle.putShortArray(key, (short[]) value);
            return;
        }
        if (value instanceof char[]) {
            bundle.putCharArray(key, (char[]) value);
            return;
        }
        if (value instanceof int[]) {
            bundle.putIntArray(key, (int[]) value);
            return;
        }
        if (value instanceof long[]) {
            bundle.putLongArray(key, (long[]) value);
            return;
        }
        if (value instanceof float[]) {
            bundle.putFloatArray(key, (float[]) value);
            return;
        }
        if (value instanceof double[]) {
            bundle.putDoubleArray(key, (double[]) value);
            return;
        }
        if (value instanceof boolean[]) {
            bundle.putBooleanArray(key, (boolean[]) value);
            return;
        }
        if (value instanceof CharSequence) {
            bundle.putCharSequence(key, (CharSequence) value);
            return;
        }
        if (value instanceof String) {
            bundle.putString(key, (String) value);
            return;
        }
        if (value instanceof Character) {
            bundle.putChar(key, (Character) value);
            return;
        }
        if (value instanceof Byte) {
            bundle.putByte(key, (Byte) value);
            return;
        }
        if (value instanceof Short) {
            bundle.putShort(key, (Short) value);
            return;
        }
        if (value instanceof Integer) {
            bundle.putInt(key, (Integer) value);
            return;
        }
        if (value instanceof Long) {
            bundle.putLong(key, (Long) value);
            return;
        }
        if (value instanceof Float) {
            bundle.putFloat(key, (Float) value);
            return;
        }
        if (value instanceof Double) {
            bundle.putDouble(key, (Double) value);
            return;
        }
        if (value instanceof Boolean) {
            bundle.putBoolean(key, (Boolean) value);
            return;
        }
        if (value instanceof Serializable) {
            bundle.putSerializable(key, (Serializable) value);
        }
    }

    private static final class SavedViewState extends View.BaseSavedState {
        private final Bundle bridgeState;

        SavedViewState(@Nullable final Parcelable superState, @NonNull final Bundle bridgeState) {
            super(superState != null ? superState : AbsSavedState.EMPTY_STATE);
            this.bridgeState = bridgeState;
        }

        SavedViewState(@NonNull final Parcel source) {
            super(source);
            bridgeState = source.readBundle(SavedViewState.class.getClassLoader());
        }

        @Override
        public void writeToParcel(@NonNull final Parcel out, final int flags) {
            super.writeToParcel(out, flags);
            out.writeBundle(bridgeState);
        }

        public static final Parcelable.Creator<SavedViewState> CREATOR =
                new Parcelable.Creator<>() {
                    @Override
                    public SavedViewState createFromParcel(final Parcel source) {
                        return new SavedViewState(source);
                    }

                    @Override
                    public SavedViewState[] newArray(final int size) {
                        return new SavedViewState[size];
                    }
                };
    }
}
