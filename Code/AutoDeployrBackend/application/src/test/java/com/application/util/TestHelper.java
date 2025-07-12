package com.application.util;

import org.mockito.ArgumentCaptor;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

public class TestHelper {
    public static void assertThrowsWithMessage(Class<? extends Throwable> expectedType,
                                               Supplier<?> executable,
                                               String expectedMessage) {
        Throwable exception = assertThrows(expectedType, executable::get);
        assertTrue(exception.getMessage().contains(expectedMessage),
                "Expected message to contain: " + expectedMessage +
                        " but was: " + exception.getMessage());
    }

    public static <T> ArgumentCaptor<T> captureArgument(Class<T> clazz) {
        return ArgumentCaptor.forClass(clazz);
    }
}