package com.example.demo;

public final class CpfUtils {

    private CpfUtils() {}

    public static String formatarSeValido(String cpf) {
        if (cpf == null) {
            return null;
        }
        String digitos = cpf.replaceAll("\\D", "");
        if (!valido(digitos)) {
            return null;
        }
        return digitos.replaceFirst("(\\d{3})(\\d{3})(\\d{3})(\\d{2})", "$1.$2.$3-$4");
    }

    private static boolean valido(String cpf) {
        if (!cpf.matches("\\d{11}") || cpf.chars().distinct().count() == 1) {
            return false;
        }
        int primeiro = calcularDigito(cpf, 9);
        int segundo = calcularDigito(cpf, 10);
        return cpf.charAt(9) - '0' == primeiro && cpf.charAt(10) - '0' == segundo;
    }

    private static int calcularDigito(String cpf, int quantidade) {
        int soma = 0;
        for (int indice = 0; indice < quantidade; indice++) {
            soma += (cpf.charAt(indice) - '0') * (quantidade + 1 - indice);
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
