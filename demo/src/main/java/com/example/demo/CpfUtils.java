package com.example.demo;

public final class CpfUtils {

    private CpfUtils() {}

    public static String formatarSeValido(String cpf) {
        if (cpf == null) {
            return null;
        }
        String digitos = cpf.replaceAll("\\D", "");
        if (!digitos.matches("\\d{11}")) {
            return null;
        }
        return digitos.replaceFirst("(\\d{3})(\\d{3})(\\d{3})(\\d{2})", "$1.$2.$3-$4");
    }
}
