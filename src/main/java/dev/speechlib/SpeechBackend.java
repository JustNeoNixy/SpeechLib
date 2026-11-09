package dev.speechlib;

/** The backend actually running inference, including automatic CPU fallback. */
public enum SpeechBackend { CPU, VULKAN, METAL }
