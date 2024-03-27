package io.github.okkez.embulk.input.trino;

public record OutputColumn(String name, TrinoType type, String columnType) {}
