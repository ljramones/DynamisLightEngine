package org.dynamislight.impl.vulkan;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

final class VulkanTelemetryStateBinder {
    static void copyMatchingFields(Object source, Object target) {
        if (source == null || target == null) {
            return;
        }
        Map<String, Field> sourceFields = allFieldsByName(source.getClass());
        Map<String, Field> targetFields = allFieldsByName(target.getClass());
        for (Map.Entry<String, Field> entry : sourceFields.entrySet()) {
            Field targetField = targetFields.get(entry.getKey());
            if (targetField == null) {
                continue;
            }
            Field sourceField = entry.getValue();
            if (!isAssignable(targetField.getType(), sourceField.getType())) {
                continue;
            }
            try {
                sourceField.setAccessible(true);
                targetField.setAccessible(true);
                targetField.set(target, sourceField.get(source));
            } catch (IllegalAccessException ignored) {
            }
        }
    }

    private static Map<String, Field> allFieldsByName(Class<?> type) {
        Map<String, Field> fields = new HashMap<>();
        Class<?> cursor = type;
        while (cursor != null && cursor != Object.class) {
            for (Field field : cursor.getDeclaredFields()) {
                fields.putIfAbsent(field.getName(), field);
            }
            cursor = cursor.getSuperclass();
        }
        return fields;
    }

    private static boolean isAssignable(Class<?> targetType, Class<?> sourceType) {
        if (targetType.isAssignableFrom(sourceType)) {
            return true;
        }
        if (!targetType.isPrimitive() && sourceType.isPrimitive()) {
            return boxedType(sourceType) == targetType;
        }
        if (targetType.isPrimitive() && !sourceType.isPrimitive()) {
            return boxedType(targetType) == sourceType;
        }
        return false;
    }

    private static Class<?> boxedType(Class<?> primitive) {
        if (primitive == boolean.class) {
            return Boolean.class;
        }
        if (primitive == byte.class) {
            return Byte.class;
        }
        if (primitive == char.class) {
            return Character.class;
        }
        if (primitive == short.class) {
            return Short.class;
        }
        if (primitive == int.class) {
            return Integer.class;
        }
        if (primitive == long.class) {
            return Long.class;
        }
        if (primitive == float.class) {
            return Float.class;
        }
        if (primitive == double.class) {
            return Double.class;
        }
        return primitive;
    }

    private VulkanTelemetryStateBinder() {
    }
}
